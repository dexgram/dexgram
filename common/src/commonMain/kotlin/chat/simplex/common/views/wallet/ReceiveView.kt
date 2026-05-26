package chat.simplex.common.views.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.shareText
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.newchat.qrCodeBitmap
import boofcv.alg.fiducial.qrcode.QrCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.res.MR

private val BgPrimary @Composable get() = WalletColors.current.bgPrimary
private val BgCard @Composable get() = WalletColors.current.bgCard
private val TextPrimary @Composable get() = WalletColors.current.textPrimary
private val TextSecondary @Composable get() = WalletColors.current.textSecondary
private val AccentBlue @Composable get() = WalletColors.current.accentBlue
private val AccentGreen @Composable get() = WalletColors.current.accentGreen
private val GraySoft @Composable get() = WalletColors.current.graySoft
private val Border @Composable get() = WalletColors.current.border

@Composable
fun ReceiveView(
    account: WalletAccount,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var showAmountRequest by remember { mutableStateOf(false) }
    var requestAmount by remember { mutableStateOf("") }
    
    // Generate real QR code
    val qrCodeBitmap = remember(account.address) {
        try {
            qrCodeBitmap(account.address, 512, QrCode.ErrorLevel.M)
        } catch (e: Exception) {
            null
        }
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
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(MR.strings.wallet_receive_title, account.network.symbol),
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            
            // Network badge with icon
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AccentBlue.copy(0.1f),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
            Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                            .size(28.dp)
                        .clip(CircleShape)
                            .background(AccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        account.network.symbol.take(1),
                            fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onPrimary
                    )
                }
                    Spacer(Modifier.width(10.dp))
                Text(
                    account.network.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                )
            }
            }
            
            // QR Code Card with real QR
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = BgCard,
                elevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(MR.strings.wallet_scan_to_receive, account.network.symbol),
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // Real QR Code
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                    .border(2.dp, Border, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                        if (qrCodeBitmap != null) {
                            Image(
                                bitmap = qrCodeBitmap,
                                contentDescription = stringResource(MR.strings.wallet_qr_code_for_desc, account.address),
                    modifier = Modifier.fillMaxSize()
                )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = TextSecondary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(MR.strings.wallet_qr_code),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Address display
                Text(
                        stringResource(MR.strings.wallet_your_address, account.network.symbol),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                
                Spacer(Modifier.height(8.dp))
                
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GraySoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                Text(
                    account.address,
                                fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                                lineHeight = 20.sp
                )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Copy button
                Button(
                    onClick = { 
                        clipboard.setText(AnnotatedString(account.address))
                        isCopied = true
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isCopied) AccentGreen else AccentBlue
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.elevation(4.dp)
                ) {
                    Icon(
                        if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        null,
                        tint = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isCopied) stringResource(MR.strings.wallet_copied) else stringResource(MR.strings.wallet_copy_address),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onPrimary
                    )
                }
                
                // Share button
                OutlinedButton(
                    onClick = { 
                        clipboard.shareText(account.address)
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    border = BorderStroke(1.5.dp, AccentBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(MR.strings.wallet_share_button),
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Request amount option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAmountRequest = !showAmountRequest },
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.RequestPage,
                    null,
                            tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                )
                    }
                    Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(MR.strings.wallet_request_specific_amount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(MR.strings.wallet_generate_qr_with_amount),
                            fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                Icon(
                    if (showAmountRequest) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = TextSecondary
                )
                }
            }
            
            AnimatedVisibility(visible = showAmountRequest) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = requestAmount,
                    onValueChange = { requestAmount = InputValidator.sanitizeAmount(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(MR.strings.wallet_enter_amount), color = TextSecondary) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AttachMoney,
                                null,
                                tint = AccentBlue
                            )
                        },
                    trailingIcon = {
                        Text(
                            account.network.symbol,
                            fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.padding(end = 16.dp)
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
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Warning card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0xFFFFFBEB),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                        Icons.Default.Warning,
                    null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                            stringResource(MR.strings.wallet_important_notice),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                            stringResource(MR.strings.wallet_receive_warning, account.network.symbol, getTokenStandard(account.network)),
                            fontSize = 13.sp,
                            color = Color(0xFFB45309),
                            lineHeight = 18.sp
                    )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun getTokenStandard(network: BlockchainNetwork): String {
    return when (network) {
        BlockchainNetwork.ETHEREUM -> "ERC20"
        BlockchainNetwork.BINANCE_SMART_CHAIN -> "BEP20"
        BlockchainNetwork.POLYGON -> "ERC20"
        BlockchainNetwork.ARBITRUM -> "ERC20"
        BlockchainNetwork.OPTIMISM -> "ERC20"
        BlockchainNetwork.AVALANCHE -> "ERC20"
        BlockchainNetwork.BASE -> "ERC20"
        else -> "tokens"
    }
}
