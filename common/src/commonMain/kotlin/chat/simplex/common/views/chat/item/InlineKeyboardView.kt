package chat.simplex.common.views.chat.item

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.bot.InlineButton

private val ButtonBg = Color(0xFF2A3352)
private val ButtonText = Color(0xFFE2E8F0)
private val PayButtonBg = Color(0xFF059669)
private val UrlButtonBg = Color(0xFF1D4ED8)

@Composable
fun InlineKeyboardView(
    keyboard: List<List<InlineButton>>,
    onButtonClick: (InlineButton) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (row in keyboard) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (button in row) {
                    val bgColor = when {
                        button.pay -> PayButtonBg
                        button.url != null -> UrlButtonBg
                        else -> ButtonBg
                    }
                    Button(
                        onClick = {
                            when {
                                button.url != null -> uriHandler.openUri(button.url)
                                else -> onButtonClick(button)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = button.text,
                            color = ButtonText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
