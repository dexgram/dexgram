package chat.simplex.common.views.chatlist

import SectionItemView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.LocalUriHandler
import chat.simplex.common.platform.BackHandler
import androidx.compose.ui.text.*
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import chat.simplex.common.AppLock
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatController.stopRemoteHostAndReloadHosts
import chat.simplex.common.ui.theme.*
import chat.simplex.common.ui.shredgram.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.call.Call
import chat.simplex.common.views.chatlist.noteFolderChatAction
import chat.simplex.common.views.chat.item.*
import chat.simplex.common.views.chat.topPaddingToContent
import chat.simplex.common.views.newchat.*
import chat.simplex.common.views.onboarding.*
import chat.simplex.common.views.usersettings.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

enum class PresetTagKind { GROUP_REPORTS, FAVORITES, CONTACTS, GROUPS, BUSINESS, NOTES }

// Chat list filter for the tabs (Chats, Contacts, Groups)
enum class ChatListFilter {
  ALL_CHATS,   // Shows all chats (Direct + Group)
  CONTACTS,    // Shows only contacts (Direct chats)
  GROUPS       // Shows only groups (Group chats)
}

sealed class ActiveFilter {
  data class PresetTag(val tag: PresetTagKind) : ActiveFilter()
  data class UserTag(val tag: ChatTag) : ActiveFilter()
  data object Unread: ActiveFilter()
}

// ═══════════════════════════════════════════════════════════════
//  PREMIUM — backed by Dexgram Subscription API
// ═══════════════════════════════════════════════════════════════

/**
 * Renders the raw 16-digit Pro code grouped in fours ("1234 5678 9012 3456")
 * for readability, while the underlying field value stays as plain digits.
 */
private val ProCodeVisualTransformation = VisualTransformation { text ->
  val digits = text.text
  val grouped = buildString {
    digits.forEachIndexed { i, c ->
      append(c)
      if (i % 4 == 3 && i != digits.lastIndex) append(' ')
    }
  }
  val offsetMapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
      if (offset <= 0) return 0
      return offset + (offset - 1) / 4
    }
    override fun transformedToOriginal(offset: Int): Int {
      return (offset - offset / 5).coerceIn(0, digits.length)
    }
  }
  TransformedText(AnnotatedString(grouped), offsetMapping)
}

private fun premiumRemainingDays(): Int {
  val activatedAt = appPrefs.premiumActivatedAt.get()
  if (activatedAt == 0L) return 0
  val durationDays = appPrefs.premiumDurationDays.get()
  val elapsedMs = System.currentTimeMillis() - activatedAt
  val elapsedDays = (elapsedMs / (1000L * 60 * 60 * 24)).toInt()
  return max(0, durationDays - elapsedDays)
}

private fun activatePremiumFromApi(daysRemaining: Int) {
  appPrefs.premiumActive.set(true)
  appPrefs.premiumActivatedAt.set(System.currentTimeMillis())
  appPrefs.premiumDurationDays.set(daysRemaining)
  appPrefs.premiumDialogShown.set(true)
}

private fun deactivatePremium() {
  appPrefs.premiumActive.set(false)
  appPrefs.premiumActivatedAt.set(0L)
  appPrefs.premiumDurationDays.set(0)
}

// Dexgram brand gradient — works in both light and dark themes
private val DexgramBrandGradient: Brush
  get() = Brush.linearGradient(colors = listOf(ElectricBlue600, ElectricBlue400))

@Composable
private fun PremiumBadge(modifier: Modifier = Modifier) {
  val remaining = remember { mutableStateOf(premiumRemainingDays()) }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .background(DexgramBrandGradient)
      .padding(horizontal = 10.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        painterResource(MR.images.ic_shield),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(13.dp)
      )
      Text(
        stringResource(MR.strings.premium_badge_pro),
        fontSize = 11.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Black,
        color = Color.White,
        letterSpacing = 1.sp
      )
      if (remaining.value > 0) {
        Text(
          "·",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White.copy(alpha = 0.7f)
        )
        Text(
          "${remaining.value}d",
          fontSize = 10.sp,
          fontFamily = DMSans,
          fontWeight = FontWeight.Bold,
          color = Color.White.copy(alpha = 0.85f)
        )
      }
    }
  }
}

@Composable
private fun FreeBadge(modifier: Modifier = Modifier) {
  val dark = isInDarkTheme()
  val bg = if (dark) DarkSurfaceElevated2 else DarkCharcoal25
  val fg = if (dark) DarkCharcoal100 else DarkCharcoal600
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .background(bg)
      .padding(horizontal = 10.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      stringResource(MR.strings.premium_badge_free),
      fontSize = 11.sp,
      fontFamily = DMSans,
      fontWeight = FontWeight.Bold,
      color = fg,
      letterSpacing = 1.sp
    )
  }
}

@Composable
private fun PremiumSearchIcon(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(32.dp)
      .clip(CircleShape)
      .background(DexgramBrandGradient),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      painterResource(MR.images.ic_shield),
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(16.dp)
    )
  }
}

@Composable
private fun PremiumUpgradeDialog(onDismiss: () -> Unit, onActivated: (Int) -> Unit) {
  val dark = isInDarkTheme()

  // Dexgram brand palette — themed surfaces
  val brandPrimary = if (dark) ElectricBlue400 else ElectricBlue500
  val brandDeep = if (dark) ElectricBlue600 else ElectricBlue700
  val cardBg = if (dark) DarkSurfaceElevated1 else ShredgramWhite
  val subtleBg = if (dark) DarkSurfaceElevated2 else DarkCharcoal25
  val onBg = if (dark) OnSurfaceDark else OnSurfaceLight
  val muted = if (dark) DarkCharcoal300 else DarkCharcoal500
  val border = if (dark) DarkBorderElevated2 else BorderElevated1
  val errorColor = Red500

  val heroGradient = Brush.linearGradient(
    colors = listOf(ElectricBlue700, ElectricBlue500, ElectricBlue400)
  )
  val ctaGradient = Brush.horizontalGradient(
    colors = listOf(brandDeep, brandPrimary)
  )

  val scope = rememberCoroutineScope()

  var codeInput by remember { mutableStateOf(DexgramApi.getSavedAccountId()) }
  var isLoading by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf("") }
  var isError by remember { mutableStateOf(false) }

  var googleProducts by remember { mutableStateOf<List<DexgramProduct>>(emptyList()) }
  var googleConnected by remember { mutableStateOf(false) }
  var googleLoading by remember { mutableStateOf(false) }
  var showGooglePlans by remember { mutableStateOf(false) }

  val strBillingUnavailable = stringResource(MR.strings.premium_billing_unavailable)
  val strPurchaseSuccess = stringResource(MR.strings.premium_google_purchase_success)

  DisposableEffect(Unit) {
    if (DexgramBilling.isAvailable) {
      scope.launch {
        googleConnected = DexgramBilling.connect()
      }
    }
    onDispose {
      DexgramBilling.disconnect()
    }
  }

  Dialog(
    onDismissRequest = { if (!isLoading && !googleLoading) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.93f)
        .shadow(24.dp, RoundedCornerShape(28.dp))
        .clip(RoundedCornerShape(28.dp))
        .background(cardBg)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // ── Hero section: brand gradient with decorative glow orbs ──
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(heroGradient),
          contentAlignment = Alignment.Center
        ) {
          // Decorative floating orbs for depth
          Box(
            modifier = Modifier
              .size(140.dp)
              .offset(x = (-90).dp, y = (-50).dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.08f))
          )
          Box(
            modifier = Modifier
              .size(100.dp)
              .offset(x = 110.dp, y = 60.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.06f))
          )
          Box(
            modifier = Modifier
              .size(60.dp)
              .offset(x = 130.dp, y = (-70).dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.1f))
          )

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Glowing shield medallion
            Box(
              modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                painterResource(MR.images.ic_shield),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
              )
            }

            Spacer(Modifier.height(12.dp))

            // PRO chip
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.22f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
              Text(
                stringResource(MR.strings.premium_badge_pro),
                fontSize = 10.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
              )
            }

            Spacer(Modifier.height(8.dp))

            Text(
              stringResource(MR.strings.premium_title),
              fontSize = 28.sp,
              fontFamily = Manrope,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White,
              letterSpacing = 0.3.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
              stringResource(MR.strings.premium_subtitle),
              fontSize = 13.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Medium,
              color = Color.White.copy(alpha = 0.9f)
            )
          }
        }

        Spacer(Modifier.height(22.dp))

        // ── Features list ──
        val features = listOf(
          MR.images.ic_bolt to MR.strings.premium_feature_voice_scramble,
          MR.images.ic_shield to MR.strings.premium_feature_encrypted_vault,
          MR.images.ic_verified_user to MR.strings.premium_feature_address_screening,
          MR.images.ic_visibility_off to MR.strings.premium_feature_anonymous_profiles,
          MR.images.ic_dns to MR.strings.premium_feature_sale_bots,
          MR.images.ic_star to MR.strings.premium_feature_priority_support,
        )

        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          features.forEach { (iconRes, textRes) ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              // Gradient icon pill
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(
                    Brush.linearGradient(
                      colors = listOf(
                        brandPrimary.copy(alpha = if (dark) 0.22f else 0.14f),
                        brandPrimary.copy(alpha = if (dark) 0.10f else 0.06f)
                      )
                    )
                  ),
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
              Text(
                stringResource(textRes),
                fontSize = 14.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Medium,
                color = onBg,
                modifier = Modifier.weight(1f)
              )
              // Check mark at the end
              Icon(
                painterResource(MR.images.ic_check),
                contentDescription = null,
                tint = brandPrimary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        // ── Google Play subscription section ──
        if (DexgramBilling.isAvailable) {
          Spacer(Modifier.height(22.dp))

          if (!showGooglePlans) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF34A853))
                .clickable(enabled = !isLoading && !googleLoading) {
                  if (!googleConnected) {
                    isError = true
                    statusMessage = strBillingUnavailable
                    return@clickable
                  }
                  showGooglePlans = true
                  googleLoading = true
                  scope.launch {
                    googleProducts = DexgramBilling.queryProducts()
                    googleLoading = false
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Text(
                stringResource(MR.strings.premium_subscribe_google),
                fontSize = 16.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          } else {
            Text(
              stringResource(MR.strings.premium_choose_plan),
              fontSize = 15.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.SemiBold,
              color = onBg,
              modifier = Modifier.padding(horizontal = 22.dp).fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            if (googleLoading) {
              CircularProgressIndicator(
                color = brandPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
              )
              Spacer(Modifier.height(4.dp))
              Text(
                stringResource(MR.strings.premium_loading_plans),
                fontSize = 13.sp,
                fontFamily = DMSans,
                color = muted
              )
            } else if (googleProducts.isEmpty()) {
              Text(
                stringResource(MR.strings.premium_billing_unavailable),
                fontSize = 13.sp,
                fontFamily = DMSans,
                color = errorColor,
                modifier = Modifier.padding(horizontal = 22.dp)
              )
            } else {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                googleProducts.forEach { product ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(56.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .border(
                        1.5.dp,
                        brandPrimary.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp)
                      )
                      .background(brandPrimary.copy(alpha = if (dark) 0.08f else 0.04f))
                      .clickable(enabled = !googleLoading && !isLoading) {
                        googleLoading = true
                        statusMessage = ""
                        isError = false
                        scope.launch {
                          val purchaseResult = DexgramBilling.purchase(product.productId)
                          if (purchaseResult.success) {
                            statusMessage = strPurchaseSuccess
                            val apiResult = DexgramApi.googlePurchase(
                              productId = purchaseResult.productId,
                              purchaseToken = purchaseResult.purchaseToken,
                              packageName = "com.dexgram.app"
                            )
                            googleLoading = false
                            when (apiResult) {
                              is ApiResult.Success -> {
                                val days = (apiResult.data.subscription.expiresAt - System.currentTimeMillis() / 1000)
                                  .let { secs -> (secs / 86400).toInt().coerceAtLeast(1) }
                                activatePremiumFromApi(days)
                                onActivated(days)
                              }
                              is ApiResult.Error -> {
                                isError = true
                                statusMessage = apiResult.message
                              }
                            }
                          } else {
                            googleLoading = false
                            if (purchaseResult.errorMessage != "Purchase cancelled") {
                              isError = true
                              statusMessage = purchaseResult.errorMessage
                            }
                          }
                        }
                      }
                      .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      product.durationLabel,
                      fontSize = 15.sp,
                      fontFamily = DMSans,
                      fontWeight = FontWeight.SemiBold,
                      color = onBg
                    )
                    Text(
                      product.price,
                      fontSize = 15.sp,
                      fontFamily = DMSans,
                      fontWeight = FontWeight.Bold,
                      color = brandPrimary
                    )
                  }
                }
              }
            }

            if (googleLoading) {
              Spacer(Modifier.height(8.dp))
              CircularProgressIndicator(
                color = Color(0xFF34A853),
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
              )
            }
          }

          // ── branded "or" divider ──
          Spacer(Modifier.height(18.dp))
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Divider(modifier = Modifier.weight(1f), color = border)
            Text(
              stringResource(MR.strings.premium_or_divider),
              fontSize = 11.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.SemiBold,
              color = muted,
              letterSpacing = 1.5.sp,
              modifier = Modifier.padding(horizontal = 12.dp)
            )
            Divider(modifier = Modifier.weight(1f), color = border)
          }
          Spacer(Modifier.height(18.dp))
        } else {
          Spacer(Modifier.height(24.dp))
        }

        // ── 16-digit code entry ──
        Text(
          stringResource(MR.strings.premium_enter_code),
          fontSize = 14.sp,
          fontFamily = DMSans,
          fontWeight = FontWeight.SemiBold,
          color = onBg,
          modifier = Modifier.padding(horizontal = 22.dp).fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        BasicTextField(
          value = codeInput,
          onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(16)
            codeInput = digits
            isError = false
            statusMessage = ""
          },
          enabled = !isLoading && !googleLoading,
          singleLine = true,
          // Display grouped as "1234 5678 9012 3456" while keeping the raw 16 digits.
          visualTransformation = ProCodeVisualTransformation,
          textStyle = TextStyle(
            fontSize = 20.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.Bold,
            color = onBg,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
          ),
          decorationBox = { innerTextField ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(subtleBg)
                .border(
                  width = 1.5.dp,
                  color = when {
                    isError -> errorColor
                    codeInput.length == 16 -> brandPrimary
                    else -> border
                  },
                  shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
              contentAlignment = Alignment.Center
            ) {
              if (codeInput.isEmpty()) {
                Text(
                  stringResource(MR.strings.premium_code_hint),
                  fontSize = 20.sp,
                  fontFamily = DMSans,
                  fontWeight = FontWeight.Bold,
                  color = muted.copy(alpha = 0.45f),
                  letterSpacing = 2.sp,
                  textAlign = TextAlign.Center
                )
              }
              innerTextField()
            }
          }
        )

        if (statusMessage.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text(
            statusMessage,
            fontSize = 13.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.Medium,
            color = if (isError) errorColor else brandPrimary,
            modifier = Modifier.padding(horizontal = 22.dp),
            textAlign = TextAlign.Center
          )
        }

        Spacer(Modifier.height(20.dp))

        // ── Primary CTA with brand gradient ──
        val canActivate = codeInput.length == 16 && !isLoading && !googleLoading
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (canActivate) ctaGradient else Brush.horizontalGradient(listOf(brandPrimary.copy(alpha = 0.35f), brandPrimary.copy(alpha = 0.35f))))
            .clickable(enabled = canActivate) {
              isLoading = true
              statusMessage = ""
              isError = false
              scope.launch {
                val loginResult = DexgramApi.login(codeInput)
                when (loginResult) {
                  is ApiResult.Error -> {
                    isLoading = false
                    isError = true
                    statusMessage = loginResult.message
                  }
                  is ApiResult.Success -> {
                    val subResult = DexgramApi.checkSubscription()
                    isLoading = false
                    when (subResult) {
                      is ApiResult.Success -> {
                        val days = subResult.data.daysRemaining
                        if (days > 0) {
                          activatePremiumFromApi(days)
                          onActivated(days)
                        } else {
                          isError = true
                          statusMessage = "No active subscription for this code"
                        }
                      }
                      is ApiResult.Error -> {
                        isError = true
                        statusMessage = subResult.message
                      }
                    }
                  }
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
              CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(10.dp))
              Text(stringResource(MR.strings.premium_activating), fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = Color.White)
            } else {
              Text(stringResource(MR.strings.premium_activate), fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.3.sp)
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        Text(
          stringResource(MR.strings.premium_get_code),
          fontSize = 12.sp,
          fontFamily = DMSans,
          fontWeight = FontWeight.Normal,
          color = muted,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 24.dp)
        )

        TextButton(
          onClick = { if (!isLoading && !googleLoading) onDismiss() },
          modifier = Modifier.padding(bottom = 14.dp, top = 2.dp)
        ) {
          Text(
            stringResource(MR.strings.premium_maybe_later),
            fontSize = 13.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            color = muted
          )
        }
      }
    }
  }
}

