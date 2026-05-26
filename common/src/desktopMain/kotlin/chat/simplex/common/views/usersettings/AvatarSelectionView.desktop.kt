package chat.simplex.common.views.usersettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.rememberFileChooserLauncher
import chat.simplex.common.platform.withLongRunningApi
import chat.simplex.common.views.helpers.getBitmapFromUri
import chat.simplex.res.MR
import java.util.Base64
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream

actual fun getAvatarBase64(avatarId: Int): String? {
    return try {
        val resource = when (avatarId) {
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
        
        // Load the image bitmap from Moko resource
        val imageBitmap = resource.image
        // Convert ImageBitmap to BufferedImage
        val originalImage = imageBitmap.toAwtImage()
        
        if (originalImage != null) {
            // Resize to match Android implementation (max dimension 192px)
            val maxDimension = 192
            val scale = minOf(maxDimension.toDouble() / originalImage.width, maxDimension.toDouble() / originalImage.height)
            val resizedWidth = (originalImage.width * scale).toInt()
            val resizedHeight = (originalImage.height * scale).toInt()
            
            val resizedImage = java.awt.image.BufferedImage(resizedWidth, resizedHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = resizedImage.createGraphics()
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(originalImage, 0, 0, resizedWidth, resizedHeight, null)
            graphics.dispose()
            
            val stream = ByteArrayOutputStream()
            ImageIO.write(resizedImage, "jpg", stream)
            val base64String = "data:image/jpg;base64," + Base64.getEncoder().encodeToString(stream.toByteArray())
            base64String
        } else {
            Log.e(TAG, "getAvatarBase64: bufferedImage is null for avatarId $avatarId")
            null
        }
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
    val processPickedImage = { uri: java.net.URI? ->
        if (uri != null) {
            val bitmap = getBitmapFromUri(uri)
            if (bitmap != null) {
                onImagePicked(bitmap)
            }
        }
    }
    val pickImageLauncher = rememberFileChooserLauncher(true, null, processPickedImage)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // On desktop, no camera available, so just show gallery button
        Box(
            modifier = Modifier.clickable {
                withLongRunningApi { pickImageLauncher.launch("image/*") }
            }
        ) {
            galleryContent()
        }
    }
}
