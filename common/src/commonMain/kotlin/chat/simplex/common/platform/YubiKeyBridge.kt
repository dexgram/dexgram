package chat.simplex.common.platform

/**
 * Platform-agnostic bridge for YubiKey operations.
 *
 * v4 architecture: ECDH key agreement on slot 9d (Key Management).
 * The Database Master Key (DMK) is never stored in plaintext — it is
 * wrapped with a KEK derived from the ECDH shared secret via HKDF.
 */
expect object YubiKeyBridge {
    suspend fun setupPIN(pin: String): Result<Unit>
    suspend fun setupPUK(puk: String): Result<Unit>
    suspend fun setupManagementKey(pin: String): Result<Unit>
    
    /** Enroll YubiKey: generate keys, ECDH, wrap DMK. Returns raw DMK for initial SQLCipher encryption. */
    suspend fun enrollForDatabaseEncryption(pin: String): Result<String>
    
    /** Unlock database: ECDH derive KEK, unwrap DMK. Returns DMK for SQLCipher. */
    suspend fun unlockDatabase(pin: String): Result<String>
    
    /** Verify PIN + identity (delegates to unlockDatabase internally). */
    suspend fun verifyPinAndIdentity(pin: String): Result<Boolean>
    
    /** Verify PIN only (no identity check). */
    suspend fun verifyPinForUnlock(pin: String): Result<Boolean>
    
    /** Verify PUK against stored hash to unlock PIN (no YubiKey tap required). */
    fun verifyPukAndUnlockPin(puk: String): Result<Boolean>
    
    /** Unblock YubiKey PIN using PUK on the physical device + verify new PIN. */
    suspend fun unblockAndVerifyPin(puk: String, pin: String): Result<Boolean>
    
    fun isAppLocked(): Boolean
    fun isPinLocked(): Boolean
    fun getRemainingPinAttempts(): Int
    fun getRemainingPukAttempts(): Int
    fun isYubiKeyDetected(): Boolean
    suspend fun isFactoryDefault(): Result<Boolean>
    suspend fun resetToFactoryDefaults(): Result<Unit>
}