private const val PROFILE_ANIMATION_DURATION_BASE_MS = 350
private const val PROFILE_ANIMATION_DURATION_PUBLIC_WIDTH_MS =
  (PROFILE_ANIMATION_DURATION_BASE_MS * 0.5).toInt()
private const val PROFILE_ANIMATION_DURATION_PRIVATE_WIDTH_MS =
  (PROFILE_ANIMATION_DURATION_BASE_MS * 0.5).toInt()
private const val PROFILE_ANIMATION_DURATION_LAYOUT_MS = PROFILE_ANIMATION_DURATION_BASE_MS
private const val PROFILE_ANIMATION_DURATION_TEXT_MS =
  (PROFILE_ANIMATION_DURATION_BASE_MS * 0.5).toInt()
private const val PROFILE_ANIMATION_TEXT_DELAY_MS =
  (PROFILE_ANIMATION_DURATION_BASE_MS * 0.2).toInt()

private fun showNewChatSheet(oneHandUI: State<Boolean>) {
  connectProgressManager.cancelConnectProgress()
  ModalManager.start.closeModals()
  ModalManager.end.closeModals()
  chatModel.newChatSheetVisible.value = true
  ModalManager.start.showCustomModal { close ->
    val close = {
      // It will set it faster than in onDispose. It's important to catch the actual state before
      // closing modal for reacting with status bar changes in [App]
      chatModel.newChatSheetVisible.value = false
      close()
    }
    ModalView(close, showAppBar = !oneHandUI.value) {
      if (appPlatform.isAndroid) {
        BackHandler {
          close()
        }
      }
      NewChatSheet(rh = chatModel.currentRemoteHost.value, close)
      DisposableEffect(Unit) {
        onDispose {
          chatModel.newChatSheetVisible.value = false
        }
      }
    }
  }
}

@Composable
fun ToggleChatListCard() {
  ChatListCard(
    close = {
      appPrefs.oneHandUICardShown.set(true)
      AlertManager.shared.showAlertMsg(
        title = generalGetString(MR.strings.one_hand_ui),
        text = generalGetString(MR.strings.one_hand_ui_change_instruction),
      )
    }
  ) {
    Column(
      modifier = Modifier
        .padding(horizontal = DEFAULT_PADDING)
        .padding(top = DEFAULT_PADDING)
    ) {
      Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(stringResource(MR.strings.one_hand_ui_card_title), style = MaterialTheme.typography.h3)
      }
      Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(stringResource(MR.strings.one_hand_ui), Modifier.weight(10f), style = MaterialTheme.typography.body1)

        Spacer(Modifier.fillMaxWidth().weight(1f))

        SharedPreferenceToggle(
          appPrefs.oneHandUI,
          enabled = true
        )
      }
    }
  }
}

@Composable
fun ChatListView(chatModel: ChatModel, userPickerState: MutableStateFlow<AnimatedViewState>, setPerformLA: (Boolean) -> Unit, stopped: Boolean) {
  val oneHandUI = remember { appPrefs.oneHandUI.state }

  LaunchedEffect(Unit) {
    // Disable automatic "What's New" popup on startup/install.
    // Users can still open it manually from Settings.
    val showWhatsNew = false
    val showUpdatedConditions = chatModel.conditions.value.conditionsAction?.shouldShowNotice ?: false
    if (showWhatsNew || showUpdatedConditions) {
      delay(1000L)
      ModalManager.center.showCustomModal { close -> WhatsNewView(close = close, updatedConditions = showUpdatedConditions) }
    }
  }

  if (appPlatform.isDesktop) {
    KeyChangeEffect(chatModel.chatId.value) {
      if (chatModel.chatId.value != null && !ModalManager.end.isLastModalOpen(ModalViewId.SECONDARY_CHAT)) {
        ModalManager.end.closeModalsExceptFirst()
      }
      AudioPlayer.stop()
      VideoPlayerHolder.stopAll()
    }
  }
  val searchText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
  val listState = rememberLazyListState(lazyListState.first, lazyListState.second)
  val selectedTab = remember { mutableStateOf(0) } // 0=Chats, 1=Vault, 2=Wallet, 3=Market, 4=Shutdown
  val showSearchOverlay = remember { mutableStateOf(false) } // Move search overlay state to top level
  val searchActive = remember { mutableStateOf(false) } // Shared search active state
  val selectedListFilter = remember { mutableStateOf(ChatListFilter.ALL_CHATS) } // Chats/Contacts/Groups filter
  val showTagEditor = remember { mutableStateOf(false) } // State for showing tag editor bottom sheet

  // Premium state — backed by Dexgram API
  val isPremium = remember { mutableStateOf(appPrefs.premiumActive.get()) }
  val showPremiumDialog = remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (DexgramApi.isLoggedIn()) {
      val result = DexgramApi.checkSubscription()
      if (result is ApiResult.Success) {
        if (result.data.daysRemaining > 0) {
          activatePremiumFromApi(result.data.daysRemaining)
          isPremium.value = true
        } else {
          deactivatePremium()
          isPremium.value = false
        }
      }
    }

    if (!appPrefs.premiumActive.get()) {
      delay(800L)
      showPremiumDialog.value = true
    }
  }

  // Observe global premium-gate requests from any other screen
  val globalUpgradeRequested = PremiumGate.upgradeRequested.collectAsState()
  LaunchedEffect(globalUpgradeRequested.value) {
    if (globalUpgradeRequested.value && !appPrefs.premiumActive.get()) {
      showPremiumDialog.value = true
    }
  }

  if (showPremiumDialog.value) {
    PremiumUpgradeDialog(
      onDismiss = {
        showPremiumDialog.value = false
        appPrefs.premiumDialogShown.set(true)
        PremiumGate.upgradeRequested.value = false
      },
      onActivated = { days ->
        isPremium.value = true
        showPremiumDialog.value = false
        PremiumGate.upgradeRequested.value = false
      }
    )
  }

  Box(Modifier.fillMaxSize()) {
    if (oneHandUI.value) {
      Column(Modifier.fillMaxSize()) {
        // Custom toolbar for one-hand UI
        ChatListToolbar(
          userPickerState,
          listState,
          stopped,
          setPerformLA,
          showSearchOverlay,
          searchText,
          searchActive,
          selectedListFilter,
          showTagEditor,
          isPremium = isPremium.value
        )
        // Chat list - filtered by search text
        Box(Modifier.weight(1f)) {
          ChatListWithLoadingScreen(searchText, listState, searchActive, selectedListFilter)
        }
      }
      // Bottom navigation
      BottomNavigationBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        selectedTab = selectedTab,
        userPickerState = userPickerState,
        stopped = stopped
      )
    } else {
      Column(Modifier.fillMaxSize()) {
      Column {
        ChatListToolbar(
          userPickerState,
          listState,
          stopped,
          setPerformLA,
          showSearchOverlay,
          searchText,
          searchActive,
          selectedListFilter,
          showTagEditor,
          isPremium = isPremium.value
        )
      }
        Box(Modifier.weight(1f)) {
          ChatListWithLoadingScreen(searchText, listState, searchActive, selectedListFilter)
        }
      }
      // Bottom navigation (also show when oneHandUI is disabled)
      BottomNavigationBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        selectedTab = selectedTab,
        userPickerState = userPickerState,
        stopped = stopped
      )
      if (searchText.value.text.isEmpty() && !chatModel.desktopNoUserNoRemote && chatModel.chatRunning.value == true) {
        NewChatSheetFloatingButton(oneHandUI, stopped)
      }
    }

    // Floating action buttons - above bottom navigation (Shredgram ChatActionButtons style)
    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 24.dp, bottom = 120.dp),  // Increased bottom padding for more space
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Group button - Shredgram style: 64dp, 16dp radius, white with ElectricBlue500 border
      Box(
        modifier = Modifier
          .size(64.dp)
          .shadow(4.dp, RoundedCornerShape(16.dp))
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colors.surface)
          .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(16.dp))
          .clickable {
            ModalManager.start.showCustomModal { close ->
              AddGroupView(chatModel, chatModel.currentRemoteHost.value, close, close)
            }
          },
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painterResource(MR.images.ic_group_new),
          contentDescription = "Create Group",
          tint = MaterialTheme.colors.primary,
          modifier = Modifier.size(24.dp)
        )
      }

    }

    // Search Overlay - DISABLED (using new inline search instead)
    // The new search bar in toolbar handles all search functionality
    if (false && showSearchOverlay.value) {
      SearchOverlayView(
        showSearchOverlay = showSearchOverlay,
        listState = listState
      )
    }

    // Tag List Editor Dialog - shows as bottom sheet overlay on ChatListView
    if (showTagEditor.value) {
      TagListEditorDialog(
        rhId = chatModel.currentRemoteHost.value?.remoteHostId,
        close = { showTagEditor.value = false }
      )
    }
  }

  if (searchText.value.text.isEmpty()) {
    if (appPlatform.isDesktop && !oneHandUI.value) {
      val call = remember { chatModel.activeCall }.value
      if (call != null) {
        ActiveCallInteractiveArea(call)
      }
    }
  }
  if (appPlatform.isAndroid) {
    val wasAllowedToSetupNotifications = rememberSaveable { mutableStateOf(false) }
    val canEnableNotifications = remember { derivedStateOf { chatModel.chatRunning.value == true } }
    if (wasAllowedToSetupNotifications.value || canEnableNotifications.value) {
      SetNotificationsModeAdditions()
      LaunchedEffect(Unit) { wasAllowedToSetupNotifications.value = true }
    }
    tryOrShowError("UserPicker", error = {}) {
      UserPicker(
        chatModel = chatModel,
        userPickerState = userPickerState,
        setPerformLA = AppLock::setPerformLA
      )
    }
  }
}

@Composable
private fun ChatListCard(
  close: () -> Unit,
  onCardClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit
) {
  Column(
    modifier = Modifier.clip(RoundedCornerShape(18.dp))
  ) {
    Box(
      modifier = Modifier
        .background(MaterialTheme.appColors.sentMessage)
        .clickable {
          onCardClick?.invoke()
        }
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().matchParentSize().padding(5.dp),
        contentAlignment = Alignment.TopEnd
      ) {
        IconButton(
          onClick = {
            close()
          }
        ) {
          Icon(
            painterResource(MR.images.ic_close), stringResource(MR.strings.back), tint = MaterialTheme.colors.secondary
          )
        }
      }
      content()
    }
  }
}

@Composable
private fun AddressCreationCard() {
  ChatListCard(
    close = {
      appPrefs.addressCreationCardShown.set(true)
      AlertManager.shared.showAlertMsg(
        title = generalGetString(MR.strings.simplex_address),
        text = generalGetString(MR.strings.address_creation_instruction),
      )
    },
    onCardClick = {
      ModalManager.start.showModal {
        UserAddressLearnMore(showCreateAddressButton = true)
      }
    }
  ) {
      Box(modifier = Modifier.matchParentSize().padding(end = (DEFAULT_PADDING_HALF + 2.dp) * fontSizeSqrtMultiplier, bottom = 2.dp), contentAlignment = Alignment.BottomEnd) {
      TextButton(
        onClick = {
          ModalManager.start.showModalCloseable { close ->
            UserAddressView(chatModel = chatModel, shareViaProfile = false, autoCreateAddress = true, close = close)
          }
        },
      ) {
        Text(stringResource(MR.strings.create_address_button), style = MaterialTheme.typography.body1)
      }
    }
    Row(
      Modifier
        .fillMaxWidth()
        .padding(DEFAULT_PADDING),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.background(MaterialTheme.colors.primary, CircleShape).padding(12.dp)) {
          ProfileImage(size = 37.dp, null, icon = MR.images.ic_mail_filled, color = Color.White, backgroundColor = Color.Red)
        }
      }
      Column(modifier = Modifier.padding(start = DEFAULT_PADDING)) {
        Text(stringResource(MR.strings.your_simplex_contact_address), style = MaterialTheme.typography.h3)
        Spacer(Modifier.fillMaxWidth().padding(DEFAULT_PADDING_HALF))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(MR.strings.how_to_use_simplex_chat), Modifier.padding(end = DEFAULT_SPACE_AFTER_ICON), style = MaterialTheme.typography.body1)
          Icon(
            painterResource(MR.images.ic_info),
            null,
          )
        }
      }
    }
  }
}

@Composable
private fun BoxScope.ChatListWithLoadingScreen(searchText: MutableState<TextFieldValue>, listState: LazyListState, searchActive: MutableState<Boolean> = mutableStateOf(false), selectedListFilter: MutableState<ChatListFilter> = mutableStateOf(ChatListFilter.ALL_CHATS)) {
  // Skip all this if desktop no user/remote
  if (chatModel.desktopNoUserNoRemote) {
    return
  }

  // Check if user has any real chats (Direct or Group), excluding notes/invitations
  val allChatsCount = chatModel.chats.value.size
  val realChats = chatModel.chats.value.filter { chat ->
    chat.chatInfo is ChatInfo.Direct || chat.chatInfo is ChatInfo.Group
  }
  val hasRealChats = realChats.isNotEmpty()

  // Debug logging

  // List all chat types
  chatModel.chats.value.forEachIndexed { index, chat ->
  }

  // Switching users/hosts - don't show anything
  if (chatModel.switchingUsersAndHosts.value) {
    return
  }

  // Loading state - only when chat is initializing
  if (chatModel.chatRunning.value == null && chatModel.chats.value.isEmpty()) {
    Text(
      stringResource(MR.strings.loading_chats),
      Modifier.align(Alignment.Center),
      color = MaterialTheme.colors.secondary
    )
    return
  }

  // No real chats - show beautiful empty state (regardless of chatRunning state)
  if (!hasRealChats) {
    EmptyChatsView(Modifier.align(Alignment.Center), searchActive)
    return
  }

  // Has real chats - show normal chat list
  ChatList(searchText = searchText, listState, selectedListFilter)
}

