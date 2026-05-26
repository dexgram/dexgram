package chat.simplex.common.views.onboarding

import SectionBottomSpacer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.usersettings.EnableSelfDestructOnboarding
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun SetupSelfDestruct(chatModel: ChatModel) {
  // Back to passphrase setup screen
  BackHandler {
    appPrefs.onboardingStage.set(OnboardingStage.Step2_5_SetupDatabasePassphrase)
  }
  
  val selfDestructPref = remember { chatModel.controller.appPrefs.selfDestruct }
  var showSetupScreen by remember { mutableStateOf(false) }

  fun proceedToNext() {
    appPrefs.onboardingStage.set(OnboardingStage.Step3_ChooseServerOperators)
  }

  if (showSetupScreen) {
    EnableSelfDestructOnboarding(
      selfDestruct = selfDestructPref,
      onComplete = { proceedToNext() }
    )
  } else {
    SetupSelfDestructExplanation(
      onSetup = { showSetupScreen = true }
    )
  }
}

@Composable
private fun SetupSelfDestructExplanation(
  onSetup: () -> Unit
) {
  Column(
    Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // TopBar - Shredgram style: 32dp vertical padding, back button height 24dp
    Row(
        Modifier
        .fillMaxWidth()
        .padding(vertical = 32.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Back button - height 24dp like Shredgram
      IconButton(
        onClick = { 
          appPrefs.onboardingStage.set(OnboardingStage.Step2_5_SetupDatabasePassphrase)
        },
        modifier = Modifier.height(24.dp)
      ) {
        Icon(
          painterResource(MR.images.ic_arrow_back_ios_new),
          contentDescription = "Back",
          tint = MaterialTheme.colors.onBackground
        )
      }
      
      // Center: Logo + "Shredgram"
      Row(verticalAlignment = Alignment.CenterVertically) {
      Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = "Shredgram Logo"
          // No explicit size - use natural drawable size like Shredgram
        )
        
        Spacer(Modifier.width(8.dp))
//
//        Text(
//          text = "Shredgram",
//          fontFamily = Manrope,
//          fontSize = 20.sp,
//          fontWeight = FontWeight.Bold,
//          color = MaterialTheme.colors.onBackground
//        )
      }
      
      // Spacer for symmetry
      Spacer(Modifier.width(48.dp))
    }

    // Eraser/Wipe Icon
        Icon(
      painter = painterResource(MR.images.ic_eraser),
          contentDescription = null,
      modifier = Modifier.size(24.dp),
          tint = Color.Black
        )

        Spacer(Modifier.height(32.dp))

    // Title
        Text(
      text = "Set your wipe mode",
      fontFamily = Manrope,
      style = MaterialTheme.typography.h4.copy(
          fontWeight = FontWeight.Bold,
        fontSize = 32.sp
      ),
          textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

    // Description with highlighted text
    val description = buildAnnotatedString {
      append("Choose your preferred way of ")
      withStyle(style = SpanStyle(color = Color(0xFFD97706), fontWeight = FontWeight.SemiBold)) {
        append("wiping all data")
      }
      append(". This feature should be used only in case of emergency only.")
    }

        Text(
      text = description,
      style = MaterialTheme.typography.body1.copy(
        fontSize = 16.sp,
        color = Color.Gray,
        lineHeight = 24.sp
      ),
          textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(Modifier.height(32.dp))

    // Duress PIN Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      backgroundColor = Color.Transparent,
      border = BorderStroke(1.dp, MaterialTheme.colors.primary),
      elevation = 0.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 16.dp)
      ) {
        // Top row: Icon, Title, and Checkmark/Circle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Icon on the left
          Icon(
            painter = painterResource(MR.images.ic_eraser),
            contentDescription = "Duress PIN",
            modifier = Modifier.size(16.dp),
            tint = Color.Black
          )
          
          Spacer(Modifier.width(12.dp))
          
          // Title - flexible width
          Text(
            text = "Duress PIN",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 2
          )
          
          Spacer(Modifier.width(12.dp))
          
          // Checkmark in circle (always selected)
          Box(
            modifier = Modifier
              .size(24.dp)
              .border(
                width = 2.dp,
                color = MaterialTheme.colors.primary,
                shape = RoundedCornerShape(12.dp)
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painter = painterResource(MR.images.ic_check),
              contentDescription = "Selected",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colors.primary
            )
          }
        }
        
        // Description below - responsive padding
        Spacer(Modifier.height(8.dp))
        Text(
          text = "Use a custom 6-digit PIN to wipe the entire app database",
          fontSize = 14.sp,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
          lineHeight = 20.sp,
          modifier = Modifier.padding(start = 28.dp) // Align with title (icon size + spacing)
        )
      }
    }

    Spacer(Modifier.height(16.dp))

    // Warning Box
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        painter = painterResource(MR.images.ic_info),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = Color.Black
      )
      
      Spacer(Modifier.width(12.dp))
      
      val warningText = buildAnnotatedString {
        append("Whenever you enter your ")
        withStyle(style = SpanStyle(color = Color(0xFFD97706), fontWeight = FontWeight.SemiBold)) {
          append("Duress PIN")
        }
        append(" while unlocking the app, all ")
        withStyle(style = SpanStyle(color = Color(0xFFD97706), fontWeight = FontWeight.SemiBold)) {
          append("your app data will be deleted")
        }
        append(", and an empty profile with a new name will be created.")
      }
      
      Text(
        text = warningText,
        style = MaterialTheme.typography.body2.copy(
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = Color.Black
        )
          )
        }

        Spacer(Modifier.weight(1f))

    // Next Button
    Button(
      onClick = onSetup,
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .clip(RoundedCornerShape(28.dp)),
      colors = ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.primary
      )
    ) {
      Text(
        text = "Next",
        style = MaterialTheme.typography.button.copy(
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold
        ),
        color = Color.White
      )
    }

    Spacer(Modifier.height(24.dp))
  }
}