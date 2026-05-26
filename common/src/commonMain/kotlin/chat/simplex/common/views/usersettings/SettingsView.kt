package chat.simplex.common.views.usersettings

import SectionItemView
import SectionView
import TextIconSpaced
import androidx.activity.compose.BackHandler
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.*
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.database.DatabaseView
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.SimpleXInfo
import chat.simplex.res.MR

@Composable
fun SettingsView(chatModel: ChatModel, setPerformLA: (Boolean) -> Unit, close: () -> Unit) {
  val user = chatModel.currentUser.value
  val stopped = chatModel.chatRunning.value == false
  SettingsLayout(
    close = close,
    stopped = stopped,
    encrypted = chatModel.chatDbEncrypted.value == true,
    passphraseSaved = remember { chatModel.controller.appPrefs.storeDBPassphrase.state }.value,
    notificationsMode = remember { chatModel.controller.appPrefs.notificationsMode.state },
    userDisplayName = user?.displayName,
    setPerformLA = setPerformLA,
    showModal = { modalView -> { ModalManager.start.showModal { modalView(chatModel) } } },
    showSettingsModal = { modalView -> { ModalManager.start.showModal(true) { modalView(chatModel) } } },
    showSettingsModalWithSearch = { modalView ->
      ModalManager.start.showCustomModal { close ->
        val search = rememberSaveable { mutableStateOf("") }
        ModalView(
          { close() },
          showSearch = true,
          searchAlwaysVisible = true,
          onSearchValueChanged = { search.value = it },
          content = { modalView(chatModel, search) })
      }
    },
    showCustomModal = { modalView -> { ModalManager.start.showCustomModal { close -> modalView(chatModel, close) } } },
    showVersion = {
      withBGApi {
        val info = chatModel.controller.apiGetVersion()
        if (info != null) {
          ModalManager.start.showModal { VersionInfoView(info) }
        }
      }
    },
    withAuth = ::doWithAuth,
  )
  KeyChangeEffect(chatModel.updatingProgress.value != null) {
    close()
  }
}

val simplexTeamUri =
  "simplex:/contact#/?v=1&smp=smp%3A%2F%2FPQUV2eL0t7OStZOoAsPEV2QYWt4-xilbakvGUGOItUo%3D%40smp6.simplex.im%2FK1rslx-m5bpXVIdMZg9NLUZ_8JBm8xTt%23MCowBQYDK2VuAyEALDeVe-sG8mRY22LsXlPgiwTNs9dbiLrNuA7f3ZMAJ2w%3D"

@Composable
fun DexSettingsItem(
  icon: Painter,
  text: String,
  onClick: (() -> Unit)? = null,
  disabled: Boolean = false,
  iconTint: Color = MaterialTheme.colors.onBackground,
  showChevron: Boolean = true
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .then(
        if (!disabled && onClick != null) Modifier.clickable { onClick() }
        else Modifier
      )
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
      Icon(
        icon,
        contentDescription = text,
        modifier = Modifier.size(22.dp),
        tint = if (disabled) MaterialTheme.colors.secondary else iconTint
      )
    }
    Spacer(Modifier.width(14.dp))
    Text(
      text,
      modifier = Modifier.weight(1f),
      fontSize = 15.sp,
      fontFamily = DMSans,
      fontWeight = FontWeight.Medium,
      color = if (disabled) MaterialTheme.colors.secondary else MaterialTheme.colors.onBackground
    )
    if (showChevron) {
      Icon(
        painterResource(MR.images.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colors.secondary.copy(alpha = 0.6f)
      )
    }
  }
}

@Composable
private fun SettingsSectionTitle(title: String) {
  Text(
    title.uppercase(),
    fontSize = 12.sp,
    fontFamily = DMSans,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colors.secondary.copy(alpha = 0.7f),
    letterSpacing = 1.sp,
    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
  )
}

