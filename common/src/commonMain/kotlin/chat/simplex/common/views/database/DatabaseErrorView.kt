package chat.simplex.common.views.database

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.AppPreferences
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.localauth.PasscodeView
import chat.simplex.common.views.usersettings.AppVersionText
import chat.simplex.common.views.usersettings.SettingsActionItem
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path

private val ElectricBlue500 = Color(0xFF1F4CFF)
private val OnSurfaceVariant = Color(0xFF3D4042)
private val BorderElevated1 = Color(0xFFCECFD0)
private val DarkCharcoal400 = Color(0xFF868889)

private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f
private val font12 = 12.sp
private val font14 = 14.sp
private val font30 = 30.sp

@Composable
fun DatabaseErrorView(
  chatDbStatus: State<DBMigrationResult?>,
  appPreferences: AppPreferences,
) {
  val progressIndicator = remember { mutableStateOf(false) }
  val dbKey = remember { mutableStateOf("") }
  var storedDBKey by remember { mutableStateOf(DatabaseUtils.ksDatabasePassword.get()) }
  var useKeychain by remember { mutableStateOf(appPreferences.storeDBPassphrase.get()) }
  val useYubiKey = remember { mutableStateOf(appPreferences.useYubiKeyForDB.get()) }
  val restoreDbFromBackup = remember { mutableStateOf(shouldShowRestoreDbButton(appPreferences)) }
  
  // YubiKey unlock states
  val yubiKeyProcessing = remember { mutableStateOf(false) }
  val yubiKeyError = remember { mutableStateOf<String?>(null) }
  val showTapKeyScreen = remember { mutableStateOf(false) }
  
  // PIN/PUK lockout states
  val isPinLocked = remember { mutableStateOf(YubiKeyBridge.isPinLocked()) }
  val isAppLocked = remember { mutableStateOf(YubiKeyBridge.isAppLocked()) }
  val showPukEntry = remember { mutableStateOf(isPinLocked.value) }
  val hasPukHash = remember { mutableStateOf(appPreferences.yubiKeyPukHash.get() != null) }

  fun callRunChat(confirmMigrations: MigrationConfirmation? = null) {
    val useKey = if (useKeychain || useYubiKey.value) null else dbKey.value
    runChat(useKey, confirmMigrations, chatDbStatus, progressIndicator)
  }

  fun saveAndRunChatOnClick() {
    DatabaseUtils.ksDatabasePassword.set(dbKey.value)
    storedDBKey = dbKey.value
    appPreferences.storeDBPassphrase.set(true)
    useKeychain = true
    appPreferences.initialRandomDBPassphrase.set(false)
    callRunChat()
  }
  
  fun unlockWithYubiKeyDMK(dmk: String) {
    yubiKeyProcessing.value = false
    runChat(dmk, null, chatDbStatus, progressIndicator)
  }
  
  @Composable
  fun DatabaseErrorDetails(content: @Composable ColumnScope.() -> Unit) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      content = content
    )
  }

  @Composable
  fun FileNameText(dbFile: String) {
    Text(
      String.format(generalGetString(MR.strings.file_with_path), dbFile.split("/").lastOrNull() ?: dbFile),
      fontFamily = DMSans,
      fontSize = font12,
      color = OnSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }

  @Composable
  fun MigrationsText(ms: List<String>) {
    Text(
      String.format(generalGetString(MR.strings.database_migrations), ms.joinToString(", ")),
      fontFamily = DMSans,
      fontSize = font12,
      color = OnSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }
  
  // Check if we should show YubiKey unlock for ErrorNotADatabase
  val showYubiKeyUnlock = useYubiKey.value && chatDbStatus.value is DBMigrationResult.ErrorNotADatabase
  
  // YubiKey PIN/PUK state - exactly like LocalAuthView
  val yubiKeyPin = remember { mutableStateOf("") }
  val yubiKeyPuk = remember { mutableStateOf("") }
  val allowToReact = remember { mutableStateOf(true) }
  
  // Build display reason with error if applicable
  val displayReason = remember(yubiKeyError.value, yubiKeyProcessing.value, showPukEntry.value, isAppLocked.value) {
    when {
      isAppLocked.value -> "App is permanently locked. You have lost access."
      yubiKeyError.value != null -> yubiKeyError.value
      yubiKeyProcessing.value -> if (showPukEntry.value) "Verifying PUK..." else "Hold your YubiKey steady..."
      showPukEntry.value -> "PIN is locked. Enter your PUK to unlock."
      else -> "Enter your PIN and tap your YubiKey"
    }
  }
  
  // Watch for YubiKey tap - auto-submit when PIN/PUK is entered and YubiKey detected
  // IMPORTANT: Use independent CoroutineScope to prevent cancellation when composition changes
  LaunchedEffect(ChatModel.yubiKeyDetected.value) {
    if (ChatModel.yubiKeyDetected.value && showYubiKeyUnlock && allowToReact.value && showTapKeyScreen.value) {
      // User tapped YubiKey on the dedicated tap screen — retrieve stored PIN and unlock
      val pin = ChatModel.secureYubiKeyPin.usePinString { it } ?: ""
      if (pin.length >= 6) {
        showTapKeyScreen.value = false
        ChatModel.yubiKeyDetected.value = false
        allowToReact.value = false
        yubiKeyProcessing.value = true
        yubiKeyError.value = null
        CoroutineScope(Dispatchers.Default).launch {
          val storedPuk = ChatModel.secureYubiKeyPuk.usePinString { it }
          if (storedPuk != null) {
            val unblockResult = YubiKeyBridge.unblockAndVerifyPin(storedPuk, pin)
            ChatModel.secureYubiKeyPuk.clear()
            if (unblockResult.isFailure) {
              allowToReact.value = true
              yubiKeyProcessing.value = false
              yubiKeyError.value = unblockResult.exceptionOrNull()?.message ?: "Unblock failed"
              return@launch
            }
          } else {
            ChatModel.secureYubiKeyPuk.clear()
          }
          val result = YubiKeyBridge.unlockDatabase(pin)
          if (result.isSuccess) {
            unlockWithYubiKeyDMK(result.getOrThrow())
          } else {
            allowToReact.value = true
            yubiKeyProcessing.value = false
            val errorMsg = result.exceptionOrNull()?.message ?: "Invalid PIN or wrong YubiKey"
            if (errorMsg.startsWith("PIN_LOCKED:")) {
              isPinLocked.value = true
              showPukEntry.value = true
              yubiKeyError.value = errorMsg.removePrefix("PIN_LOCKED:")
            } else if (errorMsg.startsWith("APP_LOCKED:")) {
              isAppLocked.value = true
              yubiKeyError.value = errorMsg.removePrefix("APP_LOCKED:")
            } else {
              yubiKeyError.value = errorMsg
            }
          }
        }
        return@LaunchedEffect
      }
    }
    if (ChatModel.yubiKeyDetected.value && showYubiKeyUnlock && allowToReact.value) {
      // Check if we're in PUK entry mode
      if (showPukEntry.value) {
        // Auto-submit PUK when YubiKey is tapped
        val puk = yubiKeyPuk.value
        if (puk.length >= 6) {
          ChatModel.yubiKeyDetected.value = false
          yubiKeyPuk.value = "" // Clear UI
          allowToReact.value = false
          yubiKeyProcessing.value = true
          yubiKeyError.value = "Verifying PUK..."
          
          CoroutineScope(Dispatchers.Default).launch {
            try {
              val result = YubiKeyBridge.verifyPukAndUnlockPin(puk)
              if (result.isSuccess && result.getOrNull() == true) {
                // Store PUK for YubiKey unblock
                ChatModel.secureYubiKeyPuk.set(puk)
                isPinLocked.value = false
                showPukEntry.value = false
                allowToReact.value = true
                yubiKeyProcessing.value = false
                yubiKeyError.value = "PUK verified! Now enter your PIN and tap YubiKey."
              } else {
                allowToReact.value = true
                yubiKeyProcessing.value = false
                val errorMsg = result.exceptionOrNull()?.message ?: "Invalid PUK"
                if (errorMsg.startsWith("APP_LOCKED:")) {
                  isAppLocked.value = true
                  yubiKeyError.value = errorMsg.removePrefix("APP_LOCKED:")
                } else {
                  yubiKeyError.value = errorMsg
                }
              }
            } catch (e: Exception) {
              allowToReact.value = true
              yubiKeyProcessing.value = false
              yubiKeyError.value = "Error: ${e.message ?: "Unknown error"}"
            }
          }
        } else {
          ChatModel.yubiKeyDetected.value = false
          yubiKeyError.value = "Enter PUK first (6-8 digits)"
        }
      } else {
        // Normal PIN entry mode
        val pin = if (yubiKeyPin.value.length >= 6) {
          yubiKeyPin.value
        } else {
          ChatModel.secureYubiKeyPin.usePinString { it } ?: ""
        }
        
        if (pin.length >= 6) {
          ChatModel.yubiKeyDetected.value = false
          ChatModel.secureYubiKeyPin.set(pin)
          allowToReact.value = false
          yubiKeyProcessing.value = true
          yubiKeyError.value = null
          
          CoroutineScope(Dispatchers.Default).launch {
            val storedPuk = ChatModel.secureYubiKeyPuk.usePinString { it }
            
            if (storedPuk != null) {
              val unblockResult = YubiKeyBridge.unblockAndVerifyPin(storedPuk, pin)
              ChatModel.secureYubiKeyPuk.clear()
              if (unblockResult.isFailure) {
                allowToReact.value = true
                yubiKeyProcessing.value = false
                yubiKeyError.value = unblockResult.exceptionOrNull()?.message ?: "Unblock failed"
                yubiKeyPin.value = ""
                return@launch
              }
            } else {
              ChatModel.secureYubiKeyPuk.clear()
            }
            
            val result = YubiKeyBridge.unlockDatabase(pin)
            
            if (result.isSuccess) {
              unlockWithYubiKeyDMK(result.getOrThrow())
            } else {
              allowToReact.value = true
              yubiKeyProcessing.value = false
              val errorMsg = result.exceptionOrNull()?.message ?: "Invalid PIN or wrong YubiKey"
              
              if (errorMsg.startsWith("PIN_LOCKED:")) {
                isPinLocked.value = true
                showPukEntry.value = true
                yubiKeyError.value = errorMsg.removePrefix("PIN_LOCKED:")
              } else if (errorMsg.startsWith("APP_LOCKED:")) {
                isAppLocked.value = true
                yubiKeyError.value = errorMsg.removePrefix("APP_LOCKED:")
              } else {
                yubiKeyError.value = errorMsg
              }
              yubiKeyPin.value = ""
            }
          }
        } else {
          ChatModel.yubiKeyDetected.value = false
          yubiKeyError.value = "Enter PIN first (6-8 digits)"
        }
      }
    }
  }
  
  if (showYubiKeyUnlock) {
    // Check if app is permanently locked
    if (isAppLocked.value) {
      // Show permanent lockout message
      PasscodeView(
        passcode = yubiKeyPin,
        title = "Access Denied",
        reason = "App is permanently locked after too many failed PUK attempts.\nYou have lost access to this database.",
        submitLabel = "",
        submitEnabled = { false },
        buttonsEnabled = remember { mutableStateOf(false) },
        submit = {},
        cancel = {}
      )
    } else if (showPukEntry.value) {
      // PUK entry mode - PIN is locked
      if (!hasPukHash.value) {
        // No PUK hash stored - enrolled before this feature was added
        PasscodeView(
          passcode = yubiKeyPuk,
          title = "PUK Not Available",
          reason = "Your YubiKey was enrolled before PUK recovery was added.\n\nYou need to factory reset your YubiKey and re-enroll to use PUK recovery.\n\nWithout re-enrollment, access cannot be recovered.",
          submitLabel = "",
          submitEnabled = { false },
          buttonsEnabled = remember { mutableStateOf(false) },
          submit = {},
          cancel = {}
        )
      } else {
        PasscodeView(
          passcode = yubiKeyPuk,
          title = "Enter PUK",
          reason = displayReason,
          submitLabel = "Unlock PIN",
          submitEnabled = { it.length >= 6 },  // PUK is 6-8 digits
          buttonsEnabled = allowToReact,
          submit = {
            val puk = yubiKeyPuk.value
            
            if (puk.length < 6) {
              yubiKeyError.value = "PUK must be at least 6 digits"
              return@PasscodeView
            }
            
            yubiKeyPuk.value = "" // Clear UI state
            allowToReact.value = false
            yubiKeyProcessing.value = true
            yubiKeyError.value = "Verifying PUK..."
            
            // Run verification directly (it's synchronous and fast)
            try {
              val result = YubiKeyBridge.verifyPukAndUnlockPin(puk)
              
              if (result.isSuccess && result.getOrNull() == true) {
                // PUK verified - store PUK temporarily to unblock YubiKey when PIN is entered
                ChatModel.secureYubiKeyPuk.set(puk)
                
                // Clear app lock state, go back to PIN entry
                isPinLocked.value = false
                showPukEntry.value = false
                allowToReact.value = true
                yubiKeyProcessing.value = false
                yubiKeyError.value = "PUK verified! Now enter your PIN and tap YubiKey."
              } else {
                allowToReact.value = true
                yubiKeyProcessing.value = false
                val errorMsg = result.exceptionOrNull()?.message ?: "Invalid PUK"
                
                // Check if app is now permanently locked
                if (errorMsg.startsWith("APP_LOCKED:")) {
                  isAppLocked.value = true
                  yubiKeyError.value = errorMsg.removePrefix("APP_LOCKED:")
                } else {
                  yubiKeyError.value = errorMsg
                }
              }
            } catch (e: Exception) {
              allowToReact.value = true
              yubiKeyProcessing.value = false
              yubiKeyError.value = "Error: ${e.message ?: "Unknown error"}"
            }
          },
          cancel = {
            // Can't cancel on unlock screen - do nothing
          }
        )
      }
    } else if (showTapKeyScreen.value) {
      // Dedicated "Tap YubiKey" screen - shown after PIN is entered
      YubiKeyTapScreen(
        isProcessing = yubiKeyProcessing.value,
        errorMessage = yubiKeyError.value,
        onBack = {
          showTapKeyScreen.value = false
          ChatModel.secureYubiKeyPin.clear()
          yubiKeyError.value = null
        }
      )
    } else {
      // Normal PIN entry mode
      PasscodeView(
        passcode = yubiKeyPin,
        title = "Unlock with YubiKey",
        reason = displayReason,
        submitLabel = "Unlock",
        submitEnabled = { it.length >= 6 },  // YubiKey PIN is 6-8 digits
        buttonsEnabled = allowToReact,
        submit = {
          // SECURITY: Store the PIN in secure container and prompt user to tap YubiKey
          val pin = yubiKeyPin.value
          ChatModel.secureYubiKeyPin.set(pin)
          yubiKeyPin.value = "" // Clear UI state immediately
          
          // Check if YubiKey is already detected
          if (ChatModel.yubiKeyDetected.value) {
            // YubiKey already tapped, verify immediately
            allowToReact.value = false
            yubiKeyProcessing.value = true
            yubiKeyError.value = null
            
            CoroutineScope(Dispatchers.Default).launch {
              ChatModel.yubiKeyDetected.value = false
              
              val storedPuk = ChatModel.secureYubiKeyPuk.usePinString { it }
              
              if (storedPuk != null) {
                val unblockResult = YubiKeyBridge.unblockAndVerifyPin(storedPuk, pin)
                ChatModel.secureYubiKeyPuk.clear()
                if (unblockResult.isFailure) {
                  allowToReact.value = true
                  yubiKeyProcessing.value = false
                  yubiKeyError.value = unblockResult.exceptionOrNull()?.message ?: "Unblock failed"
                  yubiKeyPin.value = ""
                  return@launch
                }
              } else {
                ChatModel.secureYubiKeyPuk.clear()
              }
              
              val result = YubiKeyBridge.unlockDatabase(pin)
              
              if (result.isSuccess) {
                unlockWithYubiKeyDMK(result.getOrThrow())
              } else {
                allowToReact.value = true
                yubiKeyProcessing.value = false
                val errorMsg = result.exceptionOrNull()?.message ?: "Invalid PIN or wrong YubiKey"
                
                if (errorMsg.startsWith("PIN_LOCKED:")) {
                  isPinLocked.value = true
                  showPukEntry.value = true
                  yubiKeyError.value = errorMsg.removePrefix("PIN_LOCKED:")
                } else if (errorMsg.startsWith("APP_LOCKED:")) {
                  isAppLocked.value = true
                  yubiKeyError.value = errorMsg.removePrefix("APP_LOCKED:")
                } else {
                  yubiKeyError.value = errorMsg
                }
                yubiKeyPin.value = ""
              }
            }
          } else {
            // No YubiKey detected yet - navigate to the tap key screen
            showTapKeyScreen.value = true
          }
        },
        cancel = {
          // Can't cancel on unlock screen - do nothing
        }
      )
    }
  } else {
    val buttonEnabled = validKey(dbKey.value) && !progressIndicator.value
    var showPassword by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
      delay(200L)
      try {
        focusRequester.requestFocus()
      } catch (_: IllegalStateException) {
        // FocusRequester not attached — status is not ErrorNotADatabase
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colors.background)
        .statusBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Image(
            painter = painterResource(MR.images.ic_logo),
            contentDescription = "App Logo"
          )
        }

        Icon(
          painter = painterResource(MR.images.ic_lock),
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colors.onSurface
        )

        Spacer(Modifier.height(16.dp))

        when (val status = chatDbStatus.value) {
          is DBMigrationResult.ErrorNotADatabase -> {
            val isWrongKey = useKeychain && !storedDBKey.isNullOrEmpty()
            val title = if (isWrongKey) "Wrong passphrase" else "Unlock your database"
            val subtitle = if (isWrongKey)
              "The saved passphrase did not match. Please enter the correct one."
            else
              generalGetString(MR.strings.database_passphrase_is_required)

            Text(
              text = title,
              fontFamily = Manrope,
              fontSize = font30,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colors.onSurface,
              textAlign = TextAlign.Center,
              lineHeight = (font30.value * lineHeightHeadlineS).sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
              text = subtitle,
              fontFamily = DMSans,
              fontSize = font14,
              fontWeight = FontWeight.Normal,
              color = OnSurfaceVariant,
              textAlign = TextAlign.Center,
              lineHeight = (font14.value * lineHeightBody).sp
            )

            Spacer(Modifier.height(32.dp))

            ShredgramPassphraseField(
              value = dbKey.value,
              onValueChange = { dbKey.value = it },
              placeholder = generalGetString(MR.strings.enter_passphrase),
              showPassword = showPassword,
              onToggleVisibility = { showPassword = !showPassword },
              focusRequester = focusRequester,
              onSubmit = if (buttonEnabled) {
                { if (useKeychain) saveAndRunChatOnClick() else callRunChat() }
              } else null
            )

          }
          is DBMigrationResult.ErrorMigration -> {
            when (val err = status.migrationError) {
              is MigrationError.Upgrade -> {
                DatabaseErrorDetails {
                  Text(
                    text = "Database upgrade required",
                    fontFamily = Manrope,
                    fontSize = font30,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = (font30.value * lineHeightHeadlineS).sp
                  )
                  Spacer(Modifier.height(16.dp))
                  FileNameText(status.dbFile)
                  Spacer(Modifier.height(8.dp))
                  MigrationsText(err.upMigrations.map { it.upName })
                  AppVersionText()
                }
                OpenDatabaseDirectoryButton()
              }
              is MigrationError.Downgrade -> {
                DatabaseErrorDetails {
                  Text(
                    text = "Database downgrade required",
                    fontFamily = Manrope,
                    fontSize = font30,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = (font30.value * lineHeightHeadlineS).sp
                  )
                  Spacer(Modifier.height(16.dp))
                  Text(generalGetString(MR.strings.database_downgrade_warning), fontWeight = FontWeight.Bold, color = Color(0xFFFF8A50))
                  Spacer(Modifier.height(8.dp))
                  FileNameText(status.dbFile)
                  MigrationsText(err.downMigrations)
                  AppVersionText()
                }
                OpenDatabaseDirectoryButton()
              }
              is MigrationError.Error -> {
                DatabaseErrorDetails {
                  Text(
                    text = "Incompatible database",
                    fontFamily = Manrope,
                    fontSize = font30,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = (font30.value * lineHeightHeadlineS).sp
                  )
                  Spacer(Modifier.height(16.dp))
                  FileNameText(status.dbFile)
                  Text(
                    String.format(generalGetString(MR.strings.error_with_info), mtrErrorDescription(err.mtrError)),
                    fontFamily = DMSans,
                    fontSize = font12,
                    color = Color(0xFFFF8A50),
                    textAlign = TextAlign.Center
                  )
                }
                OpenDatabaseDirectoryButton()
              }
            }
          }
          is DBMigrationResult.ErrorSQL -> {
            DatabaseErrorDetails {
              Text(
                text = "Database error",
                fontFamily = Manrope,
                fontSize = font30,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (font30.value * lineHeightHeadlineS).sp
              )
              Spacer(Modifier.height(16.dp))
              FileNameText(status.dbFile)
              Text(
                String.format(generalGetString(MR.strings.error_with_info), status.migrationSQLError),
                fontFamily = DMSans,
                fontSize = font12,
                color = Color(0xFFFF8A50),
                textAlign = TextAlign.Center
              )
            }
            OpenDatabaseDirectoryButton()
          }
          is DBMigrationResult.ErrorKeychain -> {
            DatabaseErrorDetails {
              Text(
                text = "Keychain error",
                fontFamily = Manrope,
                fontSize = font30,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (font30.value * lineHeightHeadlineS).sp
              )
              Spacer(Modifier.height(16.dp))
              Text(
                generalGetString(MR.strings.cannot_access_keychain),
                fontFamily = DMSans,
                fontSize = font14,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
            OpenDatabaseDirectoryButton()
          }
          is DBMigrationResult.InvalidConfirmation -> {
            DatabaseErrorDetails {
              Text(
                text = "Invalid migration",
                fontFamily = Manrope,
                fontSize = font30,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (font30.value * lineHeightHeadlineS).sp
              )
            }
            OpenDatabaseDirectoryButton()
          }
          is DBMigrationResult.Unknown -> {
            DatabaseErrorDetails {
              Text(
                text = "Database error",
                fontFamily = Manrope,
                fontSize = font30,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (font30.value * lineHeightHeadlineS).sp
              )
              Spacer(Modifier.height(16.dp))
              Text(
                String.format(generalGetString(MR.strings.unknown_database_error_with_info), status.json),
                fontFamily = DMSans,
                fontSize = font12,
                color = Color(0xFFFF8A50),
                textAlign = TextAlign.Center
              )
            }
            OpenDatabaseDirectoryButton()
          }
          is DBMigrationResult.OK -> {}
          null -> {}
        }

        if (restoreDbFromBackup.value) {
          Spacer(Modifier.height(24.dp))
          Text(
            generalGetString(MR.strings.database_backup_can_be_restored),
            fontFamily = DMSans,
            fontSize = font14,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(12.dp))
          TextButton(
            onClick = {
              AlertManager.shared.showAlertDialog(
                title = generalGetString(MR.strings.restore_database_alert_title),
                text = generalGetString(MR.strings.restore_database_alert_desc),
                confirmText = generalGetString(MR.strings.restore_database_alert_confirm),
                onConfirm = { restoreDb(restoreDbFromBackup, appPreferences) },
                destructive = true,
              )
            }
          ) {
            Text(
              generalGetString(MR.strings.restore_database),
              color = Color(0xFFFF8A50),
              fontFamily = DMSans,
              fontSize = font14,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        when (val status = chatDbStatus.value) {
          is DBMigrationResult.ErrorNotADatabase -> {
            Button(
              onClick = {
                if (useKeychain) saveAndRunChatOnClick() else callRunChat()
              },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(360.dp),
              colors = ButtonDefaults.buttonColors(
                backgroundColor = ElectricBlue500,
                contentColor = Color.White,
                disabledBackgroundColor = BorderElevated1,
                disabledContentColor = DarkCharcoal400
              ),
              enabled = buttonEnabled,
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
              elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
              Text(
                text = "Unlock",
                fontSize = font14,
                fontWeight = FontWeight.Medium,
                fontFamily = DMSans,
                lineHeight = (font14.value * lineHeightBody).sp
              )
            }
          }
          is DBMigrationResult.ErrorMigration -> when (status.migrationError) {
            is MigrationError.Upgrade -> {
              Button(
                onClick = { callRunChat(confirmMigrations = MigrationConfirmation.YesUp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                  backgroundColor = ElectricBlue500,
                  contentColor = Color.White,
                  disabledBackgroundColor = BorderElevated1,
                  disabledContentColor = DarkCharcoal400
                ),
                enabled = !progressIndicator.value,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
              ) {
                Text(
                  text = generalGetString(MR.strings.upgrade_and_open_chat),
                  fontSize = font14,
                  fontWeight = FontWeight.Medium,
                  fontFamily = DMSans,
                  lineHeight = (font14.value * lineHeightBody).sp
                )
              }
            }
            is MigrationError.Downgrade -> {
              Button(
                onClick = { callRunChat(confirmMigrations = MigrationConfirmation.YesUpDown) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                  backgroundColor = ElectricBlue500,
                  contentColor = Color.White,
                  disabledBackgroundColor = BorderElevated1,
                  disabledContentColor = DarkCharcoal400
                ),
                enabled = !progressIndicator.value,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
              ) {
                Text(
                  text = generalGetString(MR.strings.downgrade_and_open_chat),
                  fontSize = font14,
                  fontWeight = FontWeight.Medium,
                  fontFamily = DMSans,
                  lineHeight = (font14.value * lineHeightBody).sp
                )
              }
            }
            else -> {}
          }
          else -> {}
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
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

private fun runChat(
  dbKey: String? = null,
  confirmMigrations: MigrationConfirmation? = null,
  chatDbStatus: State<DBMigrationResult?>,
  progressIndicator: MutableState<Boolean>,
) = CoroutineScope(Dispatchers.Default).launch {
  // Don't do things concurrently. Shouldn't be here concurrently, just in case
  if (progressIndicator.value) return@launch
  progressIndicator.value = true
  try {
    initChatController(dbKey, confirmMigrations,
      startChat = if (appPreferences.chatStopped.get()) ::showStartChatAfterRestartAlert else { { CompletableDeferred(true) } }
    )
  } catch (e: Exception) {
  }
  progressIndicator.value = false
  when (val status = chatDbStatus.value) {
    is DBMigrationResult.OK -> {
      platform.androidChatStartedAfterBeingOff()
    }
    null -> {}
    else -> showErrorOnMigrationIfNeeded(status)
  }
}

fun showErrorOnMigrationIfNeeded(status: DBMigrationResult) =
  when (status) {
    is DBMigrationResult.OK -> {}
    is DBMigrationResult.ErrorNotADatabase ->
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.wrong_passphrase_title), generalGetString(MR.strings.enter_correct_passphrase))
    is DBMigrationResult.ErrorSQL ->
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.database_error), status.migrationSQLError)
    is DBMigrationResult.ErrorKeychain ->
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.keychain_error))
    is DBMigrationResult.Unknown ->
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.unknown_error), status.json)
    is DBMigrationResult.InvalidConfirmation ->
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.invalid_migration_confirmation))
    is DBMigrationResult.ErrorMigration -> {}
  }

