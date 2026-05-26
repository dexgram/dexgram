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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun ManageNetworkTokensView(onBack: () -> Unit, onDone: () -> Unit) {
    val colors = WalletColors.current
    val prefs by NetworkTokenPreferences.prefsFlow.collectAsState()
    var selectedNetwork by remember { mutableStateOf<BlockchainNetwork?>(null) }

    if (selectedNetwork != null) {
        ManageTokensForNetwork(
            network = selectedNetwork!!,
            onBack = { selectedNetwork = null }
        )
    } else {
        ManageNetworksScreen(
            prefs = prefs,
            onBack = onBack,
            onDone = onDone,
            onNetworkTap = { selectedNetwork = it }
        )
    }
}

@Composable
private fun ManageNetworksScreen(
    prefs: AssetPreferences,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNetworkTap: (BlockchainNetwork) -> Unit
) {
    val colors = WalletColors.current
    val allNetworks = remember { BlockchainNetwork.ALL_SUPPORTED }

    Column(Modifier.fillMaxSize().background(colors.bgPrimary).statusBarsPadding()) {
        Surface(elevation = 2.dp, color = colors.bgCard) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, stringResource(MR.strings.wallet_back_desc), tint = colors.textPrimary)
                }
                Text(
                    stringResource(MR.strings.wallet_manage_networks_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDone) {
                    Text(stringResource(MR.strings.wallet_done_button), color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = colors.blueSoft,
            elevation = 0.dp
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, stringResource(MR.strings.wallet_info_desc), tint = colors.accentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(MR.strings.wallet_manage_networks_info),
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    lineHeight = 18.sp
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(allNetworks) { network ->
                val isEnabled = NetworkTokenPreferences.isNetworkEnabled(network)
                val enabledTokenCount = if (isEnabled) {
                    val networkTokens = PopularTokens.getTokensForNetwork(network)
                    if (!prefs.isConfigured) networkTokens.size
                    else prefs.enabledTokens.count { it.network == network }
                } else 0
                val totalTokenCount = PopularTokens.getTokensForNetwork(network).size

                NetworkRow(
                    network = network,
                    isEnabled = isEnabled,
                    enabledTokenCount = enabledTokenCount,
                    totalTokenCount = totalTokenCount,
                    onToggle = { NetworkTokenPreferences.setNetworkEnabled(network, it) },
                    onTap = { if (isEnabled) onNetworkTap(network) }
                )
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network: BlockchainNetwork,
    isEnabled: Boolean,
    enabledTokenCount: Int,
    totalTokenCount: Int,
    onToggle: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    val colors = WalletColors.current

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = isEnabled) { onTap() },
        shape = RoundedCornerShape(14.dp),
        backgroundColor = colors.bgCard,
        elevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetworkIcon(network)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    network.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) colors.textPrimary else colors.textHint
                )
                if (totalTokenCount > 0) {
                    Text(
                        if (isEnabled) "$enabledTokenCount / $totalTokenCount tokens"
                        else "${network.symbol} · $totalTokenCount tokens available",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                } else {
                    Text("${network.symbol} · Native only", fontSize = 12.sp, color = colors.textSecondary)
                }
            }
            if (isEnabled && totalTokenCount > 0) {
                Icon(
                    Icons.Default.KeyboardArrowRight, stringResource(MR.strings.wallet_manage_tokens_desc),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.accentBlue,
                    checkedTrackColor = colors.accentBlue.copy(alpha = 0.4f),
                    uncheckedThumbColor = colors.textHint,
                    uncheckedTrackColor = colors.border
                )
            )
        }
    }
}

@Composable
private fun NetworkIcon(network: BlockchainNetwork) {
    val icon = TokenIcons.getNetworkIcon(network)
    if (icon != null) {
        Image(
            painter = painterResource(icon),
            contentDescription = network.displayName,
            modifier = Modifier.size(42.dp).clip(CircleShape)
        )
    } else {
        val bgColor = networkFallbackColor(network)
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                network.symbol.take(3),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onPrimary
            )
        }
    }
}

private fun networkFallbackColor(network: BlockchainNetwork): Color = when (network) {
    BlockchainNetwork.ETHEREUM -> Color(0xFF627EEA)
    BlockchainNetwork.BINANCE_SMART_CHAIN -> Color(0xFFF0B90B)
    BlockchainNetwork.SOLANA -> Color(0xFF9945FF)
    BlockchainNetwork.POLYGON -> Color(0xFF8247E5)
    BlockchainNetwork.AVALANCHE -> Color(0xFFE84142)
    BlockchainNetwork.TRON -> Color(0xFFFF0013)
    BlockchainNetwork.BITCOIN -> Color(0xFFF7931A)
    BlockchainNetwork.LITECOIN -> Color(0xFF345D9D)
    BlockchainNetwork.DOGECOIN -> Color(0xFFC2A633)
    BlockchainNetwork.RIPPLE -> Color(0xFF0085C0)
    BlockchainNetwork.CARDANO -> Color(0xFF0033AD)
    BlockchainNetwork.ARBITRUM -> Color(0xFF28A0F0)
    BlockchainNetwork.OPTIMISM -> Color(0xFFFF0420)
    BlockchainNetwork.BASE -> Color(0xFF0052FF)
    BlockchainNetwork.NEAR -> Color(0xFF000000)
}

