package chat.simplex.common.views.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.views.helpers.AlertManager
import chat.simplex.common.views.helpers.LAResult
import chat.simplex.common.views.helpers.authenticate
import chat.simplex.common.views.helpers.withLongRunningApi
import chat.simplex.common.views.usersettings.LAMode
import chat.simplex.common.views.wallet.*
import kotlinx.coroutines.*
import java.util.UUID

private data class TokenOption(val symbol: String, val network: BlockchainNetwork) {
    val displayLabel: String get() = "$symbol (${network.displayName})"
}

private fun buildTokenList(): List<TokenOption> {
    val list = mutableListOf<TokenOption>()
    for (net in BlockchainNetwork.ALL_SUPPORTED) {
        list.add(TokenOption(net.symbol, net))
        val tokens = try { SwapService.getSupportedTokens(net) } catch (_: Exception) { emptyList() }
        for (t in tokens) {
            if (t.symbol.equals(net.symbol, ignoreCase = true)) continue
            list.add(TokenOption(t.symbol, net))
        }
    }
    return list
}

// ═══════════════════════════════════════════════════════════════════
//  Spending limits
// ═══════════════════════════════════════════════════════════════════

object PaymentLimits {
    const val MAX_SINGLE_PAYMENT_USD = 5000.0
    const val MAX_DAILY_PAYMENTS_USD = 20000.0
    const val INVOICE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours

    private var dailySpentUsd = 0.0
    private var dailyResetTime = 0L

    fun checkDailyLimit(amountUsd: Double): Boolean {
        val now = System.currentTimeMillis()
        if (now - dailyResetTime > 24 * 60 * 60 * 1000L) {
            dailySpentUsd = 0.0
            dailyResetTime = now
        }
        return (dailySpentUsd + amountUsd) <= MAX_DAILY_PAYMENTS_USD
    }

    fun recordSpend(amountUsd: Double) {
        val now = System.currentTimeMillis()
        if (now - dailyResetTime > 24 * 60 * 60 * 1000L) {
            dailySpentUsd = 0.0
            dailyResetTime = now
        }
        dailySpentUsd += amountUsd
    }