@Composable
private fun BoxScope.NewChatSheetFloatingButton(oneHandUI: State<Boolean>, stopped: Boolean) {
  FloatingActionButton(
    onClick = {
      // Disabled - does nothing
    },
    Modifier
      .navigationBarsPadding()
      .padding(end = DEFAULT_PADDING, bottom = DEFAULT_PADDING)
      .align(Alignment.BottomEnd)
      .size(AppBarHeight * fontSizeSqrtMultiplier),
    elevation = FloatingActionButtonDefaults.elevation(
      defaultElevation = 0.dp,
      pressedElevation = 0.dp,
      hoveredElevation = 0.dp,
      focusedElevation = 0.dp,
    ),
    backgroundColor = if (!stopped) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
    contentColor = MaterialTheme.colors.onPrimary
  ) {
    Icon(painterResource(MR.images.ic_edit_filled), stringResource(MR.strings.add_contact_or_create_group), Modifier.size(22.dp * fontSizeSqrtMultiplier))
  }
}

// Shredgram BottomNavigation - exact match
@Composable
fun BottomNavigationBar(
  modifier: Modifier = Modifier,
  selectedTab: MutableState<Int>,
  userPickerState: MutableStateFlow<AnimatedViewState>? = null,
  stopped: Boolean = false
) {
  val isPreview = LocalInspectionMode.current

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colors.background)
      .imePadding()  // Move above keyboard when it appears
  ) {
    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), thickness = 1.dp)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .then(if (isPreview) Modifier else Modifier.navigationBarsPadding()),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      BottomNavItem(
        icon = MR.images.ic_chat_new,
        label = "Chats",
        selected = selectedTab.value == 0,
        onClick = { selectedTab.value = 0 }
      )

      BottomNavItem(
        icon = MR.images.ic_vault,
        label = "Vault",
        iconSize = 28.dp,
        selected = selectedTab.value == 1,
        onClick = {
          selectedTab.value = 1
          if (!isPreview) {
            ModalManager.start.showCustomModal { close ->
              chat.simplex.common.views.vault.VaultView {
                selectedTab.value = 0
                close()
              }
            }
          }
        }
      )

      BottomNavItem(
        icon = MR.images.ic_wallet_new,
        label = "Wallet",
        selected = selectedTab.value == 2,
        onClick = {
          selectedTab.value = 2
          if (!isPreview) {
            ModalManager.start.showCustomModal { close ->
              chat.simplex.common.views.wallet.WalletView {
                selectedTab.value = 0
                close()
              }
            }
          }
        }
      )

      BottomNavItem(
        icon = MR.images.ic_market,
        label = "Market",
        selected = selectedTab.value == 3,
        onClick = {
          selectedTab.value = 3
          if (!isPreview) {
            ModalManager.start.showCustomModal { close ->
              chat.simplex.common.views.wallet.MarketView {
                selectedTab.value = 0
                close()
              }
            }
          }
        }
      )

      BottomNavItem(
        icon = MR.images.ic_power_settings_new,
        label = "Shut Down",
        selected = selectedTab.value == 4,
        onClick = {
          selectedTab.value = 4
          if (!isPreview) { shutdownAppAlert() }
        }
      )
    }
  }
}

// Shredgram BottomNavButton - exact match
@Composable
fun BottomNavItem(
  icon: ImageResource,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  iconSize: Dp = 24.dp
) {
  Column(
    modifier = Modifier
      .padding(0.dp)
      .clickable(
        onClick = onClick,
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
      ),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Box: 56dp width, 40dp height, RoundedCornerShape 140dp (pill)
    Box(
      modifier = Modifier
        .width(56.dp)
        .height(40.dp)
        .clip(RoundedCornerShape(140.dp))
        .background(if (selected) MaterialTheme.colors.primary else Color.Transparent),
      contentAlignment = Alignment.Center
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = label,
      modifier = Modifier.size(iconSize),
        tint = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.secondary
    )
    }
    Spacer(Modifier.height(4.dp))
    Text(
      text = label,
      fontSize = 14.sp,
      fontFamily = DMSans,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) MaterialTheme.colors.onBackground else MaterialTheme.colors.secondary,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

// Shredgram-style NavigationTagButton for the tabs row
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavigationTagButton(
  text: String,
  icon: ImageResource,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null
) {
  val shape = RoundedCornerShape(16.dp)

  Row(
    modifier = Modifier
      .height(32.dp)
      .shadow(
        elevation = if (isSelected) 4.dp else 0.dp,
        shape = shape,
        clip = false
      )
      .clip(shape)
      .then(
        if (!isSelected) Modifier.border(1.dp, MaterialTheme.colors.secondary.copy(alpha = 0.5f), shape)
        else Modifier
      )
      .background(if (isSelected) MaterialTheme.colors.surface else Color.Transparent)
      .then(
        if (onLongClick != null) {
          Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
          )
        } else {
          Modifier.clickable(onClick = onClick)
        }
      )
      .padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = text,
      tint = if (isSelected) MaterialTheme.colors.onBackground else MaterialTheme.colors.secondary,
      modifier = Modifier.size(18.dp)
    )
    Spacer(Modifier.width(4.dp))
    Text(
      text = text,
      fontSize = 14.sp,
      fontFamily = DMSans,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (isSelected) MaterialTheme.colors.onBackground else MaterialTheme.colors.secondary
    )
  }
}

@Composable
private fun ConnectButton(text: String, onClick: () -> Unit) {
  Button(
    onClick,
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.buttonColors(
      backgroundColor = MaterialTheme.colors.primary,
      contentColor = MaterialTheme.colors.onPrimary
    ),
    elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    contentPadding = PaddingValues(horizontal = DEFAULT_PADDING, vertical = 0.dp),
    modifier = Modifier.height(48.dp)
  ) {
    Text(
      text,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 0.5.sp,
      color = MaterialTheme.colors.onPrimary
    )
  }
}

private fun shutdownAppAlert() {
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.shutdown_alert_question),
    text = generalGetString(MR.strings.shutdown_alert_desc),
    destructive = true,
    onConfirm = {
      shutdownApp()
    }
  )
}

private fun shutdownApp() {
  try {
    if (appPlatform.isAndroid) {
      platform.androidServiceSafeStop()
    }
    kotlin.system.exitProcess(0)
  } catch (e: Exception) {
    Log.e(TAG, "Failed to shutdown app: ${e.message}")
  }
}

/**
 * Checks if the input is a complete username format
 * Supported formats:
 *   - Private: "smith.789.inco" (baseName.number.inco)
 *   - Public: "smith.123.link" (baseName.number.link)
 * Format: baseName.number.domain (exactly 3 parts with "inco" or "link" domain)
 */
private fun isCompleteUsernameFormat(input: String): Boolean {
  if (input.isEmpty()) return false

  val parts = input.split(".")

  // Must have exactly 3 parts: base, number, domain
  if (parts.size != 3) return false

  // First part should have letters (base name) - at least 1 character
  if (parts[0].isEmpty() || !parts[0].any { it.isLetter() }) return false

  // Second part should be all digits - at least 1 digit
  if (parts[1].isEmpty() || !parts[1].all { it.isDigit() }) return false

  // Third part MUST be either "inco" (private) or "link" (public) - case-insensitive
  val domain = parts[2].lowercase()
  if (domain != "inco" && domain != "link") return false

  return true
}

/**
 * Checks if the username is a public profile username (ends with .link)
 */
private fun isPublicUsername(username: String): Boolean {
  return username.lowercase().endsWith(".link")
}

/**
 * Checks if the username is a private profile username (ends with .inco)
 */
private fun isPrivateUsername(username: String): Boolean {
  return username.lowercase().endsWith(".inco")
}

/**
 * Connection progress states for username-based connections
 */
sealed class UsernameConnectionState {
  object Idle : UsernameConnectionState()
  object SearchingUsername : UsernameConnectionState()
  data class UserFound(val username: String, val oneTimeAddress: String) : UsernameConnectionState()
  object SendingRequest : UsernameConnectionState()
  object WaitingForAcceptance : UsernameConnectionState()
  object Connected : UsernameConnectionState()
  data class Error(val message: String) : UsernameConnectionState()
}

/**
 * Data class to hold found user information
 */
data class FoundUserInfo(
  val username: String,
  val oneTimeAddress: String
)

