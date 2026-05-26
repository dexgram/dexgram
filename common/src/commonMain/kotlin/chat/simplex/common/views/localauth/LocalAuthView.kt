package chat.simplex.common.views.localauth

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatModel.controller
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.helpers.DatabaseUtils.ksSelfDestructPassword
import chat.simplex.common.views.helpers.DatabaseUtils.ksAppPassword
import chat.simplex.common.views.onboarding.OnboardingStage
import chat.simplex.common.platform.*
import chat.simplex.common.views.database.*
import chat.simplex.res.MR
import kotlinx.coroutines.delay

@Composable
fun LocalAuthView(m: ChatModel, authRequest: LocalAuthRequest) {
  val passcode = rememberSaveable { mutableStateOf("") }
  val allowToReact = rememberSaveable { mutableStateOf(true) }
  val failedAttempts = remember { mutableStateOf(m.controller.appPrefs.laFailedAttempts.get()) }
  val maxFailedAttempts = remember { mutableStateOf(m.controller.appPrefs.laMaxFailedAttempts.get()) }
  
  // Build reason with failed attempts warning if applicable
  val displayReason = remember(failedAttempts.value, maxFailedAttempts.value, authRequest.reason) {
    if (failedAttempts.value > 0) {
      val remainingAttempts = maxFailedAttempts.value - failedAttempts.value
      val warningText = if (remainingAttempts > 0) {
        generalGetString(MR.strings.la_failed_attempts_warning).format(failedAttempts.value, maxFailedAttempts.value, remainingAttempts)
      } else {
        ""
      }
      if (authRequest.reason.isNotEmpty() && warningText.isNotEmpty()) {
        "${authRequest.reason}\n\n$warningText"
      } else {
        warningText
      }
    } else {
      authRequest.reason
    }
  }
  
  if (!allowToReact.value) {
    BackHandler {
      // do nothing until submit action finishes to prevent concurrent removing of storage
    }
  }
  PasscodeView(passcode, authRequest.title ?: stringResource(MR.strings.la_enter_app_passcode), displayReason, stringResource(MR.strings.submit_passcode), buttonsEnabled = allowToReact,
    submit = {
      val sdPassword = ksSelfDestructPassword.get()
      if (sdPassword == passcode.value && authRequest.selfDestruct) {
        allowToReact.value = false
        deleteStorageAndRestart(m, sdPassword) { r ->
          authRequest.completed(r)
        }
      } else {
        val r: LAResult = if (passcode.value == authRequest.password) {
          // Successful authentication - reset failed attempts counter
          m.controller.appPrefs.laFailedAttempts.set(0)
          if (authRequest.selfDestruct && sdPassword != null && controller.getChatCtrl() == -1L) {
            initChatControllerOnStart()
          }
          LAResult.Success
        } else {
          // Failed authentication - increment counter
          val currentFailedAttempts = m.controller.appPrefs.laFailedAttempts.get()
          val newFailedAttempts = currentFailedAttempts + 1
          m.controller.appPrefs.laFailedAttempts.set(newFailedAttempts)
          failedAttempts.value = newFailedAttempts
          
          // Check if max attempts exceeded - wipe all data
          if (newFailedAttempts >= maxFailedAttempts.value) {
            Log.w(TAG, "Max failed attempts exceeded - wiping all data")
            allowToReact.value = false
            wipeAllDataAndReset(m) { r ->
              authRequest.completed(r)
            }
            return@PasscodeView
          }
          
          LAResult.Error(generalGetString(MR.strings.incorrect_passcode))
        }
        authRequest.completed(r)
      }
    },
    cancel = {
      authRequest.completed(LAResult.Error(generalGetString(MR.strings.authentication_cancelled)))
    })
}

