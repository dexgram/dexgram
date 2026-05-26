package chat.simplex.common.views.chatlist

import SectionCustomFooter
import SectionDivider
import SectionItemView
import TextIconSpaced
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.apiDeleteChatTag
import chat.simplex.common.model.ChatController.apiSetChatTags
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatModel.clearActiveChatFilterIfNeeded
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.views.chat.item.ItemAction
import chat.simplex.common.views.chat.item.ReactionIcon
import chat.simplex.common.views.chat.topPaddingToContent
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.*

@Composable
fun TagListView(rhId: Long?, chat: Chat? = null, close: () -> Unit, reorderMode: Boolean) {
  val userTags = remember { chatModel.userTags }
  val oneHandUI = remember { appPrefs.oneHandUI.state }
  val listState = LocalAppBarHandler.current?.listState ?: rememberLazyListState()
  val saving = remember { mutableStateOf(false) }
  val chatTagIds = derivedStateOf { chat?.chatInfo?.chatTags ?: emptyList() }

  fun reorderTags(tagIds: List<Long>) {
    saving.value = true
    withBGApi {
      try {
        chatModel.controller.apiReorderChatTags(rhId, tagIds)
      } catch (e: Exception) {
      } finally {
        saving.value = false
      }
    }
  }

  val dragDropState =
    rememberDragDropState(listState) { fromIndex, toIndex ->
      userTags.value = userTags.value.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
      reorderTags(userTags.value.map { it.chatTagId })
    }
  val topPaddingToContent = topPaddingToContent(false)

  LazyColumnWithScrollBar(
    modifier = if (reorderMode) Modifier.dragContainer(dragDropState) else Modifier,
    state = listState,
    contentPadding = PaddingValues(
      top = if (oneHandUI.value) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else topPaddingToContent,
      bottom = if (oneHandUI.value) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppBarHeight * fontSizeSqrtMultiplier else 0.dp
    ),
    verticalArrangement = if (oneHandUI.value) Arrangement.Bottom else Arrangement.Top,
  ) {
    @Composable fun CreateList() {
      SectionItemView({
        ModalManager.start.showModalCloseable { close ->
          TagListEditor(rhId = rhId, close = close, chat = chat)
        }
      }) {
        Icon(painterResource(MR.images.ic_add), stringResource(MR.strings.create_list), tint = MaterialTheme.colors.primary)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(stringResource(MR.strings.create_list), color = MaterialTheme.colors.primary)
      }
    }

    if (oneHandUI.value && !reorderMode) {
      item {
        CreateList()
      }
    }
    itemsIndexed(userTags.value, key = { _, item -> item.chatTagId }) { index, tag ->
      DraggableItem(dragDropState, index) { isDragging ->
        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

        Card(
          elevation = elevation,
          backgroundColor = if (isDragging) colors.surface else Color.Unspecified
        ) {
          Column {
            val selected = chatTagIds.value.contains(tag.chatTagId)

            Row(
              Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = DEFAULT_MIN_SECTION_ITEM_HEIGHT)
                .clickable(
                  enabled = !saving.value && !reorderMode,
                  onClick = {
                    if (chat == null) {
                      ModalManager.start.showModalCloseable { close ->
                        TagListEditor(
                          rhId = rhId,
                          tagId = tag.chatTagId,
                          close = close,
                          emoji = tag.chatTagEmoji,
                          name = tag.chatTagText,
                        )
                      }
                    } else {
                      saving.value = true
                      setTag(rhId = rhId, tagId = if (selected) null else tag.chatTagId, chat = chat, close = {
                        saving.value = false
                        close()
                      })
                    }
                  },
                )
                .padding(PaddingValues(horizontal = DEFAULT_PADDING, vertical = DEFAULT_MIN_SECTION_ITEM_PADDING_VERTICAL)),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (tag.chatTagEmoji != null) {
                ReactionIcon(tag.chatTagEmoji, fontSize = 14.sp)
              } else {
                Icon(painterResource(MR.images.ic_label), null, Modifier.size(18.sp.toDp()), tint = MaterialTheme.colors.onBackground)
              }
              Spacer(Modifier.padding(horizontal = 4.dp))
              Text(
                tag.chatTagText,
                color = MenuTextColor,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
              )
              if (selected) {
                Spacer(Modifier.weight(1f))
                Icon(painterResource(MR.images.ic_done_filled), null, Modifier.size(20.dp), tint = MaterialTheme.colors.onBackground)
              } else if (reorderMode) {
                Spacer(Modifier.weight(1f))
                Icon(painterResource(MR.images.ic_drag_handle), null, Modifier.size(20.dp), tint = MaterialTheme.colors.secondary)
              }
            }
            SectionDivider()
          }
        }
      }
    }
    if (!oneHandUI.value && !reorderMode) {
      item {
        CreateList()
      }
    }
  }
}

