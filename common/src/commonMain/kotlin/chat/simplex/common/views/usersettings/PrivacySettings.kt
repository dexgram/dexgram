package chat.simplex.common.views.usersettings

import SectionBottomSpacer
import SectionDividerSpaced
import SectionItemView
import SectionTextFooter
import SectionView
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.res.MR
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.ProfileNameField
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.helpers.DatabaseUtils.ksAppPassword
import chat.simplex.common.views.helpers.DatabaseUtils.ksSelfDestructPassword
import chat.simplex.common.views.isValidDisplayName
import chat.simplex.common.views.localauth.SetAppPasscodeView
import chat.simplex.common.views.onboarding.ReadableText
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import kotlinx.coroutines.*

enum class LAMode {
  SYSTEM,
  PASSCODE;

  val text: String
    get() = when (this) {
      SYSTEM -> generalGetString(MR.strings.la_mode_system)
      PASSCODE -> generalGetString(MR.strings.la_mode_passcode)
    }

  companion object {
    val default: LAMode
      get() = if (appPlatform == AppPlatform.ANDROID) SYSTEM else PASSCODE
  }
}

@Composable
fun PrivacySettingsView(
  chatModel: ChatModel,
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  setPerformLA: (Boolean) -> Unit
) {
  BackHandler(onBack = { ModalManager.start.closeModal() })
  val performLA = remember { appPrefs.performLA.state }
  val currentLAMode = remember { chatModel.controller.appPrefs.laMode }

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
              painterResource(MR.images.ic_arrow_back_ios_new),
              contentDescription = "Back",
              tint = MaterialTheme.colors.onBackground
            )
          }
          Text(
            stringResource(MR.strings.privacy_and_security),
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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(Modifier.height(16.dp))

      PrivacyDexItem(
        icon = if (performLA.value) painterResource(MR.images.ic_lock_filled) else painterResource(MR.images.ic_lock),
        text = stringResource(MR.strings.chat_lock),
        onClick = {
          ModalManager.start.showCustomModal { close ->
            ModalView(
              close = close,
              showClose = false,
              showAppBar = false
            ) {
              BackHandler(onBack = close)
              SimplexLockView(chatModel, currentLAMode, setPerformLA, close)
            }
          }
        },
        iconTint = if (performLA.value) SimplexGreen else MaterialTheme.colors.onBackground,
        trailing = {
          Text(
            if (performLA.value) remember { currentLAMode.state }.value.text else generalGetString(MR.strings.la_mode_off),
            fontSize = 13.sp,
            fontFamily = DMSans,
            color = MaterialTheme.colors.secondary
          )
        }
      )

      PrivacyDexToggleItemInternal(
        icon = painterResource(MR.images.ic_image),
        text = stringResource(MR.strings.auto_accept_images),
        pref = chatModel.controller.appPrefs.privacyAcceptImages
      )

      val currentUser = chatModel.currentUser.value
      if (currentUser != null && !chatModel.desktopNoUserNoRemote) {
        PrivacyDexToggleItem(
          icon = painterResource(MR.images.ic_check),
          text = stringResource(MR.strings.auto_accept_contact),
          checked = currentUser.autoAcceptMemberContacts,
          onCheckedChange = { enable ->
            withApi {
              chatModel.controller.apiSetUserAutoAcceptMemberContacts(currentUser, enable)
              chatModel.currentUser.value = currentUser.copy(autoAcceptMemberContacts = enable)
            }
          }
        )
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun PrivacyDexItem(
  icon: Painter,
  text: String,
  onClick: (() -> Unit)? = null,
  iconTint: Color = MaterialTheme.colors.onBackground,
  trailing: @Composable (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
      .padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colors.surface),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = text, modifier = Modifier.size(22.dp), tint = iconTint)
    }
    Spacer(Modifier.width(14.dp))
    Text(
      text,
      modifier = Modifier.weight(1f),
      fontSize = 15.sp,
      fontFamily = DMSans,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colors.onBackground
    )
    if (trailing != null) {
      trailing()
    }
    Icon(
      painterResource(MR.images.ic_chevron_right),
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = MaterialTheme.colors.secondary.copy(alpha = 0.6f)
    )
  }
}

@Composable
internal fun PrivacyDexToggleItemInternal(
  icon: Painter,
  text: String,
  pref: SharedPreference<Boolean>,
  onCheckedChange: ((Boolean) -> Unit)? = null
) {
  val checked = remember { pref.state }
  PrivacyDexToggleItem(
    icon = icon,
    text = text,
    checked = checked.value,
    onCheckedChange = { value ->
      pref.set(value)
      onCheckedChange?.invoke(value)
    }
  )
}

