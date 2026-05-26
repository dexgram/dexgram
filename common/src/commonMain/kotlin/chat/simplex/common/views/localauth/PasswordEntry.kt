package chat.simplex.common.views.localauth

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import dev.icerock.moko.resources.compose.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.res.MR
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Shredgram colors
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val BorderElevated1 = Color(0xFFCECFD0)
private val DarkCharcoal400 = Color(0xFF868889)
private val OutlineVariant = Color(0xFFCACACA)

@Composable
fun PasscodeEntry(
  password: MutableState<String>,
  vertical: Boolean,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    PasscodeDotsDisplay(password = password, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(60.dp))
    PasscodeKeypad(password, {}, false)
  }
}

/**
 * Shredgram-style PinIndicatorRow - EXACT match from PinIndicatorRow.kt
 * - Slot width: calculated dynamically from available width
 * - Slot height: 48dp
 * - Dot size: 16dp
 * - Spacing: 8dp between slots
 * - Empty dot: outlineVariant with 0.45 alpha
 * - Shows last digit briefly when typing (controlled by isIdle)
 * - Last digit uses headlineSmall style (32sp Bold)
 */
@Composable
fun PasscodeDotsDisplay(
  password: MutableState<String>,
  totalSlots: Int = 8,
  isIdle: Boolean = true,  // When false, shows last digit as text
  modifier: Modifier = Modifier
) {
  val filledColor = MaterialTheme.colors.onSurface
  val emptyDotColor = OutlineVariant.copy(alpha = 0.45f)  // Shredgram: outlineVariant with 0.45 alpha
  
  // Shredgram dimensions
  val dotSize = 16.dp
  val spacing = 8.dp
  val slotHeight = 48.dp
  
  // Idle state management with timeout
  var currentIsIdle by remember { mutableStateOf(true) }
  val previousLength = remember { mutableStateOf(0) }
  
  LaunchedEffect(password.value.length) {
    if (password.value.length > previousLength.value && password.value.isNotEmpty()) {
      currentIsIdle = false
      delay(500)  // Show digit for 500ms then become idle
      currentIsIdle = true
    }
    previousLength.value = password.value.length
  }

  // Use BoxWithConstraints to calculate dynamic slot width like Shredgram
  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    val availableWidth = maxWidth
    val totalSpacing = spacing * (totalSlots - 1)
    val slotWidth = (availableWidth - totalSpacing) / totalSlots
    
    Row(
      horizontalArrangement = Arrangement.spacedBy(spacing),
      verticalAlignment = Alignment.CenterVertically
    ) {
      repeat(totalSlots) { index ->
        val isFilled = index < password.value.length
        val isLastFilled = index == password.value.length - 1
        val showLastDigit = isFilled && isLastFilled && !currentIsIdle
        
        Box(
          modifier = Modifier
            .width(slotWidth)
            .height(slotHeight),
          contentAlignment = Alignment.Center
        ) {
          when {
            // Empty slot - show gray dot
            !isFilled -> {
              Box(
                modifier = Modifier
                  .size(dotSize)
                  .clip(CircleShape)
                  .background(emptyDotColor)
              )
            }
            // Last filled and not idle - show digit
            showLastDigit -> {
              Text(
                text = password.value.last().toString(),
                style = TextStyle(
                  fontFamily = Manrope,
                  fontWeight = FontWeight.Bold,
                  fontSize = 32.sp  // headlineSmall
                ),
                color = filledColor,
                maxLines = 1
              )
            }
            // Filled slot - show filled dot
            else -> {
              Box(
                modifier = Modifier
                  .size(dotSize)
                  .clip(CircleShape)
                  .background(filledColor)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Shredgram-style PinKeypad - EXACT match from PinKeypad.kt
 * - Row spacing: 28dp
 * - Digit: 56dp circular clickable area with ripple effect
 * - Digits use headlineMedium (Manrope Bold 34sp)
 * - Submit button: 56dp circular PrimaryCircleIconButton
 * - Backspace: IconButton with icon_backspace
 */
@Composable
fun PasscodeKeypad(
  passcode: MutableState<String>,
  onSubmit: () -> Unit,
  submitEnabled: Boolean
) {
  val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
  
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(28.dp)  // Shredgram: space28DP
  ) {
    // Number rows 1-9
    rows.forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        row.forEach { digit ->
          ShredgramPinDigit(
            digit = digit.toString(),
            onClick = {
              if (passcode.value.length < 8) {
                passcode.value += digit.toString()
              }
            },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
    
    // Bottom row: Backspace, 0, Submit
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Backspace - Shredgram uses IconButton
      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
      ) {
        IconButton(
          onClick = {
            if (passcode.value.isNotEmpty()) {
              passcode.value = passcode.value.dropLast(1)
            }
          }
        ) {
          Icon(
            painterResource(MR.images.ic_backspace),
            contentDescription = "Backspace",
            tint = MaterialTheme.colors.onSurface,
            modifier = Modifier.size(32.dp)  // Shredgram: bold backspace icon
          )
        }
      }
      
      // Zero
      ShredgramPinDigit(
        digit = "0",
        onClick = {
          if (passcode.value.length < 8) {
            passcode.value += "0"
          }
        },
        modifier = Modifier.weight(1f)
      )
      
      // Submit button - Shredgram PrimaryCircleIconButton style
      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Surface(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape),
          shape = CircleShape,
          color = if (submitEnabled) ElectricBlue500 else OutlineVariant,
          onClick = { if (submitEnabled) onSubmit() },
          enabled = submitEnabled
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painterResource(MR.images.ic_arrow_forward),
              contentDescription = "Submit",
              modifier = Modifier.size(24.dp),
              tint = if (submitEnabled) Color.White else DarkCharcoal400
            )
          }
        }
      }
    }
  }
}

/**
 * Shredgram-style PinDigit - EXACT match from PinKeypad.kt
 * - Height: 56dp
 * - Inner Box: 56dp × 56dp with CircleShape clip for circular ripple
 * - Text: headlineMedium (Manrope Bold 34sp)
 */
@Composable
private fun ShredgramPinDigit(
  digit: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier.height(56.dp),  // Shredgram: space56DP height
    contentAlignment = Alignment.Center
  ) {
    // Inner circular clickable area for ripple effect
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = digit,
        style = TextStyle(
          fontFamily = Manrope,  // Shredgram: headlineMedium uses Manrope
          fontWeight = FontWeight.Bold,
          fontSize = 34.sp  // headlineMedium = 34sp
        ),
        color = MaterialTheme.colors.onSurface
      )
    }
  }
}

// Keep old one for backwards compatibility
@Composable
private fun PinDigit(
  digit: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  ShredgramPinDigit(digit, onClick, modifier)
}

@Composable
fun VerticalDivider(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colors.onSurface.copy(alpha = DividerAlpha),
  thickness: Dp = 1.dp,
  startIndent: Dp = 0.dp
) {
  val indentMod = if (startIndent.value != 0f) {
    Modifier.padding(top = startIndent)
  } else {
    Modifier
  }
  val targetThickness = if (thickness == Dp.Hairline) {
    (1f / LocalDensity.current.density).dp
  } else {
    thickness
  }
  Box(
    modifier.then(indentMod)
      .fillMaxHeight()
      .width(targetThickness)
      .background(color = color)
  )
}

private const val DividerAlpha = 0.12f