// Standalone TagListEditorDialog - can be used without ModalData context
@Composable
fun TagListEditorDialog(
  rhId: Long?,
  chat: Chat? = null,
  tagId: Long? = null,
  emoji: String? = null,
  name: String = "",
  close: () -> Unit
) {
  val userTags = remember { chatModel.userTags }
  val newEmoji = remember { mutableStateOf(emoji) }
  val newName = remember { mutableStateOf(name) }
  val saving = remember { mutableStateOf<Boolean?>(null) }
  val trimmedName = remember { derivedStateOf { newName.value.trim() } }
  val isDuplicateEmojiOrName = remember {
    derivedStateOf {
      userTags.value.any { tag ->
        tag.chatTagId != tagId &&
            ((newEmoji.value != null && tag.chatTagEmoji == newEmoji.value) || tag.chatTagText == trimmedName.value)
      }
    }
  }

  fun createTag() {
    saving.value = true
    withBGApi {
      try {
        val updatedTags = chatModel.controller.apiCreateChatTag(rhId, ChatTagData(newEmoji.value, trimmedName.value))
        if (updatedTags != null) {
          saving.value = false
          userTags.value = updatedTags
          close()
        } else {
          saving.value = null
          return@withBGApi
        }

        if (chat != null) {
          val createdTag = updatedTags.firstOrNull() { it.chatTagText == trimmedName.value && it.chatTagEmoji == newEmoji.value }

          if (createdTag != null) {
            setTagForDialog(rhId, createdTag.chatTagId, chat, close = {
              saving.value = false
              close()
            })
          }
        }
      } catch (e: Exception) {
        saving.value = null
      }
    }
  }

  fun updateTag() {
    saving.value = true
    withBGApi {
      try {
        if (chatModel.controller.apiUpdateChatTag(rhId, tagId!!, ChatTagData(newEmoji.value, trimmedName.value))) {
          userTags.value = userTags.value.map { tag ->
            if (tag.chatTagId == tagId) {
              tag.copy(chatTagEmoji = newEmoji.value, chatTagText = trimmedName.value)
            } else {
              tag
            }
          }
        } else {
          saving.value = null
          return@withBGApi
        }
        saving.value = false
        close()
      } catch (e: Exception) {
        saving.value = null
      }
    }
  }

  val showError = derivedStateOf { isDuplicateEmojiOrName.value && saving.value != false }
  val isEditing = tagId != null
  
  // Shredgram-style bottom sheet design
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.onBackground.copy(alpha = 0.5f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { close() },  // Close when clicking outside
    contentAlignment = Alignment.BottomCenter
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .imePadding()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null
        ) { /* Consume clicks to prevent closing */ }
        .background(
          MaterialTheme.colors.surface,
          shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 32.dp
          )
        )
        .padding(16.dp)
    ) {
      // Header row: Close button and title
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = close,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            painterResource(MR.images.ic_close),
            contentDescription = "Close",
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(24.dp)
          )
        }
        
        Text(
          text = if (isEditing) "Edit List" else "Create List",
          fontSize = 18.sp,
          fontFamily = Manrope,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onBackground,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).offset(x = (-24).dp)
        )
      }
      
      // Description
      Text(
        text = "Organize your chats by creating custom lists",
        fontSize = 14.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
      
      Spacer(Modifier.height(16.dp))
      
      // Label
      Text(
        text = "List name",
        fontSize = 14.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.onBackground
      )
      
      Spacer(Modifier.height(8.dp))
      
      // Input field - Shredgram style (pill shape)
      val focusRequester = remember { FocusRequester() }
      var isFocused by remember { mutableStateOf(false) }
      
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
          .border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (showError.value) MaterialTheme.colors.error else if (isFocused) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
          )
          .background(MaterialTheme.colors.background)
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        BasicTextField(
          value = newName.value,
          onValueChange = { newName.value = it },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
          textStyle = TextStyle(
            color = MaterialTheme.colors.onBackground,
            fontSize = 14.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.Normal
          ),
          singleLine = true,
          cursorBrush = SolidColor(MaterialTheme.colors.primary),
          decorationBox = { innerTextField ->
            if (newName.value.isEmpty()) {
              Text(
                "Enter list name",
                color = MaterialTheme.colors.secondary,
                fontSize = 14.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Normal
              )
            }
            innerTextField()
          }
        )
      }
      
      // Error message
      if (showError.value) {
        Spacer(Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painterResource(MR.images.ic_error),
            contentDescription = "Error",
            tint = MaterialTheme.colors.error,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(4.dp))
          Text(
            "List name already exists",
            fontSize = 12.sp,
            fontFamily = DMSans,
            color = MaterialTheme.colors.error
          )
        }
      }
      
      Spacer(Modifier.height(24.dp))
      
      // Create/Save button - Shredgram style (pill shape, ElectricBlue500)
      val disabled = saving.value == true ||
          (trimmedName.value == name && newEmoji.value == emoji) ||
          trimmedName.value.isEmpty() ||
          isDuplicateEmojiOrName.value
      
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
      ) {
        Row(
          modifier = Modifier
            .height(48.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (disabled) MaterialTheme.colors.secondary else MaterialTheme.colors.primary)
            .clickable(enabled = !disabled) {
              if (tagId == null) createTag() else updateTag()
            }
            .padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            painterResource(MR.images.ic_label),
            contentDescription = null,
            tint = MaterialTheme.colors.onPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (isEditing) "Save" else "Create",
            fontSize = 14.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onPrimary
          )
        }
      }
      
      Spacer(Modifier.height(24.dp))
    }
  }
}

