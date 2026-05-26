package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.ui.shredgram.theme.*

/**
 * PIN entry keypad with digits 0-9, backspace, and proceed button
 * 
 * @param onDigit Callback when a digit is pressed
 * @param onBackspace Callback when backspace is pressed
 * @param onProceed Callback when proceed button is pressed
 * @param proceedEnabled Whether the proceed button is enabled
 * @param modifier Modifier for the keypad
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onProceed: () -> Unit,
    proceedEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimensions.space28DP)
    ) {
        // Rows 1-3 (digits 1-9)
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { digit ->
                    PinDigit(
                        digit = digit.toString(),
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom row (backspace, 0, proceed)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backspace
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(MR.strings.shredgram_cd_backspace),
                        tint = ShredgramTheme.colors.onSurface
                    )
                }
            }

            // Zero digit
            PinDigit(
                digit = "0",
                onClick = { onDigit(0) },
                modifier = Modifier.weight(1f)
            )

            // Proceed button
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                PrimaryCircleIconButton(
                    onClick = onProceed,
                    enabled = proceedEnabled,
                    modifier = Modifier.size(Dimensions.space56DP),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = stringResource(MR.strings.shredgram_cd_proceed),
                            tint = if (proceedEnabled) 
                                ShredgramTheme.colors.onPrimary 
                            else 
                                DarkCharcoal400
                        )
                    }
                )
            }
        }
    }
}

/**
 * Individual digit button for the PIN keypad
 */
@Composable
private fun PinDigit(
    digit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Box(
        modifier = modifier.height(Dimensions.space56DP),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = typography.headlineMedium,
            color = colors.onSurface,
            modifier = Modifier
                .padding(Dimensions.space4DP)
                .clickable(onClick = onClick)
        )
    }
}

/**
 * Simplified PIN keypad without the proceed button
 */
@Composable
fun SimplePinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimensions.space24DP)
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    PinDigit(
                        digit = digit.toString(),
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty space for alignment
            Box(Modifier.weight(1f))

            // Zero digit
            PinDigit(
                digit = "0",
                onClick = { onDigit(0) },
                modifier = Modifier.weight(1f)
            )

            // Backspace
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(MR.strings.shredgram_cd_backspace),
                        tint = ShredgramTheme.colors.onSurface
                    )
                }
            }
        }
    }
}

