package chat.simplex.common.views.chat.item

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

private val CatalogCardBg1 = Color(0xFF1E2440)
private val CatalogCardBg2 = Color(0xFF131830)
private val PriceGreen = Color(0xFF34D399)
private val TitleColor = Color.Black
private val CardTitleColor = Color(0xFFF1F5F9)
private val DescGray = Color(0xFFCBD5E1)
private val ButtonBg = Color(0xFF2A3352)
private val ButtonTextColor = Color(0xFFE2E8F0)

@Composable
fun BotCatalogView(
    products: List<ProductCard>,
    text: String? = null,
    onProductClick: (ProductCard) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        if (text != null) {
            Text(
                text = text,
                color = TitleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (product in products) {
                CatalogCard(product = product, onClick = { onProductClick(product) })
            }
        }
    }
}

@Composable
private fun CatalogCard(
    product: ProductCard,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(CatalogCardBg1, CatalogCardBg2),
                    start = Offset(0f, 0f),
                    end = Offset(300f, 300f)
                )
            )
            .padding(0.dp)
    ) {
        if (product.imageUrl != null && product.imageUrl.isNotBlank()) {
            BotImageView(
                url = product.imageUrl,
                contentDescription = product.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
        }

        Column(Modifier.padding(12.dp)) {
        if (product.network != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x33FFFFFF),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = product.network.uppercase(),
                    color = DescGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = product.title,
            color = CardTitleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (product.description != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = product.description,
                color = DescGray,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = product.price,
                color = PriceGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = product.tokenSymbol ?: product.currency,
                color = PriceGreen.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ButtonBg),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            elevation = ButtonDefaults.elevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "View",
                color = ButtonTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        } // inner Column (text content)
    }
}
