package chat.simplex.common.views.onboarding

import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.AppLock
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.database.stopChatAsync
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.helpers.ShredgramInlineSpinner
import chat.simplex.common.views.localauth.PasscodeView
import chat.simplex.common.views.usersettings.LAMode
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

// Shredgram Typography tokens - EXACT match from TypographyTokens.kt
private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f

// Font sizes from TypographyTokens.kt
private val font12 = 12.sp
private val font14 = 14.sp
private val font30 = 30.sp

// Shredgram Colors
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val OnSurfaceVariant = Color(0xFF3D4042)
private val Green500 = Color(0xFF11994A)
private val WarningOrange = Color(0xFFFF9500)

@Composable
fun EncryptDatabaseWithYubiKey(m: ChatModel) {
  // Back to YubiKey setup screen
  BackHandler {
    // SECURITY: Clear PIN from memory when navigating away
    m.secureYubiKeyPin.clear()
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
  }
  
  val needsPinEntry = remember { mutableStateOf(!m.secureYubiKeyPin.isSet()) }
  val isProcessing = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  
  val errorOccurred = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf<String?>(null) }
  val prefs = m.controller.appPrefs
  
  // Supervised coroutine scope for safe cancellation
  val encryptionScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
  var encryptionJob by remember { mutableStateOf<Job?>(null) }
  
  // Helper function to create user and proceed (same as passphrase flow)
  fun autoCreateUserAndProceed() {
    withBGApi {
      // Use default public profile name
      val defaultDisplayName = "Set Up Name"

      // If a user was already created (e.g. called twice), skip creation
      val existingUser = m.controller.apiGetActiveUser(null)
      if (existingUser != null) {
        chatModel.currentUser.value = existingUser
        chatModel.localUserCreated.value = true
        m.controller.ensureDefaultChatItemTTLInitialized(null)
        m.controller.getUserChatData(null)
        m.controller.appPrefs.notificationsMode.set(NotificationsMode.SERVICE)
        val currentLockMode = m.controller.appPrefs.laMode.get()
        if (currentLockMode == LAMode.PASSCODE) {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_6_SetupSelfDestruct)
        } else {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step3_ChooseServerOperators)
        }
        return@withBGApi
      }

      // Create user automatically
      val user = m.controller.apiCreateActiveUser(
        null, 
        Profile(displayName = defaultDisplayName, fullName = "", shortDescr = null, image = null)
      )
      
      if (user != null) {
        chatModel.currentUser.value = user
        chatModel.localUserCreated.value = true

        // Initialize global auto-delete ("delete messages after") default for new profile and refresh user chat data.
        m.controller.ensureDefaultChatItemTTLInitialized(null)
        m.controller.getUserChatData(null)
        
        // Set notifications mode to Service (instant) by default
        m.controller.appPrefs.notificationsMode.set(NotificationsMode.SERVICE)
        
        // Check lock mode - only show self-destruct for passcode mode
        val currentLockMode = m.controller.appPrefs.laMode.get()
        if (currentLockMode == LAMode.PASSCODE) {
          // Go to self-destruct setup screen for passcode mode
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_6_SetupSelfDestruct)
        } else {
          // Skip self-destruct for system lock, go directly to conditions
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step3_ChooseServerOperators)
        }
      } else {
        // If user creation fails, fallback to next step
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step3_ChooseServerOperators)
      }
    }
  }
  
  // Helper to start chat after database encryption (same as passphrase flow)
  suspend fun startChatWithKey(key: String?) {
    initChatController(key)
    m.chatDbChanged.value = false
    m.chatRunning.value = true
  }
  
  // If PIN is needed, show PIN entry screen first - using PasscodeView for consistent UI
  // SECURITY: Use remember (not rememberSaveable) to avoid persisting PIN to disk
  val pinState = remember { mutableStateOf("") }
  
  if (needsPinEntry.value) {
    PasscodeView(
      passcode = pinState,
      title = stringResource(MR.strings.yubikey_encrypt_enter_pin_title),
      reason = stringResource(MR.strings.yubikey_encrypt_enter_pin_reason),
      submitLabel = stringResource(MR.strings.yubikey_encrypt_btn_continue),
      submitEnabled = { it.length in 6..8 },
      submit = {
        // SECURITY: Store PIN in secure container
        m.secureYubiKeyPin.set(pinState.value)
        pinState.value = "" // Clear the UI state immediately
        needsPinEntry.value = false
      },
      cancel = {
        pinState.value = "" // Clear on cancel
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
      }
    )
    return
  }
  
  // Track if we've already started processing to avoid duplicate runs
  val processingStarted = remember { mutableStateOf(false) }
  
  // Function to perform the actual encryption (runs in supervised coroutine scope)
  fun performYubiKeyEncryption() {
    if (processingStarted.value) return
    processingStarted.value = true
    isProcessing.value = true
    
    // Reset detection immediately
    m.yubiKeyDetected.value = false
    
    // SECURITY: Use supervised scope for safe cancellation and cleanup
    encryptionJob = encryptionScope.launch {
      try {
        val pin = m.secureYubiKeyPin.usePinString { it }
        
        if (pin.isNullOrEmpty()) {
          isProcessing.value = false
          processingStarted.value = false
          errorOccurred.value = true
          errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_pin_not_found)
          return@launch
        }
        
        // Try up to 3 times in case of NFC connection loss
        var attempts = 0
        var result: Result<String>? = null
        
        while (attempts < 3) {
          attempts++
          
          if (attempts > 1) {
            // Show retry message
            errorMessage.value = "${generalGetString(MR.strings.yubikey_encrypt_retrying)} ($attempts/3)"
            delay(1000) // Brief pause before retry
          }
          
          result = chat.simplex.common.platform.YubiKeyBridge.enrollForDatabaseEncryption(pin)
          
          if (result.isSuccess) {
            break
          } else {
            val error = result.exceptionOrNull()?.message ?: ""
            // Only retry if it was an NFC connection issue
            if (error.contains("NFC connection lost", ignoreCase = true) || 
                error.contains("Tag", ignoreCase = true) ||
                error.contains("timed out", ignoreCase = true)) {
              if (attempts < 3) {
                continue
              }
            } else {
              break
            }
          }
        }
        
        if (result != null && result.isSuccess) {
          val dbKey = result.getOrNull()
          
          if (dbKey != null && dbKey.isNotEmpty()) {
            try {
              // Step 1: Stop chat if running
              if (m.chatRunning.value == true) {
                stopChatAsync(m)
              }
              
              // Step 2: Set preferences for YubiKey encryption
              prefs.storeDBPassphrase.set(false)
              prefs.useYubiKeyForDB.set(true)
              
              // Step 3: Actually encrypt the database
              val currentKey = if (prefs.initialRandomDBPassphrase.get()) {
                DatabaseUtils.ksDatabasePassword.get() ?: ""
              } else {
                ""
              }
              
              val encryptionError = m.controller.apiStorageEncryption(currentKey, dbKey)
              
              if (encryptionError != null) {
                isProcessing.value = false
                processingStarted.value = false
                errorOccurred.value = true
                errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_db_failed)
                return@launch
              }
              
              // Store the DMK for this session only; on restart ECDH will re-derive it
              DatabaseUtils.ksDatabasePassword.set(dbKey)
              prefs.initialRandomDBPassphrase.set(false)
              
              // Step 5: Start the chat with the new key
              startChatWithKey(dbKey)
              
              // SECURITY: Clear the PIN from memory now that setup is complete
              m.secureYubiKeyPin.clear()
              
              // Switch to main thread for UI updates
              kotlinx.coroutines.withContext(Dispatchers.Main) {
                isProcessing.value = false
                showSuccess.value = true
                
                delay(2000)
                
                // Step 6: Show lock authentication setup
                AppLock.showLANotice(prefs.laNoticeShown) { _ ->
                  autoCreateUserAndProceed()
                }
              }
              
            } catch (e: Exception) {
              isProcessing.value = false
              processingStarted.value = false
              errorOccurred.value = true
              errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_failed)
              // Rollback
              prefs.storeDBPassphrase.set(true)
              prefs.useYubiKeyForDB.set(false)
            }
          } else {
            isProcessing.value = false
            processingStarted.value = false
            errorOccurred.value = true
            errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_derive_failed)
          }
        } else {
          isProcessing.value = false
          processingStarted.value = false
          errorOccurred.value = true
          // SECURITY: Don't expose internal error details
          val errorMsg = result?.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_encrypt_unknown_error)
          errorMessage.value = when {
            errorMsg.contains("PIN", ignoreCase = true) -> {
              // Invalid/expired PIN should return user to PIN entry step.
              m.secureYubiKeyPin.clear()
              needsPinEntry.value = true
              generalGetString(MR.strings.yubikey_encrypt_invalid_pin)
            }
            errorMsg.contains("YubiKey", ignoreCase = true) -> errorMsg
            errorMsg.contains("timed out", ignoreCase = true) -> generalGetString(MR.strings.yubikey_encrypt_timed_out)
            else -> generalGetString(MR.strings.yubikey_encrypt_enrollment_failed)
          }
        }
      } catch (e: Exception) {
        isProcessing.value = false
        processingStarted.value = false
        errorOccurred.value = true
        errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_failed)
      }
    }
  }
  
  // Reset stale YubiKey detection when screen first loads
  LaunchedEffect(Unit) {
    m.yubiKeyDetected.value = false
  }
  
  // Watch for YubiKey detection and trigger encryption
  LaunchedEffect(m.yubiKeyDetected.value) {
    if (m.yubiKeyDetected.value && !processingStarted.value) {
      performYubiKeyEncryption()
    }
  }
  
  // Track if showing modal (for blur effect)
  val showingModal = showSuccess.value || isProcessing.value
  
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)  // Shredgram: space24DP
        .then(if (showingModal) Modifier.blur(16.dp) else Modifier),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // TopBar - Shredgram style with back button and centered logo
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp)
      ) {
        // Back button on the left
        IconButton(
          onClick = {
            m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
          },
          modifier = Modifier.align(Alignment.CenterStart)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = stringResource(MR.strings.back),
            tint = MaterialTheme.colors.onBackground
          )
        }
        
        // Centered text logo
        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = stringResource(MR.strings.yubikey_cd_logo),
          modifier = Modifier.align(Alignment.Center)
        )
      }
      
      // Key lock icon - Shredgram: space24DP
      Icon(
        painter = painterResource(MR.images.ic_key_lock),
        contentDescription = stringResource(MR.strings.yubikey_encrypt_cd_encrypt),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = stringResource(MR.strings.yubikey_encrypt_title),
        fontFamily = Manrope,
        fontSize = font30,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        lineHeight = (font30.value * lineHeightHeadlineS).sp
      )
      
      Spacer(Modifier.height(8.dp))  // Shredgram: space8DP
      
      // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
      Text(
        text = stringResource(MR.strings.yubikey_encrypt_description),
        fontFamily = DMSans,
        fontSize = font14,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font14.value * lineHeightBody).sp
      )
      
      // NFC Tap Screen - Shredgram: centered with icon 92dp
      // Hide main content when success overlay is showing
      if (!showSuccess.value) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // Passkey icon - Shredgram: 92dp
            Icon(
              painter = painterResource(MR.images.ic_passkey),
              contentDescription = stringResource(MR.strings.yubikey_cd_yubikey),
              tint = Color.Unspecified,
              modifier = Modifier.size(92.dp)
            )
            
            Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
            
            // Status text - Shredgram: bodyMedium
            Text(
              text = stringResource(MR.strings.yubikey_encrypt_tap_passkey),
              fontFamily = DMSans,
              fontSize = font14,
              fontWeight = FontWeight.Normal,
              color = OnSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      } else {
        Spacer(Modifier.weight(1f))
      }
      
      // Bottom content
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      Spacer(Modifier.navigationBarsPadding())
    }
    
    // Error dialog overlay - Shredgram modal style
    if (errorOccurred.value && errorMessage.value != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.5f))
          .clickable { 
            errorOccurred.value = false
            errorMessage.value = null
          },
        contentAlignment = Alignment.Center
      ) {
        Surface(
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 320.dp),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colors.surface,
          elevation = 8.dp
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              painter = painterResource(MR.images.ic_error),
              contentDescription = stringResource(MR.strings.yubikey_cd_error),
              tint = MaterialTheme.colors.error,
              modifier = Modifier.size(48.dp)
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
              text = stringResource(MR.strings.yubikey_encrypt_failed_title),
              fontFamily = Manrope,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colors.onSurface,
              textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
              text = errorMessage.value ?: stringResource(MR.strings.yubikey_encrypt_unknown_error),
              fontFamily = DMSans,
              fontSize = font12,
              fontWeight = FontWeight.Normal,
              color = OnSurfaceVariant,
              textAlign = TextAlign.Center,
              lineHeight = (font12.value * lineHeightBody).sp
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
              text = stringResource(MR.strings.yubikey_encrypt_tap_to_dismiss),
              fontFamily = DMSans,
              fontSize = font12,
              fontWeight = FontWeight.Normal,
              color = ElectricBlue500,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
    
    // Processing spinner overlay - Shredgram style
    if (isProcessing.value) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
      ) {
        ShredgramInlineSpinner(
          modifier = Modifier,
          size = 40.dp
        )
      }
    }
    
    // Success dialog overlay - Shredgram modal style (UIModal.kt exact match)
    if (showSuccess.value) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
      ) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),  // Shredgram: space40DP
          shape = RoundedCornerShape(16.dp),  // RadiusLarge
          color = MaterialTheme.colors.surface,
          elevation = 12.dp  // Shredgram: space12DP shadowElevation
        ) {
          Column(
            modifier = Modifier.padding(32.dp),  // Shredgram: space32DP
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // Icon - Shredgram: icon_check_green is 24dp natural size
            Icon(
              painter = painterResource(MR.images.ic_check),
              contentDescription = stringResource(MR.strings.yubikey_cd_success),
              tint = Green500,
              modifier = Modifier.size(24.dp)  // Shredgram: 24dp (natural size of icon_check_green)
            )
            
            Spacer(Modifier.height(8.dp))  // Shredgram: space8DP after icon
            
            // Title - Shredgram: bodyLargeBold (DMSans Bold 16sp)
            Text(
              text = stringResource(MR.strings.yubikey_encrypt_success),
              fontFamily = DMSans,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colors.onSurface,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
  
  // Cleanup: restart chat if it was stopped but encryption didn't complete
  // NOTE: PIN is NOT cleared here to avoid race conditions.
  // PIN is cleared explicitly in BackHandler and error/success handlers.
  DisposableEffect(Unit) {
    onDispose {
      // Cancel any pending encryption operation
      encryptionJob?.cancel()
      
      if (m.chatRunning.value != true && !showSuccess.value) {
        withBGApi {
          val user = chatController.apiGetActiveUser(null)
          if (user != null) {
            m.controller.startChat(user)
          }
        }
      }
    }
  }
}

@Composable
private fun ShredgramTermsTextEncrypt() {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(MR.strings.yubikey_terms_prefix),
      fontFamily = DMSans,
      fontSize = font12,
      fontWeight = FontWeight.Normal,
      color = OnSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = (font12.value * lineHeightBody).sp
    )
    
    Row {
      Text(
        text = stringResource(MR.strings.yubikey_terms_of_service),
        fontFamily = DMSans,
        fontSize = font12,
        fontWeight = FontWeight.Normal,
        color = ElectricBlue500,
        modifier = Modifier.clickable { }
      )
      Text(
        text = stringResource(MR.strings.yubikey_terms_and),
        fontFamily = DMSans,
        fontSize = font12,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant
      )
      Text(
        text = stringResource(MR.strings.yubikey_privacy_policy),
        fontFamily = DMSans,
        fontSize = font12,
        fontWeight = FontWeight.Normal,
        color = ElectricBlue500,
        modifier = Modifier.clickable { }
      )
    }
  }
}

