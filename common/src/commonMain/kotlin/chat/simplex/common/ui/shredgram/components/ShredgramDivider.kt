package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Divider with optional text label in the center (e.g., "or")
 * 
 * @param text Text to display in the center of the divider
 * @param modifier Modifier for the divider
 */
@Composable
fun ShredgramDivider(
    modifier: Modifier = Modifier,
    text: String = generalGetString(MR.strings.shredgram_divider_or)
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.space1DP),
            color = colors.outlineVariant
        )

        Text(
            text = text,
            style = typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimensions.space8DP)
        )

        Divider(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.space1DP),
            color = colors.outlineVariant
        )
    }
}

/**
 * Simple horizontal divider without text
 */
@Composable
fun SimpleDivider(
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors

    Divider(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.space1DP),
        color = colors.outlineVariant
    )
}

/**
 * Thicker horizontal divider for section separation
 */
@Composable
fun SectionDivider(
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors

    Divider(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.space8DP),
        color = colors.surfaceElevated0
    )
}

