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
    val backedUp: Boolean = false,
    // Server-assigned id from the cloud backup (prod-vaultdb.dexgram.app).
    // Persisted locally so the remote index can be rebuilt without re-downloading it.
    val serverFileId: String = ""
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
    val serverFileId: String = "",
    // Base64 of a small JPEG preview, so a restoring device can show a picker
    // without downloading every file. Null for non-visual files / older backups.
    val thumbnail: String? = null
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
    /**
     * Returns a small, decrypted thumbnail image (JPEG/PNG bytes) for the entry,
     * suitable for grid previews. Thumbnails are generated lazily and cached
     * encrypted on disk. Returns null for file types without a visual preview
     * (documents/audio/other) or when a password-protected file can't be opened.
     */
    fun getThumbnail(entry: VaultFileEntry, folderPassword: String?): ByteArray?
    fun markFileBacked(entryId: String, backed: Boolean)
    /** Persists the cloud server-assigned file id for a local entry. */
    fun setFileServerId(entryId: String, serverId: String)
    fun restoreVaultFile(entryId: String, rawVaultBytes: ByteArray, entry: VaultFileEntry)
    fun restoreFolder(folder: VaultFolder)
}

/** Where a newly-added vault file comes from. */
enum class VaultPickSource { FILES, CAMERA_PHOTO, CAMERA_VIDEO }

@Composable
expect fun VaultFilePicker(
    source: VaultPickSource,
    onFilePicked: (name: String, mimeType: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
)

expect fun decodeVaultImage(bytes: ByteArray): ImageBitmap?

/** This device's hardware model and user-set name, for the device manager UI. */
data class DeviceLabel(val model: String, val name: String)

expect fun currentDeviceLabel(): DeviceLabel

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
    suspend fun backupAll(folderPasswords: Map<String, String>, onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo
    suspend fun deleteBackup(entryId: String): Boolean
    /**
     * Lists the files available to restore from the cloud (with thumbnails when
     * present), so the user can pick which ones to download. Requires the Pro
     * code and recovery phrase to decrypt the index.
     */
    suspend fun listCloudFiles(): CloudListResult
    /**
     * Restores from the cloud using the currently-signed-in Pro account code.
     * Will fail if the user is not signed in to Pro.
     *
     * @param selectedIds restore only these file ids; null restores everything.
     */
    suspend fun restoreFromCloud(selectedIds: Set<String>?, onProgress: (BackupProgressInfo) -> Unit): BackupProgressInfo
    fun getStatus(): BackupStatus
    /**
     * Deletes every cloud blob owned by the authenticated user, then resets
     * local backup state. Returns the number of server-side files deleted,
     * or a negative value on failure.
     */
    suspend fun wipeServerStorage(): Int

    /**
     * Returns the account's vault storage usage (used + total quota in bytes),
     * or null if it can't be fetched (not signed in to Pro / offline).
     */
    suspend fun getStorageUsage(): VaultStorageUsage?

    // ─── 24-word recovery phrase ──────────────────────────────
    // A second secret (independent of the Pro code) that actually encrypts the
    // backup. Sharing the Pro code does not expose the backup without this.

    /** True if a recovery phrase is stored on this device. */
    fun hasBackupPassphrase(): Boolean
    /** Returns the stored recovery phrase, or null if none is set. */
    fun getBackupPassphrase(): String?
    /** Generates a fresh 24-word phrase (not stored). */
    fun generateBackupPassphrase(): String
    /** Validates word count + BIP39 checksum. */
    fun validateBackupPassphrase(phrase: String): Boolean
    /** Persists the recovery phrase locally (used when restoring on a new device). */
    fun setBackupPassphrase(phrase: String)
}

/** Vault storage usage for the signed-in account, in bytes. */
data class VaultStorageUsage(val usedBytes: Long, val quotaBytes: Long) {
    val availableBytes: Long get() = (quotaBytes - usedBytes).coerceAtLeast(0L)
    val fraction: Float get() = if (quotaBytes > 0) (usedBytes.toFloat() / quotaBytes).coerceIn(0f, 1f) else 0f
}

data class VaultBackupResult(
    val success: Boolean,
    val error: String? = null,
    /** Set on first-time enable: the freshly generated 24-word phrase to show the user. */
    val newPassphrase: String? = null
)

/** One backed-up file as seen from the cloud index — used to populate the restore picker. */
data class CloudFileInfo(
    val id: String,
    val name: String,
    val fileType: VaultFileType,
    val sizeBytes: Long,
    val folderId: String? = null,
    /** Small decoded preview bytes (JPEG), or null if the backup has no thumbnail for it. */
    val thumbnailBytes: ByteArray? = null
)

/** Result of listing the restorable files in the cloud. */
data class CloudListResult(
    val files: List<CloudFileInfo> = emptyList(),
    val error: String? = null
)

data class BackupProgressInfo(
    val current: Int,
    val total: Int,
    val done: Boolean,
    val errors: Int = 0
)
