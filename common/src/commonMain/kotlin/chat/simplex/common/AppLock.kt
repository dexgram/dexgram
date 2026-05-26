package chat.simplex.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.localauth.SetAppPasscodeView
import chat.simplex.common.views.usersettings.*
import chat.simplex.res.MR
import kotlinx.coroutines.*

object AppLock {
  /**
   * We don't want these values to be bound to Activity lifecycle since activities are changed often, for example, when a user
   * clicks on new message in notification. In this case savedInstanceState will be null (this prevents restoring the values)
   * See [SimplexService.onTaskRemoved] for another part of the logic which nullifies the values when app closed by the user
   * */
  val userAuthorized = mutableStateOf<Boolean?>(null)
  val enteredBackground = mutableStateOf<Long?>(null)

  // Remember result and show it after orientation change
  val laFailed = mutableStateOf(false)

  fun clearAuthState() {
    userAuthorized.value = null
    enteredBackground.value = null
  }

  fun showLANotice(laNoticeShown: SharedPreference<Boolean>, onComplete: ((Boolean) -> Unit)? = null) {
    if (!laNoticeShown.get() && !appPrefs.performLA.get()) {
      laNoticeShown.set(true)
      // Skip the initial dialog and go directly to lock setup
            if (appPlatform.isAndroid) {
        showChooseLAMode(onComplete)
      } else {
        setPasscode(onComplete)
      }
            } else {
      onComplete?.invoke(false)
    }
  }

