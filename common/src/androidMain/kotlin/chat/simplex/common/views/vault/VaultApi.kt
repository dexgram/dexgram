package chat.simplex.common.views.vault

import android.util.Log
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.views.wallet.SecureHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Dexgram Vault backend client — replaces the old direct Backblaze B2 S3 client.
 *
 * Backend: https://prod-vaultdb.dexgram.app
 * Auth   : Bearer token returned by /auth/login {clientCode} — the same
 *          16-digit code the user uses for the Pro subscription.
 *
 * Upload is a 3-step presigned flow:
 *   POST /uploads/request {mimeType, sizeBytes} → {uploadUrl, fileId, contentType?, contentLength?}
 *   PUT  uploadUrl  (raw bytes, with Content-Type and Content-Length from the response if provided)
 *   POST /uploads/complete {fileId}
 *
 * Download is a 2-step presigned flow:
 *   GET  /files/{fileId}/download → {downloadUrl}
 *   GET  downloadUrl              → raw bytes
 *
 * Delete: DELETE /files/{fileId}
 * List  : GET    /files           → [{id, mimeType, sizeBytes, createdAt, ...}, ...]
 *
 * No credentials are embedded in the APK — auth is per-user, scoped to the
 * caller's own files server-side.
 */
object VaultApi {

    private const val TAG = "VaultApi"
    const val BASE_URL = "https://prod-vaultdb.dexgram.app"

    /** Magic mime-type for the encrypted index blob — used to find it on a fresh device via list. */
    const val META_MIME = "application/x-dexgram-vault-meta"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = SecureHttpClient.client

