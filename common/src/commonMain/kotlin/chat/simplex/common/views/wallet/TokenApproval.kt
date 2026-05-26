package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.math.BigInteger

/**
 * Token Approval Management - Track and manage ERC20 token approvals
 */
object TokenApprovalService {
    
    // Known spender addresses (DEX routers, etc.)
    private val knownSpenders = mapOf(
        // Ethereum
        "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D".lowercase() to SpenderInfo("Uniswap V2 Router", "Uniswap", false),
        "0xE592427A0AEce92De3Edee1F18E0157C05861564".lowercase() to SpenderInfo("Uniswap V3 Router", "Uniswap", false),
        "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45".lowercase() to SpenderInfo("Uniswap Universal Router", "Uniswap", false),
        "0xd9e1cE17f2641f24aE83637ab66a2cca9C378B9F".lowercase() to SpenderInfo("SushiSwap Router", "SushiSwap", false),
        "0xDef1C0ded9bec7F1a1670819833240f027b25EfF".lowercase() to SpenderInfo("0x Exchange Proxy", "0x", false),
        "0x1111111254EEB25477B68fb85Ed929f73A960582".lowercase() to SpenderInfo("1inch Router v5", "1inch", false),
        "0x11111112542D85B3EF69AE05771c2dCCff4fAa26".lowercase() to SpenderInfo("1inch Router v4", "1inch", false),
        "0x7D2768dE32b0b80b7a3454c06BdAc94A69DDc7A9".lowercase() to SpenderInfo("Aave Lending Pool V2", "Aave", false),
        "0x87870Bca3F3fD6335C3F4ce8392D69350B4fA4E2".lowercase() to SpenderInfo("Aave Pool V3", "Aave", false),
        
        // BSC
        "0x10ED43C718714eb63d5aA57B78B54704E256024E".lowercase() to SpenderInfo("PancakeSwap Router", "PancakeSwap", false),
        "0x13f4EA83D0bd40E75C8222255bc855a974568Dd4".lowercase() to SpenderInfo("PancakeSwap V3 Router", "PancakeSwap", false),
        
        // Polygon
        "0xa5E0829CaCEd8fFDD4De3c43696c57F7D7A678ff".lowercase() to SpenderInfo("QuickSwap Router", "QuickSwap", false),
        
        // Arbitrum
        "0x1b02dA8Cb0d097eB8D57A175b88c7D8b47997506".lowercase() to SpenderInfo("SushiSwap Router", "SushiSwap", false),
        
        // Optimism
        "0x9c12939390052919af3155f41bf4160fd3666a6f".lowercase() to SpenderInfo("Velodrome Router", "Velodrome", false),
        
        // Avalanche
        "0x60aE616a2155Ee3d9A68541Ba4544862310933d4".lowercase() to SpenderInfo("TraderJoe Router", "TraderJoe", false)
    )
    
    /**
     * Spender information
     */
    data class SpenderInfo(
        val name: String,
        val protocol: String,
        val isRisky: Boolean
    )
    
    /**
     * Token allowance data
     */
    @Serializable
    data class TokenAllowance(
        val tokenAddress: String,
        val tokenSymbol: String,
        val tokenDecimals: Int,
        val spenderAddress: String,
        val spenderName: String?,
        val allowance: String,        // Raw allowance value
        val allowanceFormatted: String, // Human readable
        val isUnlimited: Boolean,
        val network: BlockchainNetwork,
        val lastChecked: Long = System.currentTimeMillis()
    )
    
    // Maximum uint256 value (unlimited approval)
    private val MAX_UINT256 = BigInteger("2").pow(256).minus(BigInteger.ONE)
    private val UNLIMITED_THRESHOLD = MAX_UINT256.divide(BigInteger.TEN) // >10% of max = unlimited
    
    /**
     * Get spender info if known
     */
    fun getSpenderInfo(spenderAddress: String): SpenderInfo? {
        return knownSpenders[spenderAddress.lowercase()]
    }
    
    /**
     * Check if an allowance is "unlimited"
     */
    fun isUnlimitedAllowance(allowance: BigInteger): Boolean {
        return allowance >= UNLIMITED_THRESHOLD
    }
    
