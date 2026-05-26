package chat.simplex.common.views.onboarding

import SectionBottomSpacer
import SectionDividerSpaced
import SectionItemView
import SectionTextFooter
import SectionView
import TextIconSpaced
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.usersettings.networkAndServers.*
import chat.simplex.common.views.migration.MigrateToDeviceView
import chat.simplex.common.views.migration.MigrationToState
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay

@Composable
fun ModalData.OnboardingConditionsView() {
  // Back to self-destruct setup screen
  BackHandler {
    appPrefs.onboardingStage.set(OnboardingStage.Step2_6_SetupSelfDestruct)
  }
  
  LaunchedEffect(Unit) {
    prepareChatBeforeFinishingOnboarding()
  }
  LaunchedEffect(Unit) {
    if (chatModel.migrationState.value != null && !ModalManager.fullscreen.hasModalsOpen()) {
      ModalManager.fullscreen.showCustomModal(animated = false) { close -> MigrateToDeviceView(close) }
    }
  }
  CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
    ModalView({}, showClose = false, showAppBar = false) {
      val serverOperators = remember { derivedStateOf { chatModel.conditions.value.serverOperators } }
      val selectedOperatorIds = remember { stateGetOrPut("selectedOperatorIds") { serverOperators.value.filter { it.enabled }.map { it.operatorId }.toSet() } }
      val selectedOperators = remember { derivedStateOf { serverOperators.value.filter { selectedOperatorIds.value.contains(it.operatorId) } } }
      var selectedTab by remember { mutableStateOf(0) }

      // Shredgram colors
      val ElectricBlue500 = Color(0xFF1F4CFF)
      val OnSurfaceVariant = Color(0xFF3D4042)  // DarkCharcoal700
      val OutlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
      val SurfaceVariantColor = Color(0xFFF5F5F5)  // Light gray for selected tab
      
      Column(
        Modifier
          .fillMaxSize()
          .background(MaterialTheme.colors.background)
          .statusBarsPadding()  // Shredgram CustomScaffold style
      ) {
        // Main content area with horizontal padding
        Column(
          Modifier
            .weight(1f)
            .padding(horizontal = 24.dp)
        ) {
          // TopBar - Shredgram style: 32dp vertical padding
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Back button - height 24dp like Shredgram
            IconButton(
              onClick = { 
              appPrefs.onboardingStage.set(OnboardingStage.Step2_6_SetupSelfDestruct)
              },
              modifier = Modifier.height(24.dp)
            ) {
              Icon(
                painterResource(MR.images.ic_arrow_back_ios_new),
                contentDescription = "Back",
                tint = MaterialTheme.colors.onBackground
              )
          }

            // Center: Text logo - Shredgram titleMedium (18sp Bold)
            Image(
              painter = painterResource(MR.images.ic_logo),
              contentDescription = "Shredgram"
            )
            
            // Spacer for symmetry - 48dp
            Spacer(Modifier.width(48.dp))
          }

          // Document Icon - Shredgram: 24dp, centered
            Icon(
              painter = painterResource(MR.images.ic_document),
              contentDescription = null,
            modifier = Modifier
              .size(24.dp)
              .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colors.onSurface
            )

          Spacer(Modifier.height(16.dp))

          // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
          Text(
            text = "Terms and conditions",
            fontFamily = Manrope,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = (30 * 1.12f).sp,
            modifier = Modifier.fillMaxWidth()
          )

            Spacer(Modifier.height(8.dp))

          // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
          Text(
            text = "Private chats, groups and your contacts are not accessible to server operators.",
            fontFamily = DMSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = (14 * 1.5f).sp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp)
          )

            Spacer(Modifier.height(32.dp))

          // Tabs - Shredgram LegalTabs style with 12dp spacing
            Row(
              modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            LegalTabPillView(
                  text = "Terms and conditions",
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              modifier = Modifier.weight(1f)
            )

            LegalTabPillView(
              text = "Privacy policy",
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              modifier = Modifier.weight(1f)
                )
              }

          Spacer(Modifier.height(16.dp))

          // Content Card - takes remaining space
          LegalContentCardView(
            selectedTab = selectedTab,
            modifier = Modifier
              .fillMaxWidth()
                  .weight(1f)
          )
        }
        
        // Bottom section - Shredgram bottomContent (with navigation bar padding)
              Column(
                modifier = Modifier
                  .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
          Spacer(Modifier.height(32.dp))
          
          AcceptConditionsButton(enabled = selectedOperatorIds.value.isNotEmpty(), selectedOperators, selectedOperatorIds)
          
          Spacer(Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
fun ModalData.ChooseServerOperators(
  serverOperators: State<List<ServerOperator>>,
  selectedOperatorIds: MutableState<Set<Long>>,
  close: (() -> Unit)
) {
  LaunchedEffect(Unit) {
    prepareChatBeforeFinishingOnboarding()
  }
  CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
    ModalView({}, showClose = false) {
      ColumnWithScrollBar(
        Modifier
          .themedBackground(bgLayerSize = LocalAppBarHandler.current?.backgroundGraphicsLayerSize, bgLayer = LocalAppBarHandler.current?.backgroundGraphicsLayer),
        maxIntrinsicSize = true
      ) {
        Box(Modifier.align(Alignment.CenterHorizontally)) {
          AppBarTitle(stringResource(MR.strings.onboarding_choose_server_operators), bottomPadding = DEFAULT_PADDING)
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING), horizontalAlignment = Alignment.CenterHorizontally) {
          OnboardingInformationButton(
            stringResource(MR.strings.how_it_helps_privacy),
            onClick = { ModalManager.fullscreen.showModal { ChooseServerOperatorsInfoView() } }
          )
        }

        Spacer(Modifier.weight(1f))
        Column((
            if (appPlatform.isDesktop) Modifier.width(600.dp).align(Alignment.CenterHorizontally) else Modifier)
          .fillMaxWidth()
          .padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          serverOperators.value.forEachIndexed { index, srvOperator ->
            OperatorCheckView(srvOperator, selectedOperatorIds)
            if (index != serverOperators.value.lastIndex) {
              Spacer(Modifier.height(DEFAULT_PADDING))
            }
          }
          Spacer(Modifier.height(DEFAULT_PADDING_HALF))

          SectionTextFooter(annotatedStringResource(MR.strings.onboarding_network_operators_simplex_flux_agreement), textAlign = TextAlign.Center)
          SectionTextFooter(annotatedStringResource(MR.strings.onboarding_network_operators_configure_via_settings), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))

        Column(Modifier.widthIn(max = if (appPlatform.isAndroid) 450.dp else 1000.dp).align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
          val enabled = selectedOperatorIds.value.isNotEmpty()
          SetOperatorsButton(enabled, close)
          // Reserve space
          TextButtonBelowOnboardingButton("", null)
        }
      }
    }
  }
}

@Composable
private fun OperatorCheckView(serverOperator: ServerOperator, selectedOperatorIds: MutableState<Set<Long>>) {
  val checked = selectedOperatorIds.value.contains(serverOperator.operatorId)
  TextButton({
    if (checked) {
      selectedOperatorIds.value -= serverOperator.operatorId
    } else {
      selectedOperatorIds.value += serverOperator.operatorId
    }
  },
    border = BorderStroke(1.dp, color = if (checked) MaterialTheme.colors.primary else MaterialTheme.colors.secondary.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(18.dp)
  ) {
    Row(Modifier.padding(DEFAULT_PADDING_HALF), verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(serverOperator.largeLogo), null, Modifier.height(48.dp))
      Spacer(Modifier.width(DEFAULT_PADDING_HALF).weight(1f))
      CircleCheckbox(checked)
    }
  }
}

@Composable
private fun CircleCheckbox(checked: Boolean) {
  if (checked) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painterResource(MR.images.ic_circle_filled),
        null,
        Modifier.size(26.dp),
        tint = MaterialTheme.colors.primary
      )
      Icon(
        painterResource(MR.images.ic_check_filled),
        null,
        Modifier.size(20.dp), tint = MaterialTheme.colors.background
      )
    }
  } else {
    Icon(
      painterResource(MR.images.ic_circle),
      null,
      Modifier.size(26.dp),
      tint = MaterialTheme.colors.secondary.copy(alpha = 0.5f)
    )
  }
}