@Composable
private fun PrivacyDexToggleItem(
  icon: Painter,
  text: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colors.surface),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = text, modifier = Modifier.size(22.dp), tint = MaterialTheme.colors.onBackground)
    }
    Spacer(Modifier.width(14.dp))
    Text(
      text,
      modifier = Modifier.weight(1f),
      fontSize = 15.sp,
      fontFamily = DMSans,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colors.onBackground
    )
    DefaultSwitch(
      checked = checked,
      onCheckedChange = onCheckedChange
    )
  }
}

@Composable
expect fun ProtectScreenToggle(chatModel: ChatModel)

@Composable
fun SimpleXLinkOptions(simplexLinkModeState: State<SimplexLinkMode>, onSelected: (SimplexLinkMode) -> Unit) {
  val modeValues = listOf(SimplexLinkMode.DESCRIPTION, SimplexLinkMode.FULL)
  val pickerValues = modeValues + if (modeValues.contains(simplexLinkModeState.value)) emptyList() else listOf(simplexLinkModeState.value)
  val values = remember {
    pickerValues.map {
      when (it) {
        SimplexLinkMode.DESCRIPTION -> it to generalGetString(MR.strings.simplex_link_mode_description)
        SimplexLinkMode.FULL -> it to generalGetString(MR.strings.simplex_link_mode_full)
        SimplexLinkMode.BROWSER -> it to generalGetString(MR.strings.simplex_link_mode_browser)
      }
    }
  }
  ExposedDropDownSettingRow(
    generalGetString(MR.strings.simplex_link_mode),
    values,
    simplexLinkModeState,
    icon = null,
    enabled = remember { mutableStateOf(true) },
    onSelected = onSelected
  )
}

@Composable
private fun BlurRadiusOptions(state: State<Int>, onSelected: (Int) -> Unit) {
  val choices = listOf(0, 12, 24, 48)
  val pickerValues = choices + if (choices.contains(state.value)) emptyList() else listOf(state.value)
  val values = remember {
    pickerValues.map {
      when (it) {
        0 -> it to generalGetString(MR.strings.privacy_media_blur_radius_off)
        12 -> it to generalGetString(MR.strings.privacy_media_blur_radius_soft)
        24 -> it to generalGetString(MR.strings.privacy_media_blur_radius_medium)
        48 -> it to generalGetString(MR.strings.privacy_media_blur_radius_strong)
        else -> it to "$it"
      }
    }
  }
  ExposedDropDownSettingRow(
    generalGetString(MR.strings.privacy_media_blur_radius),
    values,
    state,
    icon = painterResource(MR.images.ic_blur_on),
    onSelected = onSelected
  )
}

@Composable
expect fun PrivacyDeviceSection(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  setPerformLA: (Boolean) -> Unit,
)

@Composable
private fun ContacRequestsFromGroupsSection(
  currentUser: User,
  setAutoAcceptGrpDirectInvs: (Boolean) -> Unit
) {
  SectionView(stringResource(MR.strings.settings_section_title_contact_requests_from_groups)) {
    SettingsActionItemWithContent(painterResource(MR.images.ic_check), stringResource(MR.strings.auto_accept_contact)) {
      DefaultSwitch(
        checked = currentUser.autoAcceptMemberContacts,
        onCheckedChange = { enable ->
          setAutoAcceptGrpDirectInvs(enable)
        }
      )
    }
  }
  SectionTextFooter(
    remember(currentUser.displayName) {
      buildAnnotatedString {
        append(generalGetString(MR.strings.this_setting_is_for_your_current_profile) + " ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
          append(currentUser.displayName)
        }
        append(".")
      }
    }
  )
}

@Composable
private fun DeliveryReceiptsSection(
  currentUser: User,
  setOrAskSendReceiptsContacts: (Boolean) -> Unit,
  setOrAskSendReceiptsGroups: (Boolean) -> Unit,
) {
  SectionView(stringResource(MR.strings.settings_section_title_delivery_receipts)) {
    SettingsActionItemWithContent(painterResource(MR.images.ic_person), stringResource(MR.strings.receipts_section_contacts)) {
      DefaultSwitch(
        checked = currentUser.sendRcptsContacts,
        onCheckedChange = { enable ->
          setOrAskSendReceiptsContacts(enable)
        }
      )
    }
    SettingsActionItemWithContent(painterResource(MR.images.ic_group), stringResource(MR.strings.receipts_section_groups)) {
      DefaultSwitch(
        checked = currentUser.sendRcptsSmallGroups,
        onCheckedChange = { enable ->
          setOrAskSendReceiptsGroups(enable)
        }
      )
    }
  }
  SectionTextFooter(
    remember(currentUser.displayName) {
      buildAnnotatedString {
        append(generalGetString(MR.strings.receipts_section_description) + " ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
          append(currentUser.displayName)
        }
        append(".\n")
        append(generalGetString(MR.strings.receipts_section_description_1))
      }
    }
  )
}

