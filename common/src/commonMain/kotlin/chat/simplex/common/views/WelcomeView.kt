package chat.simplex.common.views

import SectionTextFooter
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatModel.controller
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.*
import chat.simplex.common.views.usersettings.SettingsActionItem
import chat.simplex.common.views.migration.MigrateToDeviceView
import chat.simplex.common.views.migration.MigrationToState
import chat.simplex.res.MR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "WelcomeView"
const val MAX_BIO_LENGTH_BYTES = 160

/**
 * Remove .onion references from query parameters in address
 * Input: https://smp8.simplex.im/i#KEY/%3Fv=1-4%26srv=xxx.onion (URL-encoded)
 * Output: https://smp8.simplex.im/i#KEY/%3Fv=1-4 (cleaned and re-encoded)
 */
private fun removeOnionFromAddress(address: String): String {
    try {
        
        // Check for both regular and URL-encoded query parameters
        val hasEncodedQuery = address.contains("%3F") || address.contains("%3f")
        val hasRegularQuery = address.contains("?")
        
        if (!hasEncodedQuery && !hasRegularQuery) {
            return address
        }
        
        // Decode the entire address first
        val decoded = address
            .replace("%3F", "?")
            .replace("%3f", "?")
            .replace("%26", "&")
            .replace("%3D", "=")
            .replace("%253D", "=")
        
        
        // Split into base and query params
        val parts = decoded.split("?", limit = 2)
        if (parts.size != 2) return address
        
        val baseAddress = parts[0]
        val queryString = parts[1]
        
        // Parse query parameters and filter out srv=...onion
        val params = queryString.split("&")
        val filteredParams = params.filter { param ->
            val shouldKeep = !(param.startsWith("srv=") && param.contains(".onion"))
            if (!shouldKeep) {
            }
            shouldKeep
        }
        
        // Rebuild and re-encode
        val cleanAddress = if (filteredParams.isEmpty()) {
            // Remove trailing slash if query was removed
            baseAddress.trimEnd('/')
        } else {
            val queryPart = filteredParams.joinToString("&")
            // Re-encode the query string
            val encodedQuery = queryPart
                .replace("=", "%3D")
                .replace("&", "%26")
            // Make sure baseAddress ends with exactly one slash
            val base = baseAddress.trimEnd('/')
            "$base/%3F$encodedQuery"
        }
        
        return cleanAddress
    } catch (e: Exception) {
        return address
    }
}

/**
 * Extract and convert SimpleX address to HTTPS format expected by the server
 * Input: https://simplex.chat/contact#/?v=1&smp=smp%3A%2F%2FKEY%40server%2Fqueue%23e2e
 * Output: https://server/i#queue
 * 
 * Or if already in HTTPS format: https://smp15.simplex.im/i#key
 * Output: same (already correct)
 */
fun extractSimplexAddress(fullLink: String): String {
    try {
        
        // If it's already in the HTTPS /i# format, clean and return it
        if (fullLink.startsWith("https://") && fullLink.contains("/i#")) {
            return removeOnionFromAddress(fullLink)
        }
        
        // If it's smp:// format, convert to HTTPS
        if (fullLink.startsWith("smp://")) {
            return removeOnionFromAddress(convertSmpToHttps(fullLink))
        }
        
        // Extract the fragment part after #
        val hashIndex = fullLink.indexOf("#")
        if (hashIndex == -1) {
            return removeOnionFromAddress(fullLink)
        }
        
        val fragment = fullLink.substring(hashIndex + 1)
        
        // Extract smp parameter from query string
        val params = fragment.substringAfter("?").split("&")
        for (param in params) {
            if (param.startsWith("smp=")) {
                val smpEncoded = param.substring(4)
                
                // URL decode the smp value
                val smpValue = smpEncoded
                    .replace("%3A", ":")
                    .replace("%2F", "/")
                    .replace("%3D", "=")
                    .replace("%40", "@")
                    .replace("%23", "#")
                
                
                // Convert smp:// to https:// format and remove onion
                return removeOnionFromAddress(convertSmpToHttps(smpValue))
            }
        }
    } catch (e: Exception) {
    }
    
    // Fallback: return original if parsing fails
    return removeOnionFromAddress(fullLink)
}

/**
 * Convert SMP protocol address to HTTPS format
 * Input: smp://KEY@smp15.simplex.im/QUEUE#E2E
 * Output: https://smp15.simplex.im/i#QUEUEE2E
 * 
 * If multiple servers (comma-separated), filters out .onion addresses and returns only public address
 */
