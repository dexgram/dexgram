package chat.simplex.common.views.chat.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
expect fun BotImageView(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
)