// Helper function for TagListEditorDialog
private fun setTagForDialog(rhId: Long?, tagId: Long?, chat: Chat, close: () -> Unit) {
  withBGApi {
    val tagIds: List<Long> = if (tagId == null) {
      emptyList()
    } else {
      listOf(tagId)
    }

    try {
      val result = ChatController.apiSetChatTags(rh = rhId, type = chat.chatInfo.chatType, id = chat.chatInfo.apiId, tagIds = tagIds)

      if (result != null) {
        val oldTags = chat.chatInfo.chatTags
        chatModel.userTags.value = result.first
        when (val cInfo = chat.chatInfo) {
          is ChatInfo.Direct -> {
            val contact = cInfo.contact.copy(chatTags = result.second)
            withContext(Dispatchers.Main) {
              chatModel.chatsContext.updateContact(rhId, contact)
            }
          }

          is ChatInfo.Group -> {
            val group = cInfo.groupInfo.copy(chatTags = result.second)
            withContext(Dispatchers.Main) {
              chatModel.chatsContext.updateGroup(rhId, group)
            }
          }

          else -> {}
        }
        chatModel.moveChatTagUnread(chat, oldTags, result.second)
        close()
      }
    } catch (e: Exception) {
    }
  }
}

@Composable
fun ModalData.TagListEditor(
  rhId: Long?,
  chat: Chat? = null,
  tagId: Long? = null,
  emoji: String? = null,
  name: String = "",
  close: () -> Unit
) {
  val userTags = remember { chatModel.userTags }
  val newEmoji = remember { stateGetOrPutNullable("chatTagEmoji") { emoji } }
  val newName = remember { stateGetOrPut("chatTagName") { name } }
  val saving = remember { mutableStateOf<Boolean?>(null) }
  val trimmedName = remember { derivedStateOf { newName.value.trim() } }
  val isDuplicateEmojiOrName = remember {
    derivedStateOf {
      userTags.value.any { tag ->
        tag.chatTagId != tagId &&
            ((newEmoji.value != null && tag.chatTagEmoji == newEmoji.value) || tag.chatTagText == trimmedName.value)
      }
    }
  }

  fun createTag() {
    saving.value = true
    withBGApi {
      try {
        val updatedTags = chatModel.controller.apiCreateChatTag(rhId, ChatTagData(newEmoji.value, trimmedName.value))
        if (updatedTags != null) {
          saving.value = false
          userTags.value = updatedTags
          close()
        } else {
          saving.value = null
          return@withBGApi
        }

        if (chat != null) {
          val createdTag = updatedTags.firstOrNull() { it.chatTagText == trimmedName.value && it.chatTagEmoji == newEmoji.value }

          if (createdTag != null) {
            setTag(rhId, createdTag.chatTagId, chat, close = {
              saving.value = false
              close()
            })
          }
        }
      } catch (e: Exception) {
        saving.value = null
      }
    }
  }

  fun updateTag() {
    saving.value = true
    withBGApi {
      try {
        if (chatModel.controller.apiUpdateChatTag(rhId, tagId!!, ChatTagData(newEmoji.value, trimmedName.value))) {
          userTags.value = userTags.value.map { tag ->
            if (tag.chatTagId == tagId) {
              tag.copy(chatTagEmoji = newEmoji.value, chatTagText = trimmedName.value)
            } else {
              tag
            }
          }
        } else {
          saving.value = null
          return@withBGApi
        }
        saving.value = false
        close()
      } catch (e: Exception) {
        saving.value = null
      }
    }
  }

  val showError = derivedStateOf { isDuplicateEmojiOrName.value && saving.value != false }
  val isEditing = tagId != null
  
  // Shredgram-style bottom sheet design
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.onBackground.copy(alpha = 0.5f)),
    contentAlignment = Alignment.BottomCenter
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .imePadding()
        .background(
          MaterialTheme.colors.surface,
          shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 32.dp
          )
        )
        .padding(16.dp)
    ) {
      // Header row: Close button and title
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = close,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            painterResource(MR.images.ic_close),
            contentDescription = "Close",
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(24.dp)
          )
        }
        
        Text(
          text = if (isEditing) "Edit List" else "Create List",
          fontSize = 18.sp,
          fontFamily = Manrope,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onBackground,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).offset(x = (-24).dp)
        )
      }
      
      // Description
      Text(
        text = "Organize your chats by creating custom lists",
        fontSize = 14.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
      
      Spacer(Modifier.height(16.dp))
      
      // Label
      Text(
        text = "List name",
        fontSize = 14.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colors.onBackground
      )
      
      Spacer(Modifier.height(8.dp))
      
      // Input field - Shredgram style (pill shape)
      val focusRequester = remember { FocusRequester() }
      var isFocused by remember { mutableStateOf(false) }
      
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
          .border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (showError.value) MaterialTheme.colors.error else if (isFocused) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
          )
          .background(MaterialTheme.colors.background)
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        BasicTextField(
          value = newName.value,
          onValueChange = { newName.value = it },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
          textStyle = TextStyle(
            color = MaterialTheme.colors.onBackground,
            fontSize = 14.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.Normal
          ),
          singleLine = true,
          cursorBrush = SolidColor(MaterialTheme.colors.primary),
          decorationBox = { innerTextField ->
            if (newName.value.isEmpty()) {
              Text(
                "Enter list name",
                color = MaterialTheme.colors.secondary,
                fontSize = 14.sp,
                fontFamily = DMSans,
                fontWeight = FontWeight.Normal
              )
            }
            innerTextField()
          }
        )
      }
      
      // Error message
      if (showError.value) {
        Spacer(Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painterResource(MR.images.ic_error),
            contentDescription = "Error",
            tint = MaterialTheme.colors.error,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(4.dp))
          Text(
            "List name already exists",
            fontSize = 12.sp,
            fontFamily = DMSans,
            color = MaterialTheme.colors.error
          )
        }
      }
      
      Spacer(Modifier.height(24.dp))
      
      // Create/Save button - Shredgram style (pill shape, ElectricBlue500)
      val disabled = saving.value == true ||
          (trimmedName.value == name && newEmoji.value == emoji) ||
          trimmedName.value.isEmpty() ||
          isDuplicateEmojiOrName.value
      
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
      ) {
        Row(
          modifier = Modifier
            .height(48.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (disabled) MaterialTheme.colors.secondary else MaterialTheme.colors.primary)
            .clickable(enabled = !disabled) {
              if (tagId == null) createTag() else updateTag()
            }
            .padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            painterResource(MR.images.ic_label),
            contentDescription = null,
            tint = MaterialTheme.colors.onPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (isEditing) "Save" else "Create",
            fontSize = 14.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onPrimary
          )
        }
      }
      
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
fun TagsDropdownMenu(rhId: Long?, tag: ChatTag, showMenu: MutableState<Boolean>, saving: MutableState<Boolean>) {
  DefaultDropdownMenu(showMenu, dropdownMenuItems = {
    EditTagAction(rhId, tag, showMenu)
    DeleteTagAction(rhId, tag, showMenu, saving)
    ChangeOrderTagAction(rhId, showMenu)
  })
}

