package chat.simplex.common.views.wallet

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Production-grade secure storage backed by Android Keystore.
 *
 * - AES-256-GCM with hardware-backed keys (StrongBox when available)
 * - Randomized encryption (different ciphertext every time)
 * - No sensitive data in SharedPreferences plaintext
 */
object SecureStorage {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val PREFS_NAME = "wallet_secure_storage"
    private const val GCM_TAG_LENGTH = 128

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        WalletPrefs.delegate = object : WalletPrefs.PrefsDelegate {
            override fun putString(key: String, value: String) {
                prefs?.edit()?.putString(key, value)?.apply()
            }
            override fun getString(key: String): String? = prefs?.getString(key, null)
            override fun remove(key: String) { prefs?.edit()?.remove(key)?.apply() }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Encrypted key-value store
    // ═══════════════════════════════════════════════════════════════

    /**
     * Encrypt and store data. WARNING: [plaintext] is wiped (zeroed) after encryption.
     * Callers must not use the array after this call. Copy first if needed.
     */
    fun encryptAndStore(alias: String, plaintext: ByteArray) {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val combined = iv + ciphertext
        prefs?.edit()?.putString("enc_$alias", Base64.encodeToString(combined, Base64.NO_WRAP))?.apply()
        WalletSecurity.wipe(plaintext)
    }

    fun decryptFromStore(alias: String): ByteArray? {
        val encoded = prefs?.getString("enc_$alias", null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size < 13) return null // IV is 12 bytes minimum

        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)

        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    fun deleteKey(alias: String) {
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
            prefs?.edit()?.remove("enc_$alias")?.apply()
        } catch (_: Exception) { }
    }

    fun hasKey(alias: String): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            ks.containsAlias(alias)
        } catch (_: Exception) { false }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Keystore key management
    // ═══════════════════════════════════════════════════════════════

    private fun getOrCreateKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (ks.containsAlias(alias)) {
            return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        try {
            keyGenerator.init(builder.build())
        } catch (_: Exception) {
            // StrongBox not available – fall back without it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(false)
                keyGenerator.init(builder.build())
            }
        }
        return keyGenerator.generateKey()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Keystore health check
    // ═══════════════════════════════════════════════════════════════

    fun isKeystoreHealthy(): Boolean {
        return try {
            val testAlias = "__ks_health_check__"
            val key = getOrCreateKey(testAlias)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal("test".toByteArray())
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            ks.deleteEntry(testAlias)
            encrypted.isNotEmpty()
        } catch (_: Exception) { false }
    }
}
