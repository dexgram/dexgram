package chat.simplex.common.views.usersettings

import androidx.compose.runtime.Composable
import chat.simplex.common.model.ChatModel

@Composable
actual fun SettingsSectionApp(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showVersion: () -> Unit,
  withAuth: (title: String, desc: String, block: () -> Unit) -> Unit
) {
  // Desktop has no restart/shutdown actions
}
