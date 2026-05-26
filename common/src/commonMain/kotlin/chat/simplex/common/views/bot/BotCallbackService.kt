package chat.simplex.common.views.bot

import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.helpers.withLongRunningApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object BotCallbackService {

    fun sendCallback(
        chatsCtx: ChatModel.ChatsContext,
        chat: Chat,
        callbackId: String,
        data: String,
        label: String? = null,
        formData: Map<String, String>? = null
    ) {
        val cInfo = chat.chatInfo
        if (!cInfo.sndReady) return

        val callback = BotCallbackQuery(
            callbackId = callbackId,
            data = data,
            label = label,
            formData = formData
        )
        val encoded = callback.encode()

        withLongRunningApi(slow = 60_000) {
            val chatItems = chatModel.controller.apiSendMessages(
                rh = chat.remoteHostId,
                type = cInfo.chatType,
                id = cInfo.apiId,
                scope = cInfo.groupChatScope(),
                composedMessages = listOf(
                    ComposedMessage(
                        fileSource = null,
                        quotedItemId = null,
                        msgContent = MsgContent.MCText(encoded),
                        mentions = emptyMap()
                    )
                )
            )
            if (!chatItems.isNullOrEmpty()) {
                chatItems.forEach { aChatItem ->
                    withContext(Dispatchers.Main) {
                        chatsCtx.addChatItem(chat.remoteHostId, aChatItem.chatInfo, aChatItem.chatItem)
                    }
                }
            }
        }
    }

    fun sendButtonCallback(
        chatsCtx: ChatModel.ChatsContext,
        chat: Chat,
        button: InlineButton,
        callbackId: String?
    ) {
        val cbData = button.callbackData ?: return
        sendCallback(
            chatsCtx = chatsCtx,
            chat = chat,
            callbackId = callbackId ?: UUID.randomUUID().toString(),
            data = cbData,
            label = button.text
        )
    }

    fun sendFormSubmit(
        chatsCtx: ChatModel.ChatsContext,
        chat: Chat,
        form: BotForm,
        formData: Map<String, String>
    ) {
        sendCallback(
            chatsCtx = chatsCtx,
            chat = chat,
            callbackId = form.id,
            data = form.callbackData ?: "form:${form.id}",
            label = "Submitted: ${form.title}",
            formData = formData
        )
    }
}
