package chat.simplex.common.views.onboarding

import androidx.compose.animation.*
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

enum class PINSetupStep {
  ENTER_PIN,       // Enter new PIN
  CONFIRM_PIN,     // Confirm the PIN
  VALIDATE_WITH_KEY // Tap YubiKey to validate
}

@Composable
fun SetupYubiKeyPIN(m: ChatModel) {
  val currentStep = remember { mutableStateOf(PINSetupStep.ENTER_PIN) }
  val enteredPin = remember { mutableStateOf("") }
  val confirmPin = remember { mutableStateOf("") }
  val isProcessing = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  
  // Use PasscodeView for PIN entry - exactly same as passcode screen
  val pinState = remember { mutableStateOf("") }
  
  // Handle system back button based on current step
  BackHandler {
    when (currentStep.value) {
      PINSetupStep.CONFIRM_PIN -> {
        // Go back to Enter PIN step
        pinState.value = ""
        currentStep.value = PINSetupStep.ENTER_PIN
      }
      else -> {
        // Go back to YubiKey setup screen
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
      }
    }
  }
  
  // For Enter and Confirm steps, use a single PasscodeView with dynamic title/reason
  // This allows the internal animation to work correctly
  if (currentStep.value == PINSetupStep.ENTER_PIN || currentStep.value == PINSetupStep.CONFIRM_PIN) {
    val isConfirmStep = currentStep.value == PINSetupStep.CONFIRM_PIN
    
    PasscodeView(
      passcode = pinState,
      title = if (isConfirmStep) stringResource(MR.strings.yubikey_pin_confirm_title) else stringResource(MR.strings.yubikey_pin_setup_title),
      reason = stringResource(MR.strings.yubikey_pin_reason),
      submitLabel = if (isConfirmStep) stringResource(MR.strings.yubikey_pin_btn_confirm) else stringResource(MR.strings.yubikey_btn_next),
      submitEnabled = { pin ->
        if (isConfirmStep) {
          pin == enteredPin.value
        } else {
          pin.length in 6..8
        }
      },
      submit = {
        if (isConfirmStep) {
          if (pinState.value == enteredPin.value) {
            confirmPin.value = pinState.value
            pinState.value = ""
            currentStep.value = PINSetupStep.VALIDATE_WITH_KEY
          }
        } else {
          enteredPin.value = pinState.value
          pinState.value = ""
          currentStep.value = PINSetupStep.CONFIRM_PIN
        }
      },
      cancel = {
        if (isConfirmStep) {
          // Go back to Enter PIN step
          pinState.value = ""
          currentStep.value = PINSetupStep.ENTER_PIN
        } else {
          // Go back to YubiKey setup screen
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
        }
      }
    )
  } else if (currentStep.value == PINSetupStep.VALIDATE_WITH_KEY) {
      ValidateWithYubiKeyScreen(
        m = m,
        title = stringResource(MR.strings.yubikey_pin_store_title),
        description = stringResource(MR.strings.yubikey_pin_reason),
        isProcessing = isProcessing.value,
        showSuccess = showSuccess.value,
        successMessage = stringResource(MR.strings.yubikey_pin_created),
        onSuccess = {
          // Go back to main YubiKey setup screen
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
        },
        onBack = {
          // Go back to ENTER_PIN step so user can enter a new PIN
          enteredPin.value = ""
          confirmPin.value = ""
          pinState.value = ""
          currentStep.value = PINSetupStep.ENTER_PIN
        },
        onProcessingChange = { isProcessing.value = it },
        onShowSuccessChange = { showSuccess.value = it },
        pin = enteredPin.value
      )
  }
}

