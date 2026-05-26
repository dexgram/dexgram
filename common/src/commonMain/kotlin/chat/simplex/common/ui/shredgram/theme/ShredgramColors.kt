package chat.simplex.common.ui.shredgram.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ------------------------------------------------------------
// BRAND COLORS
// ------------------------------------------------------------

val ElectricBlue100 = Color(0xFFD2DBFF)
val ElectricBlue200 = Color(0xFFA5B7FF)
val ElectricBlue300 = Color(0xFF7893FF)
val ElectricBlue400 = Color(0xFF4B6FFF)
val ElectricBlue500 = Color(0xFF1F4CFF)
val ElectricBlue600 = Color(0xFF183CCC)
val ElectricBlue700 = Color(0xFF122D99)
val ElectricBlue800 = Color(0xFF0C1E66)
val ElectricBlue900 = Color(0xFF060F33)

val LimeGreen600 = Color(0xFF249C3F)
val LimeGreen700 = Color(0xFF1B752F)


// ------------------------------------------------------------
// NEUTRALS — DARK CHARCOAL SCALE
// ------------------------------------------------------------

val DarkCharcoal25 = Color(0xFFF3F3F3)
val DarkCharcoal50 = Color(0xFFE7E8E8)
val DarkCharcoal100 = Color(0xFFCECFD0)
val DarkCharcoal200 = Color(0xFFB7B8B9)
val DarkCharcoal300 = Color(0xFF9E9FA1)
val DarkCharcoal400 = Color(0xFF868889)
val DarkCharcoal500 = Color(0xFF6D7071)
val DarkCharcoal600 = Color(0xFF55585A)
val DarkCharcoal700 = Color(0xFF3D4042)
val DarkCharcoal800 = Color(0xFF25282B)
val DarkCharcoal900 = Color(0xFF0C1013)


// ------------------------------------------------------------
// SYSTEM — ERROR / WARNING / SUCCESS
// ------------------------------------------------------------

val Red500 = Color(0xFFD20D0D)
val Red600 = Color(0xFFA80A0A)
val Red400 = Color(0xFFE53935)
val Orange100 = Color(0xFFFFE1CC)
val Orange500 = Color(0xFFFF6404)
val Orange600 = Color(0xFFCC5003)
val Orange400 = Color(0xFFFF8A50)

val Green100 = Color(0xFFD6F5E0)
val Green500 = Color(0xFF11994A)
val Green400 = Color(0xFF43A047)


// ------------------------------------------------------------
// BASE COLORS
// ------------------------------------------------------------

val ShredgramWhite = Color(0xFFFFFFFF)
val ShredgramBlack = Color(0xFF000000)
val GraySlate500 = Color(0xFF47526A)


// ============================================================
//  LIGHT THEME SURFACE SYSTEM
// ============================================================

// Background
val SurfaceBG = ShredgramWhite

// Elevation levels
val SurfaceElevated0 = DarkCharcoal25
val SurfaceElevated1 = ShredgramWhite
val SurfaceElevated2 = DarkCharcoal50
val SurfaceElevated3 = DarkCharcoal300

// Inputs / Semantic states
val SurfaceInput = ShredgramWhite
val SurfaceWarning = Orange100
val SurfaceError = Red500
val SurfaceSuccess = Green100

// Borders
val BorderElevated1 = DarkCharcoal100
val BorderElevated2 = DarkCharcoal200
val BorderElevated3 = DarkCharcoal400

val BorderWarning = Orange500
val BorderError = Red600
val BorderSuccess = Green500
val BorderBrand = ElectricBlue500


// ============================================================
//  DARK THEME SURFACE SYSTEM
// ============================================================

// Background (surface-bg)
val DarkSurfaceBG = DarkCharcoal800

// Elevation levels
val DarkSurfaceElevated1 = Color(0xFF1E2633)  // Night 800
val DarkSurfaceElevated2 = Color(0xFF2A3344)  // Gray Slate 800
val DarkSurfaceElevated3 = Color(0xFF394458)  // Gray Slate 600

// Borders
val DarkBorderElevated1 = Color(0xFF2A3344)   // Night 700
val DarkBorderElevated2 = Color(0xFF353F51)   // Gray Slate 700
val DarkBorderElevated3 = GraySlate500

// Semantic surfaces
val DarkSurfaceWarning = Orange500
val DarkBorderWarning = Orange400

val DarkSurfaceError = Red500
val DarkBorderError = Red400

val DarkSurfaceSuccess = Green500
val DarkBorderSuccess = Green400


// ============================================================
// ON-COLORS (TEXT & ICONS)
// Light theme on-colors
// ============================================================

val OnPrimaryLight = ShredgramWhite
val OnSurfaceLight = DarkCharcoal900

val OnDisabledTextLight = DarkCharcoal200
val OnSurfaceSecondary = DarkCharcoal700
val OnErrorLight = ShredgramWhite
val OnWarningLight = Orange600
val OnSuccessLight = DarkCharcoal900

