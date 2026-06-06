package chat.simplex.common.views.database

import SectionBottomSpacer
import SectionItemViewSpaceBetween
import SectionSpacer
import SectionView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.*
import androidx.compose.material.*
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.*
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.PasswordStrengthIndicator
import chat.simplex.common.views.onboarding.ShredgramInputField
import chat.simplex.common.views.usersettings.SettingsActionItem
import chat.simplex.res.MR
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlin.math.log2

// Global state for database encryption success dialog blur effect
object DatabaseEncryptionSuccessState {
  val isShowing = mutableStateOf(false)
}

@Composable
fun DatabaseEncryptionView(m: ChatModel, migration: Boolean) {
  val progressIndicator = remember { mutableStateOf(false) }
  val useKeychain = remember { mutableStateOf(appPrefs.storeDBPassphrase.get()) }
  val initialRandomDBPassphrase = remember { mutableStateOf(appPrefs.initialRandomDBPassphrase.get()) }
  val storedKey = remember { val key = DatabaseUtils.ksDatabasePassword.get(); mutableStateOf(key != null && key != "") }
  // Do not do rememberSaveable on current key to prevent saving it on disk in clear text
  val currentKey = remember { mutableStateOf(if (initialRandomDBPassphrase.value) DatabaseUtils.ksDatabasePassword.get() ?: "" else "") }
  val newKey = rememberSaveable { mutableStateOf("") }
  val confirmNewKey = rememberSaveable { mutableStateOf("") }
  val chatLastStart = remember { mutableStateOf(appPrefs.chatLastStart.get()) }

  Box(
    Modifier.fillMaxSize(),
  ) {
    DatabaseEncryptionLayout(
      useKeychain,
      m.chatDbEncrypted.value,
      currentKey,
      newKey,
      confirmNewKey,
      storedKey,
      initialRandomDBPassphrase,
      progressIndicator,
      migration,
      onConfirmEncrypt = {
        // it will try to stop and start the chat in case of: non-migration && successful encryption. In migration the chat will remain stopped
        stopChatRunBlockStartChat(migration, chatLastStart, progressIndicator, ) {
          val success = encryptDatabase(
            currentKey = currentKey,
            newKey = newKey,
            confirmNewKey = confirmNewKey,
            initialRandomDBPassphrase = initialRandomDBPassphrase,
            useKeychain = useKeychain,
            storedKey = storedKey,
            progressIndicator = progressIndicator,
            migration = migration
          )
          success && !migration
        }
      }
    )
    if (progressIndicator.value) {
      Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          Modifier
            .padding(horizontal = 2.dp)
            .size(30.dp),
          color = MaterialTheme.colors.secondary,
          strokeWidth = 2.5.dp
        )
      }
    }
  }
}

