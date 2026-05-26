package chat.simplex.common.views.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chat.simplex.common.AppLock
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.usersettings.LAMode
import chat.simplex.common.views.database.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Shredgram colors - EXACT match from Color.kt
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val WarningOrange = Color(0xFFFF8A50)  // Orange400
private val SuccessGreen = Color(0xFF43A047)   // Green400
private val OnSurfaceVariant = Color(0xFF3D4042)  // DarkCharcoal700
private val BorderElevated1 = Color(0xFFCECFD0)   // DarkCharcoal100
private val DarkCharcoal400 = Color(0xFF868889)

// Shredgram Typography tokens - EXACT match from TypographyTokens.kt
private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f
// Font sizes from TypographyTokens.kt
private val font10 = 10.sp
private val font12 = 12.sp
private val font14 = 14.sp
private val font16 = 16.sp
private val font30 = 30.sp

@Composable
fun SetupDatabasePassphrase(m: ChatModel) {
  // Back to choose unlock method
  BackHandler {
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_ChooseUnlockMethod)
  }
  
  val progressIndicator = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  val prefs = m.controller.appPrefs
  val initialRandomDBPassphrase = remember { mutableStateOf(prefs.initialRandomDBPassphrase.get()) }
  // Do not do rememberSaveable on current key to prevent saving it on disk in clear text
  val currentKey = remember { mutableStateOf(if (initialRandomDBPassphrase.value) DatabaseUtils.ksDatabasePassword.get() ?: "" else "") }
  val newKey = rememberSaveable { mutableStateOf("") }
  val confirmNewKey = rememberSaveable { mutableStateOf("") }
  // Store the key value for use after success dialog dismisses
  val encryptedKeyValue = remember { mutableStateOf<String?>(null) }
  
  fun nextStep() {
    if (appPlatform.isAndroid || chatModel.currentUser.value != null) {
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step3_ChooseServerOperators)
    } else {
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.LinkAMobile)
    }
  }
  
  fun autoCreateUserAndProceed() {
    withBGApi {
      try {
        // Use default public profile name
        val defaultDisplayName = "Set Up Name"

        // Controller init can race with this onboarding step; retry a few times instead of crashing.
        var createdUser: User? = null
        var lastError: Throwable? = null
        repeat(4) { attempt ->
          if (createdUser != null) return@repeat  // already succeeded, skip remaining retries
          try {
            createdUser = m.controller.apiCreateActiveUser(
              null,
              Profile(displayName = defaultDisplayName, fullName = "", shortDescr = null, image = null)
            )
          } catch (e: Exception) {
            lastError = e
            if (e.message?.contains("Controller is not initialized") == true) {
              initChatController()
              delay(250L * (attempt + 1))
            } else {
              throw e
            }
          }
        }

        val user = createdUser
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
          if (lastError != null) {
            Log.e(TAG, "autoCreateUserAndProceed failed after retries: ${lastError?.stackTraceToString()}")
          }
          // If user creation fails, fallback to next step
          nextStep()
        }
      } catch (e: Exception) {
        Log.e(TAG, "autoCreateUserAndProceed unexpected error: ${e.stackTraceToString()}")
        // Prevent onboarding crash, proceed to the next step safely.
        nextStep()
      }
    }
  }
  
  // Function to proceed after success dialog
  // Keep dialog visible until navigation to prevent flash
  fun proceedAfterSuccess() {
    // DON'T hide dialog here - keep it visible during navigation
    encryptedKeyValue.value?.let { keyValue ->
      withLongRunningApi {
        startChat(keyValue)
        // Show lock authentication setup after database passphrase is set
        AppLock.showLANotice(prefs.laNoticeShown) { _ ->
          // Auto-create user with generated username and proceed
          autoCreateUserAndProceed()
        }
      }
    }
  }
  
  // Wrap everything in a Box to apply blur when success dialog is shown
  Box(Modifier.fillMaxSize()) {
    // Main content - blur when success dialog is visible
    Box(
      modifier = Modifier
        .fillMaxSize()
        .then(if (showSuccess.value) Modifier.blur(16.dp) else Modifier)
    ) {
  SetupDatabasePassphraseLayout(
    currentKey,
    newKey,
    confirmNewKey,
    progressIndicator,
    onConfirmEncrypt = {
      withLongRunningApi {
        if (m.chatRunning.value == true) {
          // Stop chat if it's started before doing anything
          stopChatAsync(m)
        }
        prefs.storeDBPassphrase.set(false)

        val newKeyValue = newKey.value
        val success = encryptDatabase(
          currentKey = currentKey,
          newKey = newKey,
          confirmNewKey = confirmNewKey,
          initialRandomDBPassphrase = mutableStateOf(true),
          useKeychain = mutableStateOf(false),
          storedKey = mutableStateOf(true),
          progressIndicator = progressIndicator,
              migration = false,
              showSuccessAlert = false  // We show our own success dialog
        )
        if (success) {
              // Store the key and show success dialog
              encryptedKeyValue.value = newKeyValue
              progressIndicator.value = false
              showSuccess.value = true
              // proceedAfterSuccess will be called when dialog auto-dismisses
        } else {
          // Rollback in case of it is finished with error in order to allow to repeat the process again
          prefs.storeDBPassphrase.set(true)
        }
      }
    },
    nextStep = ::nextStep,
  )
    }
    
    // Success dialog overlay - auto-dismisses after 2 seconds
    ShredgramSuccessDialog(
      visible = showSuccess.value,
      title = "Database encrypted successfully",
      autoDismissMillis = 2000,
      onDismiss = { proceedAfterSuccess() }
    )
  }

  DisposableEffect(Unit) {
    onDispose {
      if (m.chatRunning.value != true) {
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SetupDatabasePassphraseLayout(
  currentKey: MutableState<String>,
  newKey: MutableState<String>,
  confirmNewKey: MutableState<String>,
  progressIndicator: MutableState<Boolean>,
  onConfirmEncrypt: () -> Unit,
  nextStep: () -> Unit,
) {
  val useNFC = remember { mutableStateOf(false) }
  val showNewPassword = remember { mutableStateOf(false) }
  val showConfirmPassword = remember { mutableStateOf(false) }
  
  // Keyboard controller to hide keyboard
  val focusManager = LocalFocusManager.current
  
  // Shredgram: Track focus state for password strength indicator visibility
  var isPassphraseFocused by remember { mutableStateOf(false) }
  var isConfirmPassphraseFocused by remember { mutableStateOf(false) }
  
  // Shredgram: Show password strength ONLY when input is focused AND has content
  val showPasswordStrength = (isPassphraseFocused || isConfirmPassphraseFocused) && newKey.value.isNotEmpty()
  
  // Detect if keyboard is visible
  val isKeyboardVisible = WindowInsets.isImeVisible
  
  // Shredgram-style Encrypt Dialog state
  var showEncryptDialog by remember { mutableStateOf(false) }
  
  // Scroll state for auto-scroll when keyboard appears
  val scrollState = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()
  
  // Password strength validation
        val passwordStrength = remember(newKey.value) {
          derivedStateOf {
            if (newKey.value.isEmpty()) PassphraseStrength.REJECTED
            else PassphraseStrength.check(newKey.value)
          }
        }

        // Shredgram-style encrypt dialog
        val onClickUpdate = {
          if (!progressIndicator.value) {
            // Skip strength validation - go directly to encrypt dialog
                        showEncryptDialog = true
          }
        }
        
        val isPasswordRejected = passwordStrength.value == PassphraseStrength.REJECTED && newKey.value.isNotEmpty()
  val disabled = newKey.value != confirmNewKey.value ||
            newKey.value.isEmpty() ||
            !validKey(newKey.value) ||
            isPasswordRejected ||
            progressIndicator.value

  // Track if any overlay is visible (for blur effect)
  val showingOverlay = showEncryptDialog || progressIndicator.value

  // Main content with blur when dialog or progress is shown
  Box(Modifier.fillMaxSize()) {
    // Content layer - apply blur when any overlay is visible
    Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
        .statusBarsPadding()
        .then(if (showingOverlay) Modifier.blur(16.dp) else Modifier)
    ) {
    // Auto-scroll 48dp when keyboard appears (only once)
    LaunchedEffect(isKeyboardVisible) {
      if (isKeyboardVisible) {
        // Scroll down 48dp to show password strength indicator
        coroutineScope.launch {
          scrollState.animateScrollTo(scrollState.value + 48)
        }
      }
    }
    
    // Main scrollable content
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(scrollState)
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
            // Go back to choose unlock method
            chatModel.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_ChooseUnlockMethod)
          },
          modifier = Modifier.height(24.dp)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = "Back",
            tint = MaterialTheme.colors.onBackground
          )
        }
        
        // Center: Logo + "Shredgram" - matches TopBar styling
        Row(verticalAlignment = Alignment.CenterVertically) {
          Image(
            painter = painterResource(MR.images.ic_logo),
            contentDescription = "Shredgram Logo"
            // No explicit size - use natural drawable size like Shredgram
          )
          
          Spacer(Modifier.width(8.dp))
          
          // Shredgram TopBar uses titleMedium or similar
//          Text(
//            text = "Shredgram",
//            fontSize = 18.sp,  // titleMedium = Manrope Bold 18sp
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
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.height(16.dp))
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = "Set your database passphrase",
        fontFamily = Manrope,
        fontSize = font30,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        lineHeight = (font30.value * lineHeightHeadlineS).sp
      )
      
      Spacer(Modifier.height(8.dp))
      
      // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
      Text(
        text = "Keep your device secure! Choose a strong passphrase for your encryption key.",
        fontFamily = DMSans,
        fontSize = font14,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font14.value * lineHeightBody).sp
      )
      
      Spacer(Modifier.height(32.dp))
      
      // New Passphrase Field - Shredgram UIInputField style
      ShredgramInputField(
        value = newKey.value,
        onValueChange = { newKey.value = it },
        placeholder = "New passphrase",
        isPassword = true,
        showPassword = showNewPassword.value,
        onToggleVisibility = { showNewPassword.value = !showNewPassword.value },
        onFocusChange = { isPassphraseFocused = it }
      )
      
      Spacer(Modifier.height(16.dp))
      
      // Confirm Passphrase Field - Shredgram UIInputField style
      ShredgramInputField(
        value = confirmNewKey.value,
        onValueChange = { confirmNewKey.value = it },
        placeholder = "Confirm passphrase",
        isPassword = true,
        showPassword = showConfirmPassword.value,
        onToggleVisibility = { showConfirmPassword.value = !showConfirmPassword.value },
        onFocusChange = { isConfirmPassphraseFocused = it }
      )
      
      // Password Strength Indicator - Shredgram: show only when focused AND has content
      if (showPasswordStrength) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
        PasswordStrengthIndicator(
          strength = passwordStrength.value,
          modifier = Modifier.fillMaxWidth()
        )
          Spacer(Modifier.height(32.dp))
      }
      } else {
        Spacer(Modifier.height(32.dp))
      }
      
      // Shredgram Divider with "or" - exact match
      ShredgramDivider()
        
        Spacer(Modifier.height(32.dp))
        
      // NFC Option Card - Shredgram OptionCard style
      ShredgramOptionCardPassphrase(
        title = "Use NFC passkey",
        description = "Unlock by tapping your hardware key.",
        selected = useNFC.value,
          onClick = {
            useNFC.value = !useNFC.value
            if (useNFC.value) {
              AlertManager.shared.showAlertMsg(
                title = generalGetString(MR.strings.coming_soon),
                text = "NFC passkey support is coming soon!"
              )
              useNFC.value = false
            }
        },
        icon = {
          // Shredgram: no explicit tint (uses LocalContentColor which is onSurface)
          Icon(
            painter = painterResource(MR.images.ic_key_lock),
            contentDescription = null
          )
        }
      )
    }
    
    // Fixed bottom section - Shredgram bottomContent
    // imePadding() moves this above keyboard when visible
    // navigationBarsPadding() handles gesture navigation bar
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // PrimaryButton - Shredgram: RadiusPill, padding 16dp h/v, labelLarge text (14sp Medium)
      Button(
        onClick = {
          // Hide keyboard first
          focusManager.clearFocus()
          
          if (useNFC.value) {
            AlertManager.shared.showAlertMsg(
              title = generalGetString(MR.strings.coming_soon),
              text = "NFC passkey support is coming soon!"
            )
          } else {
            onClickUpdate()
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
        enabled = !disabled || useNFC.value,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        elevation = ButtonDefaults.elevation(
          defaultElevation = 0.dp,
          pressedElevation = 0.dp
        )
      ) {
        // Shredgram: labelLarge = DMSans Medium 14sp, lineHeight 1.5
        Text(
          text = "Next",
          fontSize = font14,
          fontWeight = FontWeight.Medium,
          lineHeight = (font14.value * lineHeightBody).sp
        )
      }
      
      // Hide terms when keyboard is visible to save space
      if (!isKeyboardVisible) {
        Spacer(Modifier.height(16.dp))
      }
    }
    }
    
    // Shredgram Encrypt Dialog overlay - shown on top of blurred content
    ShredgramEncryptDatabaseDialog(
      visible = showEncryptDialog,
      onDismiss = { showEncryptDialog = false },
      onConfirm = {
        showEncryptDialog = false
        onConfirmEncrypt()
      }
    )
    
    // Progress overlay (on top of blurred content)
    if (progressIndicator.value) {
      ShredgramProgressOverlay()
    }
  }
}

