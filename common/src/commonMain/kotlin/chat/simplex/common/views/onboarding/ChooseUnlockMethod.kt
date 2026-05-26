package chat.simplex.common.views.onboarding

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

// Shredgram colors - EXACT match from Color.kt
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val BorderElevated1 = Color(0xFFCECFD0)
private val DarkCharcoal400 = Color(0xFF868889)
private val OnSurfaceVariant = Color(0xFF3D4042)  // DarkCharcoal700

// Shredgram Typography tokens - EXACT match from TypographyTokens.kt
private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f
// Font sizes from TypographyTokens.kt
private val font12 = 12.sp
private val font14 = 14.sp
private val font18 = 18.sp
private val font30 = 30.sp

@Composable
fun ChooseUnlockMethod(m: ChatModel) {
  // Back to third feature screen
  BackHandler {
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step0_3_FeatureDecentralized)
  }
  
  val selectedOption = remember { mutableStateOf<UnlockOption?>(null) }
  
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
  ) {
    // Main scrollable content
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // TopBar - Shredgram style: 32dp vertical padding, back button height 24dp
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Back button - height 24dp like Shredgram
        IconButton(
          onClick = {
            m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step0_3_FeatureDecentralized)
          },
          modifier = Modifier.height(24.dp)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = stringResource(MR.strings.yubikey_cd_back),
            tint = MaterialTheme.colors.onBackground
          )
        }
        
        // Center: Logo + "Shredgram" - titleMedium (Manrope Bold 18sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Image(
            painter = painterResource(MR.images.ic_logo),
            contentDescription = stringResource(MR.strings.yubikey_cd_logo)
          )
          
          Spacer(Modifier.width(8.dp))
          
//          Text(
//            text = "Shredgram",
//            fontFamily = Manrope,
//            fontSize = font18,  // titleMedium = Manrope Bold 18sp
//            fontWeight = FontWeight.Bold,
//            color = MaterialTheme.colors.onBackground
//          )
        }
        
        // Spacer for symmetry
        Spacer(Modifier.width(48.dp))
      }
      
      // Lock Icon - Shredgram: 24dp
      Icon(
        painter = painterResource(MR.images.ic_unlock),
        contentDescription = stringResource(MR.strings.yubikey_choose_unlock_cd_unlock),
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = stringResource(MR.strings.yubikey_choose_unlock_title),
        fontFamily = Manrope,
        fontSize = font30,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        lineHeight = (font30.value * lineHeightHeadlineS).sp,
        letterSpacing = (-0.02).em
      )
      
      Spacer(Modifier.height(8.dp))
      
      // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
      Text(
        text = stringResource(MR.strings.yubikey_choose_unlock_description),
        fontFamily = DMSans,
        fontSize = font14,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font14.value * lineHeightBody).sp
      )
      
      Spacer(Modifier.height(32.dp))
      
      // Passphrase Option - Shredgram OptionCard style
      ShredgramOptionCard(
        icon = { 
          Icon(
            painter = painterResource(MR.images.ic_passphrase),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface
          )
        },
        title = stringResource(MR.strings.yubikey_option_passphrase_title),
        description = stringResource(MR.strings.yubikey_option_passphrase_description),
        isSelected = selectedOption.value == UnlockOption.PASSPHRASE,
        onClick = { selectedOption.value = UnlockOption.PASSPHRASE }
      )
      
      Spacer(Modifier.height(16.dp))
      
      // NFC Option - Shredgram OptionCard style
      ShredgramOptionCard(
        icon = {
          Icon(
            painter = painterResource(MR.images.ic_key_lock),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface
          )
        },
        title = stringResource(MR.strings.yubikey_option_nfc_title),
        description = stringResource(MR.strings.yubikey_option_nfc_description),
        isSelected = selectedOption.value == UnlockOption.NFC,
        onClick = { selectedOption.value = UnlockOption.NFC }
      )
    }
    
    // Fixed bottom section - Shredgram bottomContent
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(32.dp))
      
      // PrimaryButton - Shredgram: RadiusPill, padding 16dp, labelLarge (14sp Medium)
      Button(
        onClick = {
          when (selectedOption.value) {
            UnlockOption.PASSPHRASE -> {
              m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_5_SetupDatabasePassphrase)
            }
            UnlockOption.NFC -> {
              m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
            }
            null -> {}
          }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(360.dp),  // RadiusPill
        colors = ButtonDefaults.buttonColors(
          backgroundColor = ElectricBlue500,
          contentColor = Color.White,
          disabledBackgroundColor = BorderElevated1,
          disabledContentColor = DarkCharcoal400
        ),
        enabled = selectedOption.value != null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        elevation = ButtonDefaults.elevation(
          defaultElevation = 0.dp,
          pressedElevation = 0.dp
        )
      ) {
        // labelLarge: DMSans Medium 14sp, lineHeight 1.5
        Text(
          text = stringResource(MR.strings.yubikey_btn_next),
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Medium,
          lineHeight = (font14.value * lineHeightBody).sp
        )
      }
      
      Spacer(Modifier.height(16.dp))
      
      // No footer terms text.
    }
  }
}

