package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Production blockchain service with:
 *  - Fallback RPC endpoints (auto-rotate on failure)
 *  - Exponential-backoff retry on every RPC call
 *  - Transaction confirmation polling
 *  - On-chain event watching for incoming transfers
 *  - Block-height caching
 */
object BlockchainService {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Fallback RPC endpoints (first = primary) ──────────────────
    private val rpcEndpoints: Map<BlockchainNetwork, List<String>> = mapOf(
        BlockchainNetwork.ETHEREUM to listOf(
            "https://ethereum-rpc.publicnode.com",
            "https://rpc.ankr.com/eth",
            "https://eth.llamarpc.com"
        ),
        BlockchainNetwork.BINANCE_SMART_CHAIN to listOf(
            "https://bsc-rpc.publicnode.com",
            "https://rpc.ankr.com/bsc",
            "https://bsc-dataseed1.binance.org"
        ),
        BlockchainNetwork.POLYGON to listOf(
            "https://polygon-bor-rpc.publicnode.com",
            "https://rpc.ankr.com/polygon",
            "https://polygon-rpc.com",
            "https://1rpc.io/matic"
        ),
        BlockchainNetwork.ARBITRUM to listOf(
            "https://arbitrum-one-rpc.publicnode.com",
            "https://rpc.ankr.com/arbitrum",
            "https://arb1.arbitrum.io/rpc"
        ),
        BlockchainNetwork.OPTIMISM to listOf(
            "https://optimism-rpc.publicnode.com",
            "https://rpc.ankr.com/optimism",
            "https://mainnet.optimism.io"
        ),
        BlockchainNetwork.AVALANCHE to listOf(
            "https://avalanche-c-chain-rpc.publicnode.com",
            "https://rpc.ankr.com/avalanche",
            "https://api.avax.network/ext/bc/C/rpc"
        ),
        BlockchainNetwork.BASE to listOf(
            "https://base-rpc.publicnode.com",
            "https://rpc.ankr.com/base",
            "https://mainnet.base.org"
        ),
        BlockchainNetwork.NEAR to listOf(
            "https://rpc.mainnet.near.org",
            "https://near.lava.build",
            "https://rpc.ankr.com/near"
        )
    )

    private val preferredEndpoint = ConcurrentHashMap<BlockchainNetwork, Int>()

    // ── Block heights ─────────────────────────────────────────────
    private val _blockHeights = MutableStateFlow<Map<BlockchainNetwork, Long>>(emptyMap())
    val blockHeights: StateFlow<Map<BlockchainNetwork, Long>> = _blockHeights.asStateFlow()

    // ═══════════════════════════════════════════════════════════════
    //  RPC with retry + fallback
    // ═══════════════════════════════════════════════════════════════

