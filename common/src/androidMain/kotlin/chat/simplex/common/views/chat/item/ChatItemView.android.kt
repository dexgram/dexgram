package chat.simplex.common.views.chat.item

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatItem
import chat.simplex.common.platform.FileChooserLauncher
import chat.simplex.common.platform.saveToVault
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
actual fun ReactionIcon(text: String, fontSize: TextUnit) {
  Text(text, fontSize = fontSize)
}

@Composable
actual fun SaveContentItemAction(cItem: ChatItem, saveFileLauncher: FileChooserLauncher, showMenu: MutableState<Boolean>) {
  ItemAction(stringResource(MR.strings.save_verb), painterResource(MR.images.ic_download), onClick = {
    saveToVault(cItem.file)
    showMenu.value = false
  })
}

actual fun copyItemToClipboard(cItem: ChatItem, clipboard: ClipboardManager) {
  clipboard.setText(AnnotatedString(cItem.content.text))
}
