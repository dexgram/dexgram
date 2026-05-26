package chat.simplex.common.views.vault

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.DMSans

@Composable
actual fun VaultPdfViewer(filePath: String, modifier: Modifier) {
    Text("PDF viewer not available on desktop", fontSize = 12.sp, fontFamily = DMSans, color = Color(0xFF707A8A))
}