/**
 * Shredgram PasswordStrengthIndicator - EXACT match from PasswordStrengthIndicator.kt
 * - Row with Icon (10dp) + Label (bodyExtraSmall = 10sp) + 3 progress bars (4dp height)
 * - Colors: Weak=#FF8A50, Good=#FFC48A, Strong=#43A047
 * - Inactive bars: White
 * - Spacing: 8dp between elements
 */
@Composable
fun PasswordStrengthIndicator(
  strength: PassphraseStrength,
  modifier: Modifier = Modifier
) {
  // Shredgram exact colors from PasswordStrengthIndicator.kt
  val weakColor = Color(0xFFFF8A50)    // Orange for Weak
  val goodColor = Color(0xFFFFC48A)    // Light orange for Good  
  val strongColor = Color(0xFF43A047)  // Green for Strong
  val inactiveColor = Color.White      // White for inactive bars
  
  // Map PassphraseStrength to Shredgram PassStrength levels
  // Shredgram: None, Weak, Good, Strong
  val strengthLevel = when (strength) {
    PassphraseStrength.REJECTED -> 1  // Weak
    PassphraseStrength.ACCEPTABLE -> 2  // Good
    PassphraseStrength.SECURE, PassphraseStrength.PARANOID -> 3  // Strong
  }
  
  val (label, color, iconRes) = when (strengthLevel) {
    1 -> Triple("Weak", weakColor, MR.images.ic_info)
    2 -> Triple("Good", goodColor, MR.images.ic_info)
    else -> Triple("Strong", strongColor, MR.images.ic_check_circle)
  }

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Icon + Label - Shredgram: bodyExtraSmall = DMSans Normal 10sp, lineHeight 1.5
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(10.dp)
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = label,
        fontSize = font10,
        fontWeight = FontWeight.Normal,
        color = color,
        lineHeight = (font10.value * lineHeightBody).sp
      )
    }
    
    // 3 progress bars - Shredgram: 4dp height, RadiusCircle shape
    repeat(3) { index ->
      Box(
        modifier = Modifier
          .weight(1f)
          .height(4.dp)
          .background(
            color = when {
              strengthLevel == 1 && index == 0 -> color
              strengthLevel == 2 && index <= 1 -> color
              strengthLevel == 3 -> color
              else -> inactiveColor
            },
            shape = RoundedCornerShape(50)  // RadiusCircle
          )
      )
    }
  }
}

