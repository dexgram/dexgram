package chat.simplex.common.views.chat.item

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.Chat
import chat.simplex.common.model.ChatItem
import chat.simplex.common.model.ChatModel
import chat.simplex.common.views.bot.*

private val BotTextColor = Color.Black
private val ReceiptBg = Color(0xFF065F46)

@Composable
fun CIBotMessageView(
    botMessage: BotMessage,
    ci: ChatItem,
    chatsCtx: ChatModel.ChatsContext,
    chat: Chat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (botMessage.type) {
            BotMessageType.TEXT -> {
                if (botMessage.text != null) {
                    Text(
                        text = botMessage.text,
                        color = BotTextColor,
                        fontSize = 15.sp
                    )
                }
                botMessage.keyboard?.let { kb ->
                    InlineKeyboardView(keyboard = kb) { button ->
                        BotCallbackService.sendButtonCallback(
                            chatsCtx = chatsCtx,
                            chat = chat,
                            button = button,
                            callbackId = botMessage.callbackId
                        )
                    }
                }
            }

            BotMessageType.PRODUCT -> {
                val card = botMessage.productCard
                if (card != null) {
                    ProductCardView(
                        product = card,
                        keyboard = botMessage.keyboard,
                        onButtonClick = { button ->
                            BotCallbackService.sendButtonCallback(
                                chatsCtx = chatsCtx,
                                chat = chat,
                                button = button,
                                callbackId = botMessage.callbackId
                            )
                        }
                    )
                }
            }

            BotMessageType.CATALOG -> {
                val products = botMessage.products
                if (products != null && products.isNotEmpty()) {
                    BotCatalogView(
                        products = products,
                        text = botMessage.text,
                        onProductClick = { product ->
                            BotCallbackService.sendCallback(
                                chatsCtx = chatsCtx,
                                chat = chat,
                                callbackId = botMessage.callbackId ?: "",
                                data = "view:${product.id}",
                                label = product.title
                            )
                        }
                    )
                }
            }

            BotMessageType.FORM -> {
                val form = botMessage.form
                if (form != null) {
                    BotFormView(
                        form = form,
                        text = botMessage.text,
                        onSubmit = { formData ->
                            BotCallbackService.sendFormSubmit(
                                chatsCtx = chatsCtx,
                                chat = chat,
                                form = form,
                                formData = formData
                            )
                        }
                    )
                }
            }

            BotMessageType.MEDIA -> {
                if (botMessage.mediaCaption != null) {
                    Text(
                        text = botMessage.mediaCaption,
                        color = BotTextColor,
                        fontSize = 15.sp
                    )
                }
                botMessage.keyboard?.let { kb ->
                    InlineKeyboardView(keyboard = kb) { button ->
                        BotCallbackService.sendButtonCallback(
                            chatsCtx = chatsCtx,
                            chat = chat,
                            button = button,
                            callbackId = botMessage.callbackId
                        )
                    }
                }
            }

            BotMessageType.RECEIPT -> {
                Text(
                    text = botMessage.text ?: "Payment Receipt",
                    color = Color(0xFF059669),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                botMessage.keyboard?.let { kb ->
                    InlineKeyboardView(keyboard = kb) { button ->
                        BotCallbackService.sendButtonCallback(
                            chatsCtx = chatsCtx,
                            chat = chat,
                            button = button,
                            callbackId = botMessage.callbackId
                        )
                    }
                }
            }
        }
    }
}