// ============================================================
// ON-COLORS (TEXT & ICONS)
// Dark theme on-colors
// ============================================================

val OnPrimaryDark = ShredgramBlack
val OnSurfaceDark = ShredgramWhite

val OnDisabledTextDark = GraySlate500
val OnSurfaceVariantDark = DarkCharcoal100
val OnErrorDark = ShredgramBlack
val OnWarningDark = Orange600
val OnSuccessDark = ShredgramBlack

// ============================================================
// COLOR SCHEME DATA CLASS
// ============================================================

data class ShredgramColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    // Custom colors
    val surfaceElevated0: Color,
    val surfaceElevated1: Color,
    val surfaceElevated2: Color,
    val surfaceElevated3: Color,
    val borderBrand: Color,
    val borderElevated1: Color,
    val borderElevated2: Color,
    val borderElevated3: Color,
    val surfaceWarning: Color,
    val surfaceError: Color,
    val surfaceSuccess: Color,
    val onWarning: Color,
    val onSuccess: Color,
    val onDisabledText: Color,
    val isLight: Boolean
)

val LightShredgramColors = ShredgramColorScheme(
    // Brand
    primary = ElectricBlue500,
    onPrimary = OnPrimaryLight,
    primaryContainer = ElectricBlue100,
    onPrimaryContainer = ElectricBlue700,
    // Secondary
    secondary = LimeGreen600,
    onSecondary = ShredgramWhite,
    secondaryContainer = LimeGreen700,
    onSecondaryContainer = ShredgramWhite,
    // Backgrounds
    background = SurfaceBG,
    onBackground = OnSurfaceLight,
    // Surfaces
    surface = SurfaceElevated1,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceElevated0,
    onSurfaceVariant = OnSurfaceSecondary,
    // Errors
    error = Red500,
    onError = OnErrorLight,
    errorContainer = Red600,
    onErrorContainer = OnErrorLight,
    // Outline / borders
    outline = BorderElevated2,
    outlineVariant = BorderElevated1,
    scrim = ShredgramBlack,
    // Custom
    surfaceElevated0 = SurfaceElevated0,
    surfaceElevated1 = SurfaceElevated1,
    surfaceElevated2 = SurfaceElevated2,
    surfaceElevated3 = SurfaceElevated3,
    borderBrand = BorderBrand,
    borderElevated1 = BorderElevated1,
    borderElevated2 = BorderElevated2,
    borderElevated3 = BorderElevated3,
    surfaceWarning = SurfaceWarning,
    surfaceError = SurfaceError,
    surfaceSuccess = SurfaceSuccess,
    onWarning = OnWarningLight,
    onSuccess = OnSuccessLight,
    onDisabledText = OnDisabledTextLight,
    isLight = true
)

val DarkShredgramColors = ShredgramColorScheme(
    // Brand
    primary = ElectricBlue400,
    onPrimary = OnPrimaryDark,
    primaryContainer = ElectricBlue700,
    onPrimaryContainer = ElectricBlue100,
    // Secondary
    secondary = LimeGreen600,
    onSecondary = ShredgramBlack,
    secondaryContainer = LimeGreen700,
    onSecondaryContainer = ShredgramBlack,
    // Backgrounds
    background = DarkSurfaceBG,
    onBackground = OnSurfaceDark,
    // Surfaces
    surface = DarkSurfaceElevated1,
    onSurface = OnSurfaceDark,
    surfaceVariant = DarkSurfaceElevated2,
    onSurfaceVariant = OnSurfaceVariantDark,
    // Errors
    error = Red500,
    onError = OnErrorDark,
    errorContainer = Red600,
    onErrorContainer = OnErrorDark,
    // Outline / borders
    outline = DarkBorderElevated2,
    outlineVariant = DarkBorderElevated1,
    scrim = ShredgramBlack,
    // Custom
    surfaceElevated0 = DarkSurfaceBG,
    surfaceElevated1 = DarkSurfaceElevated1,
    surfaceElevated2 = DarkSurfaceElevated2,
    surfaceElevated3 = DarkSurfaceElevated3,
    borderBrand = BorderBrand,
    borderElevated1 = DarkBorderElevated1,
    borderElevated2 = DarkBorderElevated2,
    borderElevated3 = DarkBorderElevated3,
    surfaceWarning = DarkSurfaceWarning,
    surfaceError = DarkSurfaceError,
    surfaceSuccess = DarkSurfaceSuccess,
    onWarning = OnWarningDark,
    onSuccess = OnSuccessDark,
    onDisabledText = OnDisabledTextDark,
    isLight = false
)

val LocalShredgramColors = staticCompositionLocalOf { LightShredgramColors }

val LocalIsLightTheme = staticCompositionLocalOf { true }

// Extension property to get warning text color
val ShredgramColorScheme.textWarning: Color
    get() = if (isLight) OnWarningLight else OnWarningDark