private fun showUserContactsReceiptsAlert(
  enable: Boolean,
  contactReceiptsOverrides: Int,
  setSendReceiptsContacts: (Boolean, Boolean) -> Unit
) {
  AlertManager.shared.showAlertDialogButtonsColumn(
    title = generalGetString(if (enable) MR.strings.receipts_contacts_title_enable else MR.strings.receipts_contacts_title_disable),
    text = AnnotatedString(String.format(generalGetString(if (enable) MR.strings.receipts_contacts_override_disabled else MR.strings.receipts_contacts_override_enabled), contactReceiptsOverrides)),
    buttons = {
      Column {
        SectionItemView({
          AlertManager.shared.hideAlert()
          setSendReceiptsContacts(enable, false)
        }) {
          val t = stringResource(if (enable) MR.strings.receipts_contacts_enable_keep_overrides else MR.strings.receipts_contacts_disable_keep_overrides)
          Text(t, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.primary)
        }
        SectionItemView({
          AlertManager.shared.hideAlert()
          setSendReceiptsContacts(enable, true)
        }
        ) {
          val t = stringResource(if (enable) MR.strings.receipts_contacts_enable_for_all else MR.strings.receipts_contacts_disable_for_all)
          Text(t, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.error)
        }
        SectionItemView({
          AlertManager.shared.hideAlert()
        }) {
          Text(stringResource(MR.strings.cancel_verb), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.onBackground)
        }
      }
    }
  )
}

private fun showUserGroupsReceiptsAlert(
  enable: Boolean,
  groupReceiptsOverrides: Int,
  setSendReceiptsGroups: (Boolean, Boolean) -> Unit
) {
  AlertManager.shared.showAlertDialogButtonsColumn(
    title = generalGetString(if (enable) MR.strings.receipts_groups_title_enable else MR.strings.receipts_groups_title_disable),
    text = AnnotatedString(String.format(generalGetString(if (enable) MR.strings.receipts_groups_override_disabled else MR.strings.receipts_groups_override_enabled), groupReceiptsOverrides)),
    buttons = {
      Column {
        SectionItemView({
          AlertManager.shared.hideAlert()
          setSendReceiptsGroups(enable, false)
        }) {
          val t = stringResource(if (enable) MR.strings.receipts_groups_enable_keep_overrides else MR.strings.receipts_groups_disable_keep_overrides)
          Text(t, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.primary)
        }
        SectionItemView({
          AlertManager.shared.hideAlert()
          setSendReceiptsGroups(enable, true)
        }
        ) {
          val t = stringResource(if (enable) MR.strings.receipts_groups_enable_for_all else MR.strings.receipts_groups_disable_for_all)
          Text(t, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.error)
        }
        SectionItemView({
          AlertManager.shared.hideAlert()
        }) {
          Text(stringResource(MR.strings.cancel_verb), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.onBackground)
        }
      }
    }
  )
}

private val laDelays = listOf(10, 30, 60, 180, 600, 0)

