package chat.simplex.common.views.onboarding

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.usersettings.changeNotificationsMode
import chat.simplex.common.views.extractSimplexAddress
import chat.simplex.res.MR
import kotlinx.coroutines.*

private const val TAG = "SetNotificationsMode"

@Composable
fun SetNotificationsMode(m: ChatModel) {
  LaunchedEffect(Unit) {
    prepareChatBeforeFinishingOnboarding()
  }

  CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
    ModalView({}, showClose = false) {
      ColumnWithScrollBar(Modifier.themedBackground(bgLayerSize = LocalAppBarHandler.current?.backgroundGraphicsLayerSize, bgLayer = LocalAppBarHandler.current?.backgroundGraphicsLayer)) {
        Box(Modifier.align(Alignment.CenterHorizontally)) {
          AppBarTitle(stringResource(MR.strings.onboarding_notifications_mode_title), bottomPadding = DEFAULT_PADDING)
        }
        val currentMode = rememberSaveable { mutableStateOf(NotificationsMode.default) }
        Column(Modifier.padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          OnboardingInformationButton(
            stringResource(MR.strings.onboarding_notifications_mode_subtitle),
            onClick = { ModalManager.fullscreen.showModalCloseable { NotificationBatteryUsageInfo() } }
          )
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING)) {
          SelectableCard(currentMode, NotificationsMode.SERVICE, stringResource(MR.strings.onboarding_notifications_mode_service), annotatedStringResource(MR.strings.onboarding_notifications_mode_service_desc_short)) {
            currentMode.value = NotificationsMode.SERVICE
          }
          SelectableCard(currentMode, NotificationsMode.PERIODIC, stringResource(MR.strings.onboarding_notifications_mode_periodic), annotatedStringResource(MR.strings.onboarding_notifications_mode_periodic_desc_short)) {
            currentMode.value = NotificationsMode.PERIODIC
          }
          SelectableCard(currentMode, NotificationsMode.OFF, stringResource(MR.strings.onboarding_notifications_mode_off), annotatedStringResource(MR.strings.onboarding_notifications_mode_off_desc_short)) {
            currentMode.value = NotificationsMode.OFF
          }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.widthIn(max = if (appPlatform.isAndroid) 450.dp else 1000.dp).align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
          OnboardingActionButton(
            modifier = if (appPlatform.isAndroid) Modifier.padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING).fillMaxWidth() else Modifier,
            labelId = MR.strings.use_chat,
            onboarding = null,  // Set to null, we'll set it manually after creating address
            onclick = {
              changeNotificationsMode(currentMode.value, m)
              
              // Create address and register username if not already done
              if (m.userAddress.value == null) {
                createAddressAndUsernameForOnboarding(m) {
                  // After username is created, complete onboarding
                  m.controller.appPrefs.onboardingStage.set(OnboardingStage.OnboardingComplete)
                  ModalManager.fullscreen.closeModals()
                }
              } else {
                m.controller.appPrefs.onboardingStage.set(OnboardingStage.OnboardingComplete)
                ModalManager.fullscreen.closeModals()
              }
            }
          )
          // Reserve space
          TextButtonBelowOnboardingButton("", null)
        }
      }
    }
  }
  SetNotificationsModeAdditions()
}

@Composable
expect fun SetNotificationsModeAdditions()