private fun shouldShowRestoreDbButton(prefs: AppPreferences): Boolean {
  val startedAt = prefs.encryptionStartedAt.get() ?: return false
  /** Just in case there is any small difference between reported Java's [Clock.System.now] and Linux's time on a file */
  val safeDiffInTime = 10_000L
  val filesChat = File(dataDir.absolutePath + File.separator + "${chatDatabaseFileName}.bak")
  val filesAgent = File(dataDir.absolutePath + File.separator + "${agentDatabaseFileName}.bak")
  return filesChat.exists() &&
      filesAgent.exists() &&
      startedAt.toEpochMilliseconds() - safeDiffInTime <= filesChat.lastModified() &&
      startedAt.toEpochMilliseconds() - safeDiffInTime <= filesAgent.lastModified()
}

private fun restoreDb(restoreDbFromBackup: MutableState<Boolean>, prefs: AppPreferences) {
  val filesChatBase = dataDir.absolutePath + File.separator + chatDatabaseFileName
  val filesAgentBase = dataDir.absolutePath + File.separator + agentDatabaseFileName
  try {
    Files.copy(Path("$filesChatBase.bak"), Path(filesChatBase), StandardCopyOption.REPLACE_EXISTING)
    Files.copy(Path("$filesAgentBase.bak"), Path(filesAgentBase), StandardCopyOption.REPLACE_EXISTING)
    restoreDbFromBackup.value = false
    prefs.encryptionStartedAt.set(null)
  } catch (e: Exception) {
    AlertManager.shared.showAlertMsg(generalGetString(MR.strings.database_restore_error), e.stackTraceToString())
  }
}