private fun deleteStorageAndRestart(m: ChatModel, password: String, completed: (LAResult) -> Unit) {
  withLongRunningApi {
    try {
      /** Waiting until [initChatController] finishes */
      while (m.ctrlInitInProgress.value) {
        delay(50)
      }
      if (m.chatRunning.value == true) {
        stopChatAsync(m)
      }
      val ctrl = m.controller.getChatCtrl()
      if (ctrl != null && ctrl != -1L) {
        chatCloseStore(ctrl)
      }
      deleteChatDatabaseFilesAndState()
      ksAppPassword.set(password)
      ksSelfDestructPassword.remove()
      ntfManager.cancelAllNotifications()
      val selfDestructPref = m.controller.appPrefs.selfDestruct
      val displayNamePref = m.controller.appPrefs.selfDestructDisplayName
      val displayName = displayNamePref.get()
      selfDestructPref.set(false)
      displayNamePref.set(null)
      reinitChatController()
      if (m.currentUser.value != null) {
        return@withLongRunningApi
      }
      // Generate random display name in format "Name.XX" (e.g., "Alice.23")
      // Use saved selfDestructDisplayName if available, otherwise generate new one
      val generatedName = if (!displayName.isNullOrEmpty()) {
        displayName
      } else {
        generateRandomDisplayName()
      }
      val profile = Profile(displayName = generatedName, fullName = "", shortDescr = null)
      val createdUser = m.controller.apiCreateActiveUser(null, profile, pastTimestamp = true)
      m.currentUser.value = createdUser
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.OnboardingComplete)
      if (createdUser != null) {
        m.controller.ensureDefaultChatItemTTLInitialized(null)
        m.controller.startChat(createdUser)
        m.controller.getUserChatData(null)
      }
      ModalManager.closeAllModalsEverywhere()
      AlertManager.shared.hideAllAlerts()
      AlertManager.privacySensitive.hideAllAlerts()
      completed(LAResult.Success)
    } catch (e: Exception) {
      Log.e(TAG, "Unable to delete storage: ${e.stackTraceToString()}")
      completed(LAResult.Error(generalGetString(MR.strings.incorrect_passcode)))
    }
  }
}

/**
 * Wipes all app data and resets to initial state after max failed attempts exceeded.
 * This does NOT create a new account - the app returns to the onboarding screen.
 */
private fun wipeAllDataAndReset(m: ChatModel, completed: (LAResult) -> Unit) {
  withLongRunningApi {
    try {
      
      /** Waiting until [initChatController] finishes */
      while (m.ctrlInitInProgress.value) {
        delay(50)
      }
      
      // Stop chat if running
      if (m.chatRunning.value == true) {
        stopChatAsync(m)
      }
      
      val ctrl = m.controller.getChatCtrl()
      if (ctrl != null && ctrl != -1L) {
        chatCloseStore(ctrl)
      }
      
      // Delete all database files and state
      deleteChatDatabaseFilesAndState()
      
      // Clear all app passwords and keys
      ksAppPassword.remove()
      ksSelfDestructPassword.remove()
      
      // Cancel all notifications
      ntfManager.cancelAllNotifications()
      
      // Reset self-destruct preferences
      m.controller.appPrefs.selfDestruct.set(false)
      m.controller.appPrefs.selfDestructDisplayName.set(null)
      
      // Reset failed attempts counter
      m.controller.appPrefs.laFailedAttempts.set(0)
      
      // Disable app lock
      m.controller.appPrefs.performLA.set(false)
      m.showAuthScreen.value = false
      
      // Reset to initial onboarding stage
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step1_SimpleXInfo)
      
      // Reinitialize controller
      reinitChatController()
      
      // Close all modals and alerts
      ModalManager.closeAllModalsEverywhere()
      AlertManager.shared.hideAllAlerts()
      AlertManager.privacySensitive.hideAllAlerts()
      
      Log.i(TAG, "All data wiped successfully - app reset to initial state")
      completed(LAResult.Success)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to wipe data: ${e.stackTraceToString()}")
      completed(LAResult.Error(generalGetString(MR.strings.authentication_cancelled)))
    }
  }
}

suspend fun reinitChatController() {
  chatModel.chatDbChanged.value = true
  chatModel.chatDbStatus.value = null
  try {
    initChatController()
  } catch (e: Exception) {
  }
  chatModel.chatDbChanged.value = false
}
