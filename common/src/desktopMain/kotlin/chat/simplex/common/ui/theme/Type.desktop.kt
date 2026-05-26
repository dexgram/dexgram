package chat.simplex.common.ui.theme

import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.platform.Font
import chat.simplex.common.platform.desktopPlatform
import chat.simplex.res.MR

actual val Inter: FontFamily = FontFamily(
  Font(MR.fonts.Inter.regular.file),
  Font(MR.fonts.Inter.italic.file, style = FontStyle.Italic),
  Font(MR.fonts.Inter.bold.file, FontWeight.Bold),
  Font(MR.fonts.Inter.semibold.file, FontWeight.SemiBold),
  Font(MR.fonts.Inter.medium.file, FontWeight.Medium),
  Font(MR.fonts.Inter.light.file, FontWeight.Light)
)

actual val EmojiFont: FontFamily = if (desktopPlatform.isMac()) {
  FontFamily.Default
} else {
  FontFamily(
    Font(MR.fonts.NotoColorEmoji.regular.file),
    Font(MR.fonts.NotoColorEmoji.regular.file, style = FontStyle.Italic),
    Font(MR.fonts.NotoColorEmoji.regular.file, FontWeight.Bold),
    Font(MR.fonts.NotoColorEmoji.regular.file, FontWeight.SemiBold),
    Font(MR.fonts.NotoColorEmoji.regular.file, FontWeight.Medium),
    Font(MR.fonts.NotoColorEmoji.regular.file, FontWeight.Light)
  )
}

// Shredgram fonts - Manrope for headlines/titles
actual val Manrope: FontFamily = FontFamily(
  Font(MR.fonts.Manrope.regular.file, FontWeight.Normal),
  Font(MR.fonts.Manrope.medium.file, FontWeight.Medium),
  Font(MR.fonts.Manrope.semibold.file, FontWeight.SemiBold),
  Font(MR.fonts.Manrope.bold.file, FontWeight.Bold),
  Font(MR.fonts.Manrope.extrabold.file, FontWeight.ExtraBold)
)

// Shredgram fonts - DMSans for body/labels
actual val DMSans: FontFamily = FontFamily(
  Font(MR.fonts.DMSans.light.file, FontWeight.Light),
  Font(MR.fonts.DMSans.regular.file, FontWeight.Normal),
  Font(MR.fonts.DMSans.medium.file, FontWeight.Medium),
  Font(MR.fonts.DMSans.semibold.file, FontWeight.SemiBold),
  Font(MR.fonts.DMSans.bold.file, FontWeight.Bold)
)
