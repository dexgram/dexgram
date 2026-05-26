package chat.simplex.common.views.wallet

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Shared design tokens
private val ElectricBlue500 @Composable get() = WalletColors.current.accentBlue
private val OnSurface @Composable get() = WalletColors.current.textPrimary
private val OnSurfaceVariant @Composable get() = WalletColors.current.textSecondary
private val BorderColor @Composable get() = WalletColors.current.border
private val DisabledBg @Composable get() = WalletColors.current.disabledBg
private val DisabledContent @Composable get() = WalletColors.current.disabledContent
private val ErrorRed @Composable get() = WalletColors.current.accentRed
private val AccentGreen @Composable get() = WalletColors.current.accentGreen
private val BgPrimary @Composable get() = WalletColors.current.bgPrimary
private val BgCard @Composable get() = WalletColors.current.bgCard
private val GraySoft @Composable get() = WalletColors.current.graySoft

@Composable
fun RecoverWalletView(
    onWalletRecovered: (WalletCreationResult) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var mnemonicInput by remember { mutableStateOf("") }
    var isRecovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val wordCount = mnemonicInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    val isValidWordCount = wordCount == 12 || wordCount == 24

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(MR.images.ic_arrow_back_ios_new),
                    contentDescription = stringResource(MR.strings.wallet_back_desc),
                    tint = OnSurface
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(MR.strings.wallet_recover_title),
                fontFamily = DMSans,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Icon(
                Icons.Default.Restore,
                contentDescription = null,
                tint = OnSurface,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(MR.strings.wallet_enter_recovery_heading),
                fontSize = 30.sp,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                lineHeight = (30f * 1.12f).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(MR.strings.wallet_enter_recovery_subtitle),
                fontSize = 14.sp,
                fontFamily = DMSans,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = (14f * 1.5f).sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = mnemonicInput,
                onValueChange = {
                    mnemonicInput = it.lowercase()
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                placeholder = {
                    Text(
                        stringResource(MR.strings.wallet_recovery_placeholder),
                        color = OnSurfaceVariant.copy(alpha = 0.5f),
                        fontFamily = DMSans,
                        fontSize = 14.sp
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = BgCard,
                    focusedBorderColor = ElectricBlue500,
                    unfocusedBorderColor = BorderColor,
                    textColor = OnSurface,
                    cursorColor = ElectricBlue500
                ),
                isError = errorMessage != null
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(MR.strings.wallet_word_count, wordCount),
                    fontSize = 13.sp,
                    fontFamily = DMSans,
                    color = when {
                        wordCount == 0 -> OnSurfaceVariant.copy(alpha = 0.5f)
                        isValidWordCount -> AccentGreen
                        else -> OnSurfaceVariant
                    }
                )

                if (isValidWordCount) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = AccentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(MR.strings.wallet_valid_word_count),
                            fontSize = 13.sp,
                            fontFamily = DMSans,
                            color = AccentGreen
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorRed.copy(0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        null,
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        errorMessage!!,
                        fontSize = 14.sp,
                        fontFamily = DMSans,
                        color = ErrorRed
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Tips card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GraySoft, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Text(
                    stringResource(MR.strings.wallet_tips_title),
                    fontSize = 16.sp,
                    fontFamily = DMSans,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Spacer(Modifier.height(10.dp))
                RecoverTipItem(stringResource(MR.strings.wallet_tip_spaces))
                RecoverTipItem(stringResource(MR.strings.wallet_tip_spelling))
                RecoverTipItem(stringResource(MR.strings.wallet_tip_word_count))
                RecoverTipItem(stringResource(MR.strings.wallet_tip_lowercase))
            }

            Spacer(Modifier.height(28.dp))

            // Paste button
            TextButton(
                onClick = {
                    val pastedText = clipboard.getText()?.text
                    if (!pastedText.isNullOrBlank()) {
                        mnemonicInput = pastedText.trim()
                    }
                }
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    null,
                    tint = OnSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(MR.strings.wallet_paste_from_clipboard),
                    fontSize = 16.sp,
                    fontFamily = DMSans,
                    fontWeight = FontWeight.Normal,
                    color = OnSurface
                )
            }

            Spacer(Modifier.height(24.dp))

            // Recover button
            Button(
                onClick = {
                    scope.launch {
                        isRecovering = true
                        delay(1500)

                        val walletCount = WalletCoreService.getWalletProfiles().size
                        val name = "Wallet ${walletCount + 1}"
                        val result = WalletCoreService.importWallet(mnemonicInput.trim(), name)

                        if (result.success) {
                            onWalletRecovered(result)
                        } else {
                            errorMessage = result.error ?: generalGetString(MR.strings.wallet_invalid_recovery_phrase)
                            isRecovering = false
                        }
                    }
                },
                enabled = isValidWordCount && !isRecovering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ElectricBlue500,
                    disabledBackgroundColor = DisabledBg,
                    contentColor = MaterialTheme.colors.onPrimary,
                    disabledContentColor = DisabledContent
                ),
                shape = RoundedCornerShape(360.dp),
                contentPadding = PaddingValues(vertical = 0.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                if (isRecovering) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colors.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        stringResource(MR.strings.wallet_recover_button),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun RecoverTipItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✓",
            fontSize = 16.sp,
            color = OnSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontFamily = DMSans,
            color = OnSurfaceVariant,
            lineHeight = (15f * 1.5f).sp
        )
    }
}