private fun convertSmpToHttps(smpAddress: String): String {
    try {
        if (!smpAddress.startsWith("smp://")) {
            return smpAddress
        }
        
        // Parse: smp://KEY@server/queue#e2e or smp://KEY@server1,server2.onion/queue#e2e
        val withoutProtocol = smpAddress.substring(6) // Remove "smp://"
        val atIndex = withoutProtocol.indexOf("@")
        if (atIndex == -1) return smpAddress
        
        val serverAndPath = withoutProtocol.substring(atIndex + 1) // Get "server/queue#e2e"
        val parts = serverAndPath.split("/", limit = 2)
        if (parts.size < 2) return smpAddress
        
        val serversStr = parts[0] // "smp15.simplex.im" or "smp15.simplex.im,something.onion"
        val queueAndKey = parts[1] // "queue#e2e"
        
        // If multiple servers (comma-separated), filter out .onion addresses
        val server = if (serversStr.contains(",")) {
            // Multiple servers - pick the non-onion one
            val servers = serversStr.split(",")
            servers.firstOrNull { !it.contains(".onion") } ?: servers.first()
        } else {
            serversStr
        }
        
        
        // Format: https://server/i#queuee2e (remove the # and concatenate)
        val httpsAddress = "https://$server/i#${queueAndKey.replace("#", "")}"
        
        return httpsAddress
    } catch (e: Exception) {
        return smpAddress
    }
}

fun bioFitsLimit(bio: String): Boolean {
  return chatJsonLength(bio) <= MAX_BIO_LENGTH_BYTES
}

private fun getDefaultProfileImage(): String? {
  return try {
    val imageBitmap = MR.images.ic_avatar_1.toComposeImageBitmap() ?: return null
    resizeImageToStrSize(imageBitmap, maxDataSize = 12500)
  } catch (e: Exception) {
    null
  }
}

@Composable
fun CreateProfile(chatModel: ChatModel, close: () -> Unit) {
  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()
  val keyboardState by getKeyboardState()
  var savedKeyboardState by remember { mutableStateOf(keyboardState) }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 20.dp)
    ) {
      val displayName = rememberSaveable { mutableStateOf("Set Up Name") }
      val shortDescr = rememberSaveable { mutableStateOf("") }
      val focusRequester = remember { FocusRequester() }

      ColumnWithScrollBar {
        Column(Modifier.padding(horizontal = DEFAULT_PADDING)) {
          AppBarTitle(stringResource(MR.strings.create_profile), withPadding = false, bottomPadding = DEFAULT_PADDING)
          Row(Modifier.padding(bottom = DEFAULT_PADDING_HALF).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              "Set Public Name",  // This is the PUBLIC profile name - visible to contacts
              fontSize = 16.sp
            )
            val name = displayName.value.trim()
            val validName = mkValidName(name)
            Spacer(Modifier.height(20.dp))
            if (name != validName) {
              IconButton({ showInvalidNameAlert(mkValidName(displayName.value), displayName) }, Modifier.size(20.dp)) {
                Icon(painterResource(MR.images.ic_info), null, tint = MaterialTheme.colors.error)
              }
            }
          }
          ProfileNameField(displayName, "", { it.trim() == mkValidName(it) }, focusRequester)

          Spacer(Modifier.height(DEFAULT_PADDING))

          Row(Modifier.padding(bottom = DEFAULT_PADDING_HALF).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              stringResource(MR.strings.short_descr),
              fontSize = 16.sp
            )
            Spacer(Modifier.height(20.dp))
            if (!bioFitsLimit(shortDescr.value)) {
              IconButton(
                onClick = { AlertManager.shared.showAlertMsg(title = generalGetString(MR.strings.bio_too_large)) },
                Modifier.size(20.dp)) {
                Icon(painterResource(MR.images.ic_info), null, tint = MaterialTheme.colors.error)
              }
            }
          }
          ProfileNameField(shortDescr, "", isValid = { bioFitsLimit(it) })
        }
        SettingsActionItem(
          painterResource(MR.images.ic_check),
          stringResource(MR.strings.create_another_profile_button),
          disabled = !canCreateProfile(displayName.value) || !bioFitsLimit(shortDescr.value),
          textColor = MaterialTheme.colors.primary,
          iconColor = MaterialTheme.colors.primary,
          click = {
            if (chatModel.localUserCreated.value == true) {
              createProfileInProfiles(chatModel, displayName.value, shortDescr.value, close)
            } else {
              createProfileInNoProfileSetup(displayName.value, close)
            }
          },
        )
        SectionTextFooter(generalGetString(MR.strings.your_profile_is_stored_on_your_device))
        SectionTextFooter(generalGetString(MR.strings.profile_is_only_shared_with_your_contacts))

        LaunchedEffect(Unit) {
          delay(300)
          focusRequester.requestFocus()
        }
      }
      if (savedKeyboardState != keyboardState) {
        LaunchedEffect(keyboardState) {
          scope.launch {
            savedKeyboardState = keyboardState
            scrollState.animateScrollTo(scrollState.maxValue)
          }
        }
      }
    }
}

