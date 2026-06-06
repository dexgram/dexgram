package chat.simplex.common.views.vault

actual object VaultBackup {
    actual fun isBackupEnabled(): Boolean = false
    actual suspend fun enableBackup(): VaultBackupResult =
        VaultBackupResult(false, error = "Not supported on desktop")
    actual fun disableBackup() {}
    actual suspend fun backupFile(entry: VaultFileEntry): Boolean = false
    actual suspend fun backupFileWithPassword(entry: VaultFileEntry, folderPassword: String?): Boolean = false
    actual suspend fun backupAll(folderPasswords: Map<String, String>, onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo =
        BackupProgressInfo(0, 0, true)
    actual suspend fun deleteBackup(entryId: String): Boolean = false
    actual suspend fun listCloudFiles(): CloudListResult = CloudListResult(error = "Not supported on desktop")
    actual suspend fun restoreFromCloud(selectedIds: Set<String>?, onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo =
        BackupProgressInfo(0, 0, true, 1)
    actual fun getStatus(): BackupStatus = BackupStatus()
    actual suspend fun getStorageUsage(): VaultStorageUsage? = null
    actual suspend fun wipeServerStorage(): Int = -1
    actual fun hasBackupPassphrase(): Boolean = false
    actual fun getBackupPassphrase(): String? = null
    actual fun generateBackupPassphrase(): String = ""
    actual fun validateBackupPassphrase(phrase: String): Boolean = false
    actual fun setBackupPassphrase(phrase: String) {}
}