@Composable
private fun DeleteTagAction(rhId: Long?, tag: ChatTag, showMenu: MutableState<Boolean>, saving: MutableState<Boolean>) {
  ItemAction(
    stringResource(MR.strings.delete_chat_list_menu_action),
    painterResource(MR.images.ic_delete),
    onClick = {
      deleteTagDialog(rhId, tag, saving)
      showMenu.value = false
    },
    color = Color.Red
  )
}

@Composable
private fun EditTagAction(rhId: Long?, tag: ChatTag, showMenu: MutableState<Boolean>) {
  ItemAction(
    stringResource(MR.strings.edit_chat_list_menu_action),
    painterResource(MR.images.ic_edit),
    onClick = {
      showMenu.value = false
      ModalManager.start.showModalCloseable { close ->
        TagListEditor(
          rhId = rhId,
          tagId = tag.chatTagId,
          close = close,
          emoji = tag.chatTagEmoji,
          name = tag.chatTagText
        )
      }
    },
    color = MenuTextColor
  )
}

@Composable
private fun ChangeOrderTagAction(rhId: Long?, showMenu: MutableState<Boolean>) {
  ItemAction(
    stringResource(MR.strings.change_order_chat_list_menu_action),
    painterResource(MR.images.ic_drag_handle),
    onClick = {
      showMenu.value = false
      ModalManager.start.showModalCloseable { close ->
        TagListView(rhId = rhId, close = close, reorderMode = true)
      }
    },
    color = MenuTextColor
  )
}

