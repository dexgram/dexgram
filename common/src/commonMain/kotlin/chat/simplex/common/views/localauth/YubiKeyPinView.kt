package chat.simplex.common.views.localauth

import androidx.compose.runtime.*
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.platform.WeakPinDetector
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR

/**
 * YubiKey PIN entry view - uses exact same design as PasscodeView
 * For unlocking database with YubiKey
 * 
 * SECURITY: Uses `remember` instead of `rememberSaveable` to prevent
 * PIN from being persisted to disk during state restoration.
 */
@Composable
fun YubiKeyPinView(
  title: String = generalGetString(MR.strings.yubikey_unlock_pin_title),
  reason: String? = generalGetString(MR.strings.yubikey_unlock_pin_reason),
  submitLabel: String = generalGetString(MR.strings.yubikey_unlock_btn_unlock),
  buttonsEnabled: State<Boolean> = remember { mutableStateOf(true) },
  onPinEntered: (String) -> Unit,
  onCancel: () -> Unit
) {
  // SECURITY: Use remember (not rememberSaveable) - PIN must never be saved to disk
  val pin = remember { mutableStateOf("") }
  
  BackHandler {
    onCancel()
  }
  
  PasscodeView(
    passcode = pin,
    title = title,
    reason = reason,
    submitLabel = submitLabel,
    submitEnabled = { it.length >= 6 },  // YubiKey PIN is 6-8 digits
    buttonsEnabled = buttonsEnabled,
    iconResource = MR.images.ic_yubikey,
    submit = { onPinEntered(pin.value) },
    cancel = onCancel
  )
}

/**
 * YubiKey PUK entry view - uses exact same design as PasscodeView
 * For resetting PIN when locked out
 * 
 * SECURITY: Uses `remember` instead of `rememberSaveable` to prevent
 * PUK from being persisted to disk during state restoration.
 */
@Composable
fun YubiKeyPukView(
  title: String = generalGetString(MR.strings.yubikey_unlock_puk_title),
  reason: String? = generalGetString(MR.strings.yubikey_unlock_puk_reason),
  submitLabel: String = generalGetString(MR.strings.yubikey_unlock_btn_submit),
  buttonsEnabled: State<Boolean> = remember { mutableStateOf(true) },
  onPukEntered: (String) -> Unit,
  onCancel: () -> Unit
) {
  // SECURITY: Use remember (not rememberSaveable) - PUK must never be saved to disk
  val puk = remember { mutableStateOf("") }
  
  BackHandler {
    onCancel()
  }
  
  PasscodeView(
    passcode = puk,
    title = title,
    reason = reason,
    submitLabel = submitLabel,
    submitEnabled = { it.length == 8 },  // PUK is exactly 8 digits
    buttonsEnabled = buttonsEnabled,
    iconResource = MR.images.ic_yubikey,
    submit = { onPukEntered(puk.value) },
    cancel = onCancel
  )
}

/**
 * Set YubiKey PIN view - uses exact same design as SetAppPasscodeView
 * For setting up a new PIN with confirmation
 * 
 * SECURITY: Uses `remember` instead of `rememberSaveable` to prevent
 * PIN from being persisted to disk during state restoration.
 */
@Composable
fun SetYubiKeyPinView(
  title: String = generalGetString(MR.strings.yubikey_unlock_set_pin_title),
  reason: String? = generalGetString(MR.strings.yubikey_unlock_set_pin_reason),
  currentPin: String? = null,  // Required if changing existing PIN
  onPinSet: (newPin: String) -> Unit,
  onCancel: () -> Unit,
  close: () -> Unit
) {
  // SECURITY: Use remember (not rememberSaveable) - PINs must never be saved to disk
  val passcode = remember { mutableStateOf("") }
  var enteredPin by remember { mutableStateOf("") }
  var confirming by remember { mutableStateOf(false) }
  val pinError = remember { mutableStateOf<String?>(null) }

  @Composable
  fun SetPinView(
    viewTitle: String,
    viewReason: String?,
    submitLabel: String,
    submitEnabled: ((String) -> Boolean)? = null,
    submit: () -> Unit
  ) {
    BackHandler {
      close()
      onCancel()
    }
    
    PasscodeView(
      passcode = passcode,
      title = viewTitle,
      reason = viewReason,
      submitLabel = submitLabel,
      submitEnabled = submitEnabled,
      iconResource = MR.images.ic_yubikey,
      submit = submit
    ) {
      close()
      onCancel()
    }
  }

  if (confirming) {
    SetPinView(
      viewTitle = generalGetString(MR.strings.yubikey_unlock_confirm_pin_title),
      viewReason = reason,
      submitLabel = generalGetString(MR.strings.confirm_verb),
      submitEnabled = { pwd -> pwd == enteredPin }
    ) {
      if (passcode.value == enteredPin) {
        val newPin = passcode.value
        enteredPin = ""
        passcode.value = ""
        close()
        onPinSet(newPin)
      }
    }
  } else {
    SetPinView(
      viewTitle = title,
      viewReason = pinError.value ?: reason,  // Show error if any
      submitLabel = generalGetString(MR.strings.save_verb),
      submitEnabled = { pwd -> pwd.length >= 6 && pwd.length <= 8 }
    ) {
      // SECURITY: Validate PIN strength before proceeding
      val validationError = WeakPinDetector.validatePin(passcode.value)
      if (validationError != null) {
        pinError.value = validationError
        passcode.value = ""
      } else {
        pinError.value = null
        enteredPin = passcode.value
        passcode.value = ""
        confirming = true
      }
    }
  }
}

/**
 * YubiKey PIN reset flow - enter PUK then set new PIN
 * Complete flow for resetting a locked PIN
 * 
 * SECURITY: Uses `remember` instead of `rememberSaveable` to prevent
 * PUK from being persisted to disk during state restoration.
 */
@Composable
fun YubiKeyPinResetView(
  onReset: (puk: String, newPin: String) -> Unit,
  onCancel: () -> Unit,
  close: () -> Unit
) {
  // SECURITY: Use remember (not rememberSaveable) - PUK must never be saved to disk
  var pukEntered by remember { mutableStateOf<String?>(null) }
  
  if (pukEntered == null) {
    // Step 1: Enter PUK
    YubiKeyPukView(
      title = generalGetString(MR.strings.yubikey_unlock_reset_pin_title),
      reason = generalGetString(MR.strings.yubikey_unlock_puk_reason),
      onPukEntered = { puk ->
        pukEntered = puk
      },
      onCancel = {
        close()
        onCancel()
      }
    )
  } else {
    // Step 2: Set new PIN
    SetYubiKeyPinView(
      title = generalGetString(MR.strings.yubikey_unlock_set_new_pin_title),
      reason = generalGetString(MR.strings.yubikey_unlock_set_new_pin_reason),
      onPinSet = { newPin ->
        onReset(pukEntered!!, newPin)
      },
      onCancel = {
        pukEntered = null  // Go back to PUK entry
      },
      close = close
    )
  }
}