@Composable
fun DatabaseEncryptionLayout(
  useKeychain: MutableState<Boolean>,
  chatDbEncrypted: Boolean?,
  currentKey: MutableState<String>,
  newKey: MutableState<String>,
  confirmNewKey: MutableState<String>,
  storedKey: MutableState<Boolean>,
  initialRandomDBPassphrase: MutableState<Boolean>,
  progressIndicator: MutableState<Boolean>,
  migration: Boolean,
  onConfirmEncrypt: () -> Unit,
) {
  BackHandler(onBack = { ModalManager.start.closeModal() })
  @Composable
  fun Layout() {
    Column {
      if (migration) {
        ChatStoppedView()
        SectionSpacer()
      }
      SectionView(if (migration) generalGetString(MR.strings.database_passphrase).uppercase() else null) {
        SavePassphraseSetting(
          useKeychain.value,
          initialRandomDBPassphrase.value,
          storedKey.value,
          enabled = (!initialRandomDBPassphrase.value && !progressIndicator.value) || migration
        ) { checked ->
          if (checked) {
            setUseKeychain(true, useKeychain, migration)
          } else if (storedKey.value && !migration) {
            // Don't show in migration process since it will remove the key after successful encryption
            removePassphraseAlert {
              removePassphraseFromKeyChain(useKeychain, storedKey, false)
            }
          } else {
            setUseKeychain(false, useKeychain, migration)
          }
        }

        if (!initialRandomDBPassphrase.value && chatDbEncrypted == true) {
          PassphraseField(
            currentKey,
            generalGetString(MR.strings.current_passphrase),
            modifier = Modifier.padding(horizontal = DEFAULT_PADDING),
            isValid = ::validKey,
            keyboardActions = KeyboardActions(onNext = { defaultKeyboardAction(ImeAction.Next) }),
          )
        }

        PassphraseField(
          newKey,
          generalGetString(MR.strings.new_passphrase),
          modifier = Modifier.padding(horizontal = DEFAULT_PADDING),
          showStrength = true,
          isValid = ::validKey,
          keyboardActions = KeyboardActions(onNext = { defaultKeyboardAction(ImeAction.Next) }),
        )
        
        // Only check minimum length - no character type requirements
        val meetsMinLength = meetsMinimumLength(newKey.value)
        
        // Real-time entropy calculation for display
        val entropy = remember(newKey.value) {
          derivedStateOf { 
            if (newKey.value.isEmpty()) 0.0 
            else passphraseEntropy(newKey.value)
          }
        }
        val passwordStrength = remember(newKey.value) {
          derivedStateOf {
            if (newKey.value.isEmpty()) PassphraseStrength.REJECTED
            else PassphraseStrength.check(newKey.value)
          }
        }
        
        val onClickUpdate = {
          // Don't do things concurrently. Shouldn't be here concurrently, just in case
          if (!progressIndicator.value) {
            val currentEntropy = entropy.value
            val currentStrength = passwordStrength.value
            val entropyFormatted = "%.1f".format(currentEntropy)
            
            val proceedWithChange = {
              if (currentKey.value == "") {
                if (useKeychain.value)
                  encryptDatabaseSavedAlert(onConfirmEncrypt)
                else
                  encryptDatabaseAlert(onConfirmEncrypt)
              } else {
                if (useKeychain.value)
                  changeDatabaseKeySavedAlert(onConfirmEncrypt)
                else
                  changeDatabaseKeyAlert(onConfirmEncrypt)
              }
            }
            
            when (currentStrength) {
              PassphraseStrength.REJECTED -> {
                // Weak passwords (<60 bits) are rejected - do nothing
                AlertManager.shared.showAlertMsg(
                  title = generalGetString(MR.strings.weak_passphrase_title),
                  text = generalGetString(MR.strings.passphrase_strength_rejected).format(entropyFormatted)
                )
              }
              PassphraseStrength.ACCEPTABLE -> {
                // 60-80 bits: Show warning but allow
                AlertManager.shared.showAlertDialogStacked(
                  title = generalGetString(MR.strings.medium_passphrase_title),
                  text = generalGetString(MR.strings.passphrase_strength_acceptable).format(entropyFormatted),
                  confirmText = generalGetString(MR.strings.continue_anyway),
                  onConfirm = onConfirmEncrypt, // Proceed directly without another dialog
                  dismissText = generalGetString(MR.strings.cancel_verb)
                )
              }
              PassphraseStrength.SECURE, PassphraseStrength.PARANOID -> {
                // Strong password - proceed directly
                proceedWithChange()
              }
            }
          }
        }
        
        // Reject weak passwords - disable button for REJECTED strength
        val isPasswordRejected = passwordStrength.value == PassphraseStrength.REJECTED && newKey.value.isNotEmpty()
        val disabled = currentKey.value == newKey.value ||
            newKey.value != confirmNewKey.value ||
            newKey.value.isEmpty() ||
            !validKey(currentKey.value) ||
            !validKey(newKey.value) ||
            !meetsMinLength ||
            isPasswordRejected ||
            progressIndicator.value

        PassphraseField(
          confirmNewKey,
          generalGetString(MR.strings.confirm_new_passphrase),
          modifier = Modifier.padding(horizontal = DEFAULT_PADDING),
          isValid = { confirmNewKey.value == "" || newKey.value == confirmNewKey.value },
          keyboardActions = KeyboardActions(onDone = {
            if (!disabled) onClickUpdate()
            defaultKeyboardAction(ImeAction.Done)
          }),
        )
        
        // Show real-time password strength feedback with color coding
        if (newKey.value.isEmpty()) {
          Text(
            generalGetString(MR.strings.passphrase_minimum_length_hint),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.secondary,
            modifier = Modifier.padding(horizontal = DEFAULT_PADDING).padding(top = 8.dp)
          )
        } else {
          val entropyFormatted = "%.1f bits".format(entropy.value)
          val strengthMessage = when (passwordStrength.value) {
            PassphraseStrength.REJECTED -> generalGetString(MR.strings.passphrase_strength_rejected).format(entropyFormatted)
            PassphraseStrength.ACCEPTABLE -> generalGetString(MR.strings.passphrase_strength_acceptable).format(entropyFormatted)
            PassphraseStrength.SECURE -> generalGetString(MR.strings.passphrase_strength_secure).format(entropyFormatted)
            PassphraseStrength.PARANOID -> generalGetString(MR.strings.passphrase_strength_paranoid).format(entropyFormatted)
          }
          Text(
            strengthMessage,
            style = MaterialTheme.typography.caption,
            color = passwordStrength.value.color,
            modifier = Modifier.padding(horizontal = DEFAULT_PADDING).padding(top = 8.dp)
          )
        }

        SectionItemViewSpaceBetween(onClickUpdate, disabled = disabled, minHeight = TextFieldDefaults.MinHeight) {
          Text(generalGetString(if (migration) MR.strings.set_passphrase else MR.strings.update_database_passphrase), color = if (disabled) MaterialTheme.colors.secondary else MaterialTheme.colors.primary)
        }
      }

      Column {
        DatabaseEncryptionFooter(useKeychain, chatDbEncrypted, storedKey, initialRandomDBPassphrase, migration)
      }
      SectionBottomSpacer()
    }
  }
  if (migration) {
    Column(Modifier.fillMaxWidth()) {
      Layout()
    }
  } else {
    Scaffold(
      topBar = {
        TopAppBar(
          backgroundColor = MaterialTheme.colors.background,
          elevation = 0.dp,
          contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 24.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = { ModalManager.start.closeModal() }) {
              Icon(
                painter = painterResource(MR.images.ic_arrow_back_ios_new),
                contentDescription = stringResource(MR.strings.back),
                tint = MaterialTheme.colors.onBackground
              )
            }
            Text(
              text = stringResource(MR.strings.database_passphrase),
              fontSize = 18.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colors.onBackground
            )
          }
        }
      },
      backgroundColor = MaterialTheme.colors.background,
      bottomBar = {}
    ) { paddingValues ->
      val showCurrent = remember { mutableStateOf(false) }
      val showNew = remember { mutableStateOf(false) }
      val showConfirm = remember { mutableStateOf(false) }
      var newFocused by remember { mutableStateOf(false) }
      var confirmFocused by remember { mutableStateOf(false) }

      val meetsMinLength = meetsMinimumLength(newKey.value)
      val entropy = remember(newKey.value) {
        derivedStateOf {
          if (newKey.value.isEmpty()) 0.0
          else passphraseEntropy(newKey.value)
        }
      }
      val passwordStrength = remember(newKey.value) {
        derivedStateOf {
          if (newKey.value.isEmpty()) PassphraseStrength.REJECTED
          else PassphraseStrength.check(newKey.value)
        }
      }
      val isPasswordRejected = passwordStrength.value == PassphraseStrength.REJECTED && newKey.value.isNotEmpty()
      val disabled = currentKey.value == newKey.value ||
          newKey.value != confirmNewKey.value ||
          newKey.value.isEmpty() ||
          !validKey(currentKey.value) ||
          !validKey(newKey.value) ||
          !meetsMinLength ||
          isPasswordRejected ||
          progressIndicator.value

      val onClickUpdate = {
        if (!progressIndicator.value) {
          val entropyFormatted = "%.1f".format(entropy.value)
          val proceedWithChange = {
            if (currentKey.value == "") {
              if (useKeychain.value) encryptDatabaseSavedAlert(onConfirmEncrypt) else encryptDatabaseAlert(onConfirmEncrypt)
            } else {
              if (useKeychain.value) changeDatabaseKeySavedAlert(onConfirmEncrypt) else changeDatabaseKeyAlert(onConfirmEncrypt)
            }
          }
          when (passwordStrength.value) {
            PassphraseStrength.REJECTED -> {
              AlertManager.shared.showAlertMsg(
                title = generalGetString(MR.strings.weak_passphrase_title),
                text = generalGetString(MR.strings.passphrase_strength_rejected).format(entropyFormatted)
              )
            }
            PassphraseStrength.ACCEPTABLE -> {
              AlertManager.shared.showAlertDialogStacked(
                title = generalGetString(MR.strings.medium_passphrase_title),
                text = generalGetString(MR.strings.passphrase_strength_acceptable).format(entropyFormatted),
                confirmText = generalGetString(MR.strings.continue_anyway),
                onConfirm = onConfirmEncrypt,
                dismissText = generalGetString(MR.strings.cancel_verb)
              )
            }
            PassphraseStrength.SECURE, PassphraseStrength.PARANOID -> proceedWithChange()
          }
        }
      }

      val showStrengthIndicator = (newFocused || confirmFocused) && newKey.value.isNotEmpty()

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 24.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Spacer(Modifier.height(24.dp))
        Icon(
          painter = painterResource(MR.images.ic_unlock),
          contentDescription = null,
          modifier = Modifier
            .size(24.dp)
            .align(Alignment.CenterHorizontally),
          tint = MaterialTheme.colors.onSurface
        )
        Spacer(Modifier.height(16.dp))
        Text(
          text = generalGetString(MR.strings.database_passphrase),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          fontFamily = Manrope,
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = generalGetString(MR.strings.database_passphrase_will_be_updated),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          fontFamily = DMSans,
          fontSize = 14.sp,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colors.secondary
        )
        Spacer(Modifier.height(24.dp))

        if (!initialRandomDBPassphrase.value && chatDbEncrypted == true) {
          ShredgramInputField(
            value = currentKey.value,
            onValueChange = { currentKey.value = it },
            placeholder = generalGetString(MR.strings.current_passphrase),
            isPassword = true,
            showPassword = showCurrent.value,
            onToggleVisibility = { showCurrent.value = !showCurrent.value }
          )
          Spacer(Modifier.height(12.dp))
        }

        ShredgramInputField(
          value = newKey.value,
          onValueChange = { newKey.value = it },
          placeholder = generalGetString(MR.strings.new_passphrase),
          isPassword = true,
          showPassword = showNew.value,
          onToggleVisibility = { showNew.value = !showNew.value },
          onFocusChange = { newFocused = it }
        )
        Spacer(Modifier.height(12.dp))
        ShredgramInputField(
          value = confirmNewKey.value,
          onValueChange = { confirmNewKey.value = it },
          placeholder = generalGetString(MR.strings.confirm_new_passphrase),
          isPassword = true,
          showPassword = showConfirm.value,
          onToggleVisibility = { showConfirm.value = !showConfirm.value },
          onFocusChange = { confirmFocused = it }
        )

        Spacer(Modifier.height(14.dp))
        if (showStrengthIndicator) {
          PasswordStrengthIndicator(
            strength = passwordStrength.value,
            modifier = Modifier.fillMaxWidth()
          )
        }
        Spacer(Modifier.height(24.dp))

        Button(
          onClick = onClickUpdate,
          enabled = !disabled,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          shape = RoundedCornerShape(360.dp),
          colors = ButtonDefaults.buttonColors(
            backgroundColor = SignalBlue,
            contentColor = Color.White,
            disabledBackgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
            disabledContentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
          ),
          elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
          Text(
            text = generalGetString(MR.strings.update_database_passphrase),
            fontFamily = DMSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
          )
        }

        Spacer(Modifier.height(16.dp))
        DatabaseEncryptionFooter(useKeychain, chatDbEncrypted, storedKey, initialRandomDBPassphrase, migration)
        SectionBottomSpacer()
      }
    }
  }
}

