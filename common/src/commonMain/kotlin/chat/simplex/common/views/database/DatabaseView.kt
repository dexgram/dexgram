package chat.simplex.common.views.database

import SectionBottomSpacer
import SectionDividerSpaced
import SectionTextFooter
import SectionItemView
import SectionView
import androidx.activity.compose.BackHandler
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatModel.controller
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.localauth.PasscodeView
import chat.simplex.common.views.onboarding.PasskeyItemCard
import chat.simplex.common.views.onboarding.PasswordStrengthIndicator
import chat.simplex.common.views.onboarding.ShredgramInputField
import chat.simplex.common.views.onboarding.ValidateWithYubiKeyScreen
import chat.simplex.common.views.onboarding.ValidateWithYubiKeyPUKScreen
import chat.simplex.common.views.onboarding.generateManagementKey
import chat.simplex.common.views.usersettings.*
import chat.simplex.common.platform.*
import chat.simplex.res.MR
import kotlinx.datetime.*
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlinx.coroutines.*

@Composable
fun DatabaseView() {
  val m = chatModel
  val progressIndicator = remember { mutableStateOf(false) }
  val prefs = m.controller.appPrefs
  val useKeychain = remember { mutableStateOf(prefs.storeDBPassphrase.get()) }
  val chatLastStart = remember { mutableStateOf(prefs.chatLastStart.get()) }
  val chatArchiveFile = remember { mutableStateOf<String?>(null) }
  val stopped = remember { m.chatRunning }.value == false
  val saveArchiveLauncher = rememberFileChooserLauncher(false) { to: URI? ->
    val archive = chatArchiveFile.value
    if (archive != null && to != null) {
      copyFileToFile(File(archive), to) {}
    }
    // delete no matter the database was exported or canceled the export process
    if (archive != null) {
      File(archive).delete()
      chatArchiveFile.value = null
    }
  }
  val appFilesCountAndSize = remember { mutableStateOf(directoryFileCountAndSize(appFilesDir.absolutePath)) }
  val importArchiveLauncher = rememberFileChooserLauncher(true) { to: URI? ->
    if (to != null) {
      importArchiveAlert {
        stopChatRunBlockStartChat(stopped, chatLastStart, progressIndicator) {
          importArchive(to, appFilesCountAndSize, progressIndicator, false)
        }
      }
    }
  }
  val chatItemTTL = remember { mutableStateOf(m.chatItemTTL.value) }
  Box(
    Modifier.fillMaxSize(),
  ) {
    val user = m.currentUser.value
    val rhId = user?.remoteHostId
    DatabaseLayout(
      progressIndicator.value,
      stopped,
      useKeychain.value,
      m.chatDbEncrypted.value,
      m.controller.appPrefs.storeDBPassphrase.state.value,
      m.controller.appPrefs.initialRandomDBPassphrase,
      importArchiveLauncher,
      appFilesCountAndSize,
      chatItemTTL,
      user,
      m.users,
      startChat = { startChat(m, chatLastStart, m.chatDbChanged, progressIndicator) },
      stopChatAlert = { stopChatAlert(m, progressIndicator) },
      exportArchive = {
        stopChatRunBlockStartChat(stopped, chatLastStart, progressIndicator) {
          exportArchive(m, progressIndicator, chatArchiveFile, saveArchiveLauncher)
        }
      },
      deleteChatAlert = {
        deleteChatAlert {
          stopChatRunBlockStartChat(stopped, chatLastStart, progressIndicator) {
            deleteChat(m, progressIndicator)
            true
          }
        }
      },
      deleteAppFilesAndMedia = {
        deleteFilesAndMediaAlert {
          stopChatRunBlockStartChat(stopped, chatLastStart, progressIndicator) {
            deleteFiles(appFilesCountAndSize)
            true
          }
        }
      },
      onChatItemTTLSelected = {
        if (it == null) {
          return@DatabaseLayout
        }
        val oldValue = chatItemTTL.value
        chatItemTTL.value = it
        if (it < oldValue) {
          setChatItemTTLAlert(m, rhId, chatItemTTL, progressIndicator, appFilesCountAndSize)
        } else if (it != oldValue) {
          setCiTTL(m, rhId, chatItemTTL, progressIndicator, appFilesCountAndSize)
        }
      },
      disconnectAllHosts = {
        val connected = chatModel.remoteHosts.filter { it.sessionState is RemoteHostSessionState.Connected }
        connected.forEachIndexed { index, h ->
          controller.stopRemoteHostAndReloadHosts(h, index == connected.lastIndex && chatModel.connectedToRemote())
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
fun DatabaseLayout(
  progressIndicator: Boolean,
  stopped: Boolean,
  useKeyChain: Boolean,
  chatDbEncrypted: Boolean?,
  passphraseSaved: Boolean,
  initialRandomDBPassphrase: SharedPreference<Boolean>,
  importArchiveLauncher: FileChooserLauncher,
  appFilesCountAndSize: MutableState<Pair<Int, Long>>,
  chatItemTTL: MutableState<ChatItemTTL>,
  currentUser: User?,
  users: List<UserInfo>,
  startChat: () -> Unit,
  stopChatAlert: () -> Unit,
  exportArchive: () -> Unit,
  deleteChatAlert: () -> Unit,
  deleteAppFilesAndMedia: () -> Unit,
  onChatItemTTLSelected: (ChatItemTTL?) -> Unit,
  disconnectAllHosts: () -> Unit,
) {
  BackHandler(onBack = { ModalManager.start.closeModal() })
  val operationsDisabled = progressIndicator && !chatModel.desktopNoUserNoRemote

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
            text = stringResource(MR.strings.your_chat_database),
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
    ColumnWithScrollBar(
      Modifier
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
    ) {

    if (!chatModel.desktopNoUserNoRemote) {
      SectionView(stringResource(MR.strings.messages_section_title).uppercase()) {
        TtlOptions(chatItemTTL, enabled = rememberUpdatedState(!stopped && !progressIndicator), onChatItemTTLSelected)
      }
      SectionTextFooter(
        remember(currentUser?.displayName) {
          buildAnnotatedString {
            append(generalGetString(MR.strings.messages_section_description) + " ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
              append(currentUser?.displayName ?: "")
            }
            append(".")
          }
        }
      )
      SectionDividerSpaced(maxTopPadding = true)
    }
    val toggleEnabled = remember { chatModel.remoteHosts }.none { it.sessionState is RemoteHostSessionState.Connected }
    if (chatModel.localUserCreated.value == true) {
      // still show the toggle in case database was stopped when the user opened this screen because it can be in the following situations:
      // - database was stopped after migration and the app relaunched
      // - something wrong happened with database operations and the database couldn't be launched when it should
      SectionView(stringResource(MR.strings.run_chat_section)) {
        if (!toggleEnabled) {
          SectionItemView(disconnectAllHosts) {
            Text(generalGetString(MR.strings.disconnect_remote_hosts), Modifier.fillMaxWidth(), color = WarningOrange)
          }
        }
        RunChatSetting(stopped, toggleEnabled && !progressIndicator, startChat, stopChatAlert)
      }
      if (stopped) SectionTextFooter(stringResource(MR.strings.you_must_use_the_most_recent_version_of_database))
      SectionDividerSpaced(maxTopPadding = true)
    }

    SectionView(stringResource(MR.strings.chat_database_section)) {
      if (chatModel.localUserCreated.value != true && !toggleEnabled) {
        SectionItemView(disconnectAllHosts) {
          Text(generalGetString(MR.strings.disconnect_remote_hosts), Modifier.fillMaxWidth(), color = WarningOrange)
        }
      }
      val unencrypted = chatDbEncrypted == false
      val useYubiKey = remember { chatModel.controller.appPrefs.useYubiKeyForDB.state }.value
      // If DB is encrypted with YubiKey, hide direct "Database passphrase" option.
      // Show it only for passphrase flow (or when DB is currently unencrypted).
      if (unencrypted || !useYubiKey) {
        SettingsActionItem(
          if (unencrypted) painterResource(MR.images.ic_lock_open_right) else if (useKeyChain) painterResource(MR.images.ic_vpn_key_filled)
          else painterResource(MR.images.ic_lock),
          stringResource(MR.strings.database_passphrase),
          click = {
            ModalManager.start.showCustomModal { close ->
              ModalView(
                close = close,
                showClose = false,
                showAppBar = false
              ) {
                DatabaseEncryptionView(chatModel, false)
              }
            }
          },
          iconColor = if (unencrypted || (appPlatform.isDesktop && passphraseSaved)) WarningOrange else MaterialTheme.colors.secondary,
          disabled = operationsDisabled
        )
      }
      if (!unencrypted && appPlatform.isAndroid) {
        if (!useYubiKey) {
          SettingsActionItem(
            painterResource(MR.images.ic_vpn_key_filled),
            stringResource(MR.strings.switch_to_yubikey),
            click = {
              ModalManager.start.showCustomModal { close ->
                SwitchToYubiKeyView(chatModel, close)
              }
            },
            iconColor = MaterialTheme.colors.primary,
            textColor = MaterialTheme.colors.primary,
            disabled = operationsDisabled
          )
        } else {
          SettingsActionItem(
            painterResource(MR.images.ic_lock),
            stringResource(MR.strings.switch_to_passphrase),
            click = {
              ModalManager.start.showCustomModal { close ->
                SwitchToPassphraseView(chatModel, close)
              }
            },
            iconColor = MaterialTheme.colors.primary,
            textColor = MaterialTheme.colors.primary,
            disabled = operationsDisabled
          )
        }
      }
      if (appPlatform.isDesktop) {
        SettingsActionItem(
          painterResource(MR.images.ic_folder_open),
          stringResource(MR.strings.open_database_folder),
          ::desktopOpenDatabaseDir,
          disabled = operationsDisabled
        )
      }
      SettingsActionItem(
        painterResource(MR.images.ic_ios_share),
        stringResource(MR.strings.export_database),
        click = {
          if (initialRandomDBPassphrase.get()) {
            exportProhibitedAlert()
            ModalManager.start.showCustomModal { close ->
              ModalView(
                close = close,
                showClose = false,
                showAppBar = false
              ) {
                DatabaseEncryptionView(chatModel, false)
              }
            }
          } else {
            exportArchive()
          }
        },
        textColor = MaterialTheme.colors.primary,
        iconColor = MaterialTheme.colors.primary,
        disabled = operationsDisabled
      )
      SettingsActionItem(
        painterResource(MR.images.ic_download),
        stringResource(MR.strings.import_database),
        { withLongRunningApi { importArchiveLauncher.launch("application/zip") } },
        textColor = Color.Red,
        iconColor = Color.Red,
        disabled = operationsDisabled
      )
      SettingsActionItem(
        painterResource(MR.images.ic_delete_forever),
        stringResource(MR.strings.delete_database),
        deleteChatAlert,
        textColor = Color.Red,
        iconColor = Color.Red,
        disabled = operationsDisabled
      )
    }
    SectionDividerSpaced()

    SectionView(stringResource(MR.strings.files_and_media_section).uppercase()) {
      val deleteFilesDisabled = operationsDisabled || appFilesCountAndSize.value.first == 0
      SectionItemView(
        deleteAppFilesAndMedia,
        disabled = deleteFilesDisabled
      ) {
        Text(
          stringResource(if (users.size > 1) MR.strings.delete_files_and_media_for_all_users else MR.strings.delete_files_and_media_all),
          color = if (deleteFilesDisabled) MaterialTheme.colors.secondary else Color.Red
        )
      }
    }
    val (count, size) = appFilesCountAndSize.value
    SectionTextFooter(
      if (count == 0) {
        stringResource(MR.strings.no_received_app_files)
      } else {
        String.format(stringResource(MR.strings.total_files_count_and_size), count, formatBytes(size))
      }
    )
    SectionBottomSpacer()
    }
  }
}

private fun setChatItemTTLAlert(
  m: ChatModel, rhId: Long?, selectedChatItemTTL: MutableState<ChatItemTTL>,
  progressIndicator: MutableState<Boolean>,
  appFilesCountAndSize: MutableState<Pair<Int, Long>>,
) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.enable_automatic_deletion_question),
    text = generalGetString(MR.strings.enable_automatic_deletion_message),
    confirmText = generalGetString(MR.strings.delete_messages),
    onConfirm = { setCiTTL(m, rhId, selectedChatItemTTL, progressIndicator, appFilesCountAndSize) },
    onDismiss = { selectedChatItemTTL.value = m.chatItemTTL.value },
    onDismissRequest = { selectedChatItemTTL.value = m.chatItemTTL.value },
    destructive = true,
  )
}

@Composable
fun TtlOptions(
  current: State<ChatItemTTL?>,
  enabled: State<Boolean>,
  onSelected: (ChatItemTTL?) -> Unit,
  default: State<ChatItemTTL>? = null
) {
  val values = remember {
    val all: ArrayList<ChatItemTTL> = arrayListOf(ChatItemTTL.Month, ChatItemTTL.Week, ChatItemTTL.Day)
    val currentValue = current.value
    if (currentValue is ChatItemTTL.Seconds) {
      all.add(currentValue)
    }
    val options: MutableList<Pair<ChatItemTTL?, String>> = all.map { it to it.text }.toMutableList()

    if (default != null) {
      options.add(null to String.format(generalGetString(MR.strings.chat_item_ttl_default), default.value.text))
    }

    options
  }
  ExposedDropDownSettingRow(
    generalGetString(MR.strings.delete_messages_after),
    values,
    current,
    icon = null,
    enabled = enabled,
    onSelected = onSelected
  )
}

@Composable
fun RunChatSetting(
  stopped: Boolean,
  enabled: Boolean,
  startChat: () -> Unit,
  stopChatAlert: () -> Unit
) {
  val chatRunningText = if (stopped) stringResource(MR.strings.chat_is_stopped) else stringResource(MR.strings.chat_is_running)
  SettingsActionItemWithContent(
    icon = if (stopped) painterResource(MR.images.ic_report_filled) else painterResource(MR.images.ic_play_arrow_filled),
    text = chatRunningText,
    iconColor = if (stopped) Color.Red else MaterialTheme.colors.primary,
  ) {
    DefaultSwitch(
      checked = !stopped,
      onCheckedChange = { runChatSwitch ->
        if (runChatSwitch) {
          startChat()
        } else {
          stopChatAlert()
        }
      },
      enabled = enabled,
    )
  }
}

fun startChat(
  m: ChatModel,
  chatLastStart: MutableState<Instant?>,
  chatDbChanged: MutableState<Boolean>,
  progressIndicator: MutableState<Boolean>? = null
) {
  withLongRunningApi {
    try {
      progressIndicator?.value = true
      if (chatDbChanged.value) {
        initChatController()
        chatDbChanged.value = false
      }
      if (m.chatDbStatus.value !is DBMigrationResult.OK) {
        /** Hide current view and show [DatabaseErrorView] */
        ModalManager.closeAllModalsEverywhere()
        return@withLongRunningApi
      }
      val user = m.currentUser.value
      if (user == null) {
        ModalManager.closeAllModalsEverywhere()
        return@withLongRunningApi
      } else {
        m.controller.startChat(user)
      }
      val ts = Clock.System.now()
      m.controller.appPrefs.chatLastStart.set(ts)
      chatLastStart.value = ts
      platform.androidChatStartedAfterBeingOff()
    } catch (e: Throwable) {
      m.chatRunning.value = false
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_starting_chat), e.toString())
    } finally {
      progressIndicator?.value = false
    }
  }
}