@Composable
private fun ChatListToolbar(
  userPickerState: MutableStateFlow<AnimatedViewState>,
  listState: LazyListState,
  stopped: Boolean,
  setPerformLA: (Boolean) -> Unit,
  showSearchOverlay: MutableState<Boolean>,
  searchText: MutableState<TextFieldValue>,
  searchActive: MutableState<Boolean>,
  selectedListFilter: MutableState<ChatListFilter>,
  showTagEditor: MutableState<Boolean>,
  isPremium: Boolean = false
) {
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()

  // Menu dropdown state - Shredgram style
  var menuExpanded by remember { mutableStateOf(false) }
  var isPublicProfileSetupCompleted by remember { mutableStateOf(chatModel.isPublicProfileSetupCompleted()) }
  var isPublicProfileCollapsed by remember { mutableStateOf(false) }

  LaunchedEffect(chatModel.currentUser.value) {
    isPublicProfileSetupCompleted = chatModel.isPublicProfileSetupCompleted()
  }

  // New state management for username connection flow
  val usernameConnectionState = remember { mutableStateOf<UsernameConnectionState>(UsernameConnectionState.Idle) }
  val foundUserInfo = remember { mutableStateOf<FoundUserInfo?>(null) }

  // Monitor search text for username lookup
  val view = LocalMultiplatformView()
  LaunchedEffect(Unit) {
    snapshotFlow { searchText.value.text }
      .distinctUntilChanged()
      .collect {
        val trimmedText = it.trim()

        // Check if it's a complete username format:
        // - Private: "smith.789.inco"
        // - Public: "smith.123.link"
        if (isCompleteUsernameFormat(trimmedText)) {
          // Reset found user when searching new username
          if (foundUserInfo.value?.username != trimmedText) {
            foundUserInfo.value = null
            usernameConnectionState.value = UsernameConnectionState.SearchingUsername
            hideKeyboard(view)

            withBGApi {
              val result = UsernameAPI.lookupUsername(trimmedText)

              // Check for address in address, oneTimeAddress, or simpleXAddress field
              val address = result?.data?.address ?: result?.data?.oneTimeAddress ?: result?.data?.simpleXAddress

              if (result != null && result.success && result.data != null && address != null) {
                // Found user - store info and show "Send Request" button
                foundUserInfo.value = FoundUserInfo(
                  username = trimmedText,
                  oneTimeAddress = address
                )
                usernameConnectionState.value = UsernameConnectionState.UserFound(
                  username = trimmedText,
                  oneTimeAddress = address
                )
              } else {
                usernameConnectionState.value = UsernameConnectionState.Error(
                  result?.error ?: "Username not found"
                )
              }
            }
          }
        } else {
          // Not a username format - clear state
          if (trimmedText.isEmpty()) {
            foundUserInfo.value = null
            usernameConnectionState.value = UsernameConnectionState.Idle
          }
        }
      }
  }

  // Function to send connection request
  fun sendConnectionRequest() {
    val userInfo = foundUserInfo.value ?: return

    usernameConnectionState.value = UsernameConnectionState.SendingRequest

    withBGApi {
      try {
        val inProgress = mutableStateOf(true)
        val planResult = chatModel.controller.apiConnectPlan(chatModel.remoteHostId(), userInfo.oneTimeAddress, inProgress = inProgress)

        if (planResult != null) {
          val (connectionLink, connectionPlan) = planResult
          val useIncognito = isPrivateUsername(userInfo.username)

          when (connectionPlan) {
            is ConnectionPlan.ContactAddress -> {
              when (connectionPlan.contactAddressPlan) {
                is ContactAddressPlan.OwnLink -> {
                  usernameConnectionState.value = UsernameConnectionState.Error("This is your own address")
                  return@withBGApi
                }
                is ContactAddressPlan.Known -> {
                  val contact = connectionPlan.contactAddressPlan.contact
                  openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                  withContext(Dispatchers.Main) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(500)
                    searchText.value = TextFieldValue()
                    searchActive.value = false
                    foundUserInfo.value = null
                    usernameConnectionState.value = UsernameConnectionState.Idle
                  }
                  return@withBGApi
                }
                is ContactAddressPlan.ConnectingProhibit -> {
                  val contact = connectionPlan.contactAddressPlan.contact
                  openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                  withContext(Dispatchers.Main) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(500)
                    searchText.value = TextFieldValue()
                    searchActive.value = false
                    foundUserInfo.value = null
                    usernameConnectionState.value = UsernameConnectionState.Idle
                  }
                  return@withBGApi
                }
                is ContactAddressPlan.ContactViaAddress -> {
                  val contact = connectionPlan.contactAddressPlan.contact
                  usernameConnectionState.value = UsernameConnectionState.WaitingForAcceptance

                  val ok = connectContactViaAddress(chatModel, chatModel.remoteHostId(), contact.contactId, useIncognito)
                  if (ok) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(1500)
                    withContext(Dispatchers.Main) {
                      searchText.value = TextFieldValue()
                      searchActive.value = false
                      foundUserInfo.value = null
                      usernameConnectionState.value = UsernameConnectionState.Idle
                    }
                  } else {
                    usernameConnectionState.value = UsernameConnectionState.Error("Failed to connect")
                  }
                  return@withBGApi
                }
                is ContactAddressPlan.Ok -> {
                  // Let it fall through to use connectViaUri below
                }
                else -> {}
              }
            }
            is ConnectionPlan.InvitationLink -> {
              when (connectionPlan.invitationLinkPlan) {
                is InvitationLinkPlan.OwnLink -> {
                  usernameConnectionState.value = UsernameConnectionState.Error("This is your own link")
                  return@withBGApi
                }
                is InvitationLinkPlan.Connecting -> {
                  usernameConnectionState.value = UsernameConnectionState.Error("Already connecting")
                  return@withBGApi
                }
                is InvitationLinkPlan.Known -> {
                  val contact = connectionPlan.invitationLinkPlan.contact
                  openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                  withContext(Dispatchers.Main) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(500)
                    searchText.value = TextFieldValue()
                    searchActive.value = false
                    foundUserInfo.value = null
                    usernameConnectionState.value = UsernameConnectionState.Idle
                  }
                  return@withBGApi
                }
                is InvitationLinkPlan.Ok -> {
                  // Let it fall through to use connectViaUri below
                }
                else -> {}
              }
            }
            else -> {}
          }

          usernameConnectionState.value = UsernameConnectionState.WaitingForAcceptance

          val connected = connectViaUri(
            chatModel = chatModel,
            rhId = chatModel.remoteHostId(),
            connLink = connectionLink,
            incognito = useIncognito,
            connectionPlan = connectionPlan,
            close = {
              // Success - connection request sent
              CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                usernameConnectionState.value = UsernameConnectionState.Connected
                kotlinx.coroutines.delay(2000)
                searchText.value = TextFieldValue()
                searchActive.value = false
                foundUserInfo.value = null
                usernameConnectionState.value = UsernameConnectionState.Idle
              }
            },
            cleanup = null
          )

          if (!connected) {
            usernameConnectionState.value = UsernameConnectionState.Error("Failed to connect to user")
          }
        } else {
          usernameConnectionState.value = UsernameConnectionState.Error("Failed to process connection link")
        }
      } catch (e: Exception) {
        usernameConnectionState.value = UsernameConnectionState.Error("Error: ${e.message}")
      }
    }
  }

  fun sendConnectionRequestWithMode(incognito: Boolean) {
    val userInfo = foundUserInfo.value ?: return

    usernameConnectionState.value = UsernameConnectionState.SendingRequest

    withBGApi {
      try {
        val inProgress = mutableStateOf(true)
        val planResult = chatModel.controller.apiConnectPlan(chatModel.remoteHostId(), userInfo.oneTimeAddress, inProgress = inProgress)

        if (planResult != null) {
          val (connectionLink, connectionPlan) = planResult

          when (connectionPlan) {
            is ConnectionPlan.ContactAddress -> {
              when (connectionPlan.contactAddressPlan) {
                is ContactAddressPlan.OwnLink -> {
                  usernameConnectionState.value = UsernameConnectionState.Error("This is your own address")
                  return@withBGApi
                }
                is ContactAddressPlan.Known -> {
                  val contact = connectionPlan.contactAddressPlan.contact
                  openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                  withContext(Dispatchers.Main) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(500)
                    searchText.value = TextFieldValue()
                    searchActive.value = false
                    foundUserInfo.value = null
                    usernameConnectionState.value = UsernameConnectionState.Idle
                  }
                  return@withBGApi
                }
                is ContactAddressPlan.ConnectingProhibit -> {
                  val contact = connectionPlan.contactAddressPlan.contact
                  openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                  withContext(Dispatchers.Main) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(500)
                    searchText.value = TextFieldValue()
                    searchActive.value = false
                    foundUserInfo.value = null
                    usernameConnectionState.value = UsernameConnectionState.Idle
                  }
                  return@withBGApi
                }
                is ContactAddressPlan.ContactViaAddress -> {
                  val contact = connectionPlan.contactAddressPlan.contact
                  usernameConnectionState.value = UsernameConnectionState.WaitingForAcceptance

                  val ok = connectContactViaAddress(chatModel, chatModel.remoteHostId(), contact.contactId, incognito)
                  if (ok) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(1500)
                    withContext(Dispatchers.Main) {
                      searchText.value = TextFieldValue()
                      searchActive.value = false
                      foundUserInfo.value = null
                      usernameConnectionState.value = UsernameConnectionState.Idle
                    }
                  } else {
                    usernameConnectionState.value = UsernameConnectionState.Error("Failed to connect")
                  }
                  return@withBGApi
                }
                is ContactAddressPlan.Ok -> {
                  // Let it fall through to use connectViaUri below
                }
                else -> {}
              }
            }
            is ConnectionPlan.InvitationLink -> {
              when (connectionPlan.invitationLinkPlan) {
                is InvitationLinkPlan.OwnLink -> {
                  usernameConnectionState.value = UsernameConnectionState.Error("This is your own link")
                  return@withBGApi
                }
                is InvitationLinkPlan.Connecting -> {
                  usernameConnectionState.value = UsernameConnectionState.Error("Already connecting")
                  return@withBGApi
                }
                is InvitationLinkPlan.Known -> {
                  val contact = connectionPlan.invitationLinkPlan.contact
                  openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                  withContext(Dispatchers.Main) {
                    usernameConnectionState.value = UsernameConnectionState.Connected
                    kotlinx.coroutines.delay(500)
                    searchText.value = TextFieldValue()
                    searchActive.value = false
                    foundUserInfo.value = null
                    usernameConnectionState.value = UsernameConnectionState.Idle
                  }
                  return@withBGApi
                }
                is InvitationLinkPlan.Ok -> {
                  // Let it fall through to use connectViaUri below
                }
                else -> {}
              }
            }
            else -> {}
          }

          usernameConnectionState.value = UsernameConnectionState.WaitingForAcceptance

          val connected = connectViaUri(
            chatModel = chatModel,
            rhId = chatModel.remoteHostId(),
            connLink = connectionLink,
            incognito = incognito,
            connectionPlan = connectionPlan,
            close = {
              // Success - connection request sent
              CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                usernameConnectionState.value = UsernameConnectionState.Connected
                kotlinx.coroutines.delay(2000)
                searchText.value = TextFieldValue()
                searchActive.value = false
                foundUserInfo.value = null
                usernameConnectionState.value = UsernameConnectionState.Idle
              }
            },
            cleanup = null
          )

          if (!connected) {
            usernameConnectionState.value = UsernameConnectionState.Error("Failed to connect to user")
          }
        } else {
          usernameConnectionState.value = UsernameConnectionState.Error("Failed to process connection link")
        }
      } catch (e: Exception) {
        usernameConnectionState.value = UsernameConnectionState.Error("Error: ${e.message}")
      }
    }
  }

  fun showPublicProfileSendRequestWarningDialog(onConfirm: () -> Unit) {
    AlertManager.shared.showAlertDialogButtonsColumn(
      title = "⚠️ Public Profile Notice",
      text = null,
      buttons = {
        PublicProfileSendRequestWarningDialog(onConfirm)
      }
    )
  }

  // Normal toolbar layout
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
  ) {
    // Plan badge row — above the search bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isPremium) {
        PremiumBadge()
      } else {
        FreeBadge()
      }
    }

    // Search bar row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 12.dp, bottom = 20.dp),
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isPremium && !searchActive.value) {
        PremiumSearchIcon()
        Spacer(Modifier.width(8.dp))
      } else {
        Spacer(Modifier.width(12.dp))
      }

      // Search bar
      if (!searchActive.value) {
        // Collapsed state - clickable search bar
      Box(
            modifier = Modifier
          .weight(1f)
          .height(46.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(50))
            .background(MaterialTheme.colors.surface)
            .clickable { searchActive.value = true },
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
          ) {
            Icon(
            painterResource(MR.images.ic_search),
            contentDescription = "Search",
              tint = MaterialTheme.colors.secondary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            stringResource(MR.strings.search_or_paste_simplex_link),
              color = MaterialTheme.colors.secondary,
              fontSize = 14.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Normal
            )
          }
        }
      } else {
        // Expanded state - active search TextField with blue stroke (BorderBrand = #1F4CFF)
        val clipboardManager = LocalClipboardManager.current
        Box(
          modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .border(3.dp, MaterialTheme.colors.primary, RoundedCornerShape(50))
            .background(MaterialTheme.colors.surface)
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painterResource(MR.images.ic_search),
              contentDescription = "Search",
              tint = MaterialTheme.colors.secondary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))

            val focusManager = LocalFocusManager.current
            val defaultToolbar = LocalTextToolbar.current
            val belowToolbar = remember(defaultToolbar) {
              object : TextToolbar {
                override val status get() = defaultToolbar.status
                override fun hide() = defaultToolbar.hide()
                override fun showMenu(
                  rect: ComposeRect,
                  onCopyRequested: (() -> Unit)?,
                  onPasteRequested: (() -> Unit)?,
                  onCutRequested: (() -> Unit)?,
                  onSelectAllRequested: (() -> Unit)?
                ) {
                  val shifted = ComposeRect(rect.left, rect.bottom + 80f, rect.right, rect.bottom + 140f)
                  defaultToolbar.showMenu(shifted, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
                }
              }
            }
            CompositionLocalProvider(LocalTextToolbar provides belowToolbar) {
            BasicTextField(
              value = searchText.value,
              onValueChange = { newValue -> searchText.value = newValue },
              modifier = Modifier.weight(1f),
              textStyle = TextStyle(
                color = MaterialTheme.colors.onBackground,
                fontSize = 14.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Normal
              ),
              singleLine = true,
              cursorBrush = SolidColor(MaterialTheme.colors.primary),
              decorationBox = { innerTextField ->
                if (searchText.value.text.isEmpty()) {
                  Text(
                    stringResource(MR.strings.search_or_paste_simplex_link),
                    color = MaterialTheme.colors.secondary,
                    fontSize = 14.sp,
                    fontFamily = DMSans,
                    fontWeight = FontWeight.Normal
          )
        }
                innerTextField()
              }
            )
            }

      Spacer(Modifier.width(4.dp))

            // Paste button
            IconButton(
              onClick = {
                val clip = clipboardManager.getText()
                if (clip != null) {
                  searchText.value = TextFieldValue(clip.text, TextRange(clip.text.length))
                }
              },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                painterResource(MR.images.ic_content_paste),
                contentDescription = "Paste",
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(18.dp)
              )
            }

      Spacer(Modifier.width(4.dp))

            // Close button - 24dp
      IconButton(
        onClick = {
                if (searchText.value.text.isNotEmpty()) {
                  searchText.value = TextFieldValue()
                } else {
                  focusManager.clearFocus()
                  searchActive.value = false
                }
              },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                painterResource(MR.images.ic_close),
                contentDescription = "Close",
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          // Handle back button to close search
          BackHandler(enabled = searchActive.value) {
            searchText.value = TextFieldValue()
            searchActive.value = false
          }
        }
      }

      Spacer(Modifier.width(8.dp))

      // Menu button with dropdown - Shredgram style
      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.size(24.dp)
      ) {
        Icon(
          painterResource(MR.images.ic_more_vert),
          contentDescription = "Menu",
            tint = MaterialTheme.colors.onBackground,
          modifier = Modifier.size(24.dp)
        )
      }

        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
          offset = DpOffset((-12).dp, 4.dp),
          modifier = Modifier
            .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
            .width(IntrinsicSize.Min)
        ) {
          // Profile option
          Row(
            modifier = Modifier
              .clickable {
                menuExpanded = false
                ModalManager.start.showCustomModal { close ->
                  ModalView(close, showAppBar = false) {
                    UserProfileView(chatModel, close)
                  }
                }
              }
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painterResource(MR.images.ic_person),
              contentDescription = "Profile",
              tint = MaterialTheme.colors.onSurface,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              "Profile",
              fontSize = 14.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.onSurface
            )
          }

          // Settings option
          Row(
            modifier = Modifier
              .clickable {
                menuExpanded = false
                ModalManager.start.showCustomModal { close ->
                  SettingsView(chatModel, setPerformLA, close)
                }
              }
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painterResource(MR.images.ic_settings),
              contentDescription = "Settings",
              tint = MaterialTheme.colors.onSurface,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              "Settings",
              fontSize = 14.sp,
              fontFamily = DMSans,
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.onSurface
            )
          }
        }
      }
    }

    // Username connection flow UI
    UsernameConnectionCard(
      connectionState = usernameConnectionState.value,
      foundUserInfo = foundUserInfo.value,
      onSendRequest = { sendConnectionRequest() },
      onSendRequestWithMode = { incognito ->
        if (!incognito && appPreferences.showPublicProfileWarning.get()) {
          // Show warning dialog for public profile connection
          showPublicProfileSendRequestWarningDialog { sendConnectionRequestWithMode(incognito) }
        } else {
          // Proceed directly (incognito or warning disabled)
          sendConnectionRequestWithMode(incognito)
        }
      },
      onCancel = {
        foundUserInfo.value = null
        usernameConnectionState.value = UsernameConnectionState.Idle
        searchText.value = TextFieldValue()
      }
    )

    // Public + private profile cards with collapse/expand behavior
    val publicProfileWidth by animateDpAsState(
      targetValue = if (isPublicProfileCollapsed) 64.dp else 0.dp,
      animationSpec = tween(durationMillis = PROFILE_ANIMATION_DURATION_PUBLIC_WIDTH_MS)
    )
    val privateProfileWidthFraction by animateFloatAsState(
      targetValue = if (!isPublicProfileSetupCompleted) 1f else if (isPublicProfileCollapsed) 0.8f else 1f,
      animationSpec = tween(durationMillis = PROFILE_ANIMATION_DURATION_PRIVATE_WIDTH_MS)
    )
    val privateProfileOffsetX by animateDpAsState(
      targetValue = 0.dp,
      animationSpec = tween(durationMillis = PROFILE_ANIMATION_DURATION_LAYOUT_MS)
    )
    val privateProfileOffsetY by animateDpAsState(
      targetValue = if (!isPublicProfileSetupCompleted) 0.dp else if (isPublicProfileCollapsed) 0.dp else 84.dp,
      animationSpec = tween(durationMillis = PROFILE_ANIMATION_DURATION_LAYOUT_MS)
    )
    val profileBoxHeight by animateDpAsState(
      targetValue = if (!isPublicProfileSetupCompleted) 80.dp else if (isPublicProfileCollapsed) 80.dp else 152.dp,
      animationSpec = tween(durationMillis = PROFILE_ANIMATION_DURATION_LAYOUT_MS)
    )
    val publicTextAlpha by animateFloatAsState(
      targetValue = if (isPublicProfileCollapsed) 0f else 1f,
      animationSpec = tween(
        durationMillis = PROFILE_ANIMATION_DURATION_TEXT_MS,
        delayMillis = if (isPublicProfileCollapsed) 0 else PROFILE_ANIMATION_TEXT_DELAY_MS
      )
    )
    val publicContentWeight by animateFloatAsState(
      targetValue = if (isPublicProfileCollapsed) 0f else 1f,
      animationSpec = tween(
        durationMillis = PROFILE_ANIMATION_DURATION_TEXT_MS,
        delayMillis = if (isPublicProfileCollapsed) 0 else PROFILE_ANIMATION_TEXT_DELAY_MS
      )
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .height(profileBoxHeight)
    ) {
      // Public profile card - only visible after profile setup is completed
      if (isPublicProfileSetupCompleted) {
      Row(
        modifier = Modifier
          .align(Alignment.TopStart)
          .zIndex(2f)
          .then(
            if (isPublicProfileCollapsed) {
              Modifier.width(publicProfileWidth)
            } else {
              Modifier.fillMaxWidth()
            }
          )
          .shadow(2.dp, RoundedCornerShape(16.dp))
          .background(MaterialTheme.colors.surface, RoundedCornerShape(16.dp))
          .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
          .clickable {
            if (isPublicProfileCollapsed) {
              isPublicProfileCollapsed = false
            } else {
              ModalManager.start.showCustomModal { close ->
                ModalView(close, showAppBar = false) {
                  UserProfileView(chatModel, close)
                }
              }
            }
          }
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Profile image - 48dp circle like Shredgram
        val publicUserImage = chatModel.currentUser.value?.profile?.image
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.background),
          contentAlignment = Alignment.Center
        ) {
          if (publicUserImage != null) {
            val imageBitmap = base64ToBitmap(publicUserImage)
            if (imageBitmap != null) {
              Image(
                bitmap = imageBitmap,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape)
              )
            } else {
              Image(
                painter = painterResource(MR.images.ic_avatar_1),
                contentDescription = "Profile",
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
              )
            }
          } else {
            Image(
              painter = painterResource(MR.images.ic_avatar_1),
              contentDescription = "Profile",
              modifier = Modifier.size(48.dp).clip(CircleShape),
              contentScale = ContentScale.Crop
            )
          }
        }

        if (publicContentWeight > 0.01f) {
          Spacer(Modifier.width(8.dp))

          // Public profile info - Shredgram typography
          Column(
            modifier = Modifier
              .weight(publicContentWeight)
              .graphicsLayer(alpha = publicTextAlpha)
          ) {
            // Label - bodySmall
            Text(
              "Public profile",
              fontSize = 12.sp,
              fontFamily = chat.simplex.common.ui.theme.DMSans,
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.secondary,
              maxLines = 1
            )
            val publicDisplayName = chatModel.currentUser.value?.displayName ?: chatModel.currentUser.value?.fullName ?: "User"
            Text(
              publicDisplayName,
              fontSize = 16.sp,
              fontFamily = chat.simplex.common.ui.theme.DMSans,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colors.onBackground,
              maxLines = 1
            )
            // Display name - bodyMedium
            val publicUsernameValue = chatModel.initPublicUsernameIfNeeded()

            LaunchedEffect(Unit) {

              registerPublicUsername(chatModel) { success ->
              }
            }

            Text(
              publicUsernameValue,
              fontSize = 14.sp,
              fontFamily = chat.simplex.common.ui.theme.DMSans,
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.secondary,
              maxLines = 1
            )
          }
        }

        if (publicContentWeight > 0.01f) {
          Spacer(Modifier.width(8.dp))
          val clipboardPublic = LocalClipboardManager.current
          Row(
            modifier = Modifier.graphicsLayer(alpha = publicTextAlpha),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.VisibilityOff,
              contentDescription = "Hide public profile",
              tint = MaterialTheme.colors.secondary,
              modifier = Modifier
                .size(18.dp)
                .clickable { isPublicProfileCollapsed = true }
            )
            Spacer(Modifier.width(8.dp))
            // Copy button - Shredgram style with border
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .clickable {
                  val addressToCopy = chatModel.getPublicUsername() ?: "No address"
                  clipboardPublic.setText(AnnotatedString(addressToCopy))
                  showToast("Address copied!")
                },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                painterResource(MR.images.ic_content_copy),
                contentDescription = "Copy address",
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
      } // end if (isPublicProfileSetupCompleted)

      // Private profile card
      Row(
        modifier = Modifier
          .align(if (isPublicProfileSetupCompleted && isPublicProfileCollapsed) Alignment.TopEnd else Alignment.TopStart)
          .offset(x = privateProfileOffsetX, y = privateProfileOffsetY)
          .zIndex(1f)
          .fillMaxWidth(privateProfileWidthFraction)
          .shadow(2.dp, RoundedCornerShape(16.dp))
          .background(MaterialTheme.colors.surface, RoundedCornerShape(16.dp))
          .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
      // Profile image - 48dp circle
        val users by remember { derivedStateOf { chatModel.users.filter { u -> u.user.activeUser || !u.user.hidden } } }
        val allRead = users
          .filter { u -> !u.user.activeUser && !u.user.hidden }
          .all { u -> u.unreadCount == 0 }
        Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(MR.images.ic_avatar_1),
          contentDescription = "Private Profile Avatar",
          modifier = Modifier.size(48.dp).clip(CircleShape),
          contentScale = ContentScale.Crop
        )

          if (!allRead) {
            unreadBadge()
          }
        }

      Spacer(Modifier.width(8.dp))

      // Private username with Timer - Shredgram typography
      Column(modifier = Modifier.weight(1f)) {
        val privateUsername = chatModel.getPrivateUsername() ?: "Generating..."
        val isExpired = remember { mutableStateOf(chatModel.isUsernameExpired()) }
        val isUsed = remember { mutableStateOf(chatModel.isUsernameUsed()) }
        val timeRemaining = remember { mutableStateOf(chatModel.getUsernameTimeRemaining()) }

        LaunchedEffect(Unit) {
          while (true) {
            isExpired.value = chatModel.isUsernameExpired()
            isUsed.value = chatModel.isUsernameUsed()
            timeRemaining.value = chatModel.getUsernameTimeRemaining()
            kotlinx.coroutines.delay(1000L)
          }
        }

        if (isUsed.value || isExpired.value) {
          val message = if (isUsed.value) "Username used" else "Username expired"
          Text(
            message,
            fontSize = 16.sp,
            fontFamily = chat.simplex.common.ui.theme.DMSans,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.error,
            maxLines = 1
          )
          Text(
            "Click New ID to generate new",
            fontSize = 12.sp,
            fontFamily = chat.simplex.common.ui.theme.DMSans,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colors.secondary,
            maxLines = 1
          )
        } else {
          // Label row: "Private Profile" on left, "Single-use username" badge on right
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              "Private Profile",
              fontSize = 12.sp,
              fontFamily = chat.simplex.common.ui.theme.DMSans,
              fontWeight = FontWeight.Normal,
              color = MaterialTheme.colors.secondary,
              maxLines = 1
            )
            Text(
              "Single-use",
              fontSize = 10.sp,
              fontFamily = chat.simplex.common.ui.theme.DMSans,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colors.primary,
              maxLines = 1,
              modifier = Modifier
                .background(
                  color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                  shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 5.dp, vertical = 2.dp)
            )
          }
          Text(
            privateUsername,
            fontSize = 16.sp,
            fontFamily = chat.simplex.common.ui.theme.DMSans,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1
          )
          // Timer - Shredgram style with red time
          if (timeRemaining.value > 0) {
            val minutes = (timeRemaining.value / 60000L).toInt()
            val seconds = ((timeRemaining.value % 60000L) / 1000L).toInt()
            Row {
              Text(
                "New name in: ",
                fontSize = 14.sp,
                fontFamily = chat.simplex.common.ui.theme.DMSans,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colors.secondary
              )
              Text(
                String.format("%d:%02d", minutes, seconds),
                fontSize = 14.sp,
                fontFamily = chat.simplex.common.ui.theme.DMSans,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.error
              )
            }
          }
        }
      }

      // Copy username button
      val clipboardPrivate = LocalClipboardManager.current
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(16.dp))
          .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
          .clickable {
            val usernameToCopy = chatModel.getPrivateUsername() ?: ""
            if (usernameToCopy.isNotEmpty()) {
              clipboardPrivate.setText(AnnotatedString(usernameToCopy))
              showToast("Username copied!")
            }
          },
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painterResource(MR.images.ic_content_copy),
          contentDescription = "Copy username",
          tint = MaterialTheme.colors.onBackground,
          modifier = Modifier.size(16.dp)
        )
      }

      Spacer(Modifier.width(8.dp))

      // New ID button - Shredgram style with ElectricBlue500
      val isRegeneratingUsername = remember { mutableStateOf(false) }

        Column(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colors.primary)
          .clickable(enabled = !isRegeneratingUsername.value) {
            if (isRegeneratingUsername.value) return@clickable
            isRegeneratingUsername.value = true

            withBGApi {
              try {
                regenerateUsernameAndAddress(chatModel) { success ->
                  isRegeneratingUsername.value = false
                  if (success) {
                    AlertManager.shared.showAlertMsg(
                      title = "Username Updated",
                      text = "Your new username has been created successfully!"
                    )
                  } else {
                    AlertManager.shared.showAlertMsg(
                      title = "Update Failed",
                      text = "Failed to create new username. Please try again."
                    )
                  }
                }
              } catch (e: Exception) {
                isRegeneratingUsername.value = false
                AlertManager.shared.showAlertMsg(
                  title = "Error",
                  text = "Failed to regenerate username"
                )
              }
            }
          }
          .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        if (isRegeneratingUsername.value) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colors.onPrimary,
            strokeWidth = 1.5.dp
          )
        } else {
          Icon(
            painterResource(MR.images.ic_refresh),
            contentDescription = "Refresh ID",
            tint = MaterialTheme.colors.onPrimary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = "New ID",
            fontSize = 8.sp,
            fontFamily = chat.simplex.common.ui.theme.DMSans,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onPrimary
          )
        }
      }
    }
    }

    Spacer(Modifier.height(24.dp))

    // Tabs section: Notes, Chats, Contacts, Groups + User Tags - Shredgram NavigationTags style
        val scope = rememberCoroutineScope()
    val userTags = remember { chatModel.userTags }
    val rhId = chatModel.remoteHostId()

    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 24.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Notes tab
      item {
        val isNotesSelected = false
        NavigationTagButton(
          text = "Notes",
          icon = MR.images.ic_chat_outlined,
          isSelected = isNotesSelected,
          onClick = {
            val localChat = chatModel.chats.value.find { it.chatInfo is ChatInfo.Local }
            if (localChat != null && chatModel.chatId.value != localChat.id) {
              scope.launch {
                val chatInfo = localChat.chatInfo
                if (chatInfo is ChatInfo.Local) {
                  noteFolderChatAction(localChat.remoteHostId, chatInfo.noteFolder)
                }
              }
            }
          }
        )
      }

      // Chats tab
      item {
        val isChatsSelected = selectedListFilter.value == ChatListFilter.ALL_CHATS
        NavigationTagButton(
          text = "Chats",
          icon = MR.images.ic_chat_new,
          isSelected = isChatsSelected,
          onClick = { selectedListFilter.value = ChatListFilter.ALL_CHATS }
        )
      }

      // Contacts tab
      item {
        val isContactsSelected = selectedListFilter.value == ChatListFilter.CONTACTS
        NavigationTagButton(
          text = "Contacts",
          icon = MR.images.ic_person,
          isSelected = isContactsSelected,
          onClick = { selectedListFilter.value = ChatListFilter.CONTACTS }
        )
      }

      // Groups tab
      item {
        val isGroupsSelected = selectedListFilter.value == ChatListFilter.GROUPS
        NavigationTagButton(
          text = "Groups",
          icon = MR.images.ic_group,
          isSelected = isGroupsSelected,
          onClick = { selectedListFilter.value = ChatListFilter.GROUPS }
        )
      }

      // User custom tags
      items(userTags.value) { tag ->
        val activeFilter = remember { chatModel.activeChatTagFilter }
        val isTagSelected = when (val af = activeFilter.value) {
          is ActiveFilter.UserTag -> af.tag == tag
          else -> false
        }
        var showTagMenu by remember { mutableStateOf(false) }
        val tagSaving = remember { mutableStateOf(false) }

        Box {
          NavigationTagButton(
            text = tag.chatTagText,
            icon = MR.images.ic_label,
            isSelected = isTagSelected,
            onClick = {
              if (chatModel.activeChatTagFilter.value == ActiveFilter.UserTag(tag)) {
                chatModel.activeChatTagFilter.value = null
                selectedListFilter.value = ChatListFilter.ALL_CHATS
              } else {
                chatModel.activeChatTagFilter.value = ActiveFilter.UserTag(tag)
              }
            },
            onLongClick = { showTagMenu = true }
          )

          // Tag context menu
          DropdownMenu(
            expanded = showTagMenu,
            onDismissRequest = { showTagMenu = false },
            modifier = Modifier.background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
          ) {
            // Edit tag option — opens editor pre-filled with existing tag data
            Row(
              modifier = Modifier
                .clickable {
                  showTagMenu = false
                  ModalManager.start.showModalCloseable { close ->
                    TagListEditor(
                      rhId = rhId,
                      tagId = tag.chatTagId,
                      emoji = tag.chatTagEmoji,
                      name = tag.chatTagText,
                      close = close
                    )
                  }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                painterResource(MR.images.ic_edit),
                contentDescription = "Edit",
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                "Edit tag",
                fontSize = 14.sp,
                fontFamily = DMSans,
                color = MaterialTheme.colors.onSurface
              )
            }

            // Remove tag option — shows confirmation dialog then updates model
            Row(
              modifier = Modifier
                .clickable {
                  showTagMenu = false
                  deleteTagDialog(rhId, tag, tagSaving)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                painterResource(MR.images.ic_delete),
                contentDescription = "Remove",
                tint = MaterialTheme.colors.error,
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                "Remove tag",
                fontSize = 14.sp,
                fontFamily = DMSans,
                color = MaterialTheme.colors.error
              )
            }
          }
        }
      }

      // + Add button - opens tag list editor as bottom sheet overlay
      item {
        Row(
            modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colors.secondary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable {
              showTagEditor.value = true
            }
            .padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            "+ Add",
            fontSize = 14.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colors.secondary
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))
  }  // Close Column (normal toolbar)
}

