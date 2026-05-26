package chat.simplex.common.views.chat

import SectionItemView
import TextIconSpaced
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.chatlist.acceptContactRequest
import chat.simplex.common.views.chatlist.rejectContactRequest
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay

@Composable
fun ComposeContextContactRequestActionsView(
  rhId: Long?,
  contactRequestId: Long
) {
  val inProgress = rememberSaveable { mutableStateOf(false) }
  var progressByTimeout by rememberSaveable { mutableStateOf(false) }

  KeyChangeEffect(chatModel.chatId.value) {
    if (inProgress.value) {
      inProgress.value = false
      progressByTimeout = false
    }
  }

  LaunchedEffect(inProgress.value) {
    progressByTimeout = if (inProgress.value) {
      delay(1000)
      inProgress.value
    } else {
      false
    }
  }

  Box(
    Modifier.height(60.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      Modifier
        .background(MaterialTheme.colors.surface)
        .alpha(if (progressByTimeout) 0.6f else 1f)
    ) {
      Divider()

      Row(
        Modifier
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        var rejectButtonModifier = Modifier.fillMaxWidth().fillMaxHeight().weight(1F)
        rejectButtonModifier =
          if (inProgress.value) rejectButtonModifier
          else rejectButtonModifier.clickable { showRejectRequestAlert(rhId, contactRequestId) }
        Row(
          rejectButtonModifier,
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            painterResource(MR.images.ic_close),
            contentDescription = null,
            tint = if (inProgress.value) MaterialTheme.colors.secondary else Color.Red,
          )
          TextIconSpaced(false)
          Text(
            stringResource(MR.strings.reject_contact_button),
            color = if (inProgress.value) MaterialTheme.colors.secondary else Color.Red
          )
        }
        var acceptButtonModifier = Modifier.fillMaxWidth().fillMaxHeight().weight(1F)
        acceptButtonModifier =
          if (inProgress.value) acceptButtonModifier
          else acceptButtonModifier.clickable { 
            acceptContactRequest(rhId, incognito = false, contactRequestId, isCurrentUser = true, chatModel = chatModel, close = null, inProgress = inProgress)
          }
        Row(
          acceptButtonModifier,
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            painterResource(MR.images.ic_check),
            contentDescription = null,
            tint = if (inProgress.value) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
          )
          TextIconSpaced(false)
          Text(
            stringResource(MR.strings.accept_contact_button),
            color = if (inProgress.value) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
          )
        }
      }
    }

    if (progressByTimeout) {
      ComposeProgressIndicator()
    }
  }
}

fun showRejectRequestAlert(rhId: Long?, contactRequestId: Long) {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.reject_contact_request),
    text = generalGetString(MR.strings.the_sender_will_not_be_notified),
    confirmText = generalGetString(MR.strings.reject_contact_button),
    onConfirm = {
      AlertManager.shared.hideAlert()
      rejectContactRequest(rhId, contactRequestId, chatModel, dismissToChatList = true)
    },
    destructive = true,
    hostDevice = hostDevice(rhId),
  )
}

fun showAcceptRequestAlert(rhId: Long?, contactRequestId: Long, inProgress: MutableState<Boolean>) {
  AlertManager.shared.showAlertDialogButtonsColumn(
    title = generalGetString(MR.strings.accept_contact_request),
    buttons = {
      Column {
        // Accept - directly accept as normal (public profile)
        SectionItemView({
          AlertManager.shared.hideAlert()
          acceptContactRequest(rhId, incognito = false, contactRequestId, isCurrentUser = true, chatModel = chatModel, close = null, inProgress = inProgress)
        }) {
          Text(generalGetString(MR.strings.accept_contact_button), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.primary)
        }
        // Cancel
        SectionItemView({
          AlertManager.shared.hideAlert()
        }) {
          Text(stringResource(MR.strings.cancel_verb), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colors.primary)
        }
      }
    },
    hostDevice = hostDevice(rhId),
  )
}

@Composable
fun PublicProfileWarningContent() {
  val uriHandler = LocalUriHandler.current
  
  Column(
    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Warning text
    Text(
      "By activating a public profile, your identity and activity become trackable, allowing a social graph to be built.",
      style = MaterialTheme.typography.body1,
      textAlign = TextAlign.Start,
      fontSize = 14.sp
    )
    
    Text(
      "In some countries, people are targeted not because of encryption, but because of social-graph analysis.",
      style = MaterialTheme.typography.body1,
      textAlign = TextAlign.Start,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colors.error
    )
    
    Text(
      "Be careful who you interact with when using this profile.",
      style = MaterialTheme.typography.body1,
      textAlign = TextAlign.Start,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold
    )
    
    // Learn more link
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { 
          uriHandler.openUriCatching("https://shredgram.com/publicvsprivate")
        }
        .padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        "Learn More",
        color = MaterialTheme.colors.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.Underline
      )
      Spacer(Modifier.width(4.dp))
      Icon(
        painterResource(MR.images.ic_open_in_new),
        contentDescription = null,
        tint = MaterialTheme.colors.primary,
        modifier = Modifier.size(16.dp)
      )
    }
  }
}

@Composable
fun PublicProfileWarningDialog(
  rhId: Long?,
  contactRequestId: Long,
  inProgress: MutableState<Boolean>
) {
  val dontShowAgain = remember { mutableStateOf(false) }
  
  AlertDialog(
    onDismissRequest = { AlertManager.shared.hideAlert() },
    title = {
      Text(
        "⚠️ Public Profile Notice",
        style = MaterialTheme.typography.h6,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        PublicProfileWarningContent()
        
        // Don't show again checkbox
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { 
              dontShowAgain.value = !dontShowAgain.value
            }
            .padding(vertical = 8.dp)
        ) {
          Checkbox(
            checked = dontShowAgain.value,
            onCheckedChange = { dontShowAgain.value = it },
            colors = CheckboxDefaults.colors(
              checkedColor = MaterialTheme.colors.primary
            )
          )
          Spacer(Modifier.width(4.dp))
          Text(
            "Don't show this again",
            fontSize = 14.sp,
            color = MaterialTheme.colors.onBackground
          )
        }
      }
    },
    buttons = {
      Column(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp)
      ) {
        SectionItemView({
          if (dontShowAgain.value) {
            appPreferences.showPublicProfileWarning.set(false)
          }
          AlertManager.shared.hideAlert()
          acceptContactRequest(rhId, incognito = false, contactRequestId, isCurrentUser = true, chatModel = chatModel, close = null, inProgress = inProgress)
        }) {
          Text(
            "I Understand, Continue",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Medium
          )
        }
        
        SectionItemView({
          AlertManager.shared.hideAlert()
        }) {
          Text(
            stringResource(MR.strings.cancel_verb),
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.secondary
          )
        }
      }
    },
    shape = RoundedCornerShape(corner = CornerSize(25.dp))
  )
}

fun showPublicProfileWarningDialog(rhId: Long?, contactRequestId: Long, inProgress: MutableState<Boolean>) {
  AlertManager.shared.showAlert {
    PublicProfileWarningDialog(
      rhId = rhId,
      contactRequestId = contactRequestId,
      inProgress = inProgress
    )
  }
}