    /**
     * Format allowance for display
     */
    fun formatAllowance(allowance: BigInteger, decimals: Int, symbol: String): String {
        return if (isUnlimitedAllowance(allowance)) {
            "Unlimited $symbol"
        } else {
            val divisor = BigInteger.TEN.pow(decimals)
            val formatted = allowance.toBigDecimal().divide(divisor.toBigDecimal(), 4, java.math.RoundingMode.DOWN)
            "${formatted.stripTrailingZeros().toPlainString()} $symbol"
        }
    }
    
    /**
     * Build approve call data
     */
    fun buildApproveCallData(spenderAddress: String, amount: BigInteger): String {
        // approve(address,uint256) = 0x095ea7b3
        val spenderPadded = spenderAddress.removePrefix("0x").lowercase().padStart(64, '0')
        val amountHex = amount.toString(16).padStart(64, '0')
        return "0x095ea7b3$spenderPadded$amountHex"
    }
    
    /**
     * Build unlimited approve call data
     */
    fun buildUnlimitedApproveCallData(spenderAddress: String): String {
        return buildApproveCallData(spenderAddress, MAX_UINT256)
    }
    
    /**
     * Build revoke (set to 0) call data
     */
    fun buildRevokeCallData(spenderAddress: String): String {
        return buildApproveCallData(spenderAddress, BigInteger.ZERO)
    }
    
    /**
     * Build allowance check call data
     */
    fun buildAllowanceCallData(ownerAddress: String, spenderAddress: String): String {
        // allowance(address,address) = 0xdd62ed3e
        val ownerPadded = ownerAddress.removePrefix("0x").lowercase().padStart(64, '0')
        val spenderPadded = spenderAddress.removePrefix("0x").lowercase().padStart(64, '0')
        return "0xdd62ed3e$ownerPadded$spenderPadded"
    }
    
    /**
     * Parse allowance from RPC response
     */
    fun parseAllowanceResponse(hexResponse: String): BigInteger {
        return try {
            val cleaned = hexResponse.removePrefix("0x")
            if (cleaned.isEmpty() || cleaned == "0") {
                BigInteger.ZERO
            } else {
                BigInteger(cleaned, 16)
            }
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }
    
    /**
     * Check if approval is needed for a swap/transaction
     */
    fun needsApproval(currentAllowance: BigInteger, requiredAmount: BigInteger): Boolean {
        return currentAllowance < requiredAmount
    }
    
    /**
     * Get recommended approval amount
     * Returns either exact amount or unlimited based on preferences
     */
    fun getRecommendedApproval(requiredAmount: BigInteger, useUnlimited: Boolean = true): BigInteger {
        return if (useUnlimited) MAX_UINT256 else requiredAmount
    }
    
    /**
     * Revoke approval (set to 0)
     */
    suspend fun revokeApproval(
        tokenAddress: String,
        spenderAddress: String,
        network: BlockchainNetwork
    ): Result<String> {
        return try {
            val revokeData = buildRevokeCallData(spenderAddress)
            
            PlatformWallet.sendRawTransaction(
                network = network,
                to = tokenAddress,
                data = revokeData,
                value = "0",
                gasLimit = "60000"
            )
        } catch (e: Exception) {
            Result.failure(WalletException.ApprovalFailed(
                token = tokenAddress,
                spender = spenderAddress,
                reason = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Approve token for spender
     */
    suspend fun approveToken(
        tokenAddress: String,
        spenderAddress: String,
        amount: BigInteger,
        network: BlockchainNetwork
    ): Result<String> {
        return try {
            val approveData = buildApproveCallData(spenderAddress, amount)
            
            PlatformWallet.sendRawTransaction(
                network = network,
                to = tokenAddress,
                data = approveData,
                value = "0",
                gasLimit = "60000"
            )
        } catch (e: Exception) {
            Result.failure(WalletException.ApprovalFailed(
                token = tokenAddress,
                spender = spenderAddress,
                reason = e.message ?: "Unknown error"
            ))
        }
    }
}

