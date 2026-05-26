package chat.simplex.common.views.wallet

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Palette ─────────────────────────────────────────────────────────
private val BgMain @Composable get() = WalletColors.current.bgPrimary
private val CardBg @Composable get() = WalletColors.current.bgCard
private val TextMain @Composable get() = WalletColors.current.textPrimary
private val TextSub @Composable get() = WalletColors.current.textSecondary
private val TextHint @Composable get() = WalletColors.current.textHint
private val Accent @Composable get() = WalletColors.current.accentBlue
private val GreenBright @Composable get() = WalletColors.current.accentGreen
private val RedBright @Composable get() = WalletColors.current.accentRed
private val AmberWarm @Composable get() = WalletColors.current.accentOrange
private val DividerLight @Composable get() = WalletColors.current.divider

private val GreenSoft @Composable get() = WalletColors.current.greenSoft
private val RedSoft @Composable get() = WalletColors.current.redSoft
private val BlueSoft @Composable get() = WalletColors.current.blueSoft
private val AmberSoft @Composable get() = WalletColors.current.amberSoft
private val GraySoft @Composable get() = WalletColors.current.graySoft

// ════════════════════════════════════════════════════════════════════
//  Transaction History
// ════════════════════════════════════════════════════════════════════

@Composable
fun TransactionHistoryView(
    transactions: List<WalletTransaction>,
    onTransactionClick: (WalletTransaction) -> Unit,
    onBack: () -> Unit,
    onRefresh: (suspend () -> Unit)? = null
) {
    var filterType by remember { mutableStateOf<TransactionType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val prices by WalletPriceService.pricesFlow.collectAsState()

    val todayLabel = generalGetString(MR.strings.wallet_today)
    val yesterdayLabel = generalGetString(MR.strings.wallet_yesterday)

    val filteredTransactions = remember(transactions, filterType, searchQuery) {
        transactions.filter { tx ->
            (filterType == null || tx.type == filterType) &&
            (searchQuery.isBlank() ||
                tx.txHash.contains(searchQuery, true) ||
                tx.toAddress.contains(searchQuery, true) ||
                tx.fromAddress.contains(searchQuery, true) ||
                (tx.tokenSymbol ?: "").contains(searchQuery, true) ||
                tx.network.displayName.contains(searchQuery, true))
        }
    }

    val groupedTransactions = remember(filteredTransactions, todayLabel, yesterdayLabel) {
        filteredTransactions.groupBy { tx ->
            val ts = if (tx.timestamp > 1_000_000_000_000L) tx.timestamp else tx.timestamp * 1000
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            when {
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> todayLabel
                cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> yesterdayLabel
                else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(ts))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(BgMain)) {
        // ── Header ──────────────────────────────────────────────────
        Surface(elevation = 2.dp, color = CardBg) {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextMain)
                    }
                    Text(
                        stringResource(MR.strings.wallet_activity_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        modifier = Modifier.weight(1f)
                    )
                    if (onRefresh != null) {
                        IconButton(onClick = {
                            isRefreshing = true
                            coroutineScope.launch { onRefresh(); isRefreshing = false }
                        }) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp), strokeWidth = 2.dp, color = Accent
                                )
                            } else {
                                Icon(Icons.Default.Refresh, stringResource(MR.strings.wallet_refresh_desc), tint = TextSub)
                            }
                        }
                    }
                }

                // ── Summary bar ─────────────────────────────────────
                val totalSent = filteredTransactions
                    .filter { it.type == TransactionType.SEND || it.type == TransactionType.TOKEN_TRANSFER && it.isOutgoing }
                    .size
                val totalReceived = filteredTransactions
                    .filter { it.type == TransactionType.RECEIVE }
                    .size
                val totalSwaps = filteredTransactions.count { it.type == TransactionType.SWAP }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryChip(
                        Modifier.weight(1f), stringResource(MR.strings.wallet_sent_label), "$totalSent", RedBright, RedSoft
                    )
                    SummaryChip(
                        Modifier.weight(1f), stringResource(MR.strings.wallet_received_label), "$totalReceived", GreenBright, GreenSoft
                    )
                    SummaryChip(
                        Modifier.weight(1f), stringResource(MR.strings.wallet_swaps_label), "$totalSwaps", Accent, BlueSoft
                    )
                }

                // ── Search ──────────────────────────────────────────
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(MR.strings.wallet_search_placeholder), color = TextHint, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextHint, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = TextHint, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = BgMain,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = DividerLight,
                        textColor = TextMain,
                        cursorColor = Accent
                    )
                )

                // ── Filter chips ────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TxFilterChip(stringResource(MR.strings.wallet_filter_all), filterType == null) { filterType = null }
                    TxFilterChip(stringResource(MR.strings.wallet_filter_sent), filterType == TransactionType.SEND, RedBright) {
                        filterType = if (filterType == TransactionType.SEND) null else TransactionType.SEND
                    }
                    TxFilterChip(stringResource(MR.strings.wallet_filter_received), filterType == TransactionType.RECEIVE, GreenBright) {
                        filterType = if (filterType == TransactionType.RECEIVE) null else TransactionType.RECEIVE
                    }
                    TxFilterChip(stringResource(MR.strings.wallet_filter_swaps), filterType == TransactionType.SWAP, Accent) {
                        filterType = if (filterType == TransactionType.SWAP) null else TransactionType.SWAP
                    }
                    TxFilterChip(stringResource(MR.strings.wallet_filter_tokens), filterType == TransactionType.TOKEN_TRANSFER, AmberWarm) {
                        filterType = if (filterType == TransactionType.TOKEN_TRANSFER) null else TransactionType.TOKEN_TRANSFER
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Transaction list ────────────────────────────────────────
        if (filteredTransactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(GraySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Receipt, null, tint = TextHint, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(MR.strings.wallet_no_transactions), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(MR.strings.wallet_activity_will_appear), fontSize = 14.sp, color = TextSub)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedTransactions.forEach { (date, txList) ->
                    item {
                        Text(
                            date,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSub,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }
                    items(txList, key = { it.id + it.txHash }) { tx ->
                        TransactionRow(tx, prices, onClick = { onTransactionClick(tx) })
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Transaction Row
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TransactionRow(
    tx: WalletTransaction,
    prices: Map<String, Double>,
    onClick: () -> Unit
) {
    val isOut = tx.type == TransactionType.SEND ||
        (tx.type == TransactionType.TOKEN_TRANSFER && tx.isOutgoing)
    val isSwap = tx.type == TransactionType.SWAP
    val symbol = tx.tokenSymbol ?: tx.network.symbol

    val (iconVector, iconBg, iconTint) = when {
        isSwap -> Triple(Icons.Default.SwapHoriz, BlueSoft, Accent)
        isOut -> Triple(Icons.Default.CallMade, RedSoft, RedBright)
        else -> Triple(Icons.Default.CallReceived, GreenSoft, GreenBright)
    }

    val amountColor = when {
        isSwap -> Accent
        isOut -> RedBright
        else -> GreenBright
    }
    val amountPrefix = when {
        isSwap -> ""
        isOut -> "−"
        else -> "+"
    }

    val price = prices[symbol.uppercase()] ?: WalletPriceService.getPrice(symbol.uppercase())
    val amountVal = tx.amount.toDoubleOrNull() ?: 0.0
    val usdVal = amountVal * price

    val txTypeLabel = when {
        isSwap -> stringResource(MR.strings.wallet_tx_swap)
        isOut -> stringResource(MR.strings.wallet_tx_sent)
        else -> stringResource(MR.strings.wallet_tx_received)
    }

    val addressLabel = if (isOut)
        "${stringResource(MR.strings.wallet_label_to)} ${tx.toAddress.take(6)}…${tx.toAddress.takeLast(4)}"
    else
        "${stringResource(MR.strings.wallet_label_from)} ${tx.fromAddress.take(6)}…${tx.fromAddress.takeLast(4)}"

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        elevation = 1.dp
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconVector, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        txTypeLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusPill(tx.status)
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkBadge(tx.network)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        addressLabel,
                        fontSize = 12.sp,
                        color = TextSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(formatTimeFull(tx.timestamp), fontSize = 11.sp, color = TextHint)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$amountPrefix${formatAmount(tx.amount, symbol)} $symbol",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                if (usdVal > 0.001) {
                    Text(
                        "$${formatUsd(usdVal)}",
                        fontSize = 12.sp,
                        color = TextSub
                    )
                }
                if (tx.fee.isNotEmpty() && tx.fee != "0" && tx.fee != "0.0") {
                    Text(
                        "${stringResource(MR.strings.wallet_label_fee)} ${formatAmount(tx.fee, tx.network.symbol)} ${tx.network.symbol}",
                        fontSize = 10.sp,
                        color = TextHint
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Transaction Detail
// ════════════════════════════════════════════════════════════════════

@Composable
fun TransactionDetailView(
    transaction: WalletTransaction,
    onBack: () -> Unit
) {
    val isOut = transaction.type == TransactionType.SEND ||
        (transaction.type == TransactionType.TOKEN_TRANSFER && transaction.isOutgoing)
    val isSwap = transaction.type == TransactionType.SWAP
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val prices by WalletPriceService.pricesFlow.collectAsState()

    val symbol = transaction.tokenSymbol ?: transaction.network.symbol
    val price = prices[symbol.uppercase()] ?: WalletPriceService.getPrice(symbol.uppercase())
    val amountVal = transaction.amount.toDoubleOrNull() ?: 0.0
    val usdVal = amountVal * price

    val (accentColor, softBg) = when {
        isSwap -> Accent to BlueSoft
        isOut -> RedBright to RedSoft
        else -> GreenBright to GreenSoft
    }

    val txTypeLabel = when {
        isSwap -> stringResource(MR.strings.wallet_tx_swap)
        isOut -> stringResource(MR.strings.wallet_tx_sent)
        else -> stringResource(MR.strings.wallet_tx_received)
    }

    Column(Modifier.fillMaxSize().background(BgMain)) {
        // ── Header ──────────────────────────────────────────────
        Surface(elevation = 2.dp, color = CardBg) {
            Row(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextMain)
                }
                Text(stringResource(MR.strings.wallet_transaction_details), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // ── Hero card ───────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                elevation = 2.dp
            ) {
                Box {
                    Box(
                        Modifier.fillMaxWidth().height(4.dp)
                            .background(Brush.horizontalGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.4f))
                            ))
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(softBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when {
                                    isSwap -> Icons.Default.SwapHoriz
                                    isOut -> Icons.Default.CallMade
                                    else -> Icons.Default.CallReceived
                                },
                                null, tint = accentColor, modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            txTypeLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSub
                        )
                        Spacer(Modifier.height(4.dp))

                        Text(
                            "${if (isOut) "−" else if (!isSwap) "+" else ""}${displayBalance(transaction.amount, symbol)} $symbol",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )

                        if (usdVal > 0.001) {
                            Text("≈ $${formatUsd(usdVal)}", fontSize = 16.sp, color = TextSub)
                        }

                        Spacer(Modifier.height(12.dp))
                        StatusPill(transaction.status, large = true)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Details card ────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                elevation = 1.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (transaction.tokenSymbol != null) {
                        InfoRow(stringResource(MR.strings.wallet_label_token), "${transaction.tokenSymbol} (${transaction.network.displayName})")
                        InfoDivider()
                    }
                    InfoRow(stringResource(MR.strings.wallet_label_network), transaction.network.displayName)
                    InfoDivider()
                    InfoRowCopyable(stringResource(MR.strings.wallet_label_from), transaction.fromAddress, clipboard)
                    InfoDivider()
                    InfoRowCopyable(stringResource(MR.strings.wallet_label_to), transaction.toAddress, clipboard)
                    InfoDivider()
                    InfoRow(stringResource(MR.strings.wallet_label_fee), "${transaction.fee} ${transaction.network.symbol}")
                    InfoDivider()
                    InfoRow(stringResource(MR.strings.wallet_label_date), formatDateTimeFull(transaction.timestamp))

                    if (transaction.blockNumber != null) {
                        InfoDivider()
                        InfoRow(stringResource(MR.strings.wallet_label_block), transaction.blockNumber.toString())
                    }
                    if (transaction.confirmations > 0) {
                        InfoDivider()
                        InfoRow(stringResource(MR.strings.wallet_label_confirmations), "${transaction.confirmations}")
                    }
                    if (!transaction.memo.isNullOrBlank()) {
                        InfoDivider()
                        InfoRow(stringResource(MR.strings.wallet_label_memo), transaction.memo)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Tx hash card ────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                elevation = 1.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(MR.strings.wallet_transaction_hash), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSub)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        transaction.txHash,
                        fontSize = 13.sp,
                        color = TextMain,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(transaction.txHash))
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(MR.strings.wallet_tap_to_copy), fontSize = 10.sp, color = TextHint)

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            try { uriHandler.openUri(transaction.explorerUrl()) } catch (_: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
                        elevation = ButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(MR.strings.wallet_view_on_explorer), color = MaterialTheme.colors.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Reusable Components
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SummaryChip(
    modifier: Modifier,
    label: String,
    count: String,
    accent: Color,
    bg: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, fontSize = 11.sp, color = accent.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun TxFilterChip(
    text: String,
    isSelected: Boolean,
    accentColor: Color = Accent,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) accentColor else CardBg,
        border = BorderStroke(1.dp, if (isSelected) accentColor else DividerLight)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colors.onPrimary else TextSub,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun StatusPill(status: TransactionStatus, large: Boolean = false) {
    val label: String
    val bg: Color
    val fg: Color
    val icon: ImageVector
    when (status) {
        TransactionStatus.CONFIRMED -> { label = generalGetString(MR.strings.wallet_status_confirmed); bg = GreenSoft; fg = GreenBright; icon = Icons.Default.CheckCircle }
        TransactionStatus.PENDING -> { label = generalGetString(MR.strings.wallet_status_pending); bg = AmberSoft; fg = AmberWarm; icon = Icons.Default.Schedule }
        TransactionStatus.FAILED -> { label = generalGetString(MR.strings.wallet_status_failed); bg = RedSoft; fg = RedBright; icon = Icons.Default.Cancel }
        TransactionStatus.CANCELLED -> { label = generalGetString(MR.strings.wallet_status_cancelled); bg = GraySoft; fg = TextSub; icon = Icons.Default.Close }
    }
    Surface(
        shape = RoundedCornerShape(if (large) 20.dp else 6.dp),
        color = bg
    ) {
        Row(
            Modifier.padding(horizontal = if (large) 12.dp else 6.dp, vertical = if (large) 6.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(if (large) 16.dp else 12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = if (large) 13.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}

@Composable
private fun NetworkBadge(network: BlockchainNetwork) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Accent.copy(alpha = 0.08f)
    ) {
        Text(
            network.symbol,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 13.sp, color = TextSub, modifier = Modifier.widthIn(max = 100.dp))
        Text(
            value, fontSize = 13.sp, color = TextMain, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
    }
}

@Composable
private fun InfoRowCopyable(
    label: String,
    address: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSub, modifier = Modifier.widthIn(max = 100.dp))
        Row(
            Modifier.weight(1f).padding(start = 12.dp)
                .clickable { clipboard.setText(AnnotatedString(address)) },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${address.take(10)}…${address.takeLast(6)}",
                fontSize = 13.sp,
                color = TextMain,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ContentCopy, stringResource(MR.strings.wallet_copy_desc), tint = TextHint, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun InfoDivider() {
    Divider(color = DividerLight, modifier = Modifier.padding(vertical = 4.dp))
}

// ════════════════════════════════════════════════════════════════════
//  Formatters
// ════════════════════════════════════════════════════════════════════

private fun formatTimeFull(timestamp: Long): String {
    val ts = if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
}

private fun formatDateTimeFull(timestamp: Long): String {
    val ts = if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000
    return SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(ts))
}

private fun formatAmount(amount: String, symbol: String = ""): String {
    return displayBalance(amount, symbol)
}

private fun formatUsd(value: Double): String = when {
    value >= 1_000_000 -> String.format("%.2fM", value / 1_000_000)
    value >= 1_000 -> String.format("%.2f", value)
    value < 0.01 && value > 0 -> String.format("%.4f", value)
    else -> String.format("%.2f", value)
}