/**
 * Shredgram TermsAndPrivacyText - EXACT match from TermsAndPrivacyText.kt
 * - Intro text: bodySmall (DMSans Normal 12sp, lineHeight 1.5), onSurfaceVariant color
 * - 4dp spacer
 * - Link text: bodySmall (12sp), ElectricBlue500 for links
 */
@Composable
fun ShredgramTermsAndPrivacyText(
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Intro text - Shredgram: bodySmall (12sp), onSurfaceVariant
    Text(
      text = "By continuing, you agree to our",
      fontSize = font12,
      fontWeight = FontWeight.Normal,
      color = OnSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = (font12.value * lineHeightBody).sp
    )
    
    Spacer(Modifier.height(4.dp))
    
    // Link text - Shredgram: bodySmall (12sp), with ElectricBlue500 links
    val annotatedString = buildAnnotatedString {
      pushStringAnnotation(tag = "terms", annotation = "terms")
      withStyle(style = SpanStyle(
        color = ElectricBlue500,
        fontSize = font12
      )) {
        append("Terms of Service")
      }
      pop()
      withStyle(style = SpanStyle(
        color = OnSurfaceVariant,
        fontSize = font12
      )) {
        append(" and ")
      }
      pushStringAnnotation(tag = "privacy", annotation = "privacy")
      withStyle(style = SpanStyle(
        color = ElectricBlue500,
        fontSize = font12
      )) {
        append("Privacy Policy")
      }
      pop()
    }
    
    ClickableText(
      text = annotatedString,
      style = TextStyle(
        textAlign = TextAlign.Center,
        lineHeight = (font12.value * lineHeightBody).sp
      ),
      onClick = { offset ->
        annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset)
          .firstOrNull()?.let { /* TODO: Open Terms */ }
        annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset)
          .firstOrNull()?.let { /* TODO: Open Privacy */ }
      }
    )
  }
}

