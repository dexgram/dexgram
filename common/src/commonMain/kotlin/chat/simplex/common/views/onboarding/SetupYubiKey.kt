package chat.simplex.common.views.onboarding

import SectionBottomSpacer
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

// Shredgram Typography tokens - EXACT match from TypographyTokens.kt
private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f

// Font sizes from TypographyTokens.kt
private val font12 = 12.sp
private val font14 = 14.sp
private val font16 = 16.sp
private val font18 = 18.sp
private val font30 = 30.sp

// Shredgram Colors
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val Green500 = Color(0xFF11994A)
private val OnSurfaceVariant = Color(0xFF3D4042)

@Composable
fun SetupYubiKey(m: ChatModel) {
  // Back to choose unlock method
  BackHandler {
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_ChooseUnlockMethod)
  }
  
  // Track completion status of each step
  val pinCompleted = remember { mutableStateOf(m.controller.appPrefs.yubiKeyPinSet.get()) }
  val pukCompleted = remember { mutableStateOf(m.controller.appPrefs.yubiKeyPukSet.get()) }
  val managementKeyCompleted = remember { mutableStateOf(m.controller.appPrefs.yubiKeyManagementKeySet.get()) }
  
  // Track selected step (null if none selected or completed)
  val selectedStep = remember { mutableStateOf<String?>(null) }
  
  val allStepsCompleted by remember {
    derivedStateOf {
      pinCompleted.value && pukCompleted.value && managementKeyCompleted.value
    }
  }
  
  // Listen for updates from other screens
  LaunchedEffect(Unit) {
    while (true) {
      delay(100)
      pinCompleted.value = m.controller.appPrefs.yubiKeyPinSet.get()
      pukCompleted.value = m.controller.appPrefs.yubiKeyPukSet.get()
      managementKeyCompleted.value = m.controller.appPrefs.yubiKeyManagementKeySet.get()
    }
  }
  
  // Border color for outlineVariant
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()  // Add status bar padding
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),  // Shredgram: space24DP
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
        // Back button
        IconButton(
          onClick = {
            m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_ChooseUnlockMethod)
          },
          modifier = Modifier.height(24.dp)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = stringResource(MR.strings.yubikey_cd_back),
            tint = MaterialTheme.colors.onBackground
          )
        }
        
        // Center: Logo + "Shredgram" text - titleMedium (Manrope Bold 18sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Image(
            painter = painterResource(MR.images.ic_logo),
            contentDescription = stringResource(MR.strings.yubikey_cd_logo)
          )
          
          Spacer(Modifier.width(8.dp))
          
//          Text(
//            text = "Shredgram",
//            fontFamily = Manrope,
//            fontSize = font18,
//            fontWeight = FontWeight.Bold,
//            color = MaterialTheme.colors.onBackground,
//            lineHeight = (font18.value * 1.4f).sp
//          )
        }
        
        // Spacer for symmetry
        Spacer(Modifier.width(48.dp))
      }
      
      // Key Lock Icon - 24dp like Shredgram
      Icon(
        painterResource(MR.images.ic_key_lock),
        contentDescription = null,
        modifier = Modifier.size(24.dp),  // Shredgram: space24DP
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = stringResource(MR.strings.yubikey_setup_title),
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
        text = stringResource(MR.strings.yubikey_setup_description),
        fontFamily = DMSans,
        fontSize = font14,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font14.value * lineHeightBody).sp
      )
      
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // Three setup cards - ItemCard style from Shredgram
      
      // 1. Set up PIN card
      PasskeyItemCard(
        title = stringResource(MR.strings.yubikey_setup_pin_card_title),
        description = stringResource(MR.strings.yubikey_setup_pin_card_description),
        selected = selectedStep.value == "PIN" && !pinCompleted.value,
        completed = pinCompleted.value,
        enabled = true,
        onClick = { 
          if (!pinCompleted.value) {
            selectedStep.value = "PIN"
          }
        },
        onNavigate = {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_1_SetupYubiKeyPIN)
        },
        icon = {
          Icon(
            painter = painterResource(MR.images.ic_lock_new),
            contentDescription = null,
            tint = if (pinCompleted.value) Green500 else MaterialTheme.colors.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // 2. Set up PUK card
      PasskeyItemCard(
        title = stringResource(MR.strings.yubikey_setup_puk_card_title),
        description = stringResource(MR.strings.yubikey_setup_puk_card_description),
        selected = selectedStep.value == "PUK" && !pukCompleted.value,
        completed = pukCompleted.value,
        enabled = true,
        onClick = { 
          if (!pukCompleted.value) {
            selectedStep.value = "PUK"
          }
        },
        onNavigate = {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_2_SetupYubiKeyPUK)
        },
        icon = {
          Icon(
            painter = painterResource(MR.images.ic_puk),
            contentDescription = null,
            tint = if (pukCompleted.value) Green500 else MaterialTheme.colors.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }
      )
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      // 3. Management key card
      PasskeyItemCard(
        title = stringResource(MR.strings.yubikey_setup_mgmt_card_title),
        description = stringResource(MR.strings.yubikey_setup_mgmt_card_description),
        selected = selectedStep.value == "MANAGEMENT_KEY" && !managementKeyCompleted.value,
        completed = managementKeyCompleted.value,
        enabled = true,
        onClick = { 
          if (!managementKeyCompleted.value) {
            selectedStep.value = "MANAGEMENT_KEY"
          }
        },
        onNavigate = {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_3_SetupYubiKeyManagementKey)
        },
        icon = {
          Icon(
            painter = painterResource(MR.images.ic_manage),
            contentDescription = null,
            tint = if (managementKeyCompleted.value) Green500 else MaterialTheme.colors.onSurface,
            modifier = Modifier.size(24.dp)
          )
        }
      )
      
      Spacer(Modifier.height(24.dp))  // Shredgram: space24DP
      
      // Reset YubiKey option - Shredgram TertiaryButton style
      TextButton(
        onClick = {
          m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_0_YubiKeyFactoryReset)
        },
        contentPadding = PaddingValues(0.dp)
      ) {
        Text(
          text = stringResource(MR.strings.yubikey_reset_to_factory_link),
          fontFamily = DMSans,
          fontSize = font12,
          fontWeight = FontWeight.Normal,
          color = ElectricBlue500,
          lineHeight = (font12.value * lineHeightBody).sp
        )
      }
      
      Spacer(Modifier.weight(1f))
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // Next Button - Shredgram PrimaryButton style
      Button(
        onClick = {
          if (allStepsCompleted) {
            // Proceed to database encryption
            m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_4_EncryptDatabaseYubiKey)
          } else {
            // Navigate to selected step
            when (selectedStep.value) {
              "PIN" -> m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_1_SetupYubiKeyPIN)
              "PUK" -> m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_2_SetupYubiKeyPUK)
              "MANAGEMENT_KEY" -> m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_3_SetupYubiKeyManagementKey)
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(360.dp),  // RadiusPill
        colors = ButtonDefaults.buttonColors(
          backgroundColor = ElectricBlue500,
          contentColor = Color.White,
          disabledBackgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
          disabledContentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
        ),
        enabled = allStepsCompleted || selectedStep.value != null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
      ) {
        // Shredgram: labelLarge (DMSans Medium 14sp, lineHeight 1.5)
        Text(
          text = stringResource(MR.strings.yubikey_btn_next),
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Medium,
          lineHeight = (font14.value * lineHeightBody).sp
        )
      }
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
      
      Spacer(Modifier.navigationBarsPadding())
    }
  }
}

