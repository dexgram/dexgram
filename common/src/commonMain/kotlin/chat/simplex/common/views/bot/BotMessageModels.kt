package chat.simplex.common.views.bot

import kotlinx.serialization.*
import kotlinx.serialization.json.*

private val botJson = Json { ignoreUnknownKeys = true; isLenient = true }

const val BOT_MSG_PREFIX = "__BOT_MSG__"
const val BOT_CB_PREFIX = "__BOT_CB__"

@Serializable
enum class BotMessageType {
    @SerialName("TEXT") TEXT,
    @SerialName("PRODUCT") PRODUCT,
    @SerialName("CATALOG") CATALOG,
    @SerialName("FORM") FORM,
    @SerialName("MEDIA") MEDIA,
    @SerialName("RECEIPT") RECEIPT
}

@Serializable
data class InlineButton(
    val text: String,
    val callbackData: String? = null,
    val url: String? = null,
    val pay: Boolean = false
)

@Serializable
data class ProductCard(
    val id: String,
    val title: String,
    val price: String,
    val currency: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val network: String? = null,
    val tokenSymbol: String? = null,
    val walletAddress: String? = null
)

@Serializable
enum class FormFieldType {
    @SerialName("text") TEXT,
    @SerialName("number") NUMBER,
    @SerialName("email") EMAIL,
    @SerialName("phone") PHONE,
    @SerialName("dropdown") DROPDOWN,
    @SerialName("textarea") TEXTAREA
}

@Serializable
data class FormField(
    val name: String,
    val label: String,
    val fieldType: FormFieldType = FormFieldType.TEXT,
    val required: Boolean = true,
    val placeholder: String? = null,
    val options: List<String>? = null
)

@Serializable
data class BotForm(
    val id: String,
    val title: String,
    val fields: List<FormField>,
    val submitLabel: String = "Submit",
    val callbackData: String? = null
)

@Serializable
data class BotMessage(
    val type: BotMessageType,
    val text: String? = null,
    val keyboard: List<List<InlineButton>>? = null,
    val productCard: ProductCard? = null,
    val products: List<ProductCard>? = null,
    val form: BotForm? = null,
    val mediaUrl: String? = null,
    val mediaCaption: String? = null,
    val callbackId: String? = null
) {
    companion object {
        fun decode(raw: String): BotMessage? {
            if (!raw.startsWith(BOT_MSG_PREFIX)) return null
            return try {
                botJson.decodeFromString(serializer(), raw.removePrefix(BOT_MSG_PREFIX))
            } catch (_: Exception) { null }
        }

        fun isBotMessage(text: String): Boolean = text.startsWith(BOT_MSG_PREFIX)

        fun isCallback(text: String): Boolean = text.startsWith(BOT_CB_PREFIX)

        fun isBotProtocol(text: String): Boolean = isBotMessage(text) || isCallback(text)

        fun humanReadableText(raw: String): String {
            if (raw.startsWith(BOT_MSG_PREFIX)) {
                val msg = decode(raw) ?: return "Bot Message"
                return when (msg.type) {
                    BotMessageType.TEXT -> msg.text ?: "Bot Message"
                    BotMessageType.PRODUCT -> {
                        val card = msg.productCard
                        if (card != null) "Product: ${card.title} - ${card.price} ${card.currency}"
                        else "Product"
                    }
                    BotMessageType.CATALOG -> {
                        val count = msg.products?.size ?: 0
                        "Catalog: $count products"
                    }
                    BotMessageType.FORM -> {
                        val form = msg.form
                        if (form != null) "Form: ${form.title}" else "Form"
                    }
                    BotMessageType.MEDIA -> msg.mediaCaption ?: "Media"
                    BotMessageType.RECEIPT -> "Payment Receipt"
                }
            }
            if (raw.startsWith(BOT_CB_PREFIX)) {
                val cb = BotCallbackQuery.decode(raw) ?: return "Bot Callback"
                return if (cb.formData != null) {
                    cb.label ?: "Form submitted"
                } else {
                    cb.label ?: "Selected: ${cb.data}"
                }
            }
            return raw
        }
    }
}

@Serializable
data class BotCallbackQuery(
    val callbackId: String,
    val data: String,
    val label: String? = null,
    val formData: Map<String, String>? = null
) {
    fun encode(): String = BOT_CB_PREFIX + botJson.encodeToString(serializer(), this)

    companion object {
        fun decode(raw: String): BotCallbackQuery? {
            if (!raw.startsWith(BOT_CB_PREFIX)) return null
            return try {
                botJson.decodeFromString(serializer(), raw.removePrefix(BOT_CB_PREFIX))
            } catch (_: Exception) { null }
        }
    }
}
