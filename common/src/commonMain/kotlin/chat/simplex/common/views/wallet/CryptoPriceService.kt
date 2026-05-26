package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class CryptoCoin(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String = "",
    val currentPrice: Double = 0.0,
    val marketCap: Long = 0L,
    val marketCapRank: Int = 0,
    val priceChangePercentage24h: Double = 0.0,
    val priceChangePercentage7d: Double = 0.0,
    val priceChangePercentage1h: Double = 0.0,
    val high24h: Double = 0.0,
    val low24h: Double = 0.0,
    val totalVolume: Long = 0L,
    val circulatingSupply: Double = 0.0,
    val totalSupply: Double? = null,
    val maxSupply: Double? = null,
    val ath: Double = 0.0,
    val athChangePercentage: Double = 0.0,
    val sparklineIn7d: List<Double> = emptyList()
)

data class GlobalMarketData(
    val totalMarketCap: Long = 2950000000000L,
    val marketCapChange24h: Double = 0.52,
    val btcDominance: Double = 57.5,
    val fearGreedIndex: Int = 50
)

/**
 * Market data state for UI consumption
 */
sealed class MarketDataState {
    object Loading : MarketDataState()
    data class Success(val coins: List<CryptoCoin>, val isLive: Boolean) : MarketDataState()
    data class Error(val message: String, val cachedData: List<CryptoCoin>?) : MarketDataState()
}

object CryptoPriceService {
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    // ========================================================================
    // CACHING SYSTEM
    // ========================================================================
    
    private data class CachedResponse<T>(
        val data: T,
        val timestamp: Long,
        val ttlMs: Long = 60_000 // 1 minute default TTL
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() - timestamp < ttlMs
        fun isStale(): Boolean = !isValid()
    }
    
    private var marketDataCache: CachedResponse<List<CryptoCoin>>? = null
    private var globalDataCache: CachedResponse<GlobalMarketData>? = null
    private var fearGreedCache: CachedResponse<Int>? = null
    
    // ========================================================================
    // RATE LIMITING
    // ========================================================================
    
    private var lastRequestTime = 0L
    private const val MIN_REQUEST_INTERVAL = 10_000L // 10 seconds between requests
    private var backoffMultiplier = 1
    private const val MAX_BACKOFF = 8 // Max 80 seconds delay
    