/**
 * Bigger dot visual transformation for password fields
 * Uses a larger bullet character for better visibility
 */
private class BiggerDotVisualTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    // Use bigger bullet character (●) instead of default small dot
    val masked = AnnotatedString("●".repeat(text.length))
    return TransformedText(masked, OffsetMapping.Identity)
  }
}

/**
 * Shredgram UIInputField - EXACT match from UIInputField.kt
 * - RadiusCircle shape (RoundedCornerShape(50))
 * - Padding: 24dp horizontal, 18dp vertical (dynamic height)
 * - Border: BorderBrand (ElectricBlue500) when focused, outlineVariant when not
 * - Placeholder: bodySmall (DMSans Normal 12sp), onSurfaceVariant color
 * - Text: bodySmall (12sp)
 * - Bigger password dots using BiggerDotVisualTransformation
 * - onFocusChange callback for tracking focus state
 */
@Composable
fun ShredgramInputField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  isPassword: Boolean = false,
  showPassword: Boolean = false,
  onToggleVisibility: (() -> Unit)? = null,
  onFocusChange: ((Boolean) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  var isFocused by remember { mutableStateOf(false) }
  
  // Shredgram: BorderBrand when focused, outlineVariant when not
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  val borderColor = if (isFocused) ElectricBlue500 else outlineVariant
  
  // Use bigger dots for password - Shredgram uses bodySmall.fontSize * lineHeightBody
  val visualTransformation = when {
    isPassword && !showPassword -> BiggerDotVisualTransformation()
    else -> VisualTransformation.None
  }
  
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { 
        isFocused = it.isFocused
        onFocusChange?.invoke(it.isFocused)
      },
    singleLine = true,
    textStyle = TextStyle(
      fontSize = font12,  // Shredgram: bodySmall = 12sp
      fontWeight = FontWeight.Normal,
      color = MaterialTheme.colors.onSurface,
      lineHeight = (font12.value * lineHeightBody).sp
    ),
    cursorBrush = SolidColor(ElectricBlue500),
    visualTransformation = visualTransformation,
    interactionSource = interactionSource
  ) { innerTextField ->
    // Shredgram: 2dp border when focused, 1dp when unfocused (Material3 OutlinedTextField style)
    val borderWidth = if (isFocused) 2.dp else 1.dp
    Box(
      modifier = Modifier
        .border(borderWidth, borderColor, RoundedCornerShape(50))  // RadiusCircle
        .background(MaterialTheme.colors.surface, RoundedCornerShape(50))
        .padding(horizontal = 24.dp, vertical = 18.dp),  // Shredgram: 24dp h, 18dp v
      contentAlignment = Alignment.CenterStart
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(modifier = Modifier.weight(1f)) {
          if (value.isEmpty()) {
            Text(
              text = placeholder,
              fontSize = font12,  // Shredgram: bodySmall = 12sp
              fontWeight = FontWeight.Normal,
              color = OnSurfaceVariant,
              lineHeight = (font12.value * lineHeightBody).sp
            )
          }
          innerTextField()
        }
        
        if (isPassword && onToggleVisibility != null) {
          Spacer(Modifier.width(8.dp))
          IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(24.dp)
          ) {
            Icon(
              painter = painterResource(
                if (showPassword) MR.images.ic_visibility_off_filled 
                else MR.images.ic_visibility_filled
              ),
              contentDescription = if (showPassword) "Hide password" else "Show password",
              tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Shredgram Divider - EXACT match from Devider.kt
 * - Row with two HorizontalDivider and "or" text in middle
 * - 1dp height lines
 * - outlineVariant color
 * - Text: bodySmall (DMSans Normal 12sp, lineHeight 1.5), onSurfaceVariant, horizontal padding 8dp
 */
@Composable
fun ShredgramDivider(
  modifier: Modifier = Modifier,
  text: String = "or"
) {
  // Shredgram outlineVariant
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Divider(
      modifier = Modifier
        .weight(1f)
        .height(1.dp),
      color = outlineVariant
    )
    
    Text(
      text = text,
      fontSize = font12,  // Shredgram: bodySmall = 12sp
      fontWeight = FontWeight.Normal,
      color = OnSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = (font12.value * lineHeightBody).sp,
      modifier = Modifier.padding(horizontal = 8.dp)
    )
    
    Divider(
      modifier = Modifier
        .weight(1f)
        .height(1.dp),
      color = outlineVariant
    )
  }
}

/**
 * Shredgram OptionCard for Passphrase screen - EXACT match from OptionCard.kt
 * - RadiusLarge (16dp)
 * - Shadow: 10dp when selected (shadowElevation), 0dp when not
 * - tonalElevation: 0dp
 * - Border: 1dp, BorderBrand (ElectricBlue500) when selected, outlineVariant when not
 * - Padding: 16dp
 * - Layout: Icon | 8dp | Title+Description | 8dp | RadioButton
 * - Title: titleExtraSmall (Manrope Bold 14sp, lineHeight 1.5)
 * - Description: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
 */
@Composable
fun ShredgramOptionCardPassphrase(
  title: String,
  description: String,
  selected: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit
) {
  // Shredgram outlineVariant
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),  // RadiusLarge
    color = MaterialTheme.colors.surface,
    elevation = if (selected) 10.dp else 0.dp,  // shadowElevation
    border = BorderStroke(
      width = 1.dp,
      color = if (selected) ElectricBlue500 else outlineVariant
    )
  ) {
    Row(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
      verticalAlignment = Alignment.Top
    ) {
      // Icon
      icon()
      
      Spacer(Modifier.width(8.dp))
      
      // Title and Description - Shredgram style
      // Both texts in same Column = both start at same horizontal position
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.Start  // Explicitly align start
      ) {
        // Title: titleExtraSmall (Manrope Bold 14sp, lineHeight 1.5)
        Text(
          text = title,
          fontSize = font14,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          lineHeight = (font14.value * lineHeightBody).sp,
          textAlign = TextAlign.Start
        )
        
        Spacer(Modifier.height(4.dp))
        
        // Description: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
        // Aligned with title - starts at same position
        Text(
          text = description,
          fontSize = font12,
          fontWeight = FontWeight.Normal,
          color = OnSurfaceVariant,
          lineHeight = (font12.value * lineHeightBody).sp,
          textAlign = TextAlign.Start
        )
      }
      
      Spacer(Modifier.width(8.dp))
      
      // Radio button - Shredgram style (16dp, with checkmark)
      ShredgramRadioButtonSmall(
        selected = selected,
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
fun ShredgramRadioButtonSmall(
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
fun ModernPassphraseField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  showPassword: Boolean,
  onToggleVisibility: () -> Unit
) {
  ShredgramInputField(
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    isPassword = true,
    showPassword = showPassword,
    onToggleVisibility = onToggleVisibility
  )
}

@Composable
fun NFCOptionCard(
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    backgroundColor = Color.Transparent,
    border = if (isSelected)
      BorderStroke(2.dp, MaterialTheme.colors.primary)
    else
      BorderStroke(0.5.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.2f)),
    elevation = 0.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
      // Top row: Icon, Title, and Tick/Circle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Icon on the left
        Icon(
          painter = painterResource(MR.images.ic_key_lock),
          contentDescription = "Use NFC passkey",
          modifier = Modifier.size(28.dp),
          tint = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
        )
        
        Spacer(Modifier.width(12.dp))
        
        // Title - flexible width
        Text(
          text = "Use NFC passkey",
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colors.onBackground,
          modifier = Modifier.weight(1f),
          maxLines = 2 // Allow wrapping if needed
        )
        
        Spacer(Modifier.width(12.dp))
        
        // Checkmark or empty circle on the far right
        if (isSelected) {
          // Empty circle with blue checkmark
          Box(
            modifier = Modifier
              .size(24.dp)
              .border(
                width = 2.dp,
                color = MaterialTheme.colors.primary,
                shape = RoundedCornerShape(12.dp)
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painter = painterResource(MR.images.ic_check),
              contentDescription = "Selected",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colors.primary
            )
          }
        } else {
          // Empty circle
          Box(
            modifier = Modifier
              .size(24.dp)
              .border(
                width = 2.dp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
              )
          )
        }
      }
      
      // Description below - responsive padding
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Unlock by tapping your hardware key.",
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        lineHeight = 20.sp,
        modifier = Modifier.padding(start = 40.dp) // Align with title (icon size + spacing)
      )
    }
  }
}


