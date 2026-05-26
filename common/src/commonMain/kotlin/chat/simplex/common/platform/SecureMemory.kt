package chat.simplex.common.platform

import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Secure memory handling utilities for sensitive data like PINs and keys.
 * 
 * SECURITY PRINCIPLES:
 * 1. Use CharArray instead of String for sensitive data (can be zeroed)
 * 2. Clear sensitive data immediately after use
 * 3. Use SecureRandom for all cryptographic operations
 * 4. Implement rate limiting for authentication attempts
 * 5. Use HMAC for identity verification (not plain hash)
 */
object SecureMemory {
    private val secureRandom = SecureRandom()
    
    /**
     * Securely wipe a CharArray by overwriting with zeros
     */
    fun wipe(array: CharArray?) {
        array?.let { Arrays.fill(it, '\u0000') }
    }
    
    /**
     * Securely wipe a ByteArray by overwriting with zeros
     */
    fun wipe(array: ByteArray?) {
        array?.let { Arrays.fill(it, 0.toByte()) }
    }
    
    /**
     * Generate cryptographically secure random bytes
     */
    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    /**
     * Generate a cryptographically secure management key (32 bytes for AES-256)
     */
    fun generateManagementKey(): ByteArray {
        return randomBytes(32)
    }
    
    /**
     * Convert String to CharArray for secure handling
     * IMPORTANT: Caller must wipe the returned array after use
     */
    fun toSecureChars(str: String): CharArray {
        return str.toCharArray()
    }
    
    /**
     * Execute a block with a secure CharArray, automatically wiping after use
     */
    inline fun <T> withSecureChars(str: String, block: (CharArray) -> T): T {
        val chars = str.toCharArray()
        return try {
            block(chars)
        } finally {
            wipe(chars)
        }
    }
    
    /**
     * Execute a block with a secure ByteArray, automatically wiping after use
     */
    inline fun <T> withSecureBytes(bytes: ByteArray, block: (ByteArray) -> T): T {
        return try {
            block(bytes)
        } finally {
            wipe(bytes)
        }
    }
    
    /**
     * HMAC-SHA256 with a secret key for secure identity hashing
     */
    fun hmacSha256(data: ByteArray, secret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }
    
    /**
     * HKDF-SHA256 (RFC 5869) — Extract-then-Expand key derivation.
     *
     * @param ikm   Input keying material (e.g. ECDH shared secret Z)
     * @param salt  Optional salt (random bytes; if empty, uses zero-filled hash-length array)
     * @param info  Context / application-specific info string
     * @param length Desired output length in bytes (max 255 * 32)
     * @return Derived key material of the requested length
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "HKDF output length must be 1..8160 bytes" }
        
        // Extract: PRK = HMAC-SHA256(salt, IKM)
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(ikm, effectiveSalt)
        
        // Expand: OKM = T(1) || T(2) || ... truncated to length
        // T(i) = HMAC-SHA256(PRK, T(i-1) || info || i)
        val n = (length + 31) / 32
        val okm = ByteArray(length)
        var tPrev = ByteArray(0)
        var offset = 0
        
        for (i in 1..n) {
            val input = tPrev + info + byteArrayOf(i.toByte())
            tPrev = hmacSha256(input, prk)
            val copyLen = minOf(32, length - offset)
            System.arraycopy(tPrev, 0, okm, offset, copyLen)
            offset += copyLen
            wipe(input)
        }
        
        wipe(prk)
        return okm
    }
}

/**
 * Secure container for PIN that automatically wipes on clear.
 * Use this instead of MutableState<String?> for storing PINs.
 * 
 * SECURITY NOTES:
 * - PIN is stored as CharArray (can be wiped, unlike String)
 * - Always call clear() when done
 * - Prefer usePin() or usePinString() over direct get() methods
 * - finalize() is NOT used as it's deprecated and unreliable
 */
class SecurePin {
    // THREAD SAFETY: @Volatile ensures visibility across threads
    // All methods are @Synchronized for atomic access
    @Volatile
    private var pinChars: CharArray? = null
    
    /**
     * Set the PIN value (stores as CharArray internally)
     */
    @Synchronized
    fun set(pin: String?) {
        // Wipe existing value first
        wipe()
        pinChars = pin?.toCharArray()
    }
    
    /**
     * Set the PIN from a CharArray (preferred for secure sources)
     * The input array is NOT wiped - caller is responsible
     */
    @Synchronized
    fun setFromChars(chars: CharArray?) {
        wipe()
        pinChars = chars?.copyOf()
    }
    
    /**
     * Get the PIN as a String (for API compatibility)
     * 
     * SECURITY WARNING: Returns immutable String that cannot be wiped from memory.
     * Prefer usePinString() which provides automatic scoping.
     * 
     * @deprecated Use usePinString() for safer memory handling
     */
    @Synchronized
    @Deprecated("Use usePinString() for safer memory handling", ReplaceWith("usePinString { block }"))
    fun get(): String? {
        return pinChars?.let { String(it) }
    }
    
