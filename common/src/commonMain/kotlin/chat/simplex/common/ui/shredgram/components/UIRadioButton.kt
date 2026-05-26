package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Custom radio button with checkmark when selected
 */
@Composable
fun UIRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val borderColor = colors.outlineVariant

    Surface(
        modifier = modifier
            .size(Dimensions.space16DP)
            .clip(RadiusCircle)
            .clickable(onClick = onClick),
        shape = RadiusCircle,
        color = colors.surface,
        border = BorderStroke(Dimensions.space1DP, borderColor)
    ) {
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.borderBrand,
                    modifier = Modifier.size(Dimensions.space10DP)
                )
            }
        }
    }
}

/**
 * Larger radio button variant
 */
@Composable
fun UIRadioButtonLarge(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val borderColor = if (selected) colors.borderBrand else colors.outlineVariant

    Surface(
        modifier = modifier
            .size(Dimensions.space24DP)
            .clip(RadiusCircle)
            .clickable(onClick = onClick),
        shape = RadiusCircle,
        color = if (selected) colors.primary else colors.surface,
        border = BorderStroke(Dimensions.space2DP, borderColor)
    ) {
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(Dimensions.space14DP)
                )
            }
        }
    }
}

