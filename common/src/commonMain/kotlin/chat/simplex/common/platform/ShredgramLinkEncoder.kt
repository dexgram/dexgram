package chat.simplex.common.platform

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.net.URLDecoder
import java.net.URI
import java.security.SecureRandom

/**
 * Link obfuscation for Dexgram/Shredgram group links.
 *
 * SECURITY NOTE: The key is embedded in the app binary. This provides
 * obfuscation (links are not human-readable) but NOT real encryption
 * against a motivated attacker who decompiles the APK. For true
 * confidentiality, the key must come from a server or user secret.
 *
 * Uses AES-128-GCM with random IV (12 bytes prepended to ciphertext).
 * Backward-compatible: decode() falls back to legacy CBC if GCM fails.
 */
object ShredgramLinkEncoder {
    const val SHREDGRAM_WEB_BASE = "https://groups.dexgram.app/g/"
    const val SHREDGRAM_SCHEME = "dexgram:"

    private val SECRET_KEY: ByteArray by lazy { deriveObfuscatedKey() }

    private fun deriveObfuscatedKey(): ByteArray {
        val parts = arrayOf(
            byteArrayOf(0x53, 0x68, 0x52, 0x33),  // ShR3
            byteArrayOf(0x64, 0x47, 0x72, 0x34),  // dGr4
            byteArrayOf(0x6D, 0x4B, 0x33, 0x79),  // mK3y
            byteArrayOf(0x32, 0x30, 0x32, 0x34)   // 2024
        )
        val raw = ByteArray(16)
        var offset = 0
        for (p in parts) {
            System.arraycopy(p, 0, raw, offset, p.size)
            offset += p.size
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(raw).copyOf(16)
    }

    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    private val LEGACY_KEY = byteArrayOf(
        0x53, 0x68, 0x52, 0x33, 0x64, 0x47, 0x72, 0x34,
        0x6D, 0x4B, 0x33, 0x79, 0x32, 0x30, 0x32, 0x34
    )

    // Legacy static IV kept only for decoding old links
    private val LEGACY_IV = "ShR3dGr4m1V2024!".toByteArray(Charsets.UTF_8)

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(originalLink: String): String {
        return try {
            val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(SECRET_KEY, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(originalLink.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            val encoded = Base64.UrlSafe.encode(combined).trimEnd('=')
            "$SHREDGRAM_WEB_BASE$encoded"
        } catch (_: Exception) {
            originalLink
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(shredgramLink: String): String? {
        return try {
            val encoded = extractEncodedPart(shredgramLink)
            if (encoded.isEmpty()) return null

            val paddedEncoded = when (encoded.length % 4) {
                2 -> "$encoded=="
                3 -> "$encoded="
                else -> encoded
            }
            val raw = Base64.UrlSafe.decode(paddedEncoded)

            // Try GCM first (new format: 12-byte IV + ciphertext+tag)
            decodeGcm(raw) ?: decodeLegacyCbc(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeGcm(raw: ByteArray): String? {
        if (raw.size <= GCM_IV_LEN) return null
        val iv = raw.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = raw.copyOfRange(GCM_IV_LEN, raw.size)
        // Try new derived key first
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(SECRET_KEY, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) { /* fall through to legacy key */ }
        // Fall back to legacy raw key for links encoded before key derivation change
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(LEGACY_KEY, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    private fun decodeLegacyCbc(raw: ByteArray): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(LEGACY_KEY, "AES"), IvParameterSpec(LEGACY_IV))
            String(cipher.doFinal(raw), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
    
    /**
     * Extracts the Base64 encoded part from various link formats.
     */
    private fun extractEncodedPart(link: String): String {
        val trimmed = link.trim()
        
        var extracted: String
        
        // Try to parse as URI for dexgram:// or legacy shredgram:// scheme
        if (trimmed.startsWith("dexgram:", ignoreCase = true) || trimmed.startsWith("shredgram:", ignoreCase = true)) {
            try {
                val uri = URI(trimmed)
                
                val path = uri.path?.removePrefix("/") ?: ""
                val host = uri.host ?: ""
                
                extracted = when {
                    // If host is "g", "group" or "groups", the data is in path
                    host.equals("g", ignoreCase = true) || host.equals("group", ignoreCase = true) || host.equals("groups", ignoreCase = true) -> path
                    // If path is not empty and host is not "group", check if path has data
                    path.isNotEmpty() && !host.equals("group", ignoreCase = true) && !host.equals("g", ignoreCase = true) -> path
                    // If host looks like encrypted data (not "group"), use host
                    host.isNotEmpty() && !host.equals("g", ignoreCase = true) && !host.equals("group", ignoreCase = true) && !host.equals("groups", ignoreCase = true) -> host
                    // Use schemeSpecificPart as fallback
                    uri.schemeSpecificPart != null -> uri.schemeSpecificPart.removePrefix("//").removePrefix("g/").removePrefix("group/").removePrefix("groups/")
                    // Last resort: string extraction
                    else -> trimmed
                        .removePrefix("dexgram:")
                        .removePrefix("shredgram:")
                        .removePrefix("//")
                        .removePrefix("g/")
                        .removePrefix("group/")
                        .removePrefix("groups/")
                }
            } catch (e: Exception) {
                extracted = trimmed
                    .removePrefix("dexgram:")
                    .removePrefix("shredgram:")
                    .removePrefix("//")
                    .removePrefix("g/")
                    .removePrefix("group/")
                    .removePrefix("groups/")
            }
        } else {
            extracted = when {
                // Handle https://groups.dexgram.app/g/xxx (current format)
                trimmed.contains("groups.dexgram.app/g/", ignoreCase = true) ->
                    trimmed.substringAfter("groups.dexgram.app/g/").substringBefore("?").substringBefore("#")
                // Handle legacy https://groups.dexgram.im/g/xxx format
                trimmed.contains("groups.dexgram.im/g/", ignoreCase = true) ->
                    trimmed.substringAfter("groups.dexgram.im/g/").substringBefore("?").substringBefore("#")
                // Handle legacy https://groups.shredgram.im/g/xxx format
                trimmed.contains("groups.shredgram.im/g/", ignoreCase = true) ->
                    trimmed.substringAfter("groups.shredgram.im/g/").substringBefore("?").substringBefore("#")
                // Handle legacy drysor.com format
                trimmed.contains("drysor.com/group/", ignoreCase = true) -> 
                    trimmed.substringAfter("drysor.com/group/").substringBefore("?").substringBefore("#")
                // Handle legacy blaxness.uk format
                trimmed.contains("blaxness.uk/shredgram/groups/", ignoreCase = true) -> 
                    trimmed.substringAfter("blaxness.uk/shredgram/groups/").substringBefore("?").substringBefore("#")
                // Handle legacy link.shredgram.com format
                trimmed.contains("link.shredgram.com/groups/", ignoreCase = true) -> 
                    trimmed.substringAfter("link.shredgram.com/groups/").substringBefore("?").substringBefore("#")
                else -> trimmed
            }
        }
        
        // Remove any leading slashes
        while (extracted.startsWith("/")) {
            extracted = extracted.removePrefix("/")
        }
        
        // Remove any trailing slashes
        while (extracted.endsWith("/")) {
            extracted = extracted.removeSuffix("/")
        }
        
        // Remove "g/", "group/" or "groups/" prefix if still present
        if (extracted.startsWith("g/", ignoreCase = true)) {
            extracted = extracted.substring(2)
        }
        if (extracted.startsWith("group/", ignoreCase = true)) {
            extracted = extracted.substring(6)
        }
        if (extracted.startsWith("groups/", ignoreCase = true)) {
            extracted = extracted.substring(7)
        }
        
        // Handle URL-encoded slashes
        extracted = extracted.replace("%2F", "/").replace("%2f", "/")
        if (extracted.startsWith("g/", ignoreCase = true)) {
            extracted = extracted.substring(2)
        }
        if (extracted.startsWith("group/", ignoreCase = true)) {
            extracted = extracted.substring(6)
        }
        
        // URL decode in case the data was URL-encoded during redirect
        return try {
            URLDecoder.decode(extracted, "UTF-8")
        } catch (e: Exception) {
            extracted
        }
    }
    
    /**
     * Checks if a URI is a shredgram encrypted link.
     */
    fun isShredgramLink(uri: String): Boolean {
        val trimmed = uri.trim().lowercase()
        return trimmed.startsWith("dexgram://") ||
               trimmed.startsWith("shredgram://") ||
               trimmed.startsWith(SHREDGRAM_SCHEME.lowercase()) ||
               trimmed.contains("groups.dexgram.app/g/") ||
               trimmed.contains("groups.dexgram.im/g/") ||
               trimmed.contains("groups.shredgram.im/g/") ||
               trimmed.contains("drysor.com/group/") ||
               trimmed.contains("blaxness.uk/shredgram/") ||
               trimmed.contains("link.shredgram.com/")
    }
    
    /**
     * Processes a URI - if it's a shredgram link, decrypts it; otherwise returns as-is.
     */
    fun processUri(uri: String): String {
        return if (isShredgramLink(uri)) {
            decode(uri) ?: uri
        } else {
            uri
        }
    }
}
