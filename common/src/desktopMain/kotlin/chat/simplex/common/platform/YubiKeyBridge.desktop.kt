package chat.simplex.common.platform

actual object YubiKeyBridge {
    actual suspend fun setupPIN(pin: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun setupPUK(puk: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun setupManagementKey(pin: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun enrollForDatabaseEncryption(pin: String): Result<String> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun unlockDatabase(pin: String): Result<String> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun verifyPinAndIdentity(pin: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun verifyPinForUnlock(pin: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual fun verifyPukAndUnlockPin(puk: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual suspend fun unblockAndVerifyPin(puk: String, pin: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))

    actual fun isAppLocked(): Boolean = false
    actual fun isPinLocked(): Boolean = false
    actual fun getRemainingPinAttempts(): Int = 3
    actual fun getRemainingPukAttempts(): Int = 3
    actual fun isYubiKeyDetected(): Boolean = false
    actual suspend fun isFactoryDefault(): Result<Boolean> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))
    actual suspend fun resetToFactoryDefaults(): Result<Unit> =
        Result.failure(UnsupportedOperationException("YubiKey not supported on desktop"))
}