@Composable
fun ValidateWithYubiKeyScreen(
  m: ChatModel,
  title: String,
  description: String,
  isProcessing: Boolean,
  showSuccess: Boolean,
  successMessage: String,
  onSuccess: () -> Unit,
  onBack: () -> Unit,
  onProcessingChange: (Boolean) -> Unit,
  onShowSuccessChange: (Boolean) -> Unit,
  pin: String
) {
  val errorOccurred = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf<String?>(null) }
  val showResetOption = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  
  // Reset stale YubiKey detection when screen first loads
  LaunchedEffect(Unit) {
    m.yubiKeyDetected.value = false
  }
  
  // Wait for YubiKey detection and perform real operation
  LaunchedEffect(m.yubiKeyDetected.value) {
    if (m.yubiKeyDetected.value) {
      // YubiKey detected, check if it's in factory default state first
      onProcessingChange(true)
      
      // Check factory default state
      val factoryCheck = chat.simplex.common.platform.YubiKeyBridge.isFactoryDefault()
      
      if (factoryCheck.isFailure) {
        // Connection lost or other error - prompt user to tap again
        onProcessingChange(false)
        val error = factoryCheck.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_pin_connection_lost)
        // Don't show as error for connection issues - just reset and let user tap again
        if (error.contains("connection", ignoreCase = true) || 
            error.contains("tap", ignoreCase = true) ||
            error.contains("lost", ignoreCase = true)) {
          // Silent reset - user needs to tap again
          m.yubiKeyDetected.value = false
        } else {
          errorOccurred.value = true
          errorMessage.value = error
          m.yubiKeyDetected.value = false
        }
        return@LaunchedEffect
      }
      
      if (factoryCheck.getOrNull() == false) {
        // YubiKey is not in factory default state
        onProcessingChange(false)
        errorOccurred.value = true
        errorMessage.value = generalGetString(MR.strings.yubikey_pin_already_configured)
        showResetOption.value = true
        m.yubiKeyDetected.value = false
        return@LaunchedEffect
      }
      
      // Call real YubiKey PIN setup
      val result = chat.simplex.common.platform.YubiKeyBridge.setupPIN(pin)
      
      if (result.isSuccess) {
        // SECURITY: Store the actual PIN in secure container for subsequent setup steps
        m.secureYubiKeyPin.set(pin)
        
        // Show spinner for 1 second before success
        delay(1000)
        onProcessingChange(false)
        onShowSuccessChange(true)
        delay(2000) // Show success dialog for 2 seconds (Shredgram: autoDismissMillis = 2000)
        m.yubiKeyDetected.value = false // Reset for next operation
        onSuccess()
      } else {
        onProcessingChange(false)
        errorOccurred.value = true
        val error = result.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_pin_unknown_error)
        errorMessage.value = error
        
        // Show reset option if it looks like a configuration issue
        if (error.contains("configured", ignoreCase = true) || 
            error.contains("invalid", ignoreCase = true) ||
            error.contains("blocked", ignoreCase = true)) {
          showResetOption.value = true
        }
        
        m.yubiKeyDetected.value = false
      }
    }
  }
  
  // Track if showing modal (for blur effect)
  val showingModal = showSuccess || isProcessing
  
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
      // TopBar - Shredgram style with back button and logo
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.height(24.dp)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = stringResource(MR.strings.back),
            tint = MaterialTheme.colors.onBackground
          )
        }
        
        // Center: Text logo
        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = stringResource(MR.strings.yubikey_cd_logo)
        )
        
        // Spacer for symmetry
        Spacer(Modifier.width(48.dp))
      }
      
      // Lock icon - Shredgram: space24DP
      Icon(
        painter = painterResource(MR.images.ic_lock_new),
        contentDescription = stringResource(MR.strings.yubikey_pin_cd_lock),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = title,
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
        text = description,
        fontFamily = DMSans,
        fontSize = font14,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font14.value * lineHeightBody).sp
      )
      
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
          } else if (showSuccess) {
            Icon(
              painter = painterResource(MR.images.ic_check_circle_filled),
              contentDescription = stringResource(MR.strings.yubikey_cd_success),
              tint = Color(0xFF11994A),  // Shredgram Green500
              modifier = Modifier.size(92.dp)
            )
          } else if (isProcessing) {
            ShredgramInlineSpinner(
              modifier = Modifier,
              size = 48.dp
            )
          } else {
            // YubiKey icon - Shredgram: 92dp, onSurfaceVariant
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
              errorOccurred.value -> errorMessage.value ?: stringResource(MR.strings.yubikey_pin_setup_failed)
              showSuccess -> successMessage
              isProcessing -> stringResource(MR.strings.yubikey_pin_configuring)
              else -> stringResource(MR.strings.yubikey_pin_tap_to_set)
            },
            fontFamily = DMSans,
            fontSize = font14,
            fontWeight = FontWeight.Normal,
            color = when {
              errorOccurred.value -> MaterialTheme.colors.error
              showSuccess -> Color(0xFF11994A)
              else -> OnSurfaceVariant
            },
            textAlign = TextAlign.Center
          )
          
          if (errorOccurred.value) {
            Spacer(Modifier.height(16.dp))
            
            if (showResetOption.value) {
              Button(
                onClick = {
                  errorOccurred.value = false
                  errorMessage.value = null
                  showResetOption.value = false
                  onProcessingChange(true)
                  
                  scope.launch {
                    val resetResult = chat.simplex.common.platform.YubiKeyBridge.resetToFactoryDefaults()
                    delay(500)
                    onProcessingChange(false)
                    
                    if (resetResult.isSuccess) {
                      errorMessage.value = generalGetString(MR.strings.yubikey_pin_reset_successful)
                    } else {
                      errorOccurred.value = true
                      errorMessage.value = "${generalGetString(MR.strings.yubikey_pin_reset_failed)}: ${resetResult.exceptionOrNull()?.message}"
                    }
                  }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                shape = RoundedCornerShape(360.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
              ) {
                Text(stringResource(MR.strings.yubikey_pin_btn_reset), color = Color.White)
              }
              
              Spacer(Modifier.height(8.dp))
            }
            
            Button(
              onClick = {
                errorOccurred.value = false
                errorMessage.value = null
                showResetOption.value = false
                onBack()
              },
              colors = ButtonDefaults.buttonColors(backgroundColor = ElectricBlue500),
              shape = RoundedCornerShape(360.dp)
            ) {
              Text(stringResource(MR.strings.yubikey_pin_btn_try_again), color = Color.White)
            }
          }
        }
      }
      
      // Bottom content
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      Spacer(Modifier.navigationBarsPadding())
    }
    
    // Processing spinner overlay - Shredgram style
    if (isProcessing) {
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
    if (showSuccess) {
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
              text = successMessage,
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
}

@Composable
private fun ShredgramTermsText() {
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

