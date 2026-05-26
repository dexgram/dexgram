package chat.simplex.common.ui.shredgram.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Shredgram Theme Wrapper
 * 
 * Provides the Shredgram design system colors, typography, and dimensions
 * to the composition tree.
 */
@Composable
fun ShredgramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkShredgramColors else LightShredgramColors
    
    CompositionLocalProvider(
        LocalShredgramColors provides colorScheme,
        LocalShredgramTypography provides ShredgramTypographyDefaults,
        LocalIsLightTheme provides !darkTheme,
        content = content
    )
}

/**
 * Accessor object for Shredgram theme values
 */
object ShredgramTheme {
    /**
     * Current color scheme
     */
    val colors: ShredgramColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalShredgramColors.current
    
    /**
     * Current typography
     */
    val typography: ShredgramTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalShredgramTypography.current
    
    /**
     * Whether the current theme is light
     */
    val isLight: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsLightTheme.current
}

