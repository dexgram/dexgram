package chat.simplex.common.views.wallet

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.platform.shareText
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.newchat.qrCodeBitmap
import chat.simplex.common.views.newchat.QRCodeScanner
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import boofcv.alg.fiducial.qrcode.QrCode
import chat.simplex.common.model.ChatController
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.views.helpers.LAResult
import chat.simplex.common.views.helpers.authenticate
import chat.simplex.common.views.usersettings.LAMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

// Helper functions delegate to shared TokenIcons utility
private fun getTokenIcon(symbol: String): ImageResource? = TokenIcons.getIcon(symbol = symbol)
private fun getNetworkIcon(network: BlockchainNetwork): ImageResource? = TokenIcons.getNetworkIcon(network)

// Theme-aware colors — see WalletTheme.kt
private val BgPrimary @Composable get() = WalletColors.current.bgPrimary
private val BgCard @Composable get() = WalletColors.current.bgCard
private val TextPrimary @Composable get() = WalletColors.current.textPrimary
private val TextSecondary @Composable get() = WalletColors.current.textSecondary
private val TextHint @Composable get() = WalletColors.current.textHint
private val AccentBlue @Composable get() = WalletColors.current.accentBlue
private val AccentGold @Composable get() = WalletColors.current.accentGold
private val AccentPurple @Composable get() = WalletColors.current.accentPurple
private val AccentOrange @Composable get() = WalletColors.current.accentOrange
private val Green @Composable get() = WalletColors.current.accentGreen
private val Red @Composable get() = WalletColors.current.accentRed
private val Border @Composable get() = WalletColors.current.border
private val GraySoft @Composable get() = WalletColors.current.graySoft
private val AmberSoft @Composable get() = WalletColors.current.amberSoft
private val RedSoft @Composable get() = WalletColors.current.redSoft

sealed class WalletScreen {
    object Dashboard : WalletScreen()
    object CreateWallet : WalletScreen()
    object RecoverWallet : WalletScreen()
    object WalletManagement : WalletScreen()
    object ManageNetworkTokens : WalletScreen()
    object ChooseNetworks : WalletScreen()
    data class Send(val account: WalletAccount) : WalletScreen()
    data class SendToken(val token: WalletToken, val fromAddress: String) : WalletScreen()
    data class Receive(val account: WalletAccount) : WalletScreen()
    data class ReceiveToken(val token: WalletToken, val address: String) : WalletScreen()
    object TransactionHistory : WalletScreen()
    data class TransactionDetail(val tx: WalletTransaction) : WalletScreen()
    object Swap : WalletScreen()
    object AddressBookScreen : WalletScreen()
}

private sealed class UnifiedAsset {
    abstract val usdValue: Double
    abstract val key: String

    data class NativeCoin(val account: WalletAccount, override val usdValue: Double) : UnifiedAsset() {
        override val key: String get() = "native_${account.network.name}"
    }
    data class Token(val token: WalletToken, override val usdValue: Double) : UnifiedAsset() {
        override val key: String get() = "token_${token.network.name}_${token.contractAddress}"
    }
}