/**
 * UI Component for username connection flow
 * Shows: Found user card with Send Request button, and connection progress
 */
@Composable
private fun UsernameConnectionCard(
  connectionState: UsernameConnectionState,
  foundUserInfo: FoundUserInfo?,
  onSendRequest: () -> Unit,
  onSendRequestWithMode: (incognito: Boolean) -> Unit,
  onCancel: () -> Unit
) {
  when (connectionState) {
    is UsernameConnectionState.Idle -> {
      // Nothing to show
    }

    is UsernameConnectionState.SearchingUsername -> {
      // Searching state
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 2.dp
      ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
            .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colors.primary
          )
          Spacer(Modifier.width(16.dp))
          Text(
            "Searching for user...",
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface
          )
        }
      }
    }

    is UsernameConnectionState.UserFound -> {
      // Found user - show card with Send Request button
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 4.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          // User info row
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Avatar placeholder
            Box(
              modifier = Modifier
                .size(48.dp)
          .clip(CircleShape)
                .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
              contentAlignment = Alignment.Center
      ) {
        Icon(
                painterResource(MR.images.ic_person),
                contentDescription = "User",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(28.dp)
              )
            }

            Spacer(Modifier.width(12.dp))

            // Username
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "User Found",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary
              )
              Text(
                connectionState.username,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface
              )
            }

            // Close button
            IconButton(
              onClick = onCancel,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                painterResource(MR.images.ic_close),
                contentDescription = "Cancel",
                tint = MaterialTheme.colors.secondary,
          modifier = Modifier.size(20.dp)
        )
      }
    }

          Spacer(Modifier.height(16.dp))

          // For .link usernames, show two buttons: Send Incognito and Send Public
          // For .inco usernames, show only Send Request button
          val isLinkUsername = !isPrivateUsername(connectionState.username)

          if (isLinkUsername) {
            // Two buttons for .link usernames
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              // Send Incognito button
      Button(
                onClick = { onSendRequestWithMode(true) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
                  backgroundColor = MaterialTheme.colors.secondary,
                  contentColor = MaterialTheme.colors.onSecondary
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp)
      ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    painterResource(MR.images.ic_theater_comedy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(Modifier.height(4.dp))
        Text(
                    "Incognito",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
        )
                }
      }

              // Send Public button
      Button(
                onClick = { onSendRequestWithMode(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
                  backgroundColor = MaterialTheme.colors.primary,
                  contentColor = MaterialTheme.colors.onPrimary
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp)
      ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    painterResource(MR.images.ic_person),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(Modifier.height(4.dp))
        Text(
                    "Public",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                  )
                }
              }
            }
          } else {
            // Single Send Request button for .inco usernames
            Button(
              onClick = onSendRequest,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
              ),
              elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp)
            ) {
              Icon(
                painterResource(MR.images.ic_arrow_forward_ios),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                "Send Request",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
              )
            }
          }
        }
      }
    }

    is UsernameConnectionState.SendingRequest -> {
      // Sending request - show progress
      ConnectionProgressCard(
        currentStep = 1,
        totalSteps = 3,
        stepTitle = "Sending Request",
        stepDescription = "Preparing connection...",
        onCancel = onCancel
        )
      }

    is UsernameConnectionState.WaitingForAcceptance -> {
      // Waiting for other user to accept
      ConnectionProgressCard(
        currentStep = 2,
        totalSteps = 3,
        stepTitle = "Request Sent",
        stepDescription = "Waiting for user to accept...",
        onCancel = onCancel
      )
    }

    is UsernameConnectionState.Connected -> {
      // Request sent successfully
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF4CAF50).copy(alpha = if (MaterialTheme.colors.isLight) 0.1f else 0.2f),
        elevation = 2.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painterResource(MR.images.ic_check_filled),
            contentDescription = "Request Sent",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(28.dp)
          )
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              "Request Sent!",
              style = MaterialTheme.typography.h6,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF4CAF50)
            )
            Text(
              "Chat will appear when connected",
              style = MaterialTheme.typography.body2,
              color = MaterialTheme.colors.secondary
            )
          }
        }
      }
    }

    is UsernameConnectionState.Error -> {
      // Error state
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
        elevation = 2.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painterResource(MR.images.ic_error),
            contentDescription = "Error",
            tint = MaterialTheme.colors.error,
            modifier = Modifier.size(24.dp)
          )
          Spacer(Modifier.width(12.dp))
          Text(
            connectionState.message,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.error,
            modifier = Modifier.weight(1f)
          )
      IconButton(
            onClick = onCancel,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              painterResource(MR.images.ic_close),
              contentDescription = "Close",
              tint = MaterialTheme.colors.error,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Connection progress card showing step-by-step progress
 */
@Composable
private fun ConnectionProgressCard(
  currentStep: Int,
  totalSteps: Int,
  stepTitle: String,
  stepDescription: String,
  onCancel: () -> Unit
) {
  Card(
        modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 8.dp),
    shape = RoundedCornerShape(16.dp),
    backgroundColor = MaterialTheme.colors.surface,
    elevation = 4.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      // Header with cancel button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "Step $currentStep of $totalSteps",
          style = MaterialTheme.typography.caption,
          color = MaterialTheme.colors.secondary
        )
        IconButton(
          onClick = onCancel,
          modifier = Modifier.size(32.dp)
      ) {
        Icon(
            painterResource(MR.images.ic_close),
            contentDescription = "Cancel",
            tint = MaterialTheme.colors.secondary,
          modifier = Modifier.size(20.dp)
        )
      }
    }

      Spacer(Modifier.height(8.dp))

      // Progress bar
      LinearProgressIndicator(
        progress = currentStep.toFloat() / totalSteps.toFloat(),
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
        color = MaterialTheme.colors.primary
      )

      Spacer(Modifier.height(16.dp))

      // Step info
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colors.primary
        )
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            stepTitle,
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface
          )
          Text(
            stepDescription,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.secondary
          )
        }
      }
    }
  }
}

