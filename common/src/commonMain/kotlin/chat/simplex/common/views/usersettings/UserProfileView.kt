package chat.simplex.common.views.usersettings

import SectionBottomSpacer
import androidx.activity.compose.BackHandler
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.ReadableText
import chat.simplex.common.platform.*
import chat.simplex.common.views.*
import chat.simplex.res.MR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

@Composable
fun UserProfileView(chatModel: ChatModel, close: () -> Unit) {
  val u = remember {chatModel.currentUser}
  val user = u.value
  KeyChangeEffect(u.value?.remoteHostId, u.value?.userId) {
    close()
  }

  if (user != null) {
    var profile by remember { mutableStateOf(user.profile.toProfile()) }
    val publicUsername = chatModel.getPublicUsername()
    val isFirstSetup = publicUsername == null
    val isProcessing = remember { mutableStateOf(false) }

    UserProfileLayout(
      profile = profile,
      publicUsername = publicUsername,
      isFirstSetup = isFirstSetup,
      isProcessing = isProcessing,
      close,
      saveProfile = { displayName, fullName, shortDescr, image, chosenUsername ->
        isProcessing.value = true
        withBGApi {
          try {
            // Register the chosen username with the server (first setup or username change)
            if (chosenUsername.isNotBlank()) {
              // Ensure permanent address exists
              var connLink = chatModel.userAddress.value?.connLinkContact
              if (connLink == null) {
                val createdAddress = chatModel.controller.apiCreateUserAddress(user.remoteHostId)
                if (createdAddress != null) {
                  withContext(Dispatchers.Main) {
                    chatModel.userAddress.value = UserContactLinkRec(
                      connLinkContact = createdAddress,
                      shortLinkDataSet = false,
                      shortLinkLargeDataSet = false,
                      addressSettings = AddressSettings(
                        businessAddress = false,
                        autoAccept = null,
                        autoReply = null
                      )
                    )
                  }
                  connLink = createdAddress
                }
              }

              val addressToRegister = connLink?.connShortLink
                ?: connLink?.connFullLink

              if (addressToRegister == null) {
                withContext(Dispatchers.Main) {
                  isProcessing.value = false
                  AlertManager.shared.showAlertMsg(
                    title = "Error",
                    text = "Could not create permanent address. Please try again."
                  )
                }
                return@withBGApi
              }

              val response = withContext(Dispatchers.IO) {
                UsernameAPI.registerUsername(
                  displayName = chosenUsername,
                  simpleXAddress = addressToRegister,
                  domain = "link"
                )
              }

              if (response?.success != true || response.data?.username == null) {
                withContext(Dispatchers.Main) {
                  isProcessing.value = false
                  AlertManager.shared.showAlertMsg(
                    title = "Username Unavailable",
                    text = response?.error ?: "Could not register this username. Please try a different name."
                  )
                }
                return@withBGApi
              }

              withContext(Dispatchers.Main) {
                chatModel.setPublicUsername(response.data.username!!)
              }
            }

            // Save the profile (name, about, image)
            val updatedProfile = profile.copy(
              displayName = displayName.trim(),
              fullName = fullName.trim(),
              shortDescr = shortDescr.trim().ifEmpty { null },
              image = image
            )
            val updated = chatModel.controller.apiUpdateProfile(user.remoteHostId, updatedProfile)
            if (updated != null) {
              val (newProfile, _) = updated
              withContext(Dispatchers.Main) {
                chatModel.updateCurrentUser(user.remoteHostId, newProfile)
                chatModel.setPublicProfileSetupCompleted(true)
                profile = newProfile
                isProcessing.value = false
              }
              close()
            } else {
              withContext(Dispatchers.Main) {
                isProcessing.value = false
                AlertManager.shared.showAlertMsg(
                  title = "Error",
                  text = "Failed to save profile. Please try again."
                )
              }
            }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) {
              isProcessing.value = false
              AlertManager.shared.showAlertMsg(
                title = "Error",
                text = "An error occurred: ${e.message}"
              )
            }
          }
        }
      }
    )
  }
}