@Composable
fun <T> SelectableCard(currentValue: State<T>, newValue: T, title: String, description: AnnotatedString, onSelected: (T) -> Unit) {
  TextButton(
    onClick = { onSelected(newValue) },
    border = BorderStroke(1.dp, color = if (currentValue.value == newValue) MaterialTheme.colors.primary else MaterialTheme.colors.secondary.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(35.dp),
  ) {
    Column(Modifier.padding(horizontal = 10.dp).padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()) {
      Text(
        title,
        style = MaterialTheme.typography.h3,
        fontWeight = FontWeight.Medium,
        color = if (currentValue.value == newValue) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
        modifier = Modifier.padding(bottom = 8.dp).align(Alignment.CenterHorizontally),
        textAlign = TextAlign.Center
      )
      Text(description,
        Modifier.align(Alignment.CenterHorizontally),
        fontSize = 15.sp,
        color = MaterialTheme.colors.onBackground,
        lineHeight = 24.sp,
        textAlign = TextAlign.Center
      )
    }
  }
  Spacer(Modifier.height(14.dp))
}

@Composable
private fun NotificationBatteryUsageInfo() {
  ColumnWithScrollBar(Modifier.padding(DEFAULT_PADDING)) {
    AppBarTitle(stringResource(MR.strings.onboarding_notifications_mode_battery), withPadding = false)
    Text(stringResource(MR.strings.onboarding_notifications_mode_service), style = MaterialTheme.typography.h3, color = MaterialTheme.colors.secondary)
    ReadableText(MR.strings.onboarding_notifications_mode_service_desc)
    Spacer(Modifier.height(DEFAULT_PADDING_HALF))
    Text(stringResource(MR.strings.onboarding_notifications_mode_periodic), style = MaterialTheme.typography.h3, color = MaterialTheme.colors.secondary)
    ReadableText(MR.strings.onboarding_notifications_mode_periodic_desc)
    Spacer(Modifier.height(DEFAULT_PADDING_HALF))
    Text(stringResource(MR.strings.onboarding_notifications_mode_off), style = MaterialTheme.typography.h3, color = MaterialTheme.colors.secondary)
    ReadableText(MR.strings.onboarding_notifications_mode_off_desc)
  }
}

fun prepareChatBeforeFinishingOnboarding() {
  // No visible users but may have hidden. In this case chat should be started anyway because it's stopped on this stage with hidden users
  if (chatModel.users.any { u -> !u.user.hidden }) return
  withBGApi {
    val user = chatModel.controller.apiGetActiveUser(null) ?: return@withBGApi
    chatModel.currentUser.value = user
    chatModel.controller.startChat(user)
  }
}

fun createAddressAndUsernameForOnboarding(m: ChatModel, onComplete: () -> Unit) {
  withBGApi {
    val activeUser = m.controller.apiGetActiveUser(null)
    
    if (activeUser != null) {
      m.currentUser.value = activeUser
      if (m.chatRunning.value != true) {
        m.controller.startChat(activeUser)
      }
    }
    
    val currentUser = m.currentUser.value
    
    if (currentUser == null) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = "Error",
          text = "No user found. Please restart the app."
        )
      }
      return@withBGApi
    }
    
    val invitationResult = m.controller.apiAddContact(currentUser.remoteHostId, incognito = true)
    
    if (invitationResult.first == null) {
      if (invitationResult.second != null) {
        invitationResult.second?.invoke()
      }
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(MR.strings.error_creating_address),
          text = "Could not create invitation link. Please try again."
        )
      }
      return@withBGApi
    }
    
    val linkAndConnection = invitationResult.first!!
    val connReqContact = linkAndConnection.first
    val contactConnection = linkAndConnection.second
    
    // Generate random username prefix (5 lowercase letters) - don't use display name
    val usernamePrefix = generateRandomDisplayName()
    val simpleXAddress = connReqContact.connShortLink ?: connReqContact.connFullLink
    
    val response = UsernameAPI.registerUsername(
      displayName = usernamePrefix,
      simpleXAddress = simpleXAddress,
      domain = "Inco"
    )
    
    if (response == null || !response.success || response.data == null) {
      val errorMessage = response?.error ?: "Could not register username with server"
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(MR.strings.username_registration_failed),
          text = errorMessage
        )
      }
      return@withBGApi
    }
    
    // Store ONLY the private username - do NOT update the user's display name
    // The display name should come from the public profile (set by user)
    withContext(Dispatchers.Main) {
      m.setPrivateUsername(response.data.username!!)
      // Store the connection ID for tracking when this address is used
      m.usernameConnectionId.value = contactConnection.pccConnId
      // Reset usernameUsed flag (persisted across app restarts)
      m.setUsernameUsed(false)
      // Set username expiry to 5 minutes from now (persisted across app restarts)
      m.setUsernameExpiry()
    }
    
    onComplete()
  }
}

/**
 * Regenerates username by creating a new one-time address and registering it with the server
 * Updates the user's display name with the new username from the server
 */
