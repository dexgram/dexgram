package chat.simplex.common.views.wallet

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Clean color palette - Binance inspired
private val BgPrimary @Composable get() = WalletColors.current.bgPrimary
private val BgCard @Composable get() = WalletColors.current.bgCard
private val TextPrimary @Composable get() = WalletColors.current.textPrimary
private val TextSecondary @Composable get() = WalletColors.current.textSecondary
private val Green @Composable get() = WalletColors.current.accentGreen
private val Red @Composable get() = WalletColors.current.accentRed
private val Yellow @Composable get() = WalletColors.current.accentGold
private val Border @Composable get() = WalletColors.current.border
private val ErrorRed @Composable get() = WalletColors.current.accentRed

// ============================================================================
// SHIMMER LOADING EFFECT
// ============================================================================

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    return Brush.linearGradient(
        colors = listOf(
            Border.copy(alpha = 0.6f),
            Border.copy(alpha = 0.2f),
            Border.copy(alpha = 0.6f)
        ),
        start = Offset(translateAnim - 500, 0f),
        end = Offset(translateAnim, 0f)
    )
}

@Composable
private fun CoinRowSkeleton() {
    val shimmerBrush = rememberShimmerBrush()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank skeleton
        Box(
            Modifier
                .width(24.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        Spacer(Modifier.width(12.dp))
        
        // Icon skeleton
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(shimmerBrush)
        )
        Spacer(Modifier.width(10.dp))
        
        // Name skeleton
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .width(50.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(70.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
        
        // Price skeleton
        Box(
            Modifier
                .width(65.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        Spacer(Modifier.width(8.dp))
        
        // Change skeleton
        Box(
            Modifier
                .width(50.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        Spacer(Modifier.width(8.dp))
        
        // Chart skeleton
        Box(
            Modifier
                .width(60.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
    }
    
    Divider(color = Border, modifier = Modifier.padding(start = 52.dp))
}

@Composable
private fun LoadingSkeletonList() {
    Column {
        repeat(10) {
            CoinRowSkeleton()
        }
    }
}

// ============================================================================
// ERROR BANNER
// ============================================================================

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorRed.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = ErrorRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            fontSize = 12.sp,
            color = ErrorRed,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRetry()
            }
        ) {
            Text(stringResource(MR.strings.wallet_retry_button), color = ErrorRed, fontSize = 12.sp)
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
        }
    }
}

// ============================================================================
// COIN LOGO (uses shared TokenIcons utility)
// ============================================================================

@Composable
private fun CoinLogo(coin: CryptoCoin, size: Dp) {
    val res = TokenIcons.getIcon(coinId = coin.id, symbol = coin.symbol)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = coin.symbol,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        // Fallback if logo missing
        val sizeValue = size.value
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Yellow.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                coin.symbol.take(1),
                fontSize = (sizeValue * 0.45f).sp,
                fontWeight = FontWeight.Bold,
                color = Yellow
            )
        }
    }
}

// ============================================================================
// FAVORITES MANAGEMENT
// ============================================================================

private object FavoritesManager {
    private val favorites = mutableStateOf(setOf<String>())
    
    fun isFavorite(coinId: String): Boolean = coinId in favorites.value
    
    fun toggle(coinId: String) {
        favorites.value = if (coinId in favorites.value) {
            favorites.value - coinId
        } else {
            favorites.value + coinId
        }
    }
    
    fun getFavorites(): Set<String> = favorites.value
}

// ============================================================================
// MAIN VIEWS
// ============================================================================

@Composable
fun MarketView(close: () -> Unit) {
    ModalView(close = close, showAppBar = false) {
        MarketScreen(close)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MarketScreen(close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var coins by remember { mutableStateOf<List<CryptoCoin>>(emptyList()) }
    var globalData by remember { mutableStateOf(GlobalMarketData()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCoin by remember { mutableStateOf<CryptoCoin?>(null) }
    var sortBy by remember { mutableStateOf("rank") }
    var sortAsc by remember { mutableStateOf(true) }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLiveData by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableStateOf("") } // Shows loading progress like "50 coins..."

    // Handle Android system back gesture
    BackHandler(enabled = true) {
        if (selectedCoin != null) {
            selectedCoin = null
        } else {
            close()
        }
    }
    
    // Progressive refresh function - shows coins as they load
    suspend fun refreshDataProgressively(showLoadingIndicator: Boolean = false, forceRefresh: Boolean = false) {
        if (showLoadingIndicator) isRefreshing = true
        errorMessage = null
        
        // Fetch global data in parallel
        scope.launch { 
            globalData = CryptoPriceService.fetchGlobalMarketData() 
        }
        
        // Progressive loading - emit coins in batches
        CryptoPriceService.fetchMarketDataProgressively(forceRefresh = forceRefresh)
            .collect { state ->
                when (state) {
                    is CryptoPriceService.ProgressiveLoadState.Batch -> {
                        coins = state.coins
                        isLoading = false
                        isLiveData = !state.isCached
                        loadingProgress = if (state.isComplete) "" else "Loading ${state.coins.size} coins..."
                    }
                    is CryptoPriceService.ProgressiveLoadState.Complete -> {
                        coins = state.coins
                        isLoading = false
                        isRefreshing = false
                        isLiveData = !state.isFromCache
                        loadingProgress = ""
                        errorMessage = null
                    }
                    is CryptoPriceService.ProgressiveLoadState.Error -> {
                        isLoading = false
                        isRefreshing = false
                        loadingProgress = ""
                        
                        if (state.cachedData != null) {
                            coins = state.cachedData
                            isLiveData = false
                            errorMessage = generalGetString(MR.strings.wallet_market_cached_data_error)
                        } else if (coins.isEmpty()) {
                            coins = CryptoPriceService.getMockData()
                            isLiveData = false
                            errorMessage = generalGetString(MR.strings.wallet_market_sample_data_error)
                        }
                    }
                }
            }
    }
    
    // Legacy refresh for compatibility
    suspend fun refreshData(showLoadingIndicator: Boolean = false) {
        refreshDataProgressively(showLoadingIndicator, forceRefresh = showLoadingIndicator)
    }
    
    // Initial load - progressive
    LaunchedEffect(Unit) {
        isLoading = true
        refreshDataProgressively()
    }
    
    // Lifecycle-aware auto-refresh (fixes memory leak)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60000)
            if (isActive) {
                refreshDataProgressively()
            }
        }
    }
    
    // Memoized filtered and sorted coins using derivedStateOf
    val filteredCoins by remember(coins, searchQuery, sortBy, sortAsc, showOnlyFavorites) {
        derivedStateOf {
            var filtered = if (searchQuery.isBlank()) coins
            else coins.filter {
                it.name.contains(searchQuery, true) || it.symbol.contains(searchQuery, true)
            }
            
            if (showOnlyFavorites) {
                filtered = filtered.filter { FavoritesManager.isFavorite(it.id) }
            }
            
            val sorted = when (sortBy) {
                "price" -> filtered.sortedBy { it.currentPrice }
                "change" -> filtered.sortedBy { it.priceChangePercentage24h }
                else -> filtered.sortedBy { it.marketCapRank }
            }
            
            if (sortAsc) sorted else sorted.reversed()
        }
    }
    
    // Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { refreshData(showLoadingIndicator = true) }
        }
    )
    
    if (selectedCoin != null) {
        CoinDetailScreen(coin = selectedCoin!!, onBack = { selectedCoin = null })
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPrimary)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                TopBar(
                    close = close,
                    globalData = globalData,
                    isLive = isLiveData,
                    loadingProgress = loadingProgress,
                    showOnlyFavorites = showOnlyFavorites,
                    onToggleFavorites = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showOnlyFavorites = !showOnlyFavorites
                    }
                )
                
                // Error banner
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    errorMessage?.let { msg ->
                        ErrorBanner(
                            message = msg,
                            onRetry = {
                                scope.launch { refreshData(showLoadingIndicator = true) }
                            },
                            onDismiss = { errorMessage = null }
                        )
                    }
                }
                
                // Search
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
                
                if (isLoading) {
                    LoadingSkeletonList()
                } else {
                    // Column headers
                    ListHeader(
                        sortBy = sortBy,
                        sortAsc = sortAsc,
                        onSortChange = { newSort ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (sortBy == newSort) sortAsc = !sortAsc
                            else { sortBy = newSort; sortAsc = true }
                        }
                    )
                    
                    // Coin list with proper keys for performance
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = filteredCoins,
                            key = { it.id }
                        ) { coin ->
                            CoinRow(
                                coin = coin,
                                isFavorite = FavoritesManager.isFavorite(coin.id),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedCoin = coin
                                },
                                onFavoriteClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    FavoritesManager.toggle(coin.id)
                                }
                            )
                        }
                        
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
            
            // Pull-to-refresh indicator
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = BgCard,
                contentColor = Yellow
            )
        }
    }
}

