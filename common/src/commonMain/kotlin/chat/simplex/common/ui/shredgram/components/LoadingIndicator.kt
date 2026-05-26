package chat.simplex.common.ui.shredgram.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Spinning progress indicator with animated rotation
 * 
 * @param modifier Modifier for the indicator
 * @param durationMillis Duration of one full rotation in milliseconds
 * @param strokeWidth Width of the progress arc stroke
 * @param color Color of the progress arc
 */
@Composable
fun SpinningIndicator(
    modifier: Modifier = Modifier,
    durationMillis: Int = 900,
    strokeWidth: Float = 4f,
    color: Color = ShredgramTheme.colors.primary
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Canvas(
        modifier = modifier
            .size(Dimensions.space40DP)
            .rotate(rotation)
    ) {
        val sweepAngle = 270f
        val startAngle = 0f
        
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Simple circular progress indicator using Material design
 */
@Composable
fun SimpleLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = ShredgramTheme.colors.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = Dimensions.space4DP
) {
    CircularProgressIndicator(
        modifier = modifier.size(Dimensions.space40DP),
        color = color,
        strokeWidth = strokeWidth
    )
}

/**
 * Loading controller for managing loading state
 */
@Stable
class LoadingController internal constructor(
    private val setLoading: (Boolean) -> Unit
) {
    fun show() = setLoading(true)
    fun hide() = setLoading(false)
    fun set(value: Boolean) = setLoading(value)
}

val LocalLoadingController = staticCompositionLocalOf<LoadingController> {
    error("LocalLoadingController not provided")
}

/**
 * Loading host that displays a loading overlay when active
 * 
 * @param scrimColor Color of the overlay background
 * @param content Content to display behind the loading overlay
 */
@Composable
fun LoadingHost(
    scrimColor: Color = Color.Black.copy(alpha = 0.35f),
    content: @Composable () -> Unit
) {
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val controller = remember { LoadingController { isLoading = it } }
    val interaction = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalLoadingController provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()

            if (isLoading) {
                // Overlay that blocks touches and shows spinner
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { /* consume touch events */ },
                    contentAlignment = Alignment.Center
                ) {
                    SpinningIndicator(
                        modifier = Modifier.size(Dimensions.space40DP)
                    )
                }
            }
        }
    }
}

/**
 * Full screen loading overlay
 */
@Composable
fun FullScreenLoading(
    visible: Boolean,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f)
) {
    if (!visible) return
    
    val interaction = remember { MutableInteractionSource() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { /* consume */ },
        contentAlignment = Alignment.Center
    ) {
        SpinningIndicator(
            modifier = Modifier.size(Dimensions.space48DP)
        )
    }
}

/**
 * Inline loading indicator for buttons or small spaces
 */
@Composable
fun InlineLoadingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dimensions.space24DP,
    color: Color = ShredgramTheme.colors.onPrimary
) {
    val transition = rememberInfiniteTransition(label = "inline-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val strokeWidth = size.toPx() / 8f
        
        drawArc(
            color = color.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

