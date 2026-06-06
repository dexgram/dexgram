package chat.simplex.common.views.vault

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
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
 * E2EE Backup Cryptography.
 *
 * Two independent secrets protect a backup:
 *  1. The 16-digit Pro account code — used to AUTHENTICATE with the Dexgram
 *     vault backend (/auth/login). The server knows this code, so it MUST NOT
 *     be enough on its own to decrypt anything.
 *  2. A 24-word BIP39 recovery phrase (256 bits of entropy) — generated on the
 *     device when backup is enabled and never sent to the server. This is the
 *     real encryption secret.
 *
 * Key hierarchy:
 *   recoveryPhrase (24 words)  +  proCode (as BIP39 "25th-word" passphrase)
 *     └── BIP39 toSeed (PBKDF2-HMAC-SHA512, 2048 rounds) → 64-byte master_seed
 *           ├── HKDF("backup-enc")   → backup_encryption_key (AES-256, wraps files)
 *           └── HKDF("backup-index") → index_encryption_key  (AES-256, wraps meta)
 *
 * Because the seed depends on BOTH the phrase and the code, leaking the Pro code
 * (e.g. by sharing it) does NOT expose the backup: an attacker can download the
 * encrypted blobs but cannot derive the AES keys without the 24-word phrase.
 *
 * Legacy fallback: if no recovery phrase is stored yet (backup created before
 * this feature), the seed falls back to PBKDF2 over the code alone so older
 * backups remain decryptable. New backups always have a phrase.
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
    private const val KEY_RECOVERY_PHRASE = "recovery_phrase"

    // PBKDF2 parameters (legacy code-only fallback). Fixed app-level salt —
    // input is already per-user. Iteration count follows OWASP 2023 (≥310k).
    private const val PBKDF2_ITERS = 310_000
    private const val PBKDF2_KEY_LEN_BITS = 256
    private val APP_SALT: ByteArray = MessageDigest.getInstance("SHA-256")
        .digest("DexgramVault-v1::clientCode->seed".toByteArray(Charsets.US_ASCII))

    private val rng = SecureRandom()

    // Cache the expensive derived seed once per process per (code|phrase) combo
    // so per-file backup/restore operations stay snappy.
    @Volatile private var cachedKey: String? = null
    @Volatile private var cachedSeed: ByteArray? = null

    // ─── Client-code normalization ────────────────────────────

    /** Strip every non-digit so dashes/spaces don't change the derived key. */
    private fun normalize(clientCode: String): String = clientCode.filter { it.isDigit() }

    // ─── Recovery phrase (24-word BIP39) ──────────────────────

    /** Normalizes a phrase to lowercase, single-spaced words for stable hashing. */
    private fun normalizePhrase(phrase: String): String =
        phrase.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /** Generates a fresh 24-word recovery phrase (256-bit entropy). Does NOT store it. */
    fun generateRecoveryPhrase(): String {
        val entropy = ByteArray(32).also { rng.nextBytes(it) } // 256 bits → 24 words
        val code = Mnemonics.MnemonicCode(entropy)
        return code.words.joinToString(" ") { String(it) }
    }

    /** Validates a BIP39 phrase (word count + checksum). */
    fun validateRecoveryPhrase(phrase: String): Boolean = try {
        val normalized = normalizePhrase(phrase)
        val wordCount = normalized.split(" ").size
        if (wordCount != 24) false
        else { Mnemonics.MnemonicCode(normalized).validate(); true }
    } catch (_: Exception) { false }

    fun hasRecoveryPhrase(): Boolean = !prefs().getString(KEY_RECOVERY_PHRASE, null).isNullOrBlank()

    fun getRecoveryPhrase(): String? = prefs().getString(KEY_RECOVERY_PHRASE, null)

    /** Persists the recovery phrase locally and clears the cached seed. */
    fun setRecoveryPhrase(phrase: String) {
        prefs().edit().putString(KEY_RECOVERY_PHRASE, normalizePhrase(phrase)).apply()
        cachedKey = null
        cachedSeed = null
    }

    // ─── Master seed ──────────────────────────────────────────

    private fun deriveSeed(clientCode: String): ByteArray {
        val normalized = normalize(clientCode)
        val phrase = getRecoveryPhrase()?.takeIf { it.isNotBlank() }
        val cacheKey = normalized + "|" + (phrase ?: "")
        val hit = cachedSeed
        if (hit != null && cachedKey == cacheKey) return hit

        val seed: ByteArray = if (phrase != null) {
            // BIP39 seed bound to the Pro code (used as the BIP39 passphrase).
            // toSeed runs PBKDF2-HMAC-SHA512 × 2048 internally and returns 64 bytes.
            Mnemonics.MnemonicCode(phrase).toSeed(normalized.toCharArray())
        } else {
            // Legacy code-only fallback (PBKDF2-HMAC-SHA256).
            val spec = PBEKeySpec(normalized.toCharArray(), APP_SALT, PBKDF2_ITERS, PBKDF2_KEY_LEN_BITS)
            val out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            out
        }

        cachedKey = cacheKey
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
        // re-derives fresh. The recovery phrase itself is intentionally kept so
        // the user can re-enable without re-entering it.
        cachedKey = null
        cachedSeed = null
    }

    fun setLastBackupTime(ms: Long) {
        prefs().edit().putLong(KEY_LAST_BACKUP, ms).apply()
    }

    fun getLastBackupTime(): Long = prefs().getLong(KEY_LAST_BACKUP, 0L)
}