    private suspend fun waitForRateLimit() {
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        val requiredDelay = MIN_REQUEST_INTERVAL * backoffMultiplier
        
        if (timeSinceLastRequest < requiredDelay) {
            delay(requiredDelay - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()
    }
    
    private fun onRequestSuccess() {
        backoffMultiplier = 1 // Reset on success
    }
    
    private fun onRequestError(isRateLimited: Boolean) {
        if (isRateLimited) {
            backoffMultiplier = minOf(backoffMultiplier * 2, MAX_BACKOFF)
        }
    }
    
    // ========================================================================
    // PUBLIC API - Market Data
    // ========================================================================
    
    /**
     * Fetch market data with caching and proper error handling
     * @param forceRefresh If true, bypasses cache
     * @return Result with coins list or error
     */
    suspend fun fetchMarketData(
        currency: String = "usd",
        forceRefresh: Boolean = false
    ): Result<List<CryptoCoin>> = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = marketDataCache
        if (!forceRefresh && cached?.isValid() == true) {
            return@withContext Result.success(cached.data)
        }
        
        try {
            waitForRateLimit()
            
            val allCoins = mutableListOf<CryptoCoin>()
            
            // Fetch page 1 with 100 coins (faster initial load)
            val url1 = "https://api.coingecko.com/api/v3/coins/markets?" +
                "vs_currency=$currency&order=market_cap_desc&per_page=100&page=1" +
                "&sparkline=true&price_change_percentage=1h,24h,7d"
            
            val response1 = fetchUrl(url1)
            if (response1 != null) {
                allCoins.addAll(parseMarketResponse(response1))
                onRequestSuccess()
            } else {
                // Return cached data if available
                cached?.data?.let { 
                    return@withContext Result.success(it)
                }
                return@withContext Result.failure(Exception("Failed to fetch market data"))
            }
            
            if (allCoins.isNotEmpty()) {
                // Update cache with what we have so far
                marketDataCache = CachedResponse(allCoins.toList(), System.currentTimeMillis())
            }
            
            Result.success(allCoins)
        } catch (e: Exception) {
            // Return cached data on error
            marketDataCache?.data?.let { Result.success(it) }
                ?: Result.failure(e)
        }
    }
    
    /**
     * Progressive market data loading - emits coins in batches for smooth UI
     * First batch (top 50) loads quickly, then more coins are fetched in background
     */
    fun fetchMarketDataProgressively(
        currency: String = "usd",
        forceRefresh: Boolean = false
    ): Flow<ProgressiveLoadState> = flow {
        val cached = marketDataCache
        
        // If we have valid cache and not forcing refresh, emit it immediately
        if (!forceRefresh && cached?.isValid() == true) {
            emit(ProgressiveLoadState.Complete(cached.data, isFromCache = true))
            return@flow
        }
        
        // Emit cached data first if available (stale but useful)
        if (cached?.data != null) {
            emit(ProgressiveLoadState.Batch(cached.data, batchNumber = 0, isComplete = false, isCached = true))
        }
        
        try {
            val allCoins = mutableListOf<CryptoCoin>()
            
            // Batch 1: Top 50 coins (fastest initial load)
            waitForRateLimit()
            val url1 = "https://api.coingecko.com/api/v3/coins/markets?" +
                "vs_currency=$currency&order=market_cap_desc&per_page=50&page=1" +
                "&sparkline=true&price_change_percentage=1h,24h,7d"
            
            val response1 = fetchUrl(url1)
            if (response1 != null) {
                val batch1 = parseMarketResponse(response1)
                allCoins.addAll(batch1)
                onRequestSuccess()
                emit(ProgressiveLoadState.Batch(allCoins.toList(), batchNumber = 1, isComplete = false))
            }
            
            // Batch 2: Coins 51-100
            waitForRateLimit()
            val url2 = "https://api.coingecko.com/api/v3/coins/markets?" +
                "vs_currency=$currency&order=market_cap_desc&per_page=50&page=2" +
                "&sparkline=true&price_change_percentage=1h,24h,7d"
            
            fetchUrl(url2)?.let { response ->
                val batch2 = parseMarketResponse(response)
                allCoins.addAll(batch2)
                emit(ProgressiveLoadState.Batch(allCoins.toList(), batchNumber = 2, isComplete = false))
            }
            
            // Batch 3: Coins 101-200
            waitForRateLimit()
            val url3 = "https://api.coingecko.com/api/v3/coins/markets?" +
                "vs_currency=$currency&order=market_cap_desc&per_page=100&page=3" +
                "&sparkline=true&price_change_percentage=1h,24h,7d"
            
            fetchUrl(url3)?.let { response ->
                val batch3 = parseMarketResponse(response)
                allCoins.addAll(batch3)
                emit(ProgressiveLoadState.Batch(allCoins.toList(), batchNumber = 3, isComplete = false))
            }
            
            // Batch 4: Coins 201-250
            waitForRateLimit()
            val url4 = "https://api.coingecko.com/api/v3/coins/markets?" +
                "vs_currency=$currency&order=market_cap_desc&per_page=50&page=5" +
                "&sparkline=true&price_change_percentage=1h,24h,7d"
            
            fetchUrl(url4)?.let { response ->
                val batch4 = parseMarketResponse(response)
                allCoins.addAll(batch4)
            }
            
            // Update cache with complete data
            if (allCoins.isNotEmpty()) {
                marketDataCache = CachedResponse(allCoins, System.currentTimeMillis())
            }
            
            emit(ProgressiveLoadState.Complete(allCoins, isFromCache = false))
            
        } catch (e: Exception) {
            emit(ProgressiveLoadState.Error(e.message ?: "Unknown error", cached?.data))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * State for progressive loading
     */
    sealed class ProgressiveLoadState {
        data class Batch(
            val coins: List<CryptoCoin>,
            val batchNumber: Int,
            val isComplete: Boolean,
            val isCached: Boolean = false
        ) : ProgressiveLoadState()
        
        data class Complete(
            val coins: List<CryptoCoin>,
            val isFromCache: Boolean
        ) : ProgressiveLoadState()
        
        data class Error(
            val message: String,
            val cachedData: List<CryptoCoin>?
        ) : ProgressiveLoadState()
    }
    
    /**
     * Fetch market data with state for UI
     */
    suspend fun fetchMarketDataWithState(forceRefresh: Boolean = false): MarketDataState {
        val cached = marketDataCache
        
        return try {
            val result = fetchMarketData(forceRefresh = forceRefresh)
            result.fold(
                onSuccess = { coins ->
                    val isLive = marketDataCache?.timestamp?.let { 
                        System.currentTimeMillis() - it < 120_000 // Fresh within 2 minutes
                    } ?: false
                    MarketDataState.Success(coins, isLive)
                },
                onFailure = { error ->
                    MarketDataState.Error(
                        message = error.message ?: "Unknown error",
                        cachedData = cached?.data
                    )
                }
            )
        } catch (e: Exception) {
            MarketDataState.Error(
                message = e.message ?: "Network error",
                cachedData = cached?.data
            )
        }
    }
    
    /**
     * Fetch global market data including Fear & Greed index
     */
    suspend fun fetchGlobalMarketData(): GlobalMarketData = withContext(Dispatchers.IO) {
        // Check cache
        val cached = globalDataCache
        if (cached?.isValid() == true) {
            return@withContext cached.data
        }
        
        try {
            waitForRateLimit()
            
            val globalUrl = "https://api.coingecko.com/api/v3/global"
            val response = fetchUrl(globalUrl)
            
            // Fetch Fear & Greed index separately
            val fearGreedIndex = fetchFearGreedIndex()
            
            if (response != null) {
                onRequestSuccess()
                val obj = Json.parseToJsonElement(response).jsonObject
                val data = obj["data"]?.jsonObject
                
                val globalData = GlobalMarketData(
                    totalMarketCap = data?.get("total_market_cap")?.jsonObject?.get("usd")?.jsonPrimitive?.longOrNull ?: 2950000000000L,
                    marketCapChange24h = data?.get("market_cap_change_percentage_24h_usd")?.jsonPrimitive?.doubleOrNull ?: 0.52,
                    btcDominance = data?.get("market_cap_percentage")?.jsonObject?.get("btc")?.jsonPrimitive?.doubleOrNull ?: 57.5,
                    fearGreedIndex = fearGreedIndex
                )
                
                globalDataCache = CachedResponse(globalData, System.currentTimeMillis())
                globalData
            } else {
                cached?.data ?: GlobalMarketData(fearGreedIndex = fearGreedIndex)
            }
        } catch (e: Exception) {
            cached?.data ?: GlobalMarketData()
        }
    }
    
    /**
     * Fetch Fear & Greed Index from Alternative.me API
     */
    private suspend fun fetchFearGreedIndex(): Int {
        // Check cache first
        val cached = fearGreedCache
        if (cached?.isValid() == true) {
            return cached.data
        }
        
        return try {
            val response = fetchUrl("https://api.alternative.me/fng/")
            if (response != null) {
                val jsonElement = Json.parseToJsonElement(response).jsonObject
                val index = jsonElement["data"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("value")?.jsonPrimitive?.intOrNull ?: 50
                
                // Cache for 30 minutes (Fear & Greed updates less frequently)
                fearGreedCache = CachedResponse(index, System.currentTimeMillis(), 30 * 60 * 1000L)
                index
            } else {
                cached?.data ?: 50
            }
        } catch (e: Exception) {
            cached?.data ?: 50 // Neutral fallback
        }
    }
    
    // ========================================================================
    // CHART DATA
    // ========================================================================
    
    /**
     * Data class for chart point with timestamp
     */
    data class ChartPoint(
        val timestamp: Long,
        val price: Double
    )
    
    /**
     * Fetch chart data for different timeframes - returns actual timestamped data
     */
    suspend fun fetchChartData(coinId: String, days: Int): List<ChartPoint> = withContext(Dispatchers.IO) {
        try {
            waitForRateLimit()
            
            // Use interval parameter for better data granularity
            val interval = when {
                days <= 1 -> "" // Auto (5-minute for 1 day)
                days <= 7 -> "&interval=hourly"
                else -> "&interval=daily"
            }
            
            val url = "https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=usd&days=$days$interval"
            val response = fetchUrl(url)
            
            if (response != null) {
                onRequestSuccess()
                val obj = Json.parseToJsonElement(response).jsonObject
                val prices = obj["prices"]?.jsonArray
                
                prices?.mapNotNull { item ->
                    val arr = item.jsonArray
                    val timestamp = arr.getOrNull(0)?.jsonPrimitive?.longOrNull
                    val price = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                    
                    if (timestamp != null && price != null) {
                        ChartPoint(timestamp, price)
                    } else null
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Fetch extended chart data for scrollable chart
     */
    suspend fun fetchExtendedChartData(coinId: String, timeframe: String): List<ChartPoint> = withContext(Dispatchers.IO) {
        val days = when (timeframe) {
            "1H" -> 1
            "24H" -> 1
            "7D" -> 7
            "1M" -> 30
            "3M" -> 90
            "1Y" -> 365
            "ALL" -> "max"
            else -> 1
        }
        
        try {
            waitForRateLimit()
            
            val interval = when (timeframe) {
                "1H", "24H" -> ""
                "7D" -> "&interval=hourly"
                else -> "&interval=daily"
            }
            
            val url = "https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=usd&days=$days$interval"
            val response = fetchUrl(url)
            
            if (response != null) {
                onRequestSuccess()
                val obj = Json.parseToJsonElement(response).jsonObject
                val prices = obj["prices"]?.jsonArray
                
                val points = prices?.mapNotNull { item ->
                    val arr = item.jsonArray
                    val timestamp = arr.getOrNull(0)?.jsonPrimitive?.longOrNull
                    val price = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                    
                    if (timestamp != null && price != null) {
                        ChartPoint(timestamp, price)
                    } else null
                } ?: emptyList()
                
                // For 1H, take last 12 points (1 hour of 5-min data)
                when (timeframe) {
                    "1H" -> points.takeLast(12)
                    else -> points
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun fetchCoinDetails(coinId: String): Result<CryptoCoin> = withContext(Dispatchers.IO) {
        try {
            waitForRateLimit()
            
            val url = "https://api.coingecko.com/api/v3/coins/$coinId?localization=false&tickers=false&community_data=false&developer_data=false"
            val response = fetchUrl(url)
            
            if (response != null) {
                onRequestSuccess()
                Result.success(parseCoinDetailResponse(response, coinId))
            } else {
                Result.failure(Exception("Failed to fetch coin details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // NETWORK LAYER
    // ========================================================================
    
    private fun fetchUrl(url: String): String? {
        return try {
            val response = SecureHttp.get(url, emptyMap())
            if (response != null) response else {
                onRequestError(isRateLimited = false)
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    
    // ========================================================================
    // PARSING
    // ========================================================================
    
    private fun parseMarketResponse(response: String): List<CryptoCoin> {
        return try {
            Json.parseToJsonElement(response).jsonArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    CryptoCoin(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        symbol = obj["symbol"]?.jsonPrimitive?.content?.uppercase() ?: "",
                        name = obj["name"]?.jsonPrimitive?.content ?: "",
                        image = obj["image"]?.jsonPrimitive?.content ?: "",
                        currentPrice = obj["current_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        marketCap = obj["market_cap"]?.jsonPrimitive?.longOrNull ?: 0L,
                        marketCapRank = obj["market_cap_rank"]?.jsonPrimitive?.intOrNull ?: 0,
                        priceChangePercentage24h = obj["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        priceChangePercentage7d = obj["price_change_percentage_7d_in_currency"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        priceChangePercentage1h = obj["price_change_percentage_1h_in_currency"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        high24h = obj["high_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        low24h = obj["low_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        totalVolume = obj["total_volume"]?.jsonPrimitive?.longOrNull ?: 0L,
                        circulatingSupply = obj["circulating_supply"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        totalSupply = obj["total_supply"]?.jsonPrimitive?.doubleOrNull,
                        maxSupply = obj["max_supply"]?.jsonPrimitive?.doubleOrNull,
                        ath = obj["ath"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        athChangePercentage = obj["ath_change_percentage"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        sparklineIn7d = obj["sparkline_in_7d"]?.jsonObject?.get("price")?.jsonArray?.mapNotNull { 
                            it.jsonPrimitive.doubleOrNull 
                        } ?: emptyList()
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
    
    private fun parseCoinDetailResponse(response: String, coinId: String): CryptoCoin {
        val obj = Json.parseToJsonElement(response).jsonObject
        val marketData = obj["market_data"]?.jsonObject
        
        return CryptoCoin(
            id = obj["id"]?.jsonPrimitive?.content ?: coinId,
            symbol = obj["symbol"]?.jsonPrimitive?.content?.uppercase() ?: "",
            name = obj["name"]?.jsonPrimitive?.content ?: "",
            image = obj["image"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: "",
            currentPrice = marketData?.get("current_price")?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            marketCap = marketData?.get("market_cap")?.jsonObject?.get("usd")?.jsonPrimitive?.longOrNull ?: 0L,
            marketCapRank = obj["market_cap_rank"]?.jsonPrimitive?.intOrNull ?: 0,
            priceChangePercentage24h = marketData?.get("price_change_percentage_24h")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            priceChangePercentage7d = marketData?.get("price_change_percentage_7d")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            priceChangePercentage1h = marketData?.get("price_change_percentage_1h_in_currency")?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            high24h = marketData?.get("high_24h")?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            low24h = marketData?.get("low_24h")?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            totalVolume = marketData?.get("total_volume")?.jsonObject?.get("usd")?.jsonPrimitive?.longOrNull ?: 0L,
            circulatingSupply = marketData?.get("circulating_supply")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            totalSupply = marketData?.get("total_supply")?.jsonPrimitive?.doubleOrNull,
            maxSupply = marketData?.get("max_supply")?.jsonPrimitive?.doubleOrNull,
            ath = marketData?.get("ath")?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            athChangePercentage = marketData?.get("ath_change_percentage")?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull ?: 0.0
        )
    }
    
    // ========================================================================
    // MOCK DATA (Fallback)
    // ========================================================================
    
    fun getMockData(): List<CryptoCoin> = listOf(
        CryptoCoin("bitcoin", "BTC", "Bitcoin", "", 97500.0, 1920000000000L, 1, 2.5, 5.2, 0.3, 98000.0, 96000.0, 45000000000L, 19800000.0, 21000000.0, 21000000.0, 99000.0, -1.5, generateMockSparkline(97500.0, 2.5)),
        CryptoCoin("ethereum", "ETH", "Ethereum", "", 3450.0, 415000000000L, 2, 1.8, 4.1, -0.2, 3500.0, 3380.0, 18000000000L, 120000000.0, null, null, 4878.0, -29.3, generateMockSparkline(3450.0, 1.8)),
        CryptoCoin("tether", "USDT", "Tether", "", 1.0, 140000000000L, 3, 0.01, 0.02, 0.0, 1.001, 0.999, 85000000000L, 140000000000.0, null, null, 1.32, -24.2, generateMockSparkline(1.0, 0.01)),
        CryptoCoin("binancecoin", "BNB", "BNB", "", 720.0, 104000000000L, 4, 1.2, 3.5, 0.5, 730.0, 710.0, 1800000000L, 144000000.0, 200000000.0, 200000000.0, 793.0, -9.2, generateMockSparkline(720.0, 1.2)),
        CryptoCoin("solana", "SOL", "Solana", "", 220.0, 105000000000L, 5, 3.2, 8.5, 1.1, 225.0, 215.0, 4500000000L, 480000000.0, null, null, 263.0, -16.3, generateMockSparkline(220.0, 3.2)),
        CryptoCoin("ripple", "XRP", "XRP", "", 2.35, 135000000000L, 6, 4.8, 12.1, 0.8, 2.42, 2.28, 8500000000L, 57000000000.0, 100000000000.0, 100000000000.0, 3.84, -38.8, generateMockSparkline(2.35, 4.8)),
        CryptoCoin("usd-coin", "USDC", "USDC", "", 1.0, 42000000000L, 7, 0.0, 0.01, 0.0, 1.001, 0.999, 8000000000L, 42000000000.0, null, null, 1.17, -14.5, generateMockSparkline(1.0, 0.0)),
        CryptoCoin("cardano", "ADA", "Cardano", "", 1.08, 38000000000L, 8, 2.5, 6.8, 0.4, 1.12, 1.04, 1200000000L, 35000000000.0, 45000000000.0, 45000000000.0, 3.09, -65.1, generateMockSparkline(1.08, 2.5)),
        CryptoCoin("dogecoin", "DOGE", "Dogecoin", "", 0.42, 62000000000L, 9, 3.1, 7.5, -0.5, 0.44, 0.40, 3500000000L, 147000000000.0, null, null, 0.73, -42.5, generateMockSparkline(0.42, 3.1)),
        CryptoCoin("avalanche-2", "AVAX", "Avalanche", "", 52.0, 21500000000L, 10, -2.8, -5.1, -1.2, 54.0, 50.0, 850000000L, 410000000.0, 720000000.0, 720000000.0, 144.96, -64.1, generateMockSparkline(52.0, -2.8)),
        CryptoCoin("polkadot", "DOT", "Polkadot", "", 9.50, 14500000000L, 11, 2.1, 5.8, 0.3, 9.80, 9.20, 450000000L, 1530000000.0, null, null, 55.0, -82.7, generateMockSparkline(9.50, 2.1)),
        CryptoCoin("chainlink", "LINK", "Chainlink", "", 24.50, 15800000000L, 12, 2.7, 6.2, 0.6, 25.20, 23.80, 920000000L, 645000000.0, 1000000000.0, 1000000000.0, 52.70, -53.5, generateMockSparkline(24.50, 2.7)),
        CryptoCoin("shiba-inu", "SHIB", "Shiba Inu", "", 0.0000285, 16800000000L, 13, -4.2, -9.5, -1.5, 0.0000295, 0.0000275, 1100000000L, 589000000000000.0, null, null, 0.0000861, -66.9, generateMockSparkline(0.0000285, -4.2)),
        CryptoCoin("polygon-ecosystem-token", "POL", "Polygon", "", 0.62, 6200000000L, 14, 2.1, 5.4, 0.2, 0.65, 0.59, 380000000L, 10000000000.0, null, null, 1.29, -51.9, generateMockSparkline(0.62, 2.1)),
        CryptoCoin("litecoin", "LTC", "Litecoin", "", 128.0, 9600000000L, 15, 1.5, 3.8, 0.1, 132.0, 124.0, 480000000L, 75000000.0, 84000000.0, 84000000.0, 410.0, -68.8, generateMockSparkline(128.0, 1.5)),
        CryptoCoin("uniswap", "UNI", "Uniswap", "", 17.20, 10300000000L, 16, -2.3, -5.9, -0.8, 17.80, 16.60, 520000000L, 600000000.0, 1000000000.0, 1000000000.0, 44.92, -61.7, generateMockSparkline(17.20, -2.3)),
        CryptoCoin("near", "NEAR", "NEAR Protocol", "", 7.20, 8700000000L, 17, 3.5, 8.2, 0.9, 7.50, 6.90, 620000000L, 1210000000.0, null, null, 20.44, -64.8, generateMockSparkline(7.20, 3.5)),
        CryptoCoin("pepe", "PEPE", "Pepe", "", 0.0000235, 9900000000L, 18, 5.2, 12.8, 1.8, 0.0000248, 0.0000222, 2200000000L, 420690000000000.0, 420690000000000.0, 420690000000000.0, 0.0000284, -17.3, generateMockSparkline(0.0000235, 5.2)),
        CryptoCoin("stellar", "XLM", "Stellar", "", 0.45, 13500000000L, 19, 3.2, 8.5, 0.7, 0.47, 0.43, 620000000L, 30000000000.0, 50000000000.0, 50000000000.0, 0.87, -48.3, generateMockSparkline(0.45, 3.2)),
        CryptoCoin("monero", "XMR", "Monero", "", 195.0, 3600000000L, 20, -1.4, -3.5, -0.3, 200.0, 190.0, 95000000L, 18500000.0, null, null, 517.62, -62.3, generateMockSparkline(195.0, -1.4))
    )
    
    private fun generateMockSparkline(basePrice: Double, changePercent: Double): List<Double> {
        val points = 168 // 7 days hourly
        val startPrice = basePrice / (1 + changePercent / 100)
        val priceRange = basePrice - startPrice
        
        return (0 until points).map { i ->
            val progress = i.toDouble() / points
            val trend = startPrice + (priceRange * progress)
            val noise = (Math.random() - 0.5) * basePrice * 0.02
            trend + noise
        }
    }
    
    // ========================================================================
    // FORMATTING UTILITIES
    // ========================================================================
    
    fun formatPrice(price: Double): String = when {
        price >= 1000 -> "\$${String.format("%,.0f", price)}"
        price >= 1 -> "\$${String.format("%,.2f", price)}"
        price >= 0.01 -> "\$${String.format("%.4f", price)}"
        price >= 0.0001 -> "\$${String.format("%.6f", price)}"
        else -> "\$${String.format("%.8f", price)}"
    }
    
    fun formatLargeNumber(number: Long): String = when {
        number >= 1_000_000_000_000 -> "\$${String.format("%.2f", number / 1_000_000_000_000.0)}T"
        number >= 1_000_000_000 -> "\$${String.format("%.2f", number / 1_000_000_000.0)}B"
        number >= 1_000_000 -> "\$${String.format("%.2f", number / 1_000_000.0)}M"
        else -> "\$$number"
    }
    
    fun formatCompactNumber(number: Long): String = when {
        number >= 1_000_000_000_000 -> "${String.format("%.1f", number / 1_000_000_000_000.0)}T"
        number >= 1_000_000_000 -> "${String.format("%.1f", number / 1_000_000_000.0)}B"
        number >= 1_000_000 -> "${String.format("%.1f", number / 1_000_000.0)}M"
        number >= 1_000 -> "${String.format("%.1f", number / 1_000.0)}K"
        else -> "$number"
    }
    
    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================
    
    /**
     * Clear all caches - useful when user manually refreshes
     */
    fun clearCache() {
        marketDataCache = null
        globalDataCache = null
        fearGreedCache = null
    }
    
    /**
     * Check if we have valid cached data
     */
    fun hasCachedData(): Boolean = marketDataCache?.isValid() == true
    
    /**
     * Get last update timestamp
     */
    fun getLastUpdateTime(): Long? = marketDataCache?.timestamp
}