@Composable
fun UserProfileLayout(
  profile: Profile,
  publicUsername: String? = null,
  isFirstSetup: Boolean = false,
  isProcessing: MutableState<Boolean> = mutableStateOf(false),
  close: () -> Unit,
  saveProfile: (String, String, String, String?, String) -> Unit,
) {
  val displayName = remember { mutableStateOf(profile.displayName) }
  val fullName = remember { mutableStateOf(profile.fullName) }
  val shortDescr = remember { mutableStateOf(profile.shortDescr ?: "") }
  val chosenImage = remember { mutableStateOf<URI?>(null) }
  val profileImage = remember { mutableStateOf(profile.image) }
  val chosenUsername = remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()
  val keyboardState by getKeyboardState()
  var savedKeyboardState by remember { mutableStateOf(keyboardState) }
  val focusRequester = remember { FocusRequester() }
  
  // State for editing fields
  val isEditingName = remember { mutableStateOf(false) }
  val isEditingAbout = remember { mutableStateOf(false) }
  
  // State to show avatar selection view within the same composition
  val showAvatarSelection = remember { mutableStateOf(false) }
  
  val maxBioLength = 70
  
  // Show avatar selection view or profile form based on state
  if (showAvatarSelection.value) {
    AvatarSelectionView(
      currentImage = profileImage.value,
      onImageSelected = { newImage ->
        profileImage.value = newImage
        showAvatarSelection.value = false
      },
      close = { showAvatarSelection.value = false }
    )
    return
  }
  
  Box {
      val dataUnchanged =
        displayName.value.trim() == profile.displayName &&
            fullName.value.trim() == profile.fullName &&
            shortDescr.value.trim() == (profile.shortDescr ?: "") &&
            profile.image == profileImage.value &&
            chosenUsername.value.isBlank()
      val closeWithAlert = {
        if (dataUnchanged || !canSaveProfile(displayName.value, shortDescr.value, profile)) {
          close()
        } else {
          showUnsavedChangesAlert({ saveProfile(displayName.value, fullName.value, shortDescr.value, profileImage.value, chosenUsername.value) }, close)
        }
      }
      BackHandler(onBack = closeWithAlert)
    
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
            IconButton(onClick = { closeWithAlert() }) {
              Icon(
                painterResource(MR.images.ic_arrow_back_ios_new),
                contentDescription = "Back",
                tint = MaterialTheme.colors.onBackground
              )
            }
            Text(
              "Profile",
              fontSize = 18.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colors.onBackground
            )
          }
        }
      },
      backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
          Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(scrollState)
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(Modifier.height(32.dp))
        
        // Profile Photo Section
        Box(
          modifier = Modifier.size(96.dp),
          contentAlignment = Alignment.Center
        ) {
          val imageValue = profileImage.value
          if (imageValue != null) {
            // Direct image rendering with circular clipping
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
          } else {
            // Camera placeholder circle with border and elevation
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
                  tint = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                )
              }
            }
          }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Public Photo Button - 140x37 with full rounded corners (360)
        Button(
          onClick = { showAvatarSelection.value = true },
          modifier = Modifier
            .width(140.dp)
            .height(37.dp),
          colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
          ),
          shape = RoundedCornerShape(360.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
          Icon(
            painterResource(MR.images.ic_edit),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colors.onPrimary
          )
          Spacer(Modifier.width(8.dp))
              Text(
            "Public photo",
            color = MaterialTheme.colors.onPrimary,
            fontSize = 14.sp
              )
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Public Profile ID Field
        if (isFirstSetup) {
          val displayValue = if (chosenUsername.value.isNotBlank())
            "${chosenUsername.value}.XXXX.link"
          else
            "Choose your username..."

          ProfileFieldRow(
            icon = MR.images.ic_alternate_email,
            label = "Public profile ID",
            hasInfo = true,
            value = displayValue,
            isPlaceholder = chosenUsername.value.isBlank(),
            buttonType = FieldButtonType.EDIT,
            onClick = {
              AlertManager.shared.showAlertDialogButtonsColumn(
                title = "Choose Your Username",
                buttons = {
                  Column(Modifier.padding(horizontal = 16.dp)) {
                    var tempUsername by remember { mutableStateOf(chosenUsername.value) }
                    var errorMsg by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(
                      value = tempUsername,
                      onValueChange = { raw ->
                        val filtered = raw.lowercase().filter { c -> c.isLetter() }
                        tempUsername = filtered
                        errorMsg = when {
                          filtered.isNotEmpty() && filtered.length < 3 -> "Must be at least 3 characters"
                          filtered.length > 15 -> "Must be at most 15 characters"
                          else -> null
                        }
                      },
                      label = { Text("Username") },
                      placeholder = { Text("e.g. john") },
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    if (tempUsername.isNotEmpty()) {
                      Text(
                        "Your ID will be: ${tempUsername}.XXXX.link",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                      )
                    } else {
                      Text(
                        "4 digits and .link will be added automatically",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                      )
                    }
                    if (errorMsg != null) {
                      Text(errorMsg!!, fontSize = 12.sp, color = MaterialTheme.colors.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End
                    ) {
                      TextButton(onClick = { AlertManager.shared.hideAlert() }) {
                        Text("Cancel")
                      }
                      Spacer(Modifier.width(8.dp))
                      Button(
                        onClick = {
                          chosenUsername.value = tempUsername
                          AlertManager.shared.hideAlert()
                        },
                        enabled = tempUsername.length in 3..15
                      ) {
                        Text("Set")
                      }
                    }
                  }
                }
              )
            }
          )
        } else {
          val editDisplayValue = if (chosenUsername.value.isNotBlank())
            "${chosenUsername.value}.XXXX.link"
          else
            publicUsername ?: ""

          ProfileFieldRow(
            icon = MR.images.ic_alternate_email,
            label = "Public profile ID",
            hasInfo = true,
            value = editDisplayValue,
            isPlaceholder = false,
            buttonType = FieldButtonType.EDIT,
            onClick = {
              AlertManager.shared.showAlertDialogButtonsColumn(
                title = "Change Your Username",
                buttons = {
                  Column(Modifier.padding(horizontal = 16.dp)) {
                    var tempUsername by remember { mutableStateOf(chosenUsername.value.ifBlank { extractBaseUsername(publicUsername ?: "") }) }
                    var errorMsg by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(
                      value = tempUsername,
                      onValueChange = { raw ->
                        val filtered = raw.lowercase().filter { c -> c.isLetter() }
                        tempUsername = filtered
                        errorMsg = when {
                          filtered.isNotEmpty() && filtered.length < 3 -> "Must be at least 3 characters"
                          filtered.length > 15 -> "Must be at most 15 characters"
                          else -> null
                        }
                      },
                      label = { Text("Username") },
                      placeholder = { Text("e.g. john") },
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    if (tempUsername.isNotEmpty()) {
                      Text(
                        "Your new ID will be: ${tempUsername}.XXXX.link",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                      )
                    } else {
                      Text(
                        "4 digits and .link will be added automatically",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                      )
                    }
                    if (errorMsg != null) {
                      Text(errorMsg!!, fontSize = 12.sp, color = MaterialTheme.colors.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End
                    ) {
                      TextButton(onClick = { AlertManager.shared.hideAlert() }) {
                        Text("Cancel")
                      }
                      Spacer(Modifier.width(8.dp))
                      Button(
                        onClick = {
                          val currentBase = extractBaseUsername(publicUsername ?: "")
                          if (tempUsername == currentBase) {
                            chosenUsername.value = ""
                          } else {
                            chosenUsername.value = tempUsername
                          }
                          AlertManager.shared.hideAlert()
                        },
                        enabled = tempUsername.length in 3..15
                      ) {
                        Text("Set")
                      }
                    }
                  }
                }
              )
            }
          )
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Public Profile Name Field
        ProfileFieldRow(
          icon = MR.images.ic_person,
          label = "Public profile name",
          hasInfo = true,
          value = if (displayName.value.isNotEmpty()) displayName.value else "Add your name...",
          isPlaceholder = displayName.value.isEmpty(),
          buttonType = FieldButtonType.EDIT,
          onClick = {
            // Open edit dialog for name
            AlertManager.shared.showAlertDialogButtonsColumn(
              title = "Edit Public Name",
              buttons = {
                Column(Modifier.padding(horizontal = 16.dp)) {
                  var tempName by remember { mutableStateOf(displayName.value) }
                  OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Public name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                  )
                  Spacer(Modifier.height(16.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                  ) {
                    TextButton(onClick = { AlertManager.shared.hideAlert() }) {
                      Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                      displayName.value = tempName
                      AlertManager.shared.hideAlert()
                    }) {
                      Text("Save")
            }
                  }
                }
              }
            )
          }
        )

        Spacer(Modifier.height(24.dp))
        
        // About Field
        ProfileFieldRow(
          icon = MR.images.ic_info,
          label = "About",
          hasInfo = false,
          value = if (shortDescr.value.isNotEmpty()) shortDescr.value else "Tell us about yourself...",
          isPlaceholder = shortDescr.value.isEmpty(),
          characterCount = maxBioLength - shortDescr.value.length,
          buttonType = FieldButtonType.GRAY,
          hasBottomBorder = true,
          onClick = {
            // Open edit dialog for about
            AlertManager.shared.showAlertDialogButtonsColumn(
              title = "Edit About",
              buttons = {
                Column(Modifier.padding(horizontal = 16.dp)) {
                  var tempAbout by remember { mutableStateOf(shortDescr.value) }
                  OutlinedTextField(
                    value = tempAbout,
                    onValueChange = { if (it.length <= maxBioLength) tempAbout = it },
                    label = { Text("About") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                  )
                  Spacer(Modifier.height(8.dp))
              Text(
                    "${maxBioLength - tempAbout.length} characters remaining",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.secondary
                  )
                  Spacer(Modifier.height(16.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                  ) {
                    TextButton(onClick = { AlertManager.shared.hideAlert() }) {
                      Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                      shortDescr.value = tempAbout
                      AlertManager.shared.hideAlert()
                    }) {
                      Text("Save")
                    }
                  }
                }
              }
            )
          }
        )
        
        Spacer(Modifier.height(32.dp))

        // Public profile info banner
        PublicProfileInfoBanner()

        Spacer(Modifier.height(24.dp))

        // State for display name dialog
        val showDisplayNameDialog = remember { mutableStateOf(false) }

        if (showDisplayNameDialog.value) {
          AlertDialog(
            onDismissRequest = { showDisplayNameDialog.value = false },
            title = { Text("Display Name Required", fontWeight = FontWeight.Bold) },
            text = { Text("Please set a display name before saving your profile. The current name \"Set Up Name\" is a placeholder.") },
            confirmButton = {
              Button(onClick = {
                showDisplayNameDialog.value = false
              }) {
                Text("OK")
              }
            }
          )
        }

        // Save Button
        val usernameValid = chosenUsername.value.isBlank() || chosenUsername.value.length in 3..15
        val enabled = if (isFirstSetup) {
          chosenUsername.value.length in 3..15 && canSaveProfile(displayName.value, shortDescr.value, profile) && !isProcessing.value
        } else {
          !dataUnchanged && usernameValid && canSaveProfile(displayName.value, shortDescr.value, profile) && !isProcessing.value
        }
        if (enabled || isProcessing.value) {
          Button(
            onClick = {
              if (!isProcessing.value) {
                if (displayName.value.trim() == "Set Up Name") {
                  showDisplayNameDialog.value = true
                } else {
                  saveProfile(displayName.value, fullName.value, shortDescr.value, profileImage.value, chosenUsername.value)
                }
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
              backgroundColor = MaterialTheme.colors.primary
            ),
            elevation = ButtonDefaults.elevation(
              defaultElevation = 0.dp,
              pressedElevation = 0.dp
            ),
            shape = RoundedCornerShape(26.dp)
          ) {
            if (isProcessing.value) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colors.onPrimary,
                strokeWidth = 2.dp
              )
              Spacer(Modifier.width(8.dp))
              Text(
                if (isFirstSetup) "Registering..." else "Saving...",
                color = MaterialTheme.colors.onPrimary,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
              )
            } else {
              Text(
                if (isFirstSetup) "Create Public Profile" else "Save Changes",
                color = MaterialTheme.colors.onPrimary,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
              )
            }
          }
        }
        
        Spacer(Modifier.height(32.dp))
        
          if (savedKeyboardState != keyboardState) {
            LaunchedEffect(keyboardState) {
              scope.launch {
                savedKeyboardState = keyboardState
                scrollState.animateScrollTo(scrollState.maxValue)
              }
            }
          }
          SectionBottomSpacer()
        }
      }
    }
}

enum class FieldButtonType {
  EDIT, GRAY, ADD
}

@Composable
fun ProfileFieldRow(
  icon: dev.icerock.moko.resources.ImageResource,
  label: String,
  hasInfo: Boolean,
  value: String,
  isPlaceholder: Boolean = false,
  characterCount: Int? = null,
  buttonType: FieldButtonType,
  hasBottomBorder: Boolean = false,
  onClick: () -> Unit
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left Icon - 48x48 with gray round background, 24x24 black icon inside
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painterResource(icon),
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colors.onBackground
        )
      }
      
      Spacer(Modifier.width(12.dp))
      
      // Label and Value
      Column(
        modifier = Modifier.weight(1f)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
          )
          if (hasInfo) {
            Spacer(Modifier.width(4.dp))
            Icon(
              painterResource(MR.images.ic_info),
              contentDescription = "Info",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
            )
          }
          if (characterCount != null) {
            Spacer(Modifier.weight(1f))
            Text(
              "$characterCount",
              fontSize = 12.sp,
              color = MaterialTheme.colors.secondary.copy(alpha = 0.7f)
            )
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(
          value,
          fontSize = 14.sp,
          color = if (isPlaceholder) MaterialTheme.colors.secondary.copy(alpha = 0.7f) else MaterialTheme.colors.onBackground
        )
      }
      
      Spacer(Modifier.width(12.dp))
      
      // Action Button - 48x48 with 16dp corner radius
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(
            when (buttonType) {
              FieldButtonType.EDIT -> MaterialTheme.colors.primary
              FieldButtonType.GRAY -> MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
              FieldButtonType.ADD -> MaterialTheme.colors.primary
            }
          )
          .clickable { onClick() },
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painterResource(
            when (buttonType) {
              FieldButtonType.EDIT -> MR.images.ic_edit
              FieldButtonType.GRAY -> MR.images.ic_edit
              FieldButtonType.ADD -> MR.images.ic_add
            }
          ),
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = when (buttonType) {
            FieldButtonType.EDIT -> MaterialTheme.colors.onPrimary
            FieldButtonType.GRAY -> MaterialTheme.colors.secondary
            FieldButtonType.ADD -> MaterialTheme.colors.onPrimary
          }
        )
      }
    }
    
    // Bottom border
    if (hasBottomBorder) {
      Spacer(Modifier.height(16.dp))
      Divider(
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
        thickness = 1.dp
      )
    }
  }
}

