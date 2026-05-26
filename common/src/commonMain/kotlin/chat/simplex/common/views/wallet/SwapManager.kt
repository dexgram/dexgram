package chat.simplex.common.views.wallet

import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Production swap manager that wraps [SwapService] with:
 *  - Full transaction lifecycle tracking (submitted → pending → confirmed / failed)
 *  - Local persistence of pending swaps (resume on app restart)
 *  - Allowance pre-check + approve-if-needed
 *  - Gas estimation before execution
 *  - Input validation (amounts, slippage, addresses)
 *  - Notification integration
 *  - Automatic confirmation polling via [BlockchainService]
 */
object SwapManager {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Observable state ──────────────────────────────────────────
    private val _pendingSwaps = MutableStateFlow<List<PendingSwap>>(emptyList())
    val pendingSwaps: StateFlow<List<PendingSwap>> = _pendingSwaps.asStateFlow()

    private val _swapHistory = MutableStateFlow<List<PendingSwap>>(emptyList())
    val swapHistory: StateFlow<List<PendingSwap>> = _swapHistory.asStateFlow()

    private val pollingJobs = ConcurrentHashMap<String, Job>()

    // ═══════════════════════════════════════════════════════════════
    //  Input validation
    // ═══════════════════════════════════════════════════════════════

    data class SwapValidation(val valid: Boolean, val error: String? = null)

