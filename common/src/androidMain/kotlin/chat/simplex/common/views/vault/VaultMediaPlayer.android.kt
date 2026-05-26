package chat.simplex.common.views.vault

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import chat.simplex.common.platform.androidAppContext
import chat.simplex.common.ui.theme.DMSans
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File

@Composable
actual fun VaultMediaPlayer(
    filePath: String,
    isVideo: Boolean
) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(androidAppContext).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            val uri = Uri.fromFile(File(filePath))
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    if (isVideo) {
        var isFullscreen by remember { mutableStateOf(false) }

        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setControllerShowTimeoutMs(3000)
                    controllerAutoShow = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)

                    setFullscreenButtonClickListener { full ->
                        isFullscreen = full
                        val activity = ctx as? Activity ?: return@setFullscreenButtonClickListener
                        if (full) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                        } else {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        }
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    } else {
        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerAutoShow = true
                    setControllerShowTimeoutMs(0)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    controllerShowTimeoutMs = 0
                    setDefaultArtwork(null)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(80.dp)
        )
    }
}
