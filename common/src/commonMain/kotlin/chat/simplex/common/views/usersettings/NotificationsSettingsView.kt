package chat.simplex.common.views.usersettings

import SectionBottomSpacer
import SectionTextFooter
import SectionView
import SectionViewSelectable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.compose.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import kotlin.collections.ArrayList

@Composable
fun NotificationsSettingsView(
  chatModel: ChatModel,
) {
  val onNotificationPreviewModeSelected = { mode: NotificationPreviewMode ->
    chatModel.controller.appPrefs.notificationPreviewMode.set(mode.name)
    chatModel.notificationPreviewMode.value = mode
  }

  NotificationsSettingsLayout(
    notificationsMode = remember { chatModel.controller.appPrefs.notificationsMode.state },
    notificationPreviewMode = chatModel.notificationPreviewMode,
    showPage = { page ->
      ModalManager.start.showCustomModal { close ->
        ModalView(
          close = close,
          showClose = false,
          showAppBar = false
        ) {
          when (page) {
            CurrentPage.NOTIFICATIONS_MODE ->
              NotificationsModeView(
                notificationsMode = chatModel.controller.appPrefs.notificationsMode.state,
                onNotificationsModeSelected = { changeNotificationsMode(it, chatModel) },
                close = close
              )
            CurrentPage.NOTIFICATION_PREVIEW_MODE ->
              NotificationPreviewView(
                notificationPreviewMode = chatModel.notificationPreviewMode,
                onNotificationPreviewModeSelected = onNotificationPreviewModeSelected,
                close = close
              )
          }
        }
      }
    },
  )
}

enum class CurrentPage {
  NOTIFICATIONS_MODE, NOTIFICATION_PREVIEW_MODE
}

@Composable
fun NotificationsSettingsLayout(
  notificationsMode: State<NotificationsMode>,
  notificationPreviewMode: State<NotificationPreviewMode>,
  showPage: (CurrentPage) -> Unit,
) {
  BackHandler(onBack = { ModalManager.start.closeModal() })
  val modes = remember { notificationModes() }
  val previewModes = remember { notificationPreviewModes() }

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
            text = stringResource(MR.strings.notifications),
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
      SectionView(null) {
      if (appPlatform == AppPlatform.ANDROID) {
        SettingsActionItemWithContent(null, stringResource(MR.strings.settings_notifications_mode_title), { showPage(CurrentPage.NOTIFICATIONS_MODE) }) {
          Text(
            modes.firstOrNull { it.value == notificationsMode.value }?.title ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colors.secondary
          )
        }
      }
      SettingsActionItemWithContent(null, stringResource(MR.strings.settings_notification_preview_mode_title), { showPage(CurrentPage.NOTIFICATION_PREVIEW_MODE) }) {
        Text(
          previewModes.firstOrNull { it.value == notificationPreviewMode.value }?.title ?: "",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colors.secondary
        )
      }
      if (platform.androidIsXiaomiDevice() && (notificationsMode.value == NotificationsMode.PERIODIC || notificationsMode.value == NotificationsMode.SERVICE)) {
        SectionTextFooter(annotatedStringResource(MR.strings.xiaomi_ignore_battery_optimization))
      }
    }
    SectionBottomSpacer()
    }
  }
}

@Composable
fun NotificationsModeView(
  notificationsMode: State<NotificationsMode>,
  onNotificationsModeSelected: (NotificationsMode) -> Unit,
  close: () -> Unit,
) {
  val modes = remember { notificationModes() }
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
              painter = painterResource(MR.images.ic_arrow_back_ios_new),
              contentDescription = stringResource(MR.strings.back),
              tint = MaterialTheme.colors.onBackground
            )
          }
          Text(
            text = stringResource(MR.strings.settings_notifications_mode_title),
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
      SectionViewSelectable(null, notificationsMode, modes, onNotificationsModeSelected)
      if (platform.androidIsXiaomiDevice() && (notificationsMode.value == NotificationsMode.PERIODIC || notificationsMode.value == NotificationsMode.SERVICE)) {
        SectionTextFooter(annotatedStringResource(MR.strings.xiaomi_ignore_battery_optimization))
      }
      SectionBottomSpacer()
    }
  }
}

@Composable
fun NotificationPreviewView(
  notificationPreviewMode: State<NotificationPreviewMode>,
  onNotificationPreviewModeSelected: (NotificationPreviewMode) -> Unit,
  close: () -> Unit,
) {
  val previewModes = remember { notificationPreviewModes() }
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
              painter = painterResource(MR.images.ic_arrow_back_ios_new),
              contentDescription = stringResource(MR.strings.back),
              tint = MaterialTheme.colors.onBackground
            )
          }
          Text(
            text = stringResource(MR.strings.settings_notification_preview_title),
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
      SectionViewSelectable(null, notificationPreviewMode, previewModes, onNotificationPreviewModeSelected)
      SectionBottomSpacer()
    }
  }
}

// mode, name, description
private fun notificationModes(): List<ValueTitleDesc<NotificationsMode>> {
  val res = ArrayList<ValueTitleDesc<NotificationsMode>>()
  res.add(
    ValueTitleDesc(
      NotificationsMode.OFF,
      generalGetString(MR.strings.notifications_mode_off),
      AnnotatedString(generalGetString(MR.strings.notifications_mode_off_desc)),
    )
  )
  res.add(
    ValueTitleDesc(
      NotificationsMode.PERIODIC,
      generalGetString(MR.strings.notifications_mode_periodic),
      AnnotatedString(generalGetString(MR.strings.notifications_mode_periodic_desc)),
    )
  )
  res.add(
    ValueTitleDesc(
      NotificationsMode.SERVICE,
      generalGetString(MR.strings.notifications_mode_service),
      AnnotatedString(generalGetString(MR.strings.notifications_mode_service_desc)),
    )
  )
  return res
}

// preview mode, name, description
fun notificationPreviewModes(): List<ValueTitleDesc<NotificationPreviewMode>> {
  val res = ArrayList<ValueTitleDesc<NotificationPreviewMode>>()
  res.add(
    ValueTitleDesc(
      NotificationPreviewMode.MESSAGE,
      generalGetString(MR.strings.notification_preview_mode_message),
      AnnotatedString(generalGetString(MR.strings.notification_preview_mode_message_desc)),
    )
  )
  res.add(
    ValueTitleDesc(
      NotificationPreviewMode.CONTACT,
      generalGetString(MR.strings.notification_preview_mode_contact),
      AnnotatedString(generalGetString(MR.strings.notification_preview_mode_contact_desc)),
    )
  )
  res.add(
    ValueTitleDesc(
      NotificationPreviewMode.HIDDEN,
      generalGetString(MR.strings.notification_preview_mode_hidden),
      AnnotatedString(generalGetString(MR.strings.notification_display_mode_hidden_desc)),
    )
  )
  return res
}

fun changeNotificationsMode(mode: NotificationsMode, chatModel: ChatModel) {
  chatModel.controller.appPrefs.notificationsMode.set(mode)
  platform.androidNotificationsModeChanged(mode)
}