private fun stopChatAlert(m: ChatModel, progressIndicator: MutableState<Boolean>? = null) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.stop_chat_question),
    text = generalGetString(MR.strings.stop_chat_to_export_import_or_delete_chat_database),
    confirmText = generalGetString(MR.strings.stop_chat_confirmation),
    onConfirm = { authStopChat(m, progressIndicator = progressIndicator) },
    onDismiss = { m.chatRunning.value = true }
  )
}

expect fun restartChatOrApp()

private fun exportProhibitedAlert() {
  AlertManager.shared.showAlertMsg(
    title = generalGetString(MR.strings.set_password_to_export),
    text = generalGetString(MR.strings.set_password_to_export_desc),
  )
}

fun authStopChat(m: ChatModel, progressIndicator: MutableState<Boolean>? = null, onStop: (() -> Unit)? = null) {
  if (m.controller.appPrefs.performLA.get()) {
    authenticate(
      generalGetString(MR.strings.auth_stop_chat),
      generalGetString(MR.strings.auth_log_in_using_credential),
      oneTime = true,
      completed = { laResult ->
        when (laResult) {
          LAResult.Success, is LAResult.Unavailable -> {
            stopChat(m, progressIndicator, onStop)
          }
          is LAResult.Error -> {
            m.chatRunning.value = true
            laFailedAlert()
          }
          is LAResult.Failed -> {
            m.chatRunning.value = true
          }
        }
      }
    )
  } else {
    stopChat(m, progressIndicator, onStop)
  }
}

private fun stopChat(m: ChatModel, progressIndicator: MutableState<Boolean>? = null, onStop: (() -> Unit)? = null) {
  withBGApi {
    try {
      progressIndicator?.value = true
      stopChatAsync(m)
      platform.androidChatStopped()
      // close chat view for desktop
      chatModel.chatId.value = null
      if (appPlatform.isDesktop) {
        ModalManager.end.closeModals()
      }
      onStop?.invoke()
    } catch (e: Error) {
      m.chatRunning.value = true
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_stopping_chat), e.toString())
    } finally {
      progressIndicator?.value = false
    }
  }
}

suspend fun stopChatAsync(m: ChatModel) {
  m.controller.apiStopChat()
  m.chatRunning.value = false
  controller.appPrefs.chatStopped.set(true)
}

fun stopChatRunBlockStartChat(
  stopped: Boolean,
  chatLastStart: MutableState<Instant?>,
  progressIndicator: MutableState<Boolean>,
  block: suspend () -> Boolean
) {
  // if the chat was running, the sequence is: stop chat, run block, start chat.
  // Otherwise, just run block and do nothing - the toggle will be visible anyway and the user can start the chat or not
  if (stopped) {
    withLongRunningApi {
      try {
        block()
      } catch (e: Throwable) {
        Log.e(TAG, e.stackTraceToString())
      }
    }
  } else {
    authStopChat(chatModel, progressIndicator) {
      withLongRunningApi {
        // if it throws, let's start chat again anyway
        val canStart = try {
          block()
        } catch (e: Throwable) {
          Log.e(TAG, e.stackTraceToString())
          true
        }
        if (canStart) {
          startChat(chatModel, chatLastStart, chatModel.chatDbChanged, progressIndicator)
        }
      }
    }
  }
}