@Composable
expect fun ChatTagInput(name: MutableState<String>, showError: State<Boolean>, emoji: MutableState<String?>)

@Composable
fun TagListNameTextField(name: MutableState<String>, showError: State<Boolean>) {
  var focused by rememberSaveable { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }
  val colors = TextFieldDefaults.textFieldColors(
    backgroundColor = Color.Unspecified,
    focusedIndicatorColor = MaterialTheme.colors.secondary.copy(alpha = 0.6f),
    unfocusedIndicatorColor = CurrentColors.value.colors.secondary.copy(alpha = 0.3f),
    cursorColor = MaterialTheme.colors.secondary,
  )
  BasicTextField(
    value = name.value,
    onValueChange = { name.value = it },
    interactionSource = interactionSource,
    modifier = Modifier
      .fillMaxWidth()
      .indicatorLine(true, showError.value, interactionSource, colors)
      .heightIn(min = TextFieldDefaults.MinHeight)
      .onFocusChanged { focused = it.isFocused }
      .focusRequester(focusRequester),
    textStyle = TextStyle(fontSize = 18.sp, color = MaterialTheme.colors.onBackground),
    singleLine = true,
    cursorBrush = SolidColor(MaterialTheme.colors.secondary),
    decorationBox = @Composable { innerTextField ->
      TextFieldDefaults.TextFieldDecorationBox(
        value = name.value,
        innerTextField = innerTextField,
        placeholder = {
          Text(generalGetString(MR.strings.list_name_field_placeholder), style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.secondary, lineHeight = 22.sp))
        },
        contentPadding = PaddingValues(),
        label = null,
        visualTransformation = VisualTransformation.None,
        leadingIcon = null,
        singleLine = true,
        enabled = true,
        isError = false,
        interactionSource = remember { MutableInteractionSource() },
        colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Unspecified)
      )
    }
  )
}

