package chat.simplex.common.views.chat.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.bot.InlineButton
import chat.simplex.common.views.bot.ProductCard

private val CardGrad1 = Color(0xFF1A1F36)
private val CardGrad2 = Color(0xFF0D1025)
private val PriceColor = Color(0xFF34D399)
private val LabelColor = Color(0xFF94A3B8)
private val TitleColor = Color(0xFFF1F5F9)
private val DescColor = Color(0xFFCBD5E1)
private val ChipBg = Color(0x33FFFFFF)

@Composable
fun ProductCardView(
    product: ProductCard,
    keyboard: List<List<InlineButton>>? = null,
    onButtonClick: (InlineButton) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(CardGrad1, CardGrad2),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 400f)
                )
            )
            .padding(14.dp)
    ) {
        if (product.imageUrl != null && product.imageUrl.isNotBlank()) {
            BotImageView(
                url = product.imageUrl,
                contentDescription = product.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(10.dp))
        }

        if (product.network != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ChipBg,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = product.network.uppercase(),
                    color = LabelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = product.title,
            color = TitleColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Description
        if (product.description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.description,
                color = DescColor,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        // Price
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = product.price,
                color = PriceColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = product.tokenSymbol ?: product.currency,
                color = PriceColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Inline keyboard
        if (keyboard != null && keyboard.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            InlineKeyboardView(keyboard = keyboard, onButtonClick = onButtonClick)
        }
    }
}
