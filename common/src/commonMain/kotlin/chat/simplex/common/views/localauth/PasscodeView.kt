package chat.simplex.common.views.localauth

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.icerock.moko.resources.compose.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.views.chat.group.ProgressIndicator
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR

@Composable
fun PasscodeView(
  passcode: MutableState<String>,
  title: String,
  reason: String? = null,
  submitLabel: String,
  submitEnabled: ((String) -> Boolean)? = null,
  buttonsEnabled: State<Boolean> = remember { mutableStateOf(true) },
  iconResource: dev.icerock.moko.resources.ImageResource? = null,
  iconSize: Dp = 24.dp,
  submit: () -> Unit,
  cancel: () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }

  @Composable
  fun Modifier.handleKeyboard(): Modifier {
    val numbers = remember {
      arrayOf(
        Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9
      )
    }
    return onPreviewKeyEvent {
      if (it.key in numbers && it.type == KeyEventType.KeyDown) {
        if (passcode.value.length < 8) {
          passcode.value += numbers.indexOf(it.key) % 10
        }
        true
      } else if (it.key == Key.Backspace && it.type == KeyEventType.KeyDown && (it.isCtrlPressed || it.isMetaPressed)) {
        passcode.value = ""
        true
      } else if (it.key == Key.Backspace && it.type == KeyEventType.KeyDown) {
        passcode.value = passcode.value.dropLast(1)
        true
      } else if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyUp) {
        if ((submitEnabled?.invoke(passcode.value) != false && passcode.value.length >= 6)) {
          submit()
        }
        true
      } else {
        false
      }
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .handleKeyboard()
      .focusRequester(focusRequester)
      .statusBarsPadding()
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // TopBar - Shredgram style: 32dp vertical padding, consistent with all screens
    Row(
      Modifier
        .fillMaxWidth()
        .padding(vertical = 32.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Back button - height 24dp like Shredgram
      IconButton(
        onClick = cancel, 
        enabled = buttonsEnabled.value,
        modifier = Modifier.height(24.dp)
      ) {
        Icon(
          painterResource(MR.images.ic_arrow_back_ios_new),
          contentDescription = "Back",
          tint = MaterialTheme.colors.onBackground
        )
      }
      
      // Center: Text logo
      Image(
        painter = painterResource(MR.images.ic_logo),
        contentDescription = "Shredgram"
      )
      
      // Spacer for symmetry
      Spacer(Modifier.width(48.dp))
    }

    // Lock Icon - Shredgram: 24dp, onSurface tint (no spacer between TopBar and icon)
    Icon(
      painterResource(iconResource ?: MR.images.ic_lock_new),
      contentDescription = null,
      modifier = Modifier.size(iconSize),
      tint = MaterialTheme.colors.onSurface
    )

    Spacer(Modifier.height(16.dp))

    // Shredgram DuressPinStepHeader style animation - exactly like local project
    AnimatedContent(
      targetState = title to reason,
      transitionSpec = {
        val (newTitle, _) = targetState
        val goingForward = newTitle.contains("Confirm", ignoreCase = true)
        
        if (goingForward) {
          // Forward (Enter → Confirm): new content slides in from RIGHT
          (slideInHorizontally { it } + fadeIn())
            .togetherWith(slideOutHorizontally { -it / 2 } + fadeOut())
        } else {
          // Back (Confirm → Enter): new content slides in from LEFT
          (slideInHorizontally { -it } + fadeIn())
            .togetherWith(slideOutHorizontally { it / 2 } + fadeOut())
        }
      },
      label = "pin_header_animation"
    ) { (currentTitle, currentReason) ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.height(92.dp)  // Shredgram: space92DP for header
      ) {
        // Title - Shredgram headlineSmall: Manrope Bold 30sp, lineHeight 1.12
        Text(
          text = currentTitle,
          fontFamily = Manrope,
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          textAlign = TextAlign.Center,
          lineHeight = (30f * 1.12f).sp,
          letterSpacing = (-0.02).em
        )

        Spacer(Modifier.height(8.dp))

        // Description - Shredgram bodyMedium: DMSans Normal 14sp, lineHeight 1.5, onSurfaceVariant
        if (currentReason != null) {
          Text(
            text = currentReason,
            fontFamily = DMSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF3D4042),  // onSurfaceVariant
            textAlign = TextAlign.Center,
            lineHeight = (14f * 1.5f).sp,
            modifier = Modifier.widthIn(max = 320.dp)  // Shredgram: space320DP max width
          )
        }
      }
    }

    // PIN Dots in weighted Box - Shredgram style
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(vertical = 32.dp),
      contentAlignment = Alignment.Center
    ) {
      PasscodeDotsDisplay(
        password = passcode,
        modifier = Modifier.fillMaxWidth()
      )
    }

    // Numeric Keypad (stays in place)
    PasscodeKeypad(
      passcode = passcode,
      onSubmit = submit,
      submitEnabled = submitEnabled?.invoke(passcode.value) != false && passcode.value.length >= 6 && buttonsEnabled.value
    )

    Spacer(Modifier.height(32.dp))
  }

  if (!buttonsEnabled.value) {
    ProgressIndicator()
  }
  
  val view = LocalMultiplatformView()
  LaunchedEffect(Unit) {
    hideKeyboard(view, true)
    focusRequester.requestFocus()
    focusRequester.captureFocus()
  }
}