expect fun encryptDatabaseSavedAlert(onConfirm: () -> Unit)

fun encryptDatabaseAlert(onConfirm: () -> Unit) {
  showCustomEncryptionAlert(onConfirm)
}

fun showCustomEncryptionAlert(onConfirm: () -> Unit) {
  AlertManager.shared.showAlert {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF000000).copy(alpha = 0.75f))
        .clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null,
          onClick = { /* Prevent clicks behind */ }
        ),
      contentAlignment = Alignment.Center
    ) {
      val screenWidth = maxWidth
      val dialogWidth = (screenWidth * 0.92f).coerceAtMost(400.dp).coerceAtLeast(320.dp)
      
      Card(
        modifier = Modifier
          .width(dialogWidth)
          .wrapContentHeight()
          .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        elevation = 8.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
          // Warning Icon
          Icon(
            painter = painterResource(MR.images.ic_warning),
            contentDescription = "Warning",
            modifier = Modifier.size(40.dp),
            tint = Color(0xFFF39C12) // Orange warning color
          )
          
          Spacer(Modifier.height(12.dp))
          
          // Title
          Text(
            text = "Important Reminder",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center
          )
          
          Spacer(Modifier.height(8.dp))
          
          // Description
          Text(
            text = "Ensure you remember your passphrase. Without it, you won't be able to access your encrypted data if needed.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
          )
          
          Spacer(Modifier.height(24.dp))
          
          // Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Cancel Button - no background, minimal padding
            Box(
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = { AlertManager.shared.hideAlert() }
                ),
              contentAlignment = Alignment.CenterStart
            ) {
              Text(
                text = "Cancel",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(start = 4.dp)
              )
            }
            
            // Encrypt Button - fully rounded
            Button(
              onClick = {
                onConfirm()
                AlertManager.shared.hideAlert()
              },
              modifier = Modifier
                .weight(1f)
                .height(44.dp),
              shape = RoundedCornerShape(360.dp),
              colors = ButtonDefaults.buttonColors(
                backgroundColor = SignalBlue,
                contentColor = Color.White
              ),
              elevation = ButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
              )
            ) {
              Text(
                text = "Encrypt",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }
  }
}