@Composable
private fun SetOperatorsButton(enabled: Boolean, close: () -> Unit) {
  OnboardingActionButton(
    modifier = if (appPlatform.isAndroid) Modifier.padding(horizontal = DEFAULT_ONBOARDING_HORIZONTAL_PADDING).fillMaxWidth() else Modifier.widthIn(min = 300.dp),
    labelId = MR.strings.ok,
    onboarding = null,
    enabled = enabled,
    onclick = {
      close()
    }
  )
}

@Composable
private fun AcceptConditionsButton(
  enabled: Boolean,
  selectedOperators: State<List<ServerOperator>>,
  selectedOperatorIds: State<Set<Long>>
) {
  val isRegistering = remember { mutableStateOf(false) }
  
  // Shredgram colors
  val ElectricBlue500 = Color(0xFF1F4CFF)
  val BorderElevated1 = Color(0xFFCECFD0)
  val DarkCharcoal400 = Color(0xFF868889)
  
  fun continueOnAccept() {
    // Skip notification setup and go directly to chat
    appPrefs.onboardingStage.set(OnboardingStage.OnboardingComplete)
  }
  
  // Shredgram PrimaryButton - RadiusPill, ElectricBlue500
    Button(
      onClick = {
        if (isRegistering.value) return@Button
        isRegistering.value = true
        
      withBGApi {
          val activeUser = chatModel.controller.apiGetActiveUser(null)
          
          if (activeUser != null) {
            chatModel.currentUser.value = activeUser
            if (chatModel.chatRunning.value != true) {
              chatModel.controller.startChat(activeUser)
              delay(500)
            }
          }
          
        val conditionsId = chatModel.conditions.value.currentConditions.conditionsId
        val acceptForOperators = selectedOperators.value.filter { !it.conditionsAcceptance.conditionsAccepted }
        val operatorIds = acceptForOperators.map { it.operatorId }
        val r = chatController.acceptConditions(chatModel.remoteHostId(), conditionsId = conditionsId, operatorIds = operatorIds)
          
        if (r != null) {
          chatModel.conditions.value = r
          val enabledOperators = enabledOperators(r.serverOperators, selectedOperatorIds.value)
            
          if (enabledOperators != null) {
            val r2 = chatController.setServerOperators(rh = chatModel.remoteHostId(), operators = enabledOperators)
              
            if (r2 != null) {
              chatModel.conditions.value = r2
                createAddressAndUsernameForOnboarding(chatModel) {
                  isRegistering.value = false
              continueOnAccept()
            }
          } else {
                isRegistering.value = false
              }
            } else {
              createAddressAndUsernameForOnboarding(chatModel) {
                isRegistering.value = false
            continueOnAccept()
          }
        }
          } else {
            isRegistering.value = false
          }
        }
      },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(360.dp),  // RadiusPill
      colors = ButtonDefaults.buttonColors(
      backgroundColor = ElectricBlue500,
      contentColor = Color.White,
      disabledBackgroundColor = BorderElevated1,
      disabledContentColor = DarkCharcoal400
      ),
    enabled = enabled && !isRegistering.value,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    elevation = ButtonDefaults.elevation(
      defaultElevation = 0.dp,
      pressedElevation = 0.dp
    )
    ) {
      if (isRegistering.value) {
        Row(
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
          Spacer(Modifier.width(12.dp))
          // Shredgram: labelLarge (DMSans Medium 14sp)
          Text(
            text = "Creating username...",
            fontFamily = DMSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = (14 * 1.5f).sp
          )
        }
      } else {
        // Shredgram: labelLarge (DMSans Medium 14sp)
        Text(
          text = "Finish",
          fontFamily = DMSans,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          lineHeight = (14 * 1.5f).sp
        )
      }
    }
}