    fun isInvoiceExpired(invoice: PaymentInvoice): Boolean {
        return System.currentTimeMillis() - invoice.createdAt > INVOICE_EXPIRY_MS
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Lifecycle-aware coroutine scope for payment execution
// ═══════════════════════════════════════════════════════════════════

private val paymentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun CreatePaymentInvoiceSheet(
    onDismiss: () -> Unit,
    onSendInvoice: (String) -> Unit
) {
    val allTokens = remember { buildTokenList() }
    val selectedToken = remember { mutableStateOf(allTokens.firstOrNull() ?: TokenOption("ETH", BlockchainNetwork.ETHEREUM)) }
    val amount = remember { mutableStateOf("") }
    val memo = remember { mutableStateOf("") }
    val error = remember { mutableStateOf<String?>(null) }
    val walletAddress = remember { mutableStateOf("") }

    LaunchedEffect(selectedToken.value) {
        val account = WalletCoreService.getAccount(selectedToken.value.network)
        walletAddress.value = account?.address ?: ""
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Create Payment Request",
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onBackground
        )

        Spacer(Modifier.height(16.dp))

        Text("Token & Network", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        TokenNetworkSelector(
            allTokens = allTokens,
            selected = selectedToken.value,
            onSelected = { selectedToken.value = it; error.value = null }
        )

        Spacer(Modifier.height(12.dp))

        Text("Amount", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = amount.value,
            onValueChange = { amount.value = it; error.value = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            trailingIcon = {
                Text(
                    selectedToken.value.symbol,
                    color = Color(0xFF6C8EFF),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF6C8EFF),
                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
            )
        )

        Spacer(Modifier.height(12.dp))

        Text("Your Receive Address", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(
            if (walletAddress.value.isNotEmpty())
                walletAddress.value.take(16) + "..." + walletAddress.value.takeLast(8)
            else "No wallet for ${selectedToken.value.network.displayName}",
            style = MaterialTheme.typography.body2.copy(
                color = if (walletAddress.value.isNotEmpty()) MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                else Color(0xFFEF4444)
            ),
            maxLines = 1
        )

        Spacer(Modifier.height(12.dp))

        Text("Memo (optional)", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = memo.value,
            onValueChange = { memo.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What's this for?") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF6C8EFF),
                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
            )
        )

        if (error.value != null) {
            Spacer(Modifier.height(8.dp))
            Text(error.value!!, color = Color(0xFFEF4444), style = MaterialTheme.typography.caption)
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val addr = walletAddress.value
                    val amt = amount.value.trim()
                    val tok = selectedToken.value

                    if (addr.isEmpty()) {
                        error.value = "No wallet found for ${tok.network.displayName}"
                        return@Button
                    }
                    if (amt.isEmpty() || amt.toDoubleOrNull() == null || amt.toDouble() <= 0) {
                        error.value = "Enter a valid amount"
                        return@Button
                    }

                    val tokenContract = try {
                        val tokens = SwapService.getSupportedTokens(tok.network)
                        tokens.firstOrNull { it.symbol.equals(tok.symbol, ignoreCase = true) }
                            ?.address?.takeIf { it != "NATIVE" && it != "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE" }
                    } catch (_: Exception) { null }

                    val invoice = PaymentInvoice(
                        invoiceId = UUID.randomUUID().toString(),
                        network = tok.network,
                        toAddress = addr,
                        amount = amt,
                        tokenSymbol = tok.symbol,
                        tokenContractAddress = tokenContract,
                        memo = memo.value.ifBlank { null }
                    )
                    onSendInvoice(PaymentInvoice.encode(invoice))
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3B82F6))
            ) {
                Text("Send Request", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TokenNetworkSelector(
    allTokens: List<TokenOption>,
    selected: TokenOption,
    onSelected: (TokenOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true; search = "" },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.2f))
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected.symbol,
                    color = MaterialTheme.colors.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    selected.network.displayName,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
            Text("▼", color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f), fontSize = 10.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp).width(280.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search token...", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.body2
            )

            val filtered = if (search.isBlank()) allTokens else allTokens.filter {
                it.symbol.contains(search, ignoreCase = true) ||
                it.network.displayName.contains(search, ignoreCase = true)
            }

            filtered.forEach { tok ->
                DropdownMenuItem(onClick = {
                    onSelected(tok)
                    expanded = false
                }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tok.symbol,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tok.network.displayName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

fun handlePayInvoice(invoice: PaymentInvoice, chatItem: ChatItem) {
    // Enforce invoice expiry
    if (PaymentLimits.isInvoiceExpired(invoice)) {
        AlertManager.shared.showAlertMsg(
            title = "Invoice Expired",
            text = "This payment request has expired (older than 24 hours)."
        )
        return
    }

    // Check if wallet is locked and biometric auth is required
    if (WalletLockManager.requiresAuth(WalletAction.SEND) && WalletLockManager.isLocked()) {
        AlertManager.shared.showAlertMsg(
            title = "Wallet Locked",
            text = "Unlock your wallet to make payments."
        )
        return
    }

    val amountUsd = invoice.amount.toDoubleOrNull() ?: 0.0
    if (amountUsd > PaymentLimits.MAX_SINGLE_PAYMENT_USD) {
        AlertManager.shared.showAlertMsg(
            title = "Limit Exceeded",
            text = "Single payment cannot exceed \$${PaymentLimits.MAX_SINGLE_PAYMENT_USD.toInt()}."
        )
        return
    }
    if (!PaymentLimits.checkDailyLimit(amountUsd)) {
        AlertManager.shared.showAlertMsg(
            title = "Daily Limit Reached",
            text = "You have reached the daily spending limit of \$${PaymentLimits.MAX_DAILY_PAYMENTS_USD.toInt()}."
        )
        return
    }

    AlertManager.shared.showAlertDialogButtons(
        title = "Confirm Payment",
        text = "Send ${invoice.amount} ${invoice.tokenSymbol} on ${invoice.network.displayName}?\n\nTo: ${invoice.toAddress.take(10)}...${invoice.toAddress.takeLast(6)}",
        buttons = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { AlertManager.shared.hideAlert() }) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    AlertManager.shared.hideAlert()
                    val isLockEnabled = ChatController.appPrefs.performLA.get()
                    if (isLockEnabled) {
                        val laMode = ChatController.appPrefs.laMode.get()
                        authenticate(
                            promptTitle = if (laMode == LAMode.SYSTEM) "Confirm Payment" else "Enter passcode",
                            promptSubtitle = "Authenticate to send ${invoice.amount} ${invoice.tokenSymbol}",
                            selfDestruct = false,
                            usingLAMode = laMode,
                            oneTime = true
                        ) { result ->
                            if (result is LAResult.Success) {
                                executeInvoicePayment(invoice)
                            }
                        }
                    } else {
                        executeInvoicePayment(invoice)
                    }
                }) {
                    Text("Pay", fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                }
            }
        }
    )
}

private fun executeInvoicePayment(invoice: PaymentInvoice) {
    paymentScope.launch {
        try {
            val account = WalletCoreService.getAccount(invoice.network)
            if (account == null) {
                withContext(Dispatchers.Main) {
                    AlertManager.shared.showAlertMsg(
                        title = "No Wallet",
                        text = "You don't have a ${invoice.network.displayName} wallet. Add one first."
                    )
                }
                return@launch
            }

            val request = SendTransactionRequest(
                network = invoice.network,
                fromAddress = account.address,
                toAddress = invoice.toAddress,
                amount = invoice.amount,
                tokenContractAddress = invoice.tokenContractAddress
            )

            withContext(Dispatchers.Main) {
                AlertManager.shared.showAlertMsg(title = "Sending...", text = "Processing your payment of ${invoice.amount} ${invoice.tokenSymbol}")
            }

            val result = WalletCoreService.sendTransaction(request)
            result.fold(
                onSuccess = { tx ->
                    PaymentLimits.recordSpend(invoice.amount.toDoubleOrNull() ?: 0.0)

                    val confirmation = PaymentConfirmation(
                        invoiceId = invoice.invoiceId,
                        txHash = tx.txHash
                    )
                    val confirmMsg = PaymentInvoice.encodeConfirmation(confirmation)

                    withContext(Dispatchers.Main) {
                        AlertManager.shared.hideAlert()
                        sendPaymentConfirmation(confirmMsg)
                        AlertManager.shared.showAlertMsg(
                            title = "Payment Sent",
                            text = "Successfully sent ${invoice.amount} ${invoice.tokenSymbol}\nTX: ${tx.txHash.take(16)}..."
                        )
                    }
                },
                onFailure = { error ->
                    withContext(Dispatchers.Main) {
                        AlertManager.shared.hideAlert()
                        AlertManager.shared.showAlertMsg(
                            title = "Payment Failed",
                            text = error.message ?: "Transaction failed"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                AlertManager.shared.hideAlert()
                AlertManager.shared.showAlertMsg(
                    title = "Payment Error",
                    text = e.message ?: "Unexpected error"
                )
            }
        }
    }
}

private fun sendPaymentConfirmation(encodedConfirmation: String) {
    val model = ChatModel
    val activeChatId = model.chatId.value ?: return
    val activeChat = model.chatsContext.getChat(activeChatId) ?: return

    if (activeChat.chatInfo.sndReady) {
        withLongRunningApi(slow = 60_000) {
            val cInfo = activeChat.chatInfo
            val chatItems = model.controller.apiSendMessages(
                rh = activeChat.remoteHostId,
                type = cInfo.chatType,
                id = cInfo.apiId,
                scope = cInfo.groupChatScope(),
                composedMessages = listOf(
                    ComposedMessage(
                        fileSource = null,
                        quotedItemId = null,
                        msgContent = MsgContent.MCText(encodedConfirmation),
                        mentions = emptyMap()
                    )
                )
            )
            if (!chatItems.isNullOrEmpty()) {
                chatItems.forEach { aChatItem ->
                    withContext(Dispatchers.Main) {
                        model.chatsContext.addChatItem(activeChat.remoteHostId, aChatItem.chatInfo, aChatItem.chatItem)
                    }
                }
            }
        }
    }
}

suspend fun verifyPaymentOnChain(confirmation: PaymentConfirmation, network: BlockchainNetwork): Boolean {
    return try {
        val status = PlatformWallet.checkTransactionStatus(confirmation.txHash, network)
        status == TransactionStatus.CONFIRMED
    } catch (_: Exception) {
        false
    }
}
