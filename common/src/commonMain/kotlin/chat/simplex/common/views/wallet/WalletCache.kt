package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * WalletCache - In-memory caching layer for wallet data
 * Reduces RPC calls and improves performance
 */
object WalletCache {
    
    // Cache TTLs (in milliseconds)
    private const val BALANCE_TTL = 30_000L        // 30 seconds
    private const val PRICE_TTL = 60_000L          // 1 minute
    private const val TOKEN_BALANCE_TTL = 30_000L  // 30 seconds
    private const val GAS_PRICE_TTL = 15_000L      // 15 seconds
    private const val TRANSACTION_TTL = 300_000L   // 5 minutes
    
    /**
     * Generic cached value wrapper
     */
    data class CachedValue<T>(
        val value: T,
        val timestamp: Long = System.currentTimeMillis(),
        val ttlMs: Long
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() - timestamp < ttlMs
        fun age(): Long = System.currentTimeMillis() - timestamp
    }
    
    // Cache stores
    private val balanceCache = ConcurrentHashMap<String, CachedValue<String>>()
    private val tokenBalanceCache = ConcurrentHashMap<String, CachedValue<String>>()
    private val priceCache = ConcurrentHashMap<String, CachedValue<Double>>()
    private val gasPriceCache = ConcurrentHashMap<BlockchainNetwork, CachedValue<GasPriceData>>()
    private val transactionCache = ConcurrentHashMap<String, CachedValue<WalletTransaction>>()
    
    /**
     * Gas price data including EIP-1559 fields
     */
    data class GasPriceData(
        val legacy: Long,           // Legacy gas price in Gwei
        val baseFee: Long?,         // EIP-1559 base fee
        val maxPriorityFee: Long?,  // EIP-1559 max priority fee
        val maxFee: Long?,          // EIP-1559 max fee
        val supportsEIP1559: Boolean
    )
    
    // ============ Balance Cache ============
    
    /**
     * Get cached balance
     */
    fun getBalance(address: String, network: BlockchainNetwork): String? {
        val key = "${network.name}:$address"
        return balanceCache[key]?.takeIf { it.isValid() }?.value
    }
    
    /**
     * Set balance in cache
     */
    fun setBalance(address: String, network: BlockchainNetwork, balance: String) {
        val key = "${network.name}:$address"
        balanceCache[key] = CachedValue(balance, ttlMs = BALANCE_TTL)
    }
    
    /**
     * Get or fetch balance with cache
     */
    suspend fun getOrFetchBalance(
        address: String,
        network: BlockchainNetwork,
        fetcher: suspend () -> String
    ): String {
        getBalance(address, network)?.let { return it }
        
        val balance = fetcher()
        setBalance(address, network, balance)
        return balance
    }
    
    // ============ Token Balance Cache ============
    
    /**
     * Get cached token balance
     */
    fun getTokenBalance(tokenAddress: String, walletAddress: String, network: BlockchainNetwork): String? {
        val key = "${network.name}:$tokenAddress:$walletAddress"
        return tokenBalanceCache[key]?.takeIf { it.isValid() }?.value
    }
    
    /**
     * Set token balance in cache
     */
    fun setTokenBalance(tokenAddress: String, walletAddress: String, network: BlockchainNetwork, balance: String) {
        val key = "${network.name}:$tokenAddress:$walletAddress"
        tokenBalanceCache[key] = CachedValue(balance, ttlMs = TOKEN_BALANCE_TTL)
    }
    
    // ============ Price Cache ============
    
    /**
     * Get cached price
     */
    fun getPrice(symbol: String): Double? {
        return priceCache[symbol.uppercase()]?.takeIf { it.isValid() }?.value
    }
    
    /**
     * Set price in cache
     */
    fun setPrice(symbol: String, price: Double) {
        priceCache[symbol.uppercase()] = CachedValue(price, ttlMs = PRICE_TTL)
    }
    
    /**
     * Set multiple prices at once
     */
    fun setPrices(prices: Map<String, Double>) {
        val timestamp = System.currentTimeMillis()
        prices.forEach { (symbol, price) ->
            priceCache[symbol.uppercase()] = CachedValue(price, timestamp, PRICE_TTL)
        }
    }
    
    // ============ Gas Price Cache ============
    
    /**
     * Get cached gas price
     */
    fun getGasPrice(network: BlockchainNetwork): GasPriceData? {
        return gasPriceCache[network]?.takeIf { it.isValid() }?.value
    }
    
