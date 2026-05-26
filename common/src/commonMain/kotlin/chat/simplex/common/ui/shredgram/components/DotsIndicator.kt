package chat.simplex.common.ui.shredgram.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Horizontal dots indicator for pagers/carousels
 * 
 * Shows dots representing pages, with the selected dot appearing as an elongated pill.
 * Includes smooth width animation when selection changes.
 * 
 * @param totalDots Total number of dots to display
 * @param selectedIndex Currently selected dot index (0-based)
 * @param modifier Modifier for the row
 */
@Composable
fun DotsIndicator(
    totalDots: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val dotColor = colors.onSurface.copy(alpha = 0.15f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(totalDots) { index ->
            val isSelected = index == selectedIndex
            
            // Animated width transition
            val width by animateDpAsState(
                targetValue = if (isSelected) Dimensions.space32DP else Dimensions.space8DP,
                animationSpec = tween(durationMillis = 300),
                label = "dotWidth"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = Dimensions.space4DP)
                    .width(width)
                    .height(Dimensions.space8DP)
                    .clip(RadiusPill)
                    .background(dotColor)
            )
        }
    }
}

/**
 * Simple dots indicator with uniform dot sizes
 */
@Composable
fun SimpleDotsIndicator(
    totalDots: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val activeColor = colors.primary
    val inactiveColor = colors.onSurface.copy(alpha = 0.15f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(totalDots) { index ->
            val isSelected = index == selectedIndex

            Box(
                modifier = Modifier
                    .padding(horizontal = Dimensions.space4DP)
                    .size(Dimensions.space8DP)
                    .clip(RadiusCircle)
                    .background(if (isSelected) activeColor else inactiveColor)
            )
        }
    }
}

/**
 * Linear progress indicator style dots
 */
@Composable
fun ProgressDotsIndicator(
    totalDots: Int,
    currentProgress: Int,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val activeColor = colors.primary
    val inactiveColor = colors.outlineVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space4DP)
    ) {
        repeat(totalDots) { index ->
            val isActive = index < currentProgress

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.space4DP)
                    .clip(RadiusPill)
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}

