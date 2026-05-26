package chat.simplex.common.views.wallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import chat.simplex.common.ui.theme.isInDarkTheme

/**
 * Centralized wallet color palette with light/dark mode support.
 * All wallet screens should use [WalletColors] instead of hardcoded Color values.
 */
data class WalletColorScheme(
    val bgPrimary: Color,
    val bgCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val accentBlue: Color,
    val accentGold: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentOrange: Color,
    val accentPurple: Color,
    val border: Color,
    val divider: Color,
    val disabledBg: Color,
    val disabledContent: Color,
    val greenSoft: Color,
    val redSoft: Color,
    val blueSoft: Color,
    val amberSoft: Color,
    val graySoft: Color,
)

private val LightWalletColors = WalletColorScheme(
    bgPrimary = Color(0xFFF8FAFC),
    bgCard = Color.White,
    textPrimary = Color(0xFF1E293B),
    textSecondary = Color(0xFF64748B),
    textHint = Color(0xFF9CA3AF),
    accentBlue = Color(0xFF3B82F6),
    accentGold = Color(0xFFF0B90B),
    accentGreen = Color(0xFF10B981),
    accentRed = Color(0xFFEF4444),
    accentOrange = Color(0xFFF59E0B),
    accentPurple = Color(0xFF8B5CF6),
    border = Color(0xFFE2E8F0),
    divider = Color(0xFFF0F1F3),
    disabledBg = Color(0xFFCECFD0),
    disabledContent = Color(0xFF868889),
    greenSoft = Color(0xFFECFDF5),
    redSoft = Color(0xFFFEF2F2),
    blueSoft = Color(0xFFEFF6FF),
    amberSoft = Color(0xFFFFFBEB),
    graySoft = Color(0xFFF3F4F6),
)

private val DarkWalletColors = WalletColorScheme(
    bgPrimary = Color(0xFF0F1118),
    bgCard = Color(0xFF1A1D2E),
    textPrimary = Color(0xFFE2E8F0),
    textSecondary = Color(0xFF94A3B8),
    textHint = Color(0xFF64748B),
    accentBlue = Color(0xFF60A5FA),
    accentGold = Color(0xFFFCD34D),
    accentGreen = Color(0xFF34D399),
    accentRed = Color(0xFFF87171),
    accentOrange = Color(0xFFFBBF24),
    accentPurple = Color(0xFFA78BFA),
    border = Color(0xFF2D3348),
    divider = Color(0xFF252A3A),
    disabledBg = Color(0xFF374151),
    disabledContent = Color(0xFF6B7280),
    greenSoft = Color(0xFF064E3B),
    redSoft = Color(0xFF7F1D1D),
    blueSoft = Color(0xFF1E3A5F),
    amberSoft = Color(0xFF78350F),
    graySoft = Color(0xFF1F2937),
)

object WalletColors {
    val current: WalletColorScheme
        @Composable
        get() = if (isInDarkTheme()) DarkWalletColors else LightWalletColors
}
