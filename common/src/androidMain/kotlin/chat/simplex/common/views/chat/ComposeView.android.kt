package chat.simplex.common.views.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.emoji2.emojipicker.EmojiPickerView
import chat.simplex.common.helpers.toURI
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DEFAULT_PADDING_HALF
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import java.net.URI

/**
 * Global keyboard height observer for Signal-like emoji/keyboard switching.
 * Tracks keyboard height to match emoji picker height exactly.
 * Also provides a stable "input panel height" for smooth transitions.
 */
object KeyboardHeightObserver {
  val height = mutableStateOf(280.dp)
  val isKeyboardVisible = mutableStateOf(false)
  // The stable input panel height (used to prevent chat content from shaking)
  // This should be set to max(keyboardHeight, emojiPickerHeight) during transitions
  val stableInputPanelHeight = mutableStateOf(0.dp)
}

@Composable
actual fun AttachmentSelection(
  composeState: MutableState<ComposeState>,
  attachmentOption: MutableState<AttachmentOption?>,
  processPickedFile: (URI?, String?) -> Unit,
  processPickedMedia: (List<URI>, String?) -> Unit
) {
  val cameraLauncher = rememberCameraLauncher { uri: Uri? ->
    if (uri != null) {
      val bitmap: ImageBitmap? = getBitmapFromUri(uri.toURI())
      if (bitmap != null) {
        val imagePreview = resizeImageToStrSize(bitmap, maxDataSize = 14000)
        composeState.value = composeState.value.copy(preview = ComposePreview.MediaPreview(listOf(imagePreview), listOf(UploadContent.SimpleImage(uri.toURI()))))
      }
    }
  }
  val cameraPermissionLauncher = rememberPermissionLauncher { isGranted: Boolean ->
    if (isGranted) {
      cameraLauncher.launchWithFallback()
    } else {
      showToast(generalGetString(MR.strings.toast_permission_denied))
    }
  }
  val galleryImageLauncher = rememberLauncherForActivityResult(contract = PickMultipleImagesFromGallery()) { processPickedMedia(it.map { it.toURI() }, null) }
  val galleryImageLauncherFallback = rememberGetMultipleContentsLauncher { processPickedMedia(it.map { it.toURI() }, null) }
  val galleryVideoLauncher = rememberLauncherForActivityResult(contract = PickMultipleVideosFromGallery()) { processPickedMedia(it.map { it.toURI() }, null) }
  val galleryVideoLauncherFallback = rememberGetMultipleContentsLauncher { processPickedMedia(it.map { it.toURI() }, null) }
  val filesLauncher = rememberGetContentLauncher { processPickedFile(it?.toURI(), null) }
  LaunchedEffect(attachmentOption.value) {
    when (attachmentOption.value) {
      AttachmentOption.CameraPhoto -> {
        when (PackageManager.PERMISSION_GRANTED) {
          ContextCompat.checkSelfPermission(androidAppContext, Manifest.permission.CAMERA) -> {
            cameraLauncher.launchWithFallback()
          }
          else -> {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
          }
        }
        attachmentOption.value = null
      }
      AttachmentOption.GalleryImage -> {
        try {
          galleryImageLauncher.launch(0)
        } catch (e: ActivityNotFoundException) {
          galleryImageLauncherFallback.launch("image/*")
        }
        attachmentOption.value = null
      }
      AttachmentOption.GalleryVideo -> {
        try {
          galleryVideoLauncher.launch(0)
        } catch (e: ActivityNotFoundException) {
          galleryVideoLauncherFallback.launch("video/*")
        }
        attachmentOption.value = null
      }
      AttachmentOption.File -> {
        filesLauncher.launch("*/*")
        attachmentOption.value = null
      }
      else -> {}
    }
  }
}

/**
 * Composable to observe keyboard height changes.
 * Call this in the main ChatView to track keyboard height globally.
 * Auto-closes emoji picker when keyboard becomes visible (Signal-like behavior).
 * Also manages stable input panel height for smooth transitions.
 */
@Composable
actual fun KeyboardHeightTracker(showEmojiPicker: MutableState<Boolean>?) {
  val density = LocalDensity.current
  val imeInsets = WindowInsets.ime
  val imeBottomPx = imeInsets.getBottom(density)
  val imeHeight = with(density) { imeBottomPx.toDp() }
  
  val wasKeyboardVisible = remember { mutableStateOf(false) }
  val emojiPickerHeight = maxOf(KeyboardHeightObserver.height.value, 280.dp)
  
  // Calculate stable input panel height (max of keyboard and emoji picker)
  val currentPanelHeight = when {
    showEmojiPicker?.value == true -> emojiPickerHeight
    imeHeight > 50.dp -> imeHeight
    else -> 0.dp
  }
  
  // Update stable height (only increase, never decrease during active input)
  LaunchedEffect(currentPanelHeight, showEmojiPicker?.value) {
    if (showEmojiPicker?.value == true || imeHeight > 50.dp) {
      // During active input, keep the maximum height to prevent jumping
      if (currentPanelHeight > KeyboardHeightObserver.stableInputPanelHeight.value || 
          (showEmojiPicker?.value != true && imeHeight > 50.dp)) {
        KeyboardHeightObserver.stableInputPanelHeight.value = currentPanelHeight
      }
    } else {
      // Reset when both keyboard and emoji picker are hidden
      KeyboardHeightObserver.stableInputPanelHeight.value = 0.dp
    }
  }
  
  LaunchedEffect(imeHeight) {
    val isVisible = imeHeight > 50.dp
    
    // Auto-close emoji picker when keyboard appears (user tapped input field)
    // This prevents both keyboard and emoji picker from showing simultaneously
    if (isVisible && !wasKeyboardVisible.value && showEmojiPicker?.value == true) {
      showEmojiPicker.value = false
    }
    
    wasKeyboardVisible.value = isVisible
    KeyboardHeightObserver.isKeyboardVisible.value = isVisible
    
    // Update stored height when keyboard is visible
    if (isVisible && imeHeight > 100.dp) {
      KeyboardHeightObserver.height.value = imeHeight
    }
  }
}

@Composable
actual fun EmojiPickerSheet(
  showEmojiPicker: MutableState<Boolean>,
  onEmojiSelected: (String) -> Unit
) {
  val view = LocalView.current
  val context = LocalContext.current
  val density = LocalDensity.current
  
  val imeInsets = WindowInsets.ime
  val currentKeyboardHeight = with(density) { imeInsets.getBottom(density).toDp() }
  
  val storedHeight = KeyboardHeightObserver.height.value
  val pickerHeight = maxOf(storedHeight, 280.dp)
  
  val effectiveHeight = if (currentKeyboardHeight > 50.dp) {
    maxOf(pickerHeight - currentKeyboardHeight, 0.dp)
  } else {
    pickerHeight
  }
  
  LaunchedEffect(showEmojiPicker.value) {
    if (showEmojiPicker.value) {
      val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
      imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
  }
  
  if (showEmojiPicker.value) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(effectiveHeight)
        .background(MaterialTheme.colors.surface)
    ) {
      if (effectiveHeight > 100.dp) {
        AndroidView(
          factory = { androidContext ->
            EmojiPickerView(androidContext).apply {
              emojiGridColumns = 9
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              setOnEmojiPickedListener { pickedEmoji ->
                onEmojiSelected(pickedEmoji.emoji)
              }
            }
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}
