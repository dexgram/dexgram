package chat.simplex.common.views.chat.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.painterResource
import chat.simplex.common.views.bot.BotCallbackQuery
import chat.simplex.res.MR

private val CallbackBubbleBg = Color(0xFF1E3A5F)
private val CallbackLabelColor = Color(0xFFE2E8F0)
private val CallbackIconColor = Color(0xFF60A5FA)
private val FormBubbleBg = Color(0xFF1E3B2F)
private val FormIconColor = Color(0xFF34D399)

@Composable
fun CIBotCallbackView(callback: BotCallbackQuery) {
    val isForm = callback.formData != null
    val bgColor = if (isForm) FormBubbleBg else CallbackBubbleBg
    val iconColor = if (isForm) FormIconColor else CallbackIconColor

    val displayText = when {
        callback.label != null -> callback.label
        isForm -> "Form submitted"
        else -> callback.data
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(
                if (isForm) MR.images.ic_check else MR.images.ic_arrow_forward_ios
            ),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = displayText,
            color = CallbackLabelColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
