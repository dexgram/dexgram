package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.ui.shredgram.theme.*
import kotlinx.coroutines.delay

/**
 * Modal action button configuration
 */
data class UIModalAction(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * Shredgram-styled modal dialog
 * 
 * @param visible Whether the modal is visible
 * @param onDismissRequest Callback when modal should be dismissed
 * @param title Optional title text
 * @param message Optional message text
 * @param messageContent Optional custom message content composable
 * @param primaryAction Primary action button configuration
 * @param secondaryAction Secondary action button configuration
 * @param primaryFullWidth Whether primary button should be full width
 * @param icon Optional icon composable to display at top
 * @param dismissOnBackPress Whether back press dismisses the modal
 * @param dismissOnClickOutside Whether clicking outside dismisses the modal
 */
@Composable
fun UIModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    messageContent: (@Composable () -> Unit)? = null,
    primaryAction: UIModalAction? = null,
    secondaryAction: UIModalAction? = null,
    primaryFullWidth: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    if (!visible) return

    val colors = ShredgramTheme.colors
    val typography = ShredgramTheme.typography

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.space40DP)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusLarge,
                color = colors.surface,
                elevation = Dimensions.space12DP,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Dimensions.space32DP,
                        vertical = Dimensions.space32DP
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    if (icon != null) {
                        icon()
                        Spacer(Modifier.height(Dimensions.space8DP))
                    }

                    // Title
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = typography.bodyLargeBold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Message content
                    if (messageContent != null) {
                        Spacer(Modifier.height(Dimensions.space4DP))
                        messageContent()
                    } else if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(Dimensions.space4DP))
                        Text(
                            text = message,
                            style = typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Buttons
                    val hasButtons = primaryAction != null || secondaryAction != null
                    if (hasButtons) {
                        Spacer(Modifier.height(Dimensions.space48DP))

                        when {
                            // Single primary button (full width)
                            primaryAction != null && secondaryAction == null && primaryFullWidth -> {
                                PrimaryButton(
                                    text = primaryAction.text,
                                    enabled = primaryAction.enabled,
                                    onClick = primaryAction.onClick,
                                    fullWidth = true
                                )
                            }

                            // Two buttons row (Secondary left + Primary right)
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (secondaryAction != null) {
                                        SecondaryButton(
                                            text = secondaryAction.text,
                                            enabled = secondaryAction.enabled,
                                            onClick = secondaryAction.onClick,
                                        )
                                    } else {
                                        Spacer(Modifier.width(Dimensions.space1DP))
                                    }

                                    if (primaryAction != null) {
                                        PrimaryButton(
                                            text = primaryAction.text,
                                            enabled = primaryAction.enabled,
                                            onClick = primaryAction.onClick,
                                            fullWidth = false,
                                            contentPadding = PaddingValues(
                                                horizontal = Dimensions.space16DP,
                                                vertical = Dimensions.space8DP
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal specification for use with ModalController
 */
data class ModalSpec(
    val icon: (@Composable () -> Unit)? = null,
    val title: String? = null,
    val message: String? = null,
    val messageContent: (@Composable () -> Unit)? = null,
    val primaryAction: UIModalAction? = null,
    val secondaryAction: UIModalAction? = null,
    val primaryFullWidth: Boolean = false,
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val autoDismissMillis: Long? = null,
)

/**
 * Modal controller for managing modal state
 */
class ModalController {
    var currentSpec by mutableStateOf<ModalSpec?>(null)
        private set
    
    fun show(spec: ModalSpec) {
        currentSpec = spec
    }
    
    fun dismiss() {
        currentSpec = null
    }
}

/**
 * Composition local for modal controller
 */
val LocalModalController = staticCompositionLocalOf { ModalController() }

/**
 * Modal host that displays modals from the controller
 */
@Composable
fun ModalHost(
    controller: ModalController = LocalModalController.current
) {
    val spec = controller.currentSpec
    
    // Handle auto-dismiss
    LaunchedEffect(spec) {
        spec?.autoDismissMillis?.let { delay ->
            delay(delay)
            controller.dismiss()
        }
    }
    
    UIModal(
        visible = spec != null,
        onDismissRequest = { controller.dismiss() },
        title = spec?.title,
        message = spec?.message,
        messageContent = spec?.messageContent,
        primaryAction = spec?.primaryAction,
        secondaryAction = spec?.secondaryAction,
        primaryFullWidth = spec?.primaryFullWidth ?: false,
        icon = spec?.icon,
        dismissOnBackPress = spec?.dismissOnBackPress ?: true,
        dismissOnClickOutside = spec?.dismissOnClickOutside ?: true,
    )
}

/**
 * Simple alert dialog helper
 */
@Composable
fun AlertModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmText: String = generalGetString(MR.strings.shredgram_action_ok),
    onConfirm: () -> Unit = onDismiss,
) {
    UIModal(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        primaryAction = UIModalAction(
            text = confirmText,
            onClick = {
                onConfirm()
                onDismiss()
            }
        ),
        primaryFullWidth = true
    )
}

/**
 * Confirmation dialog with cancel and confirm buttons
 */
@Composable
fun ConfirmModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmText: String = generalGetString(MR.strings.shredgram_action_confirm),
    cancelText: String = generalGetString(MR.strings.shredgram_action_cancel),
    onConfirm: () -> Unit,
) {
    UIModal(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        primaryAction = UIModalAction(
            text = confirmText,
            onClick = {
                onConfirm()
                onDismiss()
            }
        ),
        secondaryAction = UIModalAction(
            text = cancelText,
            onClick = onDismiss
        )
    )
}