private fun continueToNextStep() {
    appPrefs.onboardingStage.set(if (appPlatform.isAndroid) OnboardingStage.Step4_SetNotificationsMode else OnboardingStage.OnboardingComplete)
}

private fun continueToSetNotificationsAfterAccept() {
  appPrefs.onboardingStage.set(OnboardingStage.Step4_SetNotificationsMode)
  ModalManager.fullscreen.showModalCloseable(showClose = false) { SetNotificationsMode(chatModel) }
}

private fun enabledOperators(operators: List<ServerOperator>, selectedOperatorIds: Set<Long>): List<ServerOperator>? {
  val ops = ArrayList(operators)
  if (ops.isNotEmpty()) {
    for (i in ops.indices) {
      val op = ops[i]
      ops[i] = op.copy(enabled = selectedOperatorIds.contains(op.operatorId))
    }
    val haveSMPStorage = ops.any { it.enabled && it.smpRoles.storage }
    val haveSMPProxy = ops.any { it.enabled && it.smpRoles.proxy }
    val haveXFTPStorage = ops.any { it.enabled && it.xftpRoles.storage }
    val haveXFTPProxy = ops.any { it.enabled && it.xftpRoles.proxy }
    val firstEnabledIndex = ops.indexOfFirst { it.enabled }
    if (haveSMPStorage && haveSMPProxy && haveXFTPStorage && haveXFTPProxy) {
      return ops
    } else if (firstEnabledIndex != -1) {
      var op = ops[firstEnabledIndex]
      if (!haveSMPStorage) op = op.copy(smpRoles = op.smpRoles.copy(storage = true))
      if (!haveSMPProxy) op = op.copy(smpRoles = op.smpRoles.copy(proxy = true))
      if (!haveXFTPStorage) op = op.copy(xftpRoles = op.xftpRoles.copy(storage = true))
      if (!haveXFTPProxy) op = op.copy(xftpRoles = op.xftpRoles.copy(proxy = true))
      ops[firstEnabledIndex] = op
      return ops
    } else { // Shouldn't happen - view doesn't let to proceed if no operators are enabled
      return null
    }
  } else {
    return null
  }
}

