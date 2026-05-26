package chat.simplex.common.views.chat.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.loadImageBitmap
import dev.icerock.moko.resources.compose.painterResource
import chat.simplex.res.MR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@Composable
actual fun BotImageView(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    if (url.isBlank()) {
        PlaceholderBox(modifier, contentDescription)
        return
    }

    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                when {
                    url.startsWith("data:") -> {
                        val b64 = url.substringAfter("base64,")
                        val bytes = java.util.Base64.getDecoder().decode(b64)
                        java.io.ByteArrayInputStream(bytes).use { loadImageBitmap(it) }
                    }
                    url.startsWith("http://") || url.startsWith("https://") -> {
                        URL(url).openStream().use { loadImageBitmap(it) }
                    }
                    else -> {
                        File(url).inputStream().use { loadImageBitmap(it) }
                    }
                }
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        failed -> PlaceholderBox(modifier, contentDescription)
        else -> Box(modifier.background(Color(0xFF1E2440)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFF60A5FA), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PlaceholderBox(modifier: Modifier, contentDescription: String?) {
    Box(modifier.background(Color(0xFF1E2440)), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(MR.images.ic_image),
            contentDescription = contentDescription,
            modifier = Modifier.size(40.dp),
            tint = Color(0xFF475569)
        )
    }
}