fun showDatabaseEncryptedSuccessAlert() {
  // Just set the state - the dialog is now rendered in App.kt outside the blurred content
  DatabaseEncryptionSuccessState.isShowing.value = true
}

expect fun changeDatabaseKeySavedAlert(onConfirm: () -> Unit)

fun changeDatabaseKeyAlert(onConfirm: () -> Unit) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.change_database_passphrase_question),
    text = generalGetString(MR.strings.database_passphrase_will_be_updated) + "\n" + storeSecurelyDanger(),
    confirmText = generalGetString(MR.strings.update_database),
    onConfirm = onConfirm,
    destructive = true,
  )
}

expect fun removePassphraseAlert(onConfirm: () -> Unit)

@Composable
expect fun SavePassphraseSetting(
  useKeychain: Boolean,
  initialRandomDBPassphrase: Boolean,
  storedKey: Boolean,
  minHeight: Dp = TextFieldDefaults.MinHeight,
  enabled: Boolean,
  smallPadding: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
)

@Composable
expect fun DatabaseEncryptionFooter(
  useKeychain: MutableState<Boolean>,
  chatDbEncrypted: Boolean?,
  storedKey: MutableState<Boolean>,
  initialRandomDBPassphrase: MutableState<Boolean>,
  migration: Boolean,
)

