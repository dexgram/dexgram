package chat.simplex.common.views.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import java.net.URI

@Composable
actual fun AttachmentSelection(
  composeState: MutableState<ComposeState>,
  attachmentOption: MutableState<AttachmentOption?>,
  processPickedFile: (URI?, String?) -> Unit,
  processPickedMedia: (List<URI>, String?) -> Unit
) {
  val imageLauncher = rememberFileChooserMultipleLauncher {
    processPickedMedia(it, null)
  }
  val videoLauncher = rememberFileChooserMultipleLauncher {
    processPickedMedia(it, null)
  }
  val filesLauncher = rememberFileChooserLauncher(true) {
    if (it != null) processPickedFile(it, null)
  }
  LaunchedEffect(attachmentOption.value) {
    when (attachmentOption.value) {
      AttachmentOption.CameraPhoto -> {}
      AttachmentOption.GalleryImage -> {
        imageLauncher.launch("image/*")
      }
      AttachmentOption.GalleryVideo -> {
        videoLauncher.launch("video/*")
      }
      AttachmentOption.File -> {
        filesLauncher.launch("*/*")
      }
      else -> {}
    }
    attachmentOption.value = null
  }
}

@Composable
actual fun EmojiPickerSheet(
  showEmojiPicker: MutableState<Boolean>,
  onEmojiSelected: (String) -> Unit
) {
  if (showEmojiPicker.value) {
    // On desktop, show a hint about system emoji picker shortcuts
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("Use system emoji picker:")
        Text("Windows: Win + .")
        Text("Mac: Ctrl + Cmd + Space")
        Text("Linux: Ctrl + . or Ctrl + ;")
      }
    }
    // Auto-close after showing hint
    LaunchedEffect(Unit) {
      kotlinx.coroutines.delay(3000)
      showEmojiPicker.value = false
    }
  }
}

/**
 * No-op on Desktop - keyboard height tracking is only needed on Android
 */
@Composable
actual fun KeyboardHeightTracker(showEmojiPicker: MutableState<Boolean>?) {
  // No-op on Desktop
}