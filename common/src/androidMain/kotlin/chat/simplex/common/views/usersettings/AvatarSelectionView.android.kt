package chat.simplex.common.views.usersettings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import chat.simplex.common.helpers.toURI
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import java.io.ByteArrayOutputStream

// Map avatar IDs to moko ImageResource
private fun getAvatarResource(avatarId: Int): ImageResource {
    return when (avatarId) {
        1 -> MR.images.ic_dp_1
        2 -> MR.images.ic_dp_2
        3 -> MR.images.ic_dp_3
        4 -> MR.images.ic_dp_4
        5 -> MR.images.ic_dp_5
        6 -> MR.images.ic_dp_6
        7 -> MR.images.ic_dp_7
        8 -> MR.images.ic_dp_8
        9 -> MR.images.ic_dp_9
        10 -> MR.images.ic_dp_10
        else -> MR.images.ic_dp_1
    }
}

actual fun getAvatarBase64(avatarId: Int): String? {
    return try {
        val imageResource = getAvatarResource(avatarId)
        // Load drawable from Moko resource and convert to bitmap
        val drawable = imageResource.getDrawable(androidAppContext)
        if (drawable == null) {
            Log.e(TAG, "getAvatarBase64: drawable is null for avatarId $avatarId")
            return null
        }
        
        // Ensure proper dimensions for the bitmap (use intrinsic dimensions or default to 512x512)
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
        val originalBitmap = drawable.toBitmap(width = width, height = height, config = android.graphics.Bitmap.Config.ARGB_8888)
        
        // Resize to match camera/gallery image size (similar to resizeImageToStrSize logic)
        // Target max dimension of 192px to keep file size small
        val maxDimension = 192
        val scale = minOf(maxDimension.toFloat() / originalBitmap.width, maxDimension.toFloat() / originalBitmap.height)
        val resizedWidth = (originalBitmap.width * scale).toInt()
        val resizedHeight = (originalBitmap.height * scale).toInt()
        
        val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, resizedWidth, resizedHeight, true)
        if (resizedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        
        val stream = ByteArrayOutputStream()
        // Use JPEG with quality 75 to match camera/gallery compression
        resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
        resizedBitmap.recycle()
        
        val base64String = "data:image/jpg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        
        base64String
    } catch (e: Exception) {
        Log.e(TAG, "getAvatarBase64 error: $e")
        e.printStackTrace()
        null
    }
}

@Composable
actual fun AvatarSelectionImagePicker(
    onImagePicked: (ImageBitmap) -> Unit,
    cameraContent: @Composable () -> Unit,
    galleryContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Process picked image from camera or gallery
    val processPickedImage: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            val uriConverted = uri.toURI()
            val bitmap = getBitmapFromUri(uriConverted)
            if (bitmap != null) {
                onImagePicked(bitmap)
            }
        }
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(contract = PickFromGallery(), processPickedImage)
    val galleryLauncherFallback = rememberGetContentLauncher(processPickedImage)
    val cameraLauncher = rememberCameraLauncher(processPickedImage)
    val permissionLauncher = rememberPermissionLauncher { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launchWithFallback()
        } else {
            showToast(generalGetString(MR.strings.toast_permission_denied))
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Camera button
        Box(
            modifier = Modifier.clickable {
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                        cameraLauncher.launchWithFallback()
                    }
                    else -> {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        ) {
            cameraContent()
        }
        
        Spacer(Modifier.width(16.dp))
        
        // Photo (Gallery) button
        Box(
            modifier = Modifier.clickable {
                try {
                    galleryLauncher.launch(0)
                } catch (e: ActivityNotFoundException) {
                    galleryLauncherFallback.launch("image/*")
                }
            }
        ) {
            galleryContent()
        }
    }
}