/**
 * Shredgram OptionCard - EXACT match from OptionCard.kt
 * - Shape: RadiusLarge (16dp)
 * - tonalElevation: 0dp
 * - shadowElevation: 10dp when selected, 0dp when not
 * - Border: 1dp, BorderBrand (ElectricBlue500) when selected, outlineVariant when not
 * - Padding: 16dp
 * - Spacing: 8dp between icon/text/radio, 4dp between title/desc
 * - Title: titleExtraSmall (Manrope Bold 14sp, lineHeight 1.5)
 * - Description: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
 */
@Composable
fun ShredgramOptionCard(
  icon: @Composable () -> Unit,
  title: String,
  description: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  // Shredgram outlineVariant
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),  // RadiusLarge
    color = MaterialTheme.colors.surface,
    elevation = if (isSelected) 10.dp else 0.dp,  // shadowElevation
    border = BorderStroke(
      width = 1.dp,  // Exact 1dp border
      color = if (isSelected) ElectricBlue500 else outlineVariant  // BorderBrand when selected
    )
  ) {
    Row(
      modifier = Modifier
        .padding(16.dp)  // 16dp padding
        .fillMaxWidth(),
      verticalAlignment = Alignment.Top
    ) {
      // Icon
      icon()
      
      Spacer(Modifier.width(8.dp))  // 8dp spacing
      
      // Title and Description - Shredgram typography
      // Both texts in same Column = both start at same horizontal position
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.Start  // Explicitly align start
      ) {
        // titleExtraSmall: Manrope Bold 14sp, lineHeight 1.5
        Text(
          text = title,
          fontFamily = Manrope,
          fontSize = font14,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          lineHeight = (font14.value * lineHeightBody).sp,
          textAlign = TextAlign.Start
        )
        
        Spacer(Modifier.height(4.dp))  // 4dp spacing
        
        // bodySmall: DMSans Normal 12sp, lineHeight 1.5, onSurfaceVariant
        // Aligned with title - starts at same position
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
      
      Spacer(Modifier.width(8.dp))  // 8dp spacing
      
      // Radio button - Shredgram style
      ShredgramRadioButton(
        selected = isSelected,
        onClick = onClick
      )
    }
  }
}

/**
 * Shredgram UIRadioButton - EXACT match from UIRadioButton.kt
 * - Size: 16dp
 * - Shape: RadiusCircle (RoundedCornerShape(50)) with .clip() modifier
 * - Border: 1dp, outlineVariant (always same color, doesn't change when selected)
 * - Check icon: 10dp, BorderBrand (ElectricBlue500) tint
 */
@Composable
fun ShredgramRadioButton(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Shredgram: outlineVariant - always same border color
  val borderColor = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  
  Surface(
    modifier = modifier
      .size(16.dp)
      .clip(RoundedCornerShape(50))  // RadiusCircle - clip before clickable
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(50),  // RadiusCircle
    color = MaterialTheme.colors.surface,
    border = BorderStroke(1.dp, borderColor)  // 1dp border, outlineVariant
  ) {
    if (selected) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painter = painterResource(MR.images.ic_check),
          contentDescription = null,
          tint = ElectricBlue500,  // BorderBrand
          modifier = Modifier.size(10.dp)  // 10dp check icon
        )
      }
    }
  }
}

// Keep old one for backwards compatibility
@Composable
fun UnlockOptionCard(
  icon: androidx.compose.ui.graphics.painter.Painter,
  title: String,
  description: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  ShredgramOptionCard(
    icon = { Icon(painter = icon, contentDescription = title, tint = MaterialTheme.colors.onSurface) },
    title = title,
    description = description,
    isSelected = isSelected,
    onClick = onClick
  )
}

enum class UnlockOption {
  PASSPHRASE,
  NFC
}