    /**
     * Get the PIN as CharArray for direct use with YubiKey APIs
     * IMPORTANT: Caller MUST wipe the returned array after use
     * Prefer usePin() for automatic cleanup
     */
    @Synchronized
    fun getChars(): CharArray? {
        return pinChars?.copyOf()
    }
    
    /**
     * Check if PIN is set and non-empty
     */
    @Synchronized
    fun isSet(): Boolean {
        return pinChars != null && pinChars!!.isNotEmpty()
    }
    
    /**
     * Use the PIN CharArray in a block, with automatic cleanup of the copy
     * This is the preferred way to access the PIN securely
     */
    @Synchronized
    fun <T> usePin(block: (CharArray) -> T): T? {
        val chars = pinChars?.copyOf() ?: return null
        return try {
            block(chars)
        } finally {
            SecureMemory.wipe(chars)
        }
    }
    
    /**
     * Use the PIN as String in a block (for APIs that require String)
     * The String cannot be wiped, but at least it's scoped
     */
    @Synchronized
    fun <T> usePinString(block: (String) -> T): T? {
        val chars = pinChars ?: return null
        return block(String(chars))
    }
    
    /**
     * Securely wipe the PIN from memory
     * ALWAYS call this when the PIN is no longer needed
     */
    @Synchronized
    fun wipe() {
        SecureMemory.wipe(pinChars)
        pinChars = null
    }
    
    /**
     * Clear is an alias for wipe
     */
    @Synchronized
    fun clear() = wipe()
    
    // NOTE: finalize() is intentionally NOT implemented
    // Java's finalize() is deprecated and not guaranteed to run
    // Callers MUST explicitly call clear() in try-finally or DisposableEffect
}

/**
 * Rate limiter for authentication attempts.
 * Implements exponential backoff to prevent brute force attacks.
 * 
 * SECURITY: Supports persistence to prevent bypass via app restart
 * 
 * @param maxAttempts Maximum attempts before hard lockout
 * @param baseDelayMs Initial delay after first failure (doubles each time)
 * @param maxDelayMs Maximum delay cap
 * @param persistState Optional callback to persist state changes
 * @param loadState Optional callback to load persisted state on init
 */
class AuthRateLimiter(
    private val maxAttempts: Int = 5,
    private val baseDelayMs: Long = 2000L,
    private val maxDelayMs: Long = 300000L,  // 5 minutes max
    private val persistState: ((failedAttempts: Int, lockedUntil: Long) -> Unit)? = null,
    loadState: (() -> Pair<Int, Long>)? = null
) {
    private var failedAttempts: Int
    private var lastAttemptTime = 0L
    private var lockedUntil: Long
    
    init {
        // Load persisted state if available
        val (savedAttempts, savedLockedUntil) = loadState?.invoke() ?: Pair(0, 0L)
        failedAttempts = savedAttempts
        lockedUntil = savedLockedUntil
    }
    
    /**
     * Check if attempts are currently rate limited
     * @return Pair of (isLocked, remainingWaitMs)
     */
    @Synchronized
    fun checkRateLimit(): Pair<Boolean, Long> {
        val now = System.currentTimeMillis()
        if (lockedUntil > now) {
            return Pair(true, lockedUntil - now)
        }
        return Pair(false, 0L)
    }
    
    /**
     * Record a failed attempt and calculate next delay
     * @return Wait time in ms before next attempt is allowed
     */
    @Synchronized
    fun recordFailure(): Long {
        failedAttempts++
        lastAttemptTime = System.currentTimeMillis()
        
        // Exponential backoff: 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, capped at maxDelayMs
        val delay = minOf(baseDelayMs * (1L shl (failedAttempts - 1)), maxDelayMs)
        lockedUntil = lastAttemptTime + delay
        
        // Persist state to survive app restart
        persistState?.invoke(failedAttempts, lockedUntil)
        
        return delay
    }
    
    /**
     * Record a successful attempt, reset the rate limiter
     */
    @Synchronized
    fun recordSuccess() {
        failedAttempts = 0
        lockedUntil = 0L
        persistState?.invoke(0, 0L)
    }
    
    /**
     * Get remaining attempts before longer lockout
     */
    @Synchronized
    fun getRemainingAttempts(): Int {
        return maxOf(0, maxAttempts - failedAttempts)
    }
    
    /**
     * Check if account should be considered locked (too many failures)
     */
    @Synchronized
    fun isHardLocked(): Boolean {
        return failedAttempts >= maxAttempts
    }
    
    /**
     * Reset the rate limiter completely
     */
    @Synchronized
    fun reset() {
        failedAttempts = 0
        lastAttemptTime = 0L
        lockedUntil = 0L
        persistState?.invoke(0, 0L)
    }
    
    /**
     * Get current state for external persistence
     */
    @Synchronized
    fun getState(): Pair<Int, Long> {
        return Pair(failedAttempts, lockedUntil)
    }
}

/**
 * Weak PIN/PUK detector to prevent common/easily guessable codes
 */
object WeakPinDetector {
    