/**
 * Shredgram Terms and Privacy Text - matches TermsAndPrivacyText.kt exactly
 */
@Composable
private fun ShredgramTermsAndPrivacyText() {
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
        modifier = Modifier.clickable {
          // TODO: Open Terms of Service
        }
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
        modifier = Modifier.clickable {
          // TODO: Open Privacy Policy
        }
      )
    }
  }
}

/**
 * Shredgram ItemCard - EXACT match from ItemCard.kt
 * 
 * Design specs:
 * - Shape: RadiusLarge (16dp)
 * - shadowElevation: 10dp when selected, 0dp otherwise
 * - Border: 1dp, Green500 when completed, BorderBrand (ElectricBlue500) when selected, outlineVariant otherwise
 * - Background: Green500.copy(alpha = 0.15f) when completed, surface otherwise
 * - Padding: 16dp
 * - Icon box: 24dp with 4dp top padding
 * - Title row: title + arrow_right/check icon
 * - Title: titleExtraSmall (Manrope Bold 14sp, lineHeight 1.5)
 * - Description: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
 * - Spacing: 8dp after icon, 4dp between title and description
 */
@Composable
fun PasskeyItemCard(
  title: String,
  description: String,
  selected: Boolean,
  completed: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  onNavigate: () -> Unit,
  icon: @Composable () -> Unit
) {
  val backgroundColor = when {
    completed -> Green500.copy(alpha = 0.15f)
    else -> MaterialTheme.colors.surface
  }
  
  val borderColor = when {
    completed -> Green500
    selected -> ElectricBlue500
    else -> MaterialTheme.colors.onBackground.copy(alpha = 0.15f)  // outlineVariant
  }
  
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(enabled = enabled && !completed) { 
        onClick()
        // Auto-navigate when clicking an incomplete step
        if (!completed) {
          onNavigate()
        }
      },
    shape = RoundedCornerShape(16.dp),  // RadiusLarge
    color = backgroundColor,
    elevation = if (selected) 10.dp else 0.dp,  // shadowElevation
    border = BorderStroke(1.dp, borderColor)
  ) {
    Row(
      modifier = Modifier
        .padding(16.dp)  // Shredgram: space16DP
        .fillMaxWidth(),
      verticalAlignment = Alignment.Top
    ) {
      // Icon box with 4dp top padding
      Box(
        modifier = Modifier
          .size(24.dp)  // Shredgram: space24DP
          .padding(top = 4.dp)  // Shredgram: space4DP
      ) {
        icon()
      }
      
      Spacer(Modifier.width(8.dp))  // Shredgram: space8DP
      
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.Start
      ) {
        // Title row with arrow/check icon
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Title - Shredgram: titleExtraSmall (Manrope Bold 14sp, lineHeight 1.5)
          Text(
            text = title,
            fontFamily = Manrope,
            fontSize = font14,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colors.onSurface else OnSurfaceVariant,
            lineHeight = (font14.value * lineHeightBody).sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
          )
          
          // Arrow or check icon
          Icon(
            painter = painterResource(
              if (completed) MR.images.ic_check else MR.images.ic_chevron_right
            ),
            contentDescription = null,
            tint = if (completed) Green500 else if (enabled) MaterialTheme.colors.onSurface else OnSurfaceVariant,
            modifier = Modifier.size(16.dp)  // Shredgram: space16DP
          )
        }
        
        Spacer(Modifier.height(4.dp))  // Shredgram: space4DP
        
        // Description - Shredgram: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
        Text(
          text = description,
          fontFamily = DMSans,
          fontSize = font12,
          fontWeight = FontWeight.Normal,
          color = OnSurfaceVariant,
          lineHeight = (font12.value * lineHeightBody).sp,
          textAlign = TextAlign.Start
        )
      }
    }
  }
}
