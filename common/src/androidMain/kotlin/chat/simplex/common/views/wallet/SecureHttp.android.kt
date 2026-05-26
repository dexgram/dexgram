package chat.simplex.common.views.wallet

actual object SecureHttp {
    actual fun get(url: String, headers: Map<String, String>): String? =
        SecureHttpClient.get(url, headers)

    actual fun getWithTimeout(url: String, timeoutMs: Int): String? =
        SecureHttpClient.getWithTimeout(url, timeoutMs)

    actual fun postJson(url: String, body: String): String? =
        SecureHttpClient.postJson(url, body)

    actual fun postJsonWithHeaders(url: String, body: String, headers: Map<String, String>): String? =
        SecureHttpClient.postJsonWithHeaders(url, body, headers)

    actual fun postJsonFull(url: String, body: String, headers: Map<String, String>): Pair<String?, String?> =
        SecureHttpClient.postJsonFull(url, body, headers)
}
