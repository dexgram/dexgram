package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import chat.simplex.common.ui.shredgram.theme.*

/**
 * PIN indicator row showing filled/empty dots for PIN entry
 * 
 * @param value Current PIN value (string of digits)
 * @param totalSlots Total number of PIN slots
 * @param isIdle Whether the UI is in idle state (hides last digit)
 * @param modifier Modifier for the component
 */
@Composable
fun PinIndicatorRow(
    value: String,
    totalSlots: Int,
    isIdle: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography
    
    val filledColor = colors.onSurface
    val emptyDotColor = colors.outlineVariant.copy(alpha = 0.45f)

    val dotSize = Dimensions.space16DP
    val slotWidth = Dimensions.space38point5DP
    val slotHeight = Dimensions.space48DP

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimensions.space8DP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSlots) { i ->
                val isFilled = i < value.length
                val isLastFilled = i == value.length - 1
                val showLastDigit = isFilled && isLastFilled && !isIdle

                Box(
                    modifier = Modifier.size(width = slotWidth, height = slotHeight),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !isFilled -> {
                            // Empty dot
                            Box(
                                Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(emptyDotColor)
                            )
                        }

                        showLastDigit -> {
                            // Show the last entered digit briefly
                            Text(
                                text = value.last().toString(),
                                style = typography.headlineSmall,
                                color = filledColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        else -> {
                            // Filled dot (masked)
                            Box(
                                Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(filledColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple PIN indicator without the last digit reveal feature
 */
@Composable
fun SimplePinIndicator(
    filledCount: Int,
    totalSlots: Int,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    
    val filledColor = colors.onSurface
    val emptyDotColor = colors.outlineVariant.copy(alpha = 0.45f)
    val dotSize = Dimensions.space16DP

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space12DP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSlots) { i ->
            val isFilled = i < filledCount

            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (isFilled) filledColor else emptyDotColor)
            )
        }
    }
}

/**
 * PIN indicator with error state
 */
@Composable
fun PinIndicatorWithError(
    value: String,
    totalSlots: Int,
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    
    val filledColor = if (hasError) colors.error else colors.onSurface
    val emptyDotColor = if (hasError) colors.error.copy(alpha = 0.3f) else colors.outlineVariant.copy(alpha = 0.45f)
    val dotSize = Dimensions.space16DP

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space12DP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSlots) { i ->
            val isFilled = i < value.length

            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (isFilled) filledColor else emptyDotColor)
            )
        }
    }
}

