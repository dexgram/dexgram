package chat.simplex.common.views.wallet

import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import kotlinx.coroutines.flow.*

/**
 * Biometric Authentication Support
 * Platform-agnostic interface for biometric/PIN authentication
 */

/**
 * Authentication result
 */
sealed class AuthResult {
    object Success : AuthResult()
    data class Failed(val reason: String, val canRetry: Boolean = true) : AuthResult()
    object Cancelled : AuthResult()
    object NotAvailable : AuthResult()
    object NotConfigured : AuthResult()
}

/**
 * Authentication type
 */
enum class AuthType {
    BIOMETRIC,
    PIN,
    PASSWORD,
    PATTERN,
    NONE;

    val displayName: String get() = when (this) {
        BIOMETRIC -> generalGetString(MR.strings.wallet_auth_type_biometric)
        PIN -> generalGetString(MR.strings.wallet_auth_type_pin)
        PASSWORD -> generalGetString(MR.strings.wallet_auth_type_password)
        PATTERN -> generalGetString(MR.strings.wallet_auth_type_pattern)
        NONE -> generalGetString(MR.strings.wallet_auth_type_none)
    }
}

/**
 * Biometric capabilities
 */
data class BiometricCapabilities(
    val hasBiometric: Boolean,
    val hasFingerprint: Boolean,
    val hasFaceRecognition: Boolean,
    val hasIris: Boolean,
    val isEnrolled: Boolean,        // User has enrolled biometrics
    val isHardwareAvailable: Boolean
) {
    val canUseBiometric: Boolean get() = hasBiometric && isEnrolled && isHardwareAvailable
}

/**
 * Wallet security settings
 */
data class WalletSecuritySettings(
    val isEnabled: Boolean = true,
    val authType: AuthType = AuthType.BIOMETRIC,
    val requireAuthForSend: Boolean = true,
    val requireAuthForExport: Boolean = true,
    val requireAuthForReveal: Boolean = true,
    val autoLockTimeoutMs: Long = 5 * 60 * 1000, // 5 minutes
    val lockOnBackground: Boolean = true,
    val isConfigured: Boolean = false
)

/**
 * Biometric Authentication Service
 * Expect/actual pattern for platform-specific implementation
 */
interface BiometricAuthService {
    
    /**
     * Check biometric capabilities
     */
    fun getCapabilities(): BiometricCapabilities
    
    /**
     * Authenticate user
     */
    suspend fun authenticate(
        title: String = generalGetString(MR.strings.wallet_auth_required_title),
        subtitle: String = generalGetString(MR.strings.wallet_auth_required_subtitle),
        negativeButtonText: String = generalGetString(MR.strings.wallet_auth_cancel_button)
    ): AuthResult
    
    /**
     * Check if authenticated (within timeout)
     */
    fun isAuthenticated(): Boolean
    
    /**
     * Set authentication timeout
     */
    fun setAuthTimeout(timeoutMs: Long)
    
    /**
     * Lock wallet (require re-authentication)
     */
    fun lock()
}

/**
 * Wallet Lock Manager
 * Manages wallet lock state and auto-lock
 */
object WalletLockManager {
    
    private var settings = WalletSecuritySettings()
    private var lastAuthTime: Long = 0
    private var isUnlocked = false
    
    private val _lockState = MutableStateFlow(true)
    val lockState: StateFlow<Boolean> = _lockState.asStateFlow()

    // ── Rate limiting ──────────────────────────────────────────────
    private var failedAttempts = 0
    private var lockoutUntil: Long = 0

    private val _failedAttemptsFlow = MutableStateFlow(0)
    val failedAttemptsFlow: StateFlow<Int> = _failedAttemptsFlow.asStateFlow()

    private val _lockoutRemainingMs = MutableStateFlow(0L)
    val lockoutRemainingMs: StateFlow<Long> = _lockoutRemainingMs.asStateFlow()

    private fun getLockoutDurationMs(attempts: Int): Long = when {
        attempts < 3  -> 0L
        attempts == 3 -> 30_000L
        attempts == 4 -> 60_000L
        attempts == 5 -> 5 * 60_000L
        attempts >= 6 -> 15 * 60_000L
        else -> 0L
    }