    // Common weak PINs to reject
    private val WEAK_PINS = setOf(
        "123456", "654321", "111111", "222222", "333333", 
        "444444", "555555", "666666", "777777", "888888", 
        "999999", "000000", "123123", "121212", "112233",
        "1234567", "7654321", "1111111", "1234568", "12345678",
        "87654321", "11111111", "22222222", "00000000",
        // Additional patterns for 8-digit PUKs
        "12121212", "11223344", "12341234", "11112222",
        "13579246", "24681357"  // keyboard patterns
    )
    
    /**
     * Check if a PIN is weak
     * @return Pair of (isWeak, reason)
     */
    fun isWeakPin(pin: String): Pair<Boolean, String?> {
        // Check known weak PINs
        if (pin in WEAK_PINS) {
            return Pair(true, generalGetString(MR.strings.yubikey_validation_too_common))
        }
        
        // Check for all same digits
        if (pin.all { it == pin[0] }) {
            return Pair(true, generalGetString(MR.strings.yubikey_validation_all_same_digit))
        }
        
        // Check for simple ascending sequence
        if (isAscendingSequence(pin)) {
            return Pair(true, generalGetString(MR.strings.yubikey_validation_ascending_sequence))
        }
        
        // Check for simple descending sequence
        if (isDescendingSequence(pin)) {
            return Pair(true, generalGetString(MR.strings.yubikey_validation_descending_sequence))
        }
        
        // Check for repeated pairs (e.g., 121212)
        if (isRepeatedPattern(pin)) {
            return Pair(true, generalGetString(MR.strings.yubikey_validation_repeated_pattern))
        }
        
        // Check for keyboard rows/patterns
        if (isKeyboardPattern(pin)) {
            return Pair(true, generalGetString(MR.strings.yubikey_validation_keyboard_pattern))
        }
        
        return Pair(false, null)
    }
    
    private fun isAscendingSequence(pin: String): Boolean {
        for (i in 1 until pin.length) {
            if (pin[i].code - pin[i-1].code != 1) return false
        }
        return true
    }
    
    private fun isDescendingSequence(pin: String): Boolean {
        for (i in 1 until pin.length) {
            if (pin[i-1].code - pin[i].code != 1) return false
        }
        return true
    }
    
    private fun isRepeatedPattern(pin: String): Boolean {
        // Check for 2-char pattern repeated
        if (pin.length >= 4 && pin.length % 2 == 0) {
            val pattern = pin.substring(0, 2)
            if (pin == pattern.repeat(pin.length / 2)) return true
        }
        // Check for 3-char pattern repeated
        if (pin.length >= 6 && pin.length % 3 == 0) {
            val pattern = pin.substring(0, 3)
            if (pin == pattern.repeat(pin.length / 3)) return true
        }
        // Check for 4-char pattern repeated
        if (pin.length >= 8 && pin.length % 4 == 0) {
            val pattern = pin.substring(0, 4)
            if (pin == pattern.repeat(pin.length / 4)) return true
        }
        return false
    }
    
    private fun isKeyboardPattern(pin: String): Boolean {
        // Common numpad patterns
        val keyboardPatterns = listOf(
            "147258", "258369", "159357", "357159",
            "741852", "852963", "14789", "36987",
            "147852", "258963", "369874", "987456"
        )
        return keyboardPatterns.any { pin.contains(it) || pin == it.take(pin.length) }
    }
    
    /**
     * Validate PIN strength
     * @return null if valid, error message if invalid
     */
    fun validatePin(pin: String): String? {
        if (pin.length < 6) {
            return generalGetString(MR.strings.yubikey_validation_pin_too_short)
        }
        if (pin.length > 8) {
            return generalGetString(MR.strings.yubikey_validation_pin_too_long)
        }
        if (!pin.all { it.isDigit() }) {
            return generalGetString(MR.strings.yubikey_validation_pin_digits_only)
        }
        val (isWeak, reason) = isWeakPin(pin)
        if (isWeak) {
            return reason
        }
        return null
    }
    
    /**
     * Validate PUK strength (same rules as PIN but different length)
     * @return null if valid, error message if invalid
     */
    fun validatePuk(puk: String): String? {
        if (puk.length < 6) {
            return generalGetString(MR.strings.yubikey_validation_puk_too_short)
        }
        if (puk.length > 8) {
            return generalGetString(MR.strings.yubikey_validation_puk_too_long)
        }
        if (!puk.all { it.isDigit() }) {
            return generalGetString(MR.strings.yubikey_validation_puk_digits_only)
        }
        val (isWeak, reason) = isWeakPin(puk)
        if (isWeak) {
            return reason?.replace("PIN", "PUK")?.replace("Code", "PUK")
        }
        return null
    }
}

