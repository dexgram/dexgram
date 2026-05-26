package chat.simplex.common.views.usersettings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.shredgram.theme.*
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.ui.theme.isInDarkTheme
import chat.simplex.common.views.chatlist.DexgramApi
import chat.simplex.common.views.chatlist.PremiumGate
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Settings screen showing the user's current subscription tier, the full list of
 * Pro features, and either an Upgrade or Sign-out / Downgrade action.
 *
 * - Free user → "Upgrade to Pro" CTA that opens the global upgrade dialog
 *   (handled by [PremiumGate.upgradeRequested]). Closes this view first so the
 *   dialog appears on top of the chat list.
 * - Premium user → "Sign Out" button that calls [DexgramApi.logout], which
 *   clears the Dexgram session and the local premium flags, reverting the app
 *   to the free tier.
 */
@Composable
fun PremiumFeaturesView(close: () -> Unit) {
  BackHandler(onBack = close)

  val dark = isInDarkTheme()
  val brandPrimary = if (dark) ElectricBlue400 else ElectricBlue500
  val cardBg = if (dark) DarkSurfaceElevated1 else ShredgramWhite
  val subtleBg = if (dark) DarkSurfaceElevated2 else DarkCharcoal25
  val onBg = if (dark) OnSurfaceDark else OnSurfaceLight
  val muted = if (dark) DarkCharcoal300 else DarkCharcoal500
  val border = if (dark) DarkBorderElevated2 else BorderElevated1
  val errorRed = Red500

  val heroGradient = Brush.linearGradient(
    colors = listOf(ElectricBlue700, ElectricBlue500, ElectricBlue400)
  )
  val freeHeroGradient = Brush.linearGradient(
    colors = listOf(DarkCharcoal700, DarkCharcoal500)
  )

  // Reactive — observes the SharedPreference state so logout/upgrade flips the
  // hero & button without re-navigation.
  val isPremium = appPrefs.premiumActive.state
  val activatedAt = appPrefs.premiumActivatedAt.state
  val durationDays = appPrefs.premiumDurationDays.state

  val remaining = remember(isPremium.value, activatedAt.value, durationDays.value) {
    if (!isPremium.value || activatedAt.value == 0L) 0
    else {
      val elapsedMs = System.currentTimeMillis() - activatedAt.value
      val elapsedDays = (elapsedMs / (1000L * 60 * 60 * 24)).toInt()
      max(0, durationDays.value - elapsedDays)
    }
  }

  val scope = rememberCoroutineScope()
  var signingOut by remember { mutableStateOf(false) }
  var showSignOutConfirm by remember { mutableStateOf(false) }

  Column(
    Modifier
      .fillMaxSize()
      .background(if (dark) DarkSurfaceBG else SurfaceBG)
      .verticalScroll(rememberScrollState())
  ) {
    // ── Top bar ──
    Row(
      Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = close) {
        Icon(
          painterResource(MR.images.ic_arrow_back_ios_new),
          stringResource(MR.strings.back),
          tint = onBg,
          modifier = Modifier.size(20.dp)
        )
      }
      Text(
        stringResource(MR.strings.premium_settings_title),
        modifier = Modifier.weight(1f),
        fontSize = 18.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Bold,
        color = onBg
      )
      Spacer(Modifier.width(8.dp))
    }

    // ── Hero card with status ──
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(if (isPremium.value) heroGradient else freeHeroGradient)
    ) {
      // Decorative orb
      Box(
        modifier = Modifier
          .size(120.dp)
          .offset(x = 220.dp, y = (-20).dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.08f))
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painterResource(MR.images.ic_shield),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(26.dp)
            )
          }
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(MR.strings.premium_title),
              fontSize = 22.sp,
              fontFamily = Manrope,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
              if (isPremium.value) stringResource(MR.strings.premium_settings_active)
              else stringResource(MR.strings.premium_settings_inactive),
              fontSize = 12.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Medium,
              color = Color.White.copy(alpha = 0.92f)
            )
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(Color.White.copy(alpha = 0.22f))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              if (isPremium.value) stringResource(MR.strings.premium_badge_pro)
              else stringResource(MR.strings.premium_badge_free),
              fontSize = 10.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Black,
              color = Color.White,
              letterSpacing = 1.5.sp
            )
          }
        }

        if (isPremium.value && remaining > 0) {
          Spacer(Modifier.height(16.dp))
          Divider(color = Color.White.copy(alpha = 0.2f))
          Spacer(Modifier.height(12.dp))
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            StatBlock(stringResource(MR.strings.premium_settings_stat_remaining), "$remaining d")
            StatBlock(stringResource(MR.strings.premium_settings_stat_plan), stringResource(MR.strings.premium_badge_pro))
            StatBlock(stringResource(MR.strings.premium_settings_stat_status), stringResource(MR.strings.premium_settings_stat_active))
          }
        }
      }
    }

    Spacer(Modifier.height(22.dp))

    // ── Features list ──
    Text(
      stringResource(MR.strings.premium_settings_features_header),
      modifier = Modifier.padding(horizontal = 22.dp),
      fontSize = 12.sp,
      fontFamily = DMSans,
      fontWeight = FontWeight.SemiBold,
      color = muted,
      letterSpacing = 1.sp
    )
    Spacer(Modifier.height(10.dp))

    val features: List<Triple<ImageResource, StringResource, StringResource>> = listOf(
      Triple(MR.images.ic_bolt,           MR.strings.premium_feature_voice_scramble,        MR.strings.premium_feature_voice_scramble_desc),
      Triple(MR.images.ic_shield,         MR.strings.premium_feature_encrypted_vault,       MR.strings.premium_feature_encrypted_vault_desc),
      Triple(MR.images.ic_verified_user,  MR.strings.premium_feature_address_screening,     MR.strings.premium_feature_address_screening_desc),
      Triple(MR.images.ic_visibility_off, MR.strings.premium_feature_anonymous_profiles,    MR.strings.premium_feature_anonymous_profiles_desc),
      Triple(MR.images.ic_dns,            MR.strings.premium_feature_sale_bots,             MR.strings.premium_feature_sale_bots_desc),
      Triple(MR.images.ic_star,           MR.strings.premium_feature_priority_support,      MR.strings.premium_feature_priority_support_desc),
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(cardBg)
    ) {
      features.forEachIndexed { idx, (iconRes, titleRes, descRes) ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(brandPrimary.copy(alpha = if (dark) 0.20f else 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painterResource(iconRes),
              contentDescription = null,
              tint = brandPrimary,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(titleRes),
              fontSize = 14.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.SemiBold,
              color = onBg
            )
            Spacer(Modifier.height(2.dp))
            Text(
              stringResource(descRes),
              fontSize = 12.sp,
              fontFamily = DMSans,
              color = muted
            )
          }
          if (isPremium.value) {
            Icon(
              painterResource(MR.images.ic_check),
              contentDescription = null,
              tint = LimeGreen600,
              modifier = Modifier.size(18.dp)
            )
          } else {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(brandPrimary.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                stringResource(MR.strings.premium_badge_pro),
                fontSize = 9.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Black,
                color = brandPrimary,
                letterSpacing = 1.sp
              )
            }
          }
        }
        if (idx < features.lastIndex) {
          Divider(
            modifier = Modifier.padding(start = 66.dp),
            color = border,
            thickness = 0.6.dp
          )
        }
      }
    }

    Spacer(Modifier.height(24.dp))

    // ── Primary action ──
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
      if (isPremium.value) {
        OutlinedButton(
          onClick = { showSignOutConfirm = true },
          enabled = !signingOut,
          shape = RoundedCornerShape(27.dp),
          border = BorderStroke(1.5.dp, errorRed.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
          if (signingOut) {
            CircularProgressIndicator(
              color = errorRed,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
              stringResource(MR.strings.premium_settings_signing_out),
              fontSize = 15.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.SemiBold,
              color = errorRed
            )
          } else {
            Icon(
              painterResource(MR.images.ic_logout),
              contentDescription = null,
              tint = errorRed,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              stringResource(MR.strings.premium_settings_sign_out),
              fontSize = 15.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.SemiBold,
              color = errorRed
            )
          }
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(
              Brush.horizontalGradient(listOf(ElectricBlue600, ElectricBlue400))
            )
            .clickable {
              // Close this view first so the upgrade dialog (rendered inside
              // ChatListView) is not hidden behind it.
              close()
              PremiumGate.upgradeRequested.value = true
            },
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painterResource(MR.images.ic_shield),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              stringResource(MR.strings.premium_settings_upgrade_cta),
              fontSize = 15.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              letterSpacing = 0.3.sp
            )
          }
        }
      }
    }

    if (isPremium.value) {
      Spacer(Modifier.height(10.dp))
      Text(
        stringResource(MR.strings.premium_settings_sign_out_hint),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        fontSize = 11.sp,
        fontFamily = DMSans,
        color = muted,
        textAlign = TextAlign.Center
      )
    }

    Spacer(Modifier.height(28.dp))
  }

  // ── Sign-out confirmation ──
  if (showSignOutConfirm) {
    AlertDialog(
      onDismissRequest = { if (!signingOut) showSignOutConfirm = false },
      title = {
        Text(
          stringResource(MR.strings.premium_settings_sign_out_confirm_title),
          fontFamily = DMSans,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = onBg
        )
      },
      text = {
        Text(
          stringResource(MR.strings.premium_settings_sign_out_confirm_body),
          fontFamily = DMSans,
          fontSize = 14.sp,
          color = muted
        )
      },
      confirmButton = {
        TextButton(
          enabled = !signingOut,
          onClick = {
            signingOut = true
            scope.launch {
              // Try to inform the server, then guarantee local state is cleared.
              DexgramApi.logout()
              DexgramApi.clearSession()
              signingOut = false
              showSignOutConfirm = false
            }
          }
        ) {
          Text(
            stringResource(MR.strings.premium_settings_sign_out),
            color = errorRed,
            fontFamily = DMSans,
            fontWeight = FontWeight.Bold
          )
        }
      },
      dismissButton = {
        TextButton(
          enabled = !signingOut,
          onClick = { showSignOutConfirm = false }
        ) {
          Text(
            stringResource(MR.strings.cancel_verb),
            color = muted,
            fontFamily = DMSans,
            fontWeight = FontWeight.Medium
          )
        }
      },
      backgroundColor = cardBg,
      shape = RoundedCornerShape(20.dp)
    )
  }
}

@Composable
private fun StatBlock(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      value,
      fontSize = 15.sp,
      fontFamily = DMSans,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Spacer(Modifier.height(2.dp))
    Text(
      label,
      fontSize = 10.sp,
      fontFamily = DMSans,
      color = Color.White.copy(alpha = 0.7f),
      letterSpacing = 0.5.sp
    )
  }
}