@Composable
private fun SearchOverlayView(
  showSearchOverlay: MutableState<Boolean>,
  listState: LazyListState
) {
  val searchText = remember { mutableStateOf(TextFieldValue("")) }
  val searchShowingSimplexLink = remember { mutableStateOf(false) }
  val searchChatFilteredBySimplexLink = remember { mutableStateOf<String?>(null) }
  val isSearchingUsername = remember { mutableStateOf(false) }
  val usernameSearchError = remember { mutableStateOf<String?>(null) }

  Box(
            modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
      .zIndex(1000f) // Ensure it's on top of everything
  ) {
    Column(Modifier.fillMaxSize()) {
      // Search bar
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .padding(horizontal = 8.dp)
      ) {
        IconButton(onClick = {
          showSearchOverlay.value = false
          searchText.value = TextFieldValue()
        }) {
            Icon(
            painterResource(MR.images.ic_arrow_back_ios_new),
            contentDescription = "Back",
            tint = MaterialTheme.colors.primary
          )
        }

        val focusRequester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }

        SearchTextField(
          Modifier
            .weight(1f)
            .onFocusChanged { focused = it.hasFocus }
            .focusRequester(focusRequester),
          placeholder = stringResource(MR.strings.search_or_paste_simplex_link),
          alwaysVisible = true,
          searchText = searchText,
          enabled = !searchShowingSimplexLink.value,
          trailingContent = null,
        ) {
          searchText.value = searchText.value.copy(it)
        }

        val hasText = remember { derivedStateOf { searchText.value.text.isNotEmpty() } }
        if (hasText.value) {
          IconButton(onClick = { searchText.value = TextFieldValue() }) {
            Icon(
              painterResource(MR.images.ic_close),
              contentDescription = "Clear",
              tint = MaterialTheme.colors.secondary
            )
          }
          val hideSearchOnBack: () -> Unit = {
            searchText.value = TextFieldValue()
            showSearchOverlay.value = false
          }
          BackHandler(onBack = hideSearchOnBack)
          KeyChangeEffect(chatModel.currentRemoteHost.value) {
            hideSearchOnBack()
          }
        } else {
          if (chatModel.chats.value.isNotEmpty()) {
            ToggleFilterEnabledButton()
          }
        }

        val focusManager = LocalFocusManager.current
        val keyboardState = getKeyboardState()
        LaunchedEffect(keyboardState.value) {
          if (keyboardState.value == KeyboardState.Closed && focused) {
            focusManager.clearFocus()
          }
        }

        val view = LocalMultiplatformView()
        LaunchedEffect(Unit) {
          focusRequester.requestFocus()
          snapshotFlow { searchText.value.text }
            .distinctUntilChanged()
            .collect {
              val trimmedText = it.trim()

              // Check if it's a complete username format:
              // - Private: "smith.789.inco"
              // - Public: "smith.123.link"
              if (isCompleteUsernameFormat(trimmedText)) {
                // Username search (works for both private and public usernames)
                isSearchingUsername.value = true
                usernameSearchError.value = null
                hideKeyboard(view)

                withBGApi {
                  val result = UsernameAPI.lookupUsername(trimmedText)
                  isSearchingUsername.value = false

                  // Check for address in address, oneTimeAddress, or simpleXAddress field
                  val address = result?.data?.address ?: result?.data?.oneTimeAddress ?: result?.data?.simpleXAddress

                  if (result != null && result.success && result.data != null && address != null) {
                    // Found user - get connection plan and connect

                    val inProgress = mutableStateOf(true)
                    val planResult = chatModel.controller.apiConnectPlan(chatModel.remoteHostId(), address, inProgress = inProgress)

                    if (planResult != null) {
                      val (connectionLink, connectionPlan) = planResult

                      // Handle special cases
                      var shouldConnect = true
                      val useIncognito = isPrivateUsername(trimmedText)

                      when (connectionPlan) {
                        is ConnectionPlan.ContactAddress -> {
                          when (connectionPlan.contactAddressPlan) {
                            is ContactAddressPlan.OwnLink -> {
                              usernameSearchError.value = "This is your own address"
                              shouldConnect = false
                            }
                            is ContactAddressPlan.Known -> {
                              val contact = connectionPlan.contactAddressPlan.contact
                              openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                              withContext(Dispatchers.Main) {
                                searchText.value = TextFieldValue()
                                showSearchOverlay.value = false
                              }
                              shouldConnect = false
                            }
                            is ContactAddressPlan.ConnectingProhibit -> {
                              val contact = connectionPlan.contactAddressPlan.contact
                              openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                              withContext(Dispatchers.Main) {
                                searchText.value = TextFieldValue()
                                showSearchOverlay.value = false
                              }
                              shouldConnect = false
                            }
                            is ContactAddressPlan.ContactViaAddress -> {
                              // Contact exists as a contact card - use special API
                              val contact = connectionPlan.contactAddressPlan.contact
                              val ok = connectContactViaAddress(chatModel, chatModel.remoteHostId(), contact.contactId, useIncognito)
                              if (ok) {
                                withContext(Dispatchers.Main) {
                                  searchText.value = TextFieldValue()
                                  showSearchOverlay.value = false
                                }
                              } else {
                                usernameSearchError.value = "Failed to connect"
                              }
                              shouldConnect = false
                            }
                            is ContactAddressPlan.Ok -> {
                              // Check if short link data is present
                              val shortLinkData = connectionPlan.contactAddressPlan.contactSLinkData_
                              if (shortLinkData != null) {
                                val chat = chatModel.controller.apiPrepareContact(chatModel.remoteHostId(), connectionLink, shortLinkData)
                                if (chat != null) {
                                  withContext(Dispatchers.Main) {
                                    chatModel.chatsContext.addChat(chat)
                                    openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), chat.chatInfo)
                                    searchText.value = TextFieldValue()
                                    showSearchOverlay.value = false
                                  }
                                } else {
                                  usernameSearchError.value = "Failed to prepare contact"
                                }
                                shouldConnect = false
                              }
                              // else: no short link data, proceed with connectViaUri
                            }
                            else -> { /* proceed with connectViaUri */ }
                          }
                        }
                        is ConnectionPlan.InvitationLink -> {
                          when (connectionPlan.invitationLinkPlan) {
                            is InvitationLinkPlan.OwnLink -> {
                              usernameSearchError.value = "This is your own link"
                              shouldConnect = false
                            }
                            is InvitationLinkPlan.Connecting -> {
                              usernameSearchError.value = "Already connecting"
                              shouldConnect = false
                            }
                            is InvitationLinkPlan.Known -> {
                              val contact = connectionPlan.invitationLinkPlan.contact
                              openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), ChatInfo.Direct(contact))
                              withContext(Dispatchers.Main) {
                                searchText.value = TextFieldValue()
                                showSearchOverlay.value = false
                              }
                              shouldConnect = false
                            }
                            is InvitationLinkPlan.Ok -> {
                              // Check if short link data is present
                              val shortLinkData = connectionPlan.invitationLinkPlan.contactSLinkData_
                              if (shortLinkData != null) {
                                val chat = chatModel.controller.apiPrepareContact(chatModel.remoteHostId(), connectionLink, shortLinkData)
                                if (chat != null) {
                                  withContext(Dispatchers.Main) {
                                    chatModel.chatsContext.addChat(chat)
                                    openChat(secondaryChatsCtx = null, chatModel.remoteHostId(), chat.chatInfo)
                                    searchText.value = TextFieldValue()
                                    showSearchOverlay.value = false
                                  }
                                } else {
                                  usernameSearchError.value = "Failed to prepare contact"
                                }
                                shouldConnect = false
                              }
                              // else: no short link data, proceed with connectViaUri
                            }
                            else -> { /* proceed */ }
                          }
                        }
                        else -> { /* proceed */ }
                      }

                      if (shouldConnect) {

                        val connected = connectViaUri(
                          chatModel = chatModel,
                          rhId = chatModel.remoteHostId(),
                          connLink = connectionLink,
                          incognito = useIncognito,
                          connectionPlan = connectionPlan,
                          close = {
                            searchText.value = TextFieldValue()
                            showSearchOverlay.value = false
                          },
                          cleanup = null
                        )

                        if (!connected) {
                          usernameSearchError.value = "Failed to connect to user"
                        }
                      }
                    } else {
                      usernameSearchError.value = "Failed to process connection link"
                    }
                  } else {
                    usernameSearchError.value = result?.error ?: "Username not found"
                  }
                }
              } else {
                // Not a username, check for SimpleX link
                val link = strHasSingleSimplexLink(trimmedText)
              if (link != null) {
                hideKeyboard(view)
                if (link.format is Format.SimplexLink) {
                  val linkText = link.format.simplexLinkText
                  searchText.value = searchText.value.copy(linkText, selection = TextRange.Zero)
                }
                searchShowingSimplexLink.value = true
                searchChatFilteredBySimplexLink.value = null
                connect(link.text, searchChatFilteredBySimplexLink) { searchText.value = TextFieldValue() }
                } else if (!searchShowingSimplexLink.value || trimmedText.isEmpty()) {
                  if (trimmedText.isEmpty()) {
                  if (!chatModel.appOpenUrlConnecting.value) {
                    connectProgressManager.cancelConnectProgress()
                  }
                  if (listState.layoutInfo.totalItemsCount > 0) {
                    listState.scrollToItem(0)
                  }
                }
                searchShowingSimplexLink.value = false
                searchChatFilteredBySimplexLink.value = null
                  usernameSearchError.value = null
                }
              }
            }
        }
      }

      Divider()

      // Show username search status
      if (isSearchingUsername.value) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Searching for user...", style = MaterialTheme.typography.body1)
          }
        }
      } else if (usernameSearchError.value != null) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            usernameSearchError.value!!,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.error
          )
        }
      }

      // Search results
      val allChats = remember { chatModel.chats }
      val activeFilter = remember { chatModel.activeChatTagFilter }
      val chats = filteredChats(searchShowingSimplexLink, searchChatFilteredBySimplexLink, searchText.value.text, allChats.value.toList(), activeFilter.value)

      if (chats.isEmpty() && chatModel.chats.value.isNotEmpty()) {
        Box(Modifier.fillMaxSize().padding(horizontal = DEFAULT_PADDING), contentAlignment = Alignment.Center) {
          Text(
            if (searchText.value.text.isBlank()) {
              "No chats"
            } else {
              "No chats found"
            },
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
          )
        }
      } else if (chats.isNotEmpty()) {
        LazyColumn(Modifier.fillMaxSize()) {
          itemsIndexed(chats, key = { _, chat -> chat.remoteHostId to chat.id }) { index, chat ->
            val nextChatSelected = remember(chat.id, chats) {
              derivedStateOf {
                chatModel.chatId.value != null && chats.getOrNull(index + 1)?.id == chatModel.chatId.value
              }
            }
            ChatListNavLinkView(chat, nextChatSelected)
          }
        }
      }
    }
  }
}