private fun setTag(rhId: Long?, tagId: Long?, chat: Chat, close: () -> Unit) {
  withBGApi {
    val tagIds: List<Long> = if (tagId == null) {
      emptyList()
    } else {
      listOf(tagId)
    }

    try {
      val result = apiSetChatTags(rh = rhId, type = chat.chatInfo.chatType, id = chat.chatInfo.apiId, tagIds = tagIds)

      if (result != null) {
        val oldTags = chat.chatInfo.chatTags
        chatModel.userTags.value = result.first
        when (val cInfo = chat.chatInfo) {
          is ChatInfo.Direct -> {
            val contact = cInfo.contact.copy(chatTags = result.second)
            withContext(Dispatchers.Main) {
              chatModel.chatsContext.updateContact(rhId, contact)
            }
          }

          is ChatInfo.Group -> {
            val group = cInfo.groupInfo.copy(chatTags = result.second)
            withContext(Dispatchers.Main) {
              chatModel.chatsContext.updateGroup(rhId, group)
            }
          }

          else -> {}
        }
        chatModel.moveChatTagUnread(chat, oldTags, result.second)
        close()
      }
    } catch (e: Exception) {
    }
  }
}

private fun deleteTag(rhId: Long?, tag: ChatTag, saving: MutableState<Boolean>) {
  withBGApi {
    saving.value = true

    try {
      val tagId = tag.chatTagId
      if (apiDeleteChatTag(rhId, tagId)) {
        chatModel.userTags.value = chatModel.userTags.value.filter { it.chatTagId != tagId }
        clearActiveChatFilterIfNeeded()
        chatModel.chats.value.forEach { c ->
          when (val cInfo = c.chatInfo) {
            is ChatInfo.Direct -> {
              val contact = cInfo.contact.copy(chatTags = cInfo.contact.chatTags.filter { it != tagId })
              withContext(Dispatchers.Main) {
                chatModel.chatsContext.updateContact(rhId, contact)
              }
            }
            is ChatInfo.Group -> {
              val group = cInfo.groupInfo.copy(chatTags = cInfo.groupInfo.chatTags.filter { it != tagId })
              withContext(Dispatchers.Main) {
                chatModel.chatsContext.updateGroup(rhId, group)
              }
            }
            else -> {}
          }
        }
      }

    } catch (e: Exception) {
    } finally {
      saving.value = false
    }
  }
}

fun deleteTagDialog(rhId: Long?, tag: ChatTag, saving: MutableState<Boolean>) {
  AlertManager.shared.showAlertDialogButtonsColumn(
    title = generalGetString(MR.strings.delete_chat_list_question),
    text = String.format(generalGetString(MR.strings.delete_chat_list_warning), tag.chatTagText),
    buttons = {
      SectionItemView({
        AlertManager.shared.hideAlert()
        deleteTag(rhId, tag, saving)
      }) {
        Text(
          generalGetString(MR.strings.confirm_verb),
          Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          color = colors.error
        )
      }
      SectionItemView({
        AlertManager.shared.hideAlert()
      }) {
        Text(
          stringResource(MR.strings.cancel_verb),
          Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          color = colors.primary
        )
      }
    }
  )
}