/**
 * Shredgram-style Progress Overlay
 * Uses the shared ShredgramProgressOverlay component
 */
@Composable
private fun ShredgramProgressOverlayLocal() {
  chat.simplex.common.views.helpers.ShredgramProgressOverlay()
}

// Keep old one for backwards compatibility
@Composable
private fun ProgressIndicator() {
  ShredgramProgressOverlayLocal()
}

private suspend fun startChat(key: String?) {
  val m = ChatModel
  initChatController(key)
  m.chatDbChanged.value = false
  m.chatRunning.value = true
}

/**
 * Shredgram-style Modal Dialog
 * Matches the exact UI from the Shredgram project
 */
@Composable
fun ShredgramModal(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  title: String? = null,
  message: String? = null,
  icon: (@Composable () -> Unit)? = null,
  primaryAction: ShredgramModalAction? = null,
  secondaryAction: ShredgramModalAction? = null,
  dismissOnBackPress: Boolean = true,
  dismissOnClickOutside: Boolean = true,
) {
  if (!visible) return

  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = dismissOnBackPress,
      dismissOnClickOutside = dismissOnClickOutside
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
        elevation = 12.dp,
      ) {
        Column(
          modifier = Modifier.padding(
            horizontal = 32.dp,
            vertical = 32.dp
          ),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          if (icon != null) {
            icon()
            Spacer(Modifier.height(8.dp))
          }

          if (!title.isNullOrBlank()) {
            // Shredgram: bodyLargeBold (DMSans Bold 16sp, lineHeight 1.5)
            Text(
              text = title,
              fontSize = font16,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colors.onSurface,
              textAlign = TextAlign.Center,
              lineHeight = (font16.value * lineHeightBody).sp
            )
          }

          if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            // Shredgram: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
            Text(
              text = message,
              fontSize = font12,
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
              textAlign = TextAlign.Center,
              lineHeight = (font12.value * lineHeightBody).sp
            )
          }

          val hasButtons = primaryAction != null || secondaryAction != null
          if (hasButtons) {
            Spacer(Modifier.height(32.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              if (secondaryAction != null) {
                // Secondary button - Shredgram: transparent, onSurface
                TextButton(
                  onClick = secondaryAction.onClick,
                  enabled = secondaryAction.enabled,
                  contentPadding = PaddingValues(0.dp)
                ) {
                  Text(
                    text = secondaryAction.text,
                    fontSize = font12,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colors.onSurface,
                    lineHeight = (font12.value * lineHeightBody).sp
                  )
                }
              } else {
                Spacer(Modifier.width(1.dp))
              }

              if (primaryAction != null) {
                // Primary button - Shredgram: RadiusPill, labelLarge (14sp Medium)
                Button(
                  onClick = primaryAction.onClick,
                  enabled = primaryAction.enabled,
                  shape = RoundedCornerShape(360.dp),  // RadiusPill
                  colors = ButtonDefaults.buttonColors(
                    backgroundColor = ElectricBlue500,
                    contentColor = Color.White
                  ),
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                  elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                  )
                ) {
                  Text(
                    text = primaryAction.text,
                    fontSize = font14,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (font14.value * lineHeightBody).sp
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

data class ShredgramModalAction(
  val text: String,
  val onClick: () -> Unit,
  val enabled: Boolean = true,
)

/**
 * Shredgram-style Spinning Loading Indicator
 */
@Composable
fun ShredgramSpinningIndicator(
  modifier: Modifier = Modifier,
  durationMillis: Int = 900
) {
  val transition = rememberInfiniteTransition(label = "spinner")
  val rotation by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = durationMillis, easing = LinearEasing)
    ),
    label = "rotation"
  )

  // Use CircularProgressIndicator styled to look like Shredgram's spinner
    CircularProgressIndicator(
    modifier = modifier.graphicsLayer { rotationZ = rotation },
    color = ElectricBlue500,
      strokeWidth = 3.dp
    )
  }

/**
 * Shredgram-style Loading Overlay with blur effect
 */
@Composable
fun ShredgramLoadingOverlay(
  isLoading: Boolean,
  content: @Composable () -> Unit
) {
  val blurDp = if (isLoading) 16.dp else 0.dp
  val interaction = remember { MutableInteractionSource() }

  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().blur(blurDp)) {
      content()
    }

    if (isLoading) {
      // Blocks touches + shows scrim + spinner
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f))
          .clickable(
            interactionSource = interaction,
            indication = null
          ) { /* consume touches */ },
        contentAlignment = Alignment.Center
      ) {
        ShredgramSpinningIndicator(
          modifier = Modifier.size(40.dp)
        )
      }
    }
  }
}

