package chat.simplex.common.views.vault

import chat.simplex.common.platform.androidAppContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * E2EE Backup Cryptography — all keys derived from the user's 16-digit Pro
 * account code (the same one used to authenticate against the Dexgram backend).
 *
 * Key hierarchy:
 *   clientCode (16 digits)
 *     ├── PBKDF2-HMAC-SHA256(salt=APP_SALT, iters=310_000, len=32) → master_seed
 *     │
 *     ├── HKDF("backup-enc")   → backup_encryption_key  (AES-256, wraps vault files)
 *     ├── HKDF("backup-auth")  → user_id_hash (informational, never sent — server
 *     │                          already knows the code via /auth/login)
 *     └── HKDF("backup-index") → index_encryption_key   (AES-256, wraps meta.enc)
 *
 * Security trade-off vs. the previous BIP39 scheme: a 16-digit decimal code is
 * ~53 bits of entropy vs. BIP39's 256 bits. PBKDF2 at 310k iterations slows
 * offline brute-force but does not match BIP39 strength. This is the chosen
 * UX trade-off — "anonymous code" is the user's sole credential for both
 * subscription and backup.
 *
 * @Suppress("unused") because helpers like [encrypt] / [decrypt] / [hkdfSha256]
 * are also used by [BackupService] via direct reference and lint can't see it.
 */
object BackupCrypto {

    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val PREFS_NAME = "vault_backup_prefs"
    private const val KEY_BACKUP_ENABLED = "backup_enabled"
    private const val KEY_LAST_BACKUP = "last_backup_ms"

    // PBKDF2 parameters. Fixed app-level salt — input is already per-user.
    // Iteration count follows OWASP 2023 PBKDF2-SHA256 recommendation (≥310k).
    private const val PBKDF2_ITERS = 310_000
    private const val PBKDF2_KEY_LEN_BITS = 256
    private val APP_SALT: ByteArray = MessageDigest.getInstance("SHA-256")
        .digest("DexgramVault-v1::clientCode->seed".toByteArray(Charsets.US_ASCII))

    private val rng = SecureRandom()

    // Cache the expensive PBKDF2-derived seed once per process per code so
    // per-file backup/restore operations stay snappy.
    @Volatile private var cachedCode: String? = null
    @Volatile private var cachedSeed: ByteArray? = null

    // ─── Client-code normalization ────────────────────────────

    /** Strip every non-digit so dashes/spaces don't change the derived key. */
    private fun normalize(clientCode: String): String = clientCode.filter { it.isDigit() }

    // ─── PBKDF2 master seed ───────────────────────────────────

    private fun deriveSeed(clientCode: String): ByteArray {
        val normalized = normalize(clientCode)
        val hit = cachedSeed
        if (hit != null && cachedCode == normalized) return hit

        val spec = PBEKeySpec(
            normalized.toCharArray(),
            APP_SALT,
            PBKDF2_ITERS,
            PBKDF2_KEY_LEN_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val seed = factory.generateSecret(spec).encoded
        spec.clearPassword()

        cachedCode = normalized
        cachedSeed = seed
        return seed
    }

    // ─── HKDF-SHA256 (domain separation over the seed) ────────

    private fun hkdfSha256(ikm: ByteArray, info: String, length: Int = 32): ByteArray {
        val salt = ByteArray(32) // zero salt per RFC 5869 — input already a CSPRNG-grade seed
        val prk = hmacSha256(salt, ikm)
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val n = (length + 31) / 32
        var t = ByteArray(0)
        val okm = ByteArray(length)
        var pos = 0
        for (i in 1..n) {
            t = hmacSha256(prk, t + infoBytes + byteArrayOf(i.toByte()))
            val toCopy = minOf(32, length - pos)
            System.arraycopy(t, 0, okm, pos, toCopy)
            pos += toCopy
        }
        return okm
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ─── Purpose-specific keys ────────────────────────────────

    fun deriveBackupEncKey(clientCode: String): SecretKeySpec {
        val seed = deriveSeed(clientCode)
        return SecretKeySpec(hkdfSha256(seed, "backup-enc"), "AES")
    }

    fun deriveIndexEncKey(clientCode: String): SecretKeySpec {
        val seed = deriveSeed(clientCode)
        return SecretKeySpec(hkdfSha256(seed, "backup-index"), "AES")
    }

    // ─── AES-256-GCM Encrypt / Decrypt ────────────────────────

    fun encrypt(plaintext: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_SIZE).also { rng.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    fun decrypt(blob: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = blob.copyOfRange(0, IV_SIZE)
        val ct = blob.copyOfRange(IV_SIZE, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    // ─── .vault.bak wrapper format ────────────────────────────
    // [4] "VBAK" magic
    // [1] version 0x01
    // [12] backup IV
    // [N] AES-GCM(raw_vault_bytes, backup_enc_key)

    private val MAGIC = "VBAK".toByteArray(Charsets.US_ASCII)

    fun wrapForBackup(rawVaultBytes: ByteArray, backupKey: SecretKeySpec): ByteArray {
        val encrypted = encrypt(rawVaultBytes, backupKey)
        return MAGIC + byteArrayOf(0x01) + encrypted
    }

    fun unwrapFromBackup(bakBytes: ByteArray, backupKey: SecretKeySpec): ByteArray? {
        if (bakBytes.size < 5) return null
        if (!bakBytes.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        if (bakBytes[4] != 0x01.toByte()) return null
        return try {
            decrypt(bakBytes.copyOfRange(5, bakBytes.size), backupKey)
        } catch (_: Exception) { null }
    }

    // ─── Local flags ──────────────────────────────────────────

    private fun prefs() = androidAppContext.getSharedPreferences(PREFS_NAME, 0)

    fun markBackupEnabled() {
        prefs().edit().putBoolean(KEY_BACKUP_ENABLED, true).apply()
    }

    fun isBackupEnabled(): Boolean = prefs().getBoolean(KEY_BACKUP_ENABLED, false)

    fun disableBackup() {
        prefs().edit()
            .putBoolean(KEY_BACKUP_ENABLED, false)
            .remove(KEY_LAST_BACKUP)
            .apply()
        // Wipe the in-memory derived key so a future sign-in / sign-out cycle
        // re-runs PBKDF2 fresh.
        cachedCode = null
        cachedSeed = null
    }

    fun setLastBackupTime(ms: Long) {
        prefs().edit().putLong(KEY_LAST_BACKUP, ms).apply()
    }

    fun getLastBackupTime(): Long = prefs().getLong(KEY_LAST_BACKUP, 0L)
}
