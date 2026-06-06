package chat.simplex.common.views.chatlist

import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.views.wallet.SecureHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://prod-internalapi.dexgram.app"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════
//  Response models
// ═══════════════════════════════════════════════════════════════

@Serializable
data class LoginResponse(
    val token: String,
    val tokenType: String,
    val expiresInSeconds: Int
)

@Serializable
data class SubscriptionLimits(
    val maxDevicesDexgram: Int = 5,
    val maxDevicesDexgramVpn: Int = 5,
    val vaultQuotaMb: Int = 0
)

@Serializable
data class SubscriptionResponse(
    val accountId: String = "",
    val status: String = "inactive",
    val expiresAt: Long? = null,
    val daysRemaining: Int = 0,
    val limits: SubscriptionLimits = SubscriptionLimits()
)

@Serializable
data class DexgramDevice(
    val deviceId: String = "",
    val name: String = "",
    val deviceName: String = "",
    val platform: String = "",
    val model: String = "",
    val isActive: Boolean = false,
    val lastSeenAt: String? = null,
    val createdAt: String? = null
) {
    /** Best display label across the backend's possible naming fields. */
    val displayName: String
        get() = listOf(name, deviceName, model, platform).firstOrNull { it.isNotBlank() } ?: "Device"
}

@Serializable
data class DevicesResponse(
    val devices: List<DexgramDevice> = emptyList()
)

@Serializable
data class GooglePurchaseSubscription(
    val status: String = "",
    val planCode: String = "",
    val expiresAt: Long = 0
)

@Serializable
data class GooglePurchaseResponse(
    val accountId: String,
    val displayAccountId: String = "",
    val token: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Int = 0,
    val promoCodeApplied: String? = null,
    val subscription: GooglePurchaseSubscription = GooglePurchaseSubscription()
)

// ═══════════════════════════════════════════════════════════════
//  Sealed result type — no exceptions leak to callers
// ═══════════════════════════════════════════════════════════════

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

// ═══════════════════════════════════════════════════════════════
//  API functions — all suspend, all off-main-thread
// ═══════════════════════════════════════════════════════════════

object DexgramApi {

    suspend fun login(accountId: String): ApiResult<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanId = accountId.replace("-", "").replace(" ", "").trim()
            if (cleanId.length != 16 || !cleanId.all { it.isDigit() }) {
                return@withContext ApiResult.Error("Invalid account ID format")
            }

            val body = """{"accountId":"$cleanId"}"""
            val (success, error) = SecureHttp.postJsonFull(
                "$BASE_URL/v1/auth/account-id",
                body,
                emptyMap()
            )