fun mtrErrorDescription(err: MTRError): String =
  when (err) {
    is MTRError.NoDown ->
      String.format(generalGetString(MR.strings.mtr_error_no_down_migration), err.dbMigrations.joinToString(", "))
    is MTRError.Different ->
      String.format(generalGetString(MR.strings.mtr_error_different), err.appMigration, err.dbMigration)
  }

@Composable
private fun OpenDatabaseDirectoryButton() {
  if (appPlatform.isDesktop) {
    Spacer(Modifier.padding(top = DEFAULT_PADDING))
    SettingsActionItem(
      painterResource(MR.images.ic_folder_open),
      stringResource(MR.strings.open_database_folder),
      ::desktopOpenDatabaseDir
    )
  }
}

internal class MaskedDotVisualTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val masked = AnnotatedString("\u25CF".repeat(text.length))
    return TransformedText(masked, OffsetMapping.Identity)
  }
}

@Composable
internal fun ShredgramPassphraseField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  showPassword: Boolean,
  onToggleVisibility: () -> Unit,
  focusRequester: FocusRequester,
  onSubmit: (() -> Unit)? = null
) {
  val interactionSource = remember { MutableInteractionSource() }
  var isFocused by remember { mutableStateOf(false) }
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  val borderColor = if (isFocused) ElectricBlue500 else outlineVariant
  val visualTransformation = if (!showPassword) MaskedDotVisualTransformation() else VisualTransformation.None

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier
      .fillMaxWidth()
      .focusRequester(focusRequester)
      .onFocusChanged { isFocused = it.isFocused }
      .onPreviewKeyEvent {
        if (onSubmit != null && (it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyUp) {
          onSubmit()
          true
        } else false
      },
    singleLine = true,
    textStyle = TextStyle(
      fontSize = font12,
      fontWeight = FontWeight.Normal,
      fontFamily = DMSans,
      color = MaterialTheme.colors.onSurface,
      lineHeight = (font12.value * lineHeightBody).sp
    ),
    cursorBrush = SolidColor(ElectricBlue500),
    visualTransformation = visualTransformation,
    keyboardOptions = KeyboardOptions(
      imeAction = ImeAction.Done,
      autoCorrect = false,
      keyboardType = KeyboardType.Password
    ),
    keyboardActions = KeyboardActions(onDone = if (onSubmit != null && appPlatform.isAndroid) {
      { onSubmit() }
    } else null),
    interactionSource = interactionSource
  ) { innerTextField ->
    val borderWidth = if (isFocused) 2.dp else 1.dp
    Box(
      modifier = Modifier
        .border(borderWidth, borderColor, RoundedCornerShape(50))
        .background(MaterialTheme.colors.surface, RoundedCornerShape(50))
        .padding(horizontal = 24.dp, vertical = 18.dp),
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
              fontSize = font12,
              fontWeight = FontWeight.Normal,
              fontFamily = DMSans,
              color = OnSurfaceVariant,
              lineHeight = (font12.value * lineHeightBody).sp
            )
          }
          innerTextField()
        }
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
            contentDescription = if (showPassword) "Hide passphrase" else "Show passphrase",
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