    suspend fun rpcCall(
        network: BlockchainNetwork,
        method: String,
        params: List<Any>,
        maxRetries: Int = 3
    ): JsonElement? = withContext(Dispatchers.IO) {
        val endpoints = rpcEndpoints[network] ?: return@withContext null
        val startIdx = preferredEndpoint[network] ?: 0
        var lastError: Exception? = null

        for (attempt in 0 until maxRetries) {
            for (offset in endpoints.indices) {
                val idx = (startIdx + offset) % endpoints.size
                val url = endpoints[idx]
                try {
                    val result = executeRpc(url, method, params)
                    if (result != null) {
                        preferredEndpoint[network] = idx
                        return@withContext result
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            val backoff = (1000L shl attempt).coerceAtMost(8_000L)
            delay(backoff)
        }
        null
    }

    private fun executeRpc(rpcUrl: String, method: String, params: List<Any>): JsonElement? {
        val paramsJson = buildJsonArray {
            params.forEach { p ->
                when (p) {
                    is String -> add(JsonPrimitive(p))
                    is Number -> add(JsonPrimitive(p))
                    is Boolean -> add(JsonPrimitive(p))
                    is JsonElement -> add(p)
                    else -> add(JsonPrimitive(p.toString()))
                }
            }
        }
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            put("params", paramsJson)
        }.toString()

        return try {
            val resp = SecureHttp.postJson(rpcUrl, body) ?: return null
            val obj = json.parseToJsonElement(resp).jsonObject
            if (obj["error"] != null) return null
            obj["result"]
        } catch (_: Exception) { null }
    }

    fun getRpcUrl(network: BlockchainNetwork): String? {
        val endpoints = rpcEndpoints[network] ?: return null
        val idx = preferredEndpoint[network] ?: 0
        return endpoints.getOrNull(idx) ?: endpoints.firstOrNull()
    }

    /**
     * Send a pre-built JSON body to the RPC with fallback across endpoints.
     * Used by BatchRpcService for batch calls.
     */
    suspend fun rpcCallRaw(
        network: BlockchainNetwork,
        body: String
    ): String? = withContext(Dispatchers.IO) {
        val endpoints = rpcEndpoints[network] ?: return@withContext null
        val startIdx = preferredEndpoint[network] ?: 0

        for (offset in endpoints.indices) {
            val idx = (startIdx + offset) % endpoints.size
            try {
                val resp = SecureHttp.postJson(endpoints[idx], body)
                if (resp != null) {
                    preferredEndpoint[network] = idx
                    return@withContext resp
                }
            } catch (_: Exception) { }
        }
        null
    }

    // ═══════════════════════════════════════════════════════════════
    //  Transaction confirmation polling
    // ═══════════════════════════════════════════════════════════════

    data class TxConfirmation(
        val txHash: String,
        val status: TransactionStatus,
        val confirmations: Int,
        val blockNumber: Long?
    )

    suspend fun pollTransactionConfirmation(
        txHash: String,
        network: BlockchainNetwork,
        requiredConfirmations: Int = 1,
        timeoutMs: Long = 300_000,
        onUpdate: (TxConfirmation) -> Unit = {}
    ): TxConfirmation = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var pollInterval = 3_000L

        while (System.currentTimeMillis() - start < timeoutMs) {
            val receipt = getTransactionReceipt(txHash, network)
            if (receipt != null) {
                val status = receipt.status
                val blockNum = receipt.blockNumber
                val currentBlock = getBlockNumber(network)
                val confirmations = if (blockNum != null && currentBlock != null) {
                    (currentBlock - blockNum + 1).toInt().coerceAtLeast(0)
                } else 0

                val conf = TxConfirmation(txHash, status, confirmations, blockNum)
                onUpdate(conf)

                if (status == TransactionStatus.FAILED) return@withContext conf
                if (confirmations >= requiredConfirmations) return@withContext conf
            }

            delay(pollInterval)
            pollInterval = (pollInterval * 1.2).toLong().coerceAtMost(15_000L)
        }

        TxConfirmation(txHash, TransactionStatus.PENDING, 0, null)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Transaction receipt
    // ═══════════════════════════════════════════════════════════════

    data class TxReceipt(
        val txHash: String,
        val status: TransactionStatus,
        val blockNumber: Long?,
        val gasUsed: Long?
    )

    suspend fun getTransactionReceipt(txHash: String, network: BlockchainNetwork): TxReceipt? {
        val result = rpcCall(network, "eth_getTransactionReceipt", listOf(txHash)) ?: return null
        if (result is JsonNull) return null

        return try {
            val obj = result.jsonObject
            val statusHex = obj["status"]?.jsonPrimitive?.contentOrNull ?: return null
            val blockHex = obj["blockNumber"]?.jsonPrimitive?.contentOrNull
            val gasHex = obj["gasUsed"]?.jsonPrimitive?.contentOrNull

            TxReceipt(
                txHash = txHash,
                status = if (statusHex == "0x1") TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
                blockNumber = blockHex?.removePrefix("0x")?.toLongOrNull(16),
                gasUsed = gasHex?.removePrefix("0x")?.toLongOrNull(16)
            )
        } catch (_: Exception) { null }
    }

    suspend fun getBlockNumber(network: BlockchainNetwork): Long? {
        val result = rpcCall(network, "eth_blockNumber", emptyList()) ?: return null
        return try {
            result.jsonPrimitive.content.removePrefix("0x").toLongOrNull(16)
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Incoming transfer watcher (EVM only via eth_getLogs)
    // ═══════════════════════════════════════════════════════════════

    private val watchJobs = ConcurrentHashMap<String, Job>()
    private val lastScannedBlock = ConcurrentHashMap<String, Long>()
    @Volatile private var _watcherSeeded = false

    /**
     * Must be called once before watchers start.
     * Pre-seeds the dedup set from all stored transactions so we never
     * re-notify old transactions after an app restart.
     */
    fun seedNotifiedHashes() {
        if (_watcherSeeded) return
        _watcherSeeded = true
        try {
            val stored = PlatformWallet.getStoredTransactions()
            for (tx in stored) {
                notifiedTxHashes.add(tx.txHash)
            }
        } catch (_: Exception) { }
    }

    /**
     * Watch for incoming transfers on any supported network.
     * EVM: eth_getLogs for ERC-20 + eth_getBlockByNumber for native receives
     * Non-EVM: polling via public APIs (Tron, Solana, Bitcoin)
     */
    fun watchIncomingTransfers(
        address: String,
        network: BlockchainNetwork,
        intervalMs: Long = 15_000
    ) {
        val key = "${network.name}:$address"
        watchJobs[key]?.cancel()
        watchJobs[key] = scope.launch {
            while (isActive) {
                try {
                    if (network.isEvm) {
                        scanEvmIncoming(address, network)
                    } else {
                        scanNonEvmIncoming(address, network)
                    }
                } catch (_: Exception) { }
                delay(intervalMs)
            }
        }
    }

    fun stopWatching(address: String, network: BlockchainNetwork) {
        val key = "${network.name}:$address"
        watchJobs[key]?.cancel()
        watchJobs.remove(key)
    }

    fun stopAllWatchers() {
        watchJobs.values.forEach { it.cancel() }
        watchJobs.clear()
    }

    // De-dup: track tx hashes we already notified
    private val notifiedTxHashes = ConcurrentHashMap.newKeySet<String>()
    private const val MAX_NOTIFIED_HASHES = 10_000

    private fun alreadyNotified(txHash: String): Boolean {
        if (notifiedTxHashes.size > MAX_NOTIFIED_HASHES) notifiedTxHashes.clear()
        return !notifiedTxHashes.add(txHash)
    }

    // ── EVM incoming scan ─────────────────────────────────────────

    private suspend fun scanEvmIncoming(address: String, network: BlockchainNetwork) {
        val currentBlock = getBlockNumber(network) ?: return
        val key = "${network.name}:$address"
        val fromBlock = lastScannedBlock[key] ?: (currentBlock - 5)
        if (fromBlock >= currentBlock) return

        val addrLower = address.removePrefix("0x").lowercase()
        val paddedAddress = "0x${addrLower.padStart(64, '0')}"

        // 1) ERC-20 Transfer events
        val transferTopic = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
        val filterObj = buildJsonObject {
            put("fromBlock", "0x${fromBlock.toString(16)}")
            put("toBlock", "0x${currentBlock.toString(16)}")
            put("topics", buildJsonArray {
                add(JsonPrimitive(transferTopic))
                add(JsonNull)
                add(JsonPrimitive(paddedAddress))
            })
        }

        val logResult = rpcCall(network, "eth_getLogs", listOf(filterObj))
        if (logResult is JsonArray && logResult.isNotEmpty()) {
            logResult.forEach { logElement ->
                val log = logElement.jsonObject
                val txHash = log["transactionHash"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (alreadyNotified(txHash)) return@forEach

                val tokenAddr = log["address"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val dataHex = log["data"]?.jsonPrimitive?.contentOrNull ?: "0x0"
                val topics = log["topics"]?.jsonArray

                val fromTopic = topics?.getOrNull(1)?.jsonPrimitive?.contentOrNull
                val fromAddr = if (fromTopic != null) "0x${fromTopic.removePrefix("0x").takeLast(40)}" else ""

                val token = PopularTokens.getAllTokens()
                    .firstOrNull { it.contractAddress.equals(tokenAddr, ignoreCase = true) && it.network == network }

                val symbol: String
                val decimals: Int
                if (token != null) {
                    symbol = token.symbol
                    decimals = token.decimals
                } else {
                    symbol = "TOKEN"
                    decimals = 18
                }

                val rawAmount = try {
                    java.math.BigInteger(dataHex.removePrefix("0x").ifEmpty { "0" }, 16)
                } catch (_: Exception) { java.math.BigInteger.ZERO }
                val amount = rawAmount.toBigDecimal()
                    .divide(java.math.BigDecimal.TEN.pow(decimals), decimals, java.math.RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString()

                if (rawAmount > java.math.BigInteger.ZERO) {
                    recordIncomingTx(network, symbol, amount, txHash, fromAddr, tokenAddr)
                }
            }
        }

        // 2) Native ETH/BNB/MATIC receives via block scanning
        scanNativeEvmReceives(address, network, fromBlock, currentBlock)

        lastScannedBlock[key] = currentBlock + 1
    }

    private suspend fun scanNativeEvmReceives(
        address: String,
        network: BlockchainNetwork,
        fromBlock: Long,
        toBlock: Long
    ) {
        val addrLower = address.lowercase()
        val blockRange = fromBlock..toBlock.coerceAtMost(fromBlock + 10) // cap to avoid huge scans

        for (blockNum in blockRange) {
            val blockHex = "0x${blockNum.toString(16)}"
            val blockResult = rpcCall(network, "eth_getBlockByNumber", listOf(blockHex, true)) ?: continue
            if (blockResult is JsonNull) continue

            val txs = try { blockResult.jsonObject["transactions"]?.jsonArray } catch (_: Exception) { null } ?: continue
            for (txEl in txs) {
                val txObj = txEl.jsonObject
                val to = txObj["to"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue
                if (to != addrLower) continue

                val txHash = txObj["hash"]?.jsonPrimitive?.contentOrNull ?: continue
                if (alreadyNotified(txHash)) continue

                val valueHex = txObj["value"]?.jsonPrimitive?.contentOrNull ?: "0x0"
                val valueWei = try {
                    java.math.BigInteger(valueHex.removePrefix("0x").ifEmpty { "0" }, 16)
                } catch (_: Exception) { java.math.BigInteger.ZERO }
                if (valueWei <= java.math.BigInteger.ZERO) continue

                val amount = valueWei.toBigDecimal()
                    .divide(java.math.BigDecimal.TEN.pow(18), 18, java.math.RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString()

                val from = txObj["from"]?.jsonPrimitive?.contentOrNull ?: ""
                recordIncomingTx(network, network.symbol, amount, txHash, from, null)
            }
        }
    }

    // ── Non-EVM incoming scan ─────────────────────────────────────

    private suspend fun scanNonEvmIncoming(address: String, network: BlockchainNetwork) {
        when (network) {
            BlockchainNetwork.TRON -> scanTronIncoming(address)
            BlockchainNetwork.SOLANA -> scanSolanaIncoming(address)
            BlockchainNetwork.BITCOIN -> scanBtcIncoming(address)
            BlockchainNetwork.NEAR -> scanNearIncoming(address)
            else -> { }
        }
    }

    private suspend fun scanTronIncoming(address: String) = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.trongrid.io/v1/accounts/$address/transactions?only_to=true&limit=10&order_by=block_timestamp,desc"
            val body = SecureHttp.get(url, emptyMap()) ?: return@withContext
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonArray ?: return@withContext

            for (txEl in data) {
                val txObj = txEl.jsonObject
                val txId = txObj["txID"]?.jsonPrimitive?.contentOrNull ?: continue
                if (alreadyNotified(txId)) continue

                val rawData = txObj["raw_data"]?.jsonObject ?: continue
                val contracts = rawData["contract"]?.jsonArray ?: continue
                val contract = contracts.firstOrNull()?.jsonObject ?: continue
                val paramValue = contract["parameter"]?.jsonObject?.get("value")?.jsonObject ?: continue

                val amount = paramValue["amount"]?.jsonPrimitive?.longOrNull ?: continue
                val owner = paramValue["owner_address"]?.jsonPrimitive?.contentOrNull ?: ""
                val amountTrx = java.math.BigDecimal(amount).divide(java.math.BigDecimal.TEN.pow(6), 6, java.math.RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString()

                recordIncomingTx(BlockchainNetwork.TRON, "TRX", amountTrx, txId, owner, null)
            }
        } catch (_: Exception) { }
    }

    private suspend fun scanSolanaIncoming(address: String) = withContext(Dispatchers.IO) {
        try {
            val rpcBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getSignaturesForAddress")
                put("params", buildJsonArray {
                    add(address)
                    add(buildJsonObject { put("limit", 10) })
                })
            }.toString()
            val body = SecureHttp.postJson("https://api.mainnet-beta.solana.com", rpcBody) ?: return@withContext
            val root = json.parseToJsonElement(body).jsonObject
            val sigs = root["result"]?.jsonArray ?: return@withContext

            for (sigEl in sigs) {
                val sigObj = sigEl.jsonObject
                val sig = sigObj["signature"]?.jsonPrimitive?.contentOrNull ?: continue
                if (alreadyNotified(sig)) continue

                val err = sigObj["err"]
                if (err != null && err !is JsonNull) continue

                // We can't get the full amount cheaply without getTransaction; notify with generic message
                recordIncomingTx(BlockchainNetwork.SOLANA, "SOL", "", sig, "", null)
            }
        } catch (_: Exception) { }
    }

    private suspend fun scanBtcIncoming(address: String) = withContext(Dispatchers.IO) {
        try {
            val url = "https://mempool.space/api/address/$address/txs"
            val body = SecureHttp.get(url, emptyMap()) ?: return@withContext
            val txs = json.parseToJsonElement(body).jsonArray

            for (txEl in txs.take(10)) {
                val txObj = txEl.jsonObject
                val txid = txObj["txid"]?.jsonPrimitive?.contentOrNull ?: continue
                if (alreadyNotified(txid)) continue

                val vouts = txObj["vout"]?.jsonArray ?: continue
                var received = 0L
                for (vout in vouts) {
                    val scriptPubKey = vout.jsonObject["scriptpubkey_address"]?.jsonPrimitive?.contentOrNull
                    if (scriptPubKey == address) {
                        received += vout.jsonObject["value"]?.jsonPrimitive?.longOrNull ?: 0
                    }
                }
                if (received <= 0) continue

                val amountBtc = java.math.BigDecimal(received).divide(java.math.BigDecimal.TEN.pow(8), 8, java.math.RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString()
                recordIncomingTx(BlockchainNetwork.BITCOIN, "BTC", amountBtc, txid, "", null)
            }
        } catch (_: Exception) { }
    }

    private suspend fun scanNearIncoming(address: String) = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.nearblocks.io/v1/account/$address/txns?per_page=10&order=desc"
            val body = SecureHttp.get(url, emptyMap()) ?: return@withContext
            val root = json.parseToJsonElement(body).jsonObject
            val txs = root["txns"]?.jsonArray ?: return@withContext

            for (txEl in txs) {
                val txObj = txEl.jsonObject
                val txHash = txObj["transaction_hash"]?.jsonPrimitive?.contentOrNull ?: continue
                if (alreadyNotified(txHash)) continue

                val receiver = txObj["receiver_account_id"]?.jsonPrimitive?.contentOrNull ?: continue
                if (receiver != address) continue

                val actions = txObj["actions"]?.jsonArray ?: continue
                for (actionEl in actions) {
                    val actionObj = actionEl.jsonObject
                    val actionKind = actionObj["action"]?.jsonPrimitive?.contentOrNull ?: continue
                    if (actionKind == "TRANSFER") {
                        val deposit = actionObj["args"]?.jsonObject?.get("deposit")?.jsonPrimitive?.contentOrNull ?: continue
                        val amountNear = try {
                            java.math.BigDecimal(deposit)
                                .divide(java.math.BigDecimal.TEN.pow(24), 8, java.math.RoundingMode.DOWN)
                                .stripTrailingZeros().toPlainString()
                        } catch (_: Exception) { continue }

                        val sender = txObj["signer_account_id"]?.jsonPrimitive?.contentOrNull ?: ""
                        recordIncomingTx(BlockchainNetwork.NEAR, "NEAR", amountNear, txHash, sender, null)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    // ── Record incoming transaction into history + notify ──────────

    private fun recordIncomingTx(
        network: BlockchainNetwork,
        symbol: String,
        amount: String,
        txHash: String,
        fromAddress: String,
        contractAddress: String?
    ) {
        val tx = WalletTransaction(
            id = "rx_$txHash",
            txHash = txHash,
            network = network,
            type = TransactionType.RECEIVE,
            status = TransactionStatus.CONFIRMED,
            fromAddress = fromAddress,
            toAddress = "",
            amount = amount,
            timestamp = System.currentTimeMillis(),
            tokenSymbol = if (contractAddress != null) symbol else null,
            tokenContractAddress = contractAddress
        )
        PlatformWallet.addTransaction(tx)
        WalletCache.clearBalances()

        val displayAmount = if (amount.isNotBlank() && amount != "0") amount else ""
        val bodyText = if (displayAmount.isNotBlank()) "Received $displayAmount $symbol on ${network.displayName}"
        else "New $symbol transaction on ${network.displayName}"

        NotificationService.notifyTokensReceived(
            network = network,
            symbol = symbol,
            amount = displayAmount,
            txHash = txHash,
            fromAddress = fromAddress
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Block-height polling (used by UI)
    // ═══════════════════════════════════════════════════════════════

    private var blockPollJob: Job? = null

    fun startBlockPolling(intervalMs: Long = 30_000) {
        blockPollJob?.cancel()
        blockPollJob = scope.launch {
            while (isActive) {
                val heights = mutableMapOf<BlockchainNetwork, Long>()
                rpcEndpoints.keys.map { net ->
                    async { net to (getBlockNumber(net) ?: 0L) }
                }.awaitAll().forEach { (net, h) -> if (h > 0) heights[net] = h }
                _blockHeights.value = heights
                delay(intervalMs)
            }
        }
    }

    fun stopBlockPolling() {
        blockPollJob?.cancel()
    }

    fun shutdown() {
        stopAllWatchers()
        stopBlockPolling()
        scope.coroutineContext.cancelChildren()
    }
}