@Composable
fun WalletView(close: () -> Unit) {
    var currentScreen by remember { mutableStateOf<WalletScreen>(WalletScreen.Dashboard) }
    val serviceState by WalletCoreService.walletStateFlow.collectAsState()
    var walletState by remember { mutableStateOf(serviceState) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Mirror service state into local state so downstream composables stay updated
    LaunchedEffect(serviceState) {
        walletState = serviceState
    }

    BackHandler(enabled = currentScreen != WalletScreen.Dashboard) {
        currentScreen = WalletScreen.Dashboard
    }

    BackHandler(enabled = currentScreen == WalletScreen.Dashboard) {
        close()
    }

    suspend fun refreshBalances() {
        if (WalletCoreService.isWalletInitialized()) {
            WalletCoreService.refreshAllBalances()
        }
    }

    suspend fun refreshTransactions() {
        if (WalletCoreService.isWalletInitialized()) {
            try {
                WalletCoreService.fetchAllTransactions()
            } catch (_: Exception) { }
        }
    }

    var showDecryptionWarning by remember { mutableStateOf(false) }

    // On first open: ensure initialized, trigger a fresh fetch if background
    // hasn't completed yet, and start blockchain watchers.
    LaunchedEffect(Unit) {
        WalletCoreService.initialize(null)

        if (WalletCoreService.isWalletInitialized()) {
            WalletCoreService.startBackgroundRefresh()
        } else if (WalletCoreService.hasPersistedWallet()) {
            showDecryptionWarning = true
        }
    }

    if (showDecryptionWarning) {
        androidx.compose.material.AlertDialog(
            onDismissRequest = { showDecryptionWarning = false },
            title = { Text(stringResource(MR.strings.wallet_recovery_required_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(MR.strings.wallet_recovery_required_message),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                androidx.compose.material.TextButton(
                    onClick = {
                        showDecryptionWarning = false
                        currentScreen = WalletScreen.RecoverWallet
                    }
                ) { Text(stringResource(MR.strings.wallet_recover_dialog_button), color = AccentGold) }
            },
            dismissButton = {
                androidx.compose.material.TextButton(
                    onClick = { showDecryptionWarning = false }
                ) { Text(stringResource(MR.strings.wallet_dismiss_button), color = TextSecondary) }
            }
        )
    }

    // Pull-to-refresh still triggers a manual balance refresh
    LaunchedEffect(walletState.isInitialized) {
        if (walletState.isInitialized) {
            while (true) {
                delay(30_000)
                if (!NetworkStatusService.isConnected.value) continue
                try {
                    refreshBalances()
                    WalletCoreService.updatePendingTransactions()
                } catch (_: Exception) { }
            }
        }
    }

    fun syncWalletState() {
        walletState = WalletCoreService.walletStateFlow.value
    }

    when (val screen = currentScreen) {
        is WalletScreen.Dashboard -> {
            if (walletState.isInitialized) {
                WalletDashboard(
                    walletState = walletState,
                    onSendClick = { account -> currentScreen = WalletScreen.Send(account) },
                    onReceiveClick = { account -> currentScreen = WalletScreen.Receive(account) },
                    onSendTokenClick = { token, address -> currentScreen = WalletScreen.SendToken(token, address) },
                    onReceiveTokenClick = { token, address -> currentScreen = WalletScreen.ReceiveToken(token, address) },
                    onSwapClick = { currentScreen = WalletScreen.Swap },
                    onHistoryClick = { currentScreen = WalletScreen.TransactionHistory },
                    onTransactionClick = { tx -> currentScreen = WalletScreen.TransactionDetail(tx) },
                    onAddressBookClick = { currentScreen = WalletScreen.AddressBookScreen },
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            refreshBalances()
                            refreshTransactions()
                            delay(500)
                            isRefreshing = false
                        }
                    },
                    isRefreshing = isRefreshing,
                    onManageWallets = { currentScreen = WalletScreen.WalletManagement },
                    onManageTokens = { currentScreen = WalletScreen.ManageNetworkTokens },
                    onSwitchWallet = { walletId ->
                        WalletCoreService.switchWallet(walletId)
                        syncWalletState()
                        WalletCoreService.startBackgroundRefresh()
                    },
                    close = close
                )
            } else {
                WalletOnboarding(
                    onCreateWallet = { currentScreen = WalletScreen.CreateWallet },
                    onRecoverWallet = { currentScreen = WalletScreen.RecoverWallet },
                    close = close
                )
            }
        }
        
        is WalletScreen.CreateWallet -> {
            CreateWalletView(
                onWalletCreated = { result ->
                    syncWalletState()
                    NetworkTokenPreferences.reset()
                    currentScreen = WalletScreen.ChooseNetworks
                },
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.RecoverWallet -> {
            RecoverWalletView(
                onWalletRecovered = { result ->
                    syncWalletState()
                    NetworkTokenPreferences.reset()
                    currentScreen = WalletScreen.ChooseNetworks
                },
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.WalletManagement -> {
            WalletManagementView(
                wallets = walletState.wallets,
                activeWalletId = walletState.activeWalletId,
                onSwitchWallet = { walletId ->
                    WalletCoreService.switchWallet(walletId)
                    syncWalletState()
                    WalletCoreService.startBackgroundRefresh()
                },
                onRenameWallet = { walletId, newName ->
                    WalletCoreService.renameWallet(walletId, newName)
                    syncWalletState()
                },
                onDeleteWallet = { walletId ->
                    WalletCoreService.deleteWallet(walletId)
                    syncWalletState()
                    WalletCoreService.startBackgroundRefresh()
                },
                onCreateWallet = { currentScreen = WalletScreen.CreateWallet },
                onImportWallet = { currentScreen = WalletScreen.RecoverWallet },
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }

        is WalletScreen.ManageNetworkTokens -> {
            ManageNetworkTokensView(
                onBack = {
                    NetworkTokenPreferences.saveNow()
                    currentScreen = WalletScreen.Dashboard
                },
                onDone = {
                    NetworkTokenPreferences.saveNow()
                    currentScreen = WalletScreen.Dashboard
                    scope.launch {
                        isRefreshing = true
                        refreshBalances()
                        refreshTransactions()
                        delay(500)
                        isRefreshing = false
                    }
                }
            )
        }

        is WalletScreen.ChooseNetworks -> {
            ChooseNetworksOnboardingView(
                onDone = {
                    NetworkTokenPreferences.saveNow()
                    syncWalletState()
                    currentScreen = WalletScreen.Dashboard
                    WalletCoreService.startBackgroundRefresh()
                }
            )
        }
        
        is WalletScreen.Send -> {
            val sendAccount = screen.account
            SendTransactionView(
                account = sendAccount,
                onTransactionSent = { tx ->
                    // Immediately update the native coin balance (deduct sent amount + fee)
                    val sentAmount = tx.amount.toDoubleOrNull() ?: 0.0
                    val feeAmount = tx.fee.toDoubleOrNull() ?: 0.0
                    val totalDeducted = sentAmount + feeAmount
                    val updatedAccounts = walletState.accounts.map { account ->
                        if (account.id == sendAccount.id) {
                            val currentBalance = account.balance.toDoubleOrNull() ?: 0.0
                            val newBalance = (currentBalance - totalDeducted).coerceAtLeast(0.0)
                            account.copy(balance = String.format("%.8f", newBalance).trimEnd('0').trimEnd('.'))
                        } else {
                            account
                        }
                    }
                    walletState = walletState.copy(
                        transactions = listOf(tx) + walletState.transactions,
                        accounts = updatedAccounts
                    )
                    currentScreen = WalletScreen.TransactionDetail(tx)
                },
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.Receive -> {
            ReceiveView(
                account = screen.account,
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.SendToken -> {
            val sentToken = screen.token
            SendTokenView(
                token = sentToken,
                fromAddress = screen.fromAddress,
                onTransactionSent = { tx ->
                    // Immediately update the token balance (deduct sent amount)
                    val sentAmount = tx.amount.toDoubleOrNull() ?: 0.0
                    val updatedTokens = walletState.tokens.map { token ->
                        if (token.contractAddress == sentToken.contractAddress && token.network == sentToken.network) {
                            val currentBalance = token.balance.toDoubleOrNull() ?: 0.0
                            val newBalance = (currentBalance - sentAmount).coerceAtLeast(0.0)
                            token.copy(balance = String.format("%.${token.decimals}f", newBalance).trimEnd('0').trimEnd('.'))
                        } else {
                            token
                        }
                    }
                    walletState = walletState.copy(
                        transactions = listOf(tx) + walletState.transactions,
                        tokens = updatedTokens
                    )
                    currentScreen = WalletScreen.TransactionDetail(tx)
                },
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.ReceiveToken -> {
            ReceiveTokenView(
                token = screen.token,
                address = screen.address,
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.TransactionHistory -> {
            // Ensure tx history is refreshed when user opens the screen
            LaunchedEffect(Unit) {
                refreshTransactions()
            }
            TransactionHistoryView(
                transactions = walletState.transactions,
                onTransactionClick = { tx -> currentScreen = WalletScreen.TransactionDetail(tx) },
                onBack = { currentScreen = WalletScreen.Dashboard },
                onRefresh = { refreshTransactions() }
            )
        }
        
        is WalletScreen.TransactionDetail -> {
            TransactionDetailView(
                transaction = screen.tx,
                onBack = { currentScreen = WalletScreen.TransactionHistory }
            )
        }
        
        is WalletScreen.Swap -> {
            SwapView(
                accounts = walletState.accounts,
                tokens = walletState.tokens,
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
        
        is WalletScreen.AddressBookScreen -> {
            AddressBookView(
                onAddressSelected = null,
                onBack = { currentScreen = WalletScreen.Dashboard }
            )
        }
    }
}

@Composable
private fun WalletOnboarding(
    onCreateWallet: () -> Unit,
    onRecoverWallet: () -> Unit,
    close: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = close,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(MR.images.ic_arrow_back_ios_new),
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                stringResource(MR.strings.wallet_title),
                fontSize = 20.sp,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(MR.images.uc_wallet_main),
                contentDescription = stringResource(MR.strings.wallet_title),
                modifier = Modifier.size(180.dp)
            )

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WalletInfoChip(icon = Icons.Default.Security, label = stringResource(MR.strings.wallet_chip_self_custodial))
                WalletInfoChip(icon = Icons.Default.Layers, label = stringResource(MR.strings.wallet_chip_multi_chain))
                WalletInfoChip(icon = Icons.Default.Lock, label = stringResource(MR.strings.wallet_chip_secure))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(MR.strings.wallet_onboarding_heading),
                fontSize = 40.sp,
                  fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                lineHeight = 58.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(MR.strings.wallet_onboarding_description),
                fontSize = 18.sp,
              fontFamily = DMSans,
                fontWeight = FontWeight.Normal,
                lineHeight = 30.sp,
                color = TextSecondary,
                textAlign = TextAlign.Left,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onCreateWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 0.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(MR.strings.wallet_create_new_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRecoverWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                border = BorderStroke(1.dp, TextPrimary),
                shape = RoundedCornerShape(360.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = TextPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(MR.strings.wallet_recover_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun WalletInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GraySoft)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
    }
}

@Composable
private fun WalletDashboard(
    walletState: WalletState,
    onSendClick: (WalletAccount) -> Unit,
    onReceiveClick: (WalletAccount) -> Unit,
    onSendTokenClick: (WalletToken, String) -> Unit,
    onReceiveTokenClick: (WalletToken, String) -> Unit,
    onSwapClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onTransactionClick: (WalletTransaction) -> Unit,
    onAddressBookClick: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onManageWallets: () -> Unit,
    onManageTokens: () -> Unit,
    onSwitchWallet: (String) -> Unit,
    close: () -> Unit
) {
    var selectedAccount by remember { mutableStateOf(walletState.accounts.firstOrNull()) }
    var selectedToken by remember { mutableStateOf<WalletToken?>(null) }
    var isBalanceHidden by remember { mutableStateOf(false) }
    var showNetworkStatus by remember { mutableStateOf(false) }
    var showWalletPicker by remember { mutableStateOf(false) }
    var selectedNetwork by remember { mutableStateOf<BlockchainNetwork?>(null) }
    val prices by WalletPriceService.pricesFlow.collectAsState()

    val assetPrefs by NetworkTokenPreferences.prefsFlow.collectAsState()

    val filteredAccounts = remember(walletState.accounts, selectedNetwork, assetPrefs) {
        val base = walletState.accounts.filter { NetworkTokenPreferences.isNetworkEnabled(it.network) }
        if (selectedNetwork == null) base
        else base.filter { it.network == selectedNetwork }
    }
    val filteredTokens = remember(walletState.tokens, selectedNetwork, assetPrefs) {
        val base = walletState.tokens.filter {
            NetworkTokenPreferences.isNetworkEnabled(it.network) &&
                    NetworkTokenPreferences.isTokenEnabled(it.network, it.symbol, it.contractAddress)
        }
        if (selectedNetwork == null) base
        else base.filter { it.network == selectedNetwork }
    }

    // Unified asset list: merge native coins + tokens, sorted by USD value descending
    val unifiedAssets = remember(filteredAccounts, filteredTokens, prices) {
        val nativeAssets = filteredAccounts.map { account ->
            val bal = account.balance.toDoubleOrNull() ?: 0.0
            val sym = account.network.symbol.uppercase()
            val p: Double = prices[sym] ?: WalletPriceService.getPrice(sym)
            UnifiedAsset.NativeCoin(account, bal * p)
        }
        val tokenAssets = filteredTokens.map { token ->
            val bal = token.balance.toDoubleOrNull() ?: 0.0
            val sym = token.symbol.uppercase()
            val p: Double = prices[sym] ?: WalletPriceService.getPrice(sym)
            UnifiedAsset.Token(token, bal * p)
        }
        (nativeAssets + tokenAssets).sortedByDescending { it.usdValue }
    }

    // Available networks for the chip selector — recomputed only when accounts or prefs change.
    val availableNetworks = remember(walletState.accounts, assetPrefs) {
        walletState.accounts
            .filter { NetworkTokenPreferences.isNetworkEnabled(it.network) }
            .map { it.network }
            .distinct()
    }

    // O(1) lookup of native account address per network — avoids walletState.accounts.find() per token row.
    val addressByNetwork = remember(walletState.accounts) {
        walletState.accounts.associate { it.network to it.address }
    }

    // Cap "recent" at 5 once, instead of allocating a new sublist on every recomposition.
    val recentTransactions = remember(walletState.transactions) {
        walletState.transactions.take(5)
    }

    // Total wallet USD value — recomputed only when accounts/tokens/prices change.
    val totalUsdValue = remember(walletState.accounts, walletState.tokens, prices) {
        val nativeUsd = walletState.accounts.sumOf { account ->
            val balance = account.balance.toDoubleOrNull() ?: 0.0
            val symbol = account.network.symbol.uppercase()
            val price: Double = prices[symbol] ?: WalletPriceService.getPrice(symbol)
            balance * price
        }
        val tokensUsd = walletState.tokens.sumOf { token ->
            val balance = token.balance.toDoubleOrNull() ?: 0.0
            val symbol = token.symbol.uppercase()
            val price: Double = prices[symbol] ?: WalletPriceService.getPrice(symbol)
            balance * price
        }
        nativeUsd + tokensUsd
    }

    val totalUsdDisplay = remember(totalUsdValue) {
        when {
            totalUsdValue >= 1_000_000 -> String.format("%.2fM", totalUsdValue / 1_000_000)
            totalUsdValue >= 1_000 -> String.format("%.2fK", totalUsdValue / 1_000)
            totalUsdValue < 0.01 && totalUsdValue > 0 -> String.format("%.6f", totalUsdValue)
            else -> String.format("%.2f", totalUsdValue)
        }
    }

    // Network status monitoring
    val networkStatuses by NetworkStatusService.statusFlow.collectAsState()
    val isConnected by NetworkStatusService.isConnected.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Start network monitoring on first composition
    LaunchedEffect(Unit) {
        NetworkStatusService.checkAllNetworks()
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = close,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(MR.images.ic_arrow_back_ios_new),
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(12.dp))
                
                // Wallet selector - clickable wallet name with dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showWalletPicker = !showWalletPicker }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeWallet = walletState.wallets.find { it.id == walletState.activeWalletId }
                        Text(
                            activeWallet?.name ?: stringResource(MR.strings.wallet_title),
                            fontSize = 20.sp,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (walletState.wallets.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showWalletPicker) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(MR.strings.wallet_switch_wallet_desc),
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Wallet picker dropdown
                    DropdownMenu(
                        expanded = showWalletPicker,
                        onDismissRequest = { showWalletPicker = false },
                        modifier = Modifier
                            .background(BgCard)
                    ) {
                        walletState.wallets.forEach { wallet ->
                            DropdownMenuItem(
                                onClick = {
                                    if (wallet.id != walletState.activeWalletId) {
                                        onSwitchWallet(wallet.id)
                                    }
                                    showWalletPicker = false
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (wallet.id == walletState.activeWalletId)
                                                    AccentBlue.copy(alpha = 0.1f)
                                                else GraySoft
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = if (wallet.id == walletState.activeWalletId)
                                                AccentBlue else TextHint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            wallet.name,
                                            fontSize = 15.sp,
                                            fontWeight = if (wallet.id == walletState.activeWalletId) FontWeight.SemiBold else FontWeight.Normal,
                                            color = TextPrimary
                                        )
                                    }
                                    if (wallet.id == walletState.activeWalletId) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(MR.strings.wallet_active_desc),
                                            tint = AccentBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Divider(color = Border)
                        
                        // Manage wallets option
                        DropdownMenuItem(onClick = {
                            showWalletPicker = false
                            onManageWallets()
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = TextHint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(MR.strings.wallet_manage_wallets),
                                    fontSize = 15.sp,
                                    color = TextHint
                                )
                            }
                        }
                    }
                }
                
                // Network Status Indicator
                Spacer(Modifier.width(8.dp))
                NetworkStatusIndicator(
                    isConnected = isConnected,
                    networkStatuses = networkStatuses,
                    onClick = { showNetworkStatus = !showNetworkStatus }
                )
                
                Spacer(Modifier.weight(1f))
                
                // Refresh button
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AccentBlue
                        )
                    } else {
                        Icon(Icons.Default.Refresh, stringResource(MR.strings.wallet_refresh_desc), tint = TextSecondary)
                    }
                }
                IconButton(onClick = { /* Settings */ }) {
                    Icon(Icons.Default.Settings, null, tint = TextSecondary)
                }
            }
            
            // Expandable Network Status Panel
            AnimatedVisibility(visible = showNetworkStatus) {
                NetworkStatusPanel(
                    networkStatuses = networkStatuses,
                    onRefresh = { 
                        scope.launch { 
                            NetworkStatusService.checkAllNetworks() 
                        }
                    }
                )
            }
        }
        
        // Credit Card Style Balance
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Credit Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1A1F2C),
                                    Color(0xFF2D3748),
                                    Color(0xFF1A1F2C)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        )
                ) {
                    // Card pattern overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Subtle circles for card design
                        drawCircle(
                            color = Color.White.copy(alpha = 0.03f),
                            radius = 200f,
                            center = Offset(size.width * 0.8f, size.height * 0.2f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = 150f,
                            center = Offset(size.width * 0.9f, size.height * 0.3f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.02f),
                            radius = 300f,
                            center = Offset(size.width * 0.1f, size.height * 1.2f)
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Card Header with Logo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Text Logo
                            Image(
                                painter = painterResource(MR.images.ic_logo),
                                contentDescription = "Logo",
                                modifier = Modifier.height(22.dp),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                            // Chip icon
                            Box(
                                modifier = Modifier
                                    .size(width = 45.dp, height = 32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFD4AF37),
                                                Color(0xFFF4D03F),
                                                Color(0xFFD4AF37)
                                            )
                                        )
                                    )
                            ) {
                                // Chip lines
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    repeat(4) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .background(Color(0xFFB8860B).copy(0.5f))
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Total Balance label with hide/unhide
                        Row(
                            verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(MR.strings.wallet_total_balance_label),
                                fontSize = 12.sp,
                                color = Color.White.copy(0.6f),
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { isBalanceHidden = !isBalanceHidden },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isBalanceHidden) stringResource(MR.strings.wallet_show_balance_desc) else stringResource(MR.strings.wallet_hide_balance_desc),
                                    tint = Color.White.copy(0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(4.dp))
                
                Text(
                            if (isBalanceHidden) "$ ••••••" else "\$$totalUsdDisplay",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(0.3f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        )

                        if (walletState.isUpdatingBalances) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White.copy(0.7f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (walletState.isCachedData) stringResource(MR.strings.wallet_updating_balances) else stringResource(MR.strings.wallet_refreshing),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(0.6f)
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))
                
                        // Wallet name (like cardholder name)
                        val activeWallet = walletState.wallets.find { it.id == walletState.activeWalletId }
                        Text(
                            activeWallet?.name ?: stringResource(MR.strings.wallet_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(0.9f),
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Networks count
                        Row(
                    modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    stringResource(MR.strings.wallet_networks_label),
                                    fontSize = 9.sp,
                                    color = Color.White.copy(0.5f),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "${walletState.accounts.size} chains",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(0.9f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(MR.strings.wallet_assets_label),
                                    fontSize = 9.sp,
                                    color = Color.White.copy(0.5f),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "${walletState.accounts.size + walletState.tokens.size} total",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Quick Action Buttons - Row 1
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                        icon = Icons.Default.CallMade,
                        label = stringResource(MR.strings.wallet_action_send),
                    color = AccentBlue,
                    modifier = Modifier.weight(1f),
                        onClick = { selectedAccount?.let { onSendClick(it) } }
                    )
                QuickActionCard(
                        icon = Icons.Default.CallReceived,
                        label = stringResource(MR.strings.wallet_action_receive),
                    color = Green,
                    modifier = Modifier.weight(1f),
                        onClick = { selectedAccount?.let { onReceiveClick(it) } }
                    )
                QuickActionCard(
                    icon = Icons.Default.SwapHoriz,
                    label = stringResource(MR.strings.wallet_action_swap),
                    color = AccentPurple,
                    modifier = Modifier.weight(1f),
                    onClick = onSwapClick
                )
                QuickActionCard(
                    icon = Icons.Default.History,
                    label = stringResource(MR.strings.wallet_action_history),
                    color = AccentOrange,
                    modifier = Modifier.weight(1f),
                    onClick = onHistoryClick
                )
            }
            Spacer(Modifier.height(10.dp))
            }
        
        // ── Network Chip Selector ────────────────────────────────────
        item {
            Spacer(Modifier.height(6.dp))
            NetworkChipSelector(
                networks = availableNetworks,
                selectedNetwork = selectedNetwork,
                onNetworkSelected = { net ->
                    selectedNetwork = net
                    selectedToken = null
                }
            )
        }

        // Assets header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(MR.strings.wallet_assets_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${unifiedAssets.size} assets",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier.clickable { onManageTokens() },
                        shape = RoundedCornerShape(8.dp),
                        color = AccentGold.copy(alpha = 0.12f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Settings, stringResource(MR.strings.wallet_manage_label), tint = AccentGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(MR.strings.wallet_manage_label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentGold)
                        }
                    }
                }
            }
        }

        // Unified asset list sorted by USD value
        items(unifiedAssets, key = { it.key }) { asset ->
            when (asset) {
                is UnifiedAsset.NativeCoin -> AssetItem(
                    account = asset.account,
                    prices = prices,
                    isSelected = selectedAccount?.id == asset.account.id,
                    onClick = { selectedAccount = asset.account },
                    onSendClick = { onSendClick(asset.account) },
                    onReceiveClick = { onReceiveClick(asset.account) }
                )
                is UnifiedAsset.Token -> {
                    val walletAddress = addressByNetwork[asset.token.network] ?: ""
                    TokenItem(
                        token = asset.token,
                        prices = prices,
                        isSelected = selectedToken?.contractAddress == asset.token.contractAddress && selectedToken?.network == asset.token.network,
                        onClick = { selectedToken = if (selectedToken?.contractAddress == asset.token.contractAddress) null else asset.token },
                        onSendClick = { onSendTokenClick(asset.token, walletAddress) },
                        onReceiveClick = { onReceiveTokenClick(asset.token, walletAddress) }
                    )
                }
            }
        }
        
        // Recent transactions
        if (walletState.transactions.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(MR.strings.wallet_recent_transactions_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = onHistoryClick) {
                        Text(stringResource(MR.strings.wallet_see_all), fontSize = 13.sp, color = AccentGold)
                    }
                }
            }
            
            items(recentTransactions, key = { it.txHash }) { tx ->
                RecentTransactionItem(
                    transaction = tx,
                    onClick = { onTransactionClick(tx) }
                )
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Network Chip Selector (horizontal scrollable) ──────────────────

@Composable
private fun NetworkChipSelector(
    networks: List<BlockchainNetwork>,
    selectedNetwork: BlockchainNetwork?,
    onNetworkSelected: (BlockchainNetwork?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        item {
            NetworkChip(
                label = stringResource(MR.strings.wallet_filter_all),
                icon = null,
                isSelected = selectedNetwork == null,
                onClick = { onNetworkSelected(null) }
            )
        }
        items(networks) { network ->
            NetworkChip(
                label = network.symbol,
                icon = getNetworkIcon(network),
                isSelected = selectedNetwork == network,
                onClick = { onNetworkSelected(network) }
            )
        }
    }
}

@Composable
private fun NetworkChip(
    label: String,
    icon: ImageResource?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) TextPrimary else BgCard
    val contentColor = if (isSelected) BgCard else TextPrimary
    val borderColor = if (isSelected) Color.Transparent else Border

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = 4.dp,
        backgroundColor = BgCard
) {
    Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                    .size(34.dp)
                .clip(CircleShape)
                    .background(color.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
        }
        Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun AssetItem(
    account: WalletAccount,
    prices: Map<String, Double>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    val networkIcon = remember(account.network) { getNetworkIcon(account.network) }

    // Memoize the per-row USD value + formatted display so price-tick recompositions don't
    // re-run String.format() and HashMap lookups for every visible row.
    val usdDisplay = remember(account.balance, account.network.symbol, prices) {
        val balanceDouble = account.balance.toDoubleOrNull() ?: 0.0
        val symbol = account.network.symbol.uppercase()
        val coinPrice: Double = prices[symbol] ?: WalletPriceService.getPrice(symbol)
        val usdValue = balanceDouble * coinPrice
        if (usdValue < 0.01 && usdValue > 0) String.format("%.4f", usdValue)
        else String.format("%.2f", usdValue)
    }

    val shortAddress = remember(account.address) {
        account.address.take(8) + "..." + account.address.takeLast(6)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) AccentGold.copy(0.05f) else BgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Network icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GraySoft),
                contentAlignment = Alignment.Center
            ) {
                if (networkIcon != null) {
                    Image(
                        painter = painterResource(networkIcon),
                        contentDescription = account.network.symbol,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                } else {
                Text(
                    account.network.symbol.take(1),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    account.network.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                    Spacer(Modifier.width(6.dp))
                    // Native coin tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AccentBlue.copy(0.15f)
                    ) {
                        Text(
                            stringResource(MR.strings.wallet_native_tag),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentBlue,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    shortAddress,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${displayBalance(account.balance, account.network.symbol)} ${account.network.symbol}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    "~\$$usdDisplay",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        
        if (isSelected) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onSendClick,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, AccentGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CallMade, null, tint = AccentGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(MR.strings.wallet_action_send), color = AccentGold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onReceiveClick,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Green),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CallReceived, null, tint = Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(MR.strings.wallet_action_receive), color = Green, fontSize = 13.sp)
                }
            }
        }
    }
    
    Divider(color = Border)
}

@Composable
private fun TokenItem(
    token: WalletToken,
    prices: Map<String, Double>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    val tokenIcon = remember(token.symbol) { getTokenIcon(token.symbol) }
    val networkIcon = remember(token.network) { getNetworkIcon(token.network) }

    val tokenStandard = remember(token.network) {
        when (token.network) {
            BlockchainNetwork.ETHEREUM -> "ERC20"
            BlockchainNetwork.BINANCE_SMART_CHAIN -> "BEP20"
            BlockchainNetwork.POLYGON -> "ERC20"
            BlockchainNetwork.ARBITRUM -> "ERC20"
            BlockchainNetwork.OPTIMISM -> "ERC20"
            BlockchainNetwork.AVALANCHE -> "ERC20"
            BlockchainNetwork.BASE -> "ERC20"
            else -> "TOKEN"
        }
    }

    val standardColor = remember(tokenStandard) {
        when (tokenStandard) {
            "ERC20" -> Color(0xFF627EEA)
            "BEP20" -> Color(0xFFF0B90B)
            else -> Color(0xFF9E9E9E)
        }
    }

    // Memoize the per-row USD value so price-tick recompositions don't re-run String.format()
    // for every visible token row.
    val usdDisplay = remember(token.balance, token.symbol, prices) {
        val balanceDouble = token.balance.toDoubleOrNull() ?: 0.0
        val symbol = token.symbol.uppercase()
        val tokenPrice: Double = prices[symbol] ?: WalletPriceService.getPrice(symbol)
        val usdValue = balanceDouble * tokenPrice
        if (usdValue < 0.01 && usdValue > 0) String.format("%.4f", usdValue)
        else String.format("%.2f", usdValue)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) AccentGold.copy(0.05f) else BgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Token icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GraySoft),
                contentAlignment = Alignment.Center
            ) {
                if (tokenIcon != null) {
                    Image(
                        painter = painterResource(tokenIcon),
                        contentDescription = token.symbol,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                } else {
                    // Fallback to letter if no icon
                    Text(
                        token.symbol.take(2),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (token.symbol) {
                            "USDT" -> Color(0xFF26A17B)
                            "USDC" -> Color(0xFF2775CA)
                            "DAI" -> Color(0xFFF5AC37)
                            else -> AccentGold
                        }
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        token.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    // Token standard tag (ERC20/BEP20)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = standardColor.copy(0.15f)
                    ) {
                        Text(
                            tokenStandard,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = standardColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        token.symbol,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        " • ",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    if (networkIcon != null) {
                        Image(
                            painter = painterResource(networkIcon),
                            contentDescription = token.network.displayName,
                            modifier = Modifier.size(12.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        token.network.displayName,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${displayBalance(token.balance, token.symbol)} ${token.symbol}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    "~\$$usdDisplay",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        
        // Send/Receive buttons when selected
        if (isSelected) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onSendClick,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, AccentGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CallMade, null, tint = AccentGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(MR.strings.wallet_action_send), color = AccentGold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onReceiveClick,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Green),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CallReceived, null, tint = Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(MR.strings.wallet_action_receive), color = Green, fontSize = 13.sp)
                }
            }
        }
    }
    Divider(color = Border)
}

@Composable
private fun RecentTransactionItem(
    transaction: WalletTransaction,
    onClick: () -> Unit
) {
    // Check if this is an outgoing transaction
    val isOutgoing = transaction.type == TransactionType.SEND
    
    // Determine what symbol to show - token symbol if present, otherwise network symbol
    val displaySymbol = transaction.tokenSymbol ?: transaction.network.symbol
    val isTokenTransfer = transaction.tokenSymbol != null
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(BgCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) Red.copy(0.1f) else Green.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isOutgoing) Icons.Default.CallMade else Icons.Default.CallReceived,
                null,
                tint = if (isOutgoing) Red else Green,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(Modifier.weight(1f)) {
            Text(
                if (isOutgoing) stringResource(MR.strings.wallet_sent_symbol, displaySymbol) else stringResource(MR.strings.wallet_received_symbol, displaySymbol),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                if (isTokenTransfer) "${transaction.network.displayName} • ${stringResource(MR.strings.wallet_token_label)}" else transaction.network.displayName,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
        Text(
                "${if (isOutgoing) "-" else "+"}${transaction.amount} $displaySymbol",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isOutgoing) Red else Green
        )
            // Show status
            Text(
                when (transaction.status) {
                    TransactionStatus.PENDING -> stringResource(MR.strings.wallet_status_pending)
                    TransactionStatus.CONFIRMED -> stringResource(MR.strings.wallet_status_confirmed)
                    TransactionStatus.FAILED -> stringResource(MR.strings.wallet_status_failed)
                    TransactionStatus.CANCELLED -> stringResource(MR.strings.wallet_status_cancelled)
                },
                fontSize = 11.sp,
                color = when (transaction.status) {
                    TransactionStatus.PENDING -> AccentGold
                    TransactionStatus.CONFIRMED -> Green
                    TransactionStatus.FAILED -> Red
                    TransactionStatus.CANCELLED -> TextSecondary
                }
            )
        }
    }
    
    Divider(color = Border, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun SendTokenView(
    token: WalletToken,
    fromAddress: String,
    onTransactionSent: (WalletTransaction) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val prices by WalletPriceService.pricesFlow.collectAsState()
    var recipientAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var estimatedFee by remember { mutableStateOf<String?>(null) }
    var estimatedFeeUsd by remember { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    
    val tokenIcon = getTokenIcon(token.symbol)
    val networkIcon = getNetworkIcon(token.network)
    
    val addressValidation = remember(recipientAddress) {
        if (recipientAddress.isBlank()) null
        else WalletCoreService.validateAddress(recipientAddress, token.network)
    }
    
    val isValidAmount = amount.toDoubleOrNull()?.let { it > 0 } ?: false
    val isValidAddress = addressValidation?.isValid == true
    val canSend = isValidAddress && isValidAmount && !isSending
    
    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val tokenSymbol = token.symbol.uppercase()
    val tokenPrice: Double = prices[tokenSymbol] ?: WalletPriceService.getPrice(tokenSymbol)
    val amountUsd = amountDouble * tokenPrice
    
    LaunchedEffect(token.network) {
        try {
            val fee = WalletCoreService.estimateFee(token.network)
            val gasPriceGwei = fee.normal.gasPrice.toDoubleOrNull() ?: 20.0
            val gasLimit = 65000L
            val feeNative = (gasPriceGwei * gasLimit) / 1_000_000_000.0
            estimatedFee = String.format("%.6f", feeNative)
            estimatedFeeUsd = fee.normal.estimatedFeeUsd
        } catch (_: Exception) {
            estimatedFee = "0.001"
            estimatedFeeUsd = null
        }
    }
    
    // QR Scanner Dialog
    if (showQRScanner) {
        AlertDialog(
            onDismissRequest = { showQRScanner = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = AccentGold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(MR.strings.wallet_scan_qr_code), fontWeight = FontWeight.Bold, color = TextPrimary)
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
                    Text(stringResource(MR.strings.wallet_cancel_button), color = AccentGold)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = BgCard
        )
    }
    
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = {
                Text(
                    stringResource(MR.strings.wallet_confirm_token_transfer),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_token_label), fontSize = 13.sp, color = TextSecondary)
                        Text("${token.symbol} (${token.name})", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_to_label), fontSize = 13.sp, color = TextSecondary)
                        Text(recipientAddress.take(10) + "..." + recipientAddress.takeLast(6), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_amount_label), fontSize = 13.sp, color = TextSecondary)
                        Text("$amount ${token.symbol}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_network_fee_label), fontSize = 13.sp, color = TextSecondary)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("~${estimatedFee ?: "0.001"} ${token.network.symbol}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            estimatedFeeUsd?.let { usd ->
                                Text("≈ $${String.format("%.4f", usd)}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_network_label), fontSize = 13.sp, color = TextSecondary)
                        Text(token.network.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider(color = Border)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_total_value_label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        val totalUsd = amountUsd + (estimatedFeeUsd ?: 0.0)
                        Text("≈ $${String.format("%,.2f", totalUsd)} USD", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doTokenSend = {
                            scope.launch {
                                isSending = true
                                showConfirmation = false

                                WalletCoreService.sendTokenTransaction(token, recipientAddress, amount)
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
                        val isLockEnabled = ChatController.appPrefs.performLA.get()
                        if (isLockEnabled) {
                            val laMode = ChatController.appPrefs.laMode.get()
                            authenticate(
                                promptTitle = if (laMode == LAMode.SYSTEM) generalGetString(MR.strings.wallet_confirm_token_transfer) else generalGetString(MR.strings.wallet_enter_passcode),
                                promptSubtitle = generalGetString(MR.strings.wallet_auth_send_subtitle),
                                selfDestruct = false,
                                usingLAMode = laMode,
                                oneTime = true
                            ) { result ->
                                if (result is LAResult.Success) doTokenSend()
                            }
                        } else {
                            doTokenSend()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(MR.strings.wallet_confirm_button), color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmation = false },
                    border = BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(MR.strings.wallet_cancel_button), color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = BgCard
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
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(MR.strings.wallet_send_symbol_title, token.symbol),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "${token.name} • ${token.network.displayName}",
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
                .padding(16.dp)
        ) {
            // Token info card
            Text(stringResource(MR.strings.wallet_token_label), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GraySoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (tokenIcon != null) {
                        Image(
                            painter = painterResource(tokenIcon),
                            contentDescription = token.symbol,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                    } else {
                        Text(
                            token.symbol.take(2),
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        token.symbol,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (networkIcon != null) {
                            Image(
                                painter = painterResource(networkIcon),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            token.network.displayName,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${displayBalance(token.balance, token.symbol)} ${token.symbol}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(MR.strings.wallet_balance_label),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Recipient address
            Text(stringResource(MR.strings.wallet_recipient_address_label), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
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
                        // Paste button - WORKING
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
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // QR Scanner button
                        IconButton(onClick = { showQRScanner = true }) {
                            Icon(
                                Icons.Default.QrCodeScanner, 
                                stringResource(MR.strings.wallet_scan_qr_code),
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = BgCard,
                    focusedBorderColor = if (addressValidation?.isValid == true) Green else AccentGold,
                    unfocusedBorderColor = if (addressValidation?.isValid == true) Green else Border,
                    errorBorderColor = Red,
                    textColor = TextPrimary,
                    cursorColor = AccentGold
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
                        null,
                        tint = if (addressValidation?.isValid == true) Green else Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (addressValidation?.isValid == true) stringResource(MR.strings.wallet_valid_address_desc, token.network.symbol) 
                        else stringResource(MR.strings.wallet_invalid_address_desc),
                        fontSize = 12.sp,
                        color = if (addressValidation?.isValid == true) Green else Red
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Amount
            Text(stringResource(MR.strings.wallet_amount_label), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = InputValidator.sanitizeAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0.00", color = TextSecondary) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            token.symbol,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { amount = token.balance }) {
                            Text(stringResource(MR.strings.wallet_max_button), fontSize = 12.sp, color = AccentGold)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = BgCard,
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = Border,
                    textColor = TextPrimary,
                    cursorColor = AccentGold
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
                        null,
                        tint = Green,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "≈ $${String.format("%,.2f", amountUsd)} USD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Green
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Network fee info
            Text(stringResource(MR.strings.wallet_network_fee_label), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(MR.strings.wallet_estimated_fee),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(MR.strings.wallet_paid_in, token.network.symbol),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "~${estimatedFee ?: "..."} ${token.network.symbol}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    estimatedFeeUsd?.let { usd ->
                        Text(
                            "≈ $${String.format("%.4f", usd)}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Red.copy(0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = Red, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage!!, fontSize = 13.sp, color = Red)
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Send button
            Button(
                onClick = { showConfirmation = true },
                enabled = canSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentGold,
                    disabledBackgroundColor = Border
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        null,
                        tint = if (canSend) TextPrimary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(MR.strings.wallet_send_symbol_title, token.symbol),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) TextPrimary else TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReceiveTokenView(
    token: WalletToken,
    address: String,
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    val tokenIcon = getTokenIcon(token.symbol)
    val networkIcon = getNetworkIcon(token.network)
    
    // Generate real QR code
    val qrCodeBitmap = remember(address) {
        try {
            qrCodeBitmap(address, 512, QrCode.ErrorLevel.M)
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
                    stringResource(MR.strings.wallet_receive_symbol_title, token.symbol),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "${token.name} • ${token.network.displayName}",
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            
            // Token icon with network badge
            Box(
                modifier = Modifier.size(80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GraySoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (tokenIcon != null) {
                        Image(
                            painter = painterResource(tokenIcon),
                            contentDescription = token.symbol,
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                        )
                    } else {
                        Text(
                            token.symbol.take(2),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }
                }
                // Network badge
                if (networkIcon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(2.dp, BgCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(networkIcon),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp).clip(CircleShape)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                stringResource(MR.strings.wallet_receive_symbol_title, token.symbol),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Network info badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AccentGold.copy(0.15f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${token.network.displayName} Network",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentGold
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Real QR Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = BgCard,
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(MR.strings.wallet_scan_to_receive, token.symbol),
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Real QR Code
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(BgCard, RoundedCornerShape(12.dp))
                            .border(2.dp, Border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrCodeBitmap != null) {
                            Image(
                                bitmap = qrCodeBitmap,
                                contentDescription = "QR Code for $address",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.QrCode,
                                    null,
                                    modifier = Modifier.size(80.dp),
                                    tint = TextSecondary
                                )
                                Text(stringResource(MR.strings.wallet_qr_code), fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // Address
                    Text(
                        stringResource(MR.strings.wallet_your_address_label, token.symbol),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgPrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                address,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(14.dp),
                                lineHeight = 18.sp
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
                        clipboardManager.setText(AnnotatedString(address))
                        isCopied = true
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isCopied) Green else AccentGold
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        null,
                        tint = if (isCopied) Color.White else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isCopied) stringResource(MR.strings.wallet_copied) else stringResource(MR.strings.wallet_copy_address_button),
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCopied) Color.White else TextPrimary
                    )
                }
                
                // Share button
                OutlinedButton(
                    onClick = { 
                        clipboardManager.shareText(address)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    border = BorderStroke(1.5.dp, AccentGold),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        null,
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(MR.strings.wallet_share_button),
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGold
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Warning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentGold.copy(0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Warning,
                    null,
                    tint = AccentGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(MR.strings.wallet_important_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        stringResource(MR.strings.wallet_receive_token_warning, token.symbol, token.name, token.network.displayName),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Copy button
            Button(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(address))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(MR.strings.wallet_copy_address_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

// ── Helper: resolve icon for any asset (native coin or token) ──────
@Composable
private fun AssetIcon(asset: Any?, size: Int = 32, fallbackColor: Color = Color(0xFF8B5CF6)) {
    val symbol = getAssetSymbol(asset)
    val icon = getTokenIcon(symbol)
    val networkIcon = when (asset) {
        is WalletAccount -> getNetworkIcon(asset.network)
        is WalletToken -> getNetworkIcon(asset.network)
        else -> null
    }
    val resolved = icon ?: networkIcon

    if (resolved != null) {
        Image(
            painter = painterResource(resolved),
            contentDescription = symbol,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(fallbackColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol.take(1),
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.4).sp,
                color = fallbackColor
            )
        }
    }
}

// ── Swap View (Professional-grade) ─────────────────────────────────

private enum class SwapStep { INPUT, REVIEW, EXECUTING, RESULT }

private val SwapPurple @Composable get() = WalletColors.current.accentPurple
private val CrossChainBlue @Composable get() = WalletColors.current.accentBlue
private val WarningOrange @Composable get() = WalletColors.current.accentOrange
private val WarningRed @Composable get() = WalletColors.current.accentRed

@Composable
private fun SwapView(
    accounts: List<WalletAccount>,
    tokens: List<WalletToken>,
    onBack: () -> Unit
) {
    var fromToken by remember { mutableStateOf<Any?>(accounts.firstOrNull()) }
    var toToken by remember { mutableStateOf<Any?>(null) }
    var fromAmount by remember { mutableStateOf("") }
    var toAmount by remember { mutableStateOf("") }
    var showFromTokenPicker by remember { mutableStateOf(false) }
    var showToTokenPicker by remember { mutableStateOf(false) }
    var slippage by remember { mutableStateOf("0.5") }
    var showSlippageSettings by remember { mutableStateOf(false) }
    var customSlippage by remember { mutableStateOf("") }
    val prices by WalletPriceService.pricesFlow.collectAsState()

    var destNetwork by remember { mutableStateOf<BlockchainNetwork?>(null) }
    val fromNetwork = getAssetNetwork(fromToken)
    val toNetwork = getAssetNetwork(toToken)
    val isCrossChain = fromNetwork != null && toNetwork != null && fromNetwork != toNetwork

    LaunchedEffect(fromToken, toToken) {
        val fn = getAssetNetwork(fromToken)
        val tn = getAssetNetwork(toToken)
        destNetwork = if (fn != null && tn != null && fn != tn) tn else null
    }

    val fromAmountDouble = fromAmount.toDoubleOrNull() ?: 0.0
    val fromSymbol = getAssetSymbol(fromToken).uppercase()
    val fromPriceCached: Double = if (fromSymbol.isNotBlank()) (prices[fromSymbol] ?: WalletPriceService.getPrice(fromSymbol)) else 0.0
    val fromAmountUsdRaw = fromAmountDouble * fromPriceCached

    val toAmountDouble = toAmount.toDoubleOrNull() ?: 0.0
    val toSymbolStr = getAssetSymbol(toToken).uppercase()
    val toPriceCached: Double = if (toSymbolStr.isNotBlank()) (prices[toSymbolStr] ?: WalletPriceService.getPrice(toSymbolStr)) else 0.0
    val toAmountUsdRaw = toAmountDouble * toPriceCached

    val fromAmountUsd: Double
    val toAmountUsd: Double
    if (fromPriceCached > 0 && toPriceCached > 0) {
        fromAmountUsd = fromAmountUsdRaw
        toAmountUsd = toAmountUsdRaw
    } else if (fromPriceCached > 0 && toAmountDouble > 0 && fromAmountDouble > 0) {
        fromAmountUsd = fromAmountUsdRaw
        toAmountUsd = fromAmountUsdRaw
    } else if (toPriceCached > 0 && fromAmountDouble > 0 && toAmountDouble > 0) {
        toAmountUsd = toAmountUsdRaw
        fromAmountUsd = toAmountUsdRaw
    } else {
        fromAmountUsd = fromAmountUsdRaw
        toAmountUsd = toAmountUsdRaw
    }

    var isLoading by remember { mutableStateOf(false) }
    var allQuotes by remember { mutableStateOf<List<SwapQuote>>(emptyList()) }
    var selectedQuote by remember { mutableStateOf<SwapQuote?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var swapStep by remember { mutableStateOf(SwapStep.INPUT) }
    var executionPhase by remember { mutableStateOf("") }
    var swapResultSuccess by remember { mutableStateOf(false) }
    var swapResultTxHash by remember { mutableStateOf<String?>(null) }
    var swapResultError by remember { mutableStateOf<String?>(null) }

    var quoteTimestamp by remember { mutableStateOf(0L) }
    var quoteCountdown by remember { mutableStateOf(0) }

    val pendingSwaps by SwapManager.pendingSwaps.collectAsState()

    LaunchedEffect(Unit) {
        if (!WalletPriceService.hasPrices()) {
            WalletPriceService.refreshPrices()
        }
    }

    fun getCurrentNetwork(): BlockchainNetwork = when (fromToken) {
        is WalletAccount -> (fromToken as WalletAccount).network
        is WalletToken -> (fromToken as WalletToken).network
        else -> BlockchainNetwork.ETHEREUM
    }

    fun getFromAddress(): String = when (fromToken) {
        is WalletAccount -> (fromToken as WalletAccount).address
        is WalletToken -> accounts.find { it.network == (fromToken as WalletToken).network }?.address ?: ""
        else -> ""
    }

    fun getDestAddress(): String? {
        val dstNet = destNetwork ?: getAssetNetwork(toToken) ?: return null
        val srcNet = getCurrentNetwork()
        if (dstNet == srcNet) return getFromAddress()
        return accounts.find { it.network == dstNet }?.address
    }

    fun validate(): String? {
        val amount = fromAmount.toDoubleOrNull() ?: return generalGetString(MR.strings.wallet_swap_enter_amount)
        if (amount <= 0) return generalGetString(MR.strings.wallet_swap_amount_positive)
        val balance = getAssetBalance(fromToken).toDoubleOrNull() ?: 0.0
        if (amount > balance) return generalGetString(MR.strings.wallet_swap_insufficient_balance)
        if (toToken == null) return generalGetString(MR.strings.wallet_swap_select_receive_token)
        if (selectedQuote == null) return generalGetString(MR.strings.wallet_swap_no_quote)
        val slip = slippage.toDoubleOrNull() ?: 0.5
        if (slip < 0.01 || slip > 50.0) return generalGetString(MR.strings.wallet_swap_slippage_range)
        if (getFromAddress().isBlank()) return generalGetString(MR.strings.wallet_swap_address_unavailable)
        if (isCrossChain && selectedQuote?.provider == "ChangeNOW" && getDestAddress() == null) {
            val dstNet = destNetwork ?: getAssetNetwork(toToken)
            return "No ${dstNet?.displayName ?: "destination"} wallet found — add one to receive swapped tokens"
        }
        return null
    }

    // Quote countdown timer
    LaunchedEffect(quoteTimestamp) {
        if (quoteTimestamp > 0 && selectedQuote != null) {
            val expiry = selectedQuote!!.expiresAt
            while (true) {
                val remaining = ((expiry - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                quoteCountdown = remaining
                if (remaining <= 0) break
                delay(1000)
            }
        }
    }

    // Quote fetching
    LaunchedEffect(fromAmount, fromToken, toToken, destNetwork) {
        validationError = null
        if (fromAmount.isNotEmpty() && fromToken != null && toToken != null) {
            val amount = fromAmount.toDoubleOrNull()
            if (amount != null && amount > 0) {
                isLoading = true
                errorMessage = null
                allQuotes = emptyList()
                selectedQuote = null
                quoteCountdown = 0
                delay(400)

                try {
                    val quotes = SwapService.getAllQuotes(
                        network = getCurrentNetwork(),
                        fromToken = getAssetSymbol(fromToken),
                        toToken = getAssetSymbol(toToken),
                        amount = fromAmount,
                        fromAddress = getFromAddress(),
                        destNetwork = destNetwork,
                        destinationAddress = getDestAddress()
                    ).sortedByDescending { it.exchangeRate }

                    allQuotes = quotes
                    val best = quotes.firstOrNull()
                    selectedQuote = best
                    toAmount = best?.toAmount ?: ""
                    quoteTimestamp = System.currentTimeMillis()
                    if (quotes.isEmpty()) {
                        val fSym = getAssetSymbol(fromToken)
                        val tSym = getAssetSymbol(toToken)
                        errorMessage = if (isCrossChain) {
                            val fn = fromNetwork?.displayName ?: "?"
                            val tn = toNetwork?.displayName ?: "?"
                            "No cross-chain route found for $fSym ($fn) → $tSym ($tn). " +
                                "Not all chain pairs are supported for cross-chain swaps."
                        } else {
                            "No route found for $fSym → $tSym on this network"
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = humanReadableError(e.message)
                }
                isLoading = false
            }
        } else {
            toAmount = ""
            allQuotes = emptyList()
            selectedQuote = null
            quoteCountdown = 0
        }
    }

    val allAssets = remember(accounts, tokens) {
        accounts.map { SwapAssetItem(it.network.symbol, it.network.displayName, it.balance, true, it) } +
            tokens.map { SwapAssetItem(it.symbol, it.name, it.balance, false, it) }
    }

    val crossChainDestAssets = remember(accounts, tokens) {
        val existingSymbolsPerNetwork = mutableMapOf<BlockchainNetwork, MutableSet<String>>()
        for (acct in accounts) {
            existingSymbolsPerNetwork.getOrPut(acct.network) { mutableSetOf() }.add(acct.network.symbol.uppercase())
        }
        for (tok in tokens) {
            existingSymbolsPerNetwork.getOrPut(tok.network) { mutableSetOf() }.add(tok.symbol.uppercase())
        }

        val extras = mutableListOf<SwapAssetItem>()
        for (net in BlockchainNetwork.ALL_SUPPORTED) {
            val existing = existingSymbolsPerNetwork[net] ?: emptySet()
            if (net.symbol.uppercase() !in existing) {
                val placeholder = WalletAccount(
                    id = "cross_${net.name}",
                    name = net.displayName,
                    network = net,
                    address = "",
                    publicKey = "",
                    balance = "0",
                    balanceUsd = 0.0
                )
                extras.add(SwapAssetItem(net.symbol, net.displayName, "0", true, placeholder))
            }
            val supportedTokens = SwapService.getSupportedTokens(net)
            for (tok in supportedTokens) {
                if (tok.address == "NATIVE" || tok.address == "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE") continue
                if (tok.symbol.uppercase() !in existing && tok.symbol.uppercase() != net.symbol.uppercase()) {
                    val placeholder = WalletToken(
                        contractAddress = tok.address,
                        network = net,
                        symbol = tok.symbol,
                        name = tok.name,
                        decimals = tok.decimals,
                        balance = "0"
                    )
                    extras.add(SwapAssetItem(tok.symbol, "${tok.name} (${net.displayName})", "0", false, placeholder))
                }
            }
        }
        allAssets + extras
    }

    BackHandler(swapStep != SwapStep.INPUT) {
        if (swapStep == SwapStep.RESULT) {
            swapStep = SwapStep.INPUT
            swapResultSuccess = false; swapResultTxHash = null; swapResultError = null
        } else if (swapStep == SwapStep.REVIEW) {
            swapStep = SwapStep.INPUT
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
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (swapStep == SwapStep.INPUT) onBack()
                else { swapStep = SwapStep.INPUT; swapResultSuccess = false; swapResultTxHash = null; swapResultError = null }
            }) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Text(
                when (swapStep) {
                    SwapStep.INPUT -> stringResource(MR.strings.wallet_action_swap)
                    SwapStep.REVIEW -> stringResource(MR.strings.wallet_swap_step_review)
                    SwapStep.EXECUTING -> stringResource(MR.strings.wallet_swap_step_executing)
                    SwapStep.RESULT -> if (swapResultSuccess) stringResource(MR.strings.wallet_swap_complete) else stringResource(MR.strings.wallet_swap_failed)
                },
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (swapStep == SwapStep.INPUT) {
                IconButton(onClick = { showSlippageSettings = !showSlippageSettings }) {
                    Icon(Icons.Default.Settings, null, tint = TextSecondary)
                }
            }
        }
        Divider(color = Border)

        when (swapStep) {
            SwapStep.INPUT -> SwapInputContent(
                fromToken = fromToken,
                toToken = toToken,
                fromAmount = fromAmount,
                toAmount = toAmount,
                fromAmountUsd = fromAmountUsd,
                toAmountUsd = toAmountUsd,
                isLoading = isLoading,
                allQuotes = allQuotes,
                selectedQuote = selectedQuote,
                errorMessage = errorMessage,
                validationError = validationError,
                slippage = slippage,
                showSlippageSettings = showSlippageSettings,
                customSlippage = customSlippage,
                isCrossChain = isCrossChain,
                destNetwork = destNetwork,
                quoteCountdown = quoteCountdown,
                pendingSwaps = pendingSwaps,
                prices = prices,
                getCurrentNetwork = { getCurrentNetwork() },
                onFromAmountChange = { fromAmount = InputValidator.sanitizeAmount(it) },
                onMaxClick = { fromAmount = getAssetBalance(fromToken) },
                onSwapTokens = {
                    val tmp = fromToken; fromToken = toToken; toToken = tmp
                    val tmpA = fromAmount; fromAmount = toAmount; toAmount = tmpA
                },
                onSelectQuote = { q -> selectedQuote = q; toAmount = q.toAmount },
                onFromTokenClick = { showFromTokenPicker = true },
                onToTokenClick = { showToTokenPicker = true },
                onSlippageChange = { slippage = it },
                onCustomSlippageChange = { customSlippage = it },
                onApplyCustomSlippage = {
                    val v = customSlippage.toDoubleOrNull()
                    if (v != null && v in 0.01..50.0) { slippage = customSlippage; showSlippageSettings = false }
                },
                onToggleCrossChain = { },
                onDestNetworkClick = {},
                onReviewClick = {
                    val err = validate()
                    if (err != null) { validationError = err }
                    else { validationError = null; swapStep = SwapStep.REVIEW }
                },
                onRefreshQuotes = {
                    scope.launch {
                        isLoading = true; errorMessage = null; allQuotes = emptyList(); selectedQuote = null
                        try {
                            val quotes = SwapService.getAllQuotes(
                                getCurrentNetwork(), getAssetSymbol(fromToken), getAssetSymbol(toToken),
                                fromAmount, getFromAddress(), destNetwork, getDestAddress()
                            ).sortedByDescending { it.exchangeRate }
                            allQuotes = quotes; selectedQuote = quotes.firstOrNull(); toAmount = quotes.firstOrNull()?.toAmount ?: ""
                            quoteTimestamp = System.currentTimeMillis()
                            if (quotes.isEmpty()) errorMessage = generalGetString(MR.strings.wallet_no_route_found)
                        } catch (e: Exception) { errorMessage = humanReadableError(e.message) }
                        isLoading = false
                    }
                }
            )
            SwapStep.REVIEW -> SwapReviewContent(
                selectedQuote = selectedQuote!!,
                fromToken = fromToken,
                toToken = toToken,
                fromAmount = fromAmount,
                toAmount = toAmount,
                fromAmountUsd = fromAmountUsd,
                toAmountUsd = toAmountUsd,
                slippage = slippage,
                getCurrentNetwork = { getCurrentNetwork() },
                onBack = { swapStep = SwapStep.INPUT },
                onConfirm = {
                    val doSwap = {
                        swapStep = SwapStep.EXECUTING
                        scope.launch {
                            executionPhase = generalGetString(MR.strings.wallet_submitting_swap)
                            swapResultError = null
                            val result = SwapManager.executeSwap(
                                quote = selectedQuote!!,
                                fromAddress = getFromAddress(),
                                slippagePercent = slippage.toDoubleOrNull() ?: 0.5,
                                destinationAddress = getDestAddress()
                            )
                            result.fold(
                                onSuccess = { pending ->
                                    swapResultSuccess = true
                                    swapResultTxHash = pending.txHash ?: pending.exchangeId
                                    if (pending.isExchangeProvider && pending.depositAddress != null) {
                                        swapResultError = null
                                    }
                                    swapStep = SwapStep.RESULT
                                    fromAmount = ""; toAmount = ""; allQuotes = emptyList(); selectedQuote = null
                                },
                                onFailure = { e ->
                                    swapResultSuccess = false
                                    swapResultError = humanReadableError(e.message)
                                    swapStep = SwapStep.RESULT
                                }
                            )
                        }
                        Unit
                    }
                    val isLockEnabled = ChatController.appPrefs.performLA.get()
                    if (isLockEnabled) {
                        val laMode = ChatController.appPrefs.laMode.get()
                        authenticate(
                            promptTitle = if (laMode == LAMode.SYSTEM) generalGetString(MR.strings.wallet_confirm_swap_button) else generalGetString(MR.strings.wallet_enter_passcode),
                            promptSubtitle = generalGetString(MR.strings.wallet_auth_swap_subtitle),
                            selfDestruct = false,
                            usingLAMode = laMode,
                            oneTime = true
                        ) { result ->
                            if (result is LAResult.Success) doSwap()
                        }
                    } else {
                        doSwap()
                    }
                }
            )
            SwapStep.EXECUTING -> SwapExecutingContent(executionPhase)
            SwapStep.RESULT -> SwapResultContent(
                success = swapResultSuccess,
                txHash = swapResultTxHash,
                error = swapResultError,
                network = getCurrentNetwork(),
                onDone = {
                    swapStep = SwapStep.INPUT
                    swapResultSuccess = false; swapResultTxHash = null; swapResultError = null
                }
            )
        }
    }

    // Dialogs
    if (showFromTokenPicker) {
        SwapTokenPickerDialog(
            assets = allAssets, selectedAsset = fromToken, prices = prices,
            onSelect = { fromToken = it; showFromTokenPicker = false },
            onDismiss = { showFromTokenPicker = false }
        )
    }
    if (showToTokenPicker) {
        SwapTokenPickerDialog(
            assets = crossChainDestAssets.filter { !isSameAsset(it.original, fromToken) },
            selectedAsset = toToken, prices = prices,
            onSelect = { toToken = it; showToTokenPicker = false },
            onDismiss = { showToTokenPicker = false }
        )
    }
}

// ── STEP 1: Input Screen ─────────────────────────────────────────────

@Composable
private fun SwapInputContent(
    fromToken: Any?, toToken: Any?, fromAmount: String, toAmount: String,
    fromAmountUsd: Double, toAmountUsd: Double, isLoading: Boolean,
    allQuotes: List<SwapQuote>, selectedQuote: SwapQuote?,
    errorMessage: String?, validationError: String?, slippage: String,
    showSlippageSettings: Boolean, customSlippage: String,
    isCrossChain: Boolean, destNetwork: BlockchainNetwork?,
    quoteCountdown: Int, pendingSwaps: List<PendingSwap>,
    prices: Map<String, Double>,
    getCurrentNetwork: () -> BlockchainNetwork,
    onFromAmountChange: (String) -> Unit, onMaxClick: () -> Unit,
    onSwapTokens: () -> Unit, onSelectQuote: (SwapQuote) -> Unit,
    onFromTokenClick: () -> Unit, onToTokenClick: () -> Unit,
    onSlippageChange: (String) -> Unit, onCustomSlippageChange: (String) -> Unit,
    onApplyCustomSlippage: () -> Unit, onToggleCrossChain: (Boolean) -> Unit,
    onDestNetworkClick: () -> Unit, onReviewClick: () -> Unit,
    onRefreshQuotes: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Pending swaps banner
        if (pendingSwaps.isNotEmpty()) {
            for (swap in pendingSwaps) {
                val isFailed = swap.state == SwapLifecycleState.FAILED
                val isRefunded = swap.state == SwapLifecycleState.REFUNDED
                val isExpired = swap.state == SwapLifecycleState.EXPIRED
                val isTerminal = isFailed || isRefunded || isExpired
                val isSuccess = swap.state == SwapLifecycleState.CONFIRMED
                val statusColor = when (swap.state) {
                    SwapLifecycleState.AWAITING_DEPOSIT -> WarningOrange
                    SwapLifecycleState.CONFIRMING_DEPOSIT, SwapLifecycleState.EXCHANGING -> CrossChainBlue
                    SwapLifecycleState.SENDING -> Green
                    SwapLifecycleState.CONFIRMED -> Green
                    SwapLifecycleState.FAILED -> Red
                    SwapLifecycleState.REFUNDED -> WarningOrange
                    SwapLifecycleState.EXPIRED -> Red.copy(0.7f)
                    else -> WarningOrange
                }
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    backgroundColor = statusColor.copy(0.08f), elevation = 0.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isTerminal || isSuccess) {
                                Icon(
                                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    null, tint = statusColor, modifier = Modifier.size(16.dp)
                                )
                            } else {
                                CircularProgressIndicator(Modifier.size(16.dp), color = statusColor, strokeWidth = 2.dp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(swap.displayState, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                                Text("${swap.quote.fromAmount} ${swap.quote.fromToken} → ${swap.quote.toToken} via ${swap.quote.provider}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        if (isFailed && !swap.errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Card(
                                Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                                backgroundColor = Red.copy(0.06f), elevation = 0.dp
                            ) {
                                Text(
                                    "Reason: ${swap.errorMessage}",
                                    fontSize = 11.sp, color = Red, modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        if (swap.depositAddress != null && (swap.state == SwapLifecycleState.CONFIRMING_DEPOSIT || swap.state == SwapLifecycleState.EXCHANGING || swap.state == SwapLifecycleState.SENDING)) {
                            Spacer(Modifier.height(6.dp))
                            if (swap.txHash != null) {
                                val displayHash = if (swap.txHash.length > 20) "${swap.txHash.take(10)}...${swap.txHash.takeLast(8)}" else swap.txHash
                                Text(stringResource(MR.strings.wallet_deposit_tx_label, displayHash), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = TextSecondary.copy(0.7f))
                            }
                        }
                        if (swap.exchangeId != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(MR.strings.wallet_exchange_id_label, swap.exchangeId ?: ""), fontSize = 10.sp, color = TextSecondary.copy(0.6f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Cross-chain indicator (auto-detected)
        if (isCrossChain && destNetwork != null) {
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                backgroundColor = CrossChainBlue.copy(0.08f), elevation = 1.dp
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CompareArrows, null, tint = CrossChainBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(MR.strings.wallet_cross_chain_swap), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CrossChainBlue)
                        Text("${getCurrentNetwork().displayName} → ${destNetwork!!.displayName}", fontSize = 11.sp, color = TextSecondary)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = CrossChainBlue.copy(0.15f)) {
                        Text(stringResource(MR.strings.wallet_auto_badge), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CrossChainBlue, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // FROM + swap button + TO
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwapTokenCard(
                    label = stringResource(MR.strings.wallet_you_pay_label), token = fromToken, amount = fromAmount, amountUsd = fromAmountUsd,
                    isEditable = true, isLoading = false, onTokenClick = onFromTokenClick,
                    onAmountChange = onFromAmountChange, onMaxClick = onMaxClick,
                    accentColor = SwapPurple, bottomPadding = 6
                )
                Spacer(Modifier.height(4.dp))
                SwapTokenCard(
                    label = stringResource(MR.strings.wallet_you_receive_label), token = toToken, amount = toAmount, amountUsd = toAmountUsd,
                    isEditable = false, isLoading = isLoading, onTokenClick = onToTokenClick,
                    onAmountChange = {}, onMaxClick = {}, accentColor = Green, topPadding = 6
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center).zIndex(10f).size(44.dp)
                    .shadow(6.dp, CircleShape).clip(CircleShape)
                    .border(3.dp, BgPrimary, CircleShape).background(SwapPurple)
                    .clickable { onSwapTokens() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.SwapVert, "Switch", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }

        Spacer(Modifier.height(14.dp))

        // Slippage settings (collapsible)
        AnimatedVisibility(visible = showSlippageSettings) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), backgroundColor = BgCard, elevation = 2.dp) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(MR.strings.wallet_slippage_tolerance_label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0.1", "0.5", "1.0", "3.0").forEach { v ->
                            Surface(
                                modifier = Modifier.weight(1f).clickable { onSlippageChange(v) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (slippage == v) SwapPurple else BgPrimary
                            ) {
                                Text("$v%", modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (slippage == v) Color.White else TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = BgPrimary, modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = customSlippage, onValueChange = onCustomSlippageChange,
                                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.Center),
                                singleLine = true,
                                decorationBox = { inner ->
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) {
                                            if (customSlippage.isEmpty()) Text(stringResource(MR.strings.wallet_custom_slippage_placeholder), fontSize = 14.sp, color = TextSecondary.copy(0.5f))
                                            inner()
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.clickable { onApplyCustomSlippage() },
                            shape = RoundedCornerShape(10.dp), color = SwapPurple
                        ) {
                            Text(stringResource(MR.strings.wallet_set_button), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    val slipVal = slippage.toDoubleOrNull() ?: 0.5
                    if (slipVal > 3.0) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(MR.strings.wallet_high_slippage_warning), fontSize = 11.sp, color = WarningOrange)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Quote list
        if (isLoading) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), backgroundColor = BgCard, elevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = SwapPurple, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(MR.strings.wallet_finding_rates), fontSize = 13.sp, color = TextSecondary)
                }
            }
        } else if (allQuotes.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(MR.strings.wallet_quotes_label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (quoteCountdown > 0) {
                        Text("${quoteCountdown}s", fontSize = 11.sp, color = if (quoteCountdown < 10) WarningOrange else TextSecondary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        Icons.Default.Refresh, "Refresh", tint = SwapPurple, modifier = Modifier.size(18.dp).clickable { onRefreshQuotes() }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${allQuotes.size}", fontSize = 12.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            allQuotes.forEachIndexed { idx, quote ->
                SwapQuoteCard(
                    quote = quote, isSelected = selectedQuote == quote, isBest = idx == 0,
                    fromSymbol = getAssetSymbol(fromToken), toSymbol = getAssetSymbol(toToken),
                    getCurrentNetwork = getCurrentNetwork, onClick = { onSelectQuote(quote) }
                )
                if (idx < allQuotes.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }

        // Swap details
        if (selectedQuote != null) {
            Spacer(Modifier.height(12.dp))
            SwapDetailsCard(
                quote = selectedQuote!!, fromSymbol = getAssetSymbol(fromToken),
                toSymbol = getAssetSymbol(toToken), slippage = slippage, toAmount = toAmount,
                getCurrentNetwork = getCurrentNetwork
            )
        }

        // Price impact warning
        if (selectedQuote != null && selectedQuote!!.priceImpact > 1.0) {
            Spacer(Modifier.height(10.dp))
            val impact = selectedQuote!!.priceImpact
            val (bgColor, textColor, msg) = when {
                impact > 5.0 -> Triple(WarningRed.copy(0.1f), WarningRed, stringResource(MR.strings.wallet_price_impact_very_high, String.format("%.1f", impact)))
                impact > 3.0 -> Triple(WarningOrange.copy(0.1f), WarningOrange, stringResource(MR.strings.wallet_price_impact_high, String.format("%.1f", impact)))
                else -> Triple(WarningOrange.copy(0.06f), WarningOrange, stringResource(MR.strings.wallet_price_impact_moderate, String.format("%.1f", impact)))
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = bgColor, elevation = 0.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = textColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(msg, fontSize = 12.sp, color = textColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Error banners
        (errorMessage ?: validationError)?.let { err ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = Red.copy(0.08f), elevation = 0.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(err, fontSize = 13.sp, color = Red)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Review button
        val canReview = fromAmount.isNotEmpty() && toToken != null && selectedQuote != null && !isLoading
        Button(
            onClick = onReviewClick,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = SwapPurple, disabledBackgroundColor = Border),
            shape = RoundedCornerShape(16.dp), enabled = canReview
        ) {
            Icon(Icons.Default.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(MR.strings.wallet_review_swap_button), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── STEP 2: Review Confirmation ──────────────────────────────────────

@Composable
private fun SwapReviewContent(
    selectedQuote: SwapQuote, fromToken: Any?, toToken: Any?,
    fromAmount: String, toAmount: String, fromAmountUsd: Double, toAmountUsd: Double,
    slippage: String, getCurrentNetwork: () -> BlockchainNetwork,
    onBack: () -> Unit, onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // From/To summary
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), backgroundColor = BgCard, elevation = 4.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AssetIcon(fromToken, size = 40, fallbackColor = SwapPurple)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(MR.strings.wallet_you_pay_label), fontSize = 12.sp, color = TextSecondary)
                        Text("${displayBalance(fromAmount, getAssetSymbol(fromToken))} ${getAssetSymbol(fromToken)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        val fromNet = getAssetNetwork(fromToken)?.displayName ?: ""
                        if (fromNet.isNotBlank()) Text(fromNet, fontSize = 11.sp, color = TextSecondary)
                        if (fromAmountUsd > 0) Text("≈ $${String.format("%,.2f", fromAmountUsd)}", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Divider(color = Border)
                    Surface(shape = CircleShape, color = BgCard, modifier = Modifier.size(36.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowDownward, null, tint = SwapPurple, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AssetIcon(toToken, size = 40, fallbackColor = Green)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(MR.strings.wallet_you_receive_label), fontSize = 12.sp, color = TextSecondary)
                        Text("${displayBalance(toAmount, getAssetSymbol(toToken))} ${getAssetSymbol(toToken)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
                        val toNet = getAssetNetwork(toToken)?.displayName ?: ""
                        if (toNet.isNotBlank()) Text(toNet, fontSize = 11.sp, color = TextSecondary)
                        if (toAmountUsd > 0) Text("≈ $${String.format("%,.2f", toAmountUsd)}", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SwapDetailsCard(
            quote = selectedQuote, fromSymbol = getAssetSymbol(fromToken),
            toSymbol = getAssetSymbol(toToken), slippage = slippage, toAmount = toAmount,
            getCurrentNetwork = getCurrentNetwork
        )

        if (selectedQuote.priceImpact > 3.0) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = WarningRed.copy(0.1f), elevation = 0.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = WarningRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(MR.strings.wallet_price_impact_caution, String.format("%.1f", selectedQuote.priceImpact)), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WarningRed)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (selectedQuote.priceImpact > 5.0) WarningRed else SwapPurple
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (selectedQuote.priceImpact > 5.0) stringResource(MR.strings.wallet_swap_anyway_button) else stringResource(MR.strings.wallet_confirm_swap_button),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)
        ) { Text(stringResource(MR.strings.wallet_cancel_button), fontSize = 15.sp, color = TextSecondary) }
    }
}

// ── STEP 3: Executing ────────────────────────────────────────────────

@Composable
private fun SwapExecutingContent(phase: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(56.dp), color = SwapPurple, strokeWidth = 4.dp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(MR.strings.wallet_processing_swap), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(phase.ifEmpty { stringResource(MR.strings.wallet_submitting_transaction) }, fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(MR.strings.wallet_please_wait), fontSize = 12.sp, color = TextSecondary.copy(0.6f))
        }
    }
}

// ── STEP 4: Result ───────────────────────────────────────────────────

@Composable
private fun SwapResultContent(
    success: Boolean, txHash: String?, error: String?,
    network: BlockchainNetwork, onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .background(if (success) Green.copy(0.12f) else Red.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                null, tint = if (success) Green else Red, modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (success) stringResource(MR.strings.wallet_swap_submitted) else stringResource(MR.strings.wallet_swap_failed),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (success) stringResource(MR.strings.wallet_swap_submitted_desc)
            else (error ?: stringResource(MR.strings.wallet_unexpected_error)),
            fontSize = 14.sp, color = if (success) TextSecondary else Red.copy(0.9f), textAlign = TextAlign.Center
        )
        if (!success && error != null) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = Red.copy(0.06f), elevation = 0.dp) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(MR.strings.wallet_error_details), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Red)
                    Spacer(Modifier.height(4.dp))
                    Text(error, fontSize = 13.sp, color = TextPrimary)
                }
            }
        }
        if (success && !txHash.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = BgPrimary, elevation = 0.dp) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(MR.strings.wallet_reference_id), fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    val displayHash = if (txHash.length > 36) "${txHash.take(24)}...${txHash.takeLast(12)}" else txHash
                    Text(displayHash, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = SwapPurple),
            shape = RoundedCornerShape(16.dp)
        ) { Text(stringResource(MR.strings.wallet_done_button), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

// ── Shared Components ────────────────────────────────────────────────

@Composable
private fun SwapTokenCard(
    label: String, token: Any?, amount: String, amountUsd: Double,
    isEditable: Boolean, isLoading: Boolean,
    onTokenClick: () -> Unit, onAmountChange: (String) -> Unit, onMaxClick: () -> Unit,
    accentColor: Color, topPadding: Int = 0, bottomPadding: Int = 0
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), backgroundColor = BgCard, elevation = 3.dp) {
        Column(modifier = Modifier.padding(16.dp).padding(top = topPadding.dp, bottom = bottomPadding.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                if (token != null) Text("${stringResource(MR.strings.wallet_balance_label)}: ${displayBalance(getAssetBalance(token), getAssetSymbol(token))}", fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.clickable { onTokenClick() }, shape = RoundedCornerShape(12.dp), color = BgPrimary) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssetIcon(token, size = 28, fallbackColor = accentColor)
                        Spacer(Modifier.width(8.dp))
                        if (token != null) {
                            Column {
                                Text(getAssetSymbol(token), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                                val netName = getAssetNetwork(token)?.displayName ?: ""
                                if (netName.isNotBlank()) {
                                    Text(netName, fontSize = 10.sp, color = TextSecondary, lineHeight = 12.sp)
                                }
                            }
                        } else {
                            Text(stringResource(MR.strings.wallet_select_token_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accentColor, strokeWidth = 2.dp)
                    } else if (isEditable) {
                        BasicTextField(
                            value = amount, onValueChange = onAmountChange,
                            textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.End),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (amount.isEmpty()) Text("0.00", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextSecondary.copy(0.4f), textAlign = TextAlign.End)
                                inner()
                            },
                            modifier = Modifier.width(150.dp)
                        )
                    } else {
                        val displayAmt = if (amount.isNotEmpty()) displayBalance(amount, getAssetSymbol(token)) else "0.00"
                        Text(
                            displayAmt, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = if (amount.isEmpty()) TextSecondary.copy(0.4f) else accentColor
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (amount.isNotEmpty() && amountUsd > 0) {
                            Text("≈ $${String.format("%,.2f", amountUsd)}", fontSize = 12.sp, color = TextSecondary)
                            if (isEditable) Spacer(Modifier.width(8.dp))
                        }
                        if (isEditable) {
                            Text(stringResource(MR.strings.wallet_max_button), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onMaxClick() }.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapQuoteCard(
    quote: SwapQuote, isSelected: Boolean, isBest: Boolean,
    fromSymbol: String, toSymbol: String,
    getCurrentNetwork: () -> BlockchainNetwork, onClick: () -> Unit
) {
    val providerTypeKey = when {
        quote.provider in listOf("THORChain", "Chainflip", "Rango", "deBridge", "Symbiosis", "Relay", "Squid") -> "bridge"
        quote.provider == "ChangeNOW" -> "exchange"
        quote.provider.startsWith("Li.Fi") -> "aggregator"
        quote.provider.startsWith("Market Rate") -> "estimate"
        quote.provider in listOf("1inch", "0x (Matcha)", "Paraswap", "OpenOcean", "KyberSwap") -> "aggregator"
        quote.provider == "Jupiter" -> "solana_dex"
        quote.provider == "SunSwap" -> "tron_dex"
        quote.provider == "Ref Finance" -> "near_dex"
        quote.provider == "Defuse" -> "near_bridge"
        else -> "dex"
    }
    val providerType = when (providerTypeKey) {
        "bridge" -> stringResource(MR.strings.wallet_provider_bridge)
        "exchange" -> stringResource(MR.strings.wallet_provider_exchange)
        "aggregator" -> stringResource(MR.strings.wallet_provider_aggregator)
        "estimate" -> stringResource(MR.strings.wallet_provider_estimate)
        "solana_dex" -> stringResource(MR.strings.wallet_provider_solana_dex)
        "tron_dex" -> stringResource(MR.strings.wallet_provider_tron_dex)
        "near_dex" -> stringResource(MR.strings.wallet_provider_near_dex)
        "near_bridge" -> stringResource(MR.strings.wallet_provider_near_bridge)
        else -> stringResource(MR.strings.wallet_provider_dex)
    }
    val providerColor = when (providerTypeKey) {
        "bridge" -> CrossChainBlue; "exchange" -> Color(0xFF27AE60); "aggregator" -> Color(0xFFE67E22)
        "estimate" -> Color(0xFF95A5A6)
        "solana_dex" -> Color(0xFF9945FF); "tron_dex" -> Color(0xFFFF0013)
        "near_dex" -> Color(0xFF00C08B); "near_bridge" -> Color(0xFF00C08B)
        else -> SwapPurple
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = if (isSelected) SwapPurple.copy(0.08f) else BgCard,
        elevation = if (isSelected) 3.dp else 1.dp,
        border = if (isSelected) BorderStroke(1.5.dp, SwapPurple) else null
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(quote.provider, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = providerColor.copy(0.12f)) {
                        Text(providerType, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = providerColor)
                    }
                    if (isBest) {
                        Spacer(Modifier.width(4.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Green.copy(0.15f)) {
                            Text(stringResource(MR.strings.wallet_best_badge), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text("1 $fromSymbol ≈ ${String.format("%.6f", quote.exchangeRate)} $toSymbol", fontSize = 11.sp, color = TextSecondary)
                if (quote.destNetwork != null) {
                    Text("${getCurrentNetwork().displayName} → ${quote.destNetwork!!.displayName}", fontSize = 10.sp, color = CrossChainBlue)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(displayBalance(quote.toAmount, toSymbol), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSelected) SwapPurple else TextPrimary)
                Text(toSymbol, fontSize = 11.sp, color = TextSecondary)
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, null, tint = SwapPurple, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SwapDetailsCard(
    quote: SwapQuote, fromSymbol: String, toSymbol: String,
    slippage: String, toAmount: String,
    getCurrentNetwork: () -> BlockchainNetwork
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), backgroundColor = BgCard, elevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            SwapDetailRow(stringResource(MR.strings.wallet_swap_exchange_rate), "1 $fromSymbol ≈ ${String.format("%.6f", quote.exchangeRate)} $toSymbol")
            Divider(color = Border, modifier = Modifier.padding(vertical = 8.dp))

            val fee = "~${String.format("%.6f", quote.estimatedGas.toLongOrNull()?.times(30)?.div(1e9) ?: 0.002)} ${getCurrentNetwork().symbol}"
            SwapDetailRow(stringResource(MR.strings.wallet_network_fee_label), fee)
            Divider(color = Border, modifier = Modifier.padding(vertical = 8.dp))

            val impact = if (quote.priceImpact > 0) "${String.format("%.2f", quote.priceImpact)}%" else "<0.01%"
            val impactColor = when {
                quote.priceImpact > 5.0 -> WarningRed
                quote.priceImpact > 3.0 -> WarningOrange
                quote.priceImpact > 1.0 -> WarningOrange
                else -> TextPrimary
            }
            SwapDetailRow(stringResource(MR.strings.wallet_swap_price_impact), impact, valueColor = impactColor)
            Divider(color = Border, modifier = Modifier.padding(vertical = 8.dp))

            SwapDetailRow(stringResource(MR.strings.wallet_swap_slippage), "$slippage%")
            Divider(color = Border, modifier = Modifier.padding(vertical = 8.dp))

            val minRcv = if (toAmount.isNotEmpty()) {
                val factor = 1.0 - (slippage.toDoubleOrNull() ?: 0.5) / 100.0
                displayBalance(((toAmount.toDoubleOrNull() ?: 0.0) * factor).toString(), toSymbol)
            } else "---"
            SwapDetailRow(stringResource(MR.strings.wallet_swap_min_received), "$minRcv $toSymbol")
            Divider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
            SwapDetailRow(stringResource(MR.strings.wallet_swap_provider), quote.provider)
        }
    }
}

@Composable
private fun SwapDetailRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

// ── Token Picker (Professional) ──────────────────────────────────────

private data class SwapAssetItem(
    val symbol: String,
    val name: String,
    val balance: String,
    val isNative: Boolean,
    val original: Any
)

@Composable
private fun SwapTokenPickerDialog(
    assets: List<SwapAssetItem>,
    selectedAsset: Any?,
    prices: Map<String, Double>,
    onSelect: (Any) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNetwork by remember { mutableStateOf<BlockchainNetwork?>(null) }

    val networks = remember(assets) {
        assets.mapNotNull { getAssetNetwork(it.original) }.distinct()
    }

    val filtered = remember(assets, searchQuery, selectedNetwork) {
        val sorted = assets.sortedByDescending {
            val bal = it.balance.toDoubleOrNull() ?: 0.0
            val price = prices[it.symbol.uppercase()] ?: WalletPriceService.getPrice(it.symbol)
            bal * price
        }
        sorted.filter { asset ->
            val matchesSearch = searchQuery.isBlank() ||
                asset.symbol.contains(searchQuery, ignoreCase = true) ||
                asset.name.contains(searchQuery, ignoreCase = true)
            val matchesNetwork = selectedNetwork == null ||
                getAssetNetwork(asset.original) == selectedNetwork
            matchesSearch && matchesNetwork
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(MR.strings.wallet_select_token_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = BgPrimary) {
                    BasicTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                        singleLine = true,
                        decorationBox = { inner ->
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Box { if (searchQuery.isEmpty()) Text(stringResource(MR.strings.wallet_search_token_placeholder), fontSize = 14.sp, color = TextSecondary.copy(0.5f)); inner() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (networks.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedNetwork == null) SwapPurple.copy(0.15f) else BgPrimary,
                                modifier = Modifier.clickable { selectedNetwork = null }
                            ) {
                                Text(stringResource(MR.strings.wallet_filter_all), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp, fontWeight = if (selectedNetwork == null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedNetwork == null) SwapPurple else TextSecondary)
                            }
                        }
                        items(networks) { net ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedNetwork == net) SwapPurple.copy(0.15f) else BgPrimary,
                                modifier = Modifier.clickable { selectedNetwork = if (selectedNetwork == net) null else net }
                            ) {
                                Text(net.displayName, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp, fontWeight = if (selectedNetwork == net) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedNetwork == net) SwapPurple else TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(filtered) { asset ->
                    val isSelected = isSameAsset(selectedAsset, asset.original)
                    val balVal = asset.balance.toDoubleOrNull() ?: 0.0
                    val assetPrice = prices[asset.symbol.uppercase()] ?: WalletPriceService.getPrice(asset.symbol)
                    val usdVal = balVal * assetPrice
                    val networkName = when (asset.original) {
                        is WalletAccount -> (asset.original as WalletAccount).network.displayName
                        is WalletToken -> (asset.original as WalletToken).network.displayName
                        else -> ""
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) SwapPurple.copy(0.08f) else Color.Transparent)
                            .clickable { onSelect(asset.original) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AssetIcon(asset.original, size = 36, fallbackColor = AccentGold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(asset.symbol, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                                if (networkName.isNotBlank()) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = BgPrimary) {
                                        Text(networkName, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 9.sp, color = TextSecondary)
                                    }
                                }
                            }
                            Text(asset.name, fontSize = 12.sp, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (balVal > 0) displayBalance(asset.balance, asset.symbol) else "0",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary
                            )
                            if (usdVal > 0.01) {
                                Text("$${String.format("%,.2f", usdVal)}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        if (isSelected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.CheckCircle, null, tint = SwapPurple, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (asset != filtered.lastOrNull()) Divider(color = Border.copy(0.5f), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.wallet_cancel_button), color = SwapPurple) }
        },
        shape = RoundedCornerShape(20.dp),
        backgroundColor = BgCard
    )
}

// ── Destination Network Picker ───────────────────────────────────────

@Composable
private fun DestNetworkPickerDialog(
    currentNetwork: BlockchainNetwork, destNetwork: BlockchainNetwork?,
    onSelect: (BlockchainNetwork) -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.wallet_destination_chain_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(BlockchainNetwork.ALL_SUPPORTED.filter { it != currentNetwork }) { network ->
                    val isSelected = destNetwork == network
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CrossChainBlue.copy(0.08f) else Color.Transparent)
                            .clickable { onSelect(network) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, null, tint = if (isSelected) CrossChainBlue else TextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(network.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TextPrimary)
                            Text(network.symbol, fontSize = 12.sp, color = TextSecondary)
                        }
                        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = CrossChainBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.wallet_cancel_button), color = SwapPurple) } },
        shape = RoundedCornerShape(20.dp), backgroundColor = BgCard
    )
}

// ── Swap helper functions ────────────────────────────────────────────

private fun getAssetSymbol(asset: Any?): String {
    return when (asset) {
        is WalletAccount -> asset.network.symbol
        is WalletToken -> asset.symbol
        else -> ""
    }
}

private fun getAssetBalance(asset: Any?): String {
    return when (asset) {
        is WalletAccount -> asset.balance
        is WalletToken -> asset.balance
        else -> "0"
    }
}

private fun getAssetNetwork(asset: Any?): BlockchainNetwork? {
    return when (asset) {
        is WalletAccount -> asset.network
        is WalletToken -> asset.network
        else -> null
    }
}

private fun isSameAsset(a: Any?, b: Any?): Boolean {
    if (a == null || b == null) return false
    val symA = getAssetSymbol(a)
    val symB = getAssetSymbol(b)
    val netA = getAssetNetwork(a)
    val netB = getAssetNetwork(b)
    return symA == symB && netA == netB
}

// ==================== Network Status Components ====================

@Composable
private fun NetworkStatusIndicator(
    isConnected: Boolean,
    networkStatuses: Map<BlockchainNetwork, NetworkStatusService.NetworkStatus>,
    onClick: () -> Unit
) {
    val overallStatus = if (!isConnected) {
        NetworkStatusService.LatencyLevel.OFFLINE
    } else {
        val avgLatency = networkStatuses.values.filter { it.isConnected }.map { it.latencyMs }.average()
        when {
            avgLatency < 500 -> NetworkStatusService.LatencyLevel.FAST
            avgLatency < 2000 -> NetworkStatusService.LatencyLevel.NORMAL
            avgLatency < 5000 -> NetworkStatusService.LatencyLevel.SLOW
            else -> NetworkStatusService.LatencyLevel.VERY_SLOW
        }
    }
    
    val statusColor = Color(overallStatus.color)
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(statusColor.copy(0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            overallStatus.displayName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = statusColor
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            null,
            tint = statusColor,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun NetworkStatusPanel(
    networkStatuses: Map<BlockchainNetwork, NetworkStatusService.NetworkStatus>,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = BgCard,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(MR.strings.wallet_network_status_title),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, stringResource(MR.strings.wallet_refresh_desc), tint = AccentGold, modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            val networks = listOf(
                BlockchainNetwork.ETHEREUM,
                BlockchainNetwork.BINANCE_SMART_CHAIN,
                BlockchainNetwork.POLYGON,
                BlockchainNetwork.ARBITRUM,
                BlockchainNetwork.OPTIMISM,
                BlockchainNetwork.AVALANCHE,
                BlockchainNetwork.BASE
            )
            
            networks.forEach { network ->
                val status = networkStatuses[network]
                NetworkStatusRow(network, status)
                if (network != networks.last()) {
                    Divider(color = Border, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusRow(
    network: BlockchainNetwork,
    status: NetworkStatusService.NetworkStatus?
) {
    val latencyLevel = status?.latencyLevel ?: NetworkStatusService.LatencyLevel.OFFLINE
    val statusColor = Color(latencyLevel.color)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Network icon
        val networkIcon = getNetworkIcon(network)
        if (networkIcon != null) {
            Image(
                painter = painterResource(networkIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentGold.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(network.symbol.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGold)
            }
        }
        
        Spacer(Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                network.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (status?.blockHeight != null) {
                Text(
                    stringResource(MR.strings.wallet_block_height, status.blockHeight.toString()),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
        
        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(0.1f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (status?.isConnected == true) "${status.latencyMs}ms" else stringResource(MR.strings.wallet_latency_offline),
                fontSize = 11.sp,
                color = statusColor
            )
        }
    }
}

// ==================== Address Book UI ====================

@Composable
private fun AddressBookView(
    onAddressSelected: ((String) -> Unit)?,
    onBack: () -> Unit
) {
    val addresses by AddressBook.addressesFlow.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<AddressBook.SavedAddress?>(null) }
    val clipboard = LocalClipboardManager.current
    
    val filteredAddresses = remember(addresses, searchQuery) {
        if (searchQuery.isBlank()) addresses
        else AddressBook.search(searchQuery)
    }
    
    // Add Address Dialog
    if (showAddDialog) {
        AddAddressDialog(
            onSave = { label, address, notes ->
                AddressBook.quickSave(address, label)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Delete Confirmation Dialog
    showDeleteConfirm?.let { addressToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(MR.strings.wallet_delete_address_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    stringResource(MR.strings.wallet_confirm_delete_address, addressToDelete.label),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AddressBook.delete(addressToDelete.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Red)
                ) {
                    Text(stringResource(MR.strings.wallet_delete_button), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(MR.strings.wallet_cancel_button), color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = BgCard
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
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Text(
                stringResource(MR.strings.wallet_address_book_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, stringResource(MR.strings.wallet_add_address_desc), tint = AccentGold)
            }
        }
        
        Divider(color = Border)
        
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(MR.strings.wallet_search_addresses_placeholder), color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, null, tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = BgCard,
                focusedBorderColor = AccentGold,
                unfocusedBorderColor = Border,
                textColor = TextPrimary
            )
        )
        
        // Address List
        if (filteredAddresses.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ContactPage,
                    null,
                    tint = TextSecondary.copy(0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (searchQuery.isNotEmpty()) stringResource(MR.strings.wallet_no_addresses_found) else stringResource(MR.strings.wallet_no_saved_addresses),
                    fontSize = 16.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(MR.strings.wallet_tap_to_add_address),
                    fontSize = 14.sp,
                    color = TextSecondary.copy(0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                // Favorites section
                val favorites = filteredAddresses.filter { it.isFavorite }
                if (favorites.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(MR.strings.wallet_favorites_label),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(favorites, key = { it.id }) { address ->
                        AddressBookItem(
                            address = address,
                            onSelect = onAddressSelected,
                            onFavoriteToggle = { AddressBook.toggleFavorite(address.id) },
                            onCopy = { clipboard.setText(AnnotatedString(address.address)) },
                            onDelete = { showDeleteConfirm = address }
                        )
                    }
                }
                
                // All addresses section
                val nonFavorites = filteredAddresses.filter { !it.isFavorite }
                if (nonFavorites.isNotEmpty()) {
                    item {
                        Text(
                            if (favorites.isNotEmpty()) stringResource(MR.strings.wallet_all_addresses_label) else stringResource(MR.strings.wallet_addresses_label),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(nonFavorites, key = { it.id }) { address ->
                        AddressBookItem(
                            address = address,
                            onSelect = onAddressSelected,
                            onFavoriteToggle = { AddressBook.toggleFavorite(address.id) },
                            onCopy = { clipboard.setText(AnnotatedString(address.address)) },
                            onDelete = { showDeleteConfirm = address }
                        )
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AddressBookItem(
    address: AddressBook.SavedAddress,
    onSelect: ((String) -> Unit)?,
    onFavoriteToggle: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (onSelect != null) Modifier.clickable { onSelect(address.address) }
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = BgCard,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentGold.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    address.label.take(2).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    address.label,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    address.shortAddress(),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                if (address.useCount > 0) {
                    Text(
                        stringResource(MR.strings.wallet_used_times, address.useCount.toString()),
                        fontSize = 11.sp,
                        color = TextSecondary.copy(0.7f)
                    )
                }
            }
            
            // Actions
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (address.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                    null,
                    tint = if (address.isFavorite) AccentGold else TextSecondary
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, stringResource(MR.strings.wallet_copy_desc), tint = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(MR.strings.wallet_delete_button), tint = Red.copy(0.7f))
            }
        }
    }
}

@Composable
private fun AddAddressDialog(
    onSave: (label: String, address: String, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(MR.strings.wallet_add_address_title), fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(MR.strings.wallet_label_field)) },
                    placeholder = { Text(stringResource(MR.strings.wallet_label_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AccentGold,
                        textColor = TextPrimary
                    )
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(MR.strings.wallet_address_field)) },
                    placeholder = { Text(stringResource(MR.strings.wallet_address_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { address = it.trim() }
                        }) {
                            Icon(Icons.Default.ContentPaste, stringResource(MR.strings.wallet_paste_address_desc), tint = AccentGold)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AccentGold,
                        textColor = TextPrimary
                    )
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(MR.strings.wallet_notes_optional_field)) },
                    placeholder = { Text(stringResource(MR.strings.wallet_notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AccentGold,
                        textColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, address, notes.ifBlank { null }) },
                enabled = label.isNotBlank() && address.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentGold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(MR.strings.wallet_save_button), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.wallet_cancel_button), color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = BgCard
    )
}

// ═══════════════════════════════════════════════════════════════════
//  Wallet Management Screen
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun WalletManagementView(
    wallets: List<WalletProfile>,
    activeWalletId: String?,
    onSwitchWallet: (String) -> Unit,
    onRenameWallet: (String, String) -> Unit,
    onDeleteWallet: (String) -> Unit,
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
    onBack: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf<WalletProfile?>(null) }
    var showDeleteDialog by remember { mutableStateOf<WalletProfile?>(null) }
    var showSeedPhraseForWallet by remember { mutableStateOf<String?>(null) }
    var revealedMnemonic by remember { mutableStateOf<String?>(null) }
    var showNoLockWarning by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val manageScope = rememberCoroutineScope()

    // Request authentication then reveal seed phrase
    fun requestSeedPhrase(wallet: WalletProfile) {
        val isLockEnabled = ChatController.appPrefs.performLA.get()
        if (!isLockEnabled) {
            // No app lock set — show warning but still allow viewing
            showNoLockWarning = true
            showSeedPhraseForWallet = wallet.id
            revealedMnemonic = WalletCoreService.getMnemonicForWallet(wallet.id)
            return
        }

        val laMode = ChatController.appPrefs.laMode.get()
        val promptTitle = if (laMode == LAMode.SYSTEM) generalGetString(MR.strings.wallet_auth_seed_phrase) else generalGetString(MR.strings.wallet_enter_passcode)
        authenticate(
            promptTitle = promptTitle,
            promptSubtitle = "Verify your identity to view the recovery phrase for \"${wallet.name}\"",
            selfDestruct = false,
            usingLAMode = laMode,
            oneTime = true
        ) { result ->
            when (result) {
                is LAResult.Success -> {
                    showSeedPhraseForWallet = wallet.id
                    revealedMnemonic = WalletCoreService.getMnemonicForWallet(wallet.id)
                }
                else -> { /* auth cancelled or failed — do nothing */ }
            }
        }
    }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = painterResource(MR.images.ic_arrow_back_ios_new),
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(MR.strings.wallet_manage_wallets),
                fontSize = 20.sp,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Wallet list
            wallets.forEach { wallet ->
                val isActive = wallet.id == activeWalletId

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) AccentBlue.copy(alpha = 0.06f) else BgCard)
                        .border(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) AccentBlue.copy(alpha = 0.3f) else Border,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    // Top row: avatar + name + action icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitchWallet(wallet.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Wallet avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) AccentBlue.copy(alpha = 0.12f)
                                    else GraySoft
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (isActive) AccentBlue else TextHint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                wallet.name,
                                fontSize = 16.sp,
                                fontFamily = DMSans,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (isActive) {
                                Text(
                                    stringResource(MR.strings.wallet_active_desc),
                                    fontSize = 13.sp,
                                    color = AccentBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // View seed phrase
                        IconButton(onClick = { requestSeedPhrase(wallet) }) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = stringResource(MR.strings.wallet_view_seed_phrase_desc),
                                tint = TextHint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Rename
                        IconButton(onClick = { showRenameDialog = wallet }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(MR.strings.wallet_rename_title),
                                tint = TextHint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Delete (only if more than 1 wallet)
                        if (wallets.size > 1) {
                            IconButton(onClick = { showDeleteDialog = wallet }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(MR.strings.wallet_delete_button),
                                    tint = Red.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Create new wallet button
            Button(
                onClick = onCreateWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 0.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(MR.strings.wallet_create_new_mgmt), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))

            // Import wallet button
            OutlinedButton(
                onClick = onImportWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                border = BorderStroke(1.dp, TextPrimary),
                shape = RoundedCornerShape(360.dp),
                contentPadding = PaddingValues(vertical = 0.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(22.dp), tint = TextPrimary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(MR.strings.wallet_import_wallet), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }

            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    // ── Seed Phrase Dialog ──────────────────────────────────────────────
    if (showSeedPhraseForWallet != null && revealedMnemonic != null) {
        val walletName = wallets.find { it.id == showSeedPhraseForWallet }?.name ?: stringResource(MR.strings.wallet_title)
        val words = revealedMnemonic!!.split(" ")

        AlertDialog(
            onDismissRequest = {
                showSeedPhraseForWallet = null
                revealedMnemonic = null
            },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(MR.strings.wallet_recovery_phrase_title),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        walletName,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            },
            text = {
                Column {
                    // Warning banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AmberSoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(MR.strings.wallet_seed_phrase_warning),
                            fontSize = 13.sp,
                            color = AccentOrange,
                            lineHeight = 18.sp
                        )
                    }

                    if (showNoLockWarning) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RedSoft)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(MR.strings.wallet_no_lock_warning),
                                fontSize = 13.sp,
                                color = Red,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Seed phrase grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GraySoft)
                            .border(1.dp, Border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Display words in rows of 3
                        var wordCounter = 0
                        words.chunked(3).forEach { rowWords ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowWords.forEach { word ->
                                    wordCounter++
                                    val displayIndex = wordCounter
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BgCard)
                                            .border(1.dp, Border, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            "$displayIndex. $word",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )
                                    }
                                }
                                // Fill remaining slots if row has fewer than 3
                                repeat(3 - rowWords.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    var seedCopied by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(revealedMnemonic ?: ""))
                            seedCopied = true
                            manageScope.launch {
                                delay(30_000)
                                clipboardManager.setText(AnnotatedString(""))
                                seedCopied = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (seedCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (seedCopied) stringResource(MR.strings.wallet_copied_clipboard_clears) else stringResource(MR.strings.wallet_copy_to_clipboard),
                            fontSize = 15.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSeedPhraseForWallet = null
                        revealedMnemonic = null
                        showNoLockWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(MR.strings.wallet_done_button), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp),
            backgroundColor = BgCard
        )
    }

    // Rename dialog
    showRenameDialog?.let { wallet ->
        var newName by remember { mutableStateOf(wallet.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(MR.strings.wallet_rename_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(MR.strings.wallet_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = AccentBlue,
                        textColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenameWallet(wallet.id, newName.trim())
                            showRenameDialog = null
                        }
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(MR.strings.wallet_save_button), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(MR.strings.wallet_cancel_button), color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = BgCard
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { wallet ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = {
                Text(stringResource(MR.strings.wallet_delete_wallet_title), fontWeight = FontWeight.Bold, color = Red)
            },
            text = {
                Text(
                    stringResource(MR.strings.wallet_confirm_delete_wallet, wallet.name),
                    color = TextPrimary,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWallet(wallet.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(MR.strings.wallet_delete_button), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(MR.strings.wallet_cancel_button), color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = BgCard
        )
    }
}
