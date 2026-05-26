package chat.simplex.common.views.vault

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.DMSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun VaultPdfViewer(filePath: String, modifier: Modifier) {
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                pageCount = renderer.pageCount
                val bitmaps = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = 2.5f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bmp)
                }
                renderer.close()
                fd.close()
                pages = bitmaps
            } catch (_: Exception) {
                error = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pages?.forEach { it.recycle() }
        }
    }

    if (error) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Failed to render PDF", fontSize = 14.sp, fontFamily = DMSans, color = Color(0xFFE53935))
        }
        return
    }

    val loadedPages = pages
    if (loadedPages == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF1F4CFF))
                Spacer(Modifier.height(8.dp))
                Text("Rendering PDF...", fontSize = 13.sp, fontFamily = DMSans, color = Color(0xFF707A8A))
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.background(Color(0xFFF0F0F0)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "$pageCount page${if (pageCount != 1) "s" else ""}",
                fontSize = 12.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium,
                color = Color(0xFF707A8A),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        itemsIndexed(loadedPages) { index, bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}
