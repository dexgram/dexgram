package chat.simplex.common.ui.shredgram.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.ui.shredgram.components.*
import chat.simplex.common.ui.shredgram.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen for setting up a PIN
 * 
 * @param onPinSet Callback when PIN is set successfully
 * @param onBack Callback for back navigation
 * @param minLength Minimum PIN length (default 4)
 * @param maxLength Maximum PIN length (default 8)
 * @param currentStep Current onboarding step for progress bar
 * @param totalSteps Total onboarding steps for progress bar
 */
@Composable
fun ShredgramSetPinScreen(
    onPinSet: (pin: String) -> Unit,
    onBack: () -> Unit,
    minLength: Int = PinRules.MIN_PIN,
    maxLength: Int = PinRules.MAX_PIN,
    currentStep: Int? = null,
    totalSteps: Int? = null,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(PinStep.Enter) }
    var error by remember { mutableStateOf<String?>(null) }
    var isIdle by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors
    
    val currentPin = if (step == PinStep.Enter) pin else confirmPin
    val canProceed = currentPin.length >= minLength

    // Handle back button
    val handleBack: () -> Unit = {
        if (step == PinStep.Confirm) {
            step = PinStep.Enter
            confirmPin = ""
            error = null
        } else {
            onBack()
        }
    }

    // Handle digit input
    fun onDigit(digit: Int) {
        if (step == PinStep.Enter && pin.length < maxLength) {
            pin += digit.toString()
            isIdle = false
            scope.launch {
                delay(500)
                isIdle = true
            }
        } else if (step == PinStep.Confirm && confirmPin.length < maxLength) {
            confirmPin += digit.toString()
            isIdle = false
            scope.launch {
                delay(500)
                isIdle = true
            }
        }
    }

    // Handle backspace
    fun onBackspace() {
        if (step == PinStep.Enter && pin.isNotEmpty()) {
            pin = pin.dropLast(1)
        } else if (step == PinStep.Confirm && confirmPin.isNotEmpty()) {
            confirmPin = confirmPin.dropLast(1)
        }
        error = null
    }

    // Handle proceed
    fun onProceed() {
        if (!canProceed) return
        
        if (step == PinStep.Enter) {
            step = PinStep.Confirm
        } else {
            // Confirm step
            if (pin == confirmPin) {
                onPinSet(pin)
            } else {
                error = generalGetString(MR.strings.shredgram_pin_mismatch_error)
                confirmPin = ""
            }
        }
    }

    FixedScaffold(
        currentStep = currentStep,
        totalSteps = totalSteps,
        bottomContent = {
            PinKeypad(
                onDigit = ::onDigit,
                onBackspace = ::onBackspace,
                onProceed = ::onProceed,
                proceedEnabled = canProceed
            )

            Spacer(Modifier.height(Dimensions.space32DP))
        }
    ) {
        ShredgramTopBar(
            showBack = true,
            onBack = handleBack
        )

        // Icon
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.space24DP),
            tint = colors.onSurface
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        // Title
        Text(
            text = if (step == PinStep.Enter) stringResource(MR.strings.shredgram_pin_create_title) else stringResource(MR.strings.shredgram_pin_confirm_title),
            style = typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space8DP))

        // Subtitle
        Text(
            text = if (step == PinStep.Enter) 
                "Enter a $minLength-$maxLength digit PIN to secure your app" 
            else 
                stringResource(MR.strings.shredgram_pin_reenter_subtitle),
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Error message
        error?.let {
            Spacer(Modifier.height(Dimensions.space12DP))
            Text(
                text = it,
                color = colors.error,
                style = typography.bodySmall
            )
        }

        // PIN indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = Dimensions.space32DP),
            contentAlignment = Alignment.Center
        ) {
            PinIndicatorRow(
                value = currentPin,
                totalSlots = maxLength,
                isIdle = isIdle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Simplified PIN entry screen (for unlock)
 */
@Composable
fun ShredgramEnterPinScreen(
    onSubmit: (pin: String) -> Unit,
    onBack: (() -> Unit)? = null,
    title: String = generalGetString(MR.strings.shredgram_pin_enter_title),
    subtitle: String = generalGetString(MR.strings.shredgram_pin_enter_subtitle),
    pinLength: Int = PinRules.MAX_PIN,
    errorMessage: String? = null,
    onForgotPin: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var isIdle by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors

    // Auto-submit when PIN is complete
    LaunchedEffect(pin) {
        if (pin.length == pinLength) {
            delay(300)
            onSubmit(pin)
        }
    }

    fun onDigit(digit: Int) {
        if (pin.length < pinLength) {
            pin += digit.toString()
            isIdle = false
            scope.launch {
                delay(500)
                isIdle = true
            }
        }
    }

    fun onBackspace() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
        }
    }

    FixedScaffold(
        bottomContent = {
            SimplePinKeypad(
                onDigit = ::onDigit,
                onBackspace = ::onBackspace
            )

            if (onForgotPin != null) {
                Spacer(Modifier.height(Dimensions.space16DP))
                
                TertiaryButton(
                    text = stringResource(MR.strings.shredgram_pin_forgot),
                    onClick = onForgotPin
                )
            }

            Spacer(Modifier.height(Dimensions.space32DP))
        }
    ) {
        ShredgramTopBar(
            showBack = onBack != null,
            onBack = onBack
        )

        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.space24DP),
            tint = colors.onSurface
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        Text(
            text = title,
            style = typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimensions.space8DP))

        Text(
            text = subtitle,
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(Dimensions.space12DP))
            Text(
                text = errorMessage,
                color = colors.error,
                style = typography.bodySmall
            )
        }

        // PIN indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = Dimensions.space32DP),
            contentAlignment = Alignment.Center
        ) {
            PinIndicatorWithError(
                value = pin,
                totalSlots = pinLength,
                hasError = errorMessage != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