@Composable
fun ChatStoppedView() {
  SettingsActionItem(
    icon = painterResource(MR.images.ic_report_filled),
    text = stringResource(MR.strings.chat_is_stopped),
    iconColor = Color.Red,
  )
}

fun resetFormAfterEncryption(
  m: ChatModel,
  initialRandomDBPassphrase: MutableState<Boolean>,
  currentKey: MutableState<String>,
  newKey: MutableState<String>,
  confirmNewKey: MutableState<String>,
  storedKey: MutableState<Boolean>,
  stored: Boolean = false,
) {
  currentKey.value = ""
  newKey.value = ""
  confirmNewKey.value = ""
  storedKey.value = stored
  m.chatDbEncrypted.value = true
  initialRandomDBPassphrase.value = false
  m.controller.appPrefs.initialRandomDBPassphrase.set(false)
}

fun setUseKeychain(value: Boolean, useKeychain: MutableState<Boolean>, migration: Boolean) {
  useKeychain.value = value
  // Postpone it when migrating to the end of encryption process
  if (!migration) {
    appPrefs.storeDBPassphrase.set(value)
  }
}

private fun removePassphraseFromKeyChain(useKeychain: MutableState<Boolean>, storedKey: MutableState<Boolean>, migration: Boolean) {
  DatabaseUtils.ksDatabasePassword.remove()
  setUseKeychain(false, useKeychain, migration)
  storedKey.value = false
}

fun storeSecurelySaved() = generalGetString(MR.strings.store_passphrase_securely)

fun storeSecurelyDanger() = generalGetString(MR.strings.store_passphrase_securely_without_recover)

private fun operationEnded(m: ChatModel, progressIndicator: MutableState<Boolean>, alert: () -> Unit) {
  m.chatDbChanged.value = true
  progressIndicator.value = false
  alert.invoke()
}