suspend fun regenerateUsernameAndAddress(m: ChatModel, onComplete: (Boolean) -> Unit) {
  try {
    val activeUser = m.controller.apiGetActiveUser(null)
    if (activeUser == null) {
      withContext(Dispatchers.Main) {
        onComplete(false)
      }
      return
    }
    
    if (m.chatRunning.value != true) {
      m.controller.startChat(activeUser)
      delay(500)
    }
    
    val currentUser = m.currentUser.value
    if (currentUser == null) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = "Error",
          text = "No user found. Please restart the app."
        )
        onComplete(false)
      }
      return
    }
    
    val invitationResult = m.controller.apiAddContact(currentUser.remoteHostId, incognito = true)
    
    if (invitationResult.first == null) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = "Error",
          text = "Could not create invitation link. Please try again."
        )
        onComplete(false)
      }
      return
    }
    
    val linkAndConnection = invitationResult.first!!
    val connReqContact = linkAndConnection.first
    val pendingConnection = linkAndConnection.second
    
    // Generate random username prefix (5 lowercase letters)
    val usernamePrefix = generateRandomDisplayName()
    val simpleXAddress = connReqContact.connShortLink ?: connReqContact.connFullLink
    
    try {
      val response = UsernameAPI.registerUsername(
        displayName = usernamePrefix,
        simpleXAddress = simpleXAddress,
        domain = "Inco"
      )
      
      if (response?.success == true && response.data != null) {
        val newPrivateUsername = response.data.username!!
        
        // Store ONLY the private username - do NOT update the user's display name
        // The display name should come from the public profile
        withContext(Dispatchers.Main) {
          // Store private username separately (does not affect display name) - persisted
          m.setPrivateUsername(newPrivateUsername)
          // Store the connection ID for tracking when this address is used
          m.usernameConnectionId.value = pendingConnection.pccConnId
          // Reset usernameUsed flag since we have a new username (persisted)
          m.setUsernameUsed(false)
          // Set username expiry to 5 minutes from now (persisted across app restarts)
          m.setUsernameExpiry()
          onComplete(true)
        }
      } else {
        withContext(Dispatchers.Main) {
          AlertManager.shared.showAlertMsg(
            title = "Registration Failed",
            text = response?.error ?: "Unknown error"
          )
          onComplete(false)
        }
      }
    } catch (e: Exception) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = "Error",
          text = "Failed to register username: ${e.message}"
        )
        onComplete(false)
      }
    }
  } catch (e: Exception) {
    withContext(Dispatchers.Main) {
      onComplete(false)
    }
  }
}

private const val PUBLIC_TAG = "PublicProfile"

/**
 * Registers the public username with the permanent address
 * Format: xxxxx.123.link (server generates the number suffix)
 * This is the permanent public identity
 */
suspend fun registerPublicUsername(m: ChatModel, onComplete: (Boolean) -> Unit) {
  try {
    
    // Check if public username already exists — skip registration if user already chose one
    val existingPublicUsername = m.getPublicUsername()
    if (existingPublicUsername != null) {
      withContext(Dispatchers.Main) { onComplete(true) }
      return
    }
    
    // Get the permanent address
    val permanentAddress = m.userAddress.value?.connLinkContact?.connFullLink
    
    if (permanentAddress == null) {
      // Try to create permanent address if not exists
      val createdAddress = m.controller.apiCreateUserAddress(m.currentUser.value?.remoteHostId)
      
      if (createdAddress == null) {
        withContext(Dispatchers.Main) {
          onComplete(false)
        }
        return
      }
      
      
      // Store the created address
      withContext(Dispatchers.Main) {
        m.userAddress.value = UserContactLinkRec(
          connLinkContact = createdAddress,
          shortLinkDataSet = false,
          shortLinkLargeDataSet = false,
          addressSettings = AddressSettings(
            businessAddress = false,
            autoAccept = null,
            autoReply = null
          )
        )
      }
    }
    
    val connLink = m.userAddress.value?.connLinkContact
    val fullLink = connLink?.connFullLink
    val shortLink = connLink?.connShortLink
    
    
    // IMPORTANT: For public profiles, we MUST use the short link to avoid "large info" errors
    // The short link format (https://smpXX.simplex.im/i#KEY) is required for permanent addresses
    val addressToRegister = if (shortLink != null && shortLink.isNotEmpty()) {
      shortLink
    } else {
      
      // Try to extract short link from full link if possible
      if (fullLink != null && fullLink.contains("/i#")) {
        // Extract the short link portion from full link
        val shortLinkPart = "https://" + fullLink.substringAfter("smp=").substringBefore("&").substringBefore(",") + 
                           "/i#" + fullLink.substringAfter("/i#").substringBefore("&").substringBefore(",")
        shortLinkPart
      } else {
        null
      }
    }
    
    
    // Validate address format for public profiles
    if (addressToRegister != null && addressToRegister.length > 200) {
    }
    
    if (addressToRegister == null) {
      withContext(Dispatchers.Main) {
        onComplete(false)
      }
      return
    }
    
    // Generate username prefix (5 random letters)
    val usernamePrefix = m.generateUsernamePrefix()
    
    // Register with server using domain "link" for public profile
    
    // Run network call on IO dispatcher to avoid NetworkOnMainThreadException
    val response = withContext(Dispatchers.IO) {
      UsernameAPI.registerUsername(
        displayName = usernamePrefix,
        simpleXAddress = addressToRegister,
        domain = "link"
      )
    }
    
    
    if (response?.success == true && response.data?.username != null) {
      
      // Store the public username (format: xxxxx.123.link)
      withContext(Dispatchers.Main) {
        m.setPublicUsername(response.data.username!!)
        onComplete(true)
      }
    } else {
      withContext(Dispatchers.Main) {
        onComplete(false)
      }
    }
    
  } catch (e: Exception) {
    withContext(Dispatchers.Main) {
      onComplete(false)
    }
  }
}
