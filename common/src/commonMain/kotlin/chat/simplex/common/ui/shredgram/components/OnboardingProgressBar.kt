package chat.simplex.common.ui.shredgram.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Linear progress bar for onboarding steps
 * 
 * @param currentStep Current step index (0-based)
 * @param totalSteps Total number of steps
 * @param modifier Modifier for the progress bar
 * @param showStepText Whether to show "Step X of Y" text
 * @param animated Whether to animate progress changes
 */
@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    showStepText: Boolean = false,
    animated: Boolean = true
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography
    
    val progress = if (totalSteps > 0) (currentStep + 1).toFloat() / totalSteps.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (animated) tween(durationMillis = 300) else tween(durationMillis = 0),
        label = "progress"
    )

    Column(modifier = modifier) {
        if (showStepText) {
            Text(
                text = stringResource(MR.strings.shredgram_step_x_of_y, currentStep + 1, totalSteps),
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimensions.space8DP)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.space4DP)
                .clip(RadiusPill)
                .background(colors.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RadiusPill)
                    .background(colors.primary)
            )
        }
    }
}

/**
 * Segmented progress bar showing discrete steps
 * 
 * @param currentStep Current step index (0-based)
 * @param totalSteps Total number of steps
 * @param modifier Modifier for the progress bar
 */
@Composable
fun SegmentedProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space4DP)
    ) {
        repeat(totalSteps) { index ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.space4DP)
                    .clip(RadiusPill)
                    .background(
                        when {
                            isCompleted -> colors.primary
                            isCurrent -> colors.primary.copy(alpha = 0.5f)
                            else -> colors.outlineVariant
                        }
                    )
            )
        }
    }
}

/**
 * Circular step indicator showing numbered steps
 * 
 * @param currentStep Current step index (0-based)
 * @param totalSteps Total number of steps
 * @param modifier Modifier for the indicator
 */
@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space8DP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep
            
            val backgroundColor = when {
                isCompleted -> colors.primary
                isCurrent -> colors.primary
                else -> colors.outlineVariant
            }
            
            val textColor = when {
                isCompleted || isCurrent -> colors.onPrimary
                else -> colors.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(Dimensions.space32DP)
                    .clip(RadiusCircle)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = typography.labelMedium,
                    color = textColor
                )
            }

            // Connector line between steps
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(Dimensions.space24DP)
                        .height(Dimensions.space2DP)
                        .background(
                            if (index < currentStep) colors.primary else colors.outlineVariant
                        )
                )
            }
        }
    }
}

/**
 * Compact progress bar with percentage text
 */
@Composable
fun ProgressBarWithPercentage(
    progress: Float,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space12DP)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.space8DP)
                .clip(RadiusPill)
                .background(colors.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RadiusPill)
                    .background(colors.primary)
            )
        }
        
        if (showPercentage) {
            Text(
                text = stringResource(MR.strings.shredgram_progress_percent, (animatedProgress * 100).toInt()),
                style = typography.labelMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * Determinate progress indicator for file operations, downloads, etc.
 */
@Composable
fun DeterminateProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = ShredgramTheme.colors.outlineVariant,
    progressColor: Color = ShredgramTheme.colors.primary,
    height: androidx.compose.ui.unit.Dp = Dimensions.space8DP
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RadiusPill)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RadiusPill)
                .background(progressColor)
        )
    }
}

/**
 * Indeterminate linear progress indicator
 */
@Composable
fun IndeterminateProgressBar(
    modifier: Modifier = Modifier,
    color: Color = ShredgramTheme.colors.primary,
    trackColor: Color = ShredgramTheme.colors.outlineVariant
) {
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.space4DP)
            .clip(RadiusPill),
        color = color,
        backgroundColor = trackColor
    )
}

