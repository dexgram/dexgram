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
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.widthIn

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

enum class PUKSetupStep {
  ENTER_PUK,       // Enter new PUK
  CONFIRM_PUK,     // Confirm the PUK
  VALIDATE_WITH_KEY // Tap YubiKey to validate
}

@Composable
fun SetupYubiKeyPUK(m: ChatModel) {
  val currentStep = remember { mutableStateOf(PUKSetupStep.ENTER_PUK) }
  val enteredPuk = remember { mutableStateOf("") }
  val confirmPuk = remember { mutableStateOf("") }
  val isProcessing = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  
  // Use PasscodeView for PUK entry - exactly same as passcode screen
  val pukState = remember { mutableStateOf("") }
  
  // Handle system back button based on current step
  BackHandler {
    when (currentStep.value) {
      PUKSetupStep.CONFIRM_PUK -> {
        // Go back to Enter PUK step
        pukState.value = ""
        currentStep.value = PUKSetupStep.ENTER_PUK
      }
      else -> {
        // Go back to YubiKey setup screen
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
      }
    }
  }
  
  // For Enter and Confirm steps, use a single PasscodeView with dynamic title/reason
  // This allows the internal animation to work correctly
  if (currentStep.value == PUKSetupStep.ENTER_PUK || currentStep.value == PUKSetupStep.CONFIRM_PUK) {
    val isConfirmStep = currentStep.value == PUKSetupStep.CONFIRM_PUK
    
    PasscodeView(
      passcode = pukState,
      title = if (isConfirmStep) stringResource(MR.strings.yubikey_puk_confirm_title) else stringResource(MR.strings.yubikey_puk_setup_title),
      reason = stringResource(MR.strings.yubikey_puk_reason),
      iconResource = MR.images.ic_puk,
      submitLabel = if (isConfirmStep) stringResource(MR.strings.yubikey_puk_btn_confirm) else stringResource(MR.strings.yubikey_btn_next),
      submitEnabled = { puk ->
        if (isConfirmStep) {
          puk == enteredPuk.value
        } else {
          puk.length in 6..8
        }
      },
      submit = {
        if (isConfirmStep) {
          if (pukState.value == enteredPuk.value) {
            confirmPuk.value = pukState.value
            pukState.value = ""
            currentStep.value = PUKSetupStep.VALIDATE_WITH_KEY
          }
        } else {
          enteredPuk.value = pukState.value
          pukState.value = ""
          currentStep.value = PUKSetupStep.CONFIRM_PUK
        }
      },
      cancel = {
        if (isConfirmStep) {
          // Go back to Enter PUK step
          pukState.value = ""
          currentStep.value = PUKSetupStep.ENTER_PUK
        } else {
          // Go back to YubiKey setup screen
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
        }
      }
    )
  } else if (currentStep.value == PUKSetupStep.VALIDATE_WITH_KEY) {
      ValidateWithYubiKeyPUKScreen(
        m = m,
        puk = enteredPuk.value,
        isProcessing = isProcessing.value,
        showSuccess = showSuccess.value,
        onSuccess = {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
        },
        onBack = {
          // Go back to ENTER_PUK step so user can enter a new PUK
          enteredPuk.value = ""
          confirmPuk.value = ""
          pukState.value = ""
          currentStep.value = PUKSetupStep.ENTER_PUK
        },
        onProcessingChange = { isProcessing.value = it },
        onShowSuccessChange = { showSuccess.value = it }
      )
  }
}

@Composable
fun ValidateWithYubiKeyPUKScreen(
  m: ChatModel,
  puk: String,
  isProcessing: Boolean,
  showSuccess: Boolean,
  onSuccess: () -> Unit,
  onBack: () -> Unit,
  onProcessingChange: (Boolean) -> Unit,
  onShowSuccessChange: (Boolean) -> Unit
) {
  val errorOccurred = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf<String?>(null) }
  
  // Reset stale YubiKey detection when screen first loads
  LaunchedEffect(Unit) {
    m.yubiKeyDetected.value = false
  }
  
  // Wait for YubiKey detection and perform real operation
  LaunchedEffect(m.yubiKeyDetected.value) {
    if (m.yubiKeyDetected.value) {
      onProcessingChange(true)
      
      val result = chat.simplex.common.platform.YubiKeyBridge.setupPUK(puk)
      
      if (result.isSuccess) {
        // Show spinner for 1 second before success
        delay(1000)
        onProcessingChange(false)
        onShowSuccessChange(true)
        delay(2000) // Show success dialog for 2 seconds
        m.yubiKeyDetected.value = false
        onSuccess()
      } else {
        onProcessingChange(false)
        errorOccurred.value = true
        errorMessage.value = result.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_puk_unknown_error)
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
      
      // PUK icon - Shredgram: space24DP
      Icon(
        painter = painterResource(MR.images.ic_puk),
        contentDescription = stringResource(MR.strings.yubikey_puk_cd_puk),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = stringResource(MR.strings.yubikey_puk_store_title),
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
        text = stringResource(MR.strings.yubikey_puk_reason),
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
              tint = Green500,
              modifier = Modifier.size(92.dp)
            )
          } else if (isProcessing) {
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
              errorOccurred.value -> errorMessage.value ?: stringResource(MR.strings.yubikey_puk_setup_failed)
              showSuccess -> stringResource(MR.strings.yubikey_puk_created)
              isProcessing -> stringResource(MR.strings.yubikey_puk_configuring)
              else -> stringResource(MR.strings.yubikey_puk_tap_to_set)
            },
            fontFamily = DMSans,
            fontSize = font14,
            fontWeight = FontWeight.Normal,
            color = when {
              errorOccurred.value -> MaterialTheme.colors.error
              showSuccess -> Green500
              else -> OnSurfaceVariant
            },
            textAlign = TextAlign.Center
          )
          
          if (errorOccurred.value) {
            Spacer(Modifier.height(16.dp))
            
            Button(
              onClick = {
                errorOccurred.value = false
                errorMessage.value = null
                onBack()
              },
              colors = ButtonDefaults.buttonColors(backgroundColor = ElectricBlue500),
              shape = RoundedCornerShape(360.dp)
            ) {
              Text(stringResource(MR.strings.yubikey_puk_btn_try_again), color = Color.White)
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
              text = stringResource(MR.strings.yubikey_puk_created),
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
private fun ShredgramTermsTextPUK() {
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