@Composable
private fun TopBar(
    close: () -> Unit,
    globalData: GlobalMarketData,
    isLive: Boolean,
    loadingProgress: String = "",
    showOnlyFavorites: Boolean,
    onToggleFavorites: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    close()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(MR.strings.wallet_markets_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                // Show loading progress when fetching more coins
                AnimatedVisibility(
                    visible = loadingProgress.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = loadingProgress,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            
            // Favorites filter button
            IconButton(
                onClick = onToggleFavorites,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (showOnlyFavorites) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(MR.strings.wallet_filter_favorites_desc),
                    tint = if (showOnlyFavorites) Yellow else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(4.dp))
            
            // Live indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (isLive) Green.copy(0.1f) else TextSecondary.copy(0.1f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Pulsing dot for live indicator
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isLive) Green.copy(alpha) else TextSecondary)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isLive) stringResource(MR.strings.wallet_live_indicator) else stringResource(MR.strings.wallet_cached_indicator),
                    fontSize = 11.sp,
                    color = if (isLive) Green else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Market stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(
                label = stringResource(MR.strings.wallet_market_cap_label),
                value = CryptoPriceService.formatLargeNumber(globalData.totalMarketCap),
                change = globalData.marketCapChange24h
            )
            StatItem(
                label = stringResource(MR.strings.wallet_btc_dominance_label),
                value = "${String.format("%.1f", globalData.btcDominance)}%"
            )
            StatItem(
                label = stringResource(MR.strings.wallet_fear_greed_label),
                value = "${globalData.fearGreedIndex}",
                badge = when {
                    globalData.fearGreedIndex < 25 -> stringResource(MR.strings.wallet_extreme_fear)
                    globalData.fearGreedIndex < 45 -> stringResource(MR.strings.wallet_fear)
                    globalData.fearGreedIndex < 55 -> stringResource(MR.strings.wallet_neutral)
                    globalData.fearGreedIndex < 75 -> stringResource(MR.strings.wallet_greed)
                    else -> stringResource(MR.strings.wallet_extreme_greed)
                }
            )
        }
    }
    
    Divider(color = Border, thickness = 1.dp)
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    change: Double? = null,
    badge: String? = null
) {
    Column {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            
            if (change != null) {
                Spacer(Modifier.width(4.dp))
                val color = if (change >= 0) Green else Red
                Text(
                    "${if (change >= 0) "+" else ""}${String.format("%.2f", change)}%",
                    fontSize = 11.sp,
                    color = color
                )
            }
            
            if (badge != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    badge,
                    fontSize = 10.sp,
                    color = Yellow,
                    modifier = Modifier
                        .background(Yellow.copy(0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(MR.strings.wallet_search_coins_placeholder), color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = BgCard,
            focusedBorderColor = Yellow,
            unfocusedBorderColor = Border,
            textColor = TextPrimary,
            cursorColor = Yellow
        )
    )
}

@Composable
private fun ListHeader(
    sortBy: String,
    sortAsc: Boolean,
    onSortChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Star column for favorites
        Spacer(Modifier.width(28.dp))
        SortableHeader(stringResource(MR.strings.wallet_rank_header), "rank", sortBy, sortAsc, onSortChange, Modifier.width(32.dp))
        Text(stringResource(MR.strings.wallet_name_header), fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        SortableHeader(stringResource(MR.strings.wallet_price_header), "price", sortBy, sortAsc, onSortChange, Modifier.width(85.dp), TextAlign.End)
        SortableHeader(stringResource(MR.strings.wallet_24h_change_header), "change", sortBy, sortAsc, onSortChange, Modifier.width(65.dp), TextAlign.End)
        Text(stringResource(MR.strings.wallet_7d_header), fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(55.dp), textAlign = TextAlign.End)
    }
    Divider(color = Border)
}

@Composable
private fun SortableHeader(
    text: String,
    key: String,
    currentSort: String,
    sortAsc: Boolean,
    onSort: (String) -> Unit,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start
) {
    Row(
        modifier = modifier.clickable { onSort(key) },
        horizontalArrangement = if (align == TextAlign.End) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.sp, color = if (currentSort == key) Yellow else TextSecondary)
        if (currentSort == key) {
            Icon(
                if (sortAsc) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null,
                tint = Yellow,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CoinRow(
    coin: CryptoCoin,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val isPositive = coin.priceChangePercentage24h >= 0
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(BgCard)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favorite star
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(MR.strings.wallet_favorite_desc),
                tint = if (isFavorite) Yellow else TextSecondary.copy(0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Rank
        Text(
            "${coin.marketCapRank}",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.width(32.dp)
        )
        
        // Icon + Name
        CoinLogo(coin = coin, size = 30.dp)
        
        Spacer(Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                coin.symbol,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                coin.name,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Price
        Text(
            CryptoPriceService.formatPrice(coin.currentPrice),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.width(85.dp),
            textAlign = TextAlign.End
        )
        
        // 24h change
        Text(
            "${if (isPositive) "+" else ""}${String.format("%.2f", coin.priceChangePercentage24h)}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPositive) Green else Red,
            modifier = Modifier.width(65.dp),
            textAlign = TextAlign.End
        )
        
        // Mini chart with animation
        AnimatedMiniChart(
            data = coin.sparklineIn7d,
            isPositive = isPositive,
            modifier = Modifier.width(55.dp).height(24.dp)
        )
    }
    
    Divider(color = Border, modifier = Modifier.padding(start = 52.dp))
}

@Composable
private fun AnimatedMiniChart(data: List<Double>, isPositive: Boolean, modifier: Modifier) {
    val color = if (isPositive) Green else Red
    
    // Animate the chart drawing
    var animationProgress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(data) {
        animationProgress = 0f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animationProgress = value
        }
    }
    
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        
        val min = data.minOrNull() ?: return@Canvas
        val max = data.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        
        val path = Path()
        val visiblePoints = (data.size * animationProgress).toInt().coerceAtLeast(2)
        val step = size.width / (data.size - 1)
        
        data.take(visiblePoints).forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - ((v - min) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ============================================================================
// COIN DETAIL SCREEN - Binance Style
// ============================================================================

@Composable
private fun CoinDetailScreen(coin: CryptoCoin, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var chartPoints by remember { mutableStateOf<List<CryptoPriceService.ChartPoint>>(emptyList()) }
    var selectedTimeframe by remember { mutableStateOf("24H") }
    var isLoadingChart by remember { mutableStateOf(true) }
    var priceChange by remember { mutableStateOf(coin.priceChangePercentage24h) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var isFavorite by remember { mutableStateOf(FavoritesManager.isFavorite(coin.id)) }
    
    // Fetch real chart data when timeframe changes
    LaunchedEffect(selectedTimeframe) {
        isLoadingChart = true
        selectedIndex = null
        
        // Fetch actual data from API
        val points = CryptoPriceService.fetchExtendedChartData(coin.id, selectedTimeframe)
        
        if (points.isNotEmpty()) {
            chartPoints = points
            
            // Calculate actual price change from data
            if (points.size >= 2) {
                val first = points.first().price
                val last = points.last().price
                priceChange = ((last - first) / first) * 100
            }
        } else {
            // Fallback: generate from sparkline with timestamps
            val now = System.currentTimeMillis()
            val sparkline = coin.sparklineIn7d.ifEmpty { 
                listOf(coin.currentPrice * 0.98, coin.currentPrice * 0.99, coin.currentPrice)
            }
            
            val data = when (selectedTimeframe) {
                "1H" -> sparkline.takeLast(12)
                "24H" -> sparkline.takeLast(24)
                "7D" -> sparkline
                else -> sparkline
            }
            
            val intervalMs = when (selectedTimeframe) {
                "1H" -> 5 * 60 * 1000L
                "24H" -> 60 * 60 * 1000L
                "7D" -> 60 * 60 * 1000L
                "1M" -> 24 * 60 * 60 * 1000L
                "1Y" -> 24 * 60 * 60 * 1000L
                else -> 60 * 60 * 1000L
            }
            
            chartPoints = data.mapIndexed { i, price ->
                CryptoPriceService.ChartPoint(
                    timestamp = now - (data.size - i) * intervalMs,
                    price = price
                )
            }
            
            priceChange = when (selectedTimeframe) {
                "1H" -> coin.priceChangePercentage1h
                "24H" -> coin.priceChangePercentage24h
                "7D" -> coin.priceChangePercentage7d
                else -> coin.priceChangePercentage24h
            }
        }
        
        isLoadingChart = false
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
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            }) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            
            CoinLogo(coin = coin, size = 36.dp)
            
            Spacer(Modifier.width(10.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(coin.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("/USDT", fontSize = 14.sp, color = TextSecondary)
                }
                Text(coin.name, fontSize = 12.sp, color = TextSecondary)
            }
            
            // Favorite button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    FavoritesManager.toggle(coin.id)
                    isFavorite = !isFavorite
                }
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(MR.strings.wallet_favorite_desc),
                    tint = if (isFavorite) Yellow else TextSecondary
                )
            }
            
            // Rank
            Text(
                "#${coin.marketCapRank}",
                fontSize = 12.sp,
                color = Yellow,
                modifier = Modifier
                    .background(Yellow.copy(0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        Divider(color = Border)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Price section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(16.dp)
            ) {
                Text(
                    CryptoPriceService.formatPrice(coin.currentPrice),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPositive = priceChange >= 0
                    val changeColor = if (isPositive) Green else Red
                    
                    Icon(
                        if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        null,
                        tint = changeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${if (isPositive) "+" else ""}${String.format("%.2f", priceChange)}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = changeColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(selectedTimeframe, fontSize = 12.sp, color = TextSecondary)
                }
            }
            
            // Chart
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(16.dp)
            ) {
                // Timeframe selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("1H", "24H", "7D", "1M", "1Y").forEach { tf ->
                        TimeframeButton(
                            text = tf,
                            isSelected = selectedTimeframe == tf,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTimeframe = tf
                            }
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Scrollable Chart with touch interaction
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    if (isLoadingChart) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Yellow, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        // Tooltip showing price and time
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedIndex != null && selectedIndex!! < chartPoints.size) {
                                val point = chartPoints[selectedIndex!!]
                                
                                val dateFormat = when (selectedTimeframe) {
                                    "1H", "24H" -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                    "7D" -> SimpleDateFormat("EEE, MMM dd HH:mm", Locale.getDefault())
                                    else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .background(TextPrimary, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        CryptoPriceService.formatPrice(point.price),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BgCard
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        dateFormat.format(Date(point.timestamp)),
                                        fontSize = 12.sp,
                                        color = BgCard.copy(0.7f)
                                    )
                                }
                            } else {
                                // Show data info when not selecting
                                Text(
                                    "${chartPoints.size} data points • Swipe to scroll",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        
                        // Scrollable Interactive chart
                        ScrollableChart(
                            points = chartPoints,
                            isPositive = priceChange >= 0,
                            selectedIndex = selectedIndex,
                            onIndexChange = { selectedIndex = it },
                            timeframe = selectedTimeframe,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(16.dp)
            ) {
                Text(stringResource(MR.strings.wallet_statistics_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                
                // 24h range bar
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(stringResource(MR.strings.wallet_24h_low_label), fontSize = 11.sp, color = TextSecondary)
                        Text(stringResource(MR.strings.wallet_24h_high_label), fontSize = 11.sp, color = TextSecondary)
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    val range = coin.high24h - coin.low24h
                    val position = if (range > 0) ((coin.currentPrice - coin.low24h) / range).toFloat().coerceIn(0f, 1f) else 0.5f
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Border)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(position)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(Red, Yellow, Green))
                                )
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(CryptoPriceService.formatPrice(coin.low24h), fontSize = 12.sp, color = Red)
                        Text(CryptoPriceService.formatPrice(coin.high24h), fontSize = 12.sp, color = Green)
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                StatRow(stringResource(MR.strings.wallet_stat_market_cap), CryptoPriceService.formatLargeNumber(coin.marketCap))
                StatRow(stringResource(MR.strings.wallet_stat_24h_volume), CryptoPriceService.formatLargeNumber(coin.totalVolume))
                StatRow(stringResource(MR.strings.wallet_stat_circulating_supply), "${CryptoPriceService.formatCompactNumber(coin.circulatingSupply.toLong())} ${coin.symbol}")
                coin.maxSupply?.let {
                    StatRow(stringResource(MR.strings.wallet_stat_max_supply), "${CryptoPriceService.formatCompactNumber(it.toLong())} ${coin.symbol}")
                }
                StatRow(stringResource(MR.strings.wallet_stat_all_time_high), CryptoPriceService.formatPrice(coin.ath), "${String.format("%.1f", coin.athChangePercentage)}%")
            }
            
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun TimeframeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        if (isSelected) Yellow else Color.Transparent,
        animationSpec = tween(200),
        label = "timeframeBg"
    )
    val textColor by animateColorAsState(
        if (isSelected) TextPrimary else TextSecondary,
        animationSpec = tween(200),
        label = "timeframeText"
    )
    
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ScrollableChart(
    points: List<CryptoPriceService.ChartPoint>,
    isPositive: Boolean,
    selectedIndex: Int?,
    onIndexChange: (Int?) -> Unit,
    timeframe: String,
    modifier: Modifier
) {
    val haptic = LocalHapticFeedback.current
    val lineColor = if (isPositive) Green else Red
    val fillColor = lineColor.copy(alpha = 0.12f)
    val crosshairColor = TextSecondary
    val gridColor = WalletColors.current.divider
    val dotCenterColor = BgCard

    // Calculate chart width based on data points
    val pointWidth = when (timeframe) {
        "1H" -> 24.dp
        "24H" -> 12.dp
        "7D" -> 4.dp
        "1M" -> 8.dp
        "1Y" -> 3.dp
        else -> 8.dp
    }
    
    val minChartWidth = 400.dp
    val calculatedWidth = (points.size * pointWidth.value).dp
    val chartContentWidth = maxOf(minChartWidth, calculatedWidth)
    
    val scrollState = rememberScrollState()
    
    // Auto-scroll to end (latest data) on load
    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    // Animate chart drawing
    var animationProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(points) {
        animationProgress = 0f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animationProgress = value
        }
    }
    
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartContentWidth)
                    .fillMaxHeight()
                    .pointerInput(points) {
                        detectTapGestures(
                            onPress = { offset ->
                                if (points.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val padding = 8.dp.toPx()
                                    val cWidth = size.width - padding * 2
                                    val step = cWidth / (points.size - 1).coerceAtLeast(1)
                                    val x = (offset.x - padding).coerceIn(0f, cWidth)
                                    val index = (x / step).toInt().coerceIn(0, points.size - 1)
                                    onIndexChange(index)
                                    
                                    tryAwaitRelease()
                                    onIndexChange(null)
                                }
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (points.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val padding = 8.dp.toPx()
                                    val cWidth = size.width - padding * 2
                                    val step = cWidth / (points.size - 1).coerceAtLeast(1)
                                    val x = (offset.x - padding).coerceIn(0f, cWidth)
                                    val index = (x / step).toInt().coerceIn(0, points.size - 1)
                                    onIndexChange(index)
                                }
                            },
                            onDrag = { change, _ ->
                                if (points.isNotEmpty()) {
                                    val padding = 8.dp.toPx()
                                    val cWidth = size.width - padding * 2
                                    val step = cWidth / (points.size - 1).coerceAtLeast(1)
                                    val x = (change.position.x - padding).coerceIn(0f, cWidth)
                                    val index = (x / step).toInt().coerceIn(0, points.size - 1)
                                    onIndexChange(index)
                                }
                            },
                            onDragEnd = { onIndexChange(null) },
                            onDragCancel = { onIndexChange(null) }
                        )
                    }
            ) {
                if (points.size < 2) return@Canvas
                
                val prices = points.map { it.price }
                val min = prices.minOrNull() ?: return@Canvas
                val max = prices.maxOrNull() ?: return@Canvas
                val range = (max - min).takeIf { it > 0 } ?: 1.0
                
                val padding = 8.dp.toPx()
                val cWidth = size.width - padding * 2
                val cHeight = size.height - padding * 2
                val step = cWidth / (points.size - 1).coerceAtLeast(1)
                
                // Draw grid
                for (i in 0..4) {
                    val y = padding + (cHeight / 4) * i
                    drawLine(gridColor, Offset(padding, y), Offset(size.width - padding, y), 1.dp.toPx())
                }
                
                // Limit visible points based on animation
                val visiblePointCount = (points.size * animationProgress).toInt().coerceAtLeast(2)
                val visiblePoints = points.take(visiblePointCount)
                
                // Create paths
                val linePath = Path()
                val fillPath = Path()
                
                visiblePoints.forEachIndexed { i, point ->
                    val x = padding + i * step
                    val y = padding + cHeight - ((point.price - min) / range * cHeight).toFloat()
                    
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, padding + cHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                
                // Close fill path
                val lastVisibleX = padding + (visiblePointCount - 1) * step
                fillPath.lineTo(lastVisibleX, padding + cHeight)
                fillPath.close()
                
                // Draw gradient fill
                drawPath(fillPath, fillColor)
                
                // Draw line
                drawPath(linePath, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                
                // Draw selected point crosshair
                if (selectedIndex != null && selectedIndex < points.size) {
                    val selX = padding + selectedIndex * step
                    val selY = padding + cHeight - ((points[selectedIndex].price - min) / range * cHeight).toFloat()
                    
                    // Vertical line
                    drawLine(
                        color = crosshairColor,
                        start = Offset(selX, padding),
                        end = Offset(selX, padding + cHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Horizontal line
                    drawLine(
                        color = crosshairColor.copy(0.5f),
                        start = Offset(padding, selY),
                        end = Offset(padding + cWidth, selY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    
                    // Crosshair dot
                    drawCircle(lineColor, 8.dp.toPx(), Offset(selX, selY))
                    drawCircle(dotCenterColor, 4.dp.toPx(), Offset(selX, selY))
                } else if (animationProgress >= 1f) {
                    // Draw current price dot at end (only after animation completes)
                    val lastX = padding + (points.size - 1) * step
                    val lastY = padding + cHeight - ((points.last().price - min) / range * cHeight).toFloat()
                    
                    // Pulsing effect for current price
                    drawCircle(lineColor.copy(0.3f), 10.dp.toPx(), Offset(lastX, lastY))
                    drawCircle(lineColor, 5.dp.toPx(), Offset(lastX, lastY))
                    drawCircle(dotCenterColor, 2.5.dp.toPx(), Offset(lastX, lastY))
                }
            }
        }
        
        // Scroll indicators
        if (scrollState.value < scrollState.maxValue - 50) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(24.dp)
                    .background(BgCard.copy(0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        if (scrollState.value > 50) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .size(24.dp)
                    .background(BgCard.copy(0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, change: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (change != null) {
                Spacer(Modifier.width(6.dp))
                val isNegative = change.startsWith("-")
                Text(
                    change,
                    fontSize = 11.sp,
                    color = if (isNegative) Red else Green
                )
            }
        }
    }
    Divider(color = Border)
}
