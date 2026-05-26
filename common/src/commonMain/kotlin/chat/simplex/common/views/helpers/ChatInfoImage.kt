package chat.simplex.common.views.helpers

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.unit.*
import chat.simplex.common.model.BusinessChatType
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.model.ChatInfo
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import kotlin.math.max
import kotlin.random.Random

// Helper function to get random avatar from ic_dp_1 to ic_dp_10
fun getRandomDpAvatarResource(seed: String? = null): ImageResource {
  val id = if (seed != null) {
    // Use seed (like contact name or ID) for consistent avatar per contact
    val hash = seed.hashCode()
    val absHash = if (hash < 0) -hash else hash
    (absHash % 10) + 1
  } else {
    // Truly random
    Random.nextInt(1, 11)
  }
  
  return when (id) {
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
    else -> MR.images.ic_dp_1 // Fallback
  }
}

// Helper function to get random group avatar from group_1 to group_21
private fun getRandomGroupAvatarResource(seed: String? = null): ImageResource {
  val id = if (seed != null) {
    // Use seed (like group name or ID) for consistent avatar per group
    val hash = seed.hashCode()
    val absHash = if (hash < 0) -hash else hash
    (absHash % 21) + 1
  } else {
    // Truly random
    Random.nextInt(1, 22)
  }
  
  return when (id) {
    1 -> MR.images.group_1
    2 -> MR.images.group_2
    3 -> MR.images.group_3
    4 -> MR.images.group_4
    5 -> MR.images.group_5
    6 -> MR.images.group_6
    7 -> MR.images.group_7
    8 -> MR.images.group_8
    9 -> MR.images.group_9
    10 -> MR.images.group_10
    11 -> MR.images.group_11
    12 -> MR.images.group_12
    13 -> MR.images.group_13
    14 -> MR.images.group_14
    15 -> MR.images.group_15
    16 -> MR.images.group_16
    17 -> MR.images.group_17
    18 -> MR.images.group_18
    19 -> MR.images.group_19
    20 -> MR.images.group_20
    21 -> MR.images.group_21
    else -> MR.images.group_1 // Fallback
  }
}

@Composable
fun ChatInfoImage(chatInfo: ChatInfo, size: Dp, iconColor: Color = MaterialTheme.colors.secondaryVariant, shadow: Boolean = false) {
  val icon =
    when (chatInfo) {
      is ChatInfo.Group -> chatInfo.groupInfo.chatIconName
      is ChatInfo.Local -> MR.images.ic_folder_filled
      is ChatInfo.Direct -> chatInfo.contact.chatIconName
      else -> MR.images.ic_account_circle_filled
    }
  
  // Use contact/group name as seed for consistent random avatar
  val seed = when (chatInfo) {
    is ChatInfo.Direct -> chatInfo.contact.displayName
    is ChatInfo.Group -> chatInfo.groupInfo.displayName
    else -> null
  }
  
  // Check if this is a group
  val isGroup = chatInfo is ChatInfo.Group
  
  // For incognito connections, force default avatar (don't show their profile picture)
  val imageToShow = if (chatInfo is ChatInfo.Direct && chatInfo.contact.contactConnIncognito) {
    null  // Force random default avatar for incognito connections
  } else {
    chatInfo.image  // Show actual profile picture for public/permanent connections
  }
  
  ProfileImage(size, imageToShow, icon, if (chatInfo is ChatInfo.Local) NoteFolderIconColor else iconColor, seed = seed, isGroup = isGroup)
}

@Composable
fun IncognitoImage(size: Dp, iconColor: Color = MaterialTheme.colors.secondaryVariant) {
  Box(Modifier.size(size)) {
    Icon(
      painterResource(MR.images.ic_theater_comedy_filled), stringResource(MR.strings.incognito),
      modifier = Modifier.size(size).padding(size / 12),
      iconColor
    )
  }
}

@Composable
fun ProfileImage(
  size: Dp,
  image: String? = null,
  icon: ImageResource = MR.images.ic_account_circle_filled,
  color: Color = MaterialTheme.colors.secondaryVariant,
  backgroundColor: Color? = null,
  blurred: Boolean = false,
  seed: String? = null, // Seed for consistent random avatar per contact/group
  isGroup: Boolean = false // Whether this is a group (uses group avatars)
) {
  // Generate stable random avatar based on seed and type (contact vs group)
  val defaultAvatar = remember(seed, isGroup) { 
    if (isGroup) getRandomGroupAvatarResource(seed) else getRandomDpAvatarResource(seed)
  }
  
  LaunchedEffect(image, isGroup) {
    if (image != null) {
    } else {
    }
  }
  
  Box(Modifier.size(size)) {
    if (image == null) {
      // Show random avatar: ic_dp_1 to ic_dp_10 for contacts, group_1 to group_21 for groups
      Image(
        painter = painterResource(defaultAvatar),
          contentDescription = stringResource(MR.strings.icon_descr_profile_image_placeholder),
        contentScale = ContentScale.Crop,
        modifier = ProfileIconModifier(size, blurred = blurred)
        )
    } else {
      val imageBitmap = base64ToBitmap(image)
      Image(
        imageBitmap,
        stringResource(MR.strings.image_descr_profile_image),
        contentScale = ContentScale.Crop,
        modifier = ProfileIconModifier(size, blurred = blurred)
      )
    }
  }
}

@Composable
fun ProfileImage(size: Dp, image: ImageResource) {
  Image(
    painterResource(image),
    stringResource(MR.strings.image_descr_profile_image),
    contentScale = ContentScale.Crop,
    modifier = ProfileIconModifier(size)
  )
}

@Composable
fun ProfileIconModifier(size: Dp, padding: Boolean = true, blurred: Boolean = false): Modifier {
  // Always use circular clipping for profile images
  val m = Modifier.size(size).clip(CircleShape)
  return if (blurred) m.blur(size / 4) else m
}

/** [AccountCircleFilled] has its inner padding which leads to visible border if there is background underneath.
 * This is workaround
 * */
@Composable
fun ProfileImageForActiveCall(
  size: Dp,
  image: String? = null,
  color: Color = MaterialTheme.colors.secondaryVariant,
  backgroundColor: Color? = null,
  seed: String? = null // Seed for consistent random avatar
  ) {
  // Generate stable random avatar based on seed
  val defaultAvatar = remember(seed) { getRandomDpAvatarResource(seed) }
  
  if (image == null) {
    // Show random avatar from ic_dp_1 to ic_dp_10 as default
    Image(
      painter = painterResource(defaultAvatar),
        contentDescription = stringResource(MR.strings.icon_descr_profile_image_placeholder),
      contentScale = ContentScale.Crop,
      modifier = Modifier.requiredSize(size).clip(CircleShape)
      )
  } else {
    val imageBitmap = base64ToBitmap(image)
    Image(
      imageBitmap,
      stringResource(MR.strings.image_descr_profile_image),
      contentScale = ContentScale.Crop,
      modifier = ProfileIconModifier(size, padding = false)
    )
  }
}

@Preview
@Composable
fun PreviewChatInfoImage() {
  SimpleXTheme {
    ChatInfoImage(
      chatInfo = ChatInfo.Direct.sampleData,
      size = 55.dp
    )
  }
}