    // ─── Result type ───────────────────────────────────────────

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Error(val code: Int, val message: String) : Result<Nothing>()
    }

    // ─── DTOs ──────────────────────────────────────────────────

    @Serializable private data class LoginReq(val clientCode: String)
    @Serializable private data class LoginResp(
        val token: String,
        val expiresInSeconds: Long = 0L,
        val subscriptionActive: Boolean = true
        // expiresAt is an ISO-8601 string — ignored; expiresInSeconds is enough.
    )

    @Serializable private data class UploadReq(val mimeType: String, val sizeBytes: Long)
    @Serializable private data class UploadResp(
        val uploadUrl: String,
        val fileId: String,
        val objectKey: String = "",
        // Headers the presigned URL was signed with. MUST be sent verbatim on PUT.
        val requiredHeaders: Map<String, String> = emptyMap()
    )

    @Serializable private data class CompleteReq(val fileId: String)
    @Serializable private data class DownloadResp(val downloadUrl: String)

    @Serializable private data class UsageResp(
        val clientCode: String = "",
        val usedBytes: Long = 0L,
        // Quota is reported in GB (may be fractional); converted to bytes for the UI.
        val quotaGb: Double = 0.0
    )

    /** Tracked vault usage vs. the account quota, both in bytes. */
    data class UsageInfo(val usedBytes: Long, val quotaBytes: Long)

    /**
     * One entry from `GET /files`. Parsed defensively because the backend's
     * exact field names + types aren't formally documented.
     *
     * Known variations we tolerate:
     *  - id field:        `fileId` (current) or `id`
     *  - mimeType field:  `mimeType` or `mime_type` or `contentType`
     *  - sizeBytes field: `sizeBytes` or `size` or `contentLength`
     *  - createdAt:       ISO-8601 string (current) or Long unix ms
     */
    data class FileInfo(
        val id: String,
        val mimeType: String = "",
        val sizeBytes: Long = 0L,
        val createdAtMs: Long = 0L
    )

    // ─── Session ───────────────────────────────────────────────

    fun isLoggedIn(): Boolean {
        val tok = appPrefs.vaultToken.get() ?: ""
        return tok.isNotBlank()
    }

    fun clearSession() {
        appPrefs.vaultToken.set("")
        appPrefs.vaultExpiresAt.set(0L)
        appPrefs.vaultMetaFileId.set("")
    }

    /**
     * Authenticate with the vault backend using the same 16-digit code the
     * user activates Pro with. The returned Bearer token is cached in prefs.
     *
     * The vault backend appears to be strict about format — we strip every
     * non-digit before sending (matches the same normalization [DexgramApi]
     * does on its own login).
     */
    fun login(clientCode: String): Result<Unit> {
        val cleaned = clientCode.filter { it.isDigit() }
        if (cleaned.isEmpty()) {
            Log.w(TAG, "login: client code is empty after sanitization (raw='$clientCode')")
            return Result.Error(-1, "Empty client code")
        }
        val (ok, err) = postJsonRaw("$BASE_URL/auth/login", json.encodeToString(LoginReq(cleaned)), authed = false)
        if (ok == null) {
            Log.w(TAG, "login: auth/login failed — ${err ?: "unknown"}")
            return Result.Error(-1, err ?: "Login failed")
        }
        return try {
            val resp = json.decodeFromString<LoginResp>(ok)
            if (resp.token.isBlank()) {
                Log.w(TAG, "login: server returned empty token (body=$ok)")
                return Result.Error(-1, "Empty token from server")
            }
            if (!resp.subscriptionActive) {
                Log.w(TAG, "login: server reports subscriptionActive=false")
                return Result.Error(403, "Pro subscription is not active for this account.")
            }
            val expiresAtMs = if (resp.expiresInSeconds > 0L) {
                System.currentTimeMillis() + resp.expiresInSeconds * 1000L
            } else 0L
            appPrefs.vaultToken.set(resp.token)
            appPrefs.vaultExpiresAt.set(expiresAtMs)
            Log.d(TAG, "login OK — token cached (expiresInSeconds=${resp.expiresInSeconds})")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "login: bad response shape — body=$ok", e)
            Result.Error(-1, "Bad login response: ${e.message}")
        }
    }

    /**
     * Ensures we have a valid token; tries to log in with the saved Pro
     * account id if missing.
     * @return null on success, otherwise a human-readable error string.
     */
    private fun ensureLoggedIn(): String? {
        if (isLoggedIn()) return null
        val code = appPrefs.dexgramAccountId.get()
        if (code.isNullOrBlank()) {
            return "No Pro account id stored — sign in to Pro first"
        }
        return when (val r = login(code)) {
            is Result.Success -> null
            is Result.Error -> "Vault login failed (HTTP ${r.code}): ${r.message}"
        }
    }

    // ─── Upload (3-step presigned flow) ────────────────────────

    /**
     * Encrypt-then-upload helper: pass already-encrypted bytes; returns the
     * server-assigned fileId on success. Mime-type for ordinary backup blobs
     * should be "application/octet-stream"; the index uses [META_MIME].
     */
    fun uploadBlob(mimeType: String, bytes: ByteArray): Result<String> {
        ensureLoggedIn()?.let { return Result.Error(401, it) }

        // Step 1 — request presigned URL
        val reqBody = json.encodeToString(UploadReq(mimeType, bytes.size.toLong()))
        val (s1, e1) = postJsonRaw("$BASE_URL/uploads/request", reqBody, authed = true)
        if (s1 == null) return Result.Error(-1, e1 ?: "uploads/request failed")
        val ur = try { json.decodeFromString<UploadResp>(s1) }
                 catch (e: Exception) { return Result.Error(-1, "Bad upload response: ${e.message}") }

        // Step 2 — PUT raw bytes to presigned URL.
        // The server signs the URL against exact headers — we MUST send what
        // `requiredHeaders` lists. Header names come back lowercase.
        val signedCt = ur.requiredHeaders["content-type"] ?: mimeType
        val signedCl = ur.requiredHeaders["content-length"]?.toLongOrNull() ?: bytes.size.toLong()
        if (signedCl != bytes.size.toLong()) {
            Log.w(TAG, "PUT: signed content-length=$signedCl but bytes.size=${bytes.size}")
        }
        val putOk = putBytes(ur.uploadUrl, bytes, signedCt)
        if (!putOk) return Result.Error(-1, "PUT to presigned URL failed")

        // Step 3 — mark complete
        val (s3, e3) = postJsonRaw("$BASE_URL/uploads/complete", json.encodeToString(CompleteReq(ur.fileId)), authed = true)
        if (s3 == null) return Result.Error(-1, e3 ?: "uploads/complete failed")

        return Result.Success(ur.fileId)
    }

    // ─── Download (2-step presigned flow) ──────────────────────

    fun downloadBlob(fileId: String): Result<ByteArray> {
        ensureLoggedIn()?.let { return Result.Error(401, it) }

        val (s, e) = getJsonRaw("$BASE_URL/files/$fileId/download")
        if (s == null) return Result.Error(-1, e ?: "files/$fileId/download failed")
        val ur = try { json.decodeFromString<DownloadResp>(s) }
                 catch (ex: Exception) { return Result.Error(-1, "Bad download response: ${ex.message}") }

        val bytes = getBytes(ur.downloadUrl) ?: return Result.Error(-1, "Presigned GET failed")
        return Result.Success(bytes)
    }

    // ─── Usage / quota ─────────────────────────────────────────

    /**
     * Returns the account's tracked vault usage and total quota (both bytes).
     * `GET /usage → {clientCode, usedBytes, quotaGb}`.
     */
    fun getUsage(): Result<UsageInfo> {
        ensureLoggedIn()?.let { return Result.Error(401, it) }
        val (s, e) = getJsonRaw("$BASE_URL/usage")
        if (s == null) return Result.Error(-1, e ?: "usage failed")
        return try {
            val r = json.decodeFromString<UsageResp>(s)
            val quotaBytes = (r.quotaGb * 1073741824.0).toLong()
            Result.Success(UsageInfo(usedBytes = r.usedBytes, quotaBytes = quotaBytes))
        } catch (ex: Exception) {
            Log.e(TAG, "getUsage: bad response — body=${s.take(300)}", ex)
            Result.Error(-1, "Bad usage response: ${ex.message}")
        }
    }

    // ─── Delete ────────────────────────────────────────────────

    fun deleteFile(fileId: String): Result<Unit> {
        ensureLoggedIn()?.let { return Result.Error(401, it) }
        return try {
            val req = Request.Builder()
                .url("$BASE_URL/files/$fileId")
                .header("Authorization", "Bearer ${appPrefs.vaultToken.get() ?: ""}")
                .header("Accept", "application/json")
                .delete()
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.Success(Unit)
                else Result.Error(resp.code, "DELETE returned ${resp.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile $fileId failed", e)
            Result.Error(-1, e.message ?: "delete failed")
        }
    }

    // ─── List ──────────────────────────────────────────────────

    /**
     * Lists every file the authenticated user owns on the vault backend.
     *
     * Response shapes seen / tolerated:
     *   bare array  : `[ { "fileId": "...", "mimeType": "...", ... }, ... ]`
     *   wrapped     : `{ "files": [ ... ] }`
     *   wrapped alt : `{ "items": [ ... ] }`
     */
    fun listFiles(): Result<List<FileInfo>> {
        ensureLoggedIn()?.let { return Result.Error(401, it) }
        val (s, e) = getJsonRaw("$BASE_URL/files")
        if (s == null) return Result.Error(-1, e ?: "files list failed")
        return try {
            val root: JsonElement = json.parseToJsonElement(s)
            val arr: JsonArray = when {
                root is JsonArray -> root
                root is JsonObject && root["files"] is JsonArray -> root["files"]!!.jsonArray
                root is JsonObject && root["items"] is JsonArray -> root["items"]!!.jsonArray
                root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
                else -> {
                    Log.w(TAG, "listFiles: unrecognized root shape — body=${s.take(300)}")
                    return Result.Error(-1, "Unrecognized /files response shape")
                }
            }
            val list = arr.mapNotNull { el ->
                if (el !is JsonObject) return@mapNotNull null
                parseFileInfo(el)
            }
            Log.d(TAG, "listFiles: parsed ${list.size} entries")
            Result.Success(list)
        } catch (ex: Exception) {
            Log.e(TAG, "listFiles: parse failed — body=${s.take(300)}", ex)
            Result.Error(-1, "Bad list response: ${ex.message}")
        }
    }

    private fun parseFileInfo(o: JsonObject): FileInfo? {
        fun str(vararg keys: String): String? {
            for (k in keys) (o[k] as? JsonPrimitive)?.let { if (it.isString || it.content.isNotEmpty()) return it.content }
            return null
        }
        fun long(vararg keys: String): Long {
            for (k in keys) (o[k] as? JsonPrimitive)?.let {
                it.content.toLongOrNull()?.let { v -> return v }
            }
            return 0L
        }

        val id = str("fileId", "id", "file_id") ?: return null
        val mime = str("mimeType", "mime_type", "contentType", "content_type") ?: ""
        val size = long("sizeBytes", "size_bytes", "size", "contentLength", "content_length")
        val createdAtMs = run {
            val raw = str("createdAt", "created_at", "uploadedAt", "uploaded_at") ?: return@run 0L
            // ISO-8601 first (e.g. "2026-05-21T22:05:55.647Z"), Long ms fallback.
            parseIso8601Ms(raw) ?: raw.toLongOrNull() ?: 0L
        }
        return FileInfo(id = id, mimeType = mime, sizeBytes = size, createdAtMs = createdAtMs)
    }

    private fun parseIso8601Ms(s: String): Long? = try {
        // Java 8+ Instant — handles `2026-05-21T22:05:55.647Z` and `+00:00` offsets.
        java.time.Instant.parse(s.replace("+00:00", "Z")).toEpochMilli()
    } catch (_: Exception) {
        // Try OffsetDateTime for non-Z offsets like "+05:00".
        try { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        catch (_: Exception) { null }
    }

    /** Picks the most recent file whose mimeType identifies the encrypted index. */
    fun findMetaFile(): String? = findMetaFiles().firstOrNull()

    /**
     * Returns every blob with the meta mime-type on the server, sorted
     * newest-first. Used so we can try each candidate when the newest one
     * fails to decrypt (e.g. it's an orphaned blob from an earlier session
     * with a different key derivation).
     */
    fun findMetaFiles(): List<String> {
        val list = listFiles()
        if (list !is Result.Success) return emptyList()
        val metaFiles = list.value.filter { it.mimeType == META_MIME }
        if (metaFiles.isEmpty()) {
            Log.d(TAG, "findMetaFiles: no $META_MIME file found among ${list.value.size} files")
            return emptyList()
        }
        val sorted = metaFiles.sortedByDescending { it.createdAtMs }
        Log.d(TAG, "findMetaFiles: ${sorted.size} candidate(s) — newest=${sorted.first().id} (createdAtMs=${sorted.first().createdAtMs})")
        return sorted.map { it.id }
    }

    /** Removes every meta-mime blob from the server. Used by "reset cloud backup". */
    fun deleteAllMetaFiles(): Int {
        var deleted = 0
        for (id in findMetaFiles()) {
            if (deleteFile(id) is Result.Success) deleted++
        }
        return deleted
    }

    // ─── Low-level HTTP (uses the pinned OkHttp client) ────────

    private fun postJsonRaw(url: String, body: String, authed: Boolean): Pair<String?, String?> {
        return try {
            val rb = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(body.toRequestBody(JSON_TYPE))
            if (authed) {
                val tok = appPrefs.vaultToken.get() ?: ""
                if (tok.isNotBlank()) rb.header("Authorization", "Bearer $tok")
            }
            http.newCall(rb.build()).execute().use { resp ->
                val text = try { resp.body?.string() } catch (_: Exception) { null }
                if (resp.code == 401 && authed) {
                    // Token might have expired — drop it so the next ensureLoggedIn() re-logs in.
                    appPrefs.vaultToken.set("")
                    appPrefs.vaultExpiresAt.set(0L)
                }
                if (resp.isSuccessful) {
                    Pair(text, null)
                } else {
                    Log.w(TAG, "POST $url → HTTP ${resp.code} body=${text?.take(500)}")
                    Pair(null, "HTTP ${resp.code}: ${text?.take(300) ?: "(no body)"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $url threw", e)
            Pair(null, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun getJsonRaw(url: String): Pair<String?, String?> {
        return try {
            val tok = appPrefs.vaultToken.get() ?: ""
            val rb = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
            if (tok.isNotBlank()) rb.header("Authorization", "Bearer $tok")
            http.newCall(rb.build()).execute().use { resp ->
                val text = try { resp.body?.string() } catch (_: Exception) { null }
                if (resp.code == 401) {
                    appPrefs.vaultToken.set("")
                    appPrefs.vaultExpiresAt.set(0L)
                }
                if (resp.isSuccessful) {
                    Pair(text, null)
                } else {
                    Log.w(TAG, "GET $url → HTTP ${resp.code} body=${text?.take(500)}")
                    Pair(null, "HTTP ${resp.code}: ${text?.take(300) ?: "(no body)"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $url threw", e)
            Pair(null, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Uploads raw bytes to a presigned URL.
     *
     * NOTE: We deliberately don't set Content-Length ourselves — OkHttp derives
     * it from the [RequestBody.contentLength] of the byte array and writes it
     * automatically. Adding it as a `.header(...)` would either be a duplicate
     * or get silently dropped, and the presigned URL signs the canonical
     * lowercase `content-length` header value, so it must match exactly.
     */
    private fun putBytes(url: String, data: ByteArray, contentType: String): Boolean {
        return try {
            val mediaType = contentType.toMediaType()
            val rb = Request.Builder()
                .url(url)
                .put(data.toRequestBody(mediaType))
            http.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = try { resp.body?.string()?.take(500) } catch (_: Exception) { null }
                    Log.w(TAG, "PUT presigned → HTTP ${resp.code} body=$errBody")
                }
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "PUT $url threw", e)
            false
        }
    }

    /**
     * Downloads raw bytes from a presigned URL with a small retry, since these
     * URLs are valid for ~5 minutes and transient B2 GET failures are common.
     * Logs the HTTP status / exception so a failure isn't silent.
     */
    private fun getBytes(url: String): ByteArray? {
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                val rb = Request.Builder().url(url).header("Accept", "*/*")
                http.newCall(rb.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return resp.body?.bytes()
                    } else {
                        Log.w(TAG, "GET-bytes attempt $attempt/$maxAttempts → HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GET-bytes attempt $attempt/$maxAttempts threw ${e.javaClass.simpleName}: ${e.message}")
            }
            if (attempt < maxAttempts) {
                try { Thread.sleep(400L * attempt) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }
        Log.e(TAG, "GET-bytes failed after $maxAttempts attempts")
        return null
    }
}