@Composable
fun SimplexLockView(
  chatModel: ChatModel,
  currentLAMode: SharedPreference<LAMode>,
  setPerformLA: (Boolean) -> Unit,
  close: (() -> Unit)? = null
) {
  // Track if lock was already set up before entering this view
  val wasAlreadySetup = remember { appPrefs.performLA.get() }
  // Start with no selection - user must choose
  val selectedMode = remember { mutableStateOf<LAMode?>(if (wasAlreadySetup) currentLAMode.state.value else null) }
  val lockTimeout = remember { mutableStateOf(chatModel.controller.appPrefs.laLockDelay.get()) }
  val showAuthScreen = remember { chatModel.showAuthScreen }
  val performLA = remember { appPrefs.performLA.state }
  val laMode = remember { chatModel.controller.appPrefs.laMode.state }
  val laLockDelay = remember { chatModel.controller.appPrefs.laLockDelay }
  val showChangePasscode = remember { derivedStateOf { performLA.value && currentLAMode.state.value == LAMode.PASSCODE } }
  val selfDestructPref = remember { chatModel.controller.appPrefs.selfDestruct }
  val showPasscodeSetup = remember { mutableStateOf(false) }
  val showDuressSetup = remember { mutableStateOf(false) }
  val showSystemAuthSuccessDialog = remember { mutableStateOf(false) }

  val timeoutOptions = listOf(10, 30, 60, 180, 300, 600, 1800, 3600)
  val expanded = remember { mutableStateOf(false) }

  fun getTimeoutLabel(seconds: Int): String {
    return when {
      seconds < 60 -> "$seconds seconds"
      seconds < 3600 -> "${seconds / 60} ${if (seconds / 60 == 1) "minute" else "minutes"}"
      else -> "${seconds / 3600} ${if (seconds / 3600 == 1) "hour" else "hours"}"
    }
  }

  fun disableUnavailableLA() {
    chatModel.controller.appPrefs.performLA.set(false)
    chatModel.showAuthScreen.value = false
    currentLAMode.set(LAMode.default)
    laUnavailableInstructionAlert()
  }

  fun resetSelfDestruct() {
    selfDestructPref.set(false)
    ksSelfDestructPassword.remove()
  }

  fun setupLockMode(toLAMode: LAMode) {
    // Set the mode and notice shown flag
    chatModel.controller.appPrefs.laMode.set(toLAMode)
    chatModel.controller.appPrefs.laNoticeShown.set(true)
    currentLAMode.set(toLAMode)
    
    when (toLAMode) {
      LAMode.SYSTEM -> {
        // System authentication - trigger system auth
        authenticate(
          generalGetString(MR.strings.auth_enable_simplex_lock),
          promptSubtitle = "",
          usingLAMode = toLAMode,
          oneTime = true
        ) { laResult ->
          when (laResult) {
            LAResult.Success -> {
              // Mark as selected and set performLA to indicate setup is complete
              selectedMode.value = toLAMode
              chatModel.controller.appPrefs.performLA.set(true)
              showAuthScreen.value = true
              ksAppPassword.remove()
              resetSelfDestruct()
              
              // Show Shredgram-style success dialog with blur background
              showSystemAuthSuccessDialog.value = true
            }
            is LAResult.Unavailable, is LAResult.Error -> {
              laFailedAlert()
              selectedMode.value = null
            }
            is LAResult.Failed -> {
              selectedMode.value = null
            }
          }
        }
      }
      LAMode.PASSCODE -> {
        // Show passcode setup within the same view
        showPasscodeSetup.value = true
      }
    }
  }

  fun toggleLAMode(toLAMode: LAMode) {
    authenticate(
      if (toLAMode == LAMode.SYSTEM) {
        generalGetString(MR.strings.la_enter_app_passcode)
      } else {
        generalGetString(MR.strings.chat_lock)
      },
      generalGetString(MR.strings.change_lock_mode),
      oneTime = true,
    ) { laResult ->
      when (laResult) {
        is LAResult.Error -> {
          laFailedAlert()
        }
        is LAResult.Failed -> { /* Can be called multiple times on every failure */ }
        LAResult.Success -> {
          when (toLAMode) {
            LAMode.SYSTEM -> {
              authenticate(generalGetString(MR.strings.auth_enable_simplex_lock), promptSubtitle = "", usingLAMode = toLAMode, oneTime = true) { laResult ->
                when (laResult) {
                  LAResult.Success -> {
                    currentLAMode.set(toLAMode)
                    ksAppPassword.remove()
                    resetSelfDestruct()
                    laTurnedOnAlert()
                  }
                  is LAResult.Unavailable, is LAResult.Error -> laFailedAlert()
                  is LAResult.Failed -> { /* Can be called multiple times on every failure */ }
                }
              }
            }
            LAMode.PASSCODE -> {
              ModalManager.fullscreen.showCustomModal { close ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
                  SetAppPasscodeView(
                    submit = {
                      laLockDelay.set(30)
                      currentLAMode.set(toLAMode)
                      passcodeAlert(generalGetString(MR.strings.passcode_set))
                    },
                    cancel = {},
                    close = close
                  )
                }
              }
            }
          }
        }
        is LAResult.Unavailable -> disableUnavailableLA()
      }
    }
  }

  fun changeLAPassword() {
    authenticate(generalGetString(MR.strings.la_current_app_passcode), generalGetString(MR.strings.la_change_app_passcode), oneTime = true) { laResult ->
      when (laResult) {
        LAResult.Success -> {
          ModalManager.fullscreen.showCustomModal { close ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
              SetAppPasscodeView(
                reason = generalGetString(MR.strings.la_app_passcode),
                submit = {
                  passcodeAlert(generalGetString(MR.strings.passcode_changed))
                }, cancel = {
                  passcodeAlert(generalGetString(MR.strings.passcode_not_changed))
                }, close = close
              )
            }
          }
        }
        is LAResult.Error -> laFailedAlert()
        is LAResult.Failed -> {}
        is LAResult.Unavailable -> disableUnavailableLA()
      }
    }
  }

  // Show duress (self-destruct) PIN setup after unlock PIN is set
  if (showDuressSetup.value) {
    EnableSelfDestructOnboarding(
      selfDestruct = selfDestructPref,
      onComplete = {
        showDuressSetup.value = false
        setPerformLA(true)
        close?.invoke()
      }
    )
  } else if (showPasscodeSetup.value) {
    // Show passcode setup view
                    SetAppPasscodeView(
                      submit = {
        // Passcode set successfully - SAVE THE PREFERENCE
        selectedMode.value = LAMode.PASSCODE
                        chatModel.controller.appPrefs.performLA.set(true)
        chatModel.controller.appPrefs.laMode.set(LAMode.PASSCODE)
        showAuthScreen.value = true
        showPasscodeSetup.value = false
        if (close != null) {
          // From settings: show duress PIN setup next
          showDuressSetup.value = true
        } else {
          // From onboarding: let the onboarding flow handle duress setup
          setPerformLA(true)
        }
                      },
                      cancel = {
        // User cancelled, reset selection
        selectedMode.value = null
        showPasscodeSetup.value = false
      },
      close = {
        // User closed, reset selection
        selectedMode.value = null
        showPasscodeSetup.value = false
      }
    )
  } else {
    // Show lock mode selection
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colors.background)
    ) {
      // Main content - apply blur when success dialog is shown
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 24.dp)
          .verticalScroll(rememberScrollState())
          .then(if (showSystemAuthSuccessDialog.value) Modifier.blur(16.dp) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
      // Top bar with back button and logo
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = { close?.invoke() },
          modifier = Modifier.height(24.dp)
        ) {
          Icon(
            painter = painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = generalGetString(MR.strings.back),
            tint = MaterialTheme.colors.onBackground
          )
        }

        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = "Shredgram Logo"
        )

        Spacer(Modifier.width(48.dp))
      }
      
      // Lock Icon
      Icon(
        painter = painterResource(MR.images.ic_unlock),
        contentDescription = "Unlock",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.onBackground
      )
      
      Spacer(Modifier.height(16.dp))
      
      // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
      Text(
        text = "Set your lock mode",
        fontFamily = Manrope,
        fontSize = lockModeFont30,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        lineHeight = (lockModeFont30.value * lockModeLineHeightHeadlineS).sp,
        modifier = Modifier.fillMaxWidth()
      )
      
      Spacer(Modifier.height(8.dp))
      
      // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
      Text(
        text = "Choose your preferred way of unlocking the app. You will be required to authenticate whenever you resume the app, or you becoming idle.",
        fontFamily = DMSans,
        fontSize = lockModeFont14,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.secondary,
        textAlign = TextAlign.Center,
        lineHeight = (lockModeFont14.value * lockModeLineHeightBody).sp,
        modifier = Modifier.fillMaxWidth()
      )
      
      Spacer(Modifier.height(32.dp))
      
      // System Authentication Option
      LockModeOptionCard(
        icon = painterResource(MR.images.ic_fingerprint),
        title = "System authentication",
        description = "Use your device biometric or system authentication to unlock Shredgram.",
        isSelected = selectedMode.value == LAMode.SYSTEM,
        onClick = { 
          // Just select the option - don't trigger setup yet
          selectedMode.value = LAMode.SYSTEM
        }
      )
      
      Spacer(Modifier.height(16.dp))
      
      // PIN Option
      LockModeOptionCard(
        icon = painterResource(MR.images.ic_lock_new),
        title = "PIN",
        description = "Use a custom 6-digit PIN specific to this app to unlock Shredgram.",
        isSelected = selectedMode.value == LAMode.PASSCODE,
        onClick = { 
          // Just select the option - don't trigger setup yet
          selectedMode.value = LAMode.PASSCODE
        }
      )
      
      Spacer(Modifier.height(32.dp))
      
      // Lock Timeout Section Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.Transparent,
        elevation = 0.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
        ) {
          // Top row: Icon, Title, and Dropdown
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Icon on the left
            Icon(
              painter = painterResource(MR.images.ic_clock_new),
              contentDescription = "Lock timeout",
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colors.onBackground
            )
            
            Spacer(Modifier.width(12.dp))
            
            // Title
            Text(
              text = "Lock timeout",
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colors.onBackground,
              modifier = Modifier.weight(1f)
            )
            
            Spacer(Modifier.width(12.dp))
            
            // Dropdown
            Box {
              OutlinedButton(
                onClick = { expanded.value = true },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  backgroundColor = Color.Transparent,
                  contentColor = MaterialTheme.colors.onBackground
                ),
                border = BorderStroke(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.2f)),
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
              ) {
                Text(
                  text = getTimeoutLabel(lockTimeout.value),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                  painter = painterResource(MR.images.ic_chevron_down),
                  contentDescription = "Dropdown",
                  modifier = Modifier.size(16.dp)
                )
              }
              
              MaterialTheme(
                shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(16.dp))
              ) {
                DropdownMenu(
                  expanded = expanded.value,
                  onDismissRequest = { expanded.value = false },
                  modifier = Modifier
                    .heightIn(max = 200.dp)
                    .background(MaterialTheme.colors.surface, RoundedCornerShape(16.dp))
                    .padding(vertical = 8.5.dp)
                ) {
                  timeoutOptions.forEach { timeout ->
                    DropdownMenuItem(
                      onClick = {
                        lockTimeout.value = timeout
                        expanded.value = false
                      },
                      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                      Text(
                        text = getTimeoutLabel(timeout),
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onBackground
                      )
                    }
                  }
                }
              }
            }
          }
          
          // Description below
          Text(
            text = "App locks automatically after the selected time period",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            lineHeight = 18.sp,
            modifier = Modifier
              .width(220.dp)
              .padding(start = 28.dp) // Align with title (icon size + spacing)
          )
        }
      }

      Spacer(Modifier.weight(1f))
      Spacer(Modifier.height(32.dp))
      
      // Next Button - enabled after lock mode is selected
      Button(
        onClick = {
          // Save the timeout setting
          laLockDelay.set(lockTimeout.value)
          chatModel.controller.appPrefs.laLockDelay.set(lockTimeout.value)
          
          // Trigger setup for the selected mode
          val chosenMode = selectedMode.value
          if (chosenMode != null) {
            setupLockMode(chosenMode)
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
          backgroundColor = if (selectedMode.value != null) SignalBlue else MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
          contentColor = MaterialTheme.colors.onPrimary
        ),
        enabled = selectedMode.value != null,
        elevation = ButtonDefaults.elevation(
          defaultElevation = 0.dp,
          pressedElevation = 0.dp
        )
      ) {
            Text(
          text = "Next",
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (selectedMode.value != null) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
        )
      }

      Spacer(Modifier.height(24.dp))
      }
      
      // Shredgram-style System Auth Success Dialog overlay
      if (showSystemAuthSuccessDialog.value) {
        SystemAuthSuccessDialog(
          onConfirm = {
            showSystemAuthSuccessDialog.value = false
            setPerformLA(true)
            close?.invoke()
          }
        )
      }
    }
  }
}