@Composable
fun SettingsLayout(
  close: () -> Unit,
  stopped: Boolean,
  encrypted: Boolean,
  passphraseSaved: Boolean,
  notificationsMode: State<NotificationsMode>,
  userDisplayName: String?,
  setPerformLA: (Boolean) -> Unit,
  showModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showSettingsModalWithSearch: (@Composable (ChatModel, MutableState<String>) -> Unit) -> Unit,
  showCustomModal: (@Composable ModalData.(ChatModel, () -> Unit) -> Unit) -> (() -> Unit),
  showVersion: () -> Unit,
  withAuth: (title: String, desc: String, block: () -> Unit) -> Unit,
) {
  val view = LocalMultiplatformView()
  LaunchedEffect(Unit) {
    hideKeyboard(view)
  }
  BackHandler(onBack = close)

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
          IconButton(onClick = close) {
            Icon(
              painterResource(MR.images.ic_arrow_back_ios_new),
              contentDescription = "Back",
              tint = MaterialTheme.colors.onBackground
            )
          }
          Text(
            "Settings",
            fontSize = 18.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground
          )
        }
      }
    },
    backgroundColor = MaterialTheme.colors.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
    ) {
      Spacer(Modifier.height(20.dp))

      // ── Subscription / Dexgram Pro ──
      val isPremiumPref = remember { appPrefs.premiumActive.state }
      SettingsSectionTitle(stringResource(MR.strings.premium_settings_title))
      DexSettingsItem(
        icon = painterResource(MR.images.ic_shield),
        text = stringResource(MR.strings.settings_premium),
        onClick = showCustomModal { _, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            PremiumFeaturesView(close = close)
          }
        },
        iconTint = if (isPremiumPref.value) Color(0xFF1F4CFF) else MaterialTheme.colors.onBackground
      )

      Spacer(Modifier.height(24.dp))
      Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
      Spacer(Modifier.height(20.dp))

      SettingsSectionTitle("General")
      DexSettingsItem(
        icon = painterResource(if (notificationsMode.value == NotificationsMode.OFF) MR.images.ic_bolt_off else MR.images.ic_bolt),
        text = stringResource(MR.strings.notifications),
        onClick = showCustomModal { m, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            NotificationsSettingsView(m)
          }
        },
        disabled = stopped
      )
      DexSettingsItem(
        icon = painterResource(MR.images.ic_lock),
        text = stringResource(MR.strings.privacy_and_security),
        onClick = showCustomModal { m, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            PrivacySettingsView(m, showSettingsModal, setPerformLA)
          }
        },
        disabled = stopped
      )
      DexSettingsItem(
        icon = painterResource(MR.images.ic_light_mode),
        text = stringResource(MR.strings.appearance_settings),
        onClick = showCustomModal { m, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            AppearanceView(m)
          }
        }
      )
      DexSettingsItem(
        icon = painterResource(MR.images.ic_call),
        text = stringResource(MR.strings.settings_audio_video_calls),
        onClick = showCustomModal { m, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            CallSettingsView(m, showModal, close)
          }
        },
        disabled = stopped
      )

      Spacer(Modifier.height(24.dp))
      Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
      Spacer(Modifier.height(20.dp))

      SettingsSectionTitle("Data")
      DexSettingsItem(
        icon = painterResource(MR.images.ic_database),
        text = stringResource(MR.strings.database_passphrase_and_export),
        onClick = showCustomModal { _, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            DatabaseView()
          }
        },
        disabled = stopped,
        iconTint = if (encrypted && (appPlatform.isAndroid || !passphraseSaved)) MaterialTheme.colors.onBackground else WarningOrange
      )

      Spacer(Modifier.height(24.dp))
      Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
      Spacer(Modifier.height(20.dp))

      SettingsSectionTitle("Help & Info")
      DexSettingsItem(
        icon = painterResource(MR.images.ic_help),
        text = stringResource(MR.strings.how_to_use_simplex_chat),
        onClick = showCustomModal { _, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            HelpView(userDisplayName ?: "")
          }
        },
        disabled = stopped
      )
      DexSettingsItem(
        icon = painterResource(MR.images.ic_info),
        text = generalGetString(MR.strings.about_simplex_chat),
        onClick = showCustomModal { m, close ->
          ModalView(
            close = close,
            showClose = false,
            showAppBar = false
          ) {
            SimpleXInfo(m, onboarding = false)
          }
        }
      )

      Spacer(Modifier.height(24.dp))
      Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
      Spacer(Modifier.height(20.dp))

      SettingsSectionTitle("App")
      SettingsSectionApp(showSettingsModal, showVersion, withAuth)

      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
