package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Selectable option card with icon, title, description, and radio button
 * 
 * @param title Card title text
 * @param description Card description text
 * @param selected Whether this card is selected
 * @param onClick Callback when card is clicked
 * @param icon Icon composable to display on the left
 * @param modifier Modifier for the card
 */
@Composable
fun OptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (() -> Unit),
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RadiusLarge,
        color = colors.surface,
        elevation = if (selected) Dimensions.space10DP else Dimensions.space0DP,
        border = BorderStroke(
            width = Dimensions.space1DP,
            color = if (selected) colors.borderBrand else colors.outlineVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(Dimensions.space16DP)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            icon()

            Spacer(Modifier.width(Dimensions.space8DP))

            // Title and description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = typography.titleExtraSmall,
                    color = colors.onSurface
                )

                Spacer(Modifier.height(Dimensions.space4DP))

                Text(
                    text = description,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(Dimensions.space8DP))

            // Radio button
            UIRadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

/**
 * Option card without icon
 */
@Composable
fun SimpleOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RadiusLarge,
        color = colors.surface,
        elevation = if (selected) Dimensions.space10DP else Dimensions.space0DP,
        border = BorderStroke(
            width = Dimensions.space1DP,
            color = if (selected) colors.borderBrand else colors.outlineVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(Dimensions.space16DP)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title and description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = typography.titleExtraSmall,
                    color = colors.onSurface
                )

                if (description.isNotEmpty()) {
                    Spacer(Modifier.height(Dimensions.space4DP))

                    Text(
                        text = description,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(Dimensions.space8DP))

            // Radio button
            UIRadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

/**
 * Compact option card without description
 */
@Composable
fun CompactOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RadiusMedium,
        color = colors.surface,
        border = BorderStroke(
            width = Dimensions.space1DP,
            color = if (selected) colors.borderBrand else colors.outlineVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(Dimensions.space12DP)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon (optional)
            if (icon != null) {
                icon()
                Spacer(Modifier.width(Dimensions.space8DP))
            }

            // Title
            Text(
                text = title,
                style = typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(Dimensions.space8DP))

            // Radio button
            UIRadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

