package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Top navigation bar with optional back button and centered logo/title
 * 
 * @param showBack Whether to show the back button
 * @param onBack Callback when back button is pressed
 * @param title Optional title text
 * @param logo Optional logo painter
 * @param modifier Modifier for the top bar
 */
@Composable
fun ShredgramTopBar(
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
    title: String? = null,
    logo: Painter? = null,
) {
    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimensions.space32DP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side - Back button or spacer
        if (showBack) {
            IconButton(
                onClick = { onBack?.invoke() },
                modifier = Modifier.height(Dimensions.space24DP)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MR.strings.shredgram_cd_back),
                    tint = colors.onBackground
                )
            }
        } else {
            Spacer(Modifier.width(Dimensions.space48DP))
        }

        // Center - Logo and title
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logo != null) {
                Icon(
                    painter = logo,
                    contentDescription = stringResource(MR.strings.shredgram_cd_logo),
                    tint = colors.onBackground
                )
                
                if (title != null) {
                    Spacer(Modifier.width(Dimensions.space8DP))
                }
            }

            if (title != null) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                    color = colors.onBackground
                )
            }
        }

        // Right side - Spacer for balance
        Spacer(Modifier.width(Dimensions.space48DP))
    }
}

/**
 * Simple top bar with just a title
 */
@Composable
fun SimpleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    ShredgramTopBar(
        modifier = modifier,
        showBack = showBack,
        onBack = onBack,
        title = title,
        logo = null
    )
}

/**
 * Minimal top bar with just back button
 */
@Composable
fun BackTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShredgramTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimensions.space16DP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(Dimensions.space48DP)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(MR.strings.shredgram_cd_back),
                tint = colors.onBackground
            )
        }
    }
}

