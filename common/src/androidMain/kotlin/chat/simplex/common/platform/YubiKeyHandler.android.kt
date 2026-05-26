package chat.simplex.common.platform

import android.app.Activity
import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Base64
import android.util.Log
import chat.simplex.common.model.ChatController
import chat.simplex.common.model.ChatModel
import chat.simplex.common.views.helpers.DatabaseUtils
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.piv.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

/**
 * YubiKey PIV handler for secure database encryption.
 * 
 * SECURITY ARCHITECTURE (v4 — ECDH key agreement):
 * - Slot 9d (Key Management): P-256 private key never leaves YubiKey hardware
 * - ECDH: shared secret Z computed inside YubiKey from stored private key + app ephemeral public key
 * - HKDF-SHA256: derives Key Encryption Key (KEK) from Z with random salt + context info
 * - AES-256-GCM: wraps the random Database Master Key (DMK) with KEK
 * - DMK is NEVER stored in plaintext on device — only the wrapped form persists
 * - Every unlock requires the physical YubiKey to recompute Z via ECDH
 * - Management key: AES-256, stored in Keystore-encrypted storage
 * - PINs handled via CharArray and wiped immediately after use
 * - Lockout state in Keystore-encrypted storage (tamper-resistant)
 */
object YubiKeyHandler {
    private const val TAG = "YubiKeyHandler"
    
    // Operation timeout in milliseconds
    private const val OPERATION_TIMEOUT_MS = 30_000L
    
    // SECURITY: Maximum attempts before lockout
    private const val MAX_PIN_ATTEMPTS = 3
    private const val MAX_PUK_ATTEMPTS = 3
    
    // SECURITY: Rate limiter with persistent state to prevent bypass via app restart
    private val pinRateLimiter = AuthRateLimiter(
        maxAttempts = 5,
        baseDelayMs = 2000L,
        maxDelayMs = 300000L,  // 5 minutes max
        persistState = { attempts, lockedUntil ->
            // Persist to SharedPreferences
            ChatController.appPrefs.yubiKeyRateLimiterFailedAttempts.set(attempts)
            ChatController.appPrefs.yubiKeyRateLimiterLockedUntil.set(lockedUntil)
        },
        loadState = {
            // Load from SharedPreferences
            val attempts = ChatController.appPrefs.yubiKeyRateLimiterFailedAttempts.get()
            val lockedUntil = ChatController.appPrefs.yubiKeyRateLimiterLockedUntil.get()
            Pair(attempts, lockedUntil)
        }
    )
    
    // SECURITY: Random secret for HMAC-based identity hashing, stored in Android Keystore
    private val identitySecret: ByteArray by lazy {
        getOrCreateIdentitySecret()
    }
    
    private fun getOrCreateIdentitySecret(): ByteArray {
        val stored = DatabaseUtils.ksYubiKeyIdentitySecret.get()
        if (stored != null && stored.isNotEmpty()) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        val secret = SecureMemory.randomBytes(32)
        val encoded = Base64.encodeToString(secret, Base64.NO_WRAP)
        DatabaseUtils.ksYubiKeyIdentitySecret.set(encoded)
        return secret
    }
    
    private var yubiKitManager: YubiKitManager? = null
    private var currentDevice: YubiKeyDevice? = null
    private var currentActivity: Activity? = null
    
    /**
     * Initialize YubiKit Manager for NFC detection
     */
    fun initialize(context: Context) {
        if (yubiKitManager == null) {
            yubiKitManager = YubiKitManager(context.applicationContext)
            if (context is Activity) {
                currentActivity = context
            }
        }
    }
    
    /**
     * Start listening for NFC YubiKeys
     * Silently fails if NFC is not available or disabled
     */
    fun startNfcDiscovery(activity: Activity, onDeviceDiscovered: (YubiKeyDevice) -> Unit) {
        try {
            val manager = yubiKitManager ?: run {
                initialize(activity)
                yubiKitManager!!
            }
            
            currentActivity = activity
            
            manager.startNfcDiscovery(NfcConfiguration(), activity) { device ->
                currentDevice = device
                onDeviceDiscovered(device)
            }
        } catch (e: Exception) {
            // NFC not available or disabled - this is fine, just log it
        }
    }
    
    /**
     * Stop NFC discovery
     */
    fun stopNfcDiscovery(activity: Activity) {
        yubiKitManager?.stopNfcDiscovery(activity)
    }

    /**
     * Start NFC discovery and automatically set ChatModel state when a verified YubiKey is detected.
     * Use this from MainActivity to avoid exposing YubiKeyDevice to the android module.
     */
    fun startNfcDiscoveryWithAutoDetect(activity: Activity) {
        startNfcDiscovery(activity) { device ->
            verifyAndAcceptDevice(device) { isYubiKey ->
                if (isYubiKey) {
                    ChatModel.yubiKeyDetected.value = true
                    ChatModel.currentYubiKeyTag.value = device
                } else {
                }
            }
        }
    }
    
    /**
     * Check if YubiKey is enrolled for database encryption
     */
    fun isYubiKeyEnrolled(): Boolean {
        // SECURITY: Check encrypted storage for challenge
        val challenge = DatabaseUtils.ksYubiKeyChallenge.get()
        val useYubiKey = ChatController.appPrefs.useYubiKeyForDB.get()
        return challenge != null && challenge.isNotEmpty() && useYubiKey
    }
    
    /**
     * Get the rate limiter for external access
     */
    fun getRateLimiter(): AuthRateLimiter = pinRateLimiter
    