@Composable
fun PassphraseField(
  key: MutableState<String>,
  placeholder: String,
  modifier: Modifier = Modifier,
  showStrength: Boolean = false,
  isValid: (String) -> Boolean,
  keyboardActions: KeyboardActions = KeyboardActions(),
  dependsOn: State<Any?>? = null,
  requestFocus: Boolean = false,
) {
  var valid by remember { mutableStateOf(validKey(key.value)) }
  var showKey by remember { mutableStateOf(false) }
  val icon = if (valid) {
    if (showKey) painterResource(MR.images.ic_visibility_off_filled) else painterResource(MR.images.ic_visibility_filled)
  } else painterResource(MR.images.ic_error)
  val iconColor = if (valid) {
    if (showStrength && key.value.isNotEmpty()) PassphraseStrength.check(key.value).color else MaterialTheme.colors.secondary
  } else Color.Red
  val keyboard = LocalSoftwareKeyboardController.current
  val keyboardOptions = KeyboardOptions(
    imeAction = if (keyboardActions.onNext != null) ImeAction.Next else ImeAction.Done,
    autoCorrect = false,
    keyboardType = KeyboardType.Password
  )
  val state = remember {
    mutableStateOf(TextFieldValue(key.value))
  }
  val enabled = true
  val colors = TextFieldDefaults.textFieldColors(
    backgroundColor = Color.Unspecified,
    textColor = MaterialTheme.colors.onBackground,
    focusedIndicatorColor = Color.Unspecified,
    unfocusedIndicatorColor = Color.Unspecified,
  )
  val color = MaterialTheme.colors.onBackground
  val shape = MaterialTheme.shapes.small.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize)
  val interactionSource = remember { MutableInteractionSource() }
  val focusRequester = remember { FocusRequester() }
  BasicTextField(
    value = state.value,
    modifier = modifier
      .fillMaxWidth()
      .background(colors.backgroundColor(enabled).value, shape)
      .indicatorLine(enabled, false, interactionSource, colors)
      .defaultMinSize(
        minWidth = TextFieldDefaults.MinWidth,
        minHeight = TextFieldDefaults.MinHeight
      )
      .focusRequester(focusRequester),
    onValueChange = {
      state.value = it
      key.value = it.text
      valid = isValid(it.text)
    },
    cursorBrush = SolidColor(colors.cursorColor(false).value),
    visualTransformation = if (showKey)
      VisualTransformation.None
    else
      VisualTransformation { TransformedText(AnnotatedString(it.text.map { "*" }.joinToString(separator = "")), OffsetMapping.Identity) },
    keyboardOptions = keyboardOptions,
    keyboardActions = KeyboardActions(onDone = {
      keyboard?.hide()
      keyboardActions.onDone?.invoke(this)
    }),
    singleLine = true,
    textStyle = TextStyle.Default.copy(
      color = color,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp
    ),
    interactionSource = interactionSource,
    decorationBox = @Composable { innerTextField ->
      TextFieldDefaults.TextFieldDecorationBox(
        value = state.value.text,
        innerTextField = innerTextField,
        placeholder = { Text(placeholder, color = MaterialTheme.colors.secondary) },
        singleLine = true,
        enabled = enabled,
        isError = !valid,
        trailingIcon = {
          IconButton({ showKey = !showKey }) {
            Icon(icon, null, tint = iconColor)
          }
        },
        interactionSource = interactionSource,
        contentPadding = TextFieldDefaults.textFieldWithLabelPadding(start = 0.dp, end = 0.dp),
        visualTransformation = VisualTransformation.None,
        colors = colors
      )
    }
  )
  LaunchedEffect(Unit) {
    if (requestFocus) {
      delay(200)
      focusRequester.requestFocus()
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { dependsOn?.value }
      .distinctUntilChanged()
      .collect {
        valid = isValid(state.value.text)
      }
  }
}

