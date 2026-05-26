package chat.simplex.common.views.wallet

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.platform.shareText
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BgPrimary @Composable get() = WalletColors.current.bgPrimary
private val BgCard @Composable get() = WalletColors.current.bgCard
private val TextPrimary @Composable get() = WalletColors.current.textPrimary
private val TextSecondary @Composable get() = WalletColors.current.textSecondary
private val AccentGold @Composable get() = WalletColors.current.accentGold
private val Green @Composable get() = WalletColors.current.accentGreen
private val Border @Composable get() = WalletColors.current.border

@Composable
fun CreateWalletView(
    onWalletCreated: (WalletCreationResult) -> Unit,
    onBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    var mnemonic by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = mnemonic != null) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(MR.strings.wallet_leave_creation_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(stringResource(MR.strings.wallet_leave_creation_message), color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { showDiscardDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = WalletColors.current.accentRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(MR.strings.wallet_leave_button), color = MaterialTheme.colors.onPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardDialog = false },
                    border = BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(MR.strings.wallet_stay_button), color = TextSecondary) }
            },
            shape = RoundedCornerShape(20.dp),
            backgroundColor = BgCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
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
                    tint = MaterialTheme.colors.onBackground
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(MR.strings.wallet_create_new_title),
              fontFamily = DMSans,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground
            )
        }

        if (currentStep > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { step ->
                    Box(
                        modifier = Modifier
                            .size(if (step == currentStep) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (step <= currentStep) MaterialTheme.colors.primary else Border)
                    )
                    if (step < 2) Spacer(Modifier.width(12.dp))
                }
            }
        }
        
        when (currentStep) {
            0 -> CreateStep1(
                isCreating = isCreating,
                onCreateClick = {
                    scope.launch {
                        isCreating = true
                        delay(1500)
                        // IMPORTANT: only generate mnemonic here; do NOT create/persist wallet yet.
                        mnemonic = WalletCoreService.generateNewMnemonic()
                        isCreating = false
                        currentStep = 1
                    }
                }
            )
            1 -> CreateStep2(
                mnemonic = mnemonic ?: "",
                onContinue = { currentStep = 2 }
            )
            2 -> CreateStep3(
                mnemonic = mnemonic ?: "",
                onConfirmed = {
                    val phrase = mnemonic ?: return@CreateStep3
                    val walletCount = WalletCoreService.getWalletProfiles().size
                    val name = "Wallet ${walletCount + 1}"
                    val result = WalletCoreService.importWallet(phrase, name)
                    if (result.success) {
                        onWalletCreated(result)
                    } else {
                        errorMessage = result.error ?: generalGetString(MR.strings.wallet_creation_failed_title)
                    }
                }
            )
        }

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text(stringResource(MR.strings.wallet_creation_failed_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground) },
                text = { Text(errorMessage ?: "", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text(stringResource(MR.strings.wallet_ok_button), color = MaterialTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                backgroundColor = BgCard
            )
        }
    }
}

@Composable
private fun CreateStep1(
    isCreating: Boolean,
    onCreateClick: () -> Unit
) {
    val colors = WalletColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            painter = painterResource(MR.images.ic_wallet_new),
            contentDescription = null,
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(28.dp)
        )

        Spacer(Modifier.height(28.dp))

        Text(
            stringResource(MR.strings.wallet_create_heading),
            fontSize = 32.sp,
          fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            lineHeight = (30f * 1.12f).sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(MR.strings.wallet_create_description),
            fontSize = 16.sp,
          fontFamily = DMSans,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = (14f * 1.5f).sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.graySoft, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                stringResource(MR.strings.wallet_security_tips_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground
            )
            Spacer(Modifier.height(10.dp))
            SecurityTipItem(stringResource(MR.strings.wallet_tip_write_down))
            SecurityTipItem(stringResource(MR.strings.wallet_tip_store_secure))
            SecurityTipItem(stringResource(MR.strings.wallet_tip_never_share))
            SecurityTipItem(stringResource(MR.strings.wallet_tip_never_digital))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onCreateClick,
            enabled = !isCreating,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary,
                disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colors.onPrimary
            ),
            shape = RoundedCornerShape(360.dp),
            contentPadding = PaddingValues(vertical = 0.dp),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    stringResource(MR.strings.wallet_create_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onPrimary
                )
            }
        }

        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

@Composable
private fun SecurityTipItem(text: String) {
    val colors = WalletColors.current
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✓",
            fontSize = 16.sp,
            color = colors.textSecondary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = colors.textSecondary,
            lineHeight = (15f * 1.5f).sp
        )
    }
}

