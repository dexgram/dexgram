package chat.simplex.common.views.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import chat.simplex.common.platform.androidAppContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android vault storage — hardware-backed AES-256-GCM with per-file keys.
 *
 * Disk layout:
 *   vault/
 *     index.enc                  encrypted JSON (VaultIndex)
 *     root/                      loose files (no folder)
 *       <uuid>.vault             encrypted file
 *     folders/
 *       <folder-uuid>/
 *         <uuid>.vault
 *
 * .vault binary format (no folder password):
 *   [1]  version  0x01
 *   [1]  flags    0x00
 *   [12] master-wrap IV
 *   [48] AES-GCM(file_key_32, master_key) = 32 + 16 tag
 *   [12] content IV
 *   [N]  AES-GCM(plaintext, file_key)     = plaintext_len + 16 tag
 *
 * .vault binary format (folder password — double encryption):
 *   [1]  version  0x01
 *   [1]  flags    0x01
 *   [16] PBKDF2 salt
 *   [12] folder-wrap IV
 *   [76] AES-GCM(master_iv_12 + master_wrapped_48, folder_key) = 60 + 16 tag
 *   [12] content IV
 *   [N]  AES-GCM(plaintext, file_key)
 */
actual object VaultStorage {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "vault_master_v1"
    private const val IV_SIZE = 12
    private const val KEY_SIZE = 32
    private const val SALT_SIZE = 16
    private const val TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val VERIFY_TEXT = "VAULT_PW_OK_V1"
    private const val VERSION: Byte = 0x01
    private const val FLAG_DOUBLE: Byte = 0x01

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rng = SecureRandom()

    // ─── Keystore ────────────────────────────────────────────────

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    // ─── Primitives ──────────────────────────────────────────────

    private fun gcmEncrypt(plain: ByteArray, key: SecretKey): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return c.iv to c.doFinal(plain)
    }

    private fun gcmDecrypt(iv: ByteArray, ct: ByteArray, key: SecretKey): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return c.doFinal(ct)
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    private fun pbkdf2(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(raw.encoded, "AES")
    }

    // ─── File encrypt / decrypt ──────────────────────────────────

    private fun encryptPayload(
        content: ByteArray,
        folderPassword: String?,
        salt: ByteArray?
    ): ByteArray {
        val fileKeyBytes = randomBytes(KEY_SIZE)
        val fileKey = SecretKeySpec(fileKeyBytes, "AES")
        val (contentIv, encContent) = gcmEncrypt(content, fileKey)

        val out = java.io.ByteArrayOutputStream()
        out.write(VERSION.toInt())

        if (folderPassword != null && salt != null) {
            out.write(FLAG_DOUBLE.toInt())
            val (masterIv, masterWrapped) = gcmEncrypt(fileKeyBytes, masterKey())
            val folderKey = pbkdf2(folderPassword, salt)
            val inner = masterIv + masterWrapped
            val (folderIv, doubleWrapped) = gcmEncrypt(inner, folderKey)
            out.write(salt)
            out.write(folderIv)
            out.write(doubleWrapped)
        } else {
            out.write(0x00)
            val (masterIv, masterWrapped) = gcmEncrypt(fileKeyBytes, masterKey())
            out.write(masterIv)
            out.write(masterWrapped)
        }

        out.write(contentIv)
        out.write(encContent)
        return out.toByteArray()
    }

    private fun decryptPayload(blob: ByteArray, folderPassword: String?): ByteArray? {
        var p = 0
        if (blob[p++] != VERSION) return null
        val flags = blob[p++]
        val isDouble = (flags.toInt() and FLAG_DOUBLE.toInt()) != 0

        val fileKeyBytes: ByteArray
        if (isDouble) {
            if (folderPassword == null) return null
            val salt = blob.copyOfRange(p, p + SALT_SIZE); p += SALT_SIZE
            val folderIv = blob.copyOfRange(p, p + IV_SIZE); p += IV_SIZE
            val dw = blob.copyOfRange(p, p + 76); p += 76
            val inner = gcmDecrypt(folderIv, dw, pbkdf2(folderPassword, salt))
            val masterIv = inner.copyOfRange(0, IV_SIZE)
            val masterWrapped = inner.copyOfRange(IV_SIZE, inner.size)
            fileKeyBytes = gcmDecrypt(masterIv, masterWrapped, masterKey())
        } else {
            val masterIv = blob.copyOfRange(p, p + IV_SIZE); p += IV_SIZE
            val masterWrapped = blob.copyOfRange(p, p + 48); p += 48
            fileKeyBytes = gcmDecrypt(masterIv, masterWrapped, masterKey())
        }

        val fileKey = SecretKeySpec(fileKeyBytes, "AES")
        val contentIv = blob.copyOfRange(p, p + IV_SIZE); p += IV_SIZE
        val encContent = blob.copyOfRange(p, blob.size)
        return gcmDecrypt(contentIv, encContent, fileKey)
    }

    // ─── Directories ─────────────────────────────────────────────

    private fun baseDir(): File {
        val d = File(androidAppContext.filesDir, "vault")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileDir(folderId: String?): File {
        val d = if (folderId != null) File(baseDir(), "folders/$folderId") else File(baseDir(), "root")
        if (!d.exists()) d.mkdirs()
        return d
    }

    // ─── Encrypted index ─────────────────────────────────────────

    private fun loadIndex(): VaultIndex {
        val f = File(baseDir(), "index.enc")
        if (!f.exists()) return VaultIndex()
        return try {
            val raw = f.readBytes()
            val dec = gcmDecrypt(raw.copyOfRange(0, IV_SIZE), raw.copyOfRange(IV_SIZE, raw.size), masterKey())
            json.decodeFromString(String(dec, Charsets.UTF_8))
        } catch (_: Exception) { VaultIndex() }
    }

    private fun saveIndex(idx: VaultIndex) {
        val plain = json.encodeToString(idx).toByteArray(Charsets.UTF_8)
        val (iv, enc) = gcmEncrypt(plain, masterKey())
        File(baseDir(), "index.enc").writeBytes(iv + enc)
    }

    // ─── Password verifier ───────────────────────────────────────

    private fun createVerifier(password: String, salt: ByteArray): String {
        val (iv, enc) = gcmEncrypt(VERIFY_TEXT.toByteArray(Charsets.UTF_8), pbkdf2(password, salt))
        return Base64.encodeToString(iv + enc, Base64.NO_WRAP)
    }

    private fun checkVerifier(verifier: String, password: String, salt: ByteArray): Boolean = try {
        val b = Base64.decode(verifier, Base64.NO_WRAP)
        String(gcmDecrypt(b.copyOfRange(0, IV_SIZE), b.copyOfRange(IV_SIZE, b.size), pbkdf2(password, salt)), Charsets.UTF_8) == VERIFY_TEXT
    } catch (_: Exception) { false }

    // ─── Secure delete ───────────────────────────────────────────

    private fun secureDelete(f: File) {
        if (!f.exists()) return
        f.writeBytes(ByteArray(f.length().toInt()))
        f.delete()
    }

    // ─── Public API ──────────────────────────────────────────────

    actual fun importFile(
        name: String, mimeType: String, sourceBytes: ByteArray,
        folderId: String?, folderPassword: String?
    ): VaultFileEntry? = try {
        val idx = loadIndex()
        val folder = if (folderId != null) idx.folders.find { it.id == folderId } else null
        val salt = if (folder?.hasPassword == true)
            folder.passwordSalt?.let { Base64.decode(it, Base64.NO_WRAP) } else null

        val id = java.util.UUID.randomUUID().toString()
        val enc = encryptPayload(sourceBytes, if (salt != null) folderPassword else null, salt)
        File(fileDir(folderId), "$id.vault").writeBytes(enc)

        val type = when {
            mimeType.startsWith("image/") -> VaultFileType.PHOTO
            mimeType.startsWith("video/") -> VaultFileType.VIDEO
            mimeType.startsWith("audio/") -> VaultFileType.AUDIO
            else -> VaultFileType.DOCUMENT
        }
        val entry = VaultFileEntry(id, folderId, name, type, sourceBytes.size.toLong(), mimeType, salt != null)
        idx.files.add(entry)
        saveIndex(idx)
        entry
    } catch (e: Exception) {
        android.util.Log.e("VaultStorage", "Import failed", e); null
    }

    actual fun decryptFile(entry: VaultFileEntry, folderPassword: String?): ByteArray? = try {
        val f = File(fileDir(entry.folderId), "${entry.id}.vault")
        if (!f.exists()) null else decryptPayload(f.readBytes(), folderPassword)
    } catch (e: Exception) {
        android.util.Log.e("VaultStorage", "Decrypt failed", e); null
    }

    actual fun deleteFile(entry: VaultFileEntry): Boolean = try {
        secureDelete(File(fileDir(entry.folderId), "${entry.id}.vault"))
        val idx = loadIndex()
        idx.files.removeAll { it.id == entry.id }
        saveIndex(idx); true
    } catch (e: Exception) {
        android.util.Log.e("VaultStorage", "Delete failed", e); false
    }

    actual fun createFolder(name: String, password: String?): VaultFolder? = try {
        val id = java.util.UUID.randomUUID().toString()
        val salt = if (password != null) randomBytes(SALT_SIZE) else null
        val saltB64 = salt?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val verifier = if (password != null && salt != null) createVerifier(password, salt) else null

        val folder = VaultFolder(id, name, password != null, saltB64, verifier)
        fileDir(id)
        val idx = loadIndex()
        idx.folders.add(folder)
        saveIndex(idx)
        folder
    } catch (e: Exception) {
        android.util.Log.e("VaultStorage", "Create folder failed", e); null
    }

    actual fun renameFolder(folderId: String, newName: String): Boolean = try {
        val idx = loadIndex()
        val i = idx.folders.indexOfFirst { it.id == folderId }
        if (i < 0) false
        else { idx.folders[i] = idx.folders[i].copy(name = newName); saveIndex(idx); true }
    } catch (_: Exception) { false }

    actual fun deleteFolder(folderId: String): Boolean = try {
        val idx = loadIndex()
        idx.files.filter { it.folderId == folderId }.forEach { fe ->
            secureDelete(File(fileDir(folderId), "${fe.id}.vault"))
        }
        idx.files.removeAll { it.folderId == folderId }
        idx.folders.removeAll { it.id == folderId }
        File(baseDir(), "folders/$folderId").deleteRecursively()
        saveIndex(idx); true
    } catch (e: Exception) {
        android.util.Log.e("VaultStorage", "Delete folder failed", e); false
    }

    actual fun verifyFolderPassword(folderId: String, password: String): Boolean {
        val folder = loadIndex().folders.find { it.id == folderId } ?: return false
        if (!folder.hasPassword) return true
        val v = folder.passwordVerifier ?: return false
        val s = folder.passwordSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return checkVerifier(v, password, s)
    }

    actual fun getIndex(): VaultIndex = loadIndex()

    actual fun isAvailable(): Boolean = try { masterKey(); true } catch (_: Exception) { false }

    actual fun getRawVaultFile(entry: VaultFileEntry): ByteArray? = try {
        val f = File(fileDir(entry.folderId), "${entry.id}.vault")
        if (f.exists()) f.readBytes() else null
    } catch (e: Exception) {
        android.util.Log.e("VaultStorage", "getRawVaultFile failed", e); null
    }

    actual fun markFileBacked(entryId: String, backed: Boolean) {
        try {
            val idx = loadIndex()
            val i = idx.files.indexOfFirst { it.id == entryId }
            if (i >= 0) {
                idx.files[i] = idx.files[i].copy(backedUp = backed)
                saveIndex(idx)
            }
        } catch (e: Exception) {
            android.util.Log.e("VaultStorage", "markFileBacked failed", e)
        }
    }

    actual fun restoreFolder(folder: VaultFolder) {
        try {
            fileDir(folder.id)
            val idx = loadIndex()
            if (idx.folders.none { it.id == folder.id }) {
                idx.folders.add(folder)
                saveIndex(idx)
            }
        } catch (e: Exception) {
            android.util.Log.e("VaultStorage", "restoreFolder failed", e)
        }
    }

    actual fun restoreVaultFile(entryId: String, rawVaultBytes: ByteArray, entry: VaultFileEntry) {
        try {
            val dir = fileDir(entry.folderId)
            val encrypted = encryptPayload(rawVaultBytes, null, null)
            File(dir, "$entryId.vault").writeBytes(encrypted)
            val idx = loadIndex()
            if (idx.files.none { it.id == entryId }) {
                idx.files.add(entry.copy(isDoubleEncrypted = false))
            }
            saveIndex(idx)
        } catch (e: Exception) {
            android.util.Log.e("VaultStorage", "restoreVaultFile failed", e)
        }
    }
}
