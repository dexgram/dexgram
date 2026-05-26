package chat.simplex.common.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.random.Random

/**
 * Extracts the base username from a display name by removing the dot and numeric suffix.
 * Examples:
 *   "smith.13" -> "smith"
 *   "john.doe.42" -> "john.doe"
 *   "alice" -> "alice"
 *   "nancy.916.inco" -> "nancy" (regeneration case - extracts base from full username)
 */
fun extractBaseUsername(displayName: String): String {
    val parts = displayName.split(".")
    
    // Check for format like "nancy.916.inco" (base.number.domain)
    if (parts.size >= 3) {
        // If second part is all digits, this is a full username format
        if (parts[1].all { it.isDigit() }) {
            // Return just the first part (the base name)
            return parts[0]
        }
    }
    
    // Find the last dot
    val lastDotIndex = displayName.lastIndexOf('.')
    
    if (lastDotIndex == -1) {
        // No dot found, return the whole name
        return displayName
    }
    
    // Check if what comes after the dot is all digits
    val afterDot = displayName.substring(lastDotIndex + 1)
    if (afterDot.all { it.isDigit() }) {
        // It's a numeric suffix, remove it
        return displayName.substring(0, lastDotIndex)
    }
    
    // The part after the dot is not all digits, keep the whole name
    return displayName
}

// Request format for .link registration (new v1 API)
@Serializable
data class LinkRegistrationPayload(
    val target: String
)

@Serializable
data class LinkRegistrationRequest(
    val username: String,
    val password: String,
    val tld: String = "link",
    val payload: LinkRegistrationPayload
)

// Request format for .inco registration (new v1 API)
@Serializable
data class IncoRegistrationRequest(
    val username: String,
    val simplexUri: String,
    val tld: String = "inco"
)

@Serializable
data class UsernameData(
    val username: String? = null,        // Optional - returned in registration response
    val simpleXAddress: String? = null,  // Optional - only returned in registration lookup (old)
    val oneTimeAddress: String? = null,  // Old field name (kept for backwards compatibility)
    val address: String? = null          // Returned in query-address-by-username (current API response)
)

@Serializable
data class UsernameRegistrationResponse(
    val data: UsernameData?,
    val error: String?,
    val errorCode: String?,
    val success: Boolean
)

@Serializable
data class UsernameLookupResponse(
    val data: UsernameData?,
    val error: String?,
    val errorCode: String?,
    val success: Boolean
)

object UsernameAPI {
    private const val PUBLIC_LINK_URL = "https://prod-userdb.dexgram.app/v1/link"
    private const val PRIVATE_INCO_URL = "https://prod-userdb.dexgram.app/v1/inco"
    private const val RESOLVE_URL = "https://prod-userdb.dexgram.app/v1/resolve"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Generates a strong 16-character password with:
     * - Uppercase letters (A-Z)
     * - Lowercase letters (a-z)
     * - Digits (0-9)
     * - Special characters (!@#$%^&*()_+-=[]{}|;:,.<>?)
     */
    private fun generateStrongPassword(): String {
        val upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerCase = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val special = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        val allChars = upperCase + lowerCase + digits + special
        
        // Ensure at least one of each type
        val password = StringBuilder()
        password.append(upperCase[Random.nextInt(upperCase.length)])
        password.append(lowerCase[Random.nextInt(lowerCase.length)])
        password.append(digits[Random.nextInt(digits.length)])
        password.append(special[Random.nextInt(special.length)])
        
        // Fill remaining 12 characters randomly
        repeat(12) {
            password.append(allChars[Random.nextInt(allChars.length)])
        }
        
        // Shuffle to randomize positions
        return password.toString().toList().shuffled(Random).joinToString("")
    }

    suspend fun registerUsername(
        displayName: String,
        simpleXAddress: String,
        domain: String
    ): UsernameRegistrationResponse? {
        return when (domain.lowercase()) {
            "link" -> registerPublicUsername(displayName, simpleXAddress)
            "inco" -> registerPrivateUsername(displayName, simpleXAddress)
            else -> null
        }
    }

    /**
     * Register PUBLIC username (.link domain) via new v1 API
     */
    private suspend fun registerPublicUsername(
        displayName: String,
        simpleXAddress: String
    ): UsernameRegistrationResponse? {
        return try {
            val baseUsername = extractBaseUsername(displayName)
            
            if (simpleXAddress.isEmpty() || simpleXAddress.isBlank()) {
                return null
            }
            
            val password = generateStrongPassword()
            val requestBody = LinkRegistrationRequest(
                username = baseUsername,
                password = password,
                tld = "link",
                payload = LinkRegistrationPayload(target = simpleXAddress)
            )
            val jsonBody = json.encodeToString(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            
            val request = Request.Builder()
                .url(PUBLIC_LINK_URL)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            
            client.newCall(request).execute().use { response ->
                response.body?.use { body ->
                    val responseBody = body.string()
                    
                    if (response.isSuccessful) {
                        val apiResponse = json.decodeFromString<UsernameRegistrationResponse>(responseBody)
                        if (apiResponse.success && apiResponse.data != null) {
                            apiResponse
                        } else {
                            apiResponse
                        }
                    } else {
                        try {
                            json.decodeFromString<UsernameRegistrationResponse>(responseBody)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Register PRIVATE username (.inco domain) via new v1 API
     */
    private suspend fun registerPrivateUsername(
        displayName: String,
        simpleXAddress: String
    ): UsernameRegistrationResponse? {
        return try {
            val baseUsername = extractBaseUsername(displayName)
            
            if (simpleXAddress.isEmpty() || simpleXAddress.isBlank()) {
                return null
            }
            
            val requestBody = IncoRegistrationRequest(
                username = baseUsername,
                simplexUri = simpleXAddress,
                tld = "inco"
            )
            val jsonBody = json.encodeToString(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            
            val request = Request.Builder()
                .url(PRIVATE_INCO_URL)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            
            client.newCall(request).execute().use { response ->
                response.body?.use { body ->
                    val responseBody = body.string()
                    
                    if (response.isSuccessful) {
                        val apiResponse = json.decodeFromString<UsernameRegistrationResponse>(responseBody)
                        if (apiResponse.success && apiResponse.data != null) {
                            apiResponse
                        } else {
                            apiResponse
                        }
                    } else {
                        try {
                            json.decodeFromString<UsernameRegistrationResponse>(responseBody)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lookup/search username via resolve endpoint.
     * Supports both .inco and .link usernames (e.g. "gsimm.386.inco", "alice.42.link").
     */
    suspend fun lookupUsername(username: String): UsernameLookupResponse? {
        return try {
            val lower = username.lowercase()
            if (!lower.endsWith(".inco") && !lower.endsWith(".link")) {
                return null
            }
            
            val url = "$RESOLVE_URL/$username"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                response.body?.use { body ->
                    val responseBody = body.string()
                    
                    if (response.isSuccessful) {
                        val apiResponse = json.decodeFromString<UsernameLookupResponse>(responseBody)
                        if (apiResponse.success && apiResponse.data != null) {
                            apiResponse
                        } else {
                            apiResponse
                        }
                    } else {
                        try {
                            json.decodeFromString<UsernameLookupResponse>(responseBody)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

