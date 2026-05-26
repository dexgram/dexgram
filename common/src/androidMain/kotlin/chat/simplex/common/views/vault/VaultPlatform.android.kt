package chat.simplex.common.views.vault

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import chat.simplex.common.platform.androidAppContext

@Composable
actual fun VaultFilePicker(
    onFilePicked: (name: String, mimeType: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = androidAppContext.contentResolver
                val mime = cr.getType(uri) ?: "application/octet-stream"
                var name = "file"
                cr.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: "file"
                }
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) onFilePicked(name, mime, bytes) else onDismiss()
            } catch (_: Exception) { onDismiss() }
        } else onDismiss()
    }
    LaunchedEffect(Unit) { launcher.launch(arrayOf("*/*")) }
}

actual fun decodeVaultImage(bytes: ByteArray): ImageBitmap? = try {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) { null }

actual fun writeVaultTempFile(bytes: ByteArray, extension: String): String? = try {
    val dir = java.io.File(androidAppContext.cacheDir, "vault_tmp")
    if (!dir.exists()) dir.mkdirs()
    val f = java.io.File(dir, "play_${System.currentTimeMillis()}.$extension")
    f.writeBytes(bytes)
    f.absolutePath
} catch (_: Exception) { null }

actual fun cleanupVaultTempFile(path: String) {
    try {
        val f = java.io.File(path)
        if (f.exists()) {
            f.writeBytes(ByteArray(f.length().toInt()))
            f.delete()
        }
    } catch (_: Exception) {}
}

actual fun restoreFileToDevice(bytes: ByteArray, fileName: String, mimeType: String): Boolean = try {
    val resolver = androidAppContext.contentResolver
    val mime = mimeType.ifBlank { "application/octet-stream" }

    val collection: Uri
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
    }

    if (mime.startsWith("image/")) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vault")
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else if (mime.startsWith("video/")) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Vault")
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vault")
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    val uri = resolver.insert(collection, values)
    if (uri != null) {
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } else false
} catch (e: Exception) {
    Log.e("VaultRestore", "Failed to restore $fileName", e)
    false
}
