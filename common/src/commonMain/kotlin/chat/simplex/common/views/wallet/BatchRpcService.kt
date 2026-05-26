package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
/**
 * Batch RPC Service - Efficiently batch multiple RPC calls into single requests
 * Reduces network overhead and improves performance
 */
object BatchRpcService {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * JSON-RPC request
     */
    @Serializable
    data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val method: String,
        val params: List<JsonElement>,
        val id: Int
    )
    
    /**
     * JSON-RPC response
     */
    @Serializable
    data class JsonRpcResponse(
        val jsonrpc: String = "2.0",
        val id: Int,
        val result: JsonElement? = null,
        val error: JsonRpcError? = null
    )
    
    @Serializable
    data class JsonRpcError(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    )
    
    /**
     * Execute a batch of RPC calls
     */
    suspend fun batchCall(
        network: BlockchainNetwork,
        requests: List<RpcCall>
    ): Map<Int, RpcResult> = withContext(Dispatchers.IO) {
        val jsonRequests = requests.mapIndexed { index, call ->
            JsonRpcRequest(
                method = call.method,
                params = call.params.map { JsonPrimitive(it) },
                id = index
            )
        }
        val requestBody = json.encodeToString(jsonRequests)

        // Try preferred endpoint, then fall back through BlockchainService list
        val result = BlockchainService.rpcCallRaw(network, requestBody)
        if (result != null) {
            parseBatchResponse(result)
        } else {
            emptyMap()
        }
    }
    
    /**
     * Batch fetch multiple token balances
     */
    suspend fun batchFetchTokenBalances(
        network: BlockchainNetwork,
        walletAddress: String,
        tokens: List<WalletToken>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (tokens.isEmpty()) return@withContext emptyMap()
        
        // Build balanceOf calls for each token
        val calls = tokens.map { token ->
            val data = buildBalanceOfCallData(walletAddress)
            RpcCall(
                method = "eth_call",
                params = listOf(
                    """{"to":"${token.contractAddress}","data":"$data"}""",
                    "latest"
                )
            )
        }
        
        val results = batchCall(network, calls)
        
        // Parse results
        val balances = mutableMapOf<String, String>()
        tokens.forEachIndexed { index, token ->
            val result = results[index]
            if (result is RpcResult.Success) {
                val balance = parseBalanceHex(result.value, token.decimals)
                balances[token.contractAddress] = balance
                
                // Update cache
                WalletCache.setTokenBalance(token.contractAddress, walletAddress, network, balance)
            } else {
                balances[token.contractAddress] = "0"
            }
        }
        
        balances
    }
    
    /**
     * Batch fetch multiple native balances across networks
     */
    suspend fun batchFetchNativeBalances(
        address: String,
        networks: List<BlockchainNetwork>
    ): Map<BlockchainNetwork, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<BlockchainNetwork, String>()

        networks.map { network ->
            async {
                try { network to fetchNativeBalance(network, address) }
                catch (_: Exception) { null }
            }
        }.awaitAll().filterNotNull().forEach { (network, balance) ->
            results[network] = balance
            WalletCache.setBalance(address, network, balance)
        }

        results
    }
    
    /**
     * Batch check multiple token allowances
     */
    suspend fun batchFetchAllowances(
        network: BlockchainNetwork,
        ownerAddress: String,
        spenderAddress: String,
        tokens: List<WalletToken>
    ): Map<String, java.math.BigInteger> = withContext(Dispatchers.IO) {
        if (tokens.isEmpty()) return@withContext emptyMap()
        
        val calls = tokens.map { token ->
            val data = TokenApprovalService.buildAllowanceCallData(ownerAddress, spenderAddress)
            RpcCall(
                method = "eth_call",
                params = listOf(
                    """{"to":"${token.contractAddress}","data":"$data"}""",
                    "latest"
                )
            )
        }
        
        val results = batchCall(network, calls)
        
        val allowances = mutableMapOf<String, java.math.BigInteger>()
        tokens.forEachIndexed { index, token ->
            val result = results[index]
            if (result is RpcResult.Success) {
                allowances[token.contractAddress] = TokenApprovalService.parseAllowanceResponse(result.value)
            } else {
                allowances[token.contractAddress] = java.math.BigInteger.ZERO
            }
        }
        
        allowances
    }
    
    /**
     * Fetch EIP-1559 gas data (baseFee + priorityFee)
     */
    suspend fun fetchEIP1559GasData(network: BlockchainNetwork): EIP1559GasData? = withContext(Dispatchers.IO) {
        val calls = listOf(
            RpcCall("eth_gasPrice", emptyList()),
            RpcCall("eth_maxPriorityFeePerGas", emptyList()),
            RpcCall("eth_getBlockByNumber", listOf("latest", "false"))
        )
        
        val results = batchCall(network, calls)
        
        try {
            val gasPrice = (results[0] as? RpcResult.Success)?.value?.let { parseHexToLong(it) } ?: return@withContext null
            val maxPriorityFee = (results[1] as? RpcResult.Success)?.value?.let { parseHexToLong(it) }
            
            // Extract baseFee from block (EIP-1559 networks)
            val blockResult = results[2]
            val baseFee = if (blockResult is RpcResult.Success) {
                extractBaseFeeFromBlock(blockResult.value)
            } else null
            
            val data = EIP1559GasData(
                legacyGasPrice = gasPrice,
                baseFee = baseFee,
                maxPriorityFee = maxPriorityFee,
                supportsEIP1559 = baseFee != null
            )
            
            // Cache the result
            WalletCache.setGasPrice(network, WalletCache.GasPriceData(
                legacy = gasPrice / 1_000_000_000, // Wei to Gwei
                baseFee = baseFee?.div(1_000_000_000),
                maxPriorityFee = maxPriorityFee?.div(1_000_000_000),
                maxFee = baseFee?.let { (it * 2 + (maxPriorityFee ?: 0)) / 1_000_000_000 },
                supportsEIP1559 = baseFee != null
            ))
            
            data
        } catch (e: Exception) {
            null
        }
    }
    
    // Helper functions
    
    private fun buildBalanceOfCallData(address: String): String {
        val cleanAddress = address.removePrefix("0x").lowercase().padStart(64, '0')
        return "0x70a08231000000000000000000000000$cleanAddress"
    }
    
    private fun parseBalanceHex(hex: String, decimals: Int): String {
        return try {
            val cleanHex = hex.removePrefix("0x")
            if (cleanHex.isEmpty() || cleanHex == "0" || cleanHex.all { it == '0' }) {
                return "0"
            }
            
            val balance = java.math.BigInteger(cleanHex, 16)
            val divisor = java.math.BigDecimal.TEN.pow(decimals)
            balance.toBigDecimal().divide(divisor, decimals, java.math.RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        } catch (e: Exception) {
            "0"
        }
    }
    
    private fun parseHexToLong(hex: String): Long {
        val clean = hex.removePrefix("0x")
        return if (clean.isEmpty()) 0L else java.lang.Long.parseLong(clean, 16)
    }
    
    private fun extractBaseFeeFromBlock(blockJson: String): Long? {
        return try {
            // Simple extraction of baseFeePerGas from block JSON
            val baseFeeStart = blockJson.indexOf("\"baseFeePerGas\":\"0x")
            if (baseFeeStart < 0) return null
            
            val start = baseFeeStart + 19
            val end = blockJson.indexOf("\"", start)
            val hex = blockJson.substring(start, end)
            
            java.lang.Long.parseLong(hex, 16)
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun fetchNativeBalance(network: BlockchainNetwork, address: String): String {
        val calls = listOf(RpcCall("eth_getBalance", listOf(address, "latest")))
        val results = batchCall(network, calls)
        
        return if (results[0] is RpcResult.Success) {
            parseBalanceHex((results[0] as RpcResult.Success).value, 18)
        } else "0"
    }
    
    private fun parseBatchResponse(responseBody: String): Map<Int, RpcResult> {
        return try {
            val responses = json.decodeFromString<List<JsonRpcResponse>>(responseBody)
            responses.associate { response ->
                response.id to if (response.error != null) {
                    RpcResult.Error(response.error.code, response.error.message)
                } else {
                    val resultStr = response.result?.toString()?.trim('"') ?: ""
                    RpcResult.Success(resultStr)
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * RPC call definition
     */
    data class RpcCall(
        val method: String,
        val params: List<String>
    )
    
    /**
     * RPC result
     */
    sealed class RpcResult {
        data class Success(val value: String) : RpcResult()
        data class Error(val code: Int, val message: String) : RpcResult()
    }
    
    /**
     * EIP-1559 gas data
     */
    data class EIP1559GasData(
        val legacyGasPrice: Long,      // in Wei
        val baseFee: Long?,            // in Wei
        val maxPriorityFee: Long?,     // in Wei
        val supportsEIP1559: Boolean
    ) {
        val baseFeeGwei: Long? get() = baseFee?.div(1_000_000_000)
        val maxPriorityFeeGwei: Long? get() = maxPriorityFee?.div(1_000_000_000)
        val legacyGasPriceGwei: Long get() = legacyGasPrice / 1_000_000_000
    }
}

