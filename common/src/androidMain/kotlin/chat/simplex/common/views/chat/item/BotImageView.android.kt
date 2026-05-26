package chat.simplex.common.views.chat.item

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.icerock.moko.resources.compose.painterResource
import chat.simplex.res.MR
import java.io.File

@Composable
actual fun BotImageView(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val context = LocalContext.current

    if (url.isBlank()) {
        PlaceholderImage(modifier, contentDescription)
        return
    }

    if (url.startsWith("data:")) {
        val bitmap = remember(url) {
            try {
                val b64 = url.substringAfter("base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            PlaceholderImage(modifier, contentDescription)
        }
        return
    }

    val imageData: Any = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("file://") -> File(url.removePrefix("file://"))
        url.startsWith("/") -> File(url)
        else -> url
    }

    val model = ImageRequest.Builder(context)
        .data(imageData)
        .crossfade(true)
        .build()

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = ColorPainter(Color(0xFF1E2440)),
        error = painterResource(MR.images.ic_image)
    )
}

@Composable
private fun PlaceholderImage(modifier: Modifier, contentDescription: String?) {
    Box(modifier.background(Color(0xFF1E2440)), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(MR.images.ic_image),
            contentDescription = contentDescription,
            modifier = Modifier.size(40.dp),
            tint = Color(0xFF475569)
        )
    }
}