@Composable
fun CreateFirstProfile(chatModel: ChatModel, close: () -> Unit) {
  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()
  val keyboardState by getKeyboardState()
  var savedKeyboardState by remember { mutableStateOf(keyboardState) }
  CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
    ModalView({
      if (chatModel.users.none { !it.user.hidden }) {
        appPrefs.onboardingStage.set(OnboardingStage.Step2_5_SetupDatabasePassphrase)
      } else {
        close()
      }
    }) {
      ColumnWithScrollBar {
        val displayName = rememberSaveable { mutableStateOf("Set Up Name") }
        val focusRequester = remember { FocusRequester() }
        Column(if (appPlatform.isAndroid) Modifier.fillMaxSize().padding(start = DEFAULT_ONBOARDING_HORIZONTAL_PADDING * 2, end = DEFAULT_ONBOARDING_HORIZONTAL_PADDING * 2, bottom = DEFAULT_PADDING) else Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(horizontal = DEFAULT_PADDING).align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.align(Alignment.CenterHorizontally)) {
            AppBarTitle(stringResource(MR.strings.create_your_profile), bottomPadding = DEFAULT_PADDING, withPadding = false)
          }
          ReadableText(MR.strings.your_profile_is_stored_on_your_device, TextAlign.Center, padding = PaddingValues(), style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.secondary))
          Spacer(Modifier.height(DEFAULT_PADDING))
          ReadableText(MR.strings.profile_is_only_shared_with_your_contacts, TextAlign.Center, style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.secondary))
          Spacer(Modifier.height(DEFAULT_PADDING))
          ProfileNameField(displayName, "Enter your public name", { it.trim() == mkValidName(it) }, focusRequester)
        }
        Spacer(Modifier.fillMaxHeight().weight(1f))
        Column(Modifier.widthIn(max = if (appPlatform.isAndroid) 450.dp else 1000.dp).align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
          OnboardingActionButton(
            if (appPlatform.isAndroid) Modifier.padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING).fillMaxWidth() else Modifier.widthIn(min = 300.dp),
            labelId = MR.strings.create_profile_button,
            onboarding = null,
            enabled = canCreateProfile(displayName.value),
            onclick = { createProfileOnboarding(chat.simplex.common.platform.chatModel, displayName.value, close) }
          )
          TextButtonBelowOnboardingButton(stringResource(MR.strings.migrate_from_another_device)) {
            chatModel.migrationState.value = MigrationToState.PasteOrScanLink
            ModalManager.fullscreen.showCustomModal { close -> MigrateToDeviceView(close) }
          }
        }

        LaunchedEffect(Unit) {
          delay(300)
          focusRequester.requestFocus()
        }
      }
      LaunchedEffect(Unit) {
        setLastVersionDefault(chatModel)
      }
      LaunchedEffect(Unit) {
        if (chatModel.migrationState.value != null && !ModalManager.fullscreen.hasModalsOpen()) {
          ModalManager.fullscreen.showCustomModal(animated = false) { close -> MigrateToDeviceView(close) }
        }
      }
      if (savedKeyboardState != keyboardState) {
        LaunchedEffect(keyboardState) {
          scope.launch {
            savedKeyboardState = keyboardState
            scrollState.animateScrollTo(scrollState.maxValue)
          }
        }
      }
    }
  }
}

