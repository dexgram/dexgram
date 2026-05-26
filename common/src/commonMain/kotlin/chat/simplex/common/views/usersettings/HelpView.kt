package chat.simplex.common.views.usersettings

import androidx.activity.compose.BackHandler
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.SimpleXTheme
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun HelpView(userDisplayName: String) {
  HelpLayout(userDisplayName)
}

@Composable
fun HelpLayout(userDisplayName: String) {
  BackHandler(onBack = { ModalManager.start.closeModal() })

  Scaffold(
    topBar = {
      TopAppBar(
        backgroundColor = MaterialTheme.colors.background,
        elevation = 0.dp,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 24.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = { ModalManager.start.closeModal() }) {
            Icon(
              painter = painterResource(MR.images.ic_arrow_back_ios_new),
              contentDescription = stringResource(MR.strings.back),
              tint = MaterialTheme.colors.onBackground
            )
          }
          Text(
            text = stringResource(MR.strings.how_to_use_simplex_chat),
            fontSize = 18.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground
          )
        }
      }
    },
    backgroundColor = MaterialTheme.colors.background,
    bottomBar = {}
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(Modifier.height(12.dp))

      Text(
        text = stringResource(MR.strings.dexgram_how_to_title),
        fontSize = 22.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onBackground
      )

      Spacer(Modifier.height(8.dp))

      Text(
        text = String.format(stringResource(MR.strings.dexgram_how_to_welcome), userDisplayName),
        fontSize = 14.sp,
        fontFamily = DMSans,
        color = MaterialTheme.colors.secondary,
        lineHeight = 22.sp
      )

      Spacer(Modifier.height(20.dp))
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_1_title),
        stringResource(MR.strings.dexgram_how_to_section_1_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_2_title),
        stringResource(MR.strings.dexgram_how_to_section_2_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_3_title),
        stringResource(MR.strings.dexgram_how_to_section_3_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_4_title),
        stringResource(MR.strings.dexgram_how_to_section_4_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_5_title),
        stringResource(MR.strings.dexgram_how_to_section_5_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_6_title),
        stringResource(MR.strings.dexgram_how_to_section_6_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_7_title),
        stringResource(MR.strings.dexgram_how_to_section_7_body)
      )
      HelpSection(
        stringResource(MR.strings.dexgram_how_to_section_8_title),
        stringResource(MR.strings.dexgram_how_to_section_8_body)
      )

      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun HelpSection(title: String, body: String) {
  Text(
    text = title,
    fontSize = 15.sp,
    fontFamily = DMSans,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colors.onBackground
  )
  Spacer(Modifier.height(4.dp))
  Text(
    text = body,
    fontSize = 14.sp,
    fontFamily = DMSans,
    color = MaterialTheme.colors.secondary,
    lineHeight = 22.sp
  )
  Spacer(Modifier.height(14.dp))
}

@Preview
@Composable
fun PreviewHelpView() {
  SimpleXTheme {
    HelpLayout("Alice")
  }
}
