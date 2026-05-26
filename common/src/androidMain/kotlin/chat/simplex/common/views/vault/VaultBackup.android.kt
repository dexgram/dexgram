package chat.simplex.common.views.vault

/**
 * Android implementation of [VaultBackup].
 *
 * Delegates all heavy lifting to [BackupService] (orchestrator), which uses
 * [BackupCrypto] (E2EE) and [VaultApi] (Dexgram Vault DB transport).
 *
 * Security model recap:
 *  - The 16-digit Pro account code is the sole credential. PBKDF2-HMAC-SHA256
 *    (310k iterations) over the code yields a 256-bit master seed; HKDF then
 *    derives separate AES-256-GCM keys for file backup and the encrypted index.
 *  - Files are wrapped client-side with AES-256-GCM before upload — the server
 *    sees only ciphertext blobs and the user's bearer token.
 *  - No recovery phrase is stored anywhere; the code is already in
 *    [chat.simplex.common.model.ChatController.appPrefs.dexgramAccountId].
 */
actual object VaultBackup {

    actual fun isBackupEnabled(): Boolean = BackupCrypto.isBackupEnabled()

    actual suspend fun enableBackup(): VaultBackupResult {
        val r = BackupService.enableBackup()
        return VaultBackupResult(success = r.success, error = r.error)
    }

    actual fun disableBackup() = BackupService.disableBackup()

    actual suspend fun backupFile(entry: VaultFileEntry): Boolean =
        BackupService.backupFile(entry, folderPassword = null)

    actual suspend fun backupFileWithPassword(entry: VaultFileEntry, folderPassword: String?): Boolean =
        BackupService.backupFile(entry, folderPassword)

    actual suspend fun backupAll(onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo {
        val p = BackupService.backupAll { sp ->
            onProgress(BackupProgressInfo(sp.current, sp.total, sp.done, sp.errors))
        }
        return BackupProgressInfo(p.current, p.total, p.done, p.errors)
    }

    actual suspend fun deleteBackup(entryId: String): Boolean =
        BackupService.deleteBackup(entryId)

    actual suspend fun restoreFromCloud(
        onProgress: (BackupProgressInfo) -> Unit
    ): BackupProgressInfo {
        val p = BackupService.restoreFromCloud { sp ->
            onProgress(BackupProgressInfo(sp.current, sp.total, sp.done, sp.errors))
        }
        return BackupProgressInfo(p.current, p.total, p.done, p.errors)
    }

    actual fun getStatus(): BackupStatus = BackupService.getStatus()

    actual suspend fun wipeServerStorage(): Int = BackupService.wipeServerStorage()
}