expect fun SettingsSectionApp(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showVersion: () -> Unit,
  withAuth: (title: String, desc: String, block: () -> Unit) -> Unit
)

@Composable fun ChatPreferencesItem(showCustomModal: ((@Composable ModalData.(ChatModel, () -> Unit) -> Unit) -> (() -> Unit)), stopped: Boolean) {
  SettingsActionItem(
    painterResource(MR.images.ic_toggle_on),
    stringResource(MR.strings.chat_preferences),
    click = if (stopped) null else ({
      showCustomModal { m, close ->
        PreferencesView(m, m.currentUser.value ?: return@showCustomModal, close)
      }()
    }),
    disabled = stopped
  )
}

@Composable
fun ChatLockItem(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  setPerformLA: (Boolean) -> Unit
) {
  val performLA = remember { appPrefs.performLA.state }
  val currentLAMode = remember { ChatModel.controller.appPrefs.laMode }
  SettingsActionItemWithContent(
    click = showSettingsModal { SimplexLockView(ChatModel, currentLAMode, setPerformLA) },
    icon = if (performLA.value) painterResource(MR.images.ic_lock_filled) else painterResource(MR.images.ic_lock),
    text = stringResource(MR.strings.chat_lock),
    iconColor = if (performLA.value) SimplexGreen else MaterialTheme.colors.secondary
  ) {
    Text(if (performLA.value) remember { currentLAMode.state }.value.text else generalGetString(MR.strings.la_mode_off), color = MaterialTheme.colors.secondary)
  }
}

@Composable private fun ContributeItem(uriHandler: UriHandler) {
  SectionItemView({ uriHandler.openUriCatching("https://github.com/simplex-chat/simplex-chat#contribute") }) {
    Icon(
      painterResource(MR.images.ic_keyboard),
      contentDescription = "GitHub",
      tint = MaterialTheme.colors.secondary,
    )
    TextIconSpaced()
    Text(generalGetString(MR.strings.contribute), color = MaterialTheme.colors.primary)
  }
}

@Composable private fun RateAppItem(uriHandler: UriHandler) {
  SectionItemView({
    runCatching { uriHandler.openUriCatching("market://details?id=chat.simplex.app") }
      .onFailure { uriHandler.openUriCatching("https://play.google.com/store/apps/details?id=chat.simplex.app") }
  }
  ) {
    Icon(
      painterResource(MR.images.ic_star),
      contentDescription = "Google Play",
      tint = MaterialTheme.colors.secondary,
    )
    TextIconSpaced()
    Text(generalGetString(MR.strings.rate_the_app), color = MaterialTheme.colors.primary)
  }
}

@Composable private fun StarOnGithubItem(uriHandler: UriHandler) {
  SectionItemView({ uriHandler.openUriCatching("https://github.com/simplex-chat/simplex-chat") }) {
    Icon(
      painter = painterResource(MR.images.ic_github),
      contentDescription = "GitHub",
      tint = MaterialTheme.colors.secondary,
    )
    TextIconSpaced()
    Text(generalGetString(MR.strings.star_on_github), color = MaterialTheme.colors.primary)
  }
}

@Composable fun ChatConsoleItem(showTerminal: () -> Unit) {
  SectionItemView(showTerminal) {
    Icon(
      painter = painterResource(MR.images.ic_outline_terminal),
      contentDescription = stringResource(MR.strings.chat_console),
      tint = MaterialTheme.colors.secondary,
    )
    TextIconSpaced()
    Text(stringResource(MR.strings.chat_console))
  }
}