  private fun showLASetupScreen(onComplete: ((Boolean) -> Unit)? = null) {
    if (appPrefs.performLA.get()) {
      onComplete?.invoke(false)
      return
    }

    val currentLAMode = ChatController.appPrefs.laMode
    
    ModalManager.fullscreen.showCustomModal { close ->
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
        // Show SimplexLockView - user can setup lock and adjust timeout
        SimplexLockView(
          chatModel = ChatModel,
          currentLAMode = currentLAMode,
          setPerformLA = { enabled ->
            if (enabled) {
              // User clicked Next - lock is set up and settings confirmed
              val passcodeWasSet = currentLAMode.get() == LAMode.PASSCODE
              
              // Call callback first to update onboarding stage
              onComplete?.invoke(passcodeWasSet)
              
              // Close modal after brief delay to allow state propagation
              CoroutineScope(Dispatchers.Main).launch {
                delay(100)
                close()
              }
            }
          }
        )
      }
    }
  }

  private fun showChooseLAMode(onComplete: ((Boolean) -> Unit)? = null) {
    showLASetupScreen(onComplete)
  }

  private fun initialEnableLA() {
    val m = ChatModel
    val appPrefs = ChatController.appPrefs
    appPrefs.laMode.set(LAMode.default)
    authenticate(
      generalGetString(MR.strings.auth_enable_simplex_lock),
      generalGetString(MR.strings.auth_confirm_credential),
      oneTime = true,
      completed = { laResult ->
        when (laResult) {
          LAResult.Success -> {
            m.showAuthScreen.value = true
            appPrefs.performLA.set(true)
            laTurnedOnAlert()
          }
          is LAResult.Failed -> { /* Can be called multiple times on every failure */ }
          is LAResult.Error -> {
            m.showAuthScreen.value = false
            // Don't drop auth pref in case of state inconsistency (eg, you have set passcode but somehow bypassed toggle and turned it off and then on)
            // appPrefs.performLA.set(false)
            laFailedAlert()
          }
          is LAResult.Unavailable -> {
            m.showAuthScreen.value = false
            appPrefs.performLA.set(false)
            m.showAdvertiseLAUnavailableAlert.value = true
          }
        }
      }
    )
  }

  private fun setPasscode(onComplete: ((Boolean) -> Unit)? = null) {
    if (appPrefs.performLA.get()) {
      onComplete?.invoke(false)
      return
    }

    val appPrefs = ChatController.appPrefs
    ModalManager.fullscreen.showCustomModal { close ->
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
        SetAppPasscodeView(
          submit = {
            ChatModel.showAuthScreen.value = true
            appPrefs.performLA.set(true)
            appPrefs.laMode.set(LAMode.PASSCODE)
            laTurnedOnAlert()
            close()
            onComplete?.invoke(true) // Passcode was set
          },
          cancel = {
            ChatModel.showAuthScreen.value = false
            // Don't drop auth pref in case of state inconsistency (eg, you have set passcode but somehow bypassed toggle and turned it off and then on)
            // appPrefs.performLA.set(false)
            laPasscodeNotSetAlert()
            close()
            onComplete?.invoke(false)
          },
          close = {
            close()
            onComplete?.invoke(false)
          }
        )
      }
    }
  }

  fun setAuthState() {
    userAuthorized.value = !ChatController.appPrefs.performLA.get()
  }

  fun runAuthenticate() {
    val m = ChatModel
    setAuthState()
    if (userAuthorized.value == false) {
      // To make Main thread free in order to allow to Compose to show blank view that hiding content underneath of it faster on slow devices
      CoroutineScope(Dispatchers.Default).launch {
        delay(50)
        withContext(Dispatchers.Main) {
          authenticate(
            if (m.controller.appPrefs.laMode.get() == LAMode.SYSTEM)
              generalGetString(MR.strings.auth_unlock)
            else
              generalGetString(MR.strings.la_enter_app_passcode),
            if (m.controller.appPrefs.laMode.get() == LAMode.SYSTEM)
              generalGetString(MR.strings.auth_log_in_using_credential)
            else
              generalGetString(MR.strings.auth_unlock),
            selfDestruct = true,
            oneTime = false,
            completed = { laResult ->
              when (laResult) {
                LAResult.Success -> {
                  userAuthorized.value = true
                  // Reset failed attempts counter on successful authentication
                  m.controller.appPrefs.laFailedAttempts.set(0)
                }
                is LAResult.Failed -> { /* Can be called multiple times on every failure */ }
                is LAResult.Error -> {
                  laFailed.value = true
                  if (m.controller.appPrefs.laMode.get() == LAMode.PASSCODE) {
                    laFailedAlert()
                  }
                }
                is LAResult.Unavailable -> {
                  userAuthorized.value = true
                  m.showAuthScreen.value = false
                  m.controller.appPrefs.performLA.set(false)
                  laUnavailableTurningOffAlert()
                }
              }
            }
          )
        }
      }
    }
  }

  fun setPerformLA(on: Boolean) {
    ChatController.appPrefs.laNoticeShown.set(true)
    if (on) {
      enableLA()
    } else {
      disableLA()
    }
  }

  private fun enableLA() {
    val m = ChatModel
    authenticate(
      if (m.controller.appPrefs.laMode.get() == LAMode.SYSTEM)
        generalGetString(MR.strings.auth_enable_simplex_lock)
      else
        generalGetString(MR.strings.new_passcode),
      if (m.controller.appPrefs.laMode.get() == LAMode.SYSTEM)
        generalGetString(MR.strings.auth_confirm_credential)
      else
        "",
      oneTime = true,
      completed = { laResult ->
        val prefPerformLA = m.controller.appPrefs.performLA
        when (laResult) {
          LAResult.Success -> {
            m.showAuthScreen.value = true
            prefPerformLA.set(true)
            // Reset failed attempts counter when lock is enabled
            m.controller.appPrefs.laFailedAttempts.set(0)
            laTurnedOnAlert()
          }
          is LAResult.Failed -> { /* Can be called multiple times on every failure */ }
          is LAResult.Error -> {
            m.showAuthScreen.value = false
            prefPerformLA.set(false)
            laFailedAlert()
          }
          is LAResult.Unavailable -> {
            m.showAuthScreen.value = false
            prefPerformLA.set(false)
            laUnavailableInstructionAlert()
          }
        }
      }
    )
  }

  private fun disableLA() {
    val m = ChatModel
    authenticate(
      if (m.controller.appPrefs.laMode.get() == LAMode.SYSTEM)
        generalGetString(MR.strings.auth_disable_simplex_lock)
      else
        generalGetString(MR.strings.la_enter_app_passcode),
      if (m.controller.appPrefs.laMode.get() == LAMode.SYSTEM)
        generalGetString(MR.strings.auth_confirm_credential)
      else
        generalGetString(MR.strings.auth_disable_simplex_lock),
      oneTime = true,
      completed = { laResult ->
        val prefPerformLA = m.controller.appPrefs.performLA
        val selfDestructPref = m.controller.appPrefs.selfDestruct
        when (laResult) {
          LAResult.Success -> {
            m.showAuthScreen.value = false
            prefPerformLA.set(false)
            DatabaseUtils.ksAppPassword.remove()
            selfDestructPref.set(false)
            DatabaseUtils.ksSelfDestructPassword.remove()
            // Reset failed attempts counter when lock is disabled
            m.controller.appPrefs.laFailedAttempts.set(0)
          }
          is LAResult.Failed -> { /* Can be called multiple times on every failure */ }
          is LAResult.Error -> {
            m.showAuthScreen.value = true
            prefPerformLA.set(true)
            laFailedAlert()
          }
          is LAResult.Unavailable -> {
            m.showAuthScreen.value = false
            prefPerformLA.set(false)
            laUnavailableTurningOffAlert()
          }
        }
      }
    )
  }

  fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

  fun recheckAuthState() {
    val enteredBackgroundVal = enteredBackground.value
    val delay = ChatController.appPrefs.laLockDelay.get()
    if (enteredBackgroundVal == null || elapsedRealtime() - enteredBackgroundVal >= delay * 1000) {
      if (userAuthorized.value != false) {
        /** [runAuthenticate] will be called in [MainScreen] if needed. Making like this prevents double showing of passcode on start */
        setAuthState()
      } else if (!ChatModel.activeCallViewIsVisible.value) {
        runAuthenticate()
      }
    }
  }
  fun appWasHidden() {
    enteredBackground.value = elapsedRealtime()
  }
}