/**
 * Shredgram-style Success Dialog for System Auth
 * - Foggy scrim background (blur effect)
 * - Success icon (green checkmark)
 * - Title and description
 * - OK button to proceed
 */
@Composable
private fun SystemAuthSuccessDialog(
  onConfirm: () -> Unit
) {
  // Shredgram colors
  val successGreen = Color(0xFF43A047)
  val electricBlue500 = MaterialTheme.colors.primary
  val onSurfaceVariant = MaterialTheme.colors.secondary
  
  val scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
  
  // Full screen overlay with scrim
      Box(
        modifier = Modifier
      .fillMaxSize()
      .background(scrimColor)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { /* Don't dismiss on background click */ },
    contentAlignment = Alignment.Center
  ) {
    // Dialog card
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 40.dp)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null
        ) { /* Consume clicks on dialog */ },
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colors.surface,
      elevation = 12.dp
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Success Icon - green checkmark
        Icon(
          painter = painterResource(MR.images.ic_check_circle),
          contentDescription = null,
          tint = successGreen,
          modifier = Modifier.size(32.dp)
      )
      
      Spacer(Modifier.height(8.dp))
        
        // Title
        Text(
          text = generalGetString(MR.strings.system_auth_added_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          textAlign = TextAlign.Center,
          lineHeight = 24.sp
        )
        
        Spacer(Modifier.height(4.dp))
        
        // Description
        Text(
          text = generalGetString(MR.strings.system_auth_added_desc),
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
          color = onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = 18.sp
        )
        
        Spacer(Modifier.height(32.dp))
        
        // OK Button - Shredgram style
        Button(
          onClick = onConfirm,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(360.dp),  // RadiusPill
          colors = ButtonDefaults.buttonColors(
            backgroundColor = electricBlue500,
            contentColor = MaterialTheme.colors.onPrimary
          ),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
          elevation = ButtonDefaults.elevation(0.dp, 0.dp)
        ) {
          Text(
            text = generalGetString(MR.strings.ok),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 21.sp
          )
        }
      }
    }
  }
}

