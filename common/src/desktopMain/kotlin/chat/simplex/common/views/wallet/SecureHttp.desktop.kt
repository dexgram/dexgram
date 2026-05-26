package chat.simplex.common.views.wallet

actual object SecureHttp {
    actual fun get(url: String, headers: Map<String, String>): String? = null
    actual fun getWithTimeout(url: String, timeoutMs: Int): String? = null
    actual fun postJson(url: String, body: String): String? = null
    actual fun postJsonWithHeaders(url: String, body: String, headers: Map<String, String>): String? = null
    actual fun postJsonFull(url: String, body: String, headers: Map<String, String>): Pair<String?, String?> = Pair(null, null)
}