    /**
     * Set gas price in cache
     */
    fun setGasPrice(network: BlockchainNetwork, data: GasPriceData) {
        gasPriceCache[network] = CachedValue(data, ttlMs = GAS_PRICE_TTL)
    }
    
    // ============ Transaction Cache ============
    
    /**
     * Get cached transaction
     */
    fun getTransaction(txHash: String): WalletTransaction? {
        return transactionCache[txHash]?.takeIf { it.isValid() }?.value
    }
    
    /**
     * Set transaction in cache
     */
    fun setTransaction(tx: WalletTransaction) {
        transactionCache[tx.txHash] = CachedValue(tx, ttlMs = TRANSACTION_TTL)
    }
    
    // ============ Cache Management ============
    
    /**
     * Clear all caches
     */
    fun clearAll() {
        balanceCache.clear()
        tokenBalanceCache.clear()
        priceCache.clear()
        gasPriceCache.clear()
        transactionCache.clear()
    }
    
    /**
     * Clear balance caches only
     */
    fun clearBalances() {
        balanceCache.clear()
        tokenBalanceCache.clear()
    }
    
    /**
     * Clear expired entries from all caches
     */
    fun cleanupExpired() {
        balanceCache.entries.removeIf { !it.value.isValid() }
        tokenBalanceCache.entries.removeIf { !it.value.isValid() }
        priceCache.entries.removeIf { !it.value.isValid() }
        gasPriceCache.entries.removeIf { !it.value.isValid() }
        transactionCache.entries.removeIf { !it.value.isValid() }
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            balanceCacheSize = balanceCache.size,
            tokenBalanceCacheSize = tokenBalanceCache.size,
            priceCacheSize = priceCache.size,
            gasPriceCacheSize = gasPriceCache.size,
            transactionCacheSize = transactionCache.size
        )
    }
    
    data class CacheStats(
        val balanceCacheSize: Int,
        val tokenBalanceCacheSize: Int,
        val priceCacheSize: Int,
        val gasPriceCacheSize: Int,
        val transactionCacheSize: Int
    ) {
        val totalSize: Int get() = balanceCacheSize + tokenBalanceCacheSize + 
                                   priceCacheSize + gasPriceCacheSize + transactionCacheSize
    }
}

/**
 * EIP-1559 Fee Estimate
 */
data class EIP1559FeeEstimate(
    val network: BlockchainNetwork,
    val baseFee: Long,           // Current base fee in Gwei
    val maxPriorityFee: Long,    // Suggested priority fee in Gwei
    val maxFee: Long,            // Max fee (baseFee * 2 + priorityFee) in Gwei
    val gasLimit: Long,
    val estimatedCostWei: Long,
    val estimatedCostEth: String,
    val estimatedCostUsd: Double,
    val estimatedTime: String
) {
    companion object {
        /**
         * Create fee estimates for slow, normal, fast
         */
        fun createEstimates(
            network: BlockchainNetwork,
            baseFee: Long,
            gasLimit: Long = 21000,
            ethPrice: Double = 2350.0
        ): Triple<EIP1559FeeEstimate, EIP1559FeeEstimate, EIP1559FeeEstimate> {
            // Priority fees: slow=1, normal=2, fast=5 Gwei
            val slow = create(network, baseFee, 1, gasLimit, ethPrice, "~5 min")
            val normal = create(network, baseFee, 2, gasLimit, ethPrice, "~1 min")
            val fast = create(network, baseFee, 5, gasLimit, ethPrice, "~15 sec")
            
            return Triple(slow, normal, fast)
        }
        
        private fun create(
            network: BlockchainNetwork,
            baseFee: Long,
            priorityFee: Long,
            gasLimit: Long,
            ethPrice: Double,
            time: String
        ): EIP1559FeeEstimate {
            val maxFee = baseFee * 2 + priorityFee
            val costWei = maxFee * gasLimit * 1_000_000_000 // Gwei to Wei
            val costEth = costWei.toDouble() / 1e18
            
            return EIP1559FeeEstimate(
                network = network,
                baseFee = baseFee,
                maxPriorityFee = priorityFee,
                maxFee = maxFee,
                gasLimit = gasLimit,
                estimatedCostWei = costWei,
                estimatedCostEth = String.format("%.8f", costEth),
                estimatedCostUsd = costEth * ethPrice,
                estimatedTime = time
            )
        }
    }
}

