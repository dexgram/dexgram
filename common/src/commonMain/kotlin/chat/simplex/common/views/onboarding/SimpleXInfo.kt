package chat.simplex.common.views.onboarding

import SectionBottomSpacer
import androidx.compose.animation.core.animateDpAsState
import androidx.activity.compose.BackHandler
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.migration.MigrateToDeviceView
import chat.simplex.common.views.migration.MigrationToState
import chat.simplex.res.MR
import dev.icerock.moko.resources.StringResource
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun SimpleXInfo(chatModel: ChatModel, onboarding: Boolean = true) {
  if (onboarding) {
    CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
      ModalView({}, showClose = false, showAppBar = false) {
        SimpleXInfoLayout(
          user = chatModel.currentUser.value,
          onboardingStage = chatModel.controller.appPrefs.onboardingStage
        )
      }
    }
  } else {
    SimpleXInfoLayout(
      user = chatModel.currentUser.value,
      onboardingStage = null
    )
  }
}

@Composable
fun SimpleXInfoLayout(
  user: User?,
  onboardingStage: SharedPreference<OnboardingStage>?
) {
  if (onboardingStage == null) {
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
              text = stringResource(MR.strings.about_simplex_chat),
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
      ColumnWithScrollBar(
        Modifier
          .padding(paddingValues)
          .padding(horizontal = 16.dp)
      ) {
        ReadableText(MR.strings.dexgram_about_intro_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_intro_body)
        ReadableText(MR.strings.dexgram_about_security_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_security_body)
        ReadableText(MR.strings.dexgram_about_duress_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_duress_body)
        ReadableText(MR.strings.dexgram_about_protocol_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_protocol_body)
        ReadableText(MR.strings.dexgram_about_wallet_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_wallet_body)
        ReadableText(MR.strings.dexgram_about_vault_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_vault_body)
        ReadableText(MR.strings.dexgram_about_privacy_title, style = MaterialTheme.typography.h2)
        ReadableText(MR.strings.dexgram_about_privacy_body)
        SectionBottomSpacer()
      }
    }
    return
  }

  ColumnWithScrollBar(
    Modifier
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            SignalDarkBackground,
            SignalBlue,
            Color.Black
          )
        )
      )
      .padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(Modifier.widthIn(max = if (appPlatform.isAndroid) 250.dp else 500.dp).padding(top = DEFAULT_PADDING + 8.dp), contentAlignment = Alignment.Center) {
      SimpleXLogo()
    }

    OnboardingInformationButton(
      stringResource(MR.strings.next_generation_of_private_messaging),
      onClick = { ModalManager.fullscreen.showModal { HowItWorks(user, onboardingStage) } },
    )

    Spacer(Modifier.weight(1f))

    Column {
      InfoRow(painterResource(MR.images.privacy), MR.strings.privacy_redefined, MR.strings.first_platform_without_user_ids, width = 60.dp)
      InfoRow(painterResource(MR.images.shield), MR.strings.immune_to_spam_and_abuse, MR.strings.people_can_connect_only_via_links_you_share, width = 46.dp)
      InfoRow(painterResource(if (isInDarkTheme()) MR.images.decentralized_light else MR.images.decentralized), MR.strings.decentralized, MR.strings.opensource_protocol_and_code_anybody_can_run_servers)
    }

    Column(Modifier.fillMaxHeight().weight(1f)) { }

    Column(Modifier.widthIn(max = if (appPlatform.isAndroid) 450.dp else 1000.dp).align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally,) {
      OnboardingActionButton(user, onboardingStage)
      TextButtonBelowOnboardingButton(stringResource(MR.strings.migrate_from_another_device)) {
        chatModel.migrationState.value = MigrationToState.PasteOrScanLink
        ModalManager.fullscreen.showCustomModal { close -> MigrateToDeviceView(close) }
      }
    }
  }
  LaunchedEffect(Unit) {
    if (chatModel.migrationState.value != null && !ModalManager.fullscreen.hasModalsOpen()) {
      ModalManager.fullscreen.showCustomModal(animated = false) { close -> MigrateToDeviceView(close) }
    }
  }
}

@Composable
fun SimpleXLogo() {
  Image(
    painter = painterResource(if (isInDarkTheme()) MR.images.logo_light else MR.images.logo),
    contentDescription = stringResource(MR.strings.image_descr_simplex_logo),
    contentScale = ContentScale.FillWidth,
    modifier = Modifier
      .padding(vertical = DEFAULT_PADDING)
      .fillMaxWidth()
  )
}