/**
 * Shredgram Encrypt Database Dialog - EXACT match to local project
 * 
 * UIModal.kt specs:
 * - Dialog with usePlatformDefaultWidth = false
 * - Box with 40dp horizontal padding (space40DP)
 * - Surface with RadiusLarge (16dp), surface color, 12dp shadow elevation
 * - Column with 32dp horizontal and vertical padding
 * - Icon + 8dp spacer
 * - Title: bodyLargeBold (DMSans Bold 18sp, lineHeight 1.5)
 * - Message: bodySmall (DMSans Normal 14sp, lineHeight 1.5), onSurfaceVariant
 * - 48dp spacer before buttons (space48DP)
 * - Row: SecondaryButton (transparent, onSurface, no padding) left, PrimaryButton right
 */
@Composable
fun ShredgramEncryptDatabaseDialog(
  visible: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  if (!visible) return

  // Foggy scrim color from Figma: #0C1013 at 50% opacity
  val scrimColor = Color(0x800C1013)  // 0x80 = 50% alpha as per Figma design
  
  // Full screen overlay with scrim (shown on top of blurred content)
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(scrimColor)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { onDismiss() },
    contentAlignment = Alignment.Center
  ) {
    // Dialog card
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 40.dp)  // Shredgram: space40DP
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null
        ) { /* Consume clicks on dialog */ },
      shape = RoundedCornerShape(16.dp),  // Shredgram: RadiusLarge
      color = MaterialTheme.colors.surface,
      elevation = 12.dp  // Shredgram: space12DP shadowElevation
    ) {
      Column(
        modifier = Modifier.padding(
          horizontal = 32.dp,  // Shredgram: space32DP
          vertical = 32.dp    // Shredgram: space32DP
        ),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Warning Icon - Shredgram: natural size (no explicit size), Color.Unspecified
        Icon(
          painter = painterResource(MR.images.ic_warning),
          contentDescription = null,
          tint = Color.Unspecified  // Shredgram uses Color.Unspecified for icons
        )
        
        Spacer(Modifier.height(8.dp))  // Shredgram: space8DP
        
        // Title - Shredgram: bodyLargeBold (DMSans Bold 16sp, lineHeight 1.5)
        Text(
          text = "Encrypt database",
          fontFamily = DMSans,
          fontSize = font16,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          textAlign = TextAlign.Center,
          lineHeight = (font16.value * lineHeightBody).sp
        )
        
        Spacer(Modifier.height(4.dp))  // Shredgram: space4DP
        
        // Message - Shredgram: bodySmall (DMSans Normal 12sp, lineHeight 1.5), onSurfaceVariant
        Text(
          text = "Ensure you remember your passphrase. Without it, you won't be able to access your encrypted data if needed.",
          fontFamily = DMSans,
          fontSize = font12,
          fontWeight = FontWeight.Normal,
          color = OnSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = (font12.value * lineHeightBody).sp
        )
        
        Spacer(Modifier.height(32.dp))  // Shredgram: space32DP (reduced from 48dp for compact look)
        
        // Buttons Row - Shredgram: Cancel left, Encrypt right
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          // Secondary Button (Cancel) - Shredgram: transparent, onSurface, no padding
          TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(0.dp)  // Shredgram: space0DP
          ) {
            Text(
              text = "Cancel",
              fontFamily = DMSans,
              fontSize = font12,  // Shredgram: bodySmall = 12sp
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.onSurface,
              lineHeight = (font12.value * lineHeightBody).sp
            )
          }
          
          // Primary Button (Encrypt) - Shredgram: RadiusPill, primary color, 16dp h / 8dp v padding
          Button(
            onClick = onConfirm,
            shape = RoundedCornerShape(360.dp),  // Shredgram: RadiusPill
            colors = ButtonDefaults.buttonColors(
              backgroundColor = ElectricBlue500,  // Shredgram: primary
              contentColor = Color.White  // Shredgram: onPrimary
            ),
            contentPadding = PaddingValues(
              horizontal = 16.dp,  // Shredgram: space16DP
              vertical = 8.dp      // Shredgram: space8DP
            ),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
          ) {
            // Shredgram: labelLarge = DMSans Medium 14sp, lineHeight 1.5
            Text(
              text = "Encrypt",
              fontFamily = DMSans,
              fontSize = font14,
              fontWeight = FontWeight.Medium,
              lineHeight = (font14.value * lineHeightBody).sp
            )
          }
        }
      }
    }
  }
}

/**
 * Shredgram Success Dialog - dialog card with blur background
 * Auto-dismiss after 2 seconds
 */
@Composable
fun ShredgramSuccessDialog(
  visible: Boolean,
  title: String = "Database encrypted successfully",
  autoDismissMillis: Long = 2000,
  onDismiss: () -> Unit
) {
  if (!visible) return

  // Auto-dismiss after specified time
  LaunchedEffect(visible) {
    if (visible) {
      delay(autoDismissMillis)
      onDismiss()
    }
  }

  // Foggy scrim color: #0C1013 at 50% opacity (same as progress overlay)
  val scrimColor = Color(0x800C1013)
  
  // Full screen overlay with scrim
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(scrimColor)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { /* consume touches, auto-dismiss handles closing */ },
    contentAlignment = Alignment.Center
  ) {
    // Dialog card
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 40.dp),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colors.surface,
      elevation = 12.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Success Icon - just checkmark without circle
        Icon(
          painter = painterResource(MR.images.ic_check),
          contentDescription = null,
          tint = SuccessGreen,
          modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Title below icon
        Text(
          text = title,
          fontSize = font14,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          textAlign = TextAlign.Center,
          lineHeight = (font14.value * lineHeightBody).sp
        )
      }
    }
  }
}
