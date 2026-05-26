package chat.simplex.common.views.helpers

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource

/**
 * Shredgram-style spinning indicator
 * Uses the exact same SVG and animation as the Shredgram project
 * 
 * @param modifier Modifier for the spinner
 * @param size Size of the spinner (default 40.dp)
 * @param durationMillis Rotation duration in milliseconds (default 900)
 */
@Composable
fun ShredgramSpinningIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    durationMillis: Int = 900
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Use Image so it keeps the SVG colors (white), no tinting
    Image(
        painter = painterResource(MR.images.ic_circular_spinner),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    )
}

/**
 * Shredgram-style full screen progress overlay
 * Foggy scrim background with spinning indicator
 * 
 * Uses Figma color: #0C1013 at 50% opacity
 */
@Composable
fun ShredgramProgressOverlay() {
    val interaction = remember { MutableInteractionSource() }
    
    // Foggy scrim color from Figma: #0C1013 at 50% opacity
    val scrimColor = Color(0x800C1013)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { /* consume touches */ },
        contentAlignment = Alignment.Center
    ) {
        ShredgramSpinningIndicator()
    }
}

/**
 * Inline Shredgram spinner for use within content (not full screen)
 * Just the spinner without overlay
 * 
 * @param modifier Modifier for the spinner
 * @param size Size of the spinner (default 48.dp)
 */
@Composable
fun ShredgramInlineSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    ShredgramSpinningIndicator(
        modifier = modifier,
        size = size
    )
}

/**
 * Controller for Shredgram loading overlay
 * Allows showing/hiding the loading state from anywhere in the wrapped content
 */
@Stable
class ShredgramLoadingController internal constructor(
    private val setLoading: (Boolean) -> Unit
) {
    fun show() = setLoading(true)
    fun hide() = setLoading(false)
    fun set(value: Boolean) = setLoading(value)
}

/**
 * CompositionLocal for accessing ShredgramLoadingController
 */
val LocalShredgramLoadingController = staticCompositionLocalOf<ShredgramLoadingController> {
    error("LocalShredgramLoadingController not provided")
}

/**
 * Shredgram-style loading host that wraps content
 * Applies blur effect to content and shows loading overlay when active
 * 
 * Matches the Shredgram project's LoadingHost exactly:
 * - 16dp blur on background content when loading
 * - Black scrim at 35% alpha
 * - Centered spinning indicator
 * 
 * @param blurRadius Blur radius for background (default 16dp)
 * @param scrimColor Scrim overlay color (default black at 35% alpha)
 * @param content Content to wrap
 */
@Composable
fun ShredgramLoadingHost(
    blurRadius: Dp = 16.dp,
    scrimColor: Color = Color.Black.copy(alpha = 0.35f),
    content: @Composable () -> Unit
) {
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val controller = remember { ShredgramLoadingController { isLoading = it } }
    
    val interaction = remember { MutableInteractionSource() }
    
    // Animated blur value for smooth transition
    val animatedBlur by animateDpAsState(
        targetValue = if (isLoading) blurRadius else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "blur"
    )
    
    CompositionLocalProvider(LocalShredgramLoadingController provides controller) {
        Box(Modifier.fillMaxSize()) {
            // Content with conditional blur
            Box(Modifier.fillMaxSize().blur(animatedBlur)) {
                content()
            }
            
            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { /* consume touches */ },
                    contentAlignment = Alignment.Center
                ) {
                    ShredgramSpinningIndicator()
                }
            }
        }
    }
}

