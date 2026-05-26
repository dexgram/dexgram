package chat.simplex.common.ui.theme

import androidx.compose.ui.text.font.*
import chat.simplex.res.MR

actual val Inter: FontFamily = FontFamily(
  Font(MR.fonts.Inter.regular.fontResourceId),
  Font(MR.fonts.Inter.italic.fontResourceId, style = FontStyle.Italic),
  Font(MR.fonts.Inter.bold.fontResourceId, FontWeight.Bold),
  Font(MR.fonts.Inter.semibold.fontResourceId, FontWeight.SemiBold),
  Font(MR.fonts.Inter.medium.fontResourceId, FontWeight.Medium),
  Font(MR.fonts.Inter.light.fontResourceId, FontWeight.Light)
)

actual val EmojiFont: FontFamily = FontFamily.Default

// Shredgram fonts - Manrope for headlines/titles
actual val Manrope: FontFamily = FontFamily(
  Font(MR.fonts.Manrope.regular.fontResourceId, FontWeight.Normal),
  Font(MR.fonts.Manrope.medium.fontResourceId, FontWeight.Medium),
  Font(MR.fonts.Manrope.semibold.fontResourceId, FontWeight.SemiBold),
  Font(MR.fonts.Manrope.bold.fontResourceId, FontWeight.Bold),
  Font(MR.fonts.Manrope.extrabold.fontResourceId, FontWeight.ExtraBold)
)

// Shredgram fonts - DMSans for body/labels
actual val DMSans: FontFamily = FontFamily(
  Font(MR.fonts.DMSans.light.fontResourceId, FontWeight.Light),
  Font(MR.fonts.DMSans.regular.fontResourceId, FontWeight.Normal),
  Font(MR.fonts.DMSans.medium.fontResourceId, FontWeight.Medium),
  Font(MR.fonts.DMSans.semibold.fontResourceId, FontWeight.SemiBold),
  Font(MR.fonts.DMSans.bold.fontResourceId, FontWeight.Bold)
)
