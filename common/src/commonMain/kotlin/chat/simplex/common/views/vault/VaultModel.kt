package chat.simplex.common.views.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.*

@Serializable
enum class VaultFileType { PHOTO, VIDEO, DOCUMENT, AUDIO, OTHER }

@Serializable
data class VaultFolder(
    val id: String,
    val name: String,
    val hasPassword: Boolean = false,
    val passwordSalt: String? = null,
    val passwordVerifier: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class VaultFileEntry(
    val id: String,
    val folderId: String? = null,
    val originalName: String,
    val fileType: VaultFileType,
    val sizeBytes: Long,
    val mimeType: String = "",
    val isDoubleEncrypted: Boolean = false,
    val addedAtMs: Long = System.currentTimeMillis(),
    val backedUp: Boolean = false
)

@Serializable
data class VaultIndex(
    val folders: MutableList<VaultFolder> = mutableListOf(),
    val files: MutableList<VaultFileEntry> = mutableListOf()
)

// ─── Backup Data Models ────────────────────────────────────────

@Serializable
data class BackupIndex(
    val version: Int = 1,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastBackupAtMs: Long = System.currentTimeMillis(),
    val folders: List<BackupFolderMeta> = emptyList(),
    val files: List<BackupFileMeta> = emptyList()
)

@Serializable
data class BackupFolderMeta(
    val id: String,
    val name: String,
    val hasPassword: Boolean = false,
    val passwordSalt: String? = null,
    val passwordVerifier: String? = null,
    val createdAtMs: Long = 0L
)

@Serializable
data class BackupFileMeta(
    val id: String,
    val folderId: String? = null,
    val originalName: String,
    val fileType: VaultFileType,
    val sizeBytes: Long,
    val mimeType: String = "",
    val isDoubleEncrypted: Boolean = false,
    val addedAtMs: Long = 0L,
    // ID returned by prod-vaultdb.dexgram.app on upload — opaque to client.
    val serverFileId: String = ""
)

enum class BackupState {
    DISABLED, ENABLED, UPLOADING, RESTORING, ERROR
}

@Serializable
data class BackupStatus(
    val enabled: Boolean = false,
    val lastBackupAtMs: Long = 0L,
    val filesBacked: Int = 0,
    val totalFiles: Int = 0,
    val errorMessage: String? = null
)

/**
 * Platform-specific vault operations.
 *
 * Security architecture:
 * - Master key: AES-256-GCM in Android Keystore (hardware-backed, never leaves TEE)
 * - Per-file key: random 32 bytes, wrapped by master key
 * - Folder passwords: PBKDF2-SHA256 (100k iterations) derives a folder key
 * - Double encryption: file key wrapped by master key, then by folder key
 * - Encrypted index: all metadata encrypted with master key
 * - Secure delete: overwrite with zeros before unlink
 */
expect object VaultStorage {
    fun importFile(name: String, mimeType: String, sourceBytes: ByteArray, folderId: String?, folderPassword: String?): VaultFileEntry?
    fun decryptFile(entry: VaultFileEntry, folderPassword: String?): ByteArray?
    fun deleteFile(entry: VaultFileEntry): Boolean
    fun createFolder(name: String, password: String?): VaultFolder?
    fun renameFolder(folderId: String, newName: String): Boolean
    fun deleteFolder(folderId: String): Boolean
    fun verifyFolderPassword(folderId: String, password: String): Boolean
    fun getIndex(): VaultIndex
    fun isAvailable(): Boolean
    fun getRawVaultFile(entry: VaultFileEntry): ByteArray?
    fun markFileBacked(entryId: String, backed: Boolean)
    fun restoreVaultFile(entryId: String, rawVaultBytes: ByteArray, entry: VaultFileEntry)
    fun restoreFolder(folder: VaultFolder)
}

@Composable
expect fun VaultFilePicker(
    onFilePicked: (name: String, mimeType: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
)

expect fun decodeVaultImage(bytes: ByteArray): ImageBitmap?

expect fun writeVaultTempFile(bytes: ByteArray, extension: String): String?

expect fun cleanupVaultTempFile(path: String)

expect fun restoreFileToDevice(bytes: ByteArray, fileName: String, mimeType: String): Boolean

@Composable
expect fun VaultPdfViewer(filePath: String, modifier: Modifier = Modifier)

/**
 * Platform-specific E2EE backup operations.
 *
 * Crypto: 16-digit Pro account code → PBKDF2-HMAC-SHA256 → HKDF → AES-256-GCM keys.
 * Storage: Dexgram Vault DB (prod-vaultdb.dexgram.app). Server is zero-knowledge
 *          and never sees plaintext file content or names.
 *
 * The user's Pro account code is the sole credential for both subscription and
 * backup — no separate recovery phrase to manage.
 */
expect object VaultBackup {
    fun isBackupEnabled(): Boolean
    suspend fun enableBackup(): VaultBackupResult
    fun disableBackup()
    suspend fun backupFile(entry: VaultFileEntry): Boolean
    suspend fun backupFileWithPassword(entry: VaultFileEntry, folderPassword: String?): Boolean
    suspend fun backupAll(onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo
    suspend fun deleteBackup(entryId: String): Boolean
    /**
     * Restores from the cloud using the currently-signed-in Pro account code.
     * Will fail if the user is not signed in to Pro.
     */
    suspend fun restoreFromCloud(onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo
    fun getStatus(): BackupStatus
    /**
     * Deletes every cloud blob owned by the authenticated user, then resets
     * local backup state. Returns the number of server-side files deleted,
     * or a negative value on failure.
     */
    suspend fun wipeServerStorage(): Int
}

data class VaultBackupResult(
    val success: Boolean,
    val error: String? = null
)

data class BackupProgressInfo(
    val current: Int,
    val total: Int,
    val done: Boolean,
    val errors: Int = 0
)