suspend fun deleteChatAsync(m: ChatModel) {
  m.controller.apiDeleteStorage()
  DatabaseUtils.ksDatabasePassword.remove()
  m.controller.appPrefs.storeDBPassphrase.set(true)
  deleteChatDatabaseFilesAndState()
}

fun deleteChatDatabaseFilesAndState() {
  val chat = File(dataDir, chatDatabaseFileName)
  val chatBak = File(dataDir, "$chatDatabaseFileName.bak")
  val agent = File(dataDir, agentDatabaseFileName)
  val agentBak = File(dataDir, "$agentDatabaseFileName.bak")
  chat.delete()
  chatBak.delete()
  agent.delete()
  agentBak.delete()
  filesDir.deleteRecursively()
  filesDir.mkdir()
  remoteHostsDir.deleteRecursively()
  tmpDir.deleteRecursively()
  getMigrationTempFilesDirectory().deleteRecursively()
  tmpDir.mkdir()
  wallpapersDir.deleteRecursively()
  wallpapersDir.mkdirs()
  DatabaseUtils.ksDatabasePassword.remove()
  appPrefs.newDatabaseInitialized.set(false)
  chatModel.desktopOnboardingRandomPassword.value = false
  controller.appPrefs.storeDBPassphrase.set(true)
  controller.setChatCtrl(null)

  // Clear sensitive data on screen just in case ModalManager will fail to prevent hiding its modals while database encrypts itself
  chatModel.chatId.value = null
  withLongRunningApi {
    withContext(Dispatchers.Main) {
      chatModel.chatsContext.chatItems.clearAndNotify()
      chatModel.chatsContext.chats.clear()
      chatModel.chatsContext.popChatCollector.clear()
    }
    withContext(Dispatchers.Main) {
      chatModel.secondaryChatsContext.value?.chatItems?.clearAndNotify()
      chatModel.secondaryChatsContext.value?.chats?.clear()
      chatModel.secondaryChatsContext.value?.popChatCollector?.clear()
    }
  }
  chatModel.users.clear()
  ntfManager.cancelAllNotifications()
}

private suspend fun exportArchive(
  m: ChatModel,
  progressIndicator: MutableState<Boolean>,
  chatArchiveFile: MutableState<String?>,
  saveArchiveLauncher: FileChooserLauncher
): Boolean {
  progressIndicator.value = true
  try {
    val (archiveFile, archiveErrors) = exportChatArchive(m, null, chatArchiveFile)
    chatArchiveFile.value = archiveFile
    if (archiveErrors.isEmpty()) {
      saveArchiveLauncher.launch(archiveFile.substringAfterLast(File.separator))
    } else {
      showArchiveExportedWithErrorsAlert(generalGetString(MR.strings.chat_database_exported_save), archiveErrors) {
        withLongRunningApi {
          saveArchiveLauncher.launch(archiveFile.substringAfterLast(File.separator))
        }
      }
    }
    progressIndicator.value = false
  } catch (e: Throwable) {
    AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_exporting_chat_database), e.toString())
    progressIndicator.value = false
  }
  return false
}

suspend fun exportChatArchive(
  m: ChatModel,
  storagePath: File?,
  chatArchiveFile: MutableState<String?>
): Pair<String, List<ArchiveError>> {
  val archiveTime = Clock.System.now()
  val ts = SimpleDateFormat("yyyy-MM-dd'T'HHmmss", Locale.US).format(Date.from(archiveTime.toJavaInstant()))
  val archiveName = "simplex-chat.$ts.zip"
  val archivePath = "${(storagePath ?: databaseExportDir).absolutePath}${File.separator}$archiveName"
  val config = ArchiveConfig(archivePath, parentTempDirectory = databaseExportDir.toString())
  // Settings should be saved before changing a passphrase, otherwise the database needs to be migrated first
  if (!m.chatDbChanged.value) {
    controller.apiSaveAppSettings(AppSettings.current.prepareForExport())
  }
  wallpapersDir.mkdirs()
  val archiveErrors = m.controller.apiExportArchive(config)
  if (storagePath == null) {
    deleteOldChatArchive()
    m.controller.appPrefs.chatArchiveName.set(archiveName)
    m.controller.appPrefs.chatArchiveTime.set(archiveTime)
  }
  chatArchiveFile.value = archivePath
  return archivePath to archiveErrors
}

// Deprecated. Remove in the end of 2025. All unused archives should be deleted for the most users til then.
/** Remove [AppPreferences.chatArchiveName] and [AppPreferences.chatArchiveTime] as well */
fun deleteOldChatArchive() {
  val chatArchiveName = chatModel.controller.appPrefs.chatArchiveName.get()
  if (chatArchiveName != null) {
    val file1 = File("${filesDir.absolutePath}${File.separator}$chatArchiveName")
    val file2 = File("${databaseExportDir.absolutePath}${File.separator}$chatArchiveName")
    val fileDeleted = file1.delete() || file2.delete()
    if (fileDeleted || (!file1.exists() && !file2.exists())) {
      chatModel.controller.appPrefs.chatArchiveName.set(null)
      chatModel.controller.appPrefs.chatArchiveTime.set(null)
    } else {
      Log.e(TAG, "deleteOldArchive file.delete() error")
    }
  }
}

private fun importArchiveAlert(onConfirm: () -> Unit, ) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.import_database_question),
    text = generalGetString(MR.strings.your_current_chat_database_will_be_deleted_and_replaced_with_the_imported_one),
    confirmText = generalGetString(MR.strings.import_database_confirmation),
    onConfirm = onConfirm,
    destructive = true,
  )
}

fun showArchiveImportedWithErrorsAlert(archiveErrors: List<ArchiveError>) {
  AlertManager.shared.showAlertMsg(
    title = generalGetString(MR.strings.chat_database_imported),
    text = generalGetString(MR.strings.restart_the_app_to_use_imported_chat_database) + "\n\n" + generalGetString(MR.strings.non_fatal_errors_occured_during_import) + archiveErrorsText(archiveErrors))
}

fun showArchiveExportedWithErrorsAlert(description: String, archiveErrors: List<ArchiveError>, onConfirm: () -> Unit) {
  AlertManager.shared.showAlertMsg(
    title = generalGetString(MR.strings.chat_database_exported_title),
    text = description + "\n\n" + generalGetString(MR.strings.chat_database_exported_not_all_files) + archiveErrorsText(archiveErrors),
    confirmText = generalGetString(MR.strings.chat_database_exported_continue),
    onConfirm = onConfirm
  )
}

private fun archiveErrorsText(errs: List<ArchiveError>): String = "\n" + errs.map {
  when (it) {
    is ArchiveError.ArchiveErrorImport -> it.importError
    is ArchiveError.ArchiveErrorFile -> "${it.file}: ${it.fileError}"
  }
}.joinToString(separator = "\n")

suspend fun importArchive(
  importedArchiveURI: URI,
  appFilesCountAndSize: MutableState<Pair<Int, Long>>,
  progressIndicator: MutableState<Boolean>,
  migration: Boolean
): Boolean {
  val m = chatModel
  progressIndicator.value = true
  val archivePath = saveArchiveFromURI(importedArchiveURI)
  if (archivePath != null) {
    try {
      m.controller.apiDeleteStorage()
      wallpapersDir.mkdirs()
      try {
        val config = ArchiveConfig(archivePath, parentTempDirectory = databaseExportDir.toString())
        val archiveErrors = m.controller.apiImportArchive(config)
        appPrefs.shouldImportAppSettings.set(true)
        DatabaseUtils.ksDatabasePassword.remove()
        appFilesCountAndSize.value = directoryFileCountAndSize(appFilesDir.absolutePath)
        if (archiveErrors.isEmpty()) {
          operationEnded(m, progressIndicator) {
            AlertManager.shared.showAlertMsg(generalGetString(MR.strings.chat_database_imported), text = generalGetString(MR.strings.restart_the_app_to_use_imported_chat_database))
          }
          if (chatModel.localUserCreated.value == false) {
            chatModel.chatRunning.value = false
          }
          return true
        } else {
          operationEnded(m, progressIndicator) {
            showArchiveImportedWithErrorsAlert(archiveErrors)
          }
          return migration
        }
      } catch (e: Error) {
        operationEnded(m, progressIndicator) {
          AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_importing_database), e.toString())
        }
      }
    } catch (e: Error) {
      operationEnded(m, progressIndicator) {
        AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_deleting_database), e.toString())
      }
    } finally {
      File(archivePath).delete()
    }
  } else {
    progressIndicator.value = false
  }
  return false
}