// Shredgram Typography tokens - EXACT match from TypographyTokens.kt
private const val lockModeLineHeightHeadlineS = 1.12f
private const val lockModeLineHeightBody = 1.5f
private val lockModeFont12 = 12.sp
private val lockModeFont14 = 14.sp
private val lockModeFont30 = 30.sp

/**
 * Shredgram OptionCard - EXACT match from OptionCard.kt
 * - Shape: RadiusLarge (16dp)
 * - shadowElevation: 10dp when selected, 0dp when not
 * - Border: 1dp, BorderBrand (ElectricBlue500) when selected, outlineVariant when not
 * - Padding: 16dp
 * - Spacing: 8dp between icon/text/radio, 4dp between title/desc
 * - Title: titleExtraSmall (Manrope Bold 14sp, lineHeight 1.5)
 * - Description: bodySmall (DMSans Normal 12sp, lineHeight 1.5) - aligned with title
 */
@Composable
fun LockModeOptionCard(
  icon: androidx.compose.ui.graphics.painter.Painter,
  title: String,
  description: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  // Shredgram outlineVariant
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
  
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),  // RadiusLarge
    color = MaterialTheme.colors.surface,
    elevation = if (isSelected) 10.dp else 0.dp,  // shadowElevation
    border = BorderStroke(
      width = 1.dp,  // Exact 1dp border
      color = if (isSelected) MaterialTheme.colors.primary else outlineVariant
    )
  ) {
    Row(
      modifier = Modifier
        .padding(16.dp)  // 16dp padding
        .fillMaxWidth(),
      verticalAlignment = Alignment.Top
    ) {
      // Icon
        Icon(
          painter = icon,
          contentDescription = title,
        tint = MaterialTheme.colors.onSurface
      )
      
      Spacer(Modifier.width(8.dp))  // 8dp spacing
      
      // Title and Description - both aligned to start
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.Start
      ) {
        // titleExtraSmall: Manrope Bold 14sp, lineHeight 1.5
        Text(
          text = title,
          fontFamily = Manrope,
          fontSize = lockModeFont14,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          lineHeight = (lockModeFont14.value * lockModeLineHeightBody).sp,
          textAlign = TextAlign.Start
        )
        
        Spacer(Modifier.height(4.dp))  // 4dp spacing
        
        // bodySmall: DMSans Normal 12sp, lineHeight 1.5, onSurfaceVariant
        // Aligned with title - starts at same position
        Text(
          text = description,
          fontFamily = DMSans,
          fontSize = lockModeFont12,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colors.secondary,
          lineHeight = (lockModeFont12.value * lockModeLineHeightBody).sp,
          textAlign = TextAlign.Start
        )
      }
      
      Spacer(Modifier.width(8.dp))  // 8dp spacing
      
      // Radio button - Shredgram UIRadioButton style
      // 16dp size, 1dp border outlineVariant, 10dp checkmark in ElectricBlue500
      Surface(
        modifier = Modifier
          .size(16.dp),
        shape = RoundedCornerShape(50),  // RadiusCircle
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, outlineVariant)  // 1dp border, always outlineVariant
      ) {
        if (isSelected) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painter = painterResource(MR.images.ic_check),
              contentDescription = "Selected",
              tint = MaterialTheme.colors.primary,
              modifier = Modifier.size(10.dp)  // 10dp check icon
            )
          }
        }
      }
    }
  }
}

