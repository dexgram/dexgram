package chat.simplex.common.ui.shredgram.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.ui.theme.DMSans

// ------------------------------------------------------------
// Font sizes
// ------------------------------------------------------------
val font12 = 12.sp
val font14 = 14.sp
val font16 = 16.sp
val font18 = 18.sp
val font20 = 20.sp
val font28 = 28.sp
val font32 = 32.sp
val font36 = 36.sp
val font40 = 40.sp
val font48 = 48.sp

// ------------------------------------------------------------
// Line height ratios
// ------------------------------------------------------------
const val lineHeightDisplay = 1.2f
const val lineHeightHeadlineL = 1.2f
const val lineHeightHeadlineM = 1.18f
const val lineHeightHeadlineS = 1.12f
const val lineHeightTitleL = 1.32f
const val lineHeightTitleM = 1.4f
const val lineHeightTitleS = 1.6f
const val lineHeightBody = 1.5f

// ------------------------------------------------------------
// Letter spacing
// ------------------------------------------------------------
val letterSpacingTight = (-0.02).em
val letterSpacingNormal = 0.em

// ------------------------------------------------------------
// Helper function
// ------------------------------------------------------------
fun lineHeight(fontSize: TextUnit, ratio: Float): TextUnit =
    (fontSize.value * ratio).sp

/**
 * Shredgram Typography System
 * 
 * Note: The original design uses Manrope for headlines/titles and DM Sans for body/labels.
 * For multiplatform compatibility, we're using Inter as the default font which provides
 * similar visual characteristics. Custom fonts can be added later via moko-resources.
 */
data class ShredgramTypography(
    // DISPLAY — ExtraBold
    val displayLarge: TextStyle,
    
    // HEADLINES — Bold
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    
    // TITLES — Bold
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val titleExtraSmall: TextStyle,
    
    // BODY — Normal
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val bodyExtraSmall: TextStyle,
    
    // BODY BOLD
    val bodyLargeBold: TextStyle,
    
    // LABELS — Medium
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

// Shredgram font families - EXACT match from Shredgram Typography.kt
// For headlines and titles - Manrope
val HeadlineFont: FontFamily = Manrope
// For body and labels - DMSans
val BodyFont: FontFamily = DMSans

val ShredgramTypographyDefaults = ShredgramTypography(
    // DISPLAY — ExtraBold
    displayLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = font48,
        lineHeight = lineHeight(font48, lineHeightDisplay),
        letterSpacing = letterSpacingTight,
    ),
    
    // HEADLINES — Bold
    headlineLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font40,
        lineHeight = lineHeight(font40, lineHeightHeadlineL),
        letterSpacing = letterSpacingTight,
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font36,
        lineHeight = lineHeight(font36, lineHeightHeadlineM),
        letterSpacing = letterSpacingTight,
    ),
    headlineSmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font32,
        lineHeight = lineHeight(font32, lineHeightHeadlineS),
        letterSpacing = letterSpacingTight,
    ),
    
    // TITLES — Bold
    titleLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font28,
        lineHeight = lineHeight(font28, lineHeightTitleL),
        letterSpacing = letterSpacingTight,
    ),
    titleMedium = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font20,
        lineHeight = lineHeight(font20, lineHeightTitleM),
        letterSpacing = letterSpacingNormal,
    ),
    titleSmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font18,
        lineHeight = lineHeight(font18, lineHeightTitleS),
        letterSpacing = letterSpacingNormal,
    ),
    titleExtraSmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = font16,
        lineHeight = lineHeight(font16, lineHeightBody),
    ),
    
    // BODY — Normal
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = font18,
        lineHeight = lineHeight(font18, lineHeightBody),
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = font16,
        lineHeight = lineHeight(font16, lineHeightBody),
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = font14,
        lineHeight = lineHeight(font14, lineHeightBody),
    ),
    bodyExtraSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = font12,
        lineHeight = lineHeight(font12, lineHeightBody),
    ),
    
    // BODY BOLD
    bodyLargeBold = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = font18,
        lineHeight = lineHeight(font18, lineHeightBody),
    ),
    
    // LABELS — Medium
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = font16,
        lineHeight = lineHeight(font16, lineHeightBody),
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = font14,
        lineHeight = lineHeight(font14, lineHeightBody),
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = font12,
        lineHeight = lineHeight(font12, lineHeightBody),
    ),
)

val LocalShredgramTypography = staticCompositionLocalOf { ShredgramTypographyDefaults }

