package chat.simplex.common.views.vault

import android.util.Log
import chat.simplex.common.model.ChatController.appPrefs
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Orchestrates E2EE vault backup using the Dexgram Vault DB backend
 * (prod-vaultdb.dexgram.app) via [VaultApi].
 *
 * Credential model: the user's 16-digit Pro account code is the sole secret.
 *  - The Vault backend uses it to authenticate (POST /auth/login {clientCode}).
 *  - The client derives all AES keys from it locally via PBKDF2 + HKDF.
 *  - The server stores only ciphertext + a bearer token. It never sees vault
 *    file contents, names, or any derived encryption material.
 *
 * Index design:
 *  - One "meta" blob holds the encrypted [BackupIndex] (folder list + file
 *    metadata + the server fileId for each backed-up file).
 *  - The meta blob is tagged with [VaultApi.META_MIME] so a fresh device
 *    signing in with the same Pro code can find and decrypt it via /files.
 *  - Its server fileId is cached locally in [appPrefs.vaultMetaFileId] for
 *    fast incremental updates.
 */
object BackupService {

    private const val TAG = "BackupService"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Returns the cleaned 16-digit Pro code, or null if the user isn't signed in. */
    private fun clientCode(): String? {
        val raw = appPrefs.dexgramAccountId.get() ?: return null
        val cleaned = raw.filter { it.isDigit() }
        return cleaned.takeIf { it.isNotBlank() }
    }

    // ─── Enable Backup ────────────────────────────────────────

    data class EnableResult(val success: Boolean, val error: String? = null)

