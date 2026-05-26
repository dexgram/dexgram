package chat.simplex.common.views.wallet

import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URL
import java.net.HttpURLConnection

/**
 * Network Status Service - Monitor RPC health and connectivity
 */
object NetworkStatusService {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private fun rpcUrl(network: BlockchainNetwork): String? =
        BlockchainService.getRpcUrl(network)
    
    // Status flow for each network
    private val _statusFlow = MutableStateFlow<Map<BlockchainNetwork, NetworkStatus>>(emptyMap())
    val statusFlow: StateFlow<Map<BlockchainNetwork, NetworkStatus>> = _statusFlow.asStateFlow()
    
    // Overall connectivity status
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    /**
     * Network status for a single network
     */
    data class NetworkStatus(
        val network: BlockchainNetwork,
        val isConnected: Boolean,
        val latencyMs: Long,
        val blockHeight: Long?,
        val lastChecked: Long = System.currentTimeMillis(),
        val errorMessage: String? = null
    ) {
        val isHealthy: Boolean get() = isConnected && latencyMs < 5000
        val latencyLevel: LatencyLevel get() = when {
            !isConnected -> LatencyLevel.OFFLINE
            latencyMs < 500 -> LatencyLevel.FAST
            latencyMs < 2000 -> LatencyLevel.NORMAL
            latencyMs < 5000 -> LatencyLevel.SLOW
            else -> LatencyLevel.VERY_SLOW
        }
    }
    
    enum class LatencyLevel(val color: Long) {
        FAST(0xFF0ECB81),
        NORMAL(0xFFF0B90B),
        SLOW(0xFFF6465D),
        VERY_SLOW(0xFFF6465D),
        OFFLINE(0xFF707A8A);

        val displayName: String get() = when (this) {
            FAST -> generalGetString(MR.strings.wallet_latency_fast)
            NORMAL -> generalGetString(MR.strings.wallet_latency_normal)
            SLOW -> generalGetString(MR.strings.wallet_latency_slow)
            VERY_SLOW -> generalGetString(MR.strings.wallet_latency_very_slow)
            OFFLINE -> generalGetString(MR.strings.wallet_latency_offline)
        }
    }
    
    /**
     * Check status of a single network
     */
    suspend fun checkNetwork(network: BlockchainNetwork): NetworkStatus = withContext(Dispatchers.IO) {
        val rpcUrl = rpcUrl(network) ?: return@withContext NetworkStatus(
            network = network,
            isConnected = false,
            latencyMs = -1,
            blockHeight = null,
            errorMessage = generalGetString(MR.strings.wallet_network_no_rpc)
        )
        
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        
        try {
            connection = URL(rpcUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            
            val requestBody = """{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}"""
            connection.outputStream.write(requestBody.toByteArray())
            
            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val blockHeight = parseBlockNumber(response)
                
                NetworkStatus(
                    network = network,
                    isConnected = true,
                    latencyMs = latency,
                    blockHeight = blockHeight
                )
            } else {
                NetworkStatus(
                    network = network,
                    isConnected = false,
                    latencyMs = latency,
                    blockHeight = null,
                    errorMessage = "HTTP $responseCode"
                )
            }
        } catch (e: Exception) {
            NetworkStatus(
                network = network,
                isConnected = false,
                latencyMs = System.currentTimeMillis() - startTime,
                blockHeight = null,
                errorMessage = e.message
            )
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Check all networks
     */
    suspend fun checkAllNetworks(): Map<BlockchainNetwork, NetworkStatus> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<BlockchainNetwork, NetworkStatus>()
        
        BlockchainNetwork.EVM_NETWORKS.map { network ->
            async {
                network to checkNetwork(network)
            }
        }.awaitAll().forEach { (network, status) ->
            results[network] = status
        }
        
        _statusFlow.value = results
        _isConnected.value = results.values.any { it.isConnected }
        
        results
    }
    
    /**
     * Get cached status for a network
     */
    fun getStatus(network: BlockchainNetwork): NetworkStatus? {
        return _statusFlow.value[network]
    }
    
    /**
     * Get best (fastest) available network
     */
    fun getBestNetwork(): BlockchainNetwork? {
        return _statusFlow.value
            .filter { it.value.isConnected }
            .minByOrNull { it.value.latencyMs }
            ?.key
    }
    
    private var monitorJob: Job? = null

    fun startMonitoring(intervalMs: Long = 60000) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                checkAllNetworks()
                delay(intervalMs)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }
    
    private fun parseBlockNumber(response: String): Long? {
        return try {
            val resultStart = response.indexOf("\"result\":\"0x")
            if (resultStart >= 0) {
                val start = resultStart + 12
                val end = response.indexOf("\"", start)
                val hex = response.substring(start, end)
                java.lang.Long.parseLong(hex, 16)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