suspend fun encryptDatabase(
  currentKey: MutableState<String>,
  newKey: MutableState<String>,
  confirmNewKey: MutableState<String>,
  initialRandomDBPassphrase: MutableState<Boolean>,
  useKeychain: MutableState<Boolean>,
  storedKey: MutableState<Boolean>,
  progressIndicator: MutableState<Boolean>,
  migration: Boolean,
  showSuccessAlert: Boolean = true,
): Boolean {
  val m = ChatModel
  progressIndicator.value = true
  return try {
    appPrefs.encryptionStartedAt.set(Clock.System.now())
    if (!m.chatDbChanged.value) {
      m.controller.apiSaveAppSettings(AppSettings.current.prepareForExport())
    }
    var error = m.controller.apiStorageEncryption(currentKey.value, newKey.value)
    if (error.isStorageNoFileError()) {
      initChatController(useKey = currentKey.value)
      error = m.controller.apiStorageEncryption(currentKey.value, newKey.value)
    }
    appPrefs.encryptionStartedAt.set(null)
    val sqliteError = ((error as? ChatError.ChatErrorDatabase)?.databaseError as? DatabaseError.ErrorExport)?.sqliteError
    when {
      sqliteError is SQLiteError.ErrorNotADatabase -> {
        operationEnded(m, progressIndicator) {
          AlertManager.shared.showAlertMsg(
            generalGetString(MR.strings.wrong_passphrase_title),
            generalGetString(MR.strings.enter_correct_current_passphrase)
          )
        }
        false
      }
      error != null -> {
        operationEnded(m, progressIndicator) {
          AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_encrypting_database),
            "failed to set storage encryption: error ${error.string}"
          )
        }
        false
      }
      else -> {
        val new = newKey.value
        if (migration) {
          appPreferences.storeDBPassphrase.set(useKeychain.value)
        }
        resetFormAfterEncryption(m, initialRandomDBPassphrase, currentKey, newKey, confirmNewKey, storedKey, useKeychain.value)
        if (useKeychain.value) {
          DatabaseUtils.ksDatabasePassword.set(new)
        } else {
          removePassphraseFromKeyChain(useKeychain, storedKey, migration)
        }
        operationEnded(m, progressIndicator) {
          if (showSuccessAlert) {
            showDatabaseEncryptedSuccessAlert()
          }
        }
        true
      }
    }
  } catch (e: Exception) {
    operationEnded(m, progressIndicator) {
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_encrypting_database), e.stackTraceToString())
    }
    false
  }
}

private fun ChatError?.isStorageNoFileError(): Boolean =
  ((this as? ChatError.ChatErrorDatabase)?.databaseError as? DatabaseError.ErrorNoFile) != null

/**
 * Flexible password entropy calculation.
 * 
 * Automatically detects character types used in the password and calculates
 * entropy based on: entropy = length × log2(characterSetSize)
 * 
 * Character sets:
 * - Digits (0-9): 10 characters
 * - Lowercase letters (a-z): 26 characters
 * - Uppercase letters (A-Z): 26 characters
 * - Symbols/Special chars: 32 common ASCII symbols
 * 
 * This does NOT enforce any character requirements - it simply evaluates
 * what the user has provided.
 * 
 * @param s The password string to evaluate
 * @return Entropy in bits
 */
fun passphraseEntropy(s: String): Double {
  if (s.isEmpty()) return 0.0
  
  var hasDigits = false
  var hasUppercase = false
  var hasLowercase = false
  var hasSymbols = false
  
  // Detect which character types are present
  for (c in s) {
    when {
      c.isDigit() -> hasDigits = true
      c.isUpperCase() -> hasUppercase = true
      c.isLowerCase() -> hasLowercase = true
      !c.isLetterOrDigit() && c.isASCII() -> hasSymbols = true
    }
  }
  
  // Calculate total character pool size based on what's used
  val poolSize = (if (hasDigits) 10 else 0) + 
                 (if (hasUppercase) 26 else 0) + 
                 (if (hasLowercase) 26 else 0) + 
                 (if (hasSymbols) 32 else 0)
  
  // If pool size is 0, assume at least minimal charset
  val effectivePoolSize = if (poolSize > 0) poolSize else 10
  
  // Entropy = length × log2(pool size)
  return s.length * log2(effectivePoolSize.toDouble())
}

/**
 * Password strength categories based on entropy bits.
 * 
 * Thresholds:
 * - < 40 bits: VERY_WEAK (e.g., "password", "12345678")
 * - 40-59 bits: WEAK (e.g., "Password1", "mypassword")
 * - 60-79 bits: REASONABLE (e.g., "MyPassword123", "correct-horse")
 * - ≥ 80 bits: STRONG (e.g., "MyP@ssw0rd!2024", long passphrases)
 */