@Composable
fun EnableSelfDestructOnboarding(
  selfDestruct: SharedPreference<Boolean>,
  onComplete: () -> Unit
) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
    SetAppPasscodeView(
      passcodeKeychain = ksSelfDestructPassword,
      prohibitedPasscodeKeychain = ksAppPassword,
      title = "Set your Duress PIN",
      reason = "Choose a pin that is unpredictable, but something you will remember. The Duress PIN cannot be the same as the Unlock PIN.",
      iconResource = MR.images.ic_eraser,
      submit = {
        selfDestruct.set(true)
        onComplete()
      },
      cancel = {},
      close = {}
    )
  }
}

@Composable
private fun EnableLock(performLA: State<Boolean>, onCheckedChange: (Boolean) -> Unit) {
  SectionItemView {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(MR.strings.enable_lock), Modifier
          .padding(end = 24.dp)
          .fillMaxWidth()
          .weight(1F)
      )
      DefaultSwitch(
        checked = performLA.value,
        onCheckedChange = onCheckedChange,
      )
    }
  }
}

@Composable
private fun LockModeSelector(state: State<LAMode>, onSelected: (LAMode) -> Unit) {
  val values by remember { mutableStateOf(LAMode.values().map { it to it.text }) }
  ExposedDropDownSettingRow(
    generalGetString(MR.strings.lock_mode),
    values,
    state,
    icon = null,
    enabled = remember { mutableStateOf(true) },
    onSelected = onSelected
  )
}

