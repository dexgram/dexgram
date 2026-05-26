package chat.simplex.common.views.localauth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.shredgram.theme.ElectricBlue500
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.ui.theme.WarningOrange
import chat.simplex.common.views.helpers.DatabaseUtils
import chat.simplex.common.views.helpers.DatabaseUtils.ksAppPassword
import chat.simplex.common.views.helpers.DatabaseUtils.ksSelfDestructPassword
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource

// Green color for success checkmark matching local project (#11994A)
private val SuccessGreen = Color(0xFF11994A)

@Composable
fun SetAppPasscodeView(
  passcodeKeychain: DatabaseUtils.KeyStoreItem = ksAppPassword,
  prohibitedPasscodeKeychain: DatabaseUtils.KeyStoreItem = ksSelfDestructPassword,
  title: String = "Set your PIN",
  reason: String? = "Choose a pin that is unpredictable, but something you will remember.",
  iconResource: dev.icerock.moko.resources.ImageResource? = null,
  submit: () -> Unit,
  cancel: () -> Unit,
  close: () -> Unit
) {
  val passcode = rememberSaveable { mutableStateOf("") }
  var enteredPassword by rememberSaveable { mutableStateOf("") }
  var confirming by rememberSaveable { mutableStateOf(false) }
  var showSuccessDialog by rememberSaveable { mutableStateOf(false) }
  
  // Check if this is a duress PIN setup
  val isDuressPin = title.contains("Duress", ignoreCase = true)
  
  // Handle system back button based on current step
  BackHandler {
    if (showSuccessDialog) {
      // Don't allow back when dialog is showing
    } else if (confirming) {
      // Go back to Enter step
      passcode.value = ""
      confirming = false
    } else {
      // Exit the flow
      close()
      cancel()
    }
  }
  
  // Determine title based on confirming state
  val currentTitle = if (confirming) {
    when {
      title.contains("Duress", ignoreCase = true) -> "Confirm your Duress PIN"
      else -> "Confirm your PIN"
    }
  } else {
    title
  }
  
  Box(Modifier.fillMaxSize()) {
    // Content layer - apply blur when dialog is visible
    Box(
      modifier = Modifier
        .fillMaxSize()
        .then(if (showSuccessDialog) Modifier.blur(16.dp) else Modifier)
    ) {
      // Use a single PasscodeView with dynamic title/reason for animation to work
      // Icon: Always lock icon during PIN entry (matching Shredgram's SetDuressPinScreen)
      PasscodeView(
        passcode = passcode,
        title = currentTitle,
        reason = reason,
        submitLabel = if (confirming) generalGetString(MR.strings.confirm_verb) else generalGetString(MR.strings.save_verb),
        submitEnabled = { pwd ->
          if (confirming) {
            pwd == enteredPassword
          } else {
            pwd != prohibitedPasscodeKeychain.get()
          }
        },
        iconResource = MR.images.ic_lock_new,
        submit = {
          if (confirming) {
            if (passcode.value == enteredPassword) {
              passcodeKeychain.set(passcode.value)
              enteredPassword = ""
              passcode.value = ""
              // Show success dialog instead of closing immediately
              showSuccessDialog = true
            }
          } else {
            enteredPassword = passcode.value
            passcode.value = ""
            confirming = true
          }
        },
        cancel = {
          if (confirming) {
            // Go back to Enter step
            passcode.value = ""
            confirming = false
          } else {
            // Exit the flow
            close()
            cancel()
          }
        }
      )
    }
    
    // Success Dialog overlay
    if (showSuccessDialog) {
      // Foggy scrim overlay
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0x800C1013))
      )
      
      // Dialog
      Dialog(
        onDismissRequest = { /* Not dismissible by clicking outside */ },
        properties = DialogProperties(
          usePlatformDefaultWidth = false,
          dismissOnBackPress = false,
          dismissOnClickOutside = false
        )
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
        ) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.surface,
            elevation = 12.dp
          ) {
            Column(
              modifier = Modifier.padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              // Green checkmark icon (just tick, no circle background)
              Icon(
                painter = painterResource(MR.images.ic_check),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = SuccessGreen
              )
              
              Spacer(Modifier.height(8.dp))
              
              // Title - Shredgram: bodyLargeBold (16sp Bold) with Manrope
              Text(
                text = if (isDuressPin) "Duress PIN has been set." else "PIN has been set.",
                fontFamily = Manrope,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (16f * 1.5f).sp
              )
              
              Spacer(Modifier.height(4.dp))
              
              // Message - Shredgram: bodySmall (12sp Normal) with DMSans
              if (isDuressPin) {
                // Duress PIN message with orange bold highlights
                val annotatedMessage = buildAnnotatedString {
                  append("Remember your ")
                  withStyle(SpanStyle(color = WarningOrange, fontWeight = FontWeight.Bold)) {
                    append("Duress PIN")
                  }
                  append(". Whenever you use it, ")
                  withStyle(SpanStyle(color = WarningOrange, fontWeight = FontWeight.Bold)) {
                    append("all data will be deleted")
                  }
                  append(".")
                }
                Text(
                  text = annotatedMessage,
                  fontFamily = DMSans,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Normal,
                  color = Color(0xFF3D4042),  // onSurfaceVariant
                  textAlign = TextAlign.Center,
                  lineHeight = (12f * 1.5f).sp
                )
              } else {
                // Regular PIN message
                Text(
                  text = "Remember your PIN. You will need it every time you need to unlock the app.",
                  fontFamily = DMSans,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Normal,
                  color = Color(0xFF3D4042),  // onSurfaceVariant
                  textAlign = TextAlign.Center,
                  lineHeight = (12f * 1.5f).sp
                )
              }
              
              Spacer(Modifier.height(48.dp))
              
              // OK button - Shredgram: labelLarge (14sp Medium) with DMSans
              Button(
                onClick = {
                  showSuccessDialog = false
                  submit()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(360.dp),  // RadiusPill
                colors = ButtonDefaults.buttonColors(
                  backgroundColor = ElectricBlue500,
                  contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
              ) {
                Text(
                  text = "OK",
                  fontFamily = DMSans,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                  lineHeight = (14f * 1.5f).sp
                )
              }
            }
          }
        }
      }
    }
  }
}
