package chat.simplex.common.views.usersettings

import SectionView
import androidx.compose.runtime.Composable
import chat.simplex.common.model.ChatModel
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
actual fun PrivacyDeviceSection(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  setPerformLA: (Boolean) -> Unit,
) {
  SectionView(stringResource(MR.strings.settings_section_title_device)) {
    ChatLockItem(showSettingsModal, setPerformLA)
  }
}

@Composable
actual fun ProtectScreenToggle(chatModel: ChatModel) {
  PrivacyDexToggleItemInternal(
    icon = painterResource(MR.images.ic_visibility_off),
    text = stringResource(MR.strings.protect_app_screen),
    pref = chatModel.controller.appPrefs.privacyProtectScreen
  )
}
