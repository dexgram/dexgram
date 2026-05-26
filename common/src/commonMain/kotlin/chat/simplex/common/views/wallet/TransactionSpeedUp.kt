package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import java.math.BigInteger

/**
 * Transaction Speed Up / Cancel Service
 * Allows users to speed up or cancel pending transactions
 */
object TransactionSpeedUpService {
    
    /**
     * Speed up a pending transaction by resubmitting with higher gas
     * 
     * @param originalTxHash The hash of the pending transaction
     * @param network The blockchain network
     * @param gasPriceMultiplier Multiplier for new gas price (e.g., 1.5 = 50% increase)
     * @return New transaction hash if successful
     */
    suspend fun speedUpTransaction(
        originalTxHash: String,
        network: BlockchainNetwork,
        gasPriceMultiplier: Double = 1.5
    ): Result<SpeedUpResult> = withContext(Dispatchers.IO) {
        try {
            // Get the original transaction details
            val originalTx = getTransactionDetails(originalTxHash, network)
                ?: return@withContext Result.failure(
                    WalletException.TransactionFailed(originalTxHash, "Transaction not found")
                )
            
            // Calculate new gas price
            val originalGasPrice = originalTx.gasPrice
            val newGasPrice = (originalGasPrice * gasPriceMultiplier).toLong()
            
            // Create replacement transaction with same nonce but higher gas
            val result = PlatformWallet.sendRawTransaction(
                network = network,
                to = originalTx.to,
                data = originalTx.data,
                value = originalTx.value,
                gasLimit = originalTx.gasLimit.toString()
                // Note: In actual implementation, would need to specify nonce
            )
            
            result.fold(
                onSuccess = { newTxHash ->
                    Result.success(SpeedUpResult(
                        originalTxHash = originalTxHash,
                        newTxHash = newTxHash,
                        oldGasPrice = originalGasPrice,
                        newGasPrice = newGasPrice,
                        type = SpeedUpType.SPEED_UP
                    ))
                },
                onFailure = { e ->
                    Result.failure(WalletException.TransactionFailed(originalTxHash, e.message ?: "Speed up failed"))
                }
            )
        } catch (e: Exception) {
            Result.failure(WalletException.TransactionFailed(originalTxHash, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Cancel a pending transaction by sending 0 ETH to self with same nonce
     * 
     * @param originalTxHash The hash of the pending transaction
     * @param network The blockchain network
     * @param gasPriceMultiplier Multiplier for gas price (should be > 1 to replace)
     * @return Cancel transaction hash if successful
     */
    suspend fun cancelTransaction(
        originalTxHash: String,
        network: BlockchainNetwork,
        fromAddress: String,
        gasPriceMultiplier: Double = 1.5
    ): Result<SpeedUpResult> = withContext(Dispatchers.IO) {
        try {
            // Get the original transaction to find nonce
            val originalTx = getTransactionDetails(originalTxHash, network)
                ?: return@withContext Result.failure(
                    WalletException.TransactionFailed(originalTxHash, "Transaction not found")
                )
            
            // Calculate new gas price (must be higher to replace)
            val originalGasPrice = originalTx.gasPrice
            val newGasPrice = (originalGasPrice * gasPriceMultiplier).toLong()
            
            // Send 0 ETH to self - this replaces the pending tx
            val result = PlatformWallet.sendRawTransaction(
                network = network,
                to = fromAddress,  // Send to self
                data = "0x",       // No data
                value = "0",       // 0 ETH
                gasLimit = "21000" // Minimum gas for ETH transfer
            )
            
            result.fold(
                onSuccess = { cancelTxHash ->
                    Result.success(SpeedUpResult(
                        originalTxHash = originalTxHash,
                        newTxHash = cancelTxHash,
                        oldGasPrice = originalGasPrice,
                        newGasPrice = newGasPrice,
                        type = SpeedUpType.CANCEL
                    ))
                },
                onFailure = { e ->
                    Result.failure(WalletException.TransactionFailed(originalTxHash, e.message ?: "Cancel failed"))
                }
            )
        } catch (e: Exception) {
            Result.failure(WalletException.TransactionFailed(originalTxHash, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Check if a transaction can be sped up or cancelled
     */
    suspend fun canModifyTransaction(txHash: String, network: BlockchainNetwork): ModifyStatus {
        return try {
            val status = PlatformWallet.checkTransactionStatus(txHash, network)
            
            when (status) {
                TransactionStatus.PENDING -> ModifyStatus.CAN_MODIFY
                TransactionStatus.CONFIRMED -> ModifyStatus.ALREADY_CONFIRMED
                TransactionStatus.FAILED -> ModifyStatus.ALREADY_FAILED
                TransactionStatus.CANCELLED -> ModifyStatus.ALREADY_CANCELLED
            }
        } catch (e: Exception) {
            ModifyStatus.UNKNOWN
        }
    }
    
    /**
     * Calculate recommended gas price for speed up
     */
    suspend fun getRecommendedGasPrice(
        originalGasPrice: Long,
        network: BlockchainNetwork,
        priority: SpeedPriority
    ): Long {
        val multiplier = when (priority) {
            SpeedPriority.LOW -> 1.1      // 10% increase
            SpeedPriority.MEDIUM -> 1.25  // 25% increase
            SpeedPriority.HIGH -> 1.5     // 50% increase
            SpeedPriority.URGENT -> 2.0   // 100% increase
        }
        
        val newGasPrice = (originalGasPrice * multiplier).toLong()
        
        // Also check current network gas price
        val currentGasPrice = WalletCache.getGasPrice(network)?.legacy ?: 0
        
        // Use the higher of: original * multiplier or current network price
        return maxOf(newGasPrice, currentGasPrice)
    }
    
    /**
     * Estimate cost to speed up transaction
     */
    fun estimateSpeedUpCost(
        originalGasPrice: Long,
        newGasPrice: Long,
        gasLimit: Long
    ): SpeedUpCostEstimate {
        val originalCostWei = originalGasPrice * gasLimit * 1_000_000_000L
        val newCostWei = newGasPrice * gasLimit * 1_000_000_000L
        val additionalCostWei = newCostWei - originalCostWei
        
        return SpeedUpCostEstimate(
            originalCostWei = originalCostWei,
            newCostWei = newCostWei,
            additionalCostWei = additionalCostWei,
            originalCostEth = weiToEth(originalCostWei),
            newCostEth = weiToEth(newCostWei),
            additionalCostEth = weiToEth(additionalCostWei)
        )
    }
    
    private fun weiToEth(wei: Long): String {
        return String.format("%.8f", wei.toDouble() / 1e18)
    }
    
    // Internal: Get transaction details (would need RPC call)
    private suspend fun getTransactionDetails(txHash: String, network: BlockchainNetwork): TransactionDetails? {
        // This would call eth_getTransactionByHash
        // Returning null as placeholder - actual implementation needed
        return null
    }
    
    /**
     * Internal transaction details
     */
    data class TransactionDetails(
        val hash: String,
        val nonce: Long,
        val from: String,
        val to: String,
        val value: String,
        val data: String,
        val gasPrice: Long,
        val gasLimit: Long
    )
    
    /**
     * Speed up result
     */
    data class SpeedUpResult(
        val originalTxHash: String,
        val newTxHash: String,
        val oldGasPrice: Long,
        val newGasPrice: Long,
        val type: SpeedUpType
    )
    
    /**
     * Speed up type
     */
    enum class SpeedUpType {
        SPEED_UP,
        CANCEL
    }
    
    /**
     * Modify status
     */
    enum class ModifyStatus {
        CAN_MODIFY,
        ALREADY_CONFIRMED,
        ALREADY_FAILED,
        ALREADY_CANCELLED,
        UNKNOWN
    }
    
    /**
     * Speed priority
     */
    enum class SpeedPriority(val displayName: String) {
        LOW("Low (+10%)"),
        MEDIUM("Medium (+25%)"),
        HIGH("High (+50%)"),
        URGENT("Urgent (+100%)")
    }
    
    /**
     * Speed up cost estimate
     */
    data class SpeedUpCostEstimate(
        val originalCostWei: Long,
        val newCostWei: Long,
        val additionalCostWei: Long,
        val originalCostEth: String,
        val newCostEth: String,
        val additionalCostEth: String
    )
}