    /**
     * Setup PIN on YubiKey (called during onboarding)
     * Changes PIN from default (123456) to user's PIN
     * 
     * SECURITY: PIN is passed as String but immediately converted to CharArray
     * and wiped after use. No PIN hashes are stored.
     */
    suspend fun setupPIN(device: YubiKeyDevice, newPin: String): Result<Unit> {
        // Validate PIN strength first
        val pinError = WeakPinDetector.validatePin(newPin)
        if (pinError != null) {
            return Result.failure(Exception(pinError))
        }
        
        val newPinChars = newPin.toCharArray()
        val defaultPin = "123456".toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        // Get remaining PIN attempts before trying
                        val attempts = pivSession.pinAttempts
                        
                        if (attempts == 0) {
                            return@withPivSession Result.failure(
                                Exception("PIN is blocked. Use PUK to unblock.")
                            )
                        }
                        
                        // Change PIN from default to new PIN
                        pivSession.changePin(defaultPin, newPinChars)
                        
                        // Update completion flag only - NO PIN HASH STORED
                        ChatController.appPrefs.yubiKeyPinSet.set(true)
                        
                        pinRateLimiter.recordSuccess()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        pinRateLimiter.recordFailure()
                        val remainingAttempts = try {
                            pivSession.pinAttempts
                        } catch (ex: Exception) {
                            0
                        }
                        
                        val errorMsg = when {
                            remainingAttempts == 0 -> "PIN is blocked. Use PUK to unblock."
                            e.message?.contains("invalid", ignoreCase = true) == true -> 
                                "YubiKey may have been configured before. Please reset it first."
                            else -> "Failed to set PIN. Please try again."
                        }
                        
                        Log.e(TAG, "PIN setup failed")
                        return@withPivSession Result.failure(Exception(errorMsg))
                    }
                }
            } ?: Result.failure(Exception("Operation timed out. Please keep YubiKey close and try again."))
        } finally {
            // SECURITY: Always wipe PIN from memory
            SecureMemory.wipe(newPinChars)
            SecureMemory.wipe(defaultPin)
        }
    }
    
    /**
     * Setup PUK on YubiKey (called during onboarding)
     * Changes PUK from default (12345678) to user's PUK
     * 
     * SECURITY: PUK hash is stored for verification during PIN unlock. PUK strength is validated.
     */
    suspend fun setupPUK(device: YubiKeyDevice, newPuk: String): Result<Unit> {
        // SECURITY: Validate PUK strength first
        val pukError = WeakPinDetector.validatePuk(newPuk)
        if (pukError != null) {
            return Result.failure(Exception(pukError))
        }
        
        val newPukChars = newPuk.toCharArray()
        val defaultPuk = "12345678".toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        // Change PUK from default to new PUK
                        pivSession.changePuk(defaultPuk, newPukChars)
                        
                        // SECURITY: Store PUK hash in Keystore-encrypted storage (C3 fix)
                        val pukHash = hashPuk(newPuk)
                        DatabaseUtils.ksYubiKeyPukHash.set(pukHash)
                        
                        // Update completion flag
                        ChatController.appPrefs.yubiKeyPukSet.set(true)
                        
                        Result.success(Unit)
                    } catch (e: Exception) {
                        val errorMsg = when {
                            e.message?.contains("invalid", ignoreCase = true) == true -> 
                                "YubiKey may have been configured before. Please reset it first."
                            else -> "Failed to set PUK. Please try again."
                        }
                        
                        return@withPivSession Result.failure(Exception(errorMsg))
                    }
                }
            } ?: Result.failure(Exception("Operation timed out. Please keep YubiKey close and try again."))
        } finally {
            // SECURITY: Always wipe PUK from memory
            SecureMemory.wipe(newPukChars)
            SecureMemory.wipe(defaultPuk)
        }
    }
    
    /**
     * Setup Management Key on YubiKey (called during onboarding)
     * Generates and stores a new management key using SecureRandom.
     * 
     * SECURITY: 
     * - Uses cryptographically secure random generator
     * - Management key is encrypted before storage
     * - NO fallback to default keys - requires reset if auth fails
     */
    suspend fun setupManagementKey(device: YubiKeyDevice, pin: String): Result<Unit> {
        
        val pinChars = pin.toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        try {
                            pivSession.verifyPin(pinChars)
                        } catch (e: Exception) {
                            pinRateLimiter.recordFailure()
                            Log.e(TAG, "PIN verification failed")
                            return@withPivSession Result.failure(
                                Exception("PIN verification failed. Please check your PIN.")
                            )
                        }
                        
                        // SECURITY: AES-256 (32 bytes) instead of legacy 3DES
                        val newManagementKey = SecureMemory.randomBytes(32)
                        
                        val storedKeyStr = DatabaseUtils.ksYubiKeyManagementKey.get()
                        var authenticated = false
                        
                        if (storedKeyStr != null && storedKeyStr.isNotEmpty()) {
                            val storedKey = Base64.decode(storedKeyStr, Base64.NO_WRAP)
                            try {
                                val storedKeyType = if (storedKey.size == 24) ManagementKeyType.TDES else ManagementKeyType.AES256
                                pivSession.authenticate(storedKeyType, storedKey)
                                authenticated = true
                            } catch (e: Exception) {
                            } finally {
                                SecureMemory.wipe(storedKey)
                            }
                        }
                        
                        if (!authenticated) {
                            val defaultKeyTDES = byteArrayOf(
                                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
                            )
                            
                            try {
                                pivSession.authenticate(ManagementKeyType.TDES, defaultKeyTDES)
                                authenticated = true
                            } catch (e: Exception) {
                                for (keyType in listOf(ManagementKeyType.AES128, ManagementKeyType.AES192, ManagementKeyType.AES256)) {
                                    try {
                                        val aesKey = when (keyType) {
                                            ManagementKeyType.AES128 -> defaultKeyTDES.copyOf(16)
                                            ManagementKeyType.AES256 -> defaultKeyTDES + defaultKeyTDES.copyOf(8)
                                            else -> defaultKeyTDES
                                        }
                                        pivSession.authenticate(keyType, aesKey)
                                        authenticated = true
                                        SecureMemory.wipe(aesKey)
                                        break
                                    } catch (_: Exception) {
                                        continue
                                    }
                                }
                            } finally {
                                SecureMemory.wipe(defaultKeyTDES)
                            }
                        }
                        
                        if (!authenticated) {
                            Log.e(TAG, "Cannot authenticate with management key - reset required")
                            SecureMemory.wipe(newManagementKey)
                            return@withPivSession Result.failure(
                                Exception("Cannot authenticate with YubiKey. Your YubiKey may have been configured elsewhere. Please reset it in Settings > YubiKey > Factory Reset.")
                            )
                        }
                        
                        try {
                            pivSession.setManagementKey(
                                ManagementKeyType.AES256,
                                newManagementKey,
                                false
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to set management key")
                            SecureMemory.wipe(newManagementKey)
                            return@withPivSession Result.failure(
                                Exception("Failed to set management key. Please try again.")
                            )
                        }
                        
                        val keyEncoded = Base64.encodeToString(newManagementKey, Base64.NO_WRAP)
                        DatabaseUtils.ksYubiKeyManagementKey.set(keyEncoded)
                        SecureMemory.wipe(newManagementKey)
                        
                        ChatController.appPrefs.yubiKeyManagementKeySet.set(true)
                        
                        pinRateLimiter.recordSuccess()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error during management key setup")
                        return@withPivSession Result.failure(Exception("Setup failed. Please try again."))
                    }
                }
            } ?: Result.failure(Exception("Operation timed out. Please keep YubiKey close and try again."))
        } finally {
            SecureMemory.wipe(pinChars)
        }
    }
    
    private const val HKDF_INFO_PREFIX = "darklink-sqlite-unlock-v1"
    
    /**
     * Enroll YubiKey for database encryption using ECDH key agreement (v4 architecture).
     *
     * Flow:
     *  1. Generate P-256 key pair on slot 9d (Key Management) — private key stays in hardware
     *  2. Generate ephemeral P-256 key pair on device
     *  3. Z = YubiKey.calculateSecret(slot9d, ephemeral_pub)  — ECDH inside YubiKey
     *  4. KEK = HKDF-SHA256(Z, random_salt, info)
     *  5. DMK = random 32 bytes
     *  6. wrappedDMK = AES-256-GCM(KEK, DMK)
     *  7. Store: ephemeral_pub, wrappedDMK, salt, databaseId
     *  8. Zeroize: Z, KEK, DMK, ephemeral_priv
     *  9. Return DMK for initial SQLCipher encryption
     */
    suspend fun enrollForDatabaseEncryption(device: YubiKeyDevice, pin: String): Result<String> {
        val pinChars = pin.toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        pivSession.verifyPin(pinChars)
                        
                        val managementKeyStr = DatabaseUtils.ksYubiKeyManagementKey.get()
                            ?: throw Exception("Setup incomplete. Please complete YubiKey setup first.")
                        val managementKey = Base64.decode(managementKeyStr, Base64.NO_WRAP)
                        
                        val mkType = if (managementKey.size == 24) ManagementKeyType.TDES else ManagementKeyType.AES256
                        try {
                            pivSession.authenticate(mkType, managementKey)
                        } catch (e: Exception) {
                            val fallback = if (mkType == ManagementKeyType.AES256) ManagementKeyType.TDES else ManagementKeyType.AES256
                            pivSession.authenticate(fallback, managementKey)
                        } finally {
                            SecureMemory.wipe(managementKey)
                        }
                        
                        // [1] Generate P-256 key pair on slot 9d (Key Management)
                        @Suppress("DEPRECATION")
                        val yubiKeyPub = try {
                            pivSession.generateKey(
                                Slot.KEY_MANAGEMENT,
                                KeyType.ECCP256,
                                PinPolicy.DEFAULT,
                                TouchPolicy.DEFAULT
                            ) as ECPublicKey
                        } catch (e: Exception) {
                            if (e is android.nfc.TagLostException || e.message?.contains("Tag", ignoreCase = true) == true) {
                                throw Exception("NFC connection lost. Please keep your YubiKey close and try again.")
                            } else throw e
                        }
                        
                        // [2] Generate ephemeral P-256 key pair on device
                        val kpg = KeyPairGenerator.getInstance("EC")
                        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
                        val ephemeralKp = kpg.generateKeyPair()
                        val ephemeralPub = ephemeralKp.public as ECPublicKey
                        
                        // [3] ECDH: Z = YubiKey.calculateSecret(slot9d, ephemeralPub.W)
                        val z = pivSession.calculateSecret(Slot.KEY_MANAGEMENT, ephemeralPub.w)
                        if (z.all { it == 0.toByte() }) {
                            throw Exception("ECDH produced zero output — invalid key agreement")
                        }
                        
                        // [4] HKDF parameters
                        val salt = SecureMemory.randomBytes(32)
                        val dbId = Base64.encodeToString(SecureMemory.randomBytes(16), Base64.NO_WRAP)
                        val info = (HKDF_INFO_PREFIX + dbId).toByteArray(Charsets.UTF_8)
                        
                        // [5] KEK = HKDF-SHA256(Z, salt, info, 32)
                        val kek = SecureMemory.hkdfSha256(z, salt, info, 32)
                        
                        // [6] Generate random 32-byte Database Master Key
                        val dmk = SecureMemory.randomBytes(32)
                        
                        // [7] wrappedDMK = AES-256-GCM(KEK, DMK)
                        val wrappedDMK = aesGcmEncrypt(kek, dmk)
                        
                        // [8] Store everything in Keystore-encrypted storage
                        DatabaseUtils.ksYubiKeyEphemeralPub.set(
                            Base64.encodeToString(ephemeralPub.encoded, Base64.NO_WRAP)
                        )
                        DatabaseUtils.ksYubiKeyWrappedDMK.set(
                            Base64.encodeToString(wrappedDMK, Base64.NO_WRAP)
                        )
                        DatabaseUtils.ksYubiKeyHkdfSalt.set(
                            Base64.encodeToString(salt, Base64.NO_WRAP)
                        )
                        ChatController.appPrefs.yubiKeyDatabaseId.set(dbId)
                        
                        // Store YubiKey public key for identity verification (defense-in-depth)
                        DatabaseUtils.ksYubiKeyPublicKey.set(
                            Base64.encodeToString(yubiKeyPub.encoded, Base64.NO_WRAP)
                        )
                        val ecPointHash = hashEcPoint(yubiKeyPub)
                        ChatController.appPrefs.yubiKeyUid.set(ecPointHash)
                        
                        // Enrollment version 4 = ECDH-based
                        ChatController.appPrefs.yubiKeyEnrollmentVersion.set(4)
                        ChatController.appPrefs.useYubiKeyForDB.set(true)
                        
                        // [9] Encode DMK as Base64 for SQLCipher
                        val dmkString = Base64.encodeToString(dmk, Base64.NO_WRAP)
                        
                        // [10] Zeroize all sensitive intermediates
                        SecureMemory.wipe(z)
                        SecureMemory.wipe(kek)
                        SecureMemory.wipe(salt)
                        SecureMemory.wipe(dmk)
                        SecureMemory.wipe(wrappedDMK)
                        SecureMemory.wipe(info)
                        
                        pinRateLimiter.recordSuccess()
                        Result.success(dmkString)
                    } catch (e: Exception) {
                        pinRateLimiter.recordFailure()
                        Log.e(TAG, "ECDH enrollment failed")
                        Result.failure(Exception("Enrollment failed: ${e.message}"))
                    }
                }
            } ?: Result.failure(Exception("Operation timed out. Please keep YubiKey close and try again."))
        } finally {
            SecureMemory.wipe(pinChars)
        }
    }
    
    /**
     * Unlock database by deriving DMK from YubiKey ECDH key agreement.
     *
     * Flow:
     *  1. Verify PIN on YubiKey
     *  2. Load stored ephemeral public key, wrappedDMK, salt, databaseId
     *  3. Z = YubiKey.calculateSecret(slot9d, stored_ephemeral_pub)
     *  4. KEK = HKDF-SHA256(Z, salt, info)
     *  5. DMK = AES-256-GCM-Decrypt(KEK, wrappedDMK)
     *  6. Zeroize: Z, KEK
     *  7. Return DMK for SQLCipher
     *
     * If GCM tag verification fails, a different YubiKey was used (wrong shared secret).
     */
    suspend fun unlockDatabase(device: YubiKeyDevice, pin: String): Result<String> {
        if (isAppLocked()) {
            return Result.failure(Exception("App is permanently locked. You have lost access to this database."))
        }
        if (isPinLocked()) {
            return Result.failure(Exception("PIN_LOCKED:PIN is locked after 3 failed attempts. Please enter your PUK to unlock."))
        }
        val (isLocked, waitTime) = pinRateLimiter.checkRateLimit()
        if (isLocked) {
            return Result.failure(Exception("Too many failed attempts. Please wait ${(waitTime / 1000) + 1} seconds."))
        }
        
        val pinChars = pin.toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        // [1] Verify PIN
                        pivSession.verifyPin(pinChars)
                        
                        // [2] Load stored parameters
                        val ephPubStr = DatabaseUtils.ksYubiKeyEphemeralPub.get()
                            ?: throw Exception("No YubiKey enrolled (missing ephemeral key)")
                        val wrappedDMKStr = DatabaseUtils.ksYubiKeyWrappedDMK.get()
                            ?: throw Exception("No YubiKey enrolled (missing wrapped DMK)")
                        val saltStr = DatabaseUtils.ksYubiKeyHkdfSalt.get()
                            ?: throw Exception("No YubiKey enrolled (missing salt)")
                        val dbId = ChatController.appPrefs.yubiKeyDatabaseId.get()
                            ?: throw Exception("No YubiKey enrolled (missing database ID)")
                        
                        // Reconstruct ephemeral public key
                        val ephPubBytes = Base64.decode(ephPubStr, Base64.NO_WRAP)
                        val keyFactory = KeyFactory.getInstance("EC")
                        val ephemeralPub = keyFactory.generatePublic(X509EncodedKeySpec(ephPubBytes)) as ECPublicKey
                        
                        // [3] ECDH: Z = YubiKey.calculateSecret(slot9d, ephemeralPub.W)
                        val z = pivSession.calculateSecret(Slot.KEY_MANAGEMENT, ephemeralPub.w)
                        if (z.all { it == 0.toByte() }) {
                            throw Exception("ECDH produced zero output")
                        }
                        
                        // [4] KEK = HKDF-SHA256(Z, salt, info, 32)
                        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
                        val info = (HKDF_INFO_PREFIX + dbId).toByteArray(Charsets.UTF_8)
                        val kek = SecureMemory.hkdfSha256(z, salt, info, 32)
                        
                        // [5] DMK = AES-256-GCM-Decrypt(KEK, wrappedDMK)
                        val wrappedDMK = Base64.decode(wrappedDMKStr, Base64.NO_WRAP)
                        val dmk = try {
                            aesGcmDecrypt(kek, wrappedDMK)
                        } catch (e: AEADBadTagException) {
                            // GCM tag mismatch → wrong YubiKey (different ECDH shared secret)
                            throw SecurityException("Wrong YubiKey! GCM authentication failed.")
                        }
                        
                        val dmkString = Base64.encodeToString(dmk, Base64.NO_WRAP)
                        
                        // [6] Zeroize all intermediates
                        SecureMemory.wipe(z)
                        SecureMemory.wipe(kek)
                        SecureMemory.wipe(salt)
                        SecureMemory.wipe(wrappedDMK)
                        SecureMemory.wipe(dmk)
                        SecureMemory.wipe(info)
                        
                        // Success: reset lockout
                        val ls = loadLockoutState()
                        saveLockoutState(ls.copy(pinFailedAttempts = 0))
                        pinRateLimiter.recordSuccess()
                        
                        Result.success(dmkString)
                    } catch (e: SecurityException) {
                        // Wrong YubiKey
                        val ls = loadLockoutState()
                        val failures = ls.pinFailedAttempts + 1
                        pinRateLimiter.recordFailure()
                        if (failures >= MAX_PIN_ATTEMPTS) {
                            saveLockoutState(ls.copy(pinLocked = true, pinFailedAttempts = failures))
                            Result.failure(Exception("PIN_LOCKED:PIN is locked after 3 failed attempts. Please enter your PUK to unlock."))
                        } else {
                            saveLockoutState(ls.copy(pinFailedAttempts = failures))
                            Result.failure(Exception("Wrong YubiKey! Please use the enrolled YubiKey. ${MAX_PIN_ATTEMPTS - failures} attempt(s) remaining."))
                        }
                    } catch (e: Exception) {
                        val ls = loadLockoutState()
                        val failures = ls.pinFailedAttempts + 1
                        pinRateLimiter.recordFailure()
                        Log.e(TAG, "ECDH unlock failed (attempt $failures/$MAX_PIN_ATTEMPTS)")
                        if (failures >= MAX_PIN_ATTEMPTS) {
                            saveLockoutState(ls.copy(pinLocked = true, pinFailedAttempts = failures))
                            Result.failure(Exception("PIN_LOCKED:PIN is locked after 3 failed attempts. Please enter your PUK to unlock."))
                        } else {
                            saveLockoutState(ls.copy(pinFailedAttempts = failures))
                            val remaining = MAX_PIN_ATTEMPTS - failures
                            Result.failure(Exception("Invalid PIN or connection error. $remaining attempt(s) remaining."))
                        }
                    }
                }
            } ?: Result.failure(Exception("Operation timed out. Please keep YubiKey close and try again."))
        } finally {
            SecureMemory.wipe(pinChars)
        }
    }
    
    /**
     * Verify PIN without performing any operations
     */
    suspend fun verifyPin(device: YubiKeyDevice, pin: String): Result<Boolean> {
        // Check rate limiting
        val (isLocked, waitTime) = pinRateLimiter.checkRateLimit()
        if (isLocked) {
            val waitSeconds = (waitTime / 1000) + 1
            return Result.failure(Exception("Too many failed attempts. Please wait $waitSeconds seconds."))
        }
        
        val pinChars = pin.toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        pivSession.verifyPin(pinChars)
                        pinRateLimiter.recordSuccess()
                        Result.success(true)
                    } catch (e: Exception) {
                        pinRateLimiter.recordFailure()
                        Result.success(false)
                    }
                }
            } ?: Result.failure(Exception("Operation timed out."))
        } finally {
            SecureMemory.wipe(pinChars)
        }
    }
    
    // --- Lockout state backed by Keystore-encrypted storage (C4 fix) ---
    
    private data class LockoutState(
        val pinLocked: Boolean = false,
        val pinFailedAttempts: Int = 0,
        val pukFailedAttempts: Int = 0,
        val appLocked: Boolean = false
    ) {
        fun serialize(): String = "$pinLocked|$pinFailedAttempts|$pukFailedAttempts|$appLocked"
        
        companion object {
            fun deserialize(s: String?): LockoutState {
                if (s.isNullOrEmpty()) return LockoutState()
                val parts = s.split("|")
                return try {
                    LockoutState(
                        pinLocked = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: false,
                        pinFailedAttempts = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                        pukFailedAttempts = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                        appLocked = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                    )
                } catch (_: Exception) { LockoutState() }
            }
        }
    }
    
    private fun loadLockoutState(): LockoutState {
        return LockoutState.deserialize(DatabaseUtils.ksYubiKeyLockoutState.get())
    }
    
    private fun saveLockoutState(state: LockoutState) {
        DatabaseUtils.ksYubiKeyLockoutState.set(state.serialize())
        // Mirror to SharedPreferences as defense-in-depth (non-authoritative)
        ChatController.appPrefs.yubiKeyPinLocked.set(state.pinLocked)
        ChatController.appPrefs.yubiKeyPinFailedAttempts.set(state.pinFailedAttempts)
        ChatController.appPrefs.yubiKeyPukFailedAttempts.set(state.pukFailedAttempts)
        ChatController.appPrefs.yubiKeyAppLocked.set(state.appLocked)
    }
    
    fun isAppLocked(): Boolean {
        return loadLockoutState().appLocked
    }
    
    fun isPinLocked(): Boolean {
        return loadLockoutState().pinLocked
    }
    
    fun getRemainingPinAttempts(): Int {
        return maxOf(0, MAX_PIN_ATTEMPTS - loadLockoutState().pinFailedAttempts)
    }
    
    fun getRemainingPukAttempts(): Int {
        return maxOf(0, MAX_PUK_ATTEMPTS - loadLockoutState().pukFailedAttempts)
    }
    
    /**
     * Verify PIN AND YubiKey identity via ECDH unlock attempt.
     * In the v4 architecture, identity verification is implicit: if ECDH + GCM-unwrap succeeds,
     * the YubiKey is correct. This method is kept for API compatibility with callers that
     * don't need the DMK (e.g. PUK unblock flow).
     *
     * For database unlock, prefer unlockDatabase() which returns the DMK directly.
     */
    suspend fun verifyPinAndIdentity(device: YubiKeyDevice, pin: String): Result<Boolean> {
        val result = unlockDatabase(device, pin)
        return if (result.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Verification failed"))
        }
    }
    
    /**
     * Verify PUK to unlock PIN after 3 failed PIN attempts
     * SECURITY: 3 consecutive PUK failures permanently locks the app
     * NOTE: This does NOT require YubiKey tap - PUK is verified against stored hash
     */
    fun verifyPukAndUnlockPin(puk: String): Result<Boolean> {
        
        if (isAppLocked()) {
            Log.e(TAG, "App is permanently locked")
            return Result.failure(Exception("App is permanently locked. You have lost access to this database."))
        }
        
        val pukChars = puk.toCharArray()
        
        return try {
            // SECURITY: Read PUK hash from Keystore-encrypted storage (C3 fix)
            val storedPukHash = DatabaseUtils.ksYubiKeyPukHash.get()
            
            if (storedPukHash == null) {
                return Result.failure(Exception("PUK not configured. Please re-enroll your YubiKey."))
            }
            
            val pukHash = hashPuk(puk)
            
            if (pukHash == storedPukHash) {
                
                saveLockoutState(LockoutState())
                pinRateLimiter.reset()
                
                Result.success(true)
            } else {
                val ls = loadLockoutState()
                val currentFailures = ls.pukFailedAttempts + 1
                
                
                if (currentFailures >= MAX_PUK_ATTEMPTS) {
                    saveLockoutState(ls.copy(appLocked = true, pukFailedAttempts = currentFailures))
                    Result.failure(Exception("APP_LOCKED:App is permanently locked. You have lost access to this database."))
                } else {
                    saveLockoutState(ls.copy(pukFailedAttempts = currentFailures))
                    val remaining = MAX_PUK_ATTEMPTS - currentFailures
                    Result.failure(Exception("Invalid PUK. $remaining attempt${if (remaining > 1) "s" else ""} remaining before permanent lockout."))
                }
            }
        } finally {
            SecureMemory.wipe(pukChars)
        }
    }
    
    /**
     * Hash PUK for storage/comparison (uses HMAC with device secret)
     */
    private fun hashPuk(puk: String): String {
        val pukBytes = puk.toByteArray(Charsets.UTF_8)
        val hashBytes = SecureMemory.hmacSha256(pukBytes, identitySecret)
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
    
    /**
     * Unblock YubiKey PIN using PUK and then verify PIN identity
     * This is called after PUK verification to reset the physical YubiKey's PIN
     * REQUIRES: YubiKey tap
     */
    suspend fun unblockAndVerifyPin(device: YubiKeyDevice, puk: String, pin: String): Result<Boolean> {
        
        val pukChars = puk.toCharArray()
        val pinChars = pin.toCharArray()
        
        return try {
            withPivSession(device) { piv: PivSession ->
                try {
                    // First, unblock the  YubiKey's PIN using PUK
                    // This resets the PIN retry counter and sets the PIN to the provided value
                    piv.unblockPin(pukChars, pinChars)
                    
                    // Now verify PIN (it should work since we just set it)
                    piv.verifyPin(pinChars)
                    
                    // In v4 architecture, identity is verified via ECDH during unlock.
                    // PIN unblock + verify succeeded, so the physical YubiKey is present.
                    Result.success(true)
                } catch (e: com.yubico.yubikit.piv.InvalidPinException) {
                    Result.failure(Exception("Invalid PUK. The YubiKey rejected the PUK."))
                } catch (e: Exception) {
                    Log.e(TAG, "Error during unblock: ${e.message}")
                    Result.failure(e)
                }
            }
        } finally {
            SecureMemory.wipe(pukChars)
            SecureMemory.wipe(pinChars)
        }
    }
    
    // verifyWithChallenge removed — identity verification is now implicit via ECDH + GCM unwrap
    
    /**
     * Helper: Execute operation with PIV session and timeout
     */
    private suspend fun <T> withPivSession(
        device: YubiKeyDevice,
        block: (PivSession) -> Result<T>
    ): Result<T> {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (device is NfcYubiKeyDevice) {
                    device.requestConnection(SmartCardConnection::class.java) { connectionResult ->
                        var pivSession: PivSession? = null
                        try {
                            val connection = try {
                                connectionResult.value
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to get connection")
                                continuation.resume(
                                    Result.failure(Exception("Failed to connect to YubiKey. Please try again."))
                                )
                                return@requestConnection
                            }
                            
                            if (connection == null) {
                                Log.e(TAG, "Connection is null")
                                continuation.resume(
                                    Result.failure(Exception("Failed to connect to YubiKey. Please try again."))
                                )
                                return@requestConnection
                            }
                            
                            pivSession = PivSession(connection)
                            val operationResult = block(pivSession)
                            
                            try {
                                pivSession.close()
                                pivSession = null
                            } catch (e: Exception) {
                            }
                            
                            continuation.resume(operationResult)
                        } catch (e: android.nfc.TagLostException) {
                            // YubiKey was moved away during operation
                            Log.e(TAG, "Tag lost during PIV operation")
                            
                            try {
                                pivSession?.close()
                            } catch (closeError: Exception) {
                                // Ignore close errors
                            }
                            
                            continuation.resume(
                                Result.failure(Exception("YubiKey connection lost. Please keep your YubiKey in place and try again."))
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during PIV operation")
                            
                            try {
                                pivSession?.close()
                            } catch (closeError: Exception) {
                                // Ignore close errors
                            }
                            
                            // Handle TagLostException wrapped in other exceptions
                            val rootCause = generateSequence(e) { it.cause as? Exception }.lastOrNull() ?: e
                            if (rootCause is android.nfc.TagLostException) {
                                continuation.resume(
                                    Result.failure(Exception("YubiKey connection lost. Please keep your YubiKey in place and try again."))
                                )
                            } else {
                                continuation.resume(Result.failure(e))
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Device is not an NFC YubiKey")
                    continuation.resume(Result.failure(Exception("Device is not an NFC YubiKey")))
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                // NFC device's internal executor has been shut down (connection stale/lost)
                Log.e(TAG, "YubiKey connection is stale - executor terminated")
                continuation.resume(
                    Result.failure(Exception("YubiKey connection lost. Please tap your YubiKey again."))
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception in withPivSession")
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    // --- AES-256-GCM key wrapping for DMK ---
    
    private fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv                           // 12 bytes auto-generated
        val ciphertext = cipher.doFinal(plaintext)   // ciphertext + 16-byte GCM tag
        return iv + ciphertext                       // IV(12) || ciphertext || tag(16)
    }
    
    private fun aesGcmDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(data.size > 12) { "AES-GCM data too short" }
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Hash EC public key point for identity verification using HMAC-SHA256
     * SECURITY: Uses device-specific secret to prevent key hash prediction
     */
    private fun hashEcPoint(publicKey: ECPublicKey): String {
        val ecPoint = publicKey.w
        
        val xBytes = normalizeCoordinate(ecPoint.affineX)
        val yBytes = normalizeCoordinate(ecPoint.affineY)
        
        val pointBytes = xBytes + yBytes
        
        // SECURITY: Use HMAC-SHA256 with device-specific secret instead of plain SHA-256
        // This prevents an attacker who knows the EC point from computing the expected hash
        val hashBytes = SecureMemory.hmacSha256(pointBytes, identitySecret)
        val result = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        
        SecureMemory.wipe(xBytes)
        SecureMemory.wipe(yBytes)
        SecureMemory.wipe(pointBytes)
        SecureMemory.wipe(hashBytes)
        
        return result
    }
    
    /**
     * Normalize a BigInteger coordinate to exactly 32 bytes
     */
    private fun normalizeCoordinate(coord: java.math.BigInteger): ByteArray {
        val bytes = coord.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }
    
    // PIV applet AID (NIST SP 800-73)
    private val PIV_AID = byteArrayOf(
        0xA0.toByte(), 0x00, 0x00, 0x03, 0x08, 0x00, 0x00, 0x10, 0x00, 0x01, 0x00
    )
    
    /**
     * Check if an NFC tag is a YubiKey by probing for the PIV applet.
     * Credit cards, transit cards, and other NFC devices will fail this check.
     *
     * Sends a SELECT command for the PIV AID; only devices with a PIV applet
     * (YubiKeys, smart cards with PIV) will respond with SW 9000 (success).
     */
    fun isYubiKeyTag(tag: Tag): Boolean {
        if (!tag.techList.contains("android.nfc.tech.IsoDep")) return false
        
        val isoDep = IsoDep.get(tag) ?: return false
        return try {
            isoDep.connect()
            try {
                // SELECT the PIV applet by AID
                val selectApdu = buildSelectApdu(PIV_AID)
                val response = isoDep.transceive(selectApdu)
                
                // Check SW (last 2 bytes): 0x90 0x00 = success
                val sw = if (response.size >= 2) {
                    ((response[response.size - 2].toInt() and 0xFF) shl 8) or
                        (response[response.size - 1].toInt() and 0xFF)
                } else {
                    0
                }
                
                val isPiv = sw == 0x9000
                if (isPiv) {
                } else {
                }
                isPiv
            } finally {
                try { isoDep.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Build an ISO 7816-4 SELECT command APDU for the given AID.
     */
    private fun buildSelectApdu(aid: ByteArray): ByteArray {
        // CLA=00, INS=A4, P1=04 (select by name), P2=00, Lc=len, AID
        val apdu = ByteArray(5 + aid.size)
        apdu[0] = 0x00          // CLA
        apdu[1] = 0xA4.toByte() // INS: SELECT
        apdu[2] = 0x04          // P1: Select by DF name
        apdu[3] = 0x00          // P2
        apdu[4] = aid.size.toByte() // Lc
        System.arraycopy(aid, 0, apdu, 5, aid.size)
        return apdu
    }
    
    /**
     * Verify an NFC device discovered by YubiKit SDK is actually a YubiKey
     * by attempting to open a PIV session. Only YubiKeys (and PIV smart cards)
     * will succeed. Credit cards, transit cards, etc. will fail.
     */
    fun verifyAndAcceptDevice(device: YubiKeyDevice, callback: (Boolean) -> Unit) {
        if (device !is NfcYubiKeyDevice) {
            callback(false)
            return
        }
        
        device.requestConnection(SmartCardConnection::class.java) { connectionResult ->
            try {
                val connection = connectionResult.value ?: run {
                    callback(false)
                    return@requestConnection
                }
                val pivSession = PivSession(connection)
                // If PivSession opens without exception, this device has a PIV applet
                try { pivSession.close() } catch (_: Exception) {}
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }
    
    /**
     * Remove YubiKey enrollment (for factory reset)
     */
    fun removeYubiKeyEnrollment() {
        // Clear all Keystore-encrypted storage (v3 + v4 entries)
        DatabaseUtils.ksYubiKeyChallenge.remove()
        DatabaseUtils.ksYubiKeyManagementKey.remove()
        DatabaseUtils.ksYubiKeyIdentitySecret.remove()
        DatabaseUtils.ksYubiKeyPublicKey.remove()
        DatabaseUtils.ksYubiKeyPukHash.remove()
        DatabaseUtils.ksYubiKeyLockoutState.remove()
        DatabaseUtils.ksYubiKeyWrappedDMK.remove()
        DatabaseUtils.ksYubiKeyEphemeralPub.remove()
        DatabaseUtils.ksYubiKeyHkdfSalt.remove()
        ChatController.appPrefs.yubiKeyDatabaseId.set(null)
        
        // Clear legacy plaintext preferences
        @Suppress("DEPRECATION")
        ChatController.appPrefs.yubiKeyChallenge.set(null)
        ChatController.appPrefs.yubiKeyUid.set(null)
        @Suppress("DEPRECATION")
        ChatController.appPrefs.yubiKeyPin.set(null)
        @Suppress("DEPRECATION")
        ChatController.appPrefs.yubiKeyPuk.set(null)
        @Suppress("DEPRECATION")
        ChatController.appPrefs.yubiKeyManagementKey.set(null)
        @Suppress("DEPRECATION")
        ChatController.appPrefs.yubiKeyPukHash.set(null)
        ChatController.appPrefs.yubiKeyPinSet.set(false)
        ChatController.appPrefs.yubiKeyPukSet.set(false)
        ChatController.appPrefs.yubiKeyManagementKeySet.set(false)
        ChatController.appPrefs.useYubiKeyForDB.set(false)
        ChatController.appPrefs.yubiKeyEnrollmentVersion.set(0)
        saveLockoutState(LockoutState())
        pinRateLimiter.reset()
    }
    
    /**
     * Get remaining PIN attempts from YubiKey
     */
    suspend fun getRemainingPinAttempts(device: YubiKeyDevice): Result<Int> {
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    val attempts = pivSession.pinAttempts
                    Result.success(attempts)
                }
            } ?: Result.failure(Exception("Operation timed out."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get PIN attempts")
            Result.failure(e)
        }
    }
    
    /**
     * Reset YubiKey PIN using PUK
     */
    suspend fun resetPinWithPuk(device: YubiKeyDevice, puk: String, newPin: String): Result<Unit> {
        // Validate new PIN
        val pinError = WeakPinDetector.validatePin(newPin)
        if (pinError != null) {
            return Result.failure(Exception(pinError))
        }
        
        val pukChars = puk.toCharArray()
        val newPinChars = newPin.toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        pivSession.unblockPin(pukChars, newPinChars)
                        
                        pinRateLimiter.reset()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG, "PIN reset failed")
                        Result.failure(Exception("Failed to reset PIN. Check your PUK."))
                    }
                }
            } ?: Result.failure(Exception("Operation timed out."))
        } finally {
            SecureMemory.wipe(pukChars)
            SecureMemory.wipe(newPinChars)
        }
    }
    
    /**
     * Reset YubiKey PIV applet to factory defaults
     * WARNING: This will delete all PIV keys and certificates!
     */
    suspend fun resetToFactoryDefaults(device: YubiKeyDevice): Result<Unit> {
        return try {
            
            val result = withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        val wrongPin = "000000".toCharArray()
                        repeat(5) {
                            try {
                                pivSession.verifyPin(wrongPin)
                            } catch (e: Exception) {
                                // Expected to fail
                            }
                        }
                        SecureMemory.wipe(wrongPin)
                        
                        val wrongPuk = "00000000".toCharArray()
                        val wrongPinForReset = "000000".toCharArray()
                        repeat(5) {
                            try {
                                pivSession.unblockPin(wrongPuk, wrongPinForReset)
                            } catch (e: Exception) {
                                // Expected to fail
                            }
                        }
                        SecureMemory.wipe(wrongPuk)
                        SecureMemory.wipe(wrongPinForReset)
                        
                        try {
                            pivSession.reset()
                        } catch (e: Exception) {
                            Log.e(TAG, "PIV reset failed: ${e.message}")
                            return@withPivSession Result.failure(Exception("PIV reset failed. Keep your YubiKey in place and try again."))
                        }
                        
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Reset failed: ${e.message}")
                        return@withPivSession Result.failure(Exception("Reset failed. Keep your YubiKey in place and try again."))
                    }
                }
            } ?: Result.failure(Exception("Operation timed out. Keep your YubiKey in place and try again."))
            
            // Check if the operation succeeded
            if (result.isFailure) {
                return result
            }
            
            delay(500)
            
            pinRateLimiter.reset()
            saveLockoutState(LockoutState())
            DatabaseUtils.ksYubiKeyPukHash.remove()
            DatabaseUtils.ksYubiKeyWrappedDMK.remove()
            DatabaseUtils.ksYubiKeyEphemeralPub.remove()
            DatabaseUtils.ksYubiKeyHkdfSalt.remove()
            ChatController.appPrefs.yubiKeyDatabaseId.set(null)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Factory reset failed: ${e.message}")
            Result.failure(Exception("Unable to reset YubiKey. Keep your YubiKey in place and try again."))
        }
    }
    
    /**
     * Check if YubiKey PIV is in factory default state
     */
    suspend fun isFactoryDefault(device: YubiKeyDevice): Result<Boolean> {
        val defaultPin = "123456".toCharArray()
        
        return try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                withPivSession(device) { pivSession ->
                    try {
                        pivSession.verifyPin(defaultPin)
                        Result.success(true)
                    } catch (e: Exception) {
                        Result.success(false)
                    }
                }
            } ?: Result.failure(Exception("Operation timed out."))
        } finally {
            SecureMemory.wipe(defaultPin)
        }
    }
}
