package chat.simplex.common.views.vault

actual object VaultBackup {
    actual fun isBackupEnabled(): Boolean = false
    actual suspend fun enableBackup(): VaultBackupResult =
        VaultBackupResult(false, error = "Not supported on desktop")
    actual fun disableBackup() {}
    actual suspend fun backupFile(entry: VaultFileEntry): Boolean = false
    actual suspend fun backupFileWithPassword(entry: VaultFileEntry, folderPassword: String?): Boolean = false
    actual suspend fun backupAll(onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo =
        BackupProgressInfo(0, 0, true)
    actual suspend fun deleteBackup(entryId: String): Boolean = false
    actual suspend fun restoreFromCloud(onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo =
        BackupProgressInfo(0, 0, true, 1)
    actual fun getStatus(): BackupStatus = BackupStatus()
    actual suspend fun wipeServerStorage(): Int = -1
}