@Composable
fun SubscriptionStatusIndicator(click: (() -> Unit)) {
  var subs by remember { mutableStateOf(SMPServerSubs.newSMPServerSubs) }
  var hasSess by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  suspend fun setSubsTotal() {
    if (chatModel.currentUser.value != null && chatModel.controller.hasChatCtrl() && chatModel.chatRunning.value == true) {
      val r = chatModel.controller.getAgentSubsTotal(chatModel.remoteHostId())
      if (r != null) {
        subs = r.first
        hasSess = r.second
      }
    }
  }

  LaunchedEffect(Unit) {
    setSubsTotal()
    scope.launch {
      while (isActive) {
        delay(1.seconds)
        if ((appPlatform.isDesktop || chatModel.chatId.value == null) && !ModalManager.start.hasModalsOpen() && !ModalManager.fullscreen.hasModalsOpen() && isAppVisibleAndFocused()) {
          setSubsTotal()
        }
      }
    }
  }

  SimpleButtonFrame(
    click = click,
    disabled = chatModel.chatRunning.value != true
  ) {
    SubscriptionStatusIndicatorView(subs = subs, hasSess = hasSess)
  }
}

@Composable
fun UserProfileButton(image: String?, allRead: Boolean, onButtonClicked: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onButtonClicked) {
      Box {
        ProfileImage(
          image = image,
          size = 37.dp * fontSizeSqrtMultiplier,
          color = MaterialTheme.colors.secondaryVariant.mixWith(MaterialTheme.colors.onBackground, 0.97f)
        )
        if (!allRead) {
          unreadBadge()
        }
      }
    }
    if (appPlatform.isDesktop) {
      val h by remember { chatModel.currentRemoteHost }
      if (h != null) {
        Spacer(Modifier.width(12.dp))
        HostDisconnectButton {
          stopRemoteHostAndReloadHosts(h!!, true)
        }
      }
    }
  }
}


@Composable
private fun BoxScope.unreadBadge(text: String? = "") {
  Text(
    text ?: "",
    color = MaterialTheme.colors.onPrimary,
    fontSize = 6.sp,
    modifier = Modifier
      .background(MaterialTheme.colors.primary, shape = CircleShape)
      .badgeLayout()
      .padding(horizontal = 3.dp)
      .padding(vertical = 1.dp)
      .align(Alignment.TopEnd)
  )
}

@Composable
private fun ToggleFilterEnabledButton() {
  val showUnread = remember { chatModel.activeChatTagFilter }.value == ActiveFilter.Unread

  IconButton(onClick = {
    if (showUnread) {
      chatModel.activeChatTagFilter.value = null
    } else {
      chatModel.activeChatTagFilter.value = ActiveFilter.Unread
    }
  }) {
    val sp16 = with(LocalDensity.current) { 16.sp.toDp() }
    Icon(
      painterResource(MR.images.ic_filter_list),
      null,
      tint = if (showUnread) MaterialTheme.colors.background else MaterialTheme.colors.secondary,
      modifier = Modifier
        .padding(3.dp)
        .background(color = if (showUnread) MaterialTheme.colors.primary else Color.Unspecified, shape = RoundedCornerShape(50))
        .border(width = 1.dp, color = if (showUnread) MaterialTheme.colors.primary else Color.Unspecified, shape = RoundedCornerShape(50))
        .padding(3.dp)
        .size(sp16)
    )
  }
}

@Composable
expect fun ActiveCallInteractiveArea(call: Call)

fun connectIfOpenedViaUri(rhId: Long?, uri: String, chatModel: ChatModel) {
  // Decode shredgram encrypted links before processing
  val processedUri = chat.simplex.common.platform.ShredgramLinkEncoder.processUri(uri)

  if (chatModel.currentUser.value == null) {
    chatModel.appOpenUrl.value = rhId to processedUri
  } else {
    withBGApi {
      chatModel.appOpenUrlConnecting.value = true
      planAndConnect(rhId, processedUri, close = null, cleanup = { chatModel.appOpenUrlConnecting.value = false })
    }
  }
}

// Old ChatListSearchBar removed - now using search overlay only

private fun connect(link: String, searchChatFilteredBySimplexLink: MutableState<String?>, cleanup: (() -> Unit)?) {
  // Decode shredgram encrypted links before processing
  val processedLink = chat.simplex.common.platform.ShredgramLinkEncoder.processUri(link)
  withBGApi {
    planAndConnect(
      chatModel.remoteHostId(),
      processedLink,
      filterKnownContact = { searchChatFilteredBySimplexLink.value = it.id },
      filterKnownGroup = { searchChatFilteredBySimplexLink.value = it.id },
      close = null,
      cleanup = cleanup,
    )
  }
}

@Composable
private fun ErrorSettingsView() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(generalGetString(MR.strings.error_showing_content), color = MaterialTheme.colors.error, fontStyle = FontStyle.Italic)
  }
}

private var lazyListState = 0 to 0

enum class ScrollDirection {
  Up, Down, Idle
}

@Composable
fun BoxScope.StatusBarBackground() {
  if (appPlatform.isAndroid) {
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colors.background))
  }
}

@Composable
fun BoxScope.NavigationBarBackground(appBarOnBottom: Boolean = false, mixedColor: Boolean, noAlpha: Boolean = false) {
  if (appPlatform.isAndroid) {
    val barPadding = WindowInsets.navigationBars.asPaddingValues()
    val paddingBottom = barPadding.calculateBottomPadding()
    Box(Modifier.align(Alignment.BottomStart).height(paddingBottom).fillMaxWidth().background(MaterialTheme.colors.background))
  }
}

@Composable
fun BoxScope.NavigationBarBackground(modifier: Modifier, color: Color = MaterialTheme.colors.background) {
  val keyboardState = getKeyboardState()
  if (appPlatform.isAndroid && keyboardState.value == KeyboardState.Closed) {
    val barPadding = WindowInsets.navigationBars.asPaddingValues()
    val paddingBottom = barPadding.calculateBottomPadding()
    Box(modifier.align(Alignment.BottomStart).height(paddingBottom).fillMaxWidth().background(color))
  }
}

@Composable
private fun BoxScope.ChatList(searchText: MutableState<TextFieldValue>, listState: LazyListState, selectedListFilter: MutableState<ChatListFilter> = mutableStateOf(ChatListFilter.ALL_CHATS)) {

  var scrollDirection by remember { mutableStateOf(ScrollDirection.Idle) }
  var previousIndex by remember { mutableStateOf(0) }
  var previousScrollOffset by remember { mutableStateOf(0) }
  val keyboardState by getKeyboardState()
  val oneHandUI = remember { appPrefs.oneHandUI.state }
  val activeFilter = remember { chatModel.activeChatTagFilter }

  LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
    val currentIndex = listState.firstVisibleItemIndex
    val currentScrollOffset = listState.firstVisibleItemScrollOffset
    val threshold = 25

    scrollDirection = when {
      currentIndex > previousIndex -> ScrollDirection.Down
      currentIndex < previousIndex -> ScrollDirection.Up
      currentScrollOffset > previousScrollOffset + threshold -> ScrollDirection.Down
      currentScrollOffset < previousScrollOffset - threshold -> ScrollDirection.Up
      currentScrollOffset == previousScrollOffset -> ScrollDirection.Idle
      else -> scrollDirection
    }

    previousIndex = currentIndex
    previousScrollOffset = currentScrollOffset
  }

  DisposableEffect(Unit) {
    onDispose { lazyListState = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
  }
  val allChats = remember { chatModel.chats }
  // In some not always reproducible situations this code produce IndexOutOfBoundsException on Compose's side
  // which is related to [derivedStateOf]. Using safe alternative instead
  // val chats by remember(search, showUnreadAndFavorites) { derivedStateOf { filteredChats(showUnreadAndFavorites, search, allChats.toList()) } }
  val searchShowingSimplexLink = remember { mutableStateOf(false) }
  val searchChatFilteredBySimplexLink = remember { mutableStateOf<String?>(null) }
  val chats = filteredChats(searchShowingSimplexLink, searchChatFilteredBySimplexLink, searchText.value.text, allChats.value.toList(), activeFilter.value, selectedListFilter.value)
  val topPaddingToContent = topPaddingToContent(false)
  val blankSpaceSize = if (oneHandUI.value) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else topPaddingToContent

  // Check if we should show empty state BEFORE rendering the list
  val hasRealChats = chatModel.chats.value.any { it.chatInfo is ChatInfo.Direct || it.chatInfo is ChatInfo.Group }
  if (chats.isEmpty()) {
    if (hasRealChats) {
      // Has real chats but filtered to empty - show filter message
      // Don't use imePadding() - content should stay in place when keyboard appears
      Box(Modifier.fillMaxSize().padding(horizontal = DEFAULT_PADDING), contentAlignment = Alignment.Center) {
        NoChatsView(searchText = searchText)
      }
    } else {
      // No real chats at all - show the beautiful empty state with avatars
      // Don't use imePadding() here - avatars and text should stay in place when keyboard appears
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyChatsView()
      }
    }
  } else {
    // Has chats to show - render the list with Shredgram gradient overlays
    Box(modifier = Modifier.fillMaxSize()) {
  LazyColumnWithScrollBar(
    if (!oneHandUI.value) Modifier.imePadding() else Modifier,
    listState,
    reverseLayout = false
  ) {
        // Only add top spacer for non-oneHandUI mode (where toolbar is overlaid)
        // For oneHandUI, the toolbar is in the Column layout so no extra space needed
        if (!oneHandUI.value) {
    item { Spacer(Modifier.height(blankSpaceSize)) }
    }
    itemsIndexed(chats, key = { _, chat -> chat.remoteHostId to chat.id }) { index, chat ->
      val nextChatSelected = remember(chat.id, chats) { derivedStateOf {
        chatModel.chatId.value != null && chats.getOrNull(index + 1)?.id == chatModel.chatId.value
      } }
      ChatListNavLinkView(chat, nextChatSelected)
    }
    // Old feature cards removed (one-hand UI card, address creation card)
    if (appPlatform.isAndroid) {
      item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).padding(bottom = if (oneHandUI.value) AppBarHeight * fontSizeSqrtMultiplier else 0.dp)) }
    }
  }

      // Shredgram: Top gradient overlay (32dp height, white to transparent)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(32.dp)
          .align(Alignment.TopCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                MaterialTheme.colors.background,
                Color.Transparent
              )
            )
          )
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(32.dp)
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                MaterialTheme.colors.background
              )
            )
          )
      )
    }
  }

  LaunchedEffect(activeFilter.value) {
    searchText.value = TextFieldValue("")
  }
}

private fun getRandomDpAvatar(id: Int): ImageResource {
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
    else -> MR.images.ic_avatar_1
  }
}

// Made public for preview support - Shredgram EmptyChatsState style
@Composable
fun EmptyChatsView(modifier: Modifier = Modifier, searchActive: MutableState<Boolean>? = null) {
  val isPreview = LocalInspectionMode.current

  val oneHandUI = if (isPreview) {
    remember { mutableStateOf(true) }
  } else {
    remember { appPrefs.oneHandUI.state }
  }

  // Randomly select 6 different avatar IDs from 1-10
  val randomAvatarIds = remember {
    (1..10).shuffled().take(6)
  }

  // Avatar colors for preview fallback
  val avatarColors = listOf(
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF2196F3),
    Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF607D8B)
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .fillMaxHeight()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.Center)
        .offset(y = (-80).dp)  // Move content up for more bottom space
        .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
      // Overlapping avatar circles - using original avatars with Shredgram 32dp size
      Box(
        contentAlignment = Alignment.Center
      ) {
        Row(
          modifier = Modifier.offset(x = 15.dp) // Half of total overlap (5 * 6 / 2 = 15)
        ) {
          randomAvatarIds.forEachIndexed { index, avatarId ->
        Box(
          modifier = Modifier
                .offset(x = (-6 * index).dp)
                .zIndex((6 - index).toFloat())
            ) {
              if (isPreview) {
                // Preview placeholder - colored circles
                Box(
                  modifier = Modifier
                    .size(32.dp)
            .clip(CircleShape)
                    .background(avatarColors[index % avatarColors.size])
                    .border(2.dp, MaterialTheme.colors.background, CircleShape),
          contentAlignment = Alignment.Center
        ) {
                  Text(listOf("👤", "👩", "👨", "🧑", "👱", "🧔")[index], fontSize = 14.sp)
                }
              } else {
                Image(
                  painter = painterResource(getRandomDpAvatar(avatarId)),
            contentDescription = null,
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colors.background, CircleShape),
                  contentScale = ContentScale.Crop
                )
              }
            }
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    // Title - Shredgram titleMedium
    Text(
      "You have no chats yet.",
      fontSize = 18.sp,
      fontFamily = chat.simplex.common.ui.theme.Manrope,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colors.secondary,
      textAlign = TextAlign.Center
    )

    Text(
      "Search by user name or phone number to start chatting with friends.",
      fontSize = 16.sp,
      fontFamily = chat.simplex.common.ui.theme.DMSans,
      fontWeight = FontWeight.Normal,
      color = MaterialTheme.colors.secondary,
      textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(16.dp))

    // Search button - Shredgram style
    Button(
      onClick = {
        if (isPreview) return@Button
        if (searchActive != null) {
          searchActive.value = true
        } else {
        showNewChatSheet(oneHandUI)
        }
      },
      shape = RoundedCornerShape(24.dp),
      colors = ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary
      ),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp)
    ) {
      if (isPreview) {
        Text("🔍", fontSize = 16.sp)
      } else {
      Icon(
        painterResource(MR.images.ic_search),
        contentDescription = "Search",
          tint = MaterialTheme.colors.onPrimary,
          modifier = Modifier.size(16.dp)
      )
      }
      Spacer(Modifier.width(8.dp))
      Text(
        "Search",
        fontSize = 14.sp,
        fontFamily = chat.simplex.common.ui.theme.DMSans,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.onPrimary
      )
    }
    }  // Close Column
  }  // Close Box
}