fun createProfileInNoProfileSetup(displayName: String, close: () -> Unit) {
  withBGApi {
    val defaultImage = getDefaultProfileImage()
    
    val profile = Profile(displayName.trim(), "", null, defaultImage)
    
    val createdUser = controller.apiCreateActiveUser(null, profile) ?: return@withBGApi
    if (createdUser.profile.image != null) {
    }
    
    chatModel.currentUser.value = createdUser
    
    if (!chatModel.connectedToRemote()) {
      chatModel.localUserCreated.value = true
    }

    // Initialize global auto-delete ("delete messages after") default for newly created profile.
    controller.ensureDefaultChatItemTTLInitialized(null)
    controller.getUserChatData(null)
    
    // Create one-time invitation link for username registration
    val invitationResult = chatModel.controller.apiAddContact(null, incognito = true)
    
    if (invitationResult.first == null) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(MR.strings.error_creating_address),
          text = "Could not create invitation link. Please try again."
        )
      }
      return@withBGApi
    }
    
    val linkAndConnection = invitationResult.first!!
    val connReqContact = linkAndConnection.first  // CreatedConnLink
    val contactConnection = linkAndConnection.second  // PendingContactConnection
    
    
    // Register username with server - REQUIRED
    // Generate random username prefix (5 lowercase letters) - don't use display name
    val usernamePrefix = generateRandomDisplayName()
    // Use connShortLink (https://smpXX.simplex.im/i#KEY format)
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
    
    
    // Store ONLY the private username - do NOT change the profile's displayName
    // The user's chosen displayName is their PUBLIC profile name
    withContext(Dispatchers.Main) {
      chatModel.setPrivateUsername(response.data.username!!)
    }
    
    
    // Only proceed if everything succeeded
    controller.appPrefs.onboardingStage.set(OnboardingStage.Step3_ChooseServerOperators)
    controller.startChat(chatModel.currentUser.value!!)
    controller.getUserChatData(null)
    controller.switchUIRemoteHost(null)
    close()
  }
}

fun createProfileInProfiles(chatModel: ChatModel, displayName: String, shortDescr: String, close: () -> Unit) {
  withBGApi {
    val rhId = chatModel.remoteHostId()
    val defaultImage = getDefaultProfileImage()
    
    val profile = Profile(displayName.trim(), "", shortDescr.trim().ifEmpty { null }, defaultImage)
    
    val createdUser = chatModel.controller.apiCreateActiveUser(rhId, profile) ?: return@withBGApi
    
    chatModel.currentUser.value = createdUser
    
    // Create one-time invitation link for username registration
    val invitationResult = chatModel.controller.apiAddContact(rhId, incognito = true)
    
    if (invitationResult.first == null) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(MR.strings.error_creating_address),
          text = "Could not create invitation link. Please try again."
        )
      }
      return@withBGApi
    }
    
    val linkAndConnection = invitationResult.first!!
    val connReqContact = linkAndConnection.first  // CreatedConnLink
    val contactConnection = linkAndConnection.second  // PendingContactConnection
    
    
    // Register username - REQUIRED
    // Generate random username prefix (5 lowercase letters) - don't use display name
    val usernamePrefix = generateRandomDisplayName()
    // Send connFullLink directly - API will handle the format
    val simpleXAddress = connReqContact.connFullLink
    
    
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
    
    
    // Store ONLY the private username - do NOT change the profile's displayName
    // The user's chosen displayName is their PUBLIC profile name
    chatModel.setPrivateUsername(response.data.username!!)
    
    
    // Only proceed if everything succeeded
    if (chatModel.users.isEmpty()) {
      // Initialize global auto-delete ("delete messages after") default for newly created profile.
      chatModel.controller.ensureDefaultChatItemTTLInitialized(rhId)
      chatModel.controller.startChat(chatModel.currentUser.value!!)
      chatModel.controller.getUserChatData(rhId)
      chatModel.controller.appPrefs.onboardingStage.set(OnboardingStage.Step4_SetNotificationsMode)
    } else {
      val users = chatModel.controller.listUsers(rhId)
      chatModel.users.clear()
      chatModel.users.addAll(users)
      chatModel.controller.ensureDefaultChatItemTTLInitialized(rhId)
      chatModel.controller.getUserChatData(rhId)
      close()
    }
  }
}