            if (success != null) {
                val resp = json.decodeFromString<LoginResponse>(success)
                appPrefs.dexgramAccountId.set(cleanId)
                appPrefs.dexgramToken.set(resp.token)
                appPrefs.dexgramExpiresAt.set(
                    System.currentTimeMillis() + resp.expiresInSeconds * 1000L
                )
                ApiResult.Success(resp)
            } else {
                val msg = parseErrorMessage(error)
                ApiResult.Error(msg)
            }
        } catch (e: Exception) {
            ApiResult.Error("Connection failed")
        }
    }

    suspend fun checkSubscription(): ApiResult<SubscriptionResponse> = withContext(Dispatchers.IO) {
        try {
            val token = appPrefs.dexgramToken.get() ?: ""
            if (token.isBlank()) {
                return@withContext ApiResult.Error("Not logged in")
            }

            val result = SecureHttp.get(
                "$BASE_URL/v1/subscription",
                mapOf("Authorization" to "Bearer $token")
            )

            if (result != null) {
                val resp = json.decodeFromString<SubscriptionResponse>(result)
                ApiResult.Success(resp)
            } else {
                val retried = tryRelogin()
                if (retried) {
                    val retryToken = appPrefs.dexgramToken.get() ?: ""
                    val retryResult = SecureHttp.get(
                        "$BASE_URL/v1/subscription",
                        mapOf("Authorization" to "Bearer $retryToken")
                    )
                    if (retryResult != null) {
                        ApiResult.Success(json.decodeFromString<SubscriptionResponse>(retryResult))
                    } else {
                        ApiResult.Error("Session expired")
                    }
                } else {
                    ApiResult.Error("Session expired")
                }
            }
        } catch (e: Exception) {
            ApiResult.Error("Connection failed")
        }
    }

    /**
     * Lists the devices registered to this Dexgram (chat) account.
     * `GET /v1/devices/dexgram → {devices: [{isActive, ...}, ...]}`.
     * Relogs in once on a 401-style failure, mirroring [checkSubscription].
     */
    suspend fun getDevices(): ApiResult<List<DexgramDevice>> = withContext(Dispatchers.IO) {
        try {
            val token = appPrefs.dexgramToken.get() ?: ""
            if (token.isBlank()) return@withContext ApiResult.Error("Not logged in")

            fun fetch(tok: String): String? = SecureHttp.get(
                "$BASE_URL/v1/devices/dexgram",
                mapOf("Authorization" to "Bearer $tok")
            )

            var body = fetch(token)
            if (body == null && tryRelogin()) {
                body = fetch(appPrefs.dexgramToken.get() ?: "")
            }
            if (body == null) return@withContext ApiResult.Error("Session expired")

            ApiResult.Success(json.decodeFromString<DevicesResponse>(body).devices)
        } catch (e: Exception) {
            ApiResult.Error("Connection failed")
        }
    }

    suspend fun googlePurchase(
        productId: String,
        purchaseToken: String,
        packageName: String,
        promoCode: String? = null
    ): ApiResult<GooglePurchaseResponse> = withContext(Dispatchers.IO) {
        try {
            val bodyMap = mutableMapOf(
                "productId" to "\"$productId\"",
                "purchaseToken" to "\"$purchaseToken\"",
                "packageName" to "\"$packageName\""
            )
            if (!promoCode.isNullOrBlank()) {
                bodyMap["promoCode"] = "\"$promoCode\""
            }
            val body = bodyMap.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

            val (success, error) = SecureHttp.postJsonFull(
                "$BASE_URL/v1/billing/google/purchase",
                body,
                emptyMap()
            )

            if (success != null) {
                val resp = json.decodeFromString<GooglePurchaseResponse>(success)
                appPrefs.dexgramAccountId.set(resp.accountId)
                appPrefs.dexgramToken.set(resp.token)
                appPrefs.dexgramExpiresAt.set(
                    System.currentTimeMillis() + resp.expiresInSeconds * 1000L
                )
                ApiResult.Success(resp)
            } else {
                ApiResult.Error(parseErrorMessage(error))
            }
        } catch (e: Exception) {
            ApiResult.Error("Connection failed")
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = appPrefs.dexgramToken.get() ?: return@withContext false
            SecureHttp.postJsonWithHeaders(
                "$BASE_URL/v1/auth/logout",
                "{}",
                mapOf("Authorization" to "Bearer $token")
            )
            clearSession()
            true
        } catch (_: Exception) {
            clearSession()
            false
        }
    }

    private suspend fun tryRelogin(): Boolean {
        val accountId = appPrefs.dexgramAccountId.get() ?: return false
        if (accountId.isBlank()) return false
        val result = login(accountId)
        return result is ApiResult.Success
    }

    fun clearSession() {
        appPrefs.dexgramToken.set("")
        appPrefs.dexgramExpiresAt.set(0L)
        appPrefs.premiumActive.set(false)
        appPrefs.premiumActivatedAt.set(0L)
        appPrefs.premiumDurationDays.set(0)
    }

    fun isLoggedIn(): Boolean {
        val token = appPrefs.dexgramToken.get() ?: ""
        return token.isNotBlank()
    }

    fun getSavedAccountId(): String {
        return appPrefs.dexgramAccountId.get() ?: ""
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Connection failed"
        return try {
            @Serializable
            data class ErrorDetail(val detail: String = "")
            json.decodeFromString<ErrorDetail>(errorBody).detail.ifBlank { "Request failed" }
        } catch (_: Exception) {
            "Request failed"
        }
    }
}
