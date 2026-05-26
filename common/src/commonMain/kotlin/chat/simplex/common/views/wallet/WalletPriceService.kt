package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time price service for wallet tokens
 * Uses CoinGecko API for live prices
 */
object WalletPriceService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Price cache: symbol -> price in USD
    private val priceCache = ConcurrentHashMap<String, Double>()
    
    // Last update timestamp
    private var lastUpdate: Long = 0
    private const val CACHE_TTL = 60_000L // 1 minute cache
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Prices flow for reactive updates
    private val _pricesFlow = MutableStateFlow<Map<String, Double>>(emptyMap())
    val pricesFlow: StateFlow<Map<String, Double>> = _pricesFlow.asStateFlow()
    
    // Symbol to CoinGecko ID mapping
    private val symbolToId = mapOf(
        // Native coins
        "ETH" to "ethereum",
        "BTC" to "bitcoin",
        "BNB" to "binancecoin",
        "MATIC" to "matic-network",
        "POL" to "polygon-ecosystem-token",
        "AVAX" to "avalanche-2",
        "SOL" to "solana",
        "ADA" to "cardano",
        "DOGE" to "dogecoin",
        "LTC" to "litecoin",
        "XRP" to "ripple",
        "TRX" to "tron",
        "DOT" to "polkadot",
        "ATOM" to "cosmos",
        "NEAR" to "near",
        "ARB" to "arbitrum",
        "OP" to "optimism",
        
        // Stablecoins
        "USDT" to "tether",
        "USDC" to "usd-coin",
        "DAI" to "dai",
        "BUSD" to "binance-usd",
        "TUSD" to "true-usd",
        
        // Wrapped tokens
        "WETH" to "weth",
        "WBTC" to "wrapped-bitcoin",
        "BTCB" to "bitcoin-bep2",
        "WMATIC" to "wmatic",
        "WAVAX" to "wrapped-avax",
        "WBNB" to "wbnb",
        
        // DeFi & others
        "LINK" to "chainlink",
        "UNI" to "uniswap",
        "AAVE" to "aave",
        "MKR" to "maker",
        "CRV" to "curve-dao-token",
        "LDO" to "lido-dao",
        "CAKE" to "pancakeswap-token",
        "SUSHI" to "sushi",
        "COMP" to "compound-governance-token",
        "SNX" to "synthetix-network-token",
        "1INCH" to "1inch",
        "BAL" to "balancer",
        "YFI" to "yearn-finance",
        "GRT" to "the-graph",
        "ENS" to "ethereum-name-service",
        "APE" to "apecoin",
        "SAND" to "the-sandbox",
        "MANA" to "decentraland",
        "AXS" to "axie-infinity",
        
        // Meme coins
        "SHIB" to "shiba-inu",
        "PEPE" to "pepe",
        "FLOKI" to "floki",
        "BONK" to "bonk",
        "WIF" to "dogwifcoin",
        
        // Others
        "FET" to "fetch-ai",
        "RNDR" to "render-token",
        "INJ" to "injective-protocol",
        "IMX" to "immutable-x",
        "BLUR" to "blur",
        "STX" to "blockstack",
        "SUI" to "sui",
        "SEI" to "sei-network",
        "TIA" to "celestia",
        "JUP" to "jupiter",
        "PYTH" to "pyth-network",
        "FIL" to "filecoin",
        "NEAR" to "near",
        "STG" to "stargate-finance",
        "WLD" to "worldcoin-wld",
        "ENA" to "ethena",
        "ONDO" to "ondo-finance",
        "PENDLE" to "pendle",
        "GMX" to "gmx",
        "SAND" to "the-sandbox",
        "MANA" to "decentraland",
        "AXS" to "axie-infinity",
        "YFI" to "yearn-finance",
        "RPL" to "rocket-pool",
        "FRAX" to "frax",
        "TUSD" to "true-usd",

        // Liquid staking
        "stETH" to "staked-ether",
        "rETH" to "rocket-pool-eth",
        "cbETH" to "coinbase-wrapped-staked-eth",
        "stMATIC" to "lido-staked-matic",
        "MSOL" to "msol",
        "JITOSOL" to "jito-governance-token",

        // Solana ecosystem
        "RAY" to "raydium",
        "ORCA" to "orca",
        "JTO" to "jito-governance-token",
        "W" to "wormhole",
        "TENSOR" to "tensor",
        "HNT" to "helium",
        "MEW" to "cat-in-a-dogs-world",
        "POPCAT" to "popcat",

        // BSC ecosystem
        "TWT" to "trust-wallet-token",
        "SFP" to "safepal",
        "BAKE" to "bakerytoken",

        // DEX/DeFi
        "QUICK" to "quickswap",
        "VELO" to "velodrome-finance",
        "AERO" to "aerodrome-finance",
        "JOE" to "joe",
        "PNG" to "pangolin",
        "GHST" to "aavegotchi",
        "DEGEN" to "degen-base",
        "BRETT" to "brett",
        "TOSHI" to "toshi"
    )
    
    @Volatile private var initialized = false
    private var refreshJob: Job? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        refreshJob = scope.launch {
            refreshPrices()
            while (isActive) {
                delay(CACHE_TTL)
                refreshPrices()
            }
        }
    }
    
    /**
     * Get price for a symbol (returns cached or fetches if needed)
     */
    fun getPrice(symbol: String): Double {
        val upperSymbol = symbol.uppercase()
        
        // Check cache first
        priceCache[upperSymbol]?.let { return it }
        
        // Return fallback if not in cache
        return getFallbackPrice(upperSymbol)
    }
    
    /**
     * Get price for a symbol, triggering refresh if cache is stale
     */
    suspend fun getPriceAsync(symbol: String): Double {
        val upperSymbol = symbol.uppercase()
        
        // Check if cache needs refresh
        if (System.currentTimeMillis() - lastUpdate > CACHE_TTL) {
            refreshPrices()
        }
        
        return priceCache[upperSymbol] ?: getFallbackPrice(upperSymbol)
    }
    
    /**
     * Refresh all prices from CoinGecko
     */
    suspend fun refreshPrices(): Boolean {
        if (_isLoading.value) return false
        
        _isLoading.value = true
        
        return try {
            val result = CryptoPriceService.fetchMarketData()
            
            result.onSuccess { coins ->
                coins.forEach { coin ->
                    val symbol = coin.symbol.uppercase()
                    if (coin.currentPrice > 0) {
                        priceCache[symbol] = coin.currentPrice
                    }
                }
                lastUpdate = System.currentTimeMillis()
                _pricesFlow.value = priceCache.toMap()
            }
            
            result.isSuccess
        } catch (e: Exception) {
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Force refresh prices
     */
    suspend fun forceRefresh(): Boolean {
        lastUpdate = 0
        return refreshPrices()
    }
    
    /**
     * Get USD value for an amount of token
     */
    fun getUsdValue(symbol: String, amount: Double): Double {
        return amount * getPrice(symbol)
    }
    
    /**
     * Get USD value async
     */
    suspend fun getUsdValueAsync(symbol: String, amount: Double): Double {
        return amount * getPriceAsync(symbol)
    }
    
    /**
     * Check if prices are loaded
     */
    fun hasPrices(): Boolean = priceCache.isNotEmpty()
    
    /**
     * Get all cached prices
     */
    fun getAllPrices(): Map<String, Double> = priceCache.toMap()
    
    /**
     * Fallback prices when API is unavailable
     */
    private fun getFallbackPrice(symbol: String): Double {
        return when (symbol.uppercase()) {
            // Stablecoins
            "USDT", "USDC", "DAI", "BUSD", "TUSD" -> 1.0
            
            // Major coins (rough estimates - will be updated by API)
            "BTC", "WBTC", "BTCB" -> 97000.0
            "ETH", "WETH" -> 3400.0
            "BNB", "WBNB" -> 700.0
            "SOL" -> 200.0
            "XRP" -> 2.3
            "ADA" -> 1.0
            "DOGE" -> 0.4
            "AVAX", "WAVAX" -> 50.0
            "MATIC", "WMATIC", "POL" -> 0.55
            "DOT" -> 9.0
            "LINK" -> 24.0
            "LTC" -> 125.0
            "ATOM" -> 12.0
            "NEAR" -> 7.0
            "ARB" -> 0.8
            "OP" -> 1.8
            "UNI" -> 16.0
            "AAVE" -> 350.0
            "MKR" -> 1600.0
            "LDO" -> 2.0
            "CRV" -> 0.5
            "CAKE" -> 2.5
            "SHIB" -> 0.000025
            "PEPE" -> 0.00002
            "FLOKI" -> 0.0002
            "APE" -> 1.5
            "SAND" -> 0.5
            "MANA" -> 0.5
            "INJ" -> 25.0
            "PENDLE" -> 5.0
            "GMX" -> 30.0
            "STG" -> 0.5
            "WLD" -> 3.0
            "FIL" -> 6.0
            "YFI" -> 8000.0
            "RPL" -> 20.0
            "FRAX" -> 1.0
            "TUSD" -> 1.0
            "stETH" -> 3400.0
            "rETH" -> 3600.0
            "cbETH" -> 3500.0
            "MSOL" -> 220.0
            "JITOSOL" -> 220.0
            "JUP" -> 1.0
            "RAY" -> 3.0
            "JTO" -> 3.0
            "BONK" -> 0.00003
            "WIF" -> 2.5
            "ORCA" -> 5.0
            "PYTH" -> 0.4
            "TWT" -> 1.5
            "AERO" -> 1.5
            "DEGEN" -> 0.01
            "BRETT" -> 0.1
            
            else -> 0.0
        }
    }
    
    /**
     * Get CoinGecko ID for a symbol
     */
    fun getCoinGeckoId(symbol: String): String? {
        return symbolToId[symbol.uppercase()]
    }
}