fun createProfileOnboarding(chatModel: ChatModel, displayName: String, close: () -> Unit) {
  withBGApi {
    
    val defaultImage = getDefaultProfileImage()
    
    val profile = Profile(displayName.trim(), "", null, defaultImage)
    
    val createdUser = chatModel.controller.apiCreateActiveUser(null, profile) ?: return@withBGApi
    
    chatModel.currentUser.value = createdUser
    chatModel.localUserCreated.value = true

    // Initialize global auto-delete ("delete messages after") default for newly created profile.
    chatModel.controller.ensureDefaultChatItemTTLInitialized(null)
    chatModel.controller.getUserChatData(null)
    
    // Create one-time invitation link for username registration
    val invitationResult = chatModel.controller.apiAddContact(null, incognito = true)
    
    if (invitationResult.first == null) {
      withContext(Dispatchers.Main) {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(MR.strings.error_creating_address),
          text = "Could not create invitation link. Please try again."
        )
      }
      return@withBGApi
    }
    
    val linkAndConnection = invitationResult.first!!
    val connReqContact = linkAndConnection.first  // CreatedConnLink
    val contactConnection = linkAndConnection.second  // PendingContactConnection
    
    
    // Register username with server - REQUIRED
    // Generate random username prefix (5 lowercase letters) - don't use display name
    val usernamePrefix = generateRandomDisplayName()
    
    // Send connFullLink directly - API will handle the format
    val simpleXAddress = connReqContact.connFullLink
    
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
    
    
    // Store ONLY the private username - do NOT change the profile's displayName
    // The user's chosen displayName is their PUBLIC profile name
    withContext(Dispatchers.Main) {
      chatModel.setPrivateUsername(response.data.username!!)
    }
    
    // Only proceed if everything succeeded
    val onboardingStage = chatModel.controller.appPrefs.onboardingStage
    // No users or no visible users
    if (chatModel.users.none { u -> !u.user.hidden }) {
      onboardingStage.set(if (appPlatform.isDesktop && chatModel.controller.appPrefs.initialRandomDBPassphrase.get() && !chatModel.desktopOnboardingRandomPassword.value) {
        OnboardingStage.Step2_5_SetupDatabasePassphrase
      } else {
        OnboardingStage.Step3_ChooseServerOperators
      })
    } else {
      // the next two lines are only needed for failure case when because of the database error the app gets stuck on on-boarding screen,
      // this will get it unstuck.
      onboardingStage.set(OnboardingStage.OnboardingComplete)
      close()
    }
  }
}

@Composable
fun ProfileNameField(name: MutableState<String>, placeholder: String = "", isValid: (String) -> Boolean = { true }, focusRequester: FocusRequester? = null) {
  var valid by rememberSaveable { mutableStateOf(true) }
  var focused by rememberSaveable { mutableStateOf(false) }
  val strokeColor by remember {
    derivedStateOf {
      if (valid) {
        if (focused) {
          CurrentColors.value.colors.secondary.copy(alpha = 0.6f)
        } else {
          CurrentColors.value.colors.secondary.copy(alpha = 0.3f)
        }
      } else Color.Red
    }
  }
  val modifier = Modifier
    .fillMaxWidth()
    .heightIn(min = 50.dp)
    .onFocusChanged { focused = it.isFocused }
  Column(
    Modifier
      .fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    BasicTextField(
      value = name.value,
      onValueChange = { name.value = it },
      modifier = if (focusRequester == null) modifier else modifier.focusRequester(focusRequester),
      textStyle = TextStyle(fontSize = 18.sp, color = colors.onBackground),
      singleLine = true,
      cursorBrush = SolidColor(MaterialTheme.colors.secondary),
      decorationBox = @Composable { innerTextField ->
        TextFieldDefaults.TextFieldDecorationBox(
          value = name.value,
          innerTextField = innerTextField,
          placeholder = if (placeholder != "") {{ Text(placeholder, style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.secondary, lineHeight = 22.sp)) }} else null,
          contentPadding = PaddingValues(),
          label = null,
          visualTransformation = VisualTransformation.None,
          leadingIcon = null,
          trailingIcon = if (!valid && placeholder != "") {
            {
              IconButton({ showInvalidNameAlert(mkValidName(name.value), name) }, Modifier.size(20.dp)) {
                Icon(painterResource(MR.images.ic_info), null, tint = MaterialTheme.colors.error)
              }
            }
          } else null,
          singleLine = true,
          enabled = true,
          isError = false,
          interactionSource = remember { MutableInteractionSource() },
          colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Unspecified)
        )
      }
    )
    Divider(color = strokeColor)
  }
  LaunchedEffect(Unit) {
    snapshotFlow { name.value }
      .distinctUntilChanged()
      .collect {
        valid = isValid(it)
      }
  }
}

private fun canCreateProfile(displayName: String): Boolean {
  val name = displayName.trim()
  return name.isNotEmpty() && mkValidName(name) == name
}

fun showInvalidNameAlert(name: String, displayName: MutableState<String>) {
  if (name.isEmpty()) {
    AlertManager.shared.showAlertMsg(
      title = generalGetString(MR.strings.invalid_name),
    )
  } else {
    AlertManager.shared.showAlertDialog(
      title = generalGetString(MR.strings.invalid_name),
      text = generalGetString(MR.strings.correct_name_to).format(name),
      onConfirm = {
        displayName.value = name
      }
    )
  }
}

fun isValidDisplayName(name: String) : Boolean = mkValidName(name.trim()) == name

fun mkValidName(s: String): String = chatValidName(s)