enum class PassphraseStrength(val color: Color) {
  REJECTED(Color.Red),           // 0-59 bits: Weak - rejected
  ACCEPTABLE(WarningOrange),     // 60-80 bits: Acceptable - allowed but discouraged
  SECURE(SimplexGreen),          // 80-100 bits: Secure - allowed
  PARANOID(Color(0xFF9C27B0));   // >100 bits: Paranoid level

  companion object {
    fun check(s: String): PassphraseStrength {
      val entropy = passphraseEntropy(s)
      return when {
        entropy > 100 -> PARANOID     // >100 bits: Paranoid level
        entropy > 80 -> SECURE        // 80-100 bits: Secure
        entropy >= 60 -> ACCEPTABLE   // 60-80 bits: Acceptable
        else -> REJECTED              // 0-59 bits: Rejected
      }
    }
    
    fun getEntropyBits(s: String): Double = passphraseEntropy(s)
  }
}

/**
 * Estimates brute-force attack time in a safe, educational manner.
 * 
 * IMPORTANT: This uses a conservative guess rate of 1 million guesses/second
 * for educational purposes. This is intentionally LOW to provide a safe,
 * general estimate that doesn't assist malicious use.
 * 
 * Real-world attack speeds vary greatly based on:
 * - Hash algorithm used
 * - Hardware (CPU vs GPU vs specialized equipment)
 * - Network vs offline attack
 * - Additional security measures
 * 
 * @param entropy Password entropy in bits
 * @return Human-readable time estimate
 */
fun estimateBruteForceTime(entropy: Double): String {
  // Conservative estimate: 1 million guesses per second
  // This is educational and intentionally modest
  val guessesPerSecond = 1_000_000.0
  
  // Total possible combinations
  val totalCombinations = Math.pow(2.0, entropy)
  
  // Average case: attacker finds password after trying half the keyspace
  val secondsToBreak = totalCombinations / (2 * guessesPerSecond)
  
  return when {
    secondsToBreak < 1 -> "instantly"
    secondsToBreak < 60 -> "less than a minute"
    secondsToBreak < 3600 -> "${(secondsToBreak / 60).toInt()} minutes"
    secondsToBreak < 86400 -> "${(secondsToBreak / 3600).toInt()} hours"
    secondsToBreak < 604800 -> "${(secondsToBreak / 86400).toInt()} days"
    secondsToBreak < 2592000 -> "${(secondsToBreak / 604800).toInt()} weeks"
    secondsToBreak < 31536000 -> "${(secondsToBreak / 2592000).toInt()} months"
    secondsToBreak < 315360000 -> "${(secondsToBreak / 31536000).toInt()} years"
    secondsToBreak < 31536000000 -> "${(secondsToBreak / 31536000 / 1000).toInt()} thousand years"
    else -> "millions of years"
  }
}

/**
 * Validates that a password key contains only valid characters.
 * 
 * Requirements:
 * - No whitespace characters
 * - Only ASCII printable characters (code 32-126)
 * 
 * This does NOT enforce character type requirements (uppercase, lowercase, etc.)
 * Password strength is evaluated separately through entropy calculation.
 */
fun validKey(s: String): Boolean {
  for (c in s) {
    if (c.isWhitespace() || !c.isASCII()) {
      return false
    }
  }
  return true
}

/**
 * Checks if password meets minimum length requirement.
 * No character type requirements - only length matters for the basic check.
 * 
 * @param s Password string
 * @return true if password is at least 8 characters long
 */
fun meetsMinimumLength(s: String): Boolean {
  return s.length >= 8
}

private fun Char.isASCII() = code in 32..126

@Preview
@Composable
fun PreviewDatabaseEncryptionLayout() {
  SimpleXTheme {
    DatabaseEncryptionLayout(
      useKeychain = remember { mutableStateOf(true) },
      chatDbEncrypted = true,
      currentKey = remember { mutableStateOf("") },
      newKey = remember { mutableStateOf("") },
      confirmNewKey = remember { mutableStateOf("") },
      storedKey = remember { mutableStateOf(true) },
      initialRandomDBPassphrase = remember { mutableStateOf(true) },
      progressIndicator = remember { mutableStateOf(false) },
      migration = false,
      onConfirmEncrypt = {},
    )
  }
}
