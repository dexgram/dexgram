package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Base button content composable
 */
@Composable
internal fun UIButtonBaseContent(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Dimensions.space16DP,
        vertical = Dimensions.space16DP
    ),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = if (fullWidth) modifier.fillMaxWidth() else modifier,
        shape = shape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = containerColor,
            contentColor = contentColor,
            disabledBackgroundColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
        ),
        contentPadding = contentPadding,
        elevation = ButtonDefaults.elevation(
            defaultElevation = Dimensions.space0DP,
            pressedElevation = Dimensions.space2DP,
            disabledElevation = Dimensions.space0DP
        ),
        content = content
    )
}

/**
 * Base button with text
 */
@Composable
internal fun UIButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Dimensions.space16DP,
        vertical = Dimensions.space16DP
    ),
) {
    UIButtonBaseContent(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
        enabled = enabled,
        fullWidth = fullWidth,
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = ShredgramTheme.typography.labelLarge
        )
    }
}

/**
 * Primary button - main action button with filled background
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Dimensions.space16DP,
        vertical = Dimensions.space16DP
    ),
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    UIButtonBase(
        text = text,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusPill,
        fullWidth = fullWidth,
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        disabledContainerColor = colors.borderElevated1,
        disabledContentColor = DarkCharcoal400,
        contentPadding = contentPadding
    )
}

/**
 * Secondary button - text button without background
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    UIButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusSmall,
        enabled = enabled,
        fullWidth = false,
        containerColor = Color.Transparent,
        contentColor = colors.onSurface,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = DarkCharcoal400,
        contentPadding = PaddingValues(Dimensions.space0DP)
    )
}

/**
 * Primary circle icon button
 */
@Composable
fun PrimaryCircleIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val colors = ShredgramTheme.colors
    UIButtonBaseContent(
        onClick = onClick,
        enabled = enabled,
        fullWidth = false,
        modifier = modifier,
        shape = CircleShape,
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        disabledContainerColor = colors.outlineVariant,
        disabledContentColor = DarkCharcoal400,
        contentPadding = PaddingValues(Dimensions.space0DP)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

/**
 * Tertiary button - blue text button
 */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    UIButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusSmall,
        enabled = enabled,
        fullWidth = false,
        containerColor = Color.Transparent,
        contentColor = ElectricBlue500,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = DarkCharcoal400,
    )
}

/**
 * Ghost button - same as tertiary but with different styling intent
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    UIButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusSmall,
        enabled = enabled,
        fullWidth = false,
        containerColor = Color.Transparent,
        contentColor = ElectricBlue500,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = DarkCharcoal400,
    )
}

/**
 * Error button - red background for destructive actions
 */
@Composable
fun ErrorButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    UIButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusSmall,
        enabled = enabled,
        fullWidth = true,
        containerColor = colors.surfaceError,
        contentColor = colors.onError,
        disabledContainerColor = colors.borderElevated1,
        disabledContentColor = DarkCharcoal400,
    )
}

/**
 * Warning button - orange background for warning actions
 */
@Composable
fun WarningButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    UIButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusSmall,
        enabled = enabled,
        fullWidth = true,
        containerColor = colors.surfaceWarning,
        contentColor = colors.onWarning,
        disabledContainerColor = colors.borderElevated1,
        disabledContentColor = DarkCharcoal400,
    )
}

/**
 * Success button - green background for positive actions
 */
@Composable
fun SuccessButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    UIButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = RadiusSmall,
        enabled = enabled,
        fullWidth = true,
        containerColor = colors.surfaceSuccess,
        contentColor = colors.onSuccess,
        disabledContainerColor = colors.borderElevated1,
        disabledContentColor = DarkCharcoal400,
    )
}

