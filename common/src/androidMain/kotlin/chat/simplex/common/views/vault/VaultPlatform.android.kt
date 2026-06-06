package chat.simplex.common.views.vault

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import chat.simplex.common.helpers.APPLICATION_ID
import chat.simplex.common.platform.androidAppContext
import chat.simplex.common.platform.tmpDir
import java.io.File

@Composable
actual fun VaultFilePicker(
    source: VaultPickSource,
    onFilePicked: (name: String, mimeType: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    when (source) {
        VaultPickSource.FILES -> {
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
        VaultPickSource.CAMERA_PHOTO, VaultPickSource.CAMERA_VIDEO ->
            VaultCameraCapture(
                isVideo = source == VaultPickSource.CAMERA_VIDEO,
                onCaptured = onFilePicked,
                onDismiss = onDismiss
            )
    }
}

/**
 * Captures a photo or video straight into the vault. The camera writes to a
 * private temp file (exposed only via our FileProvider), which we read,
 * hand to the encrypted import, and immediately delete — nothing ever lands
 * in the device gallery.
 */
@Composable
private fun VaultCameraCapture(
    isVideo: Boolean,
    onCaptured: (name: String, mimeType: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Created once: temp file + content uri + the display name we'll import under.
    val capture = remember {
        val ext = if (isVideo) "mp4" else "jpg"
        val prefix = if (isVideo) "VID" else "IMG"
        val file = File.createTempFile("vault_cam_", ".$ext", tmpDir).apply { deleteOnExit() }
        val uri = FileProvider.getUriForFile(context, "$APPLICATION_ID.provider", file)
        Triple(file, uri, "$prefix-${System.currentTimeMillis()}.$ext")
    }

    val finish = { success: Boolean ->
        if (success && capture.first.exists() && capture.first.length() > 0) {
            try {
                val bytes = capture.first.readBytes()
                onCaptured(capture.third, if (isVideo) "video/mp4" else "image/jpeg", bytes)
            } catch (e: Exception) {
                Log.e("VaultCamera", "Failed to read captured file", e)
                onDismiss()
            }
        } else onDismiss()
        try { capture.first.delete() } catch (_: Exception) {}
    }

    val captureLauncher = rememberLauncherForActivityResult(
        if (isVideo) ActivityResultContracts.CaptureVideo() else ActivityResultContracts.TakePicture()
    ) { success: Boolean -> finish(success) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try { captureLauncher.launch(capture.second) } catch (e: Exception) { Log.e("VaultCamera", "launch failed", e); onDismiss() }
        } else onDismiss()
    }

    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (launched) return@LaunchedEffect
        launched = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            try { captureLauncher.launch(capture.second) } catch (e: Exception) { Log.e("VaultCamera", "launch failed", e); onDismiss() }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

actual fun decodeVaultImage(bytes: ByteArray): ImageBitmap? = try {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) { null }

actual fun currentDeviceLabel(): DeviceLabel {
    val manufacturer = (Build.MANUFACTURER ?: "").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    val model = Build.MODEL ?: ""
    // Avoid "Samsung Samsung SM-..." when the model already carries the brand.
    val modelStr = when {
        model.isBlank() -> manufacturer
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
    }.trim()
    val name = (
        runCatching {
            android.provider.Settings.Global.getString(androidAppContext.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
        }.getOrNull()
            ?: runCatching {
                android.provider.Settings.Secure.getString(androidAppContext.contentResolver, "bluetooth_name")
            }.getOrNull()
            ?: ""
        ).trim()
    return DeviceLabel(model = modelStr, name = name)
}

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