    /** Call after a failed auth attempt. Returns remaining lockout millis (0 if none). */
    fun recordFailedAttempt(): Long {
        failedAttempts++
        _failedAttemptsFlow.value = failedAttempts
        val lockout = getLockoutDurationMs(failedAttempts)
        if (lockout > 0) {
            lockoutUntil = System.currentTimeMillis() + lockout
        }
        _lockoutRemainingMs.value = lockout
        return lockout
    }

    /** True if the user is currently in a lockout period. Updates [lockoutRemainingMs]. */
    fun isRateLimited(): Boolean {
        if (lockoutUntil <= 0) return false
        val remaining = lockoutUntil - System.currentTimeMillis()
        if (remaining <= 0) {
            lockoutUntil = 0
            _lockoutRemainingMs.value = 0
            return false
        }
        _lockoutRemainingMs.value = remaining
        return true
    }

    /** Reset failed attempts counter — call after successful authentication. */
    private fun resetFailedAttempts() {
        failedAttempts = 0
        lockoutUntil = 0
        _failedAttemptsFlow.value = 0
        _lockoutRemainingMs.value = 0
    }

    // ── Core lock/unlock ───────────────────────────────────────────

    fun updateSettings(newSettings: WalletSecuritySettings) {
        settings = newSettings
        if (!newSettings.isEnabled) {
            unlock()
        }
    }
    
    fun getSettings(): WalletSecuritySettings = settings
    
    fun isLocked(): Boolean {
        if (!settings.isEnabled || !settings.isConfigured) return false
        
        if (isUnlocked && settings.autoLockTimeoutMs > 0) {
            val elapsed = System.currentTimeMillis() - lastAuthTime
            if (elapsed > settings.autoLockTimeoutMs) {
                lock()
            }
        }
        
        return !isUnlocked
    }
    
    fun lock() {
        isUnlocked = false
        _lockState.value = true
        onLockListeners.forEach { runCatching { it() } }
    }

    private val onLockListeners = mutableListOf<() -> Unit>()

    fun addOnLockListener(listener: () -> Unit) {
        onLockListeners.add(listener)
    }
    
    fun unlock() {
        isUnlocked = true
        lastAuthTime = System.currentTimeMillis()
        _lockState.value = false
        resetFailedAttempts()
    }
    
    fun refreshAuthTime() {
        if (isUnlocked) {
            lastAuthTime = System.currentTimeMillis()
        }
    }
    
    fun requiresAuth(action: WalletAction): Boolean {
        if (!settings.isEnabled || !settings.isConfigured) return false
        
        return when (action) {
            WalletAction.SEND -> settings.requireAuthForSend
            WalletAction.EXPORT_MNEMONIC -> settings.requireAuthForExport
            WalletAction.REVEAL_PRIVATE_KEY -> settings.requireAuthForReveal
            WalletAction.APPROVE_TOKEN -> settings.requireAuthForSend
            WalletAction.SWAP -> settings.requireAuthForSend
            WalletAction.SIGN_MESSAGE -> settings.requireAuthForSend
            WalletAction.VIEW_ONLY -> false
        }
    }
    
    fun onBackground() {
        if (settings.lockOnBackground) {
            lock()
        }
    }
    
    fun onForeground() {
        isLocked()
    }
}

/**
 * Wallet actions that may require authentication
 */
enum class WalletAction {
    SEND,
    EXPORT_MNEMONIC,
    REVEAL_PRIVATE_KEY,
    APPROVE_TOKEN,
    SWAP,
    SIGN_MESSAGE,
    VIEW_ONLY
}

/**
 * Default (no-op) implementation for platforms without biometric support
 */
class DefaultBiometricAuthService : BiometricAuthService {
    
    override fun getCapabilities(): BiometricCapabilities {
        return BiometricCapabilities(
            hasBiometric = false,
            hasFingerprint = false,
            hasFaceRecognition = false,
            hasIris = false,
            isEnrolled = false,
            isHardwareAvailable = false
        )
    }
    
    override suspend fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String
    ): AuthResult {
        return AuthResult.NotAvailable
    }
    
    override fun isAuthenticated(): Boolean = true
    
    override fun setAuthTimeout(timeoutMs: Long) {}
    
    override fun lock() {}
}