@Composable fun TerminalAlwaysVisibleItem(pref: SharedPreference<Boolean>, onChange: (Boolean) -> Unit) {
  SettingsActionItemWithContent(painterResource(MR.images.ic_engineering), stringResource(MR.strings.terminal_always_visible)) {
    DefaultSwitch(
      checked = remember { pref.state }.value,
      onCheckedChange = onChange,
    )
  }
}

@Composable fun InstallTerminalAppItem(uriHandler: UriHandler) {
  SectionItemView({ uriHandler.openUriCatching("https://github.com/simplex-chat/simplex-chat") }) {
    Icon(
      painter = painterResource(MR.images.ic_github),
      contentDescription = "GitHub",
      tint = MaterialTheme.colors.secondary,
    )
    TextIconSpaced()
    Text(generalGetString(MR.strings.install_simplex_chat_for_terminal), color = MaterialTheme.colors.primary)
  }
}

@Composable fun ResetHintsItem(unchangedHints: MutableState<Boolean>) {
  SectionItemView({
    resetHintPreferences()
    unchangedHints.value = true
  }, disabled = unchangedHints.value) {
    Icon(
      painter = painterResource(MR.images.ic_lightbulb),
      contentDescription = "Lightbulb",
      tint = MaterialTheme.colors.secondary,
    )
    TextIconSpaced()
    Text(generalGetString(MR.strings.reset_all_hints), color = if (unchangedHints.value) MaterialTheme.colors.secondary else MaterialTheme.colors.primary)
  }
}

private fun resetHintPreferences() {
  for ((pref, def) in appPreferences.hintPreferences) {
    pref.set(def)
  }
}

fun unchangedHintPreferences(): Boolean = appPreferences.hintPreferences.all { (pref, def) ->
  pref.state.value == def
}

@Composable
fun AppVersionItem(showVersion: () -> Unit) {
  SectionItemView(showVersion) { AppVersionText() }
}

@Composable fun AppVersionText() {
  Text(appVersionInfo.first + (if (appVersionInfo.second != null) " (" + appVersionInfo.second + ")" else ""))
}

