package chat.simplex.common.views.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.DMSans

@Composable
actual fun VaultMediaPlayer(
    filePath: String,
    isVideo: Boolean
) {
    Box(
        Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF607D8B).copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, null, Modifier.size(48.dp), tint = Color(0xFF607D8B))
    }
    Spacer(Modifier.height(8.dp))
    Text("Playback not available on desktop", fontSize = 12.sp, fontFamily = DMSans, color = Color(0xFF707A8A))
}
