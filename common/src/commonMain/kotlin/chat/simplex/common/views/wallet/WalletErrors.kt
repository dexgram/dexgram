package chat.simplex.common.views.wallet

import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString

/**
 * Sealed class hierarchy for wallet-specific errors
 * Provides type-safe error handling across the wallet module
 */
sealed class WalletException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Insufficient balance for transaction
     */
    data class InsufficientBalance(
        val required: String,
        val available: String,
        val symbol: String
    ) : WalletException("Insufficient balance: need $required $symbol, have $available $symbol")
    
    /**
     * Invalid wallet address
     */
    data class InvalidAddress(
        val address: String,
        val network: BlockchainNetwork? = null
    ) : WalletException("Invalid address${network?.let { " for ${it.displayName}" } ?: ""}: $address")
    
    /**
     * Network/RPC error
     */
    data class NetworkError(
        val network: BlockchainNetwork,
        val details: String,
        override val cause: Throwable? = null
    ) : WalletException("Network error on ${network.displayName}: $details", cause)
    
    /**
     * Transaction failed to execute
     */
    data class TransactionFailed(
        val txHash: String?,
        val reason: String
    ) : WalletException("Transaction failed${txHash?.let { " ($it)" } ?: ""}: $reason")
    
    /**
     * Transaction was rejected
     */
    data class TransactionRejected(
        val reason: String
    ) : WalletException("Transaction rejected: $reason")
    
    /**
     * Wallet is locked
     */
    object WalletLocked : WalletException(generalGetString(MR.strings.wallet_error_wallet_locked_auth))
    
    /**
     * Invalid mnemonic phrase
     */
    data class InvalidMnemonic(
        val reason: String = generalGetString(MR.strings.wallet_error_invalid_mnemonic)
    ) : WalletException(reason)
    
    /**
     * Token not found
     */
    data class TokenNotFound(
        val symbol: String,
        val network: BlockchainNetwork
    ) : WalletException("Token $symbol not found on ${network.displayName}")
    
    /**
     * Swap failed
     */
    data class SwapFailed(
        val fromToken: String,
        val toToken: String,
        val reason: String
    ) : WalletException("Swap from $fromToken to $toToken failed: $reason")
    
    /**
     * Approval failed
     */
    data class ApprovalFailed(
        val token: String,
        val spender: String,
        val reason: String
    ) : WalletException("Failed to approve $token for $spender: $reason")
    
    /**
     * Gas estimation failed
     */
    data class GasEstimationFailed(
        val network: BlockchainNetwork,
        val reason: String
    ) : WalletException("Gas estimation failed on ${network.displayName}: $reason")
    
    /**
     * Contract call failed
     */
    data class ContractCallFailed(
        val contract: String,
        val method: String,
        val reason: String
    ) : WalletException("Contract call to $contract.$method failed: $reason")
    
    /**
     * Biometric authentication failed
     */
    data class BiometricAuthFailed(
        val reason: String
    ) : WalletException("Biometric authentication failed: $reason")
    
    /**
     * Unknown error
     */
    data class Unknown(
        val details: String,
        override val cause: Throwable? = null
    ) : WalletException("Unknown wallet error: $details", cause)
}

/**
 * Extension to convert generic exceptions to WalletException
 */
fun Throwable.toWalletException(network: BlockchainNetwork? = null): WalletException {
    return when (this) {
        is WalletException -> this
        else -> {
            val message = this.message ?: "Unknown error"
            when {
                message.contains("insufficient funds", ignoreCase = true) ->
                    WalletException.InsufficientBalance("unknown", "unknown", "ETH")
                message.contains("nonce too low", ignoreCase = true) ->
                    WalletException.TransactionFailed(null, "Nonce too low - transaction may already be processed")
                message.contains("gas too low", ignoreCase = true) ->
                    WalletException.TransactionFailed(null, "Gas limit too low")
                message.contains("rejected", ignoreCase = true) ->
                    WalletException.TransactionRejected(message)
                message.contains("timeout", ignoreCase = true) || message.contains("connection", ignoreCase = true) ->
                    WalletException.NetworkError(network ?: BlockchainNetwork.ETHEREUM, message, this)
                else -> WalletException.Unknown(message, this)
            }
        }
    }
}

fun humanReadableError(rawMessage: String?): String {
    val msg = rawMessage ?: return generalGetString(MR.strings.wallet_error_generic)
    val lower = msg.lowercase()
    return when {
        lower.contains("insufficient funds") || lower.contains("insufficient balance") ->
            generalGetString(MR.strings.wallet_error_human_insufficient)
        lower.contains("gas required exceeds allowance") || lower.contains("out of gas") ->
            generalGetString(MR.strings.wallet_error_human_out_of_gas)
        lower.contains("nonce too low") || lower.contains("already known") ->
            generalGetString(MR.strings.wallet_error_human_nonce_conflict)
        lower.contains("execution reverted") || lower.contains("transaction reverted") ->
            generalGetString(MR.strings.wallet_error_human_reverted)
        lower.contains("replacement transaction underpriced") ->
            generalGetString(MR.strings.wallet_error_human_underpriced)
        lower.contains("timeout") || lower.contains("timed out") ->
            generalGetString(MR.strings.wallet_error_human_timeout)
        lower.contains("unable to resolve host") || lower.contains("no internet") || lower.contains("unreachable") ->
            generalGetString(MR.strings.wallet_error_human_no_internet)
        lower.contains("connection refused") || lower.contains("connection reset") ->
            generalGetString(MR.strings.wallet_error_human_connection_refused)
        lower.contains("invalid address") || lower.contains("bad address") ->
            generalGetString(MR.strings.wallet_error_human_invalid_address)
        lower.contains("approval failed") || lower.contains("approve") ->
            generalGetString(MR.strings.wallet_error_human_approval)
        lower.contains("slippage") ->
            generalGetString(MR.strings.wallet_error_human_slippage)
        lower.contains("rate limit") || lower.contains("429") || lower.contains("too many requests") ->
            generalGetString(MR.strings.wallet_error_human_rate_limit)
        lower.contains("reading transaction object failed") ->
            generalGetString(MR.strings.wallet_error_human_build_tx)
        lower.contains("no route found") || lower.contains("no quotes") ->
            generalGetString(MR.strings.wallet_error_human_no_route)
        lower.contains("authentication required") || lower.contains("wallet is locked") ->
            generalGetString(MR.strings.wallet_error_human_locked)
        lower.contains("server error") || lower.contains("500") || lower.contains("502") || lower.contains("503") ->
            generalGetString(MR.strings.wallet_error_human_server)
        lower.contains("failed to fetch") || lower.contains("fetch failed") ->
            generalGetString(MR.strings.wallet_error_human_fetch_failed)
        msg.length > 120 -> "${msg.take(100)}... (see logs for details)"
        else -> msg
    }
}