@Composable
private fun ChooseServerOperatorsInfoView() {
  ColumnWithScrollBar {
    AppBarTitle(stringResource(MR.strings.onboarding_network_operators))

    Column(
      Modifier.padding(horizontal = DEFAULT_PADDING)
    ) {
      ReadableText(stringResource(MR.strings.onboarding_network_operators_app_will_use_different_operators))
      ReadableText(stringResource(MR.strings.onboarding_network_operators_cant_see_who_talks_to_whom))
      ReadableText(stringResource(MR.strings.onboarding_network_operators_app_will_use_for_routing))
    }

    SectionDividerSpaced()

    SectionView(title = stringResource(MR.strings.onboarding_network_about_operators).uppercase()) {
      chatModel.conditions.value.serverOperators.forEach { op ->
        ServerOperatorRow(op)
      }
    }
    SectionBottomSpacer()
  }
}

@Composable
private fun ServerOperatorRow(
  operator: ServerOperator
) {
  SectionItemView(
    {
      ModalManager.fullscreen.showModalCloseable { close ->
        OperatorInfoView(operator)
      }
    }
  ) {
    Image(
      painterResource(operator.logo),
      operator.tradeName,
      modifier = Modifier.size(24.dp)
    )
    TextIconSpaced()
    Text(operator.tradeName)
  }
}

/**
 * Shredgram TermsAndPrivacyText for Terms and Conditions screen
 */
@Composable
private fun ShredgramTermsAndPrivacyTextTC() {
  val ElectricBlue500 = Color(0xFF1F4CFF)
  val OnSurfaceVariant = Color(0xFF3D4042)
  
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Intro text - Shredgram: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
    Text(
      text = "By continuing, you agree to our",
      fontFamily = DMSans,
      fontSize = 12.sp,
      fontWeight = FontWeight.Normal,
      color = OnSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = (12 * 1.5f).sp
    )
    
    // Link text - Shredgram: bodySmall with ElectricBlue500 links
    val annotatedString = buildAnnotatedString {
      pushStringAnnotation(tag = "terms", annotation = "terms")
      withStyle(style = SpanStyle(
        color = ElectricBlue500,
        fontSize = 12.sp
      )) {
        append("Terms of Service")
      }
      pop()
      withStyle(style = SpanStyle(
        color = OnSurfaceVariant,
        fontSize = 12.sp
      )) {
        append(" and ")
      }
      pushStringAnnotation(tag = "privacy", annotation = "privacy")
      withStyle(style = SpanStyle(
        color = ElectricBlue500,
        fontSize = 12.sp
      )) {
        append("Privacy Policy")
      }
      pop()
    }
    
    ClickableText(
      text = annotatedString,
      style = TextStyle(
        fontFamily = DMSans,
        textAlign = TextAlign.Center,
        lineHeight = (12 * 1.5f).sp
      ),
      onClick = { offset ->
        annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset)
          .firstOrNull()?.let { /* TODO: Open Terms */ }
        annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset)
          .firstOrNull()?.let { /* TODO: Open Privacy */ }
      }
    )
  }
}

/**
 * Shredgram LegalTabPill - exact match from local project
 * - Pill shape (RadiusPill / RoundedCornerShape(50))
 * - Shadow 8dp when selected, 0dp when not
 * - Background: surfaceVariant when selected, surface when not
 * - Border: none when selected, 1dp outlineVariant when not
 * - Text: labelLarge (16sp Medium, lineHeight 1.5)
 */