    fun validateSwapInput(
        fromAmount: String,
        fromBalance: String,
        toToken: Any?,
        slippage: Double,
        fromAddress: String,
        network: BlockchainNetwork
    ): SwapValidation {
        val amount = fromAmount.toDoubleOrNull()
            ?: return SwapValidation(false, generalGetString(MR.strings.wallet_swap_invalid_amount))
        if (amount <= 0)
            return SwapValidation(false, generalGetString(MR.strings.wallet_swap_amount_greater_zero))

        val balance = fromBalance.toDoubleOrNull() ?: 0.0
        if (amount > balance)
            return SwapValidation(false, generalGetString(MR.strings.wallet_swap_insufficient_balance))

        if (toToken == null)
            return SwapValidation(false, generalGetString(MR.strings.wallet_swap_select_destination))

        if (slippage < 0.01 || slippage > 50.0)
            return SwapValidation(false, generalGetString(MR.strings.wallet_swap_slippage_range))

        if (fromAddress.isBlank())
            return SwapValidation(false, generalGetString(MR.strings.wallet_swap_address_unavailable))

        if (network.isEvm && !fromAddress.startsWith("0x"))
            return SwapValidation(false, generalGetString(MR.strings.wallet_swap_invalid_evm_address))

        return SwapValidation(true)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Pre-swap checks
    // ═══════════════════════════════════════════════════════════════

    suspend fun checkAllowance(
        tokenAddress: String,
        routerAddress: String,
        ownerAddress: String,
        requiredAmount: BigInteger,
        network: BlockchainNetwork
    ): AllowanceCheck = withContext(Dispatchers.IO) {
        try {
            val data = TokenApprovalService.buildAllowanceCallData(ownerAddress, routerAddress)
            val result = BlockchainService.rpcCall(
                network, "eth_call",
                listOf(buildJsonObject {
                    put("to", tokenAddress)
                    put("data", data)
                }.toString(), "latest")
            )
            val hexResult = result?.jsonPrimitive?.contentOrNull ?: "0x0"
            val current = TokenApprovalService.parseAllowanceResponse(hexResult)
            val needsApproval = current < requiredAmount
            AllowanceCheck(
                currentAllowance = current,
                requiredAmount = requiredAmount,
                needsApproval = needsApproval
            )
        } catch (_: Exception) {
            AllowanceCheck(BigInteger.ZERO, requiredAmount, true)
        }
    }

    suspend fun estimateGas(
        network: BlockchainNetwork,
        from: String,
        to: String,
        data: String,
        value: String = "0"
    ): Long? = withContext(Dispatchers.IO) {
        try {
            val callObj = buildJsonObject {
                put("from", from)
                put("to", to)
                put("data", data)
                if (value != "0") put("value", "0x${BigInteger(value).toString(16)}")
            }
            val result = BlockchainService.rpcCall(network, "eth_estimateGas", listOf(callObj))
            result?.jsonPrimitive?.contentOrNull
                ?.removePrefix("0x")
                ?.toLongOrNull(16)
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Execute swap (full lifecycle)
    // ═══════════════════════════════════════════════════════════════

    suspend fun executeSwap(
        quote: SwapQuote,
        fromAddress: String,
        slippagePercent: Double,
        destinationAddress: String? = null
    ): Result<PendingSwap> = withContext(Dispatchers.IO) {
        val pending = PendingSwap(
            id = "swap_${System.currentTimeMillis()}",
            quote = quote,
            fromAddress = fromAddress,
            slippage = slippagePercent,
            state = SwapLifecycleState.SUBMITTING,
            createdAt = System.currentTimeMillis()
        )
        addPending(pending)

        try {
            if (quote.provider == "ChangeNOW") {
                return@withContext executeExchangeSwap(pending, quote, fromAddress, destinationAddress)
            }

            val result = SwapService.executeSwap(quote, fromAddress, slippagePercent)

            result.fold(
                onSuccess = { swapResult ->
                    val updated = pending.copy(
                        state = SwapLifecycleState.PENDING,
                        txHash = swapResult.txHash
                    )
                    updatePending(updated)
                    persistPendingSwaps()

                    if (swapResult.txHash.isNotBlank() && quote.network.isEvm) {
                        startConfirmationPolling(updated)
                    }

                    Result.success(updated)
                },
                onFailure = { e ->
                    val failed = pending.copy(
                        state = SwapLifecycleState.FAILED,
                        errorMessage = e.message,
                        completedAt = System.currentTimeMillis()
                    )
                    updatePending(failed)
                    moveToHistory(failed)
                    persistPendingSwaps()

                    NotificationService.notifySwapFailed(
                        network = quote.network,
                        fromSymbol = quote.fromToken,
                        toSymbol = quote.toToken,
                        reason = e.message ?: generalGetString(MR.strings.wallet_unknown_error),
                        txHash = null
                    )

                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            val failed = pending.copy(
                state = SwapLifecycleState.FAILED,
                errorMessage = e.message,
                completedAt = System.currentTimeMillis()
            )
            updatePending(failed)
            moveToHistory(failed)
            persistPendingSwaps()
            Result.failure(e)
        }
    }

    private suspend fun executeExchangeSwap(
        pending: PendingSwap, quote: SwapQuote, fromAddress: String,
        destinationAddress: String? = null
    ): Result<PendingSwap> {
        val srcNetwork = quote.network
        val dstNetwork = quote.destNetwork ?: quote.network

        val recipientAddress = if (!destinationAddress.isNullOrBlank()) {
            destinationAddress
        } else if (dstNetwork == srcNetwork) {
            fromAddress
        } else {
            return Result.failure(Exception(
                String.format(generalGetString(MR.strings.wallet_swap_no_destination_address), dstNetwork.displayName)
            ))
        }


        val exchangeResult = SwapService.createChangeNowExchange(
            srcNetwork, dstNetwork,
            quote.fromToken, quote.toToken, quote.fromAmount,
            recipientAddress, refundAddress = fromAddress
        )

        return exchangeResult.fold(
            onSuccess = { exchange ->
                val awaitingDeposit = pending.copy(
                    state = SwapLifecycleState.AWAITING_DEPOSIT,
                    exchangeId = exchange.exchangeId,
                    exchangeStatus = exchange.status,
                    depositAddress = exchange.depositAddress,
                    depositExtraId = exchange.depositExtraId
                )
                updatePending(awaitingDeposit)
                persistPendingSwaps()

                val depositResult = sendDepositToExchange(
                    network = srcNetwork,
                    token = quote.fromToken,
                    amount = quote.fromAmount,
                    depositAddress = exchange.depositAddress,
                    fromAddress = fromAddress,
                    memo = exchange.depositExtraId
                )

                depositResult.fold(
                    onSuccess = { txHash ->
                        val deposited = awaitingDeposit.copy(
                            state = SwapLifecycleState.CONFIRMING_DEPOSIT,
                            txHash = txHash
                        )
                        updatePending(deposited)
                        persistPendingSwaps()
                        startExchangeStatusPolling(deposited)
                        Result.success(deposited)
                    },
                    onFailure = { depositError ->
                        val detailedError = "Deposit ${quote.fromAmount} ${quote.fromToken} " +
                            "to ${exchange.depositAddress.take(12)}... failed: ${depositError.message}"
                        SecureLog.e("SwapExchange", "Deposit failed: ${depositError.javaClass.simpleName}", null)
                        val failedDeposit = awaitingDeposit.copy(
                            state = SwapLifecycleState.FAILED,
                            errorMessage = detailedError,
                            completedAt = System.currentTimeMillis()
                        )
                        updatePending(failedDeposit)
                        moveToHistory(failedDeposit)
                        persistPendingSwaps()
                        Result.failure(Exception(detailedError))
                    }
                )
            },
            onFailure = { e ->
                val errorMsg = e.message ?: generalGetString(MR.strings.wallet_swap_exchange_creation_failed)
                val failed = pending.copy(
                    state = SwapLifecycleState.FAILED,
                    errorMessage = errorMsg,
                    completedAt = System.currentTimeMillis()
                )
                updatePending(failed)
                moveToHistory(failed)
                persistPendingSwaps()

                NotificationService.notifySwapFailed(
                    network = quote.network,
                    fromSymbol = quote.fromToken,
                    toSymbol = quote.toToken,
                    reason = errorMsg,
                    txHash = null
                )

                Result.failure(Exception(errorMsg))
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Auto-deposit to exchange
    // ═══════════════════════════════════════════════════════════════

    private suspend fun sendDepositToExchange(
        network: BlockchainNetwork,
        token: String,
        amount: String,
        depositAddress: String,
        fromAddress: String,
        memo: String? = null
    ): Result<String> {
        return try {
            val tokenAddress = SwapService.resolveTokenAddressPublic(network, token)
            val isNative = tokenAddress == null
                || tokenAddress == "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
                || tokenAddress == "NATIVE"
                || tokenAddress == "So11111111111111111111111111111111111111112"
                || tokenAddress == "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"


            if (network.isEvm && isNative) {
                val request = SendTransactionRequest(
                    network = network,
                    fromAddress = fromAddress,
                    toAddress = depositAddress,
                    amount = amount,
                    memo = memo
                )
                val result = WalletCoreService.sendTransaction(request)
                result.fold(
                    onSuccess = { },
                    onFailure = { e -> SecureLog.e("SwapDeposit", "Failed: ${e.message}", null) }
                )
                result.map { it.txHash }
            } else if (network.isEvm && !isNative && tokenAddress != null) {
                val decimals = SwapService.getTokenDecimals(token, network)
                val amountWei = SwapService.convertToWeiPublic(amount, decimals)
                val amountHex = BigInteger(amountWei).toString(16).padStart(64, '0')
                val toHex = depositAddress.removePrefix("0x").lowercase().padStart(64, '0')
                val transferData = "0xa9059cbb$toHex$amountHex"
                val result = PlatformWallet.sendRawTransaction(
                    network = network,
                    to = tokenAddress,
                    data = transferData,
                    value = "0",
                    gasLimit = "100000"
                )
                result.fold(
                    onSuccess = { },
                    onFailure = { e -> SecureLog.e("SwapDeposit", "Failed: ${e.message}", null) }
                )
                result
            } else {
                val request = SendTransactionRequest(
                    network = network,
                    fromAddress = fromAddress,
                    toAddress = depositAddress,
                    amount = amount,
                    memo = memo
                )
                val result = WalletCoreService.sendTransaction(request)
                result.fold(
                    onSuccess = { },
                    onFailure = { e -> SecureLog.e("SwapDeposit", "Failed: ${e.message}", null) }
                )
                result.map { it.txHash }
            }
        } catch (e: Exception) {
            SecureLog.e("SwapDeposit", "Exception: ${e.javaClass.simpleName}", null)
            Result.failure(Exception(String.format(generalGetString(MR.strings.wallet_swap_deposit_send_error), e.message ?: "")))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Confirmation polling
    // ═══════════════════════════════════════════════════════════════

    private fun startConfirmationPolling(swap: PendingSwap) {
        val txHash = swap.txHash ?: return
        pollingJobs[txHash]?.cancel()
        pollingJobs[txHash] = scope.launch {
            val conf = BlockchainService.pollTransactionConfirmation(
                txHash = txHash,
                network = swap.quote.network,
                requiredConfirmations = 1,
                timeoutMs = 300_000
            ) { update ->
                val s = _pendingSwaps.value.find { it.txHash == txHash } ?: return@pollTransactionConfirmation
                updatePending(s.copy(confirmations = update.confirmations))
            }

            val current = _pendingSwaps.value.find { it.txHash == txHash } ?: return@launch
            if (conf.status == TransactionStatus.CONFIRMED) {
                val completed = current.copy(
                    state = SwapLifecycleState.CONFIRMED,
                    confirmations = conf.confirmations,
                    completedAt = System.currentTimeMillis()
                )
                updatePending(completed)
                moveToHistory(completed)
                recordSwapToHistory(completed, TransactionStatus.CONFIRMED)

                NotificationService.notifySwapCompleted(
                    network = completed.quote.network,
                    fromSymbol = completed.quote.fromToken,
                    toSymbol = completed.quote.toToken,
                    fromAmount = completed.quote.fromAmount,
                    toAmount = completed.quote.toAmount,
                    txHash = txHash,
                    provider = completed.quote.provider
                )

                WalletCache.clearBalances()
            } else if (conf.status == TransactionStatus.FAILED) {
                val failed = current.copy(
                    state = SwapLifecycleState.FAILED,
                    errorMessage = generalGetString(MR.strings.wallet_swap_tx_reverted),
                    completedAt = System.currentTimeMillis()
                )
                updatePending(failed)
                moveToHistory(failed)
                recordSwapToHistory(failed, TransactionStatus.FAILED)

                NotificationService.notifySwapFailed(
                    network = failed.quote.network,
                    fromSymbol = failed.quote.fromToken,
                    toSymbol = failed.quote.toToken,
                    reason = generalGetString(MR.strings.wallet_swap_tx_reverted_on_chain),
                    txHash = txHash
                )
            }

            persistPendingSwaps()
            pollingJobs.remove(txHash)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Exchange status polling (ChangeNOW, etc.)
    // ═══════════════════════════════════════════════════════════════

    private fun startExchangeStatusPolling(swap: PendingSwap) {
        val exchangeId = swap.exchangeId ?: return
        pollingJobs[exchangeId]?.cancel()
        pollingJobs[exchangeId] = scope.launch {
            var pollIntervalMs = 10_000L
            val maxPollTime = 24 * 60 * 60 * 1000L
            val startTime = System.currentTimeMillis()

            while (isActive && (System.currentTimeMillis() - startTime) < maxPollTime) {
                delay(pollIntervalMs)
                val current = _pendingSwaps.value.find { it.exchangeId == exchangeId } ?: break

                val status = SwapService.getChangeNowExchangeStatus(exchangeId) ?: continue

                val newState = when (status.status) {
                    "new", "waiting" -> SwapLifecycleState.AWAITING_DEPOSIT
                    "confirming" -> SwapLifecycleState.CONFIRMING_DEPOSIT
                    "exchanging" -> SwapLifecycleState.EXCHANGING
                    "sending" -> SwapLifecycleState.SENDING
                    "finished" -> SwapLifecycleState.CONFIRMED
                    "failed" -> SwapLifecycleState.FAILED
                    "refunded" -> SwapLifecycleState.REFUNDED
                    "expired" -> SwapLifecycleState.EXPIRED
                    else -> current.state
                }

                val failReason = status.failureReason
                val updated = current.copy(
                    state = newState,
                    exchangeStatus = status.status,
                    payoutHash = status.payoutHash ?: current.payoutHash,
                    txHash = status.payinHash ?: current.txHash,
                    errorMessage = failReason ?: current.errorMessage,
                    completedAt = if (status.isTerminal) System.currentTimeMillis() else null
                )
                updatePending(updated)
                persistPendingSwaps()

                if (status.isTerminal) {
                    moveToHistory(updated)
                    recordSwapToHistory(updated,
                        if (status.isSuccess) TransactionStatus.CONFIRMED else TransactionStatus.FAILED
                    )

                    if (status.isSuccess) {
                        NotificationService.notifySwapCompleted(
                            network = updated.quote.network,
                            fromSymbol = updated.quote.fromToken,
                            toSymbol = updated.quote.toToken,
                            fromAmount = updated.quote.fromAmount,
                            toAmount = status.amountTo ?: updated.quote.toAmount,
                            txHash = status.payoutHash ?: exchangeId,
                            provider = updated.quote.provider
                        )
                        WalletCache.clearBalances()
                    } else {
                        NotificationService.notifySwapFailed(
                            network = updated.quote.network,
                            fromSymbol = updated.quote.fromToken,
                            toSymbol = updated.quote.toToken,
                            reason = failReason ?: "Exchange ${status.status}",
                            txHash = status.payinHash
                        )
                    }

                    pollingJobs.remove(exchangeId)
                    break
                }

                pollIntervalMs = when (newState) {
                    SwapLifecycleState.AWAITING_DEPOSIT -> 15_000L
                    SwapLifecycleState.CONFIRMING_DEPOSIT -> 10_000L
                    SwapLifecycleState.EXCHANGING -> 8_000L
                    SwapLifecycleState.SENDING -> 5_000L
                    else -> 15_000L
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Resume pending swaps on app start
    // ═══════════════════════════════════════════════════════════════

    fun resumePendingSwaps() {
        loadPersistedSwaps()
        _pendingSwaps.value.forEach { swap ->
            when {
                swap.exchangeId != null && !swap.state.let {
                    it == SwapLifecycleState.CONFIRMED || it == SwapLifecycleState.FAILED ||
                    it == SwapLifecycleState.REFUNDED || it == SwapLifecycleState.EXPIRED
                } -> startExchangeStatusPolling(swap)
                swap.state == SwapLifecycleState.PENDING && swap.txHash != null ->
                    startConfirmationPolling(swap)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Persistence helpers
    // ═══════════════════════════════════════════════════════════════

    private val PREFS_KEY = "pending_swaps_json"

    private fun persistPendingSwaps() {
        try {
            val active = _pendingSwaps.value.filter {
                it.state != SwapLifecycleState.CONFIRMED && it.state != SwapLifecycleState.FAILED &&
                it.state != SwapLifecycleState.REFUNDED && it.state != SwapLifecycleState.EXPIRED &&
                it.state != SwapLifecycleState.DROPPED
            }
            val serialized = json.encodeToString(active.map { it.toSerializable() })
            WalletPrefs.putString(PREFS_KEY, serialized)
        } catch (_: Exception) { }
    }

    private fun loadPersistedSwaps() {
        try {
            val raw = WalletPrefs.getString(PREFS_KEY) ?: return
            val list = json.decodeFromString<List<SerializableSwap>>(raw)
            val loaded = list.mapNotNull { it.toPendingSwap() }
            _pendingSwaps.value = loaded
        } catch (_: Exception) { }
    }

    // ── Persist swap to main transaction history ───────────────────

    private fun recordSwapToHistory(swap: PendingSwap, status: TransactionStatus) {
        val hash = swap.payoutHash ?: swap.txHash ?: swap.exchangeId ?: return
        val now = swap.completedAt ?: System.currentTimeMillis()

        val sendTx = WalletTransaction(
            id = swap.id,
            txHash = hash,
            network = swap.quote.network,
            type = TransactionType.SWAP,
            status = status,
            fromAddress = swap.fromAddress,
            toAddress = swap.depositAddress ?: "",
            amount = swap.quote.fromAmount,
            timestamp = now,
            memo = "${swap.quote.fromToken} → ${swap.quote.toToken} via ${swap.quote.provider}" +
                if (swap.exchangeId != null) " (ID: ${swap.exchangeId})" else "",
            tokenSymbol = swap.quote.fromToken
        )
        PlatformWallet.addTransaction(sendTx)

        if (status == TransactionStatus.CONFIRMED) {
            val destNetwork = swap.quote.destNetwork ?: swap.quote.network
            val receiveTx = WalletTransaction(
                id = "${swap.id}_receive",
                txHash = swap.payoutHash ?: hash,
                network = destNetwork,
                type = TransactionType.RECEIVE,
                status = TransactionStatus.CONFIRMED,
                fromAddress = swap.depositAddress ?: swap.quote.provider,
                toAddress = swap.fromAddress,
                amount = swap.quote.toAmount,
                timestamp = now + 1,
                memo = "Received from swap: ${swap.quote.fromToken} → ${swap.quote.toToken} via ${swap.quote.provider}",
                tokenSymbol = swap.quote.toToken
            )
            PlatformWallet.addTransaction(receiveTx)
        }
    }

    // ── State management ──────────────────────────────────────────

    private fun addPending(swap: PendingSwap) {
        _pendingSwaps.value = _pendingSwaps.value + swap
    }

    private fun updatePending(swap: PendingSwap) {
        _pendingSwaps.value = _pendingSwaps.value.map { if (it.id == swap.id) swap else it }
    }

    private fun moveToHistory(swap: PendingSwap) {
        _pendingSwaps.value = _pendingSwaps.value.filter { it.id != swap.id }
        val history = _swapHistory.value.toMutableList()
        history.add(0, swap)
        if (history.size > 100) history.subList(100, history.size).clear()
        _swapHistory.value = history
    }

    fun shutdown() {
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Data models
// ═══════════════════════════════════════════════════════════════════

enum class SwapLifecycleState {
    SUBMITTING,
    PENDING,
    AWAITING_DEPOSIT,
    CONFIRMING_DEPOSIT,
    EXCHANGING,
    SENDING,
    CONFIRMED,
    FAILED,
    REFUNDED,
    EXPIRED,
    DROPPED
}

data class PendingSwap(
    val id: String,
    val quote: SwapQuote,
    val fromAddress: String,
    val slippage: Double,
    val state: SwapLifecycleState,
    val txHash: String? = null,
    val confirmations: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val exchangeId: String? = null,
    val exchangeStatus: String? = null,
    val depositAddress: String? = null,
    val depositExtraId: String? = null,
    val payoutHash: String? = null
) {
    val isExchangeProvider: Boolean get() = quote.provider == "ChangeNOW"

    val displayState: String get() = when (state) {
        SwapLifecycleState.SUBMITTING -> if (isExchangeProvider) generalGetString(MR.strings.wallet_swap_state_creating_exchange) else generalGetString(MR.strings.wallet_swap_state_submitting)
        SwapLifecycleState.PENDING -> if (isExchangeProvider) generalGetString(MR.strings.wallet_swap_state_processing) else generalGetString(MR.strings.wallet_swap_state_pending)
        SwapLifecycleState.AWAITING_DEPOSIT -> generalGetString(MR.strings.wallet_swap_state_sending_deposit)
        SwapLifecycleState.CONFIRMING_DEPOSIT -> generalGetString(MR.strings.wallet_swap_state_confirming_deposit)
        SwapLifecycleState.EXCHANGING -> generalGetString(MR.strings.wallet_swap_state_exchanging)
        SwapLifecycleState.SENDING -> generalGetString(MR.strings.wallet_swap_state_sending)
        SwapLifecycleState.CONFIRMED -> generalGetString(MR.strings.wallet_swap_state_completed)
        SwapLifecycleState.FAILED -> generalGetString(MR.strings.wallet_swap_state_failed)
        SwapLifecycleState.REFUNDED -> generalGetString(MR.strings.wallet_swap_state_refunded)
        SwapLifecycleState.EXPIRED -> generalGetString(MR.strings.wallet_swap_state_expired)
        SwapLifecycleState.DROPPED -> generalGetString(MR.strings.wallet_swap_state_dropped)
    }

    fun toSerializable() = SerializableSwap(
        id = id,
        fromToken = quote.fromToken,
        toToken = quote.toToken,
        fromAmount = quote.fromAmount,
        toAmount = quote.toAmount,
        provider = quote.provider,
        network = quote.network.name,
        txHash = txHash,
        state = state.name,
        slippage = slippage,
        fromAddress = fromAddress,
        createdAt = createdAt,
        exchangeId = exchangeId,
        exchangeStatus = exchangeStatus,
        depositAddress = depositAddress,
        depositExtraId = depositExtraId,
        payoutHash = payoutHash
    )
}

data class AllowanceCheck(
    val currentAllowance: BigInteger,
    val requiredAmount: BigInteger,
    val needsApproval: Boolean
)

@Serializable
data class SerializableSwap(
    val id: String,
    val fromToken: String,
    val toToken: String,
    val fromAmount: String,
    val toAmount: String,
    val provider: String,
    val network: String,
    val txHash: String?,
    val state: String,
    val slippage: Double,
    val fromAddress: String,
    val createdAt: Long,
    val exchangeId: String? = null,
    val exchangeStatus: String? = null,
    val depositAddress: String? = null,
    val depositExtraId: String? = null,
    val payoutHash: String? = null
) {
    fun toPendingSwap(): PendingSwap? {
        val net = try { BlockchainNetwork.valueOf(network) } catch (_: Exception) { return null }
        val lifecycleState = try { SwapLifecycleState.valueOf(state) } catch (_: Exception) { return null }
        return PendingSwap(
            id = id,
            quote = SwapQuote(
                fromToken = fromToken, toToken = toToken,
                fromAmount = fromAmount, toAmount = toAmount,
                exchangeRate = 0.0, priceImpact = 0.0,
                estimatedGas = "0", network = net,
                provider = provider, expiresAt = 0
            ),
            fromAddress = fromAddress,
            slippage = slippage,
            state = lifecycleState,
            txHash = txHash,
            createdAt = createdAt,
            exchangeId = exchangeId,
            exchangeStatus = exchangeStatus,
            depositAddress = depositAddress,
            depositExtraId = depositExtraId,
            payoutHash = payoutHash
        )
    }
}

/**
 * Minimal preference helper – delegates to platform SharedPreferences via PlatformWallet.
 * In production, use an expect/actual wrapper. For now, backed by a simple in-memory map
 * that the Android initializer replaces with real SharedPreferences.
 */
object WalletPrefs {
    private var store: MutableMap<String, String> = mutableMapOf()

    var delegate: PrefsDelegate? = null

    fun putString(key: String, value: String) {
        delegate?.putString(key, value) ?: run { store[key] = value }
    }
    fun getString(key: String): String? {
        return delegate?.getString(key) ?: store[key]
    }
    fun remove(key: String) {
        delegate?.remove(key) ?: run { store.remove(key) }
    }

    interface PrefsDelegate {
        fun putString(key: String, value: String)
        fun getString(key: String): String?
        fun remove(key: String)
    }
}
