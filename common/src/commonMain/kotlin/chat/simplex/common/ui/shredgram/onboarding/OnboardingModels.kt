package chat.simplex.common.ui.shredgram.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Model representing an onboarding page
 */
data class OnboardingPageModel(
    val title: String,
    val description: String,
    val image: Painter? = null,
)

/**
 * Unlock method options
 */
enum class UnlockMethod {
    Passphrase,
    NfcPasskey
}

/**
 * Password/Passphrase strength levels
 */
enum class PassStrength {
    None,
    Weak,
    Good,
    Strong
}

/**
 * PIN entry step
 */
enum class PinStep {
    Enter,
    Confirm
}

/**
 * Constants for PIN validation
 */
object PinRules {
    const val MIN_PIN = 4
    const val MAX_PIN = 8
}