@Composable
fun EditImageButton(click: () -> Unit) {
  IconButton(
    onClick = click,
    modifier = Modifier.size(30.dp)
  ) {
    Icon(
      painterResource(MR.images.ic_photo_camera),
      contentDescription = stringResource(MR.strings.edit_image),
      tint = MaterialTheme.colors.onPrimary,
      modifier = Modifier.size(30.dp)
    )
  }
}

@Composable
fun DeleteImageButton(click: () -> Unit) {
  IconButton(onClick = click) {
    Icon(
      painterResource(MR.images.ic_close),
      contentDescription = stringResource(MR.strings.delete_image),
      tint = MaterialTheme.colors.primary,
    )
  }
}

@Composable
private fun PublicProfileInfoBanner() {
  val uriHandler = LocalUriHandler.current
  val isDark = isInDarkTheme()
  val bgColor = if (isDark) Color(0xFF1C2333) else Color(0xFFF0F4FF)
  val borderColor = if (isDark) Color(0xFF2D3A55) else Color(0xFFBFCFFF)
  val textColor = if (isDark) Color(0xFFB0BEC5) else Color(0xFF4A5568)
  val linkColor = Color(0xFF1F4CFF)

  val learnMoreText = stringResource(MR.strings.public_profile_learn_more)
  val bodyText = buildAnnotatedString {
    append(stringResource(MR.strings.public_profile_exposure_warning))
    append(" ")
    append(stringResource(MR.strings.public_profile_private_recommendation))
    append(" ")
    append(stringResource(MR.strings.public_profile_best_for))
    append(" ")
    pushStringAnnotation(tag = "URL", annotation = "https://dexgram.com/#profiles")
    withStyle(SpanStyle(color = linkColor, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)) {
      append(learnMoreText)
    }
    pop()
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(bgColor, RoundedCornerShape(12.dp))
      .border(1.dp, borderColor, RoundedCornerShape(12.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp)
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Icon(
        painter = painterResource(MR.images.ic_lock),
        contentDescription = null,
        tint = linkColor,
        modifier = Modifier
          .size(16.dp)
          .padding(top = 2.dp)
      )
      Spacer(Modifier.width(8.dp))
      androidx.compose.foundation.text.ClickableText(
        text = bodyText,
        style = MaterialTheme.typography.body2.copy(
          color = textColor,
          fontSize = 12.sp,
          lineHeight = 18.sp,
          fontFamily = DMSans
        ),
        onClick = { offset ->
          bodyText.getStringAnnotations(tag = "URL", start = offset, end = offset)
            .firstOrNull()?.let { annotation ->
              uriHandler.openUri(annotation.item)
            }
        }
      )
    }
  }
}