private fun saveArchiveFromURI(importedArchiveURI: URI): String? {
  return try {
    val inputStream = importedArchiveURI.inputStream()
    val archiveName = getFileName(importedArchiveURI)
    if (inputStream != null && archiveName != null) {
      val archivePath = "$databaseExportDir${File.separator}$archiveName"
      val destFile = File(archivePath)
      Files.copy(inputStream, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
      archivePath
    } else {
      Log.e(TAG, "saveArchiveFromURI null inputStream")
      null
    }
  } catch (e: Exception) {
    AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_saving_database), e.stackTraceToString())
    Log.e(TAG, "saveArchiveFromURI error: ${e.stackTraceToString()}")
    null
  }
}

private fun deleteChatAlert(onConfirm: () -> Unit) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.delete_chat_profile_question),
    text = generalGetString(MR.strings.delete_chat_profile_action_cannot_be_undone_warning),
    confirmText = generalGetString(MR.strings.delete_verb),
    onConfirm = onConfirm,
    destructive = true,
  )
}

private suspend fun deleteChat(m: ChatModel, progressIndicator: MutableState<Boolean>) {
  if (!DatabaseUtils.hasAtLeastOneDatabase(dataDir.absolutePath)) {
    return
  }
  progressIndicator.value = true
  try {
    deleteChatAsync(m)
    operationEnded(m, progressIndicator) {
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.chat_database_deleted), generalGetString(MR.strings.restart_the_app_to_create_a_new_chat_profile))
    }
  } catch (e: Throwable) {
    operationEnded(m, progressIndicator) {
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_deleting_database), e.toString())
    }
  }
}

private fun setCiTTL(
  m: ChatModel,
  rhId: Long?,
  chatItemTTL: MutableState<ChatItemTTL>,
  progressIndicator: MutableState<Boolean>,
  appFilesCountAndSize: MutableState<Pair<Int, Long>>,
) {
  progressIndicator.value = true
  withBGApi {
    try {
      m.controller.setChatItemTTL(rhId, chatItemTTL.value)
      // Update model on success
      m.chatItemTTL.value = chatItemTTL.value
      afterSetCiTTL(m, progressIndicator, appFilesCountAndSize)
    } catch (e: Exception) {
      // Rollback to model's value
      chatItemTTL.value = m.chatItemTTL.value
      afterSetCiTTL(m, progressIndicator, appFilesCountAndSize)
      AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_changing_message_deletion), e.stackTraceToString())
    }
  }
}

private fun afterSetCiTTL(
  m: ChatModel,
  progressIndicator: MutableState<Boolean>,
  appFilesCountAndSize: MutableState<Pair<Int, Long>>,
) {
  progressIndicator.value = false
  appFilesCountAndSize.value = directoryFileCountAndSize(appFilesDir.absolutePath)
  withApi {
    try {
      withContext(Dispatchers.Main) {
        // this is using current remote host on purpose - if it changes during update, it will load correct chats
        val chats = m.controller.apiGetChats(m.remoteHostId())
        chatModel.chatsContext.updateChats(chats)
      }
    } catch (e: Exception) {
      Log.e(TAG, "apiGetChats error: ${e.message}")
    }
  }
}

private fun deleteFilesAndMediaAlert(onConfirm: () -> Unit) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.delete_files_and_media_question),
    text = generalGetString(MR.strings.delete_files_and_media_desc),
    confirmText = generalGetString(MR.strings.delete_verb),
    onConfirm = onConfirm,
    destructive = true
  )
}

private fun deleteFiles(appFilesCountAndSize: MutableState<Pair<Int, Long>>) {
  deleteAppFiles()
  appFilesCountAndSize.value = directoryFileCountAndSize(appFilesDir.absolutePath)
}

private fun operationEnded(m: ChatModel, progressIndicator: MutableState<Boolean>, alert: () -> Unit) {
  m.chatDbChanged.value = true
  progressIndicator.value = false
  alert.invoke()
}

private enum class SwitchYubiKeyStep {
  ENTER_CURRENT_KEY,
  SETUP_CARDS,
  SETUP_PIN_ENTER, SETUP_PIN_CONFIRM, SETUP_PIN_TAP,
  SETUP_PUK_ENTER, SETUP_PUK_CONFIRM, SETUP_PUK_TAP,
  SETUP_MGMT_PIN, SETUP_MGMT_TAP,
  ENCRYPT_PIN, ENCRYPT_TAP
}

private val ElectricBlue500 = Color(0xFF1F4CFF)
private val Green500 = Color(0xFF11994A)
private val OnSurfaceVariant = Color(0xFF3D4042)
private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f
private val font12 = 12.sp
private val font14 = 14.sp
private val font30 = 30.sp