@Composable fun ProfilePreview(profileOf: NamedChat, size: Dp = 60.dp, iconColor: Color = MaterialTheme.colors.secondaryVariant, textColor: Color = MaterialTheme.colors.onBackground, stopped: Boolean = false) {
  ProfileImage(size = size, image = profileOf.image, color = iconColor)
  Spacer(Modifier.padding(horizontal = 8.dp))
  Column(Modifier.height(size), verticalArrangement = Arrangement.Center) {
    Text(
      profileOf.displayName,
      style = MaterialTheme.typography.caption,
      fontWeight = FontWeight.Bold,
      color = if (stopped) MaterialTheme.colors.secondary else textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    if (profileOf.fullName.isNotEmpty() && profileOf.fullName != profileOf.displayName) {
      Text(
        profileOf.fullName,
        Modifier.padding(vertical = 5.dp),
        color = if (stopped) MaterialTheme.colors.secondary else textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun SettingsActionItem(icon: Painter, text: String, click: (() -> Unit)? = null, textColor: Color = Color.Unspecified, iconColor: Color = MaterialTheme.colors.secondary, disabled: Boolean = false, extraPadding: Boolean = false) {
  SectionItemView(click, disabled = disabled, extraPadding = extraPadding) {
    Icon(icon, text, tint = if (disabled) MaterialTheme.colors.secondary else iconColor)
    TextIconSpaced(extraPadding)
    Text(text, color = if (disabled) MaterialTheme.colors.secondary else textColor)
  }
}

@Composable
fun SettingsActionItemWithContent(icon: Painter?, text: String? = null, click: (() -> Unit)? = null, iconColor: Color = MaterialTheme.colors.secondary, textColor: Color = MaterialTheme.colors.onBackground, disabled: Boolean = false, extraPadding: Boolean = false, content: @Composable RowScope.() -> Unit) {
  SectionItemView(
    click,
    extraPadding = extraPadding,
    padding = if (extraPadding && icon != null)
      PaddingValues(start = DEFAULT_PADDING * 1.7f, end = DEFAULT_PADDING)
    else
      PaddingValues(horizontal = DEFAULT_PADDING),
    disabled = disabled
  ) {
    if (icon != null) {
      Icon(icon, text, Modifier, tint = if (disabled) MaterialTheme.colors.secondary else iconColor)
      TextIconSpaced(extraPadding)
    }
    if (text != null) {
      val padding = with(LocalDensity.current) { 6.sp.toDp() }
      Text(text, Modifier.weight(1f).padding(vertical = padding), color = if (disabled) MaterialTheme.colors.secondary else textColor)
      Spacer(Modifier.width(DEFAULT_PADDING))
      Row(Modifier.widthIn(max = (windowWidth() - DEFAULT_PADDING * 2) / 2)) {
        content()
      }
    } else {
      Row {
        content()
      }
    }
  }
}

@Composable
fun SettingsPreferenceItem(
  icon: Painter?,
  text: String,
  pref: SharedPreference<Boolean>,
  iconColor: Color = MaterialTheme.colors.secondary,
  enabled: Boolean = true,
  onChange: ((Boolean) -> Unit)? = null,
) {
  SettingsActionItemWithContent(icon, text, iconColor = iconColor,) {
    SharedPreferenceToggle(pref, enabled, onChange)
  }
}

@Composable
fun PreferenceToggle(
  text: String,
  disabled: Boolean = false,
  checked: Boolean,
  onChange: (Boolean) -> Unit = {},
) {
  SettingsActionItemWithContent(null, text, disabled = disabled) {
    DefaultSwitch(
      checked = checked,
      onCheckedChange = onChange,
      enabled = !disabled
    )
  }
}

@Composable
fun PreferenceToggleWithIcon(
  text: String,
  icon: Painter? = null,
  iconColor: Color? = MaterialTheme.colors.secondary,
  disabled: Boolean = false,
  checked: Boolean,
  extraPadding: Boolean = false,
  onChange: (Boolean) -> Unit = {},
) {
  SettingsActionItemWithContent(icon, text, iconColor = iconColor ?: MaterialTheme.colors.secondary, extraPadding = extraPadding) {
    DefaultSwitch(
      checked = checked,
      onCheckedChange = {
        onChange(it)
      },
      enabled = !disabled
    )
  }
}

fun doWithAuth(title: String, desc: String, block: () -> Unit) {
  val requireAuth = chatModel.controller.appPrefs.performLA.get()
  if (!requireAuth) {
    block()
  } else {
    var autoShow = true
    ModalManager.fullscreen.showModalCloseable { close ->
      val onFinishAuth = { success: Boolean ->
        if (success) {
          close()
          block()
        }
      }

      LaunchedEffect(Unit) {
        if (autoShow) {
          autoShow = false
          runAuth(title, desc, onFinishAuth)
        }
      }
      Surface(color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
        Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          SimpleButton(
            stringResource(MR.strings.auth_unlock),
            icon = painterResource(MR.images.ic_lock),
            click = {
              runAuth(title, desc, onFinishAuth)
            }
          )
        }
      }
    }
  }
}

private fun runAuth(title: String, desc: String, onFinish: (success: Boolean) -> Unit) {
  authenticate(
    title,
    desc,
    oneTime = true,
    completed = { laResult ->
      onFinish(laResult == LAResult.Success || laResult is LAResult.Unavailable)
    }
  )
}

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "Dark Mode"
)*/
@Composable
fun PreviewSettingsLayout() {
  SimpleXTheme {
    SettingsLayout(
      close = {},
      stopped = false,
      encrypted = false,
      passphraseSaved = false,
      notificationsMode = remember { mutableStateOf(NotificationsMode.OFF) },
      userDisplayName = "Alice",
      setPerformLA = { _ -> },
      showModal = { {} },
      showSettingsModal = { {} },
      showSettingsModalWithSearch = { },
      showCustomModal = { {} },
      showVersion = {},
      withAuth = { _, _, _ -> },
    )
  }
}
