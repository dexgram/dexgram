package chat.simplex.common.views.usersettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource
import java.net.URI

// Expect function to convert avatar resource to base64 string
expect fun getAvatarBase64(avatarId: Int): String?

// Data class for avatar options
data class AvatarOption(
    val id: Int,
    val resource: ImageResource
)

// List of available avatars
val avatarOptions = listOf(
    AvatarOption(1, MR.images.ic_dp_1),
    AvatarOption(2, MR.images.ic_dp_2),
    AvatarOption(3, MR.images.ic_dp_3),
    AvatarOption(4, MR.images.ic_dp_4),
    AvatarOption(5, MR.images.ic_dp_5),
    AvatarOption(6, MR.images.ic_dp_6),
    AvatarOption(7, MR.images.ic_dp_7),
    AvatarOption(8, MR.images.ic_dp_8),
    AvatarOption(9, MR.images.ic_dp_9),
    AvatarOption(10, MR.images.ic_dp_10),
)

@Composable
expect fun AvatarSelectionImagePicker(
    onImagePicked: (ImageBitmap) -> Unit,
    cameraContent: @Composable () -> Unit,
    galleryContent: @Composable () -> Unit
)

@Composable
fun AvatarSelectionView(
    currentImage: String?,
    onImageSelected: (String?) -> Unit,
    close: () -> Unit
) {
    val selectedAvatarId = remember { mutableStateOf<Int?>(null) }
    val localProfileImage = remember { mutableStateOf(currentImage) }
    // Store the processed image string from camera/gallery
    val customImageStr = remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = close)
    
    Scaffold(
        topBar = {
            // Custom header with 24dp top padding
            TopAppBar(
                backgroundColor = MaterialTheme.colors.background,
                elevation = 0.dp,
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = close) {
                        Icon(
                            painterResource(MR.images.ic_close),
                            contentDescription = "Close",
                            tint = MaterialTheme.colors.onBackground
                        )
                    }
                }
            }
        },
        backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            
            // Current Profile Photo with X button to remove
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                if (localProfileImage.value != null || selectedAvatarId.value != null) {
                    // Show either the profile image or selected avatar
                    if (selectedAvatarId.value != null) {
                        val avatar = avatarOptions.find { it.id == selectedAvatarId.value }
                        if (avatar != null) {
                            Image(
                                painter = painterResource(avatar.resource),
                                contentDescription = "Selected Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                            )
                        }
                    } else {
                        // Direct image rendering with circular clipping
                        val imageValue = localProfileImage.value
                        if (imageValue != null) {
                            val imageBitmap = base64ToBitmap(imageValue)
                            if (imageBitmap != null) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Profile Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }
                    
                    // X button to remove image
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.onBackground)
                            .clickable {
                                localProfileImage.value = null
                                selectedAvatarId.value = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(MR.images.ic_close),
                            contentDescription = "Remove",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colors.background
                        )
                    }
                } else {
                    // Camera placeholder
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colors.surface,
                        elevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(MR.images.ic_photo_camera),
                                contentDescription = "Camera",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colors.secondary
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Camera and Photo buttons - platform specific, centered with 16dp space between
            AvatarSelectionImagePicker(
                onImagePicked = { bitmap ->
                    val imageStr = resizeImageToStrSize(cropToSquare(bitmap), maxDataSize = 12500)
                    customImageStr.value = imageStr
                    localProfileImage.value = imageStr
                    selectedAvatarId.value = null
                },
                cameraContent = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(80.dp)
                                .height(72.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colors.surface
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(MR.images.ic_photo_camera),
                                    contentDescription = "Camera",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colors.onBackground
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Camera",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onBackground
                        )
                    }
                },
                galleryContent = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(80.dp)
                                .height(72.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colors.surface
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(MR.images.ic_image),
                                    contentDescription = "Photo",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colors.onBackground
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Photo",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onBackground
                        )
                    }
                }
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Avatar Grid - 5 columns, 64x64 images, 11dp spacing, 24dp horizontal padding
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(avatarOptions) { avatar ->
                    AvatarGridItem(
                        avatar = avatar,
                        isSelected = selectedAvatarId.value == avatar.id,
                        onClick = {
                            selectedAvatarId.value = avatar.id
                            localProfileImage.value = null // Clear custom image when selecting avatar
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Save button
            Button(
                onClick = {
                    val imageToSave: String? = when {
                        selectedAvatarId.value != null -> {
                            // User selected a preset avatar - convert to base64
                            val base64 = getAvatarBase64(selectedAvatarId.value!!)
                            base64
                        }
                        customImageStr.value != null -> {
                            // User picked from camera/gallery
                            customImageStr.value
                        }
                        else -> {
                            // Use existing or cleared image
                            localProfileImage.value
                        }
                    }
                    // onImageSelected callback will handle closing the modal after updating state
                    onImageSelected(imageToSave)
                },
                modifier = Modifier
                    .width(160.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    painterResource(MR.images.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Save",
                    color = MaterialTheme.colors.onPrimary,
                    fontSize = 16.sp
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AvatarGridItem(
    avatar: AvatarOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colors.primary,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(avatar.resource),
            contentDescription = "Avatar ${avatar.id}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (isSelected) 58.dp else 64.dp)
                .clip(CircleShape)
        )
    }
}