@Composable
private fun InfoRow(icon: Painter, titleId: StringResource, textId: StringResource, width: Dp = 58.dp) {
  Row(Modifier.padding(bottom = 27.dp), verticalAlignment = Alignment.Top) {
    Spacer(Modifier.width((4.dp + 58.dp - width) / 2))
    Image(icon, contentDescription = null, modifier = Modifier
      .width(width))
    Spacer(Modifier.width((4.dp + 58.dp - width) / 2 + DEFAULT_PADDING_HALF + 7.dp))
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(DEFAULT_PADDING_HALF)) {
      Text(stringResource(titleId), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h3, lineHeight = 24.sp)
      Text(stringResource(textId), lineHeight = 24.sp, style = MaterialTheme.typography.body1, color = MaterialTheme.colors.secondary)
    }
  }
}

@Composable
expect fun OnboardingActionButton(user: User?, onboardingStage: SharedPreference<OnboardingStage>, onclick: (() -> Unit)? = null)

@Composable
fun OnboardingActionButton(
  modifier: Modifier = Modifier,
  labelId: StringResource,
  onboarding: OnboardingStage?,
  enabled: Boolean = true,
  icon: Painter? = null,
  iconColor: Color = MaterialTheme.colors.onPrimary,
  onclick: (() -> Unit)?
) {
  Button(
    onClick = {
      onclick?.invoke()
      if (onboarding != null) {
        appPrefs.onboardingStage.set(onboarding)
      }
    },
    modifier = modifier.height(48.dp),
    shape = RoundedCornerShape(6.dp),
    enabled = enabled,
    elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp, hoveredElevation = 0.dp),
    contentPadding = PaddingValues(horizontal = if (icon == null) DEFAULT_PADDING * 2 else DEFAULT_PADDING * 1.5f, vertical = 0.dp),
    colors = ButtonDefaults.buttonColors(
      backgroundColor = MaterialTheme.colors.primary,
      contentColor = MaterialTheme.colors.onPrimary,
      disabledBackgroundColor = MaterialTheme.colors.secondary
    )
  ) {
    if (icon != null) {
      Icon(icon, stringResource(labelId), Modifier.padding(end = DEFAULT_PADDING_HALF), tint = iconColor)
    }
    Text(
      stringResource(labelId),
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 0.5.sp,
      color = MaterialTheme.colors.onPrimary
    )
  }
}

@Composable
fun TextButtonBelowOnboardingButton(text: String, onClick: (() -> Unit)?) {
  val state = getKeyboardState()
  val enabled = onClick != null
  val topPadding by animateDpAsState(if (appPlatform.isAndroid && state.value == KeyboardState.Opened) 0.dp else 7.5.dp)
  val bottomPadding by animateDpAsState(if (appPlatform.isAndroid && state.value == KeyboardState.Opened) 0.dp else 7.5.dp)
  if ((appPlatform.isAndroid && state.value == KeyboardState.Closed) || topPadding > 0.dp) {
    TextButton(
      onClick = { onClick?.invoke() },
      modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
      enabled = enabled,
      shape = RoundedCornerShape(6.dp)
    ) {
      Text(
        text,
        Modifier.padding(horizontal = DEFAULT_PADDING_HALF, vertical = 2.dp),
        color = if (enabled) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        textAlign = TextAlign.Center
      )
    }
  } else {
    // Hide from view when keyboard is open and move the view down
    Spacer(Modifier.height(DEFAULT_PADDING * 2))
  }
}

@Composable
fun OnboardingInformationButton(
  text: String,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .clickable { onClick() }
  ) {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      Icon(
        painterResource(MR.images.ic_info),
        null,
        tint = MaterialTheme.colors.primary
      )
      // https://issuetracker.google.com/issues/206039942#comment32
      var textLayoutResult: TextLayoutResult? by remember { mutableStateOf(null) }
      Text(
        text,
        Modifier
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val newTextLayoutResult = textLayoutResult

            if (newTextLayoutResult == null || newTextLayoutResult.lineCount == 0) {
              // Default behavior if there is no text or the text layout is not measured yet
              layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
              }
            } else {
              val minX = (0 until newTextLayoutResult.lineCount).minOf(newTextLayoutResult::getLineLeft)
              val maxX = (0 until newTextLayoutResult.lineCount).maxOf(newTextLayoutResult::getLineRight)

              layout(ceil(maxX - minX).toInt(), placeable.height) {
                placeable.place(-floor(minX).toInt(), 0)
              }
            }
          },
        onTextLayout = {
          textLayoutResult = it
        },
        style = MaterialTheme.typography.button,
        color = MaterialTheme.colors.primary
      )
    }
  }
}

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "Dark Mode"
)*/
@Composable
fun PreviewSimpleXInfo() {
  SimpleXTheme {
    SimpleXInfoLayout(
      user = null,
      onboardingStage = null
    )
  }
}
