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

    data class EnableResult(val success: Boolean, val error: String? = null, val newPassphrase: String? = null)

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

            // Ensure this device has a 24-word recovery phrase — the secret that
            // actually encrypts the backup (independent of the shareable Pro code).
            // Generated once; returned so the UI can show it to the user to save.
            var newPassphrase: String? = null
            if (!BackupCrypto.hasRecoveryPhrase()) {
                val phrase = BackupCrypto.generateRecoveryPhrase()
                BackupCrypto.setRecoveryPhrase(phrase)
                newPassphrase = phrase
                Log.d(TAG, "enableBackup: generated a new recovery phrase for this account")
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
                return@withContext EnableResult(true, newPassphrase = newPassphrase)
            }

            // No remote index → fresh enable. Local files (if any) are NOT in
            // the cloud yet, so wipe stale `backedUp` flags from prior sessions.
            resetAllLocalBackedFlags()

            // Seed the initial index with the current folder structure so even
            // empty / locked folders are backed up immediately on enable.
            val localIndex = VaultStorage.getIndex()
            val initialIndex = BackupIndex(
                createdAtMs = System.currentTimeMillis(),
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
                }
            )
            val indexJson = json.encodeToString(initialIndex).toByteArray(Charsets.UTF_8)
            val encryptedIndex = BackupCrypto.encrypt(indexJson, indexKey)

            when (val r = VaultApi.uploadBlob(VaultApi.META_MIME, encryptedIndex)) {
                is VaultApi.Result.Success -> {
                    appPrefs.vaultMetaFileId.set(r.value)
                    BackupCrypto.markBackupEnabled()
                    BackupCrypto.setLastBackupTime(System.currentTimeMillis())
                    EnableResult(true, newPassphrase = newPassphrase)
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
                    // Persist the server id locally so the remote index can always
                    // be rebuilt from local state, even if downloading the previous
                    // index fails transiently.
                    VaultStorage.setFileServerId(entry.id, r.value)
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

    /**
     * Backs up all eligible files.
     *
     * @param folderPasswords map of folderId → password for password-protected
     *   folders the user has unlocked for this backup. Files in such folders are
     *   double-encrypted and can only be uploaded if the password is supplied here.
     */
    suspend fun backupAll(
        folderPasswords: Map<String, String> = emptyMap(),
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupProgress = withContext(Dispatchers.IO) {
        val code = clientCode()
        if (code == null) {
            val p = BackupProgress(0, 0, true, 1)
            onProgress(p); return@withContext p
        }

        val index = VaultStorage.getIndex()
        // A file is backupable when it's not double-encrypted, OR it's in a
        // password folder whose password we were given.
        val backupable = index.files.filter { e ->
            !e.isDoubleEncrypted || (e.folderId != null && folderPasswords.containsKey(e.folderId))
        }
        val total = backupable.size

        var done = 0
        var errors = 0
        for (entry in backupable) {
            val pwd = entry.folderId?.let { folderPasswords[it] }
            val ok = backupFile(entry, pwd)
            if (ok) done++ else errors++
            onProgress(BackupProgress(done + errors, total, false, errors))
        }

        // Always sync the remote index — even with zero files — so the folder
        // structure (including empty or still-locked folders) is backed up and
        // can be recreated on restore.
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
        selectedIds: Set<String>? = null,
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

            // Only files that were actually uploaded (have a serverFileId) can be
            // restored. Entries without one were never in the cloud (e.g. password
            // folder files that couldn't be backed up, or stale index entries) —
            // exclude them from the count so they don't surface as restore errors.
            val withServerId = remoteIndex.files.filter { it.serverFileId.isNotBlank() }
            val restorable = withServerId.filter { selectedIds == null || it.id in selectedIds }
            val skippedNoServerId = remoteIndex.files.size - withServerId.size
            if (skippedNoServerId > 0) {
                Log.w(TAG, "restore: skipping $skippedNoServerId index entr(ies) with no serverFileId")
            }
            val total = restorable.size
            if (total == 0) {
                // The remote index has nothing to restore. On a flaky backend a
                // file blob can upload successfully while the meta update that
                // records it is lost to a 503 — leaving the blob orphaned from
                // the index. If THIS device still remembers backed-up files
                // (serverFileId persisted locally), repair the remote index from
                // local state so it's not lost for future devices.
                val locallyKnown = localIndex.files.filter { it.backedUp && it.serverFileId.isNotBlank() }
                val remoteIds = remoteIndex.files.map { it.id }.toHashSet()
                val missingFromRemote = locallyKnown.filter { it.id !in remoteIds }
                if (missingFromRemote.isNotEmpty()) {
                    Log.w(TAG, "restore: remote index missing ${missingFromRemote.size} locally-backed file(s) — repairing remote index")
                    updateRemoteIndex(code)
                }
                BackupCrypto.markBackupEnabled()
                val p = RestoreProgress(0, 0, true); onProgress(p); return@withContext p
            }

            var restored = 0
            var errors = 0

            for (fileMeta in restorable) {
                try {
                    val currentIndex = VaultStorage.getIndex()
                    if (currentIndex.files.any { it.id == fileMeta.id }) {
                        restored++
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
                                backedUp = true,
                                serverFileId = fileMeta.serverFileId
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

    // ─── List restorable files (for the restore picker) ──────

    suspend fun listCloudFiles(): CloudListResult = withContext(Dispatchers.IO) {
        val code = clientCode() ?: return@withContext CloudListResult(error = "Sign in to Pro first.")
        if (!VaultApi.isLoggedIn()) {
            if (VaultApi.login(code) is VaultApi.Result.Error) {
                return@withContext CloudListResult(error = "Couldn't reach the backup server. Check your connection.")
            }
        }
        if (!BackupCrypto.hasRecoveryPhrase()) {
            return@withContext CloudListResult(error = "Recovery phrase required to read your backup.")
        }
        val index = downloadRemoteIndex(code)
            ?: return@withContext CloudListResult(error = "Couldn't read your backup. Check your recovery phrase and connection.")

        val files = index.files
            .filter { it.serverFileId.isNotBlank() }
            .map { m ->
                val thumb = m.thumbnail?.let {
                    try { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
                }
                CloudFileInfo(
                    id = m.id,
                    name = m.originalName,
                    fileType = m.fileType,
                    sizeBytes = m.sizeBytes,
                    folderId = m.folderId,
                    thumbnailBytes = thumb
                )
            }
        Log.d(TAG, "listCloudFiles: ${files.size} restorable file(s)")
        CloudListResult(files = files)
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

    // ─── Storage usage ─────────────────────────────────────────

    suspend fun getStorageUsage(): VaultStorageUsage? = withContext(Dispatchers.IO) {
        val code = clientCode() ?: return@withContext null
        if (!VaultApi.isLoggedIn()) {
            if (VaultApi.login(code) is VaultApi.Result.Error) return@withContext null
        }
        when (val r = VaultApi.getUsage()) {
            is VaultApi.Result.Success -> VaultStorageUsage(r.value.usedBytes, r.value.quotaBytes)
            is VaultApi.Result.Error -> {
                Log.w(TAG, "getStorageUsage failed: ${r.message}")
                null
            }
        }
    }

    /**
     * Best-effort small JPEG preview (base64) for a file, used only to populate
     * the cloud index so a restoring device can show a picker. Returns null for
     * non-visual files or when no cached thumbnail is available.
     */
    private fun thumbnailB64(entry: VaultFileEntry): String? = try {
        VaultStorage.getThumbnail(entry, null)
            ?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
    } catch (e: Exception) {
        Log.w(TAG, "thumbnailB64 failed for ${entry.id}: ${e.message}")
        null
    }

    // ─── Remote Encrypted Index helpers ───────────────────────

    /**
     * Downloads + decrypts the remote index.
     *
     * The vault backend is occasionally flaky (transient 503s on the presigned
     * GET/PUT URLs). That means a freshly-uploaded meta blob can succeed
     * server-side while its upload/complete response is lost — leaving
     * [appPrefs.vaultMetaFileId] pointing at the *previous* meta even though a
     * newer, more complete one now exists on the server. If we blindly trusted
     * the cached id we'd happily decrypt that stale meta and wrongly conclude
     * there's "nothing to restore".
     *
     * Strategy (correctness over a couple of extra GETs):
     *  1. Enumerate every meta-mime blob on the server (newest-first) plus the
     *     cached id, de-duplicated.
     *  2. Download + decrypt every candidate we can.
     *  3. Pick the most authoritative index: newest [BackupIndex.lastBackupAtMs]
     *     wins, tie-broken by the larger file count. (Server `createdAt`
     *     timestamps can be missing/unparseable, so we trust the decrypted
     *     content, not list metadata.)
     *  4. Cache the winner's fileId so the next incremental update targets it.
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

        // Build an ordered, de-duplicated candidate list: cached id first (it's
        // usually the right one and saves a list round-trip on the happy path),
        // then every meta blob the server knows about, newest-first.
        val cached = appPrefs.vaultMetaFileId.get()
        val candidateIds = LinkedHashSet<String>()
        if (!cached.isNullOrBlank()) candidateIds.add(cached)
        candidateIds.addAll(VaultApi.findMetaFiles())

        if (candidateIds.isEmpty()) {
            Log.w(TAG, "downloadRemoteIndex: no meta-mime blobs on server")
            return null
        }

        // Decrypt every candidate and keep the most authoritative one. We do NOT
        // short-circuit on the first decrypt: a stale-but-valid meta must lose to
        // a newer one that actually contains the user's backed-up files.
        var best: BackupIndex? = null
        var bestId: String? = null
        var decrypted = 0
        for (id in candidateIds) {
            val blob = when (val r = VaultApi.downloadBlob(id)) {
                is VaultApi.Result.Success -> r.value
                is VaultApi.Result.Error -> {
                    Log.w(TAG, "downloadRemoteIndex: fetch failed for $id: ${r.message}")
                    continue
                }
            }
            val index = tryDecrypt(id, blob) ?: continue
            decrypted++
            val isBetter = best == null ||
                index.lastBackupAtMs > best!!.lastBackupAtMs ||
                (index.lastBackupAtMs == best!!.lastBackupAtMs && index.files.size > best!!.files.size)
            if (isBetter) {
                best = index
                bestId = id
            }
        }

        if (best == null || bestId == null) {
            Log.w(TAG, "downloadRemoteIndex: ${candidateIds.size} candidate(s), none decrypted")
            return null
        }

        if (bestId != cached) {
            Log.d(
                TAG,
                "downloadRemoteIndex: selected meta $bestId over cached '${cached ?: ""}' " +
                    "(files=${best!!.files.size}, lastBackupAtMs=${best!!.lastBackupAtMs}) — re-caching"
            )
            appPrefs.vaultMetaFileId.set(bestId)
        } else {
            Log.d(TAG, "downloadRemoteIndex: cached meta $bestId is authoritative (files=${best!!.files.size}, decrypted=$decrypted)")
        }
        return best
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
                    // Prefer the locally-persisted server id — this survives transient
                    // failures to download the previous remote index.
                    e.serverFileId.isNotBlank() -> e.serverFileId
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
                    serverFileId = serverId,
                    // Carry the previous thumbnail forward; only (re)generate when missing
                    // so the restore picker can show real previews without re-decrypting.
                    thumbnail = prev?.thumbnail ?: thumbnailB64(e)
                )
            }.let { list ->
                if (removalEntryId != null) list.filter { it.id != removalEntryId } else list
            }.filter { meta ->
                // Never write an entry that isn't actually in the cloud — a blank
                // serverFileId would otherwise surface as a restore error later.
                if (meta.serverFileId.isBlank()) {
                    Log.w(TAG, "updateRemoteIndex: dropping ${meta.id} (no serverFileId — not uploaded)")
                    false
                } else true
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
