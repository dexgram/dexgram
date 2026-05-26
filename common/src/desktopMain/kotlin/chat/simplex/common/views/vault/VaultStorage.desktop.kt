package chat.simplex.common.views.vault

actual object VaultStorage {
    actual fun importFile(name: String, mimeType: String, sourceBytes: ByteArray, folderId: String?, folderPassword: String?): VaultFileEntry? = null
    actual fun decryptFile(entry: VaultFileEntry, folderPassword: String?): ByteArray? = null
    actual fun deleteFile(entry: VaultFileEntry): Boolean = false
    actual fun createFolder(name: String, password: String?): VaultFolder? = null
    actual fun renameFolder(folderId: String, newName: String): Boolean = false
    actual fun deleteFolder(folderId: String): Boolean = false
    actual fun verifyFolderPassword(folderId: String, password: String): Boolean = false
    actual fun getIndex(): VaultIndex = VaultIndex()
    actual fun isAvailable(): Boolean = false
    actual fun getRawVaultFile(entry: VaultFileEntry): ByteArray? = null
    actual fun markFileBacked(entryId: String, backed: Boolean) {}
    actual fun restoreFolder(folder: VaultFolder) {}
    actual fun restoreVaultFile(entryId: String, rawVaultBytes: ByteArray, entry: VaultFileEntry) {}
}