    suspend fun enableBackup(): EnableResult = withContext(Dispatchers.IO) {
        try {
            val code = clientCode()
                ?: return@withContext EnableResult(false, error = "Sign in to Pro first to enable cloud backup.")

            if (!VaultApi.isLoggedIn()) {
                Log.d(TAG, "enableBackup: vault not logged in, attempting login")
                when (val r = VaultApi.login(code)) {
                    is VaultApi.Result.Error -> {
                        Log.e(TAG, "enableBackup: vault login failed (code=${r.code}, msg=${r.message})")
                        return@withContext EnableResult(false, error = "Vault auth failed: ${r.message}")
                    }
                    is VaultApi.Result.Success -> Log.d(TAG, "enableBackup: vault login OK")
                }
            }

            val indexKey = BackupCrypto.deriveIndexEncKey(code)

            // If a meta blob already exists for this account on the server,
            // adopt it instead of overwriting — this is how a reinstall picks
            // up its previous backups silently. We also reconcile the local
            // `backedUp` flags against the remote index so the UI doesn't lie.
            val existing = downloadRemoteIndex(code)
            if (existing != null) {
                reconcileLocalBackedFlags(existing)
                BackupCrypto.markBackupEnabled()
                BackupCrypto.setLastBackupTime(System.currentTimeMillis())
                Log.d(TAG, "enableBackup: adopted existing remote index (${existing.files.size} files)")
                return@withContext EnableResult(true)
            }

            // No remote index → fresh enable. Local files (if any) are NOT in
            // the cloud yet, so wipe stale `backedUp` flags from prior sessions.
            resetAllLocalBackedFlags()

            val emptyIndex = BackupIndex(
                createdAtMs = System.currentTimeMillis(),
                lastBackupAtMs = System.currentTimeMillis()
            )
            val indexJson = json.encodeToString(emptyIndex).toByteArray(Charsets.UTF_8)
            val encryptedIndex = BackupCrypto.encrypt(indexJson, indexKey)

            when (val r = VaultApi.uploadBlob(VaultApi.META_MIME, encryptedIndex)) {
                is VaultApi.Result.Success -> {
                    appPrefs.vaultMetaFileId.set(r.value)
                    BackupCrypto.markBackupEnabled()
                    BackupCrypto.setLastBackupTime(System.currentTimeMillis())
                    EnableResult(true)
                }
                is VaultApi.Result.Error -> {
                    Log.e(TAG, "Failed to upload initial index: ${r.message}")
                    EnableResult(false, error = "Failed to connect to backup server: ${r.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "enableBackup failed", e)
            EnableResult(false, error = e.message)
        }
    }

    // ─── Disable Backup ───────────────────────────────────────

    fun disableBackup() {
        BackupCrypto.disableBackup()
        VaultApi.clearSession()
        // Server-side blobs are intentionally NOT deleted — the user can come
        // back, sign in again, and pick up where they left off. Wipe is opt-in
        // via [wipeServerStorage] below.
    }

    /**
     * Deletes every blob the authenticated user owns on the vault server,
     * then clears local backup state. Use to recover from orphaned/un-decryptable
     * meta blobs (e.g. left over from earlier test cycles) or to fully reset.
     *
     * @return number of files deleted server-side, or -1 on auth failure.
     */
    suspend fun wipeServerStorage(): Int = withContext(Dispatchers.IO) {
        val code = clientCode() ?: return@withContext -1
        if (!VaultApi.isLoggedIn()) {
            if (VaultApi.login(code) is VaultApi.Result.Error) return@withContext -1
        }
        // List everything (not just meta) and delete one-by-one.
        val list = VaultApi.listFiles()
        if (list !is VaultApi.Result.Success) return@withContext -1
        var deleted = 0
        for (f in list.value) {
            if (VaultApi.deleteFile(f.id) is VaultApi.Result.Success) deleted++
        }
        // Reset local state — backup is effectively disabled until the user
        // re-enables it (which will upload a fresh empty meta).
        BackupCrypto.disableBackup()
        appPrefs.vaultMetaFileId.set("")
        // Wipe per-file `backedUp` flags so the UI doesn't keep claiming files
        // are synced to a server that no longer has them.
        resetAllLocalBackedFlags()
        Log.d(TAG, "wipeServerStorage: deleted $deleted server-side blobs")
        deleted
    }

    // ─── Local backedUp-flag reconciliation ───────────────────

    /** Clears the `backedUp` flag on every local vault file. */
    private fun resetAllLocalBackedFlags() {
        try {
            for (e in VaultStorage.getIndex().files) {
                if (e.backedUp) VaultStorage.markFileBacked(e.id, false)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "resetAllLocalBackedFlags failed", t)
        }
    }

    /** Sets each local file's `backedUp` flag to match whether it's listed in the remote index. */
    private fun reconcileLocalBackedFlags(remote: BackupIndex) {
        try {
            val remoteIds = remote.files.map { it.id }.toHashSet()
            for (e in VaultStorage.getIndex().files) {
                val shouldBeBacked = e.id in remoteIds
                if (e.backedUp != shouldBeBacked) {
                    VaultStorage.markFileBacked(e.id, shouldBeBacked)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "reconcileLocalBackedFlags failed", t)
        }
    }

    // ─── Backup Single File ───────────────────────────────────

    suspend fun backupFile(entry: VaultFileEntry, folderPassword: String? = null): Boolean = withContext(Dispatchers.IO) {
        val code = clientCode() ?: return@withContext false
        try {
            val plaintext = VaultStorage.decryptFile(entry, folderPassword)
            if (plaintext == null) {
                Log.w(TAG, "Cannot decrypt ${entry.id} for backup (double-encrypted without password?)")
                return@withContext false
            }
            val backupKey = BackupCrypto.deriveBackupEncKey(code)
            val bakBytes = BackupCrypto.wrapForBackup(plaintext, backupKey)

            when (val r = VaultApi.uploadBlob("application/octet-stream", bakBytes)) {
                is VaultApi.Result.Success -> {
                    VaultStorage.markFileBacked(entry.id, true)
                    updateRemoteIndex(code, addition = entry.id to r.value)
                    true
                }
                is VaultApi.Result.Error -> {
                    Log.w(TAG, "Upload ${entry.id} failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "backupFile ${entry.id} failed", e)
            false
        }
    }

    // ─── Backup All Un-backed Files ───────────────────────────

    data class BackupProgress(val current: Int, val total: Int, val done: Boolean, val errors: Int = 0)

    suspend fun backupAll(onProgress: (BackupProgress) -> Unit = {}): BackupProgress = withContext(Dispatchers.IO) {
        val code = clientCode()
        if (code == null) {
            val p = BackupProgress(0, 0, true, 1)
            onProgress(p); return@withContext p
        }

        val index = VaultStorage.getIndex()
        val allFiles = index.files
        val total = allFiles.size
        if (total == 0) {
            val p = BackupProgress(0, 0, true)
            onProgress(p); return@withContext p
        }

        var done = 0
        var errors = 0
        for (entry in allFiles) {
            val ok = backupFile(entry)
            if (ok) done++ else errors++
            onProgress(BackupProgress(done + errors, total, false, errors))
        }

        updateRemoteIndex(code)
        BackupCrypto.setLastBackupTime(System.currentTimeMillis())
        val p = BackupProgress(done, total, true, errors)
        onProgress(p)
        p
    }

    // ─── Delete Backup of a File ──────────────────────────────

    suspend fun deleteBackup(entryId: String): Boolean = withContext(Dispatchers.IO) {
        val code = clientCode() ?: return@withContext false
        try {
            val remote = downloadRemoteIndex(code) ?: return@withContext false
            val meta = remote.files.find { it.id == entryId } ?: return@withContext false
            if (meta.serverFileId.isBlank()) return@withContext false

            when (VaultApi.deleteFile(meta.serverFileId)) {
                is VaultApi.Result.Success -> {
                    updateRemoteIndex(code, removalEntryId = entryId)
                    true
                }
                is VaultApi.Result.Error -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteBackup $entryId failed", e)
            false
        }
    }

    // ─── Restore From Cloud (using the signed-in Pro code) ────

    data class RestoreProgress(val current: Int, val total: Int, val done: Boolean, val errors: Int = 0)

    suspend fun restoreFromCloud(
        onProgress: (RestoreProgress) -> Unit = {}
    ): RestoreProgress = withContext(Dispatchers.IO) {
        try {
            val code = clientCode()
            if (code == null) {
                Log.w(TAG, "restore: no Pro account code stored")
                val p = RestoreProgress(0, 0, true, 1); onProgress(p); return@withContext p
            }
            if (!VaultApi.isLoggedIn()) {
                when (val r = VaultApi.login(code)) {
                    is VaultApi.Result.Error -> {
                        Log.e(TAG, "restore: vault login failed: ${r.message}")
                        val p = RestoreProgress(0, 0, true, 1); onProgress(p); return@withContext p
                    }
                    is VaultApi.Result.Success -> Unit
                }
            }

            val remoteIndex = downloadRemoteIndex(code)
            if (remoteIndex == null) {
                Log.w(TAG, "restore: no remote index found")
                val p = RestoreProgress(0, 0, true, 1); onProgress(p); return@withContext p
            }

            val backupKey = BackupCrypto.deriveBackupEncKey(code)
            val localIndex = VaultStorage.getIndex()

            for (folderMeta in remoteIndex.folders) {
                if (localIndex.folders.none { it.id == folderMeta.id }) {
                    val folder = VaultFolder(
                        id = folderMeta.id,
                        name = folderMeta.name,
                        hasPassword = folderMeta.hasPassword,
                        passwordSalt = folderMeta.passwordSalt,
                        passwordVerifier = folderMeta.passwordVerifier,
                        createdAtMs = folderMeta.createdAtMs
                    )
                    VaultStorage.restoreFolder(folder)
                }
            }

            val total = remoteIndex.files.size
            if (total == 0) {
                BackupCrypto.markBackupEnabled()
                val p = RestoreProgress(0, 0, true); onProgress(p); return@withContext p
            }

            var restored = 0
            var errors = 0

            for (fileMeta in remoteIndex.files) {
                try {
                    val currentIndex = VaultStorage.getIndex()
                    if (currentIndex.files.any { it.id == fileMeta.id }) {
                        restored++
                        onProgress(RestoreProgress(restored + errors, total, false, errors))
                        continue
                    }

                    if (fileMeta.serverFileId.isBlank()) {
                        Log.w(TAG, "File ${fileMeta.id} has no serverFileId — old format?")
                        errors++
                        onProgress(RestoreProgress(restored + errors, total, false, errors))
                        continue
                    }

                    when (val r = VaultApi.downloadBlob(fileMeta.serverFileId)) {
                        is VaultApi.Result.Error -> {
                            errors++
                            onProgress(RestoreProgress(restored + errors, total, false, errors))
                            continue
                        }
                        is VaultApi.Result.Success -> {
                            val plaintext = BackupCrypto.unwrapFromBackup(r.value, backupKey)
                            if (plaintext == null) {
                                Log.w(TAG, "Failed to decrypt backup file ${fileMeta.id}")
                                errors++
                                onProgress(RestoreProgress(restored + errors, total, false, errors))
                                continue
                            }
                            val entry = VaultFileEntry(
                                id = fileMeta.id,
                                folderId = fileMeta.folderId,
                                originalName = fileMeta.originalName,
                                fileType = fileMeta.fileType,
                                sizeBytes = fileMeta.sizeBytes,
                                mimeType = fileMeta.mimeType,
                                isDoubleEncrypted = false,
                                addedAtMs = fileMeta.addedAtMs,
                                backedUp = true
                            )
                            VaultStorage.restoreVaultFile(fileMeta.id, plaintext, entry)
                            restored++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Restore file ${fileMeta.id} failed", e)
                    errors++
                }
                onProgress(RestoreProgress(restored + errors, total, false, errors))
            }

            BackupCrypto.markBackupEnabled()
            BackupCrypto.setLastBackupTime(System.currentTimeMillis())

            val p = RestoreProgress(restored, total, true, errors)
            onProgress(p)
            p
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromCloud failed", e)
            val p = RestoreProgress(0, 0, true, 1)
            onProgress(p)
            p
        }
    }

    // ─── Get Backup Status ────────────────────────────────────

    fun getStatus(): BackupStatus {
        val enabled = BackupCrypto.isBackupEnabled()
        if (!enabled) return BackupStatus()

        val index = VaultStorage.getIndex()
        val backed = index.files.count { it.backedUp }
        return BackupStatus(
            enabled = true,
            lastBackupAtMs = BackupCrypto.getLastBackupTime(),
            filesBacked = backed,
            totalFiles = index.files.size
        )
    }

    // ─── Remote Encrypted Index helpers ───────────────────────

    /**
     * Downloads + decrypts the remote index.
     *
     * Strategy (defensive against orphaned blobs from previous sessions):
     *  1. Try the cached meta fileId from prefs (fast path for known-good).
     *  2. If that fails, list every meta-mime blob on the server, sorted
     *     newest-first, and try each one until one decrypts cleanly.
     *  3. The first one that decrypts wins — it's the authoritative index
     *     for the current key derivation. Its fileId is cached in prefs.
     */
    private fun downloadRemoteIndex(clientCode: String): BackupIndex? {
        val indexKey = BackupCrypto.deriveIndexEncKey(clientCode)

        fun tryDecrypt(fileId: String, blob: ByteArray): BackupIndex? {
            return try {
                val plain = BackupCrypto.decrypt(blob, indexKey)
                json.decodeFromString<BackupIndex>(String(plain, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Meta decrypt failed for $fileId — " +
                        "${e.javaClass.simpleName}: ${e.message} " +
                        "(blob=${blob.size}B, head=${blob.take(8).joinToString(",") { "%02x".format(it) }})"
                )
                null
            }
        }

        // Fast path: cached id
        val cached = appPrefs.vaultMetaFileId.get()
        if (!cached.isNullOrBlank()) {
            when (val r = VaultApi.downloadBlob(cached)) {
                is VaultApi.Result.Success -> tryDecrypt(cached, r.value)?.let {
                    Log.d(TAG, "Meta decrypted via cached fileId $cached")
                    return it
                }
                is VaultApi.Result.Error -> Log.w(TAG, "Cached meta fileId fetch failed: ${r.message}")
            }
        }

        // Slow path: try every meta-mime blob newest-first
        val candidates = VaultApi.findMetaFiles()
        if (candidates.isEmpty()) {
            Log.w(TAG, "downloadRemoteIndex: no meta-mime blobs on server")
            return null
        }
        // Skip the cached id we already tried.
        val toTry = if (!cached.isNullOrBlank()) candidates.filterNot { it == cached } else candidates
        for (candidateId in toTry) {
            when (val r = VaultApi.downloadBlob(candidateId)) {
                is VaultApi.Result.Success -> {
                    val index = tryDecrypt(candidateId, r.value)
                    if (index != null) {
                        Log.d(TAG, "Meta decrypted from candidate $candidateId — caching")
                        appPrefs.vaultMetaFileId.set(candidateId)
                        return index
                    }
                }
                is VaultApi.Result.Error -> {
                    Log.w(TAG, "Failed to download candidate $candidateId: ${r.message}")
                }
            }
        }

        Log.w(TAG, "downloadRemoteIndex: tried ${toTry.size + if (!cached.isNullOrBlank()) 1 else 0} candidate(s), none decrypted")
        return null
    }

    private fun updateRemoteIndex(
        clientCode: String,
        addition: Pair<String, String>? = null,
        removalEntryId: String? = null
    ) {
        try {
            val indexKey = BackupCrypto.deriveIndexEncKey(clientCode)
            val previous = downloadRemoteIndex(clientCode)
            val previousFilesById = previous?.files?.associateBy { it.id } ?: emptyMap()

            val localIndex = VaultStorage.getIndex()
            val backedFiles = localIndex.files.filter { it.backedUp }

            val mergedFiles = backedFiles.map { e ->
                val prev = previousFilesById[e.id]
                val serverId = when {
                    addition?.first == e.id -> addition.second
                    prev != null && prev.serverFileId.isNotBlank() -> prev.serverFileId
                    else -> ""
                }
                BackupFileMeta(
                    id = e.id,
                    folderId = e.folderId,
                    originalName = e.originalName,
                    fileType = e.fileType,
                    sizeBytes = e.sizeBytes,
                    mimeType = e.mimeType,
                    isDoubleEncrypted = e.isDoubleEncrypted,
                    addedAtMs = e.addedAtMs,
                    serverFileId = serverId
                )
            }.let { list ->
                if (removalEntryId != null) list.filter { it.id != removalEntryId } else list
            }

            val newIndex = BackupIndex(
                createdAtMs = previous?.createdAtMs ?: System.currentTimeMillis(),
                lastBackupAtMs = System.currentTimeMillis(),
                folders = localIndex.folders.map { f ->
                    BackupFolderMeta(
                        id = f.id,
                        name = f.name,
                        hasPassword = f.hasPassword,
                        passwordSalt = f.passwordSalt,
                        passwordVerifier = f.passwordVerifier,
                        createdAtMs = f.createdAtMs
                    )
                },
                files = mergedFiles
            )

            val indexJson = json.encodeToString(newIndex).toByteArray(Charsets.UTF_8)
            val encrypted = BackupCrypto.encrypt(indexJson, indexKey)
            when (val r = VaultApi.uploadBlob(VaultApi.META_MIME, encrypted)) {
                is VaultApi.Result.Success -> {
                    val previousMetaId = appPrefs.vaultMetaFileId.get()
                    appPrefs.vaultMetaFileId.set(r.value)
                    BackupCrypto.setLastBackupTime(System.currentTimeMillis())
                    if (!previousMetaId.isNullOrBlank() && previousMetaId != r.value) {
                        VaultApi.deleteFile(previousMetaId)
                    }
                }
                is VaultApi.Result.Error -> {
                    Log.e(TAG, "Failed to upload updated index: ${r.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateRemoteIndex failed", e)
        }
    }
}