private fun showUnsavedChangesAlert(save: () -> Unit, revert: () -> Unit) {
  AlertManager.shared.showAlertDialogStacked(
    title = generalGetString(MR.strings.save_preferences_question),
    confirmText = generalGetString(MR.strings.save_and_notify_contacts),
    dismissText = generalGetString(MR.strings.exit_without_saving),
    onConfirm = save,
    onDismiss = revert,
  )
}

private fun isValidNewProfileName(displayName: String, profile: Profile): Boolean =
  displayName == profile.displayName || isValidDisplayName(displayName.trim())

private fun showFullName(profile: Profile): Boolean =
  profile.fullName.trim().isNotEmpty() && profile.fullName.trim() != profile.displayName.trim()

private fun canSaveProfile(displayName: String, shortDescr: String, profile: Profile): Boolean =
  displayName.trim().isNotEmpty() && isValidNewProfileName(displayName, profile) && bioFitsLimit(shortDescr)

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "Dark Mode"
)*/
@Composable
fun PreviewUserProfileLayoutEditOff() {
  SimpleXTheme {
    UserProfileLayout(
      profile = Profile.sampleData,
      close = {},
      saveProfile = { _, _, _, _, _ -> }
    )
  }
}

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "Dark Mode"
)*/
@Composable
fun PreviewUserProfileLayoutEditOn() {
  SimpleXTheme {
    UserProfileLayout(
      profile = Profile.sampleData,
      close = {},
      saveProfile = { _, _, _, _, _ -> }
    )
  }
}
