package chat.simplex.common.views.onboarding

import androidx.compose.foundation.*
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
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.helpers.ShredgramInlineSpinner
import chat.simplex.common.views.localauth.PasscodeView
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.*
import java.security.SecureRandom

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

@Composable
fun SetupYubiKeyManagementKey(m: ChatModel) {
  // Back to YubiKey setup screen
  BackHandler {
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
  }
  
  val needsPinEntry = remember { mutableStateOf(!m.secureYubiKeyPin.isSet()) }
  val enteredPin = remember { mutableStateOf("") }
  val isProcessing = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  val errorOccurred = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  
  val managementKey = remember {
    mutableStateOf(
      // SECURITY: Use encrypted storage
      DatabaseUtils.ksYubiKeyManagementKey.get() 
        ?: generateManagementKey()
    )
  }
  
  // Store the generated key and reset stale YubiKey detection
  LaunchedEffect(Unit) {
    // Reset stale YubiKey detection when screen first loads
    m.yubiKeyDetected.value = false
    
    // SECURITY: Use encrypted storage for management key
    if (DatabaseUtils.ksYubiKeyManagementKey.get() == null) {
      DatabaseUtils.ksYubiKeyManagementKey.set(managementKey.value)
    }
  }
  
  // If PIN is needed, show PIN entry screen first - using PasscodeView for consistent UI
  val pinState = remember { mutableStateOf("") }
  
  if (needsPinEntry.value) {
    PasscodeView(
      passcode = pinState,
      title = stringResource(MR.strings.yubikey_mgmt_enter_pin_title),
      reason = stringResource(MR.strings.yubikey_mgmt_enter_pin_reason),
      submitLabel = stringResource(MR.strings.yubikey_mgmt_btn_continue),
      submitEnabled = { it.length in 6..8 },
      submit = {
        // SECURITY: Store PIN in secure container
        m.secureYubiKeyPin.set(pinState.value)
        pinState.value = "" // Clear UI state immediately
        needsPinEntry.value = false
      },
      cancel = {
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
      }
    )
    return
  }
  
  // Track key visibility
  val keyVisible = remember { mutableStateOf(false) }
  
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
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)  // Shredgram: space24DP
        .then(if (showingModal) Modifier.blur(16.dp) else Modifier),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // TopBar - Shredgram style with back button and logo
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = {
            m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
          }
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = stringResource(MR.strings.back),
            tint = MaterialTheme.colors.onBackground
          )
        }
        
        // Center logo
        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = stringResource(MR.strings.yubikey_cd_logo)
        )
        
        // Spacer for symmetry
        Spacer(Modifier.width(48.dp))
      }
      
      // Management Key icon - Shredgram: space24DP
      Icon(
        painter = painterResource(MR.images.ic_manage),
        contentDescription = stringResource(MR.strings.yubikey_mgmt_cd_key),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = stringResource(MR.strings.yubikey_mgmt_title),
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
        text = stringResource(MR.strings.yubikey_mgmt_description),
        fontFamily = DMSans,
        fontSize = font14,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font14.value * lineHeightBody).sp
      )
      
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // Management key display - Shredgram style with border and eye icon
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .border(
            width = 1.dp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp)
          )
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (keyVisible.value) managementKey.value else "•".repeat(managementKey.value.length),
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colors.onSurface,
          modifier = Modifier.weight(1f)
        )
        
        IconButton(
          onClick = { 
            keyVisible.value = !keyVisible.value
          },
          modifier = Modifier.size(24.dp)
        ) {
          Icon(
            painter = painterResource(
              if (keyVisible.value) MR.images.ic_visibility_off else MR.images.ic_visibility
            ),
            contentDescription = if (keyVisible.value) stringResource(MR.strings.yubikey_mgmt_cd_hide_key) else stringResource(MR.strings.yubikey_mgmt_cd_show_key),
            tint = OnSurfaceVariant
          )
        }
      }
      
      // NFC Tap Screen - Shredgram: centered with icon 92dp
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          if (errorOccurred.value) {
            Icon(
              painter = painterResource(MR.images.ic_error_filled),
              contentDescription = stringResource(MR.strings.yubikey_cd_error),
              tint = MaterialTheme.colors.error,
              modifier = Modifier.size(92.dp)
            )
          } else if (showSuccess.value) {
            Icon(
              painter = painterResource(MR.images.ic_check_circle_filled),
              contentDescription = stringResource(MR.strings.yubikey_cd_success),
              tint = Green500,
              modifier = Modifier.size(92.dp)
            )
          } else if (isProcessing.value) {
            ShredgramInlineSpinner(
              modifier = Modifier,
              size = 48.dp
            )
          } else {
            // YubiKey icon - Shredgram: 92dp
            Icon(
              painter = painterResource(MR.images.ic_passkey),
              contentDescription = stringResource(MR.strings.yubikey_cd_yubikey),
              tint = Color.Unspecified,
              modifier = Modifier.size(92.dp)
            )
          }
          
          Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
          
          // Status text - Shredgram: bodyMedium
          Text(
            text = when {
              errorOccurred.value -> errorMessage.value ?: stringResource(MR.strings.yubikey_mgmt_setup_failed)
              showSuccess.value -> stringResource(MR.strings.yubikey_mgmt_key_generated)
              isProcessing.value -> stringResource(MR.strings.yubikey_mgmt_configuring)
              else -> stringResource(MR.strings.yubikey_mgmt_tap_to_store)
            },
            fontFamily = DMSans,
            fontSize = font14,
            fontWeight = FontWeight.Normal,
            color = when {
              errorOccurred.value -> MaterialTheme.colors.error
              showSuccess.value -> Green500
              else -> OnSurfaceVariant
            },
            textAlign = TextAlign.Center
          )
          
          if (errorOccurred.value) {
            Spacer(Modifier.height(16.dp))
            
            val needsReset = errorMessage.value?.contains("authenticate", ignoreCase = true) == true ||
                            errorMessage.value?.contains("management key", ignoreCase = true) == true ||
                            errorMessage.value?.contains("factory default", ignoreCase = true) == true
            
            if (needsReset) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
              ) {
                Button(
                  onClick = {
                    // SECURITY: Clear encrypted storage
                    DatabaseUtils.ksYubiKeyChallenge.remove()
                    DatabaseUtils.ksYubiKeyManagementKey.remove()
                    
                    m.controller.appPrefs.yubiKeyPinSet.set(false)
                    m.controller.appPrefs.yubiKeyPukSet.set(false)
                    m.controller.appPrefs.yubiKeyManagementKeySet.set(false)
                    // Clear legacy plaintext preferences
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyManagementKey.set(null)
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyPin.set(null)
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyPuk.set(null)
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyChallenge.set(null)
                    m.controller.appPrefs.yubiKeyUid.set(null)
                    m.controller.appPrefs.useYubiKeyForDB.set(false)
                    // SECURITY: Clear PIN from secure container
                    m.secureYubiKeyPin.clear()
                    
                    errorOccurred.value = false
                    errorMessage.value = null
                    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                  },
                  colors = ButtonDefaults.buttonColors(backgroundColor = ElectricBlue500),
                  shape = RoundedCornerShape(360.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(stringResource(MR.strings.yubikey_mgmt_btn_clear_data), color = Color.White)
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                  text = stringResource(MR.strings.yubikey_mgmt_or),
                  fontFamily = DMSans,
                  fontSize = font14,
                  fontWeight = FontWeight.Bold,
                  color = OnSurfaceVariant
                )
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                  onClick = {
                    errorOccurred.value = false
                    errorMessage.value = null
                    isProcessing.value = true
                    
                    scope.launch {
                      val resetResult = chat.simplex.common.platform.YubiKeyBridge.resetToFactoryDefaults()
                      delay(500)
                      isProcessing.value = false
                      
                      if (resetResult.isSuccess) {
                        // SECURITY: Clear encrypted storage
                        DatabaseUtils.ksYubiKeyChallenge.remove()
                        DatabaseUtils.ksYubiKeyManagementKey.remove()
                        
                        m.controller.appPrefs.yubiKeyPinSet.set(false)
                        m.controller.appPrefs.yubiKeyPukSet.set(false)
                        m.controller.appPrefs.yubiKeyManagementKeySet.set(false)
                        // Clear legacy plaintext preferences
                        @Suppress("DEPRECATION")
                        m.controller.appPrefs.yubiKeyManagementKey.set(null)
                        @Suppress("DEPRECATION")
                        m.controller.appPrefs.yubiKeyPin.set(null)
                        @Suppress("DEPRECATION")
                        m.controller.appPrefs.yubiKeyPuk.set(null)
                        @Suppress("DEPRECATION")
                        m.controller.appPrefs.yubiKeyChallenge.set(null)
                        m.controller.appPrefs.yubiKeyUid.set(null)
                        m.controller.appPrefs.useYubiKeyForDB.set(false)
                        // SECURITY: Clear PIN from secure container
                        m.secureYubiKeyPin.clear()
                        
                        showSuccess.value = true
                        delay(1500)
                        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                      } else {
                        errorOccurred.value = true
                        errorMessage.value = "${generalGetString(MR.strings.yubikey_mgmt_reset_failed)}: ${resetResult.exceptionOrNull()?.message}"
                      }
                    }
                  },
                  colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                  shape = RoundedCornerShape(360.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(stringResource(MR.strings.yubikey_mgmt_btn_reset_hardware), color = Color.White)
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                  text = stringResource(MR.strings.yubikey_mgmt_reset_warning),
                  fontFamily = DMSans,
                  fontSize = font12,
                  fontWeight = FontWeight.Normal,
                  color = MaterialTheme.colors.error.copy(alpha = 0.8f),
                  textAlign = TextAlign.Center
                )
              }
            } else {
              Button(
                onClick = {
                  errorOccurred.value = false
                  errorMessage.value = null
                  m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = ElectricBlue500),
                shape = RoundedCornerShape(360.dp)
              ) {
                Text(stringResource(MR.strings.yubikey_mgmt_btn_try_again), color = Color.White)
              }
            }
          }
        }
      }
      
      // Bottom content
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      Spacer(Modifier.navigationBarsPadding())
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
    
    // Success dialog - Shredgram modal style (UIModal.kt exact match)
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
              tint = Color(0xFF11994A),  // Shredgram Green500
              modifier = Modifier.size(24.dp)  // Shredgram: 24dp (natural size of icon_check_green)
            )
            
            Spacer(Modifier.height(8.dp))  // Shredgram: space8DP after icon
            
            // Title - Shredgram: bodyLargeBold (DMSans Bold 16sp)
            Text(
              text = stringResource(MR.strings.yubikey_mgmt_key_generated),
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
  
  // Wait for YubiKey detection and perform real operation
  LaunchedEffect(m.yubiKeyDetected.value) {
    if (m.yubiKeyDetected.value) {
      isProcessing.value = true
      
      val pin = m.secureYubiKeyPin.usePinString { it }
      
      if (pin.isNullOrEmpty()) {
        isProcessing.value = false
        errorOccurred.value = true
        errorMessage.value = generalGetString(MR.strings.yubikey_mgmt_pin_not_found)
        m.yubiKeyDetected.value = false
        return@LaunchedEffect
      }
      
      val result = chat.simplex.common.platform.YubiKeyBridge.setupManagementKey(pin)
      
      if (result.isSuccess) {
        // Show spinner for 1 second before success
        delay(1000)
        isProcessing.value = false
        showSuccess.value = true
        delay(2000) // Show success dialog for 2 seconds
        m.yubiKeyDetected.value = false
        
        // Mark as completed and go back
        m.controller.appPrefs.yubiKeyManagementKeySet.set(true)
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
      } else {
        isProcessing.value = false
        errorOccurred.value = true
        errorMessage.value = result.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_mgmt_unknown_error)
        m.yubiKeyDetected.value = false
      }
    }
  }
}

/**
 * SECURITY: Generate management key using cryptographically secure random
 */
internal fun generateManagementKey(): String {
  val chars = "ABCDEF0123456789"
  val secureRandom = SecureRandom()
  return (1..32)
    .map { chars[secureRandom.nextInt(chars.length)] }
    .joinToString("")
    .chunked(4)
    .joinToString("")
}

@Composable
private fun ShredgramTermsTextManagement() {
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

