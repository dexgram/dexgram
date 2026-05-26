package chat.simplex.common.platform

import chat.simplex.common.model.ChatModel
import com.yubico.yubikit.core.YubiKeyDevice

/**
 * Android implementation of YubiKeyBridge using YubiKit SDK
 */
actual object YubiKeyBridge {
    
    /**
     * Get the currently detected YubiKey device from ChatModel
     */
    private fun getCurrentDevice(): YubiKeyDevice? {
        return ChatModel.currentYubiKeyTag.value as? YubiKeyDevice
    }
    
    actual suspend fun setupPIN(pin: String): Result<Unit> {
        val device = getCurrentDevice() 
            ?: return Result.failure(Exception("No YubiKey detected"))
        
        return YubiKeyHandler.setupPIN(device, pin)
    }
    
    actual suspend fun setupPUK(puk: String): Result<Unit> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected"))
        
        return YubiKeyHandler.setupPUK(device, puk)
    }
    
    actual suspend fun setupManagementKey(pin: String): Result<Unit> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected"))
        
        return YubiKeyHandler.setupManagementKey(device, pin)
    }
    
    actual suspend fun enrollForDatabaseEncryption(pin: String): Result<String> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected"))
        
        return YubiKeyHandler.enrollForDatabaseEncryption(device, pin)
    }
    
    actual suspend fun unlockDatabase(pin: String): Result<String> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected. Please tap your YubiKey."))
        
        return YubiKeyHandler.unlockDatabase(device, pin)
    }
    
    actual suspend fun verifyPinAndIdentity(pin: String): Result<Boolean> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected. Please tap your YubiKey."))
        
        return YubiKeyHandler.verifyPinAndIdentity(device, pin)
    }
    
    actual suspend fun verifyPinForUnlock(pin: String): Result<Boolean> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected. Please tap your YubiKey."))
        
        return YubiKeyHandler.verifyPin(device, pin)
    }
    
    actual fun verifyPukAndUnlockPin(puk: String): Result<Boolean> {
        // NOTE: PUK verification does NOT require YubiKey tap
        // It verifies against stored hash for better UX
        return YubiKeyHandler.verifyPukAndUnlockPin(puk)
    }
    
    actual suspend fun unblockAndVerifyPin(puk: String, pin: String): Result<Boolean> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected. Please tap your YubiKey."))
        
        return YubiKeyHandler.unblockAndVerifyPin(device, puk, pin)
    }
    
    actual fun isAppLocked(): Boolean {
        return YubiKeyHandler.isAppLocked()
    }
    
    actual fun isPinLocked(): Boolean {
        return YubiKeyHandler.isPinLocked()
    }
    
    actual fun getRemainingPinAttempts(): Int {
        return YubiKeyHandler.getRemainingPinAttempts()
    }
    
    actual fun getRemainingPukAttempts(): Int {
        return YubiKeyHandler.getRemainingPukAttempts()
    }
    
    actual fun isYubiKeyDetected(): Boolean {
        return ChatModel.yubiKeyDetected.value
    }
    
    actual suspend fun isFactoryDefault(): Result<Boolean> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected"))
        
        return YubiKeyHandler.isFactoryDefault(device)
    }
    
    actual suspend fun resetToFactoryDefaults(): Result<Unit> {
        val device = getCurrentDevice()
            ?: return Result.failure(Exception("No YubiKey detected"))
        
        return YubiKeyHandler.resetToFactoryDefaults(device)
    }
}

