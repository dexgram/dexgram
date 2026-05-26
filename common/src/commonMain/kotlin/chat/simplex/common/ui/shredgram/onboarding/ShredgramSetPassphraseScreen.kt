package chat.simplex.common.ui.shredgram.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.ui.shredgram.components.*
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Screen for setting up a passphrase
 * 
 * @param onNext Callback when passphrase is set successfully
 * @param onBack Callback for back navigation
 * @param showNfcFallback Whether to show the NFC fallback option
 * @param onNfcFallbackSelected Callback when NFC fallback is toggled
 * @param currentStep Current onboarding step for progress bar
 * @param totalSteps Total onboarding steps for progress bar
 */
@Composable
fun ShredgramSetPassphraseScreen(
    onNext: (passphrase: String) -> Unit,
    onBack: () -> Unit,
    showNfcFallback: Boolean = false,
    onNfcFallbackSelected: ((Boolean) -> Unit)? = null,
    currentStep: Int? = null,
    totalSteps: Int? = null,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var isPassphraseFocused by remember { mutableStateOf(false) }
    var isConfirmFocused by remember { mutableStateOf(false) }
    var nfcFallbackEnabled by remember { mutableStateOf(false) }
    
    val confirmFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors
    
    // Calculate password strength
    val strength = calculatePasswordStrength(passphrase)
    val showStrengthIndicator = (isPassphraseFocused || isConfirmFocused) && passphrase.isNotEmpty()
    
    // Validation
    val passwordsMatch = passphrase == confirmPassphrase
    val isValid = passphrase.isNotEmpty() && 
                  confirmPassphrase.isNotEmpty() && 
                  passwordsMatch && 
                  strength != PasswordStrength.None

    CustomScaffold(
        currentStep = currentStep,
        totalSteps = totalSteps,
        bottomContent = {
            PrimaryButton(
                text = stringResource(MR.strings.shredgram_action_next),
                enabled = isValid,
                onClick = {
                    keyboardController?.hide()
                    onNext(passphrase)
                }
            )

            Spacer(Modifier.height(Dimensions.space16DP))
            
            Text(
                text = stringResource(MR.strings.shredgram_passphrase_terms_text),
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(Dimensions.space16DP))
        }
    ) {
        ShredgramTopBar(
            showBack = true,
            onBack = onBack
        )

        // Icon
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.space24DP),
            tint = colors.onSurface
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        // Title
        Text(
            text = stringResource(MR.strings.shredgram_passphrase_set_title),
            style = typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space8DP))

        // Subtitle
        Text(
            text = stringResource(MR.strings.shredgram_passphrase_set_subtitle),
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space32DP))

        // New passphrase input
        UIInputField(
            isPassword = true,
            value = passphrase,
            onValueChange = { passphrase = it },
            placeholder = stringResource(MR.strings.shredgram_passphrase_new_placeholder),
            onFocusChange = { isPassphraseFocused = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { confirmFocusRequester.requestFocus() }
            )
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        // Confirm passphrase input
        UIInputField(
            isPassword = true,
            value = confirmPassphrase,
            onValueChange = { confirmPassphrase = it },
            placeholder = stringResource(MR.strings.shredgram_passphrase_confirm_placeholder),
            modifier = Modifier.focusRequester(confirmFocusRequester),
            onFocusChange = { isConfirmFocused = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (isValid) {
                        onNext(passphrase)
                    }
                }
            ),
            isError = confirmPassphrase.isNotEmpty() && !passwordsMatch
        )

        // Error message for mismatch
        if (confirmPassphrase.isNotEmpty() && !passwordsMatch) {
            Spacer(Modifier.height(Dimensions.space8DP))
            Text(
                text = stringResource(MR.strings.shredgram_passphrase_mismatch_error),
                style = typography.bodySmall,
                color = colors.error
            )
        }

        // Password strength indicator
        if (showStrengthIndicator) {
            Spacer(Modifier.height(Dimensions.space16DP))
            PasswordStrengthIndicator(strength = strength)
        }

        Spacer(Modifier.height(Dimensions.space32DP))

        // Divider and NFC fallback option
        if (showNfcFallback) {
            ShredgramDivider()

            Spacer(Modifier.height(Dimensions.space32DP))

            OptionCard(
            title = stringResource(MR.strings.shredgram_option_nfc_passkey),
            description = stringResource(MR.strings.shredgram_option_nfc_backup_desc),
                selected = nfcFallbackEnabled,
                onClick = { 
                    nfcFallbackEnabled = !nfcFallbackEnabled
                    onNfcFallbackSelected?.invoke(nfcFallbackEnabled)
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        tint = colors.onSurface
                    )
                }
            )
        }
    }
}

/**
 * Simplified passphrase entry without confirmation (for unlock)
 */
@Composable
fun ShredgramEnterPassphraseScreen(
    onSubmit: (passphrase: String) -> Unit,
    onBack: (() -> Unit)? = null,
    title: String = generalGetString(MR.strings.shredgram_passphrase_enter_title),
    subtitle: String = generalGetString(MR.strings.shredgram_passphrase_enter_subtitle),
    errorMessage: String? = null,
) {
    var passphrase by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors

    CustomScaffold(
        bottomContent = {
            PrimaryButton(
                text = stringResource(MR.strings.shredgram_action_unlock),
                enabled = passphrase.isNotEmpty(),
                onClick = {
                    keyboardController?.hide()
                    onSubmit(passphrase)
                }
            )

            Spacer(Modifier.height(Dimensions.space32DP))
        }
    ) {
        ShredgramTopBar(
            showBack = onBack != null,
            onBack = onBack
        )

        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.space24DP),
            tint = colors.onSurface
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        Text(
            text = title,
            style = typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space8DP))

        Text(
            text = subtitle,
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space32DP))

        UIInputField(
            isPassword = true,
            value = passphrase,
            onValueChange = { passphrase = it },
            placeholder = stringResource(MR.strings.shredgram_passphrase_placeholder),
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (passphrase.isNotEmpty()) {
                        onSubmit(passphrase)
                    }
                }
            )
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(Dimensions.space8DP))
            Text(
                text = errorMessage,
                style = typography.bodySmall,
                color = colors.error
            )
        }
    }
}