@Composable
private fun CreateStep2(
    mnemonic: String,
    onContinue: () -> Unit
) {
    val colors = WalletColors.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val words = mnemonic.split(" ")
    var isCopied by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = null,
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(MR.strings.wallet_recovery_phrase_title),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            lineHeight = (30f * 1.12f).sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            stringResource(MR.strings.wallet_recovery_phrase_subtitle),
            fontSize = 14.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = (14f * 1.5f).sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.graySoft, RoundedCornerShape(16.dp))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        if (index < words.size) {
                            MnemonicWord(
                                number = index + 1,
                                word = words[index],
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (row < 3) Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = colors.accentOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(MR.strings.wallet_recovery_phrase_warning),
                fontSize = 14.sp,
                color = colors.textSecondary,
                lineHeight = (14f * 1.5f).sp
            )
        }

        Spacer(Modifier.height(10.dp))

        TextButton(
            onClick = { 
                clipboard.shareText(mnemonic)
                isCopied = true
                scope.launch {
                    delay(30_000)
                    clipboard.setText(AnnotatedString(""))
                    isCopied = false
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isCopied) stringResource(MR.strings.wallet_copied) else stringResource(MR.strings.wallet_copy_to_clipboard),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colors.onBackground
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            ),
            shape = RoundedCornerShape(360.dp),
            contentPadding = PaddingValues(vertical = 0.dp),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            Text(
                stringResource(MR.strings.wallet_phrase_stored_button),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onPrimary
            )
        }

        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

@Composable
private fun MnemonicWord(
    number: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    val colors = WalletColors.current
    Box(
        modifier = Modifier
            .then(modifier)
            .height(40.dp)
            .clip(RoundedCornerShape(360.dp))
            .background(colors.graySoft)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number. $word",
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun CreateStep3(
    mnemonic: String,
    onConfirmed: () -> Unit
) {
    val colors = WalletColors.current
    val words = mnemonic.split(" ")
    val shuffledWords = remember { words.shuffled() }
    var selectedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Icon(
            imageVector = Icons.Default.Checklist,
            contentDescription = null,
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(MR.strings.wallet_verify_phrase_title),
            fontSize = 30.sp,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            lineHeight = (30f * 1.12f).sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            stringResource(MR.strings.wallet_verify_phrase_subtitle),
            fontSize = 14.sp,
            fontFamily = DMSans,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = (14f * 1.5f).sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isError) colors.redSoft else colors.graySoft,
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    if (isError) colors.accentRed else colors.border,
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
                .heightIn(min = 100.dp)
        ) {
            if (selectedWords.isEmpty()) {
                Text(
                    stringResource(MR.strings.wallet_verify_placeholder),
                    fontSize = 14.sp,
                    fontFamily = DMSans,
                    color = colors.textHint,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                FlowRow(selectedWords) { word ->
                    Text(
                        word,
                        fontSize = 14.sp,
                        fontFamily = DMSans,
                        color = MaterialTheme.colors.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(360.dp))
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable {
                                selectedWords = selectedWords - word
                                isError = false
                            }
                    )
                }
            }
        }

        if (isError) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(MR.strings.wallet_verify_incorrect_order),
                fontSize = 13.sp,
                fontFamily = DMSans,
                color = colors.accentRed
            )
        }

        Spacer(Modifier.height(20.dp))

        FlowRow(shuffledWords.filter { it !in selectedWords }) { word ->
            Text(
                word,
                fontSize = 14.sp,
                fontFamily = DMSans,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(360.dp))
                    .background(colors.graySoft)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable {
                        selectedWords = selectedWords + word
                        isError = false
                    }
            )
        }

        Spacer(Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { selectedWords = emptyList(); isError = false },
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp),
                border = BorderStroke(1.dp, MaterialTheme.colors.onBackground),
                shape = RoundedCornerShape(360.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Text(
                    stringResource(MR.strings.wallet_clear_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onBackground
                )
            }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = {
                    if (selectedWords.joinToString(" ") == mnemonic) {
                        onConfirmed()
                    } else {
                        isError = true
                    }
                },
                enabled = selectedWords.size == words.size,
                modifier = Modifier
                    .weight(2f)
                    .height(62.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    disabledBackgroundColor = colors.disabledBg,
                    contentColor = MaterialTheme.colors.onPrimary,
                    disabledContentColor = colors.disabledContent
                ),
                shape = RoundedCornerShape(360.dp),
                contentPadding = PaddingValues(vertical = 0.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Text(
                    stringResource(MR.strings.wallet_confirm_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

@Composable
private fun FlowRow(items: List<String>, content: @Composable (String) -> Unit) {
    Column {
        var currentRowItems = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            currentRowItems.add(item)
            if (currentRowItems.size >= 4 || index == items.lastIndex) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentRowItems.forEach { content(it) }
                }
                currentRowItems = mutableListOf()
            }
        }
    }
}