// Typography tokens matching the codebase
private const val tapScreenLineHeightHeadlineS = 1.12f
private const val tapScreenLineHeightBody = 1.5f
private val tapScreenFont14 = 14.sp
private val tapScreenFont30 = 30.sp
private val tapScreenOnSurfaceVariant = Color(0xFF3D4042)
private val tapScreenElectricBlue500 = Color(0xFF1F4CFF)

@Composable
private fun YubiKeyTapScreen(
  isProcessing: Boolean,
  errorMessage: String?,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top bar: back button (left) + logo (center) + spacer (right)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp)
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.align(Alignment.CenterStart)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = stringResource(MR.strings.back),
            tint = MaterialTheme.colors.onBackground
          )
        }
        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = null,
          modifier = Modifier.align(Alignment.Center)
        )
      }

      // Key lock icon (24dp)
      Icon(
        painter = painterResource(MR.images.ic_key_lock),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onSurface
      )

      Spacer(Modifier.height(16.dp))

      // Title — Manrope Bold 30sp
      Text(
        text = "Unlock your database",
        fontFamily = Manrope,
        fontSize = tapScreenFont30,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        lineHeight = (tapScreenFont30.value * tapScreenLineHeightHeadlineS).sp
      )

      Spacer(Modifier.height(8.dp))

      // Description — DMSans Normal 14sp
      Text(
        text = "Tap your passkey to unlock your database.",
        fontFamily = DMSans,
        fontSize = tapScreenFont14,
        fontWeight = FontWeight.Normal,
        color = tapScreenOnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (tapScreenFont14.value * tapScreenLineHeightBody).sp
      )

      // Center: passkey icon + status text
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            painter = painterResource(MR.images.ic_passkey),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(92.dp)
          )

          Spacer(Modifier.height(16.dp))

          Text(
            text = if (isProcessing) "Reading YubiKey…" else "Insert or tap your physical passkey",
            fontFamily = DMSans,
            fontSize = tapScreenFont14,
            fontWeight = FontWeight.Normal,
            color = tapScreenOnSurfaceVariant,
            textAlign = TextAlign.Center
          )
        }
      }

      Spacer(Modifier.height(16.dp))
      Spacer(Modifier.navigationBarsPadding())
    }

    // Processing overlay
    if (isProcessing) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(40.dp),
          color = tapScreenElectricBlue500,
          strokeWidth = 3.dp
        )
      }
    }

    // Error overlay
    if (errorMessage != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.5f))
          .clickable { /* dismissed by going back */ },
        contentAlignment = Alignment.Center
      ) {
        Surface(
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 320.dp),
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colors.surface,
          elevation = 8.dp
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              painter = painterResource(MR.images.ic_error),
              contentDescription = null,
              tint = MaterialTheme.colors.error,
              modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
              text = "Unlock Failed",
              fontFamily = Manrope,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colors.onSurface,
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
              text = errorMessage,
              fontFamily = DMSans,
              fontSize = tapScreenFont14,
              fontWeight = FontWeight.Normal,
              color = tapScreenOnSurfaceVariant,
              textAlign = TextAlign.Center,
              lineHeight = (tapScreenFont14.value * tapScreenLineHeightBody).sp
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) {
              Text(
                text = "← Go back",
                fontFamily = DMSans,
                fontSize = tapScreenFont14,
                color = tapScreenElectricBlue500
              )
            }
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun PreviewChatInfoLayout() {
  SimpleXTheme {
    DatabaseErrorView(
      remember { mutableStateOf(DBMigrationResult.ErrorNotADatabase("simplex_v1_chat.db")) },
      AppPreferences()
    )
  }
}