@Composable
fun SwitchToYubiKeyView(m: ChatModel, close: () -> Unit) {
  val step = remember { mutableStateOf(SwitchYubiKeyStep.ENTER_CURRENT_KEY) }
  val currentKey = remember { mutableStateOf("") }
  val prefs = m.controller.appPrefs
  val encryptionScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
  var encryptionJob by remember { mutableStateOf<Job?>(null) }

  val storedKey = remember { DatabaseUtils.ksDatabasePassword.get() }
  val hasStoredPassphrase = remember { prefs.storeDBPassphrase.get() && storedKey != null && storedKey.isNotEmpty() }

  // Step completion tracking (same as onboarding SetupYubiKey)
  val pinCompleted = remember { mutableStateOf(false) }
  val pukCompleted = remember { mutableStateOf(false) }
  val mgmtCompleted = remember { mutableStateOf(false) }
  val allStepsCompleted by remember { derivedStateOf { pinCompleted.value && pukCompleted.value && mgmtCompleted.value } }

  // PIN/PUK entry states
  val pinState = remember { mutableStateOf("") }
  val enteredPin = remember { mutableStateOf("") }
  val enteredPuk = remember { mutableStateOf("") }

  // Management key (same as onboarding SetupYubiKeyManagementKey)
  val managementKey = remember {
    mutableStateOf(DatabaseUtils.ksYubiKeyManagementKey.get() ?: generateManagementKey())
  }
  LaunchedEffect(Unit) {
    if (DatabaseUtils.ksYubiKeyManagementKey.get() == null) {
      DatabaseUtils.ksYubiKeyManagementKey.set(managementKey.value)
    }
  }

  // Processing/overlay states
  val isProcessing = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  val errorOccurred = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf<String?>(null) }
  val processingStarted = remember { mutableStateOf(false) }
  val passphraseError = remember { mutableStateOf<String?>(null) }
  val verifyingPassphrase = remember { mutableStateOf(false) }

  val handleBack = {
    when (step.value) {
      SwitchYubiKeyStep.ENTER_CURRENT_KEY -> {
        currentKey.value = ""
        close()
      }
      SwitchYubiKeyStep.SETUP_CARDS -> {
        currentKey.value = ""
        close()
      }
      SwitchYubiKeyStep.SETUP_PIN_ENTER -> {
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
      SwitchYubiKeyStep.SETUP_PIN_CONFIRM -> {
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_PIN_ENTER
      }
      SwitchYubiKeyStep.SETUP_PIN_TAP -> {
        enteredPin.value = ""
        pinState.value = ""
        m.yubiKeyDetected.value = false
        step.value = SwitchYubiKeyStep.SETUP_PIN_ENTER
      }
      SwitchYubiKeyStep.SETUP_PUK_ENTER -> {
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
      SwitchYubiKeyStep.SETUP_PUK_CONFIRM -> {
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_PUK_ENTER
      }
      SwitchYubiKeyStep.SETUP_PUK_TAP -> {
        enteredPuk.value = ""
        pinState.value = ""
        m.yubiKeyDetected.value = false
        step.value = SwitchYubiKeyStep.SETUP_PUK_ENTER
      }
      SwitchYubiKeyStep.SETUP_MGMT_PIN -> {
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
      SwitchYubiKeyStep.SETUP_MGMT_TAP -> {
        m.yubiKeyDetected.value = false
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
      SwitchYubiKeyStep.ENCRYPT_PIN -> {
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
      SwitchYubiKeyStep.ENCRYPT_TAP -> {
        m.yubiKeyDetected.value = false
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
    }
  }
  BackHandler(onBack = handleBack)

  fun verifyAndProceed() {
    if (verifyingPassphrase.value) return
    verifyingPassphrase.value = true
    passphraseError.value = null
    encryptionScope.launch {
      try {
        val error = chatController.testStorageEncryption(currentKey.value)
        withContext(Dispatchers.Main) {
          verifyingPassphrase.value = false
          if (error == null) {
            step.value = SwitchYubiKeyStep.SETUP_CARDS
          } else {
            passphraseError.value = generalGetString(MR.strings.wrong_passphrase)
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          verifyingPassphrase.value = false
          passphraseError.value = generalGetString(MR.strings.wrong_passphrase)
        }
      }
    }
  }

  // --- Encrypt database with YubiKey (final step) ---
  fun performEncryption() {
    if (processingStarted.value) return
    processingStarted.value = true
    isProcessing.value = true
    m.yubiKeyDetected.value = false

    encryptionJob = encryptionScope.launch {
      try {
        val pin = m.secureYubiKeyPin.usePinString { it }
        if (pin.isNullOrEmpty()) {
          withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_pin_not_found) }
          return@launch
        }
        var attempts = 0; var result: Result<String>? = null
        while (attempts < 3) {
          attempts++
          if (attempts > 1) delay(1000)
          result = YubiKeyBridge.enrollForDatabaseEncryption(pin)
          if (result.isSuccess) break
          val error = result.exceptionOrNull()?.message ?: ""
          if (error.contains("NFC", true) || error.contains("Tag", true) || error.contains("timed out", true)) { if (attempts < 3) continue } else break
        }
        if (result != null && result.isSuccess) {
          val dbKey = result.getOrNull()
          if (dbKey != null && dbKey.isNotEmpty()) {
            try {
              if (m.chatRunning.value == true) stopChatAsync(m)
              val encryptionError = m.controller.apiStorageEncryption(currentKey.value, dbKey)
              if (encryptionError != null) {
                m.secureYubiKeyPin.clear()
                withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_db_failed) }
                return@launch
              }
              prefs.storeDBPassphrase.set(false); prefs.useYubiKeyForDB.set(true); prefs.initialRandomDBPassphrase.set(false)
              DatabaseUtils.ksDatabasePassword.set(dbKey)
              initChatController(dbKey); m.chatDbChanged.value = false; m.chatRunning.value = true
              m.secureYubiKeyPin.clear()
              withContext(Dispatchers.Main) { isProcessing.value = false; showSuccess.value = true; delay(2500); close() }
            } catch (e: Exception) {
              prefs.storeDBPassphrase.set(true); prefs.useYubiKeyForDB.set(false); m.secureYubiKeyPin.clear()
              withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = e.message ?: generalGetString(MR.strings.switch_to_yubikey_failed) }
            }
          } else {
            m.secureYubiKeyPin.clear()
            withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_derive_failed) }
          }
        } else {
          val errMsg = result?.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_encrypt_unknown_error)
          m.secureYubiKeyPin.clear()
          withContext(Dispatchers.Main) {
            isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true
            errorMessage.value = when {
              errMsg.contains("PIN", true) -> generalGetString(MR.strings.yubikey_encrypt_invalid_pin)
              errMsg.contains("timed out", true) -> generalGetString(MR.strings.yubikey_encrypt_timed_out)
              else -> errMsg
            }
          }
        }
      } catch (e: Exception) {
        m.secureYubiKeyPin.clear()
        withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = e.message ?: generalGetString(MR.strings.switch_to_yubikey_failed) }
      }
    }
  }

  // Watch for YubiKey detection on MGMT/ENCRYPT tap screens only
  // PIN and PUK taps are handled by their own onboarding composables below
  LaunchedEffect(Unit) { m.yubiKeyDetected.value = false }

  LaunchedEffect(m.yubiKeyDetected.value, step.value) {
    if (!m.yubiKeyDetected.value) return@LaunchedEffect
    when (step.value) {
      SwitchYubiKeyStep.SETUP_MGMT_TAP -> {
        isProcessing.value = true
        val pin = m.secureYubiKeyPin.usePinString { it }
        if (pin.isNullOrEmpty()) {
          isProcessing.value = false; errorOccurred.value = true
          errorMessage.value = generalGetString(MR.strings.yubikey_mgmt_pin_not_found)
          m.yubiKeyDetected.value = false
          return@LaunchedEffect
        }
        val result = YubiKeyBridge.setupManagementKey(pin)
        if (result.isSuccess) {
          delay(1000); isProcessing.value = false; showSuccess.value = true; delay(2000)
          showSuccess.value = false; m.yubiKeyDetected.value = false
          mgmtCompleted.value = true; step.value = SwitchYubiKeyStep.SETUP_CARDS
        } else {
          isProcessing.value = false; errorOccurred.value = true
          val error = result.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_mgmt_unknown_error)
          errorMessage.value = error
          m.yubiKeyDetected.value = false
        }
      }
      SwitchYubiKeyStep.ENCRYPT_TAP -> {
        if (!processingStarted.value) performEncryption()
      }
      else -> {}
    }
  }

  // --- Full-screen composables that replace the entire view (with `return`) ---

  // PIN: Enter → Confirm (PasscodeView)
  if (step.value == SwitchYubiKeyStep.SETUP_PIN_ENTER || step.value == SwitchYubiKeyStep.SETUP_PIN_CONFIRM) {
    val isConfirm = step.value == SwitchYubiKeyStep.SETUP_PIN_CONFIRM
    PasscodeView(
      passcode = pinState,
      title = if (isConfirm) generalGetString(MR.strings.yubikey_pin_confirm_title) else generalGetString(MR.strings.yubikey_pin_setup_title),
      reason = generalGetString(MR.strings.yubikey_pin_reason),
      submitLabel = if (isConfirm) generalGetString(MR.strings.yubikey_pin_btn_confirm) else generalGetString(MR.strings.yubikey_btn_next),
      submitEnabled = { p -> if (isConfirm) p == enteredPin.value else p.length in 6..8 },
      submit = {
        if (isConfirm) {
          if (pinState.value == enteredPin.value) { pinState.value = ""; step.value = SwitchYubiKeyStep.SETUP_PIN_TAP }
        } else {
          enteredPin.value = pinState.value; pinState.value = ""; step.value = SwitchYubiKeyStep.SETUP_PIN_CONFIRM
        }
      },
      cancel = {
        pinState.value = ""
        if (isConfirm) step.value = SwitchYubiKeyStep.SETUP_PIN_ENTER else step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
    )
    return
  }

  // PIN: Tap YubiKey — reuse onboarding ValidateWithYubiKeyScreen (handles factory check + reset)
  if (step.value == SwitchYubiKeyStep.SETUP_PIN_TAP) {
    val pinTapProcessing = remember { mutableStateOf(false) }
    val pinTapSuccess = remember { mutableStateOf(false) }
    ValidateWithYubiKeyScreen(
      m = m,
      title = generalGetString(MR.strings.yubikey_pin_store_title),
      description = generalGetString(MR.strings.yubikey_pin_reason),
      isProcessing = pinTapProcessing.value,
      showSuccess = pinTapSuccess.value,
      successMessage = generalGetString(MR.strings.yubikey_pin_created),
      onSuccess = {
        pinCompleted.value = true
        enteredPin.value = ""
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      },
      onBack = {
        enteredPin.value = ""
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_PIN_ENTER
      },
      onProcessingChange = { pinTapProcessing.value = it },
      onShowSuccessChange = { pinTapSuccess.value = it },
      pin = enteredPin.value
    )
    return
  }

  // PUK: Enter → Confirm (PasscodeView)
  if (step.value == SwitchYubiKeyStep.SETUP_PUK_ENTER || step.value == SwitchYubiKeyStep.SETUP_PUK_CONFIRM) {
    val isConfirm = step.value == SwitchYubiKeyStep.SETUP_PUK_CONFIRM
    PasscodeView(
      passcode = pinState,
      title = if (isConfirm) generalGetString(MR.strings.yubikey_puk_confirm_title) else generalGetString(MR.strings.yubikey_puk_setup_title),
      reason = generalGetString(MR.strings.yubikey_puk_reason),
      iconResource = MR.images.ic_puk,
      submitLabel = if (isConfirm) generalGetString(MR.strings.yubikey_puk_btn_confirm) else generalGetString(MR.strings.yubikey_btn_next),
      submitEnabled = { p -> if (isConfirm) p == enteredPuk.value else p.length in 6..8 },
      submit = {
        if (isConfirm) {
          if (pinState.value == enteredPuk.value) { pinState.value = ""; step.value = SwitchYubiKeyStep.SETUP_PUK_TAP }
        } else {
          enteredPuk.value = pinState.value; pinState.value = ""; step.value = SwitchYubiKeyStep.SETUP_PUK_CONFIRM
        }
      },
      cancel = {
        pinState.value = ""
        if (isConfirm) step.value = SwitchYubiKeyStep.SETUP_PUK_ENTER else step.value = SwitchYubiKeyStep.SETUP_CARDS
      }
    )
    return
  }

  // PUK: Tap YubiKey — reuse onboarding ValidateWithYubiKeyPUKScreen
  if (step.value == SwitchYubiKeyStep.SETUP_PUK_TAP) {
    val pukTapProcessing = remember { mutableStateOf(false) }
    val pukTapSuccess = remember { mutableStateOf(false) }
    ValidateWithYubiKeyPUKScreen(
      m = m,
      puk = enteredPuk.value,
      isProcessing = pukTapProcessing.value,
      showSuccess = pukTapSuccess.value,
      onSuccess = {
        pukCompleted.value = true
        enteredPuk.value = ""
        step.value = SwitchYubiKeyStep.SETUP_CARDS
      },
      onBack = {
        enteredPuk.value = ""
        pinState.value = ""
        step.value = SwitchYubiKeyStep.SETUP_PUK_ENTER
      },
      onProcessingChange = { pukTapProcessing.value = it },
      onShowSuccessChange = { pukTapSuccess.value = it }
    )
    return
  }

  // Management Key: re-enter PIN
  if (step.value == SwitchYubiKeyStep.SETUP_MGMT_PIN) {
    PasscodeView(
      passcode = pinState,
      title = generalGetString(MR.strings.yubikey_mgmt_enter_pin_title),
      reason = generalGetString(MR.strings.yubikey_mgmt_enter_pin_reason),
      submitLabel = generalGetString(MR.strings.yubikey_mgmt_btn_continue),
      submitEnabled = { it.length in 6..8 },
      submit = {
        m.secureYubiKeyPin.set(pinState.value)
        pinState.value = ""
        // Reset stale detection so the next physical tap always emits a fresh event.
        m.yubiKeyDetected.value = false
        step.value = SwitchYubiKeyStep.SETUP_MGMT_TAP
      },
      cancel = { pinState.value = ""; step.value = SwitchYubiKeyStep.SETUP_CARDS }
    )
    return
  }

  // Encrypt: re-enter PIN before tap
  if (step.value == SwitchYubiKeyStep.ENCRYPT_PIN) {
    PasscodeView(
      passcode = pinState,
      title = generalGetString(MR.strings.yubikey_encrypt_enter_pin_title),
      reason = generalGetString(MR.strings.yubikey_encrypt_enter_pin_reason),
      submitLabel = generalGetString(MR.strings.yubikey_encrypt_btn_continue),
      submitEnabled = { it.length in 6..8 },
      submit = {
        m.secureYubiKeyPin.set(pinState.value)
        pinState.value = ""
        // Reset stale detection so the next physical tap always emits a fresh event.
        m.yubiKeyDetected.value = false
        step.value = SwitchYubiKeyStep.ENCRYPT_TAP
      },
      cancel = { pinState.value = ""; step.value = SwitchYubiKeyStep.SETUP_CARDS }
    )
    return
  }

  // --- Step 1: Enter current passphrase (matches unlock screen design exactly) ---
  if (step.value == SwitchYubiKeyStep.ENTER_CURRENT_KEY) {
    val buttonEnabled = validKey(currentKey.value) && !verifyingPassphrase.value
    var showPassword by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val BorderElevated1 = Color(0xFFCECFD0)
    val DarkCharcoal400 = Color(0xFF868889)

    LaunchedEffect(Unit) {
      delay(200L)
      focusRequester.requestFocus()
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

        Text(
          text = generalGetString(MR.strings.switch_to_yubikey_confirm_title),
          fontFamily = Manrope,
          fontSize = font30,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
          textAlign = TextAlign.Center,
          lineHeight = (font30.value * lineHeightHeadlineS).sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
          text = generalGetString(MR.strings.switch_to_yubikey_enter_current),
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Normal,
          color = OnSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = (font14.value * lineHeightBody).sp
        )

        Spacer(Modifier.height(32.dp))

        ShredgramPassphraseField(
          value = currentKey.value,
          onValueChange = { currentKey.value = it; passphraseError.value = null },
          placeholder = generalGetString(MR.strings.enter_passphrase),
          showPassword = showPassword,
          onToggleVisibility = { showPassword = !showPassword },
          focusRequester = focusRequester,
          onSubmit = if (buttonEnabled) { { verifyAndProceed() } } else null
        )

        if (passphraseError.value != null) {
          Spacer(Modifier.height(8.dp))
          Text(
            passphraseError.value ?: "",
            fontFamily = DMSans, fontSize = font12, fontWeight = FontWeight.Normal,
            color = MaterialTheme.colors.error, textAlign = TextAlign.Center
          )
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
        Button(
          onClick = { verifyAndProceed() },
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
          if (verifyingPassphrase.value) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Text(
              text = "Unlock",
              fontSize = font14,
              fontWeight = FontWeight.Medium,
              fontFamily = DMSans,
              lineHeight = (font14.value * lineHeightBody).sp
            )
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
    return
  }

  // --- Non-PasscodeView screens ---
  val showingModal = showSuccess.value || isProcessing.value

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .imePadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState())
        .then(if (showingModal) Modifier.blur(16.dp) else Modifier),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // TopBar
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = handleBack,
          modifier = Modifier.height(24.dp)
        ) {
          Icon(painterResource(MR.images.ic_arrow_back_ios_new), contentDescription = stringResource(MR.strings.back), tint = MaterialTheme.colors.onBackground)
        }
        Image(painterResource(MR.images.ic_logo), contentDescription = null, modifier = Modifier)
        Spacer(Modifier.width(48.dp))
      }

      val stepIcon = when (step.value) {
        SwitchYubiKeyStep.SETUP_MGMT_TAP -> MR.images.ic_manage
        else -> MR.images.ic_key_lock
      }
      Icon(painterResource(stepIcon), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colors.onSurface)
      Spacer(Modifier.height(16.dp))

      when (step.value) {
        // --- Step 2: Setup cards (PIN, PUK, Management Key) - exact match with SetupYubiKey ---
        SwitchYubiKeyStep.SETUP_CARDS -> {
          Text(generalGetString(MR.strings.yubikey_setup_title), fontFamily = Manrope, fontSize = font30, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center, lineHeight = (font30.value * lineHeightHeadlineS).sp)
          Spacer(Modifier.height(8.dp))
          Text(generalGetString(MR.strings.yubikey_setup_description), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = (font14.value * lineHeightBody).sp)
          Spacer(Modifier.height(32.dp))

          PasskeyItemCard(
            title = generalGetString(MR.strings.yubikey_setup_pin_card_title),
            description = generalGetString(MR.strings.yubikey_setup_pin_card_description),
            selected = false, completed = pinCompleted.value, enabled = !pinCompleted.value,
            onClick = { if (!pinCompleted.value) step.value = SwitchYubiKeyStep.SETUP_PIN_ENTER },
            onNavigate = { if (!pinCompleted.value) step.value = SwitchYubiKeyStep.SETUP_PIN_ENTER },
            icon = { Icon(painterResource(MR.images.ic_lock_new), contentDescription = null, tint = if (pinCompleted.value) Green500 else MaterialTheme.colors.onSurface, modifier = Modifier.size(24.dp)) }
          )
          Spacer(Modifier.height(16.dp))
          PasskeyItemCard(
            title = generalGetString(MR.strings.yubikey_setup_puk_card_title),
            description = generalGetString(MR.strings.yubikey_setup_puk_card_description),
            selected = false, completed = pukCompleted.value, enabled = !pukCompleted.value,
            onClick = { if (!pukCompleted.value) step.value = SwitchYubiKeyStep.SETUP_PUK_ENTER },
            onNavigate = { if (!pukCompleted.value) step.value = SwitchYubiKeyStep.SETUP_PUK_ENTER },
            icon = { Icon(painterResource(MR.images.ic_puk), contentDescription = null, tint = if (pukCompleted.value) Green500 else MaterialTheme.colors.onSurface, modifier = Modifier.size(24.dp)) }
          )
          Spacer(Modifier.height(16.dp))
          PasskeyItemCard(
            title = generalGetString(MR.strings.yubikey_setup_mgmt_card_title),
            description = generalGetString(MR.strings.yubikey_setup_mgmt_card_description),
            selected = false, completed = mgmtCompleted.value, enabled = !mgmtCompleted.value,
            onClick = { if (!mgmtCompleted.value) step.value = SwitchYubiKeyStep.SETUP_MGMT_PIN },
            onNavigate = { if (!mgmtCompleted.value) step.value = SwitchYubiKeyStep.SETUP_MGMT_PIN },
            icon = { Icon(painterResource(MR.images.ic_manage), contentDescription = null, tint = if (mgmtCompleted.value) Green500 else MaterialTheme.colors.onSurface, modifier = Modifier.size(24.dp)) }
          )

          Spacer(Modifier.weight(1f))
          Spacer(Modifier.height(32.dp))

          Button(
            onClick = { if (allStepsCompleted) step.value = SwitchYubiKeyStep.ENCRYPT_PIN },
            enabled = allStepsCompleted,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(360.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ElectricBlue500, contentColor = Color.White, disabledBackgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f), disabledContentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp)
          ) { Text(generalGetString(MR.strings.yubikey_btn_next), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Medium, lineHeight = (font14.value * lineHeightBody).sp) }
          Spacer(Modifier.height(32.dp))
        }

        // --- Management Key tap screen (with key display, matching onboarding) ---
        SwitchYubiKeyStep.SETUP_MGMT_TAP -> {
          var keyVisible by remember { mutableStateOf(false) }

          Text(generalGetString(MR.strings.yubikey_mgmt_title), fontFamily = Manrope, fontSize = font30, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center, lineHeight = (font30.value * lineHeightHeadlineS).sp)
          Spacer(Modifier.height(8.dp))
          Text(generalGetString(MR.strings.yubikey_mgmt_description), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = (font14.value * lineHeightBody).sp)
          Spacer(Modifier.height(32.dp))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (keyVisible) managementKey.value else "\u2022".repeat(managementKey.value.length),
              fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.onSurface, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { keyVisible = !keyVisible }, modifier = Modifier.size(24.dp)) {
              Icon(
                painter = painterResource(if (keyVisible) MR.images.ic_visibility_off else MR.images.ic_visibility),
                contentDescription = if (keyVisible) "Hide key" else "Show key",
                tint = OnSurfaceVariant
              )
            }
          }

          Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              if (errorOccurred.value) {
                Icon(painterResource(MR.images.ic_error_filled), contentDescription = null, tint = MaterialTheme.colors.error, modifier = Modifier.size(92.dp))
              } else if (isProcessing.value) {
                ShredgramInlineSpinner(modifier = Modifier, size = 48.dp)
              } else {
                Icon(painterResource(MR.images.ic_passkey), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(92.dp))
              }
              Spacer(Modifier.height(16.dp))
              Text(
                text = when {
                  errorOccurred.value -> errorMessage.value ?: generalGetString(MR.strings.yubikey_mgmt_setup_failed)
                  isProcessing.value -> generalGetString(MR.strings.yubikey_mgmt_configuring)
                  else -> generalGetString(MR.strings.yubikey_mgmt_tap_to_store)
                },
                fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal,
                color = if (errorOccurred.value) MaterialTheme.colors.error else OnSurfaceVariant,
                textAlign = TextAlign.Center
              )
              if (errorOccurred.value) {
                Spacer(Modifier.height(16.dp))
                val showResetMgmt = errorMessage.value?.let {
                  it.contains("blocked", ignoreCase = true) || it.contains("configured", ignoreCase = true) ||
                  it.contains("authenticate", ignoreCase = true) || it.contains("invalid", ignoreCase = true)
                } ?: false
                if (showResetMgmt) {
                  Button(
                    onClick = {
                      errorOccurred.value = false; errorMessage.value = null
                      isProcessing.value = true
                      encryptionScope.launch {
                        val resetResult = YubiKeyBridge.resetToFactoryDefaults()
                        delay(500)
                        withContext(Dispatchers.Main) {
                          isProcessing.value = false
                          if (resetResult.isSuccess) {
                            pinCompleted.value = false; pukCompleted.value = false; mgmtCompleted.value = false
                            m.secureYubiKeyPin.clear()
                            step.value = SwitchYubiKeyStep.SETUP_CARDS
                          } else {
                            errorOccurred.value = true
                            errorMessage.value = "${generalGetString(MR.strings.yubikey_pin_reset_failed)}: ${resetResult.exceptionOrNull()?.message}"
                          }
                        }
                      }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                    shape = RoundedCornerShape(360.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                  ) { Text(generalGetString(MR.strings.yubikey_pin_btn_reset), color = Color.White) }
                  Spacer(Modifier.height(8.dp))
                }
                Button(
                  onClick = {
                    errorOccurred.value = false; errorMessage.value = null
                    m.yubiKeyDetected.value = false
                  },
                  colors = ButtonDefaults.buttonColors(backgroundColor = ElectricBlue500),
                  shape = RoundedCornerShape(360.dp)
                ) { Text(generalGetString(MR.strings.yubikey_pin_btn_try_again), color = Color.White) }
              }
            }
          }
        }

        // --- Encrypt tap screen ---
        SwitchYubiKeyStep.ENCRYPT_TAP -> {
          Text(generalGetString(MR.strings.yubikey_encrypt_title), fontFamily = Manrope, fontSize = font30, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center, lineHeight = (font30.value * lineHeightHeadlineS).sp)
          Spacer(Modifier.height(8.dp))
          Text(generalGetString(MR.strings.yubikey_encrypt_description), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = (font14.value * lineHeightBody).sp)

          Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              if (isProcessing.value) {
                ShredgramInlineSpinner(modifier = Modifier, size = 48.dp)
              } else {
                Icon(painterResource(MR.images.ic_passkey), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(92.dp))
              }
              Spacer(Modifier.height(16.dp))
              Text(
                text = if (isProcessing.value) generalGetString(MR.strings.yubikey_mgmt_configuring) else generalGetString(MR.strings.yubikey_encrypt_tap_passkey),
                fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal,
                color = OnSurfaceVariant, textAlign = TextAlign.Center
              )
            }
          }
        }

        else -> {}
      }
    }

    // Error overlay
    if (errorOccurred.value && errorMessage.value != null) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { errorOccurred.value = false; errorMessage.value = null; processingStarted.value = false }, contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(horizontal = 24.dp).widthIn(max = 320.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colors.surface, elevation = 8.dp) {
          Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(MR.images.ic_error), contentDescription = null, tint = MaterialTheme.colors.error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(generalGetString(MR.strings.yubikey_encrypt_failed_title), fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(errorMessage.value ?: generalGetString(MR.strings.yubikey_encrypt_unknown_error), fontFamily = DMSans, fontSize = font12, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = (font12.value * lineHeightBody).sp)
            Spacer(Modifier.height(16.dp))
            Text(generalGetString(MR.strings.yubikey_encrypt_tap_to_dismiss), fontFamily = DMSans, fontSize = font12, fontWeight = FontWeight.Normal, color = ElectricBlue500, textAlign = TextAlign.Center)
          }
        }
      }
    }

    // Processing spinner
    if (isProcessing.value) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        ShredgramInlineSpinner(modifier = Modifier, size = 40.dp)
      }
    }

    // Success overlay
    if (showSuccess.value) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 40.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colors.surface, elevation = 12.dp) {
          Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(MR.images.ic_check), contentDescription = null, tint = Green500, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(generalGetString(MR.strings.switch_to_yubikey_success), fontFamily = DMSans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center)
          }
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      encryptionJob?.cancel()
      m.secureYubiKeyPin.clear()
      if (m.chatRunning.value != true && !showSuccess.value) {
        withBGApi {
          val user = chatController.apiGetActiveUser(null)
          if (user != null) m.controller.startChat(user)
        }
      }
    }
  }
}