// ═══════════════════════════════════════════════════════════════
//  Token management screen for a specific network
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ManageTokensForNetwork(network: BlockchainNetwork, onBack: () -> Unit) {
    val colors = WalletColors.current
    val allTokens = remember(network) { PopularTokens.getTokensForNetwork(network) }
    var searchQuery by remember { mutableStateOf("") }

    val enabledSet = remember {
        mutableStateMapOf<String, Boolean>().also { map ->
            allTokens.forEach { token ->
                map[token.contractAddress] = NetworkTokenPreferences.isTokenEnabled(
                    network, token.symbol, token.contractAddress
                )
            }
        }
    }

    val filtered = remember(allTokens, searchQuery) {
        if (searchQuery.isBlank()) allTokens
        else allTokens.filter {
            it.symbol.contains(searchQuery, ignoreCase = true) ||
                    it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(colors.bgPrimary).statusBarsPadding()) {
        Surface(elevation = 2.dp, color = colors.bgCard) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, stringResource(MR.strings.wallet_back_desc), tint = colors.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        network.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        stringResource(MR.strings.wallet_select_tokens_subtitle),
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                TextButton(onClick = {
                    allTokens.forEach { enabledSet[it.contractAddress] = true }
                    NetworkTokenPreferences.setAllTokensForNetwork(network, true)
                }) {
                    Text(stringResource(MR.strings.wallet_all_button), color = colors.accentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = {
                    allTokens.forEach { enabledSet[it.contractAddress] = false }
                    NetworkTokenPreferences.setAllTokensForNetwork(network, false)
                }) {
                    Text(stringResource(MR.strings.wallet_none_button), color = colors.accentRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.graySoft
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, stringResource(MR.strings.wallet_search_desc), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = colors.textPrimary),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(stringResource(MR.strings.wallet_search_tokens_placeholder), fontSize = 14.sp, color = colors.textHint)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(filtered, key = { "${it.network.name}_${it.contractAddress}" }) { token ->
                val isEnabled = enabledSet[token.contractAddress] ?: false
                TokenToggleRow(
                    token = token,
                    isEnabled = isEnabled,
                    onToggle = { checked ->
                        enabledSet[token.contractAddress] = checked
                        NetworkTokenPreferences.setTokenEnabled(
                            network, token.symbol, token.contractAddress, checked
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TokenToggleRow(token: WalletToken, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = WalletColors.current

    Card(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = colors.bgCard,
        elevation = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isEnabled) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TokenIconSmall(token)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    token.symbol,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) colors.textPrimary else colors.textHint
                )
                Text(
                    token.name,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${token.decimals} dec",
                fontSize = 10.sp,
                color = colors.textHint
            )
            Spacer(Modifier.width(10.dp))
            Checkbox(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.accentBlue,
                    uncheckedColor = colors.border
                )
            )
        }
    }
}

@Composable
private fun TokenIconSmall(token: WalletToken) {
    val icon = TokenIcons.getIcon(symbol = token.symbol)
    if (icon != null) {
        Image(
            painter = painterResource(icon),
            contentDescription = token.symbol,
            modifier = Modifier.size(36.dp).clip(CircleShape)
        )
    } else {
        val networkIcon = TokenIcons.getNetworkIcon(token.network)
        if (networkIcon != null) {
            Image(
                painter = painterResource(networkIcon),
                contentDescription = token.symbol,
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
        } else {
            val bgColor = networkFallbackColor(token.network)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(bgColor.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    token.symbol.take(2).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = bgColor
                )
            }
        }
    }
}

@Composable
fun ChooseNetworksOnboardingView(onDone: () -> Unit) {
    val colors = WalletColors.current
    val allNetworks = remember { BlockchainNetwork.ALL_SUPPORTED }
    val enabledMap = remember { mutableStateMapOf<BlockchainNetwork, Boolean>() }

    LaunchedEffect(Unit) {
        allNetworks.forEach { enabledMap[it] = false }
    }

    val selectedCount = enabledMap.count { it.value }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = colors.accentBlue
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(MR.strings.wallet_choose_networks_title),
                fontFamily = Manrope,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(MR.strings.wallet_choose_networks_subtitle),
                fontFamily = DMSans,
                fontSize = 14.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(allNetworks) { network ->
                val isEnabled = enabledMap[network] ?: false
                val totalTokenCount = PopularTokens.getTokensForNetwork(network).size

                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val newVal = !isEnabled
                            enabledMap[network] = newVal
                        },
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = if (isEnabled) colors.accentBlue.copy(alpha = 0.08f) else colors.bgCard,
                    elevation = if (isEnabled) 0.dp else 1.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkIconOnboarding(network)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                network.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEnabled) colors.textPrimary else colors.textHint
                            )
                            Text(
                                "${network.symbol} · $totalTokenCount tokens",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Checkbox(
                            checked = isEnabled,
                            onCheckedChange = { enabledMap[network] = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentBlue,
                                uncheckedColor = colors.border
                            )
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    enabledMap.forEach { (network, enabled) ->
                        if (enabled) {
                            NetworkTokenPreferences.setNetworkEnabled(network, true)
                            NetworkTokenPreferences.setAllTokensForNetwork(network, true)
                        }
                    }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    disabledBackgroundColor = colors.border,
                    disabledContentColor = colors.textHint
                ),
                enabled = selectedCount > 0,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Text(
                    if (selectedCount == 0) stringResource(MR.strings.wallet_select_at_least_one)
                    else String.format(stringResource(MR.strings.wallet_continue_with_networks), selectedCount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = DMSans
                )
            }
        }
    }
}

@Composable
private fun NetworkIconOnboarding(network: BlockchainNetwork) {
    val icon = TokenIcons.getNetworkIcon(network)
    if (icon != null) {
        Image(
            painter = painterResource(icon),
            contentDescription = network.displayName,
            modifier = Modifier.size(42.dp).clip(CircleShape)
        )
    } else {
        val bgColor = networkFallbackColor(network)
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                network.symbol.take(3),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onPrimary
            )
        }
    }
}
