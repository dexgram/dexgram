package chat.simplex.common.ui.shredgram.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.ui.shredgram.components.*
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Screen for choosing the unlock method (passphrase or NFC YubiKey PIV)
 * 
 * @param onMethodSelected Callback when a method is selected and user proceeds
 * @param onBack Callback for back navigation
 * @param termsText Optional terms text
 * @param currentStep Current onboarding step for progress bar
 * @param totalSteps Total onboarding steps for progress bar
 */
@Composable
fun ShredgramChooseUnlockScreen(
    onMethodSelected: (UnlockMethod) -> Unit,
    onBack: () -> Unit,
    termsText: String? = null,
    currentStep: Int? = null,
    totalSteps: Int? = null,
) {
    var selectedMethod by remember { mutableStateOf<UnlockMethod?>(null) }
    
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors

    CustomScaffold(
        currentStep = currentStep,
        totalSteps = totalSteps,
        bottomContent = {
            Spacer(Modifier.height(Dimensions.space32DP))

            PrimaryButton(
                text = stringResource(MR.strings.shredgram_action_next),
                enabled = selectedMethod != null,
                onClick = {
                    selectedMethod?.let { onMethodSelected(it) }
                }
            )

            Spacer(Modifier.height(Dimensions.space16DP))

            if (termsText != null) {
                Text(
                    text = termsText,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
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
            tint = colors.onSurface,
            modifier = Modifier.size(Dimensions.space24DP)
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        // Title
        Text(
            text = stringResource(MR.strings.shredgram_choose_unlock_title),
            style = typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space8DP))

        // Description
        Text(
            text = stringResource(MR.strings.shredgram_choose_unlock_subtitle),
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space32DP))

        // Passphrase option
        OptionCard(
            title = stringResource(MR.strings.shredgram_option_passphrase),
            description = stringResource(MR.strings.shredgram_option_passphrase_desc),
            selected = selectedMethod == UnlockMethod.Passphrase,
            onClick = { selectedMethod = UnlockMethod.Passphrase },
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = colors.onSurface
                )
            }
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        // NFC option
        OptionCard(
            title = stringResource(MR.strings.shredgram_option_nfc_passkey),
            description = stringResource(MR.strings.shredgram_option_nfc_passkey_desc),
            selected = selectedMethod == UnlockMethod.NfcPasskey,
            onClick = { selectedMethod = UnlockMethod.NfcPasskey },
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

/**
 * Simpler unlock method selection with custom options
 */
@Composable
fun UnlockMethodSelector(
    options: List<UnlockOption>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimensions.space16DP)
    ) {
        options.forEachIndexed { index, option ->
            OptionCard(
                title = option.title,
                description = option.description,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = ShredgramTheme.colors.onSurface
                    )
                }
            )
        }
    }
}

/**
 * Data class for unlock option
 */
data class UnlockOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