private enum class SwitchPassphraseStep { ENTER_PIN, TAP_KEY, SET_PASSPHRASE }

@Composable
fun SwitchToPassphraseView(m: ChatModel, close: () -> Unit) {
  val step = remember { mutableStateOf(SwitchPassphraseStep.ENTER_PIN) }
  val pinState = remember { mutableStateOf("") }
  val newPassphrase = remember { mutableStateOf("") }
  val confirmPassphrase = remember { mutableStateOf("") }
  val prefs = m.controller.appPrefs
  val encryptionScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
  var encryptionJob by remember { mutableStateOf<Job?>(null) }

  val isProcessing = remember { mutableStateOf(false) }
  val showSuccess = remember { mutableStateOf(false) }
  val errorOccurred = remember { mutableStateOf(false) }
  val errorMessage = remember { mutableStateOf<String?>(null) }
  val processingStarted = remember { mutableStateOf(false) }
  val mismatchError = remember { mutableStateOf(false) }

  var derivedDbKey by remember { mutableStateOf<String?>(null) }

  val handleBack = {
    when (step.value) {
      SwitchPassphraseStep.ENTER_PIN -> {
        pinState.value = ""
        close()
      }
      SwitchPassphraseStep.TAP_KEY -> {
        m.yubiKeyDetected.value = false
        step.value = SwitchPassphraseStep.ENTER_PIN
      }
      SwitchPassphraseStep.SET_PASSPHRASE -> {
        step.value = SwitchPassphraseStep.ENTER_PIN
        newPassphrase.value = ""
        confirmPassphrase.value = ""
        mismatchError.value = false
        processingStarted.value = false
        derivedDbKey = null
        m.secureYubiKeyPin.clear()
        m.yubiKeyDetected.value = false
      }
    }
  }
  BackHandler(onBack = handleBack)

  LaunchedEffect(m.yubiKeyDetected.value) {
    if (!m.yubiKeyDetected.value || step.value != SwitchPassphraseStep.TAP_KEY) return@LaunchedEffect
    isProcessing.value = true
    val pin = m.secureYubiKeyPin.usePinString { it }
    if (pin.isNullOrEmpty()) {
      isProcessing.value = false; errorOccurred.value = true
      errorMessage.value = generalGetString(MR.strings.yubikey_encrypt_pin_not_found)
      m.yubiKeyDetected.value = false
      return@LaunchedEffect
    }
    // IMPORTANT: use unlock (derive existing DB key), not enroll (creates new enrollment state)
    val result = YubiKeyBridge.unlockDatabase(pin)
    if (result.isSuccess) {
      derivedDbKey = result.getOrNull()
      delay(500); isProcessing.value = false; m.yubiKeyDetected.value = false
      step.value = SwitchPassphraseStep.SET_PASSPHRASE
    } else {
      isProcessing.value = false; errorOccurred.value = true
      errorMessage.value = result.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_encrypt_unknown_error)
      m.yubiKeyDetected.value = false
    }
  }

  if (step.value == SwitchPassphraseStep.ENTER_PIN) {
    PasscodeView(
      passcode = pinState,
      title = generalGetString(MR.strings.yubikey_encrypt_enter_pin_title),
      reason = generalGetString(MR.strings.yubikey_encrypt_enter_pin_reason),
      submitLabel = generalGetString(MR.strings.yubikey_encrypt_btn_continue),
      submitEnabled = { it.length in 6..8 },
      submit = {
        m.secureYubiKeyPin.set(pinState.value)
        pinState.value = ""
        step.value = SwitchPassphraseStep.TAP_KEY
      },
      cancel = { pinState.value = ""; close() }
    )
    return
  }

  val showingModal = showSuccess.value || isProcessing.value

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .imePadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState())
        .then(if (showingModal) Modifier.blur(16.dp) else Modifier),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = handleBack,
          modifier = Modifier.height(24.dp)
        ) {
          Icon(painterResource(MR.images.ic_arrow_back_ios_new), contentDescription = stringResource(MR.strings.back), tint = MaterialTheme.colors.onBackground)
        }
        Image(painterResource(MR.images.ic_logo), contentDescription = null, modifier = Modifier)
        Spacer(Modifier.width(48.dp))
      }

      Icon(painterResource(MR.images.ic_lock), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colors.onSurface)
      Spacer(Modifier.height(16.dp))

      when (step.value) {
        SwitchPassphraseStep.TAP_KEY -> {
          Text(generalGetString(MR.strings.switch_to_passphrase_title), fontFamily = Manrope, fontSize = font30, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center, lineHeight = (font30.value * lineHeightHeadlineS).sp)
          Spacer(Modifier.height(8.dp))
          Text(generalGetString(MR.strings.switch_to_passphrase_desc), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = (font14.value * lineHeightBody).sp)
          Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(painterResource(MR.images.ic_passkey), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(92.dp))
              Spacer(Modifier.height(16.dp))
              Text(generalGetString(MR.strings.yubikey_encrypt_tap_passkey), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center)
            }
          }
        }

        SwitchPassphraseStep.SET_PASSPHRASE -> {
          var showNew by remember { mutableStateOf(false) }
          var showConfirm by remember { mutableStateOf(false) }
          var newFocused by remember { mutableStateOf(false) }
          var confirmFocused by remember { mutableStateOf(false) }
          val entropy = remember(newPassphrase.value) {
            derivedStateOf {
              if (newPassphrase.value.isEmpty()) 0.0
              else passphraseEntropy(newPassphrase.value)
            }
          }
          val passwordStrength = remember(newPassphrase.value) {
            derivedStateOf {
              if (newPassphrase.value.isEmpty()) PassphraseStrength.REJECTED
              else PassphraseStrength.check(newPassphrase.value)
            }
          }
          val meetsMinLength = meetsMinimumLength(newPassphrase.value)
          val isPasswordRejected = passwordStrength.value == PassphraseStrength.REJECTED && newPassphrase.value.isNotEmpty()
          val showStrengthIndicator = (newFocused || confirmFocused) && newPassphrase.value.isNotEmpty()

          Text(generalGetString(MR.strings.switch_to_passphrase_title), fontFamily = Manrope, fontSize = font30, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center, lineHeight = (font30.value * lineHeightHeadlineS).sp)
          Spacer(Modifier.height(8.dp))
          Text(generalGetString(MR.strings.switch_to_passphrase_desc), fontFamily = DMSans, fontSize = font14, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = (font14.value * lineHeightBody).sp)
          Spacer(Modifier.height(32.dp))

          ShredgramInputField(
            value = newPassphrase.value,
            onValueChange = { newPassphrase.value = it; mismatchError.value = false },
            placeholder = generalGetString(MR.strings.switch_to_passphrase_enter_new),
            isPassword = true,
            showPassword = showNew,
            onToggleVisibility = { showNew = !showNew },
            onFocusChange = { newFocused = it }
          )
          Spacer(Modifier.height(12.dp))
          ShredgramInputField(
            value = confirmPassphrase.value,
            onValueChange = { confirmPassphrase.value = it; mismatchError.value = false },
            placeholder = generalGetString(MR.strings.switch_to_passphrase_confirm_new),
            isPassword = true,
            showPassword = showConfirm,
            onToggleVisibility = { showConfirm = !showConfirm },
            onFocusChange = { confirmFocused = it }
          )
          if (mismatchError.value) {
            Spacer(Modifier.height(8.dp))
            Text(generalGetString(MR.strings.switch_to_passphrase_mismatch), fontFamily = DMSans, fontSize = font12, color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
          }
          Spacer(Modifier.height(14.dp))
          if (showStrengthIndicator) {
            PasswordStrengthIndicator(
              strength = passwordStrength.value,
              modifier = Modifier.fillMaxWidth()
            )
          }
          Spacer(Modifier.height(24.dp))
        }

        else -> {}
      }
    }

    if (step.value == SwitchPassphraseStep.SET_PASSPHRASE) {
      val meetsMinLength = meetsMinimumLength(newPassphrase.value)
      val isPasswordRejected = newPassphrase.value.isNotEmpty() && PassphraseStrength.check(newPassphrase.value) == PassphraseStrength.REJECTED
      val canProceed = newPassphrase.value.isNotEmpty() &&
        confirmPassphrase.value.isNotEmpty() &&
        newPassphrase.value == confirmPassphrase.value &&
        meetsMinLength &&
        !isPasswordRejected &&
        !processingStarted.value
      Button(
        onClick = {
          if (newPassphrase.value != confirmPassphrase.value) {
            mismatchError.value = true
            return@Button
          }
          mismatchError.value = false
          val strength = PassphraseStrength.check(newPassphrase.value)
          val entropyFormatted = "%.1f".format(passphraseEntropy(newPassphrase.value))
          val startSwitch = {
            if (!processingStarted.value) {
              processingStarted.value = true
              isProcessing.value = true
              encryptionJob = encryptionScope.launch {
                try {
                  val oldKey = derivedDbKey ?: DatabaseUtils.ksDatabasePassword.get() ?: ""
                  val newKey = newPassphrase.value
                  if (m.chatRunning.value == true) stopChatAsync(m)
                  val err = m.controller.apiStorageEncryption(oldKey, newKey)
                  if (err != null) {
                    withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = generalGetString(MR.strings.switch_to_passphrase_failed) }
                    return@launch
                  }
                  prefs.storeDBPassphrase.set(false)
                  prefs.useYubiKeyForDB.set(false)
                  prefs.initialRandomDBPassphrase.set(false)
                  DatabaseUtils.ksDatabasePassword.remove()
                  initChatController(newKey)
                  m.chatDbChanged.value = false
                  m.chatRunning.value = true
                  m.secureYubiKeyPin.clear()
                  withContext(Dispatchers.Main) { isProcessing.value = false; showSuccess.value = true; delay(2500); close() }
                } catch (e: Exception) {
                  m.secureYubiKeyPin.clear()
                  withContext(Dispatchers.Main) { isProcessing.value = false; processingStarted.value = false; errorOccurred.value = true; errorMessage.value = e.message ?: generalGetString(MR.strings.switch_to_passphrase_failed) }
                }
              }
            }
          }
          when (strength) {
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
                onConfirm = startSwitch,
                dismissText = generalGetString(MR.strings.cancel_verb)
              )
            }
            PassphraseStrength.SECURE, PassphraseStrength.PARANOID -> startSwitch()
          }
        },
        enabled = canProceed,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
          .navigationBarsPadding()
          .imePadding()
          .height(52.dp),
        shape = RoundedCornerShape(360.dp),
        colors = ButtonDefaults.buttonColors(
          backgroundColor = ElectricBlue500,
          contentColor = Color.White,
          disabledBackgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
          disabledContentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
      ) {
        Text(
          generalGetString(MR.strings.yubikey_encrypt_btn_continue),
          fontFamily = DMSans,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (errorOccurred.value && errorMessage.value != null) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { errorOccurred.value = false; errorMessage.value = null; processingStarted.value = false }, contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(horizontal = 24.dp).widthIn(max = 320.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colors.surface, elevation = 8.dp) {
          Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(MR.images.ic_error), contentDescription = null, tint = MaterialTheme.colors.error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(generalGetString(MR.strings.yubikey_encrypt_failed_title), fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(errorMessage.value ?: "", fontFamily = DMSans, fontSize = font12, fontWeight = FontWeight.Normal, color = OnSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(generalGetString(MR.strings.yubikey_encrypt_tap_to_dismiss), fontFamily = DMSans, fontSize = font12, fontWeight = FontWeight.Normal, color = ElectricBlue500, textAlign = TextAlign.Center)
          }
        }
      }
    }

    if (isProcessing.value) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        ShredgramInlineSpinner(modifier = Modifier, size = 40.dp)
      }
    }

    if (showSuccess.value) {
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 40.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colors.surface, elevation = 12.dp) {
          Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(MR.images.ic_check), contentDescription = null, tint = Green500, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(generalGetString(MR.strings.switch_to_passphrase_success), fontFamily = DMSans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, textAlign = TextAlign.Center)
          }
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      encryptionJob?.cancel()
      m.secureYubiKeyPin.clear()
      if (m.chatRunning.value != true && !showSuccess.value) {
        withBGApi {
          val user = chatController.apiGetActiveUser(null)
          if (user != null) m.controller.startChat(user)
        }
      }
    }
  }
}

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "Dark Mode"
)*/
@Composable
fun PreviewDatabaseLayout() {
  SimpleXTheme {
    DatabaseLayout(
      progressIndicator = false,
      stopped = false,
      useKeyChain = false,
      chatDbEncrypted = false,
      passphraseSaved = false,
      initialRandomDBPassphrase = SharedPreference({ true }, {}),
      importArchiveLauncher = rememberFileChooserLauncher(true) {},
      appFilesCountAndSize = remember { mutableStateOf(0 to 0L) },
      chatItemTTL = remember { mutableStateOf(ChatItemTTL.Week) },
      currentUser = User.sampleData,
      users = listOf(UserInfo.sampleData),
      startChat = {},
      stopChatAlert = {},
      exportArchive = {},
      deleteChatAlert = {},
      deleteAppFilesAndMedia = {},
      onChatItemTTLSelected = {},
      disconnectAllHosts = {},
    )
  }
}