@Composable
private fun LegalTabPillView(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Shredgram colors
  val surfaceVariant = Color(0xFFF5F5F5)  // surfaceVariant for selected
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)  // Border for unselected
  
  val bg = if (selected) surfaceVariant else MaterialTheme.colors.surface
  val elevation = if (selected) 8.dp else 0.dp
  
  Surface(
    modifier = modifier
      .shadow(elevation = elevation, shape = RoundedCornerShape(50), clip = false)
      .clip(RoundedCornerShape(50))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(50),  // RadiusPill
    color = bg,
    border = if (selected) null else BorderStroke(1.dp, outlineVariant)
  ) {
    Box(
      modifier = Modifier.padding(vertical = 8.dp),
      contentAlignment = Alignment.Center
    ) {
      // Shredgram: labelLarge (DMSans Medium 14sp, lineHeight 1.5)
      Text(
        text = text,
        fontFamily = DMSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onSurface,
        lineHeight = (14 * 1.5f).sp,
        maxLines = 1
      )
    }
  }
}

/**
 * Shredgram LegalContentCard - exact match from local project
 * - RadiusLarge (16dp)
 * - Border: 1dp outlineVariant
 * - Padding: 16dp
 * - Title: "Introduction" with titleSmall style (18sp Bold, lineHeight 1.6)
 * - Content: bodySmall (14sp, lineHeight 1.5), onSurfaceVariant color
 * - Fade gradient (24dp) at bottom when scrollable
 */
@Composable
private fun LegalContentCardView(
  selectedTab: Int,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  val surfaceColor = MaterialTheme.colors.surface
  val outlineVariant = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)  // Shredgram outlineVariant
  val onSurfaceVariant = Color(0xFF3D4042)  // DarkCharcoal700
  val fadeHeight = 24.dp
  
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),  // RadiusLarge
    color = MaterialTheme.colors.surface,
    border = BorderStroke(1.dp, outlineVariant)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
      // Title - Shredgram: titleSmall (Manrope Bold 16sp, lineHeight 1.6)
      Text(
        text = "Introduction",
        fontFamily = Manrope,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
        lineHeight = (16 * 1.6f).sp
      )
      
      Spacer(Modifier.height(8.dp))
      
      // Scrollable content with fade
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        val body = if (selectedTab == 0) {
          // Terms content
          "Fognest is the first communication network based on a new protocol stack that builds on the same ideas of complete openness and decentralization as email and web, with the focus on providing security and privacy of communications, and without compromising on usability.\n\n" +
          "Fognest messaging protocol is the first protocol that has no user profile IDs of any kind, not even random numbers, cryptographic keys or hashes that identify the users. Fognest apps allow their users to send messages and files via relay server infrastructure. Relay server owners and operators do not have any access to your messages, thanks to double-ratchet end-to-end encryption algorithm (also known as Signal algorithm - do not confuse with Signal protocols or platform) and additional encryption layers, and they also have no access to your profile and contacts - as they do not host user accounts.\n\n" +
          "Double ratchet algorithm has such important properties as forward secrecy, sender repudiation and break-in recovery (also known as post-compromise security).\n\n" +
          "If you believe that any part of this document is not aligned with SimpleX network mission or values, please raise it via email or chat."
        } else {
          // Privacy content
          "Your privacy is our top priority. Fognest is designed from the ground up to protect your personal information and communications.\n\n" +
          "We do not collect any personal data. Your messages are end-to-end encrypted, meaning only you and the intended recipient can read them.\n\n" +
          "Fognest does not store your messages on our servers. All data is stored locally on your device and transmitted securely.\n\n" +
          "We do not track your location, contacts, or any other sensitive information. Your communications remain private and secure.\n\n" +
          "For more details about our privacy practices, please contact our support team."
        }
        
        // Content text - Shredgram: bodySmall (DMSans Normal 12sp, lineHeight 1.5)
        Text(
          text = body,
          fontFamily = DMSans,
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
          color = onSurfaceVariant,
          lineHeight = (12 * 1.5f).sp,
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(end = 8.dp)
        )
        
        // Fade gradient at bottom - Shredgram style (24dp height)
        val canScroll = scrollState.maxValue > 0
        val showBottomFade = canScroll && scrollState.value < scrollState.maxValue
        
        if (showBottomFade) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(fadeHeight)
              .align(Alignment.BottomCenter)
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    surfaceColor.copy(alpha = 0f),
                    surfaceColor
                  )
                )
              )
          )
        }
      }
    }
  }
}