@Composable
private fun LockModeCardSelector(state: State<LAMode>, performLA: State<Boolean>, onSelected: (LAMode) -> Unit) {
  Column(
    Modifier.padding(horizontal = DEFAULT_PADDING, vertical = DEFAULT_PADDING_HALF),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      generalGetString(MR.strings.lock_mode),
      fontSize = 16.sp,
      fontWeight = FontWeight.Medium
    )
    
    LockModeCard(
      title = generalGetString(MR.strings.la_mode_system),
      description = generalGetString(MR.strings.lock_mode_system_description),
      icon = MR.images.ic_security,
      isSelected = performLA.value && state.value == LAMode.SYSTEM,
      onClick = { onSelected(LAMode.SYSTEM) }
    )
    
    LockModeCard(
      title = generalGetString(MR.strings.la_mode_passcode),
      description = generalGetString(MR.strings.lock_mode_passcode_description),
      icon = MR.images.ic_lock,
      isSelected = performLA.value && state.value == LAMode.PASSCODE,
      onClick = { onSelected(LAMode.PASSCODE) }
    )
  }
}

@Composable
private fun LockModeCard(
  title: String,
  description: String,
  icon: dev.icerock.moko.resources.ImageResource,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else MaterialTheme.colors.surface,
    border = androidx.compose.foundation.BorderStroke(
      width = if (isSelected) 2.dp else 1.dp,
      color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.secondary.copy(alpha = 0.3f)
    ),
    onClick = onClick
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.secondary
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = description,
          fontSize = 14.sp,
          color = MaterialTheme.colors.secondary,
          lineHeight = 20.sp
        )
      }
    }
  }
}

@Composable
private fun LockDelaySelector(state: State<Int>, onSelected: (Int) -> Unit) {
  val delays = remember { if (laDelays.contains(state.value)) laDelays else listOf(state.value) + laDelays }
  val values by remember { mutableStateOf(delays.map { it to laDelayText(it) }) }
  ExposedDropDownSettingRow(
    generalGetString(MR.strings.lock_after),
    values,
    state,
    icon = null,
    enabled = remember { mutableStateOf(true) },
    onSelected = onSelected
  )
}

@Composable
private fun MaxFailedAttemptsSelector(state: State<Int>, onSelected: (Int) -> Unit) {
  val attemptCounts = remember { (3..9).toList() }
  val values by remember { mutableStateOf(attemptCounts.map { it to generalGetString(MR.strings.la_attempts_count).format(it) }) }
  Column {
    ExposedDropDownSettingRow(
      generalGetString(MR.strings.la_max_failed_attempts_title),
      values,
      state,
      icon = null,
      enabled = remember { mutableStateOf(true) },
      onSelected = onSelected
    )
    SectionTextFooter(generalGetString(MR.strings.la_max_failed_attempts_description))
  }
}

@Composable
private fun TextListItem(n: String, text: String) {
  Box {
    Text(n)
    Text(text, Modifier.padding(start = 20.dp))
  }
}

private fun laDelayText(t: Int): String {
  val m = t / 60
  val s = t % 60
  return if (t == 0) {
    generalGetString(MR.strings.la_immediately)
  } else if (m == 0 || s != 0) {
    // there are no options where both minutes and seconds are needed
    generalGetString(MR.strings.la_seconds).format(s)
  } else {
    generalGetString(MR.strings.la_minutes).format(m)
  }
}

private fun passcodeAlert(title: String) {
  AlertManager.shared.showAlertMsg(
    title = title,
    text = generalGetString(MR.strings.la_please_remember_to_store_password)
  )
}

private fun selfDestructPasscodeAlert(title: String) {
  AlertManager.shared.showAlertMsg(title, generalGetString(MR.strings.if_you_enter_passcode_data_removed))
}

fun laTurnedOnAlert() = AlertManager.shared.showAlertMsg(
  generalGetString(MR.strings.auth_simplex_lock_turned_on),
  generalGetString(MR.strings.auth_you_will_be_required_to_authenticate_when_you_start_or_resume)
)

fun laPasscodeNotSetAlert() = AlertManager.shared.showAlertMsg(
  generalGetString(MR.strings.lock_not_enabled),
  generalGetString(MR.strings.you_can_turn_on_lock)
)

fun laFailedAlert() {
  AlertManager.shared.showAlertMsg(
    title = generalGetString(MR.strings.la_auth_failed),
    text = generalGetString(MR.strings.la_could_not_be_verified)
  )
}

fun laUnavailableInstructionAlert() = AlertManager.shared.showAlertMsg(
  generalGetString(MR.strings.auth_unavailable),
  generalGetString(MR.strings.auth_device_authentication_is_not_enabled_you_can_turn_on_in_settings_once_enabled)
)

fun laUnavailableTurningOffAlert() = AlertManager.shared.showAlertMsg(
  generalGetString(MR.strings.auth_unavailable),
  generalGetString(MR.strings.auth_device_authentication_is_disabled_turning_off)
)
