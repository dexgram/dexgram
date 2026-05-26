package chat.simplex.common.views.wallet

/**
 * Platform-agnostic HTTP client for wallet/swap/blockchain traffic.
 * Android: delegates to OkHttp-backed SecureHttpClient.
 * Desktop: stub (wallet not supported on desktop).
 */
expect object SecureHttp {
    fun get(url: String, headers: Map<String, String>): String?
    fun getWithTimeout(url: String, timeoutMs: Int): String?
    fun postJson(url: String, body: String): String?
    fun postJsonWithHeaders(url: String, body: String, headers: Map<String, String>): String?
    fun postJsonFull(url: String, body: String, headers: Map<String, String>): Pair<String?, String?>
}
