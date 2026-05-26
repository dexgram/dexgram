package chat.simplex.common.views.wallet

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.newchat.QRCodeScanner
import chat.simplex.common.views.usersettings.LAMode
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch

// Theme-aware colors — see WalletTheme.kt
private val BgPrimary @Composable get() = WalletColors.current.bgPrimary
private val BgCard @Composable get() = WalletColors.current.bgCard
private val TextPrimary @Composable get() = WalletColors.current.textPrimary
private val TextSecondary @Composable get() = WalletColors.current.textSecondary
private val AccentBlue @Composable get() = WalletColors.current.accentBlue
private val AccentGreen @Composable get() = WalletColors.current.accentGreen
private val AccentRed @Composable get() = WalletColors.current.accentRed
private val AccentOrange @Composable get() = WalletColors.current.accentOrange
private val Border @Composable get() = WalletColors.current.border

@Composable
fun SendTransactionView(
    account: WalletAccount,
    onTransactionSent: (WalletTransaction) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var recipientAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var feeEstimate by remember { mutableStateOf<FeeEstimate?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val hasUnsavedInput = recipientAddress.isNotBlank() || amount.isNotBlank() || memo.isNotBlank()

    BackHandler(enabled = hasUnsavedInput && !isSending) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(MR.strings.wallet_send_discard_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(stringResource(MR.strings.wallet_send_discard_message), color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { showDiscardDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(MR.strings.wallet_send_discard_button), color = MaterialTheme.colors.onPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardDialog = false },
                    border = BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(MR.strings.wallet_send_keep_editing), color = TextSecondary) }
            },
            shape = RoundedCornerShape(20.dp),
            backgroundColor = BgCard
        )
    }

    val addressValidation = remember(recipientAddress) {
        if (recipientAddress.isBlank()) null
        else WalletCoreService.validateAddress(recipientAddress, account.network)
    }
    
    val isValidAmount = amount.toDoubleOrNull()?.let { it > 0 } ?: false
    val balance = account.balance.toDoubleOrNull() ?: 0.0
    val exceedsBalance = amount.toDoubleOrNull()?.let { it > balance } ?: false
    
    // Calculate USD value using real-time prices from CoinGecko
    val prices by WalletPriceService.pricesFlow.collectAsState()
    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val symbol = account.network.symbol.uppercase()
    val price = prices[symbol] ?: WalletPriceService.getPrice(symbol)
    val amountUsd = amountDouble * price
    val isValidAddress = addressValidation?.isValid == true
    val canSend = isValidAddress && isValidAmount && !exceedsBalance && !isSending
    
    LaunchedEffect(account.network) {
        feeEstimate = WalletCoreService.estimateFee(account.network)
    }
    
    // QR Scanner Dialog
    if (showQRScanner) {
        AlertDialog(
            onDismissRequest = { showQRScanner = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QrCodeScanner, stringResource(MR.strings.wallet_scan_qr_desc), tint = AccentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(MR.strings.wallet_scan_qr_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    QRCodeScanner(
                        onBarcode = { scannedAddress ->
                            val cleanAddress = scannedAddress
                                .removePrefix("ethereum:")
                                .removePrefix("eth:")
                                .removePrefix("0x").let { if (it.length == 40) "0x$it" else scannedAddress }
                                .trim()
                            
                            recipientAddress = cleanAddress
                            showQRScanner = false
                            true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQRScanner = false }) {
                    Text(stringResource(MR.strings.wallet_cancel_button), color = AccentBlue)
                }
            },
            shape = RoundedCornerShape(20.dp),
            backgroundColor = BgCard
        )
    }
    
    if (showConfirmation) {
        val doSend = {
            scope.launch {
                isSending = true
                showConfirmation = false

                val request = SendTransactionRequest(
                    network = account.network,
                    fromAddress = account.address,
                    toAddress = recipientAddress,
                    amount = amount,
                    memo = memo.ifBlank { null },
                    feeOption = feeEstimate?.normal
                )

                WalletCoreService.sendTransaction(request)
                    .onSuccess { tx ->
                        onTransactionSent(tx)
                    }
                    .onFailure { e ->
                        errorMessage = humanReadableError(e.message)
                        isSending = false
                    }
            }
            Unit
        }

        ConfirmTransactionDialog(
            account = account,
            recipientAddress = recipientAddress,
            amount = amount,
            fee = feeEstimate?.normal,
            onConfirm = {
                val isLockEnabled = ChatController.appPrefs.performLA.get()
                if (isLockEnabled) {
                    val laMode = ChatController.appPrefs.laMode.get()
                    authenticate(
                        promptTitle = if (laMode == LAMode.SYSTEM) generalGetString(MR.strings.wallet_confirm_transaction_title) else generalGetString(MR.strings.wallet_enter_passcode),
                        promptSubtitle = generalGetString(MR.strings.wallet_auth_send_subtitle).format(amount, account.network.symbol),
                        selfDestruct = false,
                        usingLAMode = laMode,
                        oneTime = true
                    ) { result ->
                        if (result is LAResult.Success) doSend()
                    }
                } else {
                    doSend()
                }
            },
            onCancel = { showConfirmation = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, stringResource(MR.strings.wallet_go_back_desc), tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(MR.strings.wallet_send_title, account.network.symbol),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    account.network.displayName,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        
        Divider(color = Border)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // From Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = BgCard,
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            account.network.symbol.take(1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AccentBlue
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(MR.strings.wallet_label_from),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            account.network.symbol,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            "${account.address.take(8)}...${account.address.takeLast(6)}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(MR.strings.wallet_label_balance),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            "${displayBalance(account.balance, account.network.symbol)} ${account.network.symbol}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Recipient Address
            Text(
                stringResource(MR.strings.wallet_label_recipient_address),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = recipientAddress,
                onValueChange = { recipientAddress = it; errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(MR.strings.wallet_enter_paste_address), color = TextSecondary) },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        IconButton(
                            onClick = { 
                                clipboard.getText()?.text?.let { pastedText ->
                                    recipientAddress = pastedText.trim()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentPaste, 
                                stringResource(MR.strings.wallet_paste_address_desc),
                                tint = AccentBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { showQRScanner = true }) {
                            Icon(
                                Icons.Default.QrCodeScanner, 
                                stringResource(MR.strings.wallet_scan_qr_desc),
                                tint = AccentBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = BgCard,
                    focusedBorderColor = if (addressValidation?.isValid == true) AccentGreen else AccentBlue,
                    unfocusedBorderColor = if (addressValidation?.isValid == true) AccentGreen else Border,
                    errorBorderColor = AccentRed,
                    textColor = TextPrimary,
                    cursorColor = AccentBlue
                ),
                isError = addressValidation?.isValid == false
            )
            
            // Address validation feedback
            AnimatedVisibility(visible = addressValidation != null) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (addressValidation?.isValid == true) Icons.Default.CheckCircle else Icons.Default.Error,
                        if (addressValidation?.isValid == true) stringResource(MR.strings.wallet_valid_address_desc) else stringResource(MR.strings.wallet_invalid_address_desc),
                        tint = if (addressValidation?.isValid == true) AccentGreen else AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (addressValidation?.isValid == true) stringResource(MR.strings.wallet_valid_address_format, account.network.symbol)
                        else stringResource(MR.strings.wallet_invalid_address_format),
                        fontSize = 12.sp,
                        color = if (addressValidation?.isValid == true) AccentGreen else AccentRed
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Amount
            Text(
                stringResource(MR.strings.wallet_label_amount),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = InputValidator.sanitizeAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0.00", color = TextSecondary) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            account.network.symbol.take(1),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            account.network.symbol,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { 
                                val maxAmount = (account.balance.toDoubleOrNull() ?: 0.0) * 0.99
                                amount = displayBalance(maxAmount.toString(), account.network.symbol)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = AccentOrange
                            )
                        ) {
                            Text(
                                stringResource(MR.strings.wallet_max_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = exceedsBalance && isValidAmount,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = BgCard,
                    focusedBorderColor = if (exceedsBalance) AccentRed else AccentBlue,
                    unfocusedBorderColor = if (exceedsBalance) AccentRed else Border,
                    errorBorderColor = AccentRed,
                    textColor = TextPrimary,
                    cursorColor = AccentBlue
                )
            )
            
            // USD value display
            AnimatedVisibility(visible = amount.isNotEmpty() && amountUsd > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AttachMoney,
                        stringResource(MR.strings.wallet_usd_value_desc),
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "≈ $${String.format("%,.2f", amountUsd)} USD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentGreen
                    )
                }
            }

            AnimatedVisibility(visible = exceedsBalance && isValidAmount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        stringResource(MR.strings.wallet_insufficient_balance_desc),
                        tint = AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(MR.strings.wallet_insufficient_balance, displayBalance(account.balance, account.network.symbol), account.network.symbol),
                        fontSize = 13.sp,
                        color = AccentRed
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Memo (Optional)
            Text(
                stringResource(MR.strings.wallet_label_memo_optional),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = InputValidator.sanitizeMemo(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(MR.strings.wallet_add_note_placeholder), color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Notes,
                        stringResource(MR.strings.wallet_memo_desc),
                        tint = TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = BgCard,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    textColor = TextPrimary,
                    cursorColor = AccentBlue
                )
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Network Fee Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = BgCard,
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    feeEstimate?.let { fee ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AccentOrange.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalGasStation,
                                    stringResource(MR.strings.wallet_network_fee_desc),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stringResource(MR.strings.wallet_network_fee),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    stringResource(MR.strings.wallet_current_gas_price),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${fee.normal.estimatedFee} ${account.network.symbol}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "≈ \$${String.format("%.4f", fee.normal.estimatedFeeUsd)}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    } ?: run {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(MR.strings.wallet_fetching_gas_price), fontSize = 14.sp, color = TextSecondary)
                        }
                    }
                }
            }
            
            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = AccentRed.copy(0.1f),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            stringResource(MR.strings.wallet_error_desc),
                            tint = AccentRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            errorMessage ?: "",
                            fontSize = 13.sp,
                            color = AccentRed
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Send Button
            Button(
                onClick = { showConfirmation = true },
                enabled = canSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(if (canSend) 8.dp else 0.dp, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentBlue,
                    disabledBackgroundColor = Border
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 4.dp
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colors.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        stringResource(MR.strings.wallet_send_desc),
                        tint = if (canSend) MaterialTheme.colors.onPrimary else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(MR.strings.wallet_send_button, account.network.symbol),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) MaterialTheme.colors.onPrimary else TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConfirmTransactionDialog(
    account: WalletAccount,
    recipientAddress: String,
    amount: String,
    fee: FeeOption?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = stringResource(MR.strings.wallet_security_desc),
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(MR.strings.wallet_confirm_transaction_title),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column {
                ConfirmRow(stringResource(MR.strings.wallet_label_to), "${recipientAddress.take(10)}...${recipientAddress.takeLast(8)}")
                Spacer(Modifier.height(12.dp))
                ConfirmRow(stringResource(MR.strings.wallet_label_amount), "$amount ${account.network.symbol}")
                Spacer(Modifier.height(12.dp))
                ConfirmRow(stringResource(MR.strings.wallet_network_fee), "${fee?.estimatedFee ?: "~"} ${account.network.symbol}")
                Spacer(Modifier.height(12.dp))
                Divider(color = Border)
                Spacer(Modifier.height(12.dp))
                
                val total = (amount.toDoubleOrNull() ?: 0.0) + (fee?.estimatedFee?.toDoubleOrNull() ?: 0.0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(MR.strings.wallet_label_total),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "${String.format("%.8f", total).trimEnd('0').trimEnd('.')} ${account.network.symbol}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(MR.strings.wallet_confirm_button), tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(MR.strings.wallet_confirm_button), color = MaterialTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(MR.strings.wallet_cancel_button), color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        backgroundColor = BgCard
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}
