package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Custom input field with Shredgram styling
 * 
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param placeholder Placeholder text
 * @param isPassword Whether this is a password field (shows visibility toggle)
 * @param modifier Modifier for the input field
 * @param keyboardOptions Keyboard configuration
 * @param keyboardActions Keyboard actions
 * @param contentPadding Padding inside the input field
 * @param trailingIconPaddingEnd End padding for trailing icon
 * @param onFocusChange Callback when focus changes
 * @param enabled Whether the input is enabled
 * @param singleLine Whether to restrict to single line
 * @param isError Whether to show error state
 */
@Composable
fun UIInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Dimensions.inputHorizontalPadding,
        vertical = Dimensions.inputPadding
    ),
    trailingIconPaddingEnd: Dp = Dimensions.space20DP,
    onFocusChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography
    
    val borderColor = when {
        isError -> colors.error
        isFocused -> colors.borderBrand
        else -> colors.outlineVariant
    }
    
    val transformation = if (isPassword && !isPasswordVisible) {
        PasswordVisualTransformation('●')
    } else {
        VisualTransformation.None
    }
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        visualTransformation = transformation,
        textStyle = typography.bodySmall.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                onFocusChange?.invoke(focusState.isFocused)
            },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RadiusCircle)
                    .background(colors.surface)
                    .border(
                        width = Dimensions.space1DP,
                        color = borderColor,
                        shape = RadiusCircle
                    )
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
                
                if (isPassword) {
                    Spacer(Modifier.width(Dimensions.space8DP))
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                        modifier = Modifier
                            .size(Dimensions.space24DP)
                            .padding(end = trailingIconPaddingEnd - Dimensions.space20DP)
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) stringResource(MR.strings.shredgram_cd_hide_password) else stringResource(MR.strings.shredgram_cd_show_password),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

/**
 * Simpler text input without password functionality
 */
@Composable
fun UITextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    UIInputField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        isPassword = false,
        modifier = modifier,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        onFocusChange = onFocusChange
    )
}

