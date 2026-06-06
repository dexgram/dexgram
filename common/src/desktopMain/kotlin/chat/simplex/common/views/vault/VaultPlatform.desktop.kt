package chat.simplex.common.views.vault

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap

@Composable
actual fun VaultFilePicker(
    source: VaultPickSource,
    onFilePicked: (name: String, mimeType: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onDismiss() }
}

actual fun decodeVaultImage(bytes: ByteArray): ImageBitmap? = null

actual fun currentDeviceLabel(): DeviceLabel =
    DeviceLabel(model = System.getProperty("os.name") ?: "Desktop", name = "")

actual fun writeVaultTempFile(bytes: ByteArray, extension: String): String? = null

actual fun cleanupVaultTempFile(path: String) {}

actual fun restoreFileToDevice(bytes: ByteArray, fileName: String, mimeType: String): Boolean = false