@Composable
private fun NoChatsView(searchText: MutableState<TextFieldValue>) {
  val activeFilter = remember { chatModel.activeChatTagFilter }.value

  if (searchText.value.text.isBlank()) {
    when (activeFilter) {
      is ActiveFilter.PresetTag -> Text(generalGetString(MR.strings.no_filtered_chats), color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
      is ActiveFilter.UserTag -> Text(String.format(generalGetString(MR.strings.no_chats_in_list), activeFilter.tag.chatTagText), color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
      is ActiveFilter.Unread -> {
          Row(
            Modifier.clip(shape = CircleShape).clickable { chatModel.activeChatTagFilter.value = null }.padding(DEFAULT_PADDING_HALF),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painterResource(MR.images.ic_filter_list),
              null,
              tint = MaterialTheme.colors.secondary
            )
            Text(generalGetString(MR.strings.no_unread_chats), color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
          }
      }
      null -> {
        // Show beautiful empty state with avatars
        EmptyChatsView()
      }
    }
  } else {
    Text(generalGetString(MR.strings.no_chats_found), color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
  }
}

@Composable
private fun ChatListFeatureCards() {
  val oneHandUI = remember { appPrefs.oneHandUI.state }
  val oneHandUICardShown = remember { appPrefs.oneHandUICardShown.state }
  val addressCreationCardShown = remember { appPrefs.addressCreationCardShown.state }

  Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // Hidden: Toggle chat list card
    // if (!oneHandUICardShown.value && !oneHandUI.value) {
    //   ToggleChatListCard()
    // }
    // Hidden: Your Simplex address card
    // if (!addressCreationCardShown.value) {
    //   AddressCreationCard()
    // }
    // Hidden: Toggle chat list card
    // if (!oneHandUICardShown.value && oneHandUI.value) {
    //   ToggleChatListCard()
    // }
  }
}

private val TAG_MIN_HEIGHT = 35.dp

@Composable
private fun TagsView(searchText: MutableState<TextFieldValue>) {
  val userTags = remember { chatModel.userTags }
  val presetTags = remember { chatModel.presetTags }
  val collapsiblePresetTags = presetTags.filter { presetCanBeCollapsed(it.key) && it.value > 0 }
  val alwaysShownPresetTags = presetTags.filter { !presetCanBeCollapsed(it.key) && it.value > 0 }
  val activeFilter = remember { chatModel.activeChatTagFilter }
  val unreadTags = remember { chatModel.unreadTags }
  val rhId = chatModel.remoteHostId()

  val rowSizeModifier = Modifier.sizeIn(minHeight = TAG_MIN_HEIGHT * fontSizeSqrtMultiplier)

  TagsRow {
    if (collapsiblePresetTags.size > 1) {
      if (collapsiblePresetTags.size + alwaysShownPresetTags.size + userTags.value.size <= 3) {
        PresetTagKind.entries.filter { t -> (presetTags[t] ?: 0) > 0 }.forEach { tag ->
          ExpandedTagFilterView(tag)
        }
      } else {
        CollapsedTagsFilterView(searchText)
        alwaysShownPresetTags.forEach { tag ->
          ExpandedTagFilterView(tag.key)
        }
      }
    }

    userTags.value.forEach { tag ->
      val current = when (val af = activeFilter.value) {
        is ActiveFilter.UserTag -> af.tag == tag
        else -> false
      }
      val interactionSource = remember { MutableInteractionSource() }
      val showMenu = rememberSaveable { mutableStateOf(false) }
      val saving = remember { mutableStateOf(false) }
      Box {
        Row(
          rowSizeModifier
            .clip(shape = CircleShape)
            .combinedClickable(
              onClick = {
                if (chatModel.activeChatTagFilter.value == ActiveFilter.UserTag(tag)) {
                  chatModel.activeChatTagFilter.value = null
                } else {
                  chatModel.activeChatTagFilter.value = ActiveFilter.UserTag(tag)
                }
              },
              onLongClick = { showMenu.value = true },
              interactionSource = interactionSource,
              indication = LocalIndication.current,
              enabled = !saving.value
            )
            .onRightClick { showMenu.value = true }
            .padding(4.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (tag.chatTagEmoji != null) {
            ReactionIcon(tag.chatTagEmoji, fontSize = 14.sp)
          } else {
            Icon(
              painterResource(if (current) MR.images.ic_label_filled else MR.images.ic_label),
              null,
              Modifier.size(18.sp.toDp()),
              tint = if (current) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
            )
          }
          Spacer(Modifier.width(4.dp))
          Box {
            val badgeText = if ((unreadTags[tag.chatTagId] ?: 0) > 0) " ●" else ""
            val invisibleText = buildAnnotatedString {
              append(tag.chatTagText)
              withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)) {
                append(badgeText)
              }
            }
            Text(
              text = invisibleText,
              fontWeight = FontWeight.Medium,
              fontSize = 15.sp,
              color = Color.Transparent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            // Visible text with styles
            val visibleText = buildAnnotatedString {
              append(tag.chatTagText)
              withStyle(SpanStyle(fontSize = 12.5.sp, color = MaterialTheme.colors.primary)) {
                append(badgeText)
              }
            }
            Text(
              text = visibleText,
              fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
              fontSize = 15.sp,
              color = if (current) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        TagsDropdownMenu(rhId, tag, showMenu, saving)
      }
    }
    val plusClickModifier = Modifier
      .clickable {
        ModalManager.start.showModalCloseable { close ->
          TagListEditor(rhId = rhId, close = close)
        }
      }

    if (userTags.value.isEmpty()) {
      Row(rowSizeModifier.clip(shape = CircleShape).then(plusClickModifier).padding(start = 2.dp, top = 4.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(MR.images.ic_add), stringResource(MR.strings.chat_list_add_list), Modifier.size(18.sp.toDp()), tint = MaterialTheme.colors.secondary)
        Spacer(Modifier.width(2.dp))
        Text(stringResource(MR.strings.chat_list_add_list), color = MaterialTheme.colors.secondary, fontSize = 15.sp)
      }
    } else {
      Box(rowSizeModifier, contentAlignment = Alignment.Center) {
        Icon(
          painterResource(MR.images.ic_add), stringResource(MR.strings.chat_list_add_list), Modifier.clip(shape = CircleShape).then(plusClickModifier).padding(2.dp), tint = MaterialTheme.colors.secondary
        )
      }
    }
  }
}

@Composable
expect fun TagsRow(content: @Composable() (() -> Unit))

@Composable
private fun ExpandedTagFilterView(tag: PresetTagKind) {
  val activeFilter = remember { chatModel.activeChatTagFilter }
  val active = when (val af = activeFilter.value) {
    is ActiveFilter.PresetTag -> af.tag == tag
    else -> false
  }
  val (icon, text) = presetTagLabel(tag, active)
  val color = if (active) MaterialTheme.colors.primary else MaterialTheme.colors.secondary

  Row(
    modifier = Modifier
      .sizeIn(minHeight = TAG_MIN_HEIGHT * fontSizeSqrtMultiplier)
      .clip(shape = CircleShape)
      .clickable {
        if (activeFilter.value == ActiveFilter.PresetTag(tag)) {
          chatModel.activeChatTagFilter.value = null
        } else {
          chatModel.activeChatTagFilter.value = ActiveFilter.PresetTag(tag)
        }
      }
      .padding(horizontal = 5.dp, vertical = 4.dp)
    ,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    Icon(
      painterResource(icon),
      stringResource(text),
      Modifier.size(18.sp.toDp()),
      tint = color
    )
    Spacer(Modifier.width(4.dp))
    Box {
      Text(
        stringResource(text),
        color = if (active) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        fontSize = 15.sp
      )
      Text(
        stringResource(text),
        color = Color.Transparent,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
      )
    }
  }
}


@Composable
private fun CollapsedTagsFilterView(searchText: MutableState<TextFieldValue>) {
  val activeFilter = remember { chatModel.activeChatTagFilter }
  val presetTags = remember { chatModel.presetTags }
  val showMenu = remember { mutableStateOf(false) }

  val selectedPresetTag = when (val af = activeFilter.value) {
    is ActiveFilter.PresetTag -> if (presetCanBeCollapsed(af.tag)) af.tag else null
    else -> null
  }

  Box(Modifier
    .clip(shape = CircleShape)
    .size(TAG_MIN_HEIGHT * fontSizeSqrtMultiplier)
    .clickable { showMenu.value = true },
    contentAlignment = Alignment.Center
  ) {
    if (selectedPresetTag != null) {
      val (icon, text) = presetTagLabel(selectedPresetTag, true)
      Icon(
        painterResource(icon),
        stringResource(text),
        Modifier.size(18.sp.toDp()),
        tint = MaterialTheme.colors.primary
      )
    } else {
      Icon(
        painterResource(MR.images.ic_menu),
        stringResource(MR.strings.chat_list_all),
        tint = MaterialTheme.colors.secondary
      )
    }

    val onCloseMenuAction = remember { mutableStateOf<(() -> Unit)>({}) }

    DefaultDropdownMenu(showMenu = showMenu, onClosed = onCloseMenuAction) {
      if (activeFilter.value != null || searchText.value.text.isNotBlank()) {
        ItemAction(
          stringResource(MR.strings.chat_list_all),
          painterResource(MR.images.ic_menu),
          onClick = {
            onCloseMenuAction.value = {
              searchText.value = TextFieldValue()
              chatModel.activeChatTagFilter.value = null
              onCloseMenuAction.value = {}
            }
            showMenu.value = false
          }
        )
      }
      PresetTagKind.entries.forEach { tag ->
        if ((presetTags[tag] ?: 0) > 0 && presetCanBeCollapsed(tag)) {
          ItemPresetFilterAction(tag, tag == selectedPresetTag, showMenu, onCloseMenuAction)
        }
      }
    }
  }
}

@Composable
fun ItemPresetFilterAction(
  presetTag: PresetTagKind,
  active: Boolean,
  showMenu: MutableState<Boolean>,
  onCloseMenuAction: MutableState<(() -> Unit)>
) {
  val (icon, text) = presetTagLabel(presetTag, active)
  ItemAction(
    stringResource(text),
    painterResource(icon),
    color = if (active) MaterialTheme.colors.primary else Color.Unspecified,
    onClick = {
      onCloseMenuAction.value = {
        chatModel.activeChatTagFilter.value = ActiveFilter.PresetTag(presetTag)
        onCloseMenuAction.value = {}
      }
      showMenu.value = false
    }
  )
}

fun filteredChats(
  searchShowingSimplexLink: State<Boolean>,
  searchChatFilteredBySimplexLink: State<String?>,
  searchText: String,
  chats: List<Chat>,
  activeFilter: ActiveFilter? = null,
  listFilter: ChatListFilter = ChatListFilter.ALL_CHATS
): List<Chat> {
  val linkChatId = searchChatFilteredBySimplexLink.value
  return if (linkChatId != null) {
    chats.filter { it.id == linkChatId }
  } else {
    val s = if (searchShowingSimplexLink.value) "" else searchText.trim().lowercase()
    if (s.isEmpty())
      chats.filter { chat ->
        chat.id == chatModel.chatId.value ||
        (!chat.chatInfo.chatDeleted &&
         !chat.chatInfo.contactCard &&
         // Exclude private notes, contact requests, and pending connections from main list
         chat.chatInfo !is ChatInfo.Local &&
         chat.chatInfo !is ChatInfo.ContactRequest &&
         chat.chatInfo !is ChatInfo.ContactConnection &&
         filtered(chat, activeFilter) &&
         matchesListFilter(chat.chatInfo, listFilter))
      }
    else {
      chats.filter { chat ->
        chat.id == chatModel.chatId.value ||
          (when (val cInfo = chat.chatInfo) {
            is ChatInfo.Direct -> !cInfo.contact.chatDeleted && !chat.chatInfo.contactCard && cInfo.anyNameContains(s)
            is ChatInfo.Group -> cInfo.anyNameContains(s)
            // Exclude from search results too
            is ChatInfo.Local -> false
            is ChatInfo.ContactRequest -> false
            is ChatInfo.ContactConnection -> false
            is ChatInfo.InvalidJSON -> false
          } && matchesListFilter(chat.chatInfo, listFilter))
          }
      }
    }
  }

// Filter chats based on the Chats/Contacts/Groups tab selection
private fun matchesListFilter(chatInfo: ChatInfo, listFilter: ChatListFilter): Boolean =
  when (listFilter) {
    ChatListFilter.ALL_CHATS -> true // Show all Direct and Group chats
    ChatListFilter.CONTACTS -> chatInfo is ChatInfo.Direct // Show only contacts (Direct chats)
    ChatListFilter.GROUPS -> chatInfo is ChatInfo.Group // Show only groups
}

private fun filtered(chat: Chat, activeFilter: ActiveFilter?): Boolean =
  when (activeFilter) {
    is ActiveFilter.PresetTag -> presetTagMatchesChat(activeFilter.tag, chat.chatInfo, chat.chatStats)
    is ActiveFilter.UserTag -> chat.chatInfo.chatTags?.contains(activeFilter.tag.chatTagId) ?: false
    is ActiveFilter.Unread -> chat.unreadTag
    else -> true
  }

fun presetTagMatchesChat(tag: PresetTagKind, chatInfo: ChatInfo, chatStats: Chat.ChatStats): Boolean =
  when (tag) {
    PresetTagKind.GROUP_REPORTS -> chatStats.reportsCount > 0
    PresetTagKind.FAVORITES -> chatInfo.chatSettings?.favorite == true
    PresetTagKind.CONTACTS -> when (chatInfo) {
      is ChatInfo.Direct -> !chatInfo.contact.isContactCard && !chatInfo.contact.chatDeleted
      is ChatInfo.ContactRequest -> true
      is ChatInfo.ContactConnection -> true
      is ChatInfo.Group -> chatInfo.groupInfo.businessChat?.chatType == BusinessChatType.Customer
      else -> false
    }
    PresetTagKind.GROUPS -> when (chatInfo) {
      is ChatInfo.Group -> chatInfo.groupInfo.businessChat == null
      else -> false
    }
    PresetTagKind.BUSINESS -> when (chatInfo) {
      is ChatInfo.Group -> chatInfo.groupInfo.businessChat?.chatType == BusinessChatType.Business
      else -> false
    }
    PresetTagKind.NOTES -> when (chatInfo) {
      is ChatInfo.Local -> !chatInfo.noteFolder.chatDeleted
      else -> false
    }
  }

private fun presetTagLabel(tag: PresetTagKind, active: Boolean): Pair<ImageResource, StringResource> =
  when (tag) {
    PresetTagKind.GROUP_REPORTS -> (if (active) MR.images.ic_flag_filled else MR.images.ic_flag) to MR.strings.chat_list_group_reports
    PresetTagKind.FAVORITES -> (if (active) MR.images.ic_star_filled else MR.images.ic_star) to MR.strings.chat_list_favorites
    PresetTagKind.CONTACTS -> (if (active) MR.images.ic_person_filled else MR.images.ic_person) to MR.strings.chat_list_contacts
    PresetTagKind.GROUPS -> (if (active) MR.images.ic_group_filled else MR.images.ic_group) to MR.strings.chat_list_groups
    PresetTagKind.BUSINESS -> (if (active) MR.images.ic_work_filled else MR.images.ic_work) to MR.strings.chat_list_businesses
    PresetTagKind.NOTES -> (if (active) MR.images.ic_folder_closed_filled else MR.images.ic_folder_closed) to MR.strings.chat_list_notes
  }

private fun presetCanBeCollapsed(tag: PresetTagKind): Boolean = when (tag) {
  PresetTagKind.GROUP_REPORTS -> false
  else -> true
}

fun scrollToBottom(scope: CoroutineScope, listState: LazyListState) {
  scope.launch { try { listState.animateScrollToItem(0) } catch (e: Exception) { Log.e(TAG, e.stackTraceToString()) } }
}

@Composable
fun PublicProfileSendRequestWarningDialog(onConfirm: () -> Unit) {
  val dontShowAgain = remember { mutableStateOf(false) }
  val uriHandler = LocalUriHandler.current

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Warning text
    Text(
      "By sending a request with your public profile, your identity and activity become trackable, allowing a social graph to be built.",
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

    // Buttons
    Column(Modifier.fillMaxWidth()) {
      SectionItemView({
        if (dontShowAgain.value) {
          appPreferences.showPublicProfileWarning.set(false)
        }
        AlertManager.shared.hideAlert()
        onConfirm()
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
  }
}
