package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Password strength levels
 */
enum class PasswordStrength {
    None,
    Weak,
    Good,
    Strong
}

/**
 * Password strength indicator with visual bars
 * 
 * @param strength Current password strength
 * @param modifier Modifier for the component
 */
@Composable
fun PasswordStrengthIndicator(
    strength: PasswordStrength,
    modifier: Modifier = Modifier
) {
    if (strength == PasswordStrength.None) return

    val (label, color, icon) = when (strength) {
        PasswordStrength.Weak -> Triple(
            stringResource(MR.strings.shredgram_password_strength_weak),
            Orange400,
            Icons.Default.Warning
        )
        PasswordStrength.Good -> Triple(
            stringResource(MR.strings.shredgram_password_strength_good),
            Color(0xFFFFC48A),
            Icons.Default.Info
        )
        PasswordStrength.Strong -> Triple(
            stringResource(MR.strings.shredgram_password_strength_strong),
            Green400,
            Icons.Default.Check
        )
        else -> return
    }

    val typography = ShredgramTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space8DP)
    ) {
        // Label with icon
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(Dimensions.space10DP)
            )
            Spacer(Modifier.width(Dimensions.space4DP))
            Text(
                text = label,
                style = typography.bodyExtraSmall.copy(color = color)
            )
        }
        
        // Strength bars
        repeat(3) { index ->
            val isActive = when {
                strength == PasswordStrength.Weak && index == 0 -> true
                strength == PasswordStrength.Good && index <= 1 -> true
                strength == PasswordStrength.Strong -> true
                else -> false
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.space4DP)
                    .background(
                        color = if (isActive) color else ShredgramWhite,
                        shape = RadiusCircle,
                    )
            )
        }
    }
}

/**
 * Calculate password strength based on the password string
 */
fun calculatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.None
    
    var score = 0
    
    // Length checks
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    
    // Character variety checks
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    
    return when {
        score >= 6 -> PasswordStrength.Strong
        score >= 4 -> PasswordStrength.Good
        score >= 1 -> PasswordStrength.Weak
        else -> PasswordStrength.None
    }
}

/**
 * Simplified strength indicator with just bars (no label)
 */
@Composable
fun SimpleStrengthBars(
    strength: PasswordStrength,
    modifier: Modifier = Modifier
) {
    if (strength == PasswordStrength.None) return

    val color = when (strength) {
        PasswordStrength.Weak -> Orange400
        PasswordStrength.Good -> Color(0xFFFFC48A)
        PasswordStrength.Strong -> Green400
        else -> return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.space4DP)
    ) {
        repeat(3) { index ->
            val isActive = when {
                strength == PasswordStrength.Weak && index == 0 -> true
                strength == PasswordStrength.Good && index <= 1 -> true
                strength == PasswordStrength.Strong -> true
                else -> false
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Dimensions.space4DP)
                    .background(
                        color = if (isActive) color else ShredgramWhite,
                        shape = RadiusCircle,
                    )
            )
        }
    }
}

