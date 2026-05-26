package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class EnabledAssetKey(
    val network: BlockchainNetwork,
    val symbol: String,
    val contractAddress: String = ""
)

@Serializable
data class AssetPreferences(
    val enabledNetworks: Set<BlockchainNetwork> = emptySet(),
    val enabledTokens: Set<EnabledAssetKey> = emptySet(),
    val isConfigured: Boolean = false
)

object NetworkTokenPreferences {
    private const val PREFS_KEY = "wallet_asset_preferences"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var saveJob: Job? = null

    private val _prefsFlow = MutableStateFlow(AssetPreferences())
    val prefsFlow: StateFlow<AssetPreferences> = _prefsFlow

    val current: AssetPreferences get() = _prefsFlow.value

    fun load() {
        val raw = WalletPrefs.getString(PREFS_KEY)
        if (raw != null) {
            try {
                _prefsFlow.value = json.decodeFromString(raw)
            } catch (_: Exception) {
                _prefsFlow.value = AssetPreferences()
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(300)
            try {
                val data = json.encodeToString(_prefsFlow.value)
                WalletPrefs.putString(PREFS_KEY, data)
            } catch (_: Exception) { }
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        try {
            WalletPrefs.putString(PREFS_KEY, json.encodeToString(_prefsFlow.value))
        } catch (_: Exception) { }
    }

    fun isConfigured(): Boolean = _prefsFlow.value.isConfigured

    fun getEnabledNetworks(): Set<BlockchainNetwork> = _prefsFlow.value.enabledNetworks

    fun isNetworkEnabled(network: BlockchainNetwork): Boolean =
        !_prefsFlow.value.isConfigured || network in _prefsFlow.value.enabledNetworks

    fun isTokenEnabled(network: BlockchainNetwork, symbol: String, contractAddress: String = ""): Boolean {
        val prefs = _prefsFlow.value
        if (!prefs.isConfigured) return true
        if (network !in prefs.enabledNetworks) return false
        if (contractAddress.isBlank()) return true
        return EnabledAssetKey(network, symbol.uppercase(), contractAddress) in prefs.enabledTokens
    }

    fun setNetworkEnabled(network: BlockchainNetwork, enabled: Boolean) {
        val prefs = _prefsFlow.value
        val networks = prefs.enabledNetworks.toMutableSet()
        val tokens = prefs.enabledTokens.toMutableSet()
        if (enabled) {
            networks.add(network)
        } else {
            networks.remove(network)
            tokens.removeAll { it.network == network }
        }
        _prefsFlow.value = prefs.copy(
            enabledNetworks = networks,
            enabledTokens = tokens,
            isConfigured = true
        )
        scheduleSave()
    }

    fun setTokenEnabled(network: BlockchainNetwork, symbol: String, contractAddress: String, enabled: Boolean) {
        val prefs = _prefsFlow.value
        val tokens = prefs.enabledTokens.toMutableSet()
        val key = EnabledAssetKey(network, symbol.uppercase(), contractAddress)
        if (enabled) tokens.add(key) else tokens.remove(key)
        _prefsFlow.value = prefs.copy(enabledTokens = tokens, isConfigured = true)
        scheduleSave()
    }

    fun setAllTokensForNetwork(network: BlockchainNetwork, enabled: Boolean) {
        val prefs = _prefsFlow.value
        val tokens = prefs.enabledTokens.toMutableSet()
        val networkTokens = PopularTokens.getTokensForNetwork(network)
        if (enabled) {
            networkTokens.forEach { tokens.add(EnabledAssetKey(network, it.symbol.uppercase(), it.contractAddress)) }
        } else {
            tokens.removeAll { it.network == network }
        }
        _prefsFlow.value = prefs.copy(enabledTokens = tokens, isConfigured = true)
        scheduleSave()
    }

    fun getEnabledTokensForNetwork(network: BlockchainNetwork): List<WalletToken> {
        val prefs = _prefsFlow.value
        if (!prefs.isConfigured) return PopularTokens.getTokensForNetwork(network)
        if (network !in prefs.enabledNetworks) return emptyList()
        val all = PopularTokens.getTokensForNetwork(network)
        return all.filter { token ->
            EnabledAssetKey(network, token.symbol.uppercase(), token.contractAddress) in prefs.enabledTokens
        }
    }

    fun initDefaults(networks: List<BlockchainNetwork>? = null) {
        if (_prefsFlow.value.isConfigured) return
        val nets = networks ?: listOf(
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.BINANCE_SMART_CHAIN,
            BlockchainNetwork.TRON,
            BlockchainNetwork.SOLANA
        )
        val tokens = mutableSetOf<EnabledAssetKey>()
        val defaultSymbols = setOf("USDT", "USDC")
        for (net in nets) {
            PopularTokens.getTokensForNetwork(net)
                .filter { it.symbol.uppercase() in defaultSymbols }
                .forEach { tokens.add(EnabledAssetKey(net, it.symbol.uppercase(), it.contractAddress)) }
        }
        _prefsFlow.value = AssetPreferences(
            enabledNetworks = nets.toSet(),
            enabledTokens = tokens,
            isConfigured = true
        )
        scheduleSave()
    }

    fun reset() {
        saveJob?.cancel()
        _prefsFlow.value = AssetPreferences()
        WalletPrefs.remove(PREFS_KEY)
    }
}
