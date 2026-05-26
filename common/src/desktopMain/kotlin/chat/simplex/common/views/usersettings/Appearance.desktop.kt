package chat.simplex.common.views.usersettings

import SectionBottomSpacer
import SectionDividerSpaced
import SectionSpacer
import SectionView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatModel
import chat.simplex.common.model.SharedPreference
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
actual fun AppearanceView(m: ChatModel) {
  AppearanceScope.AppearanceLayout(
    m.controller.appPrefs.appLanguage,
    m.controller.appPrefs.systemDarkTheme,
  )
}

@Composable
fun AppearanceScope.AppearanceLayout(
  languagePref: SharedPreference<String?>,
  systemDarkTheme: SharedPreference<String?>,
) {
  BackHandler(onBack = { ModalManager.start.closeModal() })
  val languageState = rememberSaveable { mutableStateOf(languagePref.get() ?: "system") }
  val colorModeState: State<DefaultThemeMode?> = remember(appPrefs.currentTheme.get(), CurrentColors.value.base.mode) {
    derivedStateOf {
      if (appPrefs.currentTheme.get() == DefaultTheme.SYSTEM_THEME_NAME) null else CurrentColors.value.base.mode
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        backgroundColor = MaterialTheme.colors.background,
        elevation = 0.dp,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 24.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = { ModalManager.start.closeModal() }) {
            Icon(
              painterResource(MR.images.ic_arrow_back_ios_new),
              contentDescription = "Back",
              tint = MaterialTheme.colors.onBackground
            )
          }
          Text(
            stringResource(MR.strings.appearance_settings),
            fontSize = 18.sp,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground
          )
        }
      }
    },
    backgroundColor = MaterialTheme.colors.background,
    bottomBar = {}
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(Modifier.height(16.dp))

      AppearanceDropdownItem(
        icon = painterResource(MR.images.ic_info),
        text = generalGetString(MR.strings.settings_section_title_language).lowercase().replaceFirstChar {
          if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
      ) {
        AppearanceScope.LangSelector(languageState) { selected ->
          languageState.value = selected
          withApi {
            delay(200)
            if (selected == "system") {
              languagePref.set(null)
              Locale.setDefault(defaultLocale)
            } else {
              languagePref.set(selected)
              Locale.setDefault(Locale.forLanguageTag(selected))
            }
          }
        }
      }

      AppearanceDropdownItem(
        icon = painterResource(MR.images.ic_light_mode),
        text = stringResource(MR.strings.color_mode)
      ) {
        AppearanceScope.ColorModeSelector(colorModeState) { selected ->
          AppearanceScope.applyColorModeSelection(selected)
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun AppearanceDropdownItem(
  icon: Painter,
  text: String,
  content: @Composable () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp, horizontal = 4.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(percent = 50))
          .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          icon,
          contentDescription = text,
          modifier = Modifier.size(22.dp),
          tint = MaterialTheme.colors.onBackground
        )
      }
      Spacer(Modifier.width(14.dp))
      Text(
        text,
        modifier = Modifier.weight(1f),
        fontSize = 15.sp,
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onBackground
      )
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.padding(start = 58.dp)) {
      content()
    }
  }
}

@Composable
fun DensityScaleSection() {
  val localDensityScale = remember { mutableStateOf(appPrefs.densityScale.get()) }
  SectionView(stringResource(MR.strings.appearance_zoom).uppercase(), contentPadding = PaddingValues(horizontal = DEFAULT_PADDING)) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(50.dp)
        .background(MaterialTheme.colors.surface, RoundedCornerShape(percent = 22))
        .clip(RoundedCornerShape(percent = 22))
        .clickable {
          localDensityScale.value = 1f
          appPrefs.densityScale.set(localDensityScale.value)
        },
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(
          LocalDensity provides Density(LocalDensity.current.density * localDensityScale.value, LocalDensity.current.fontScale)
        ) {
          Text("${localDensityScale.value}",
            color = if (localDensityScale.value == 1f) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground,
            fontSize = 12.sp,
            maxLines = 1
          )
        }
      }
      Spacer(Modifier.width(15.dp))
      Slider(
        localDensityScale.value,
        valueRange = 1f..2f,
        steps = 11,
        onValueChange = {
          val diff = it % 0.1f
          localDensityScale.value = String.format(Locale.US, "%.1f", it + (if (diff >= 0.05f) -diff + 0.1f else -diff)).toFloatOrNull() ?: 1f
        },
        onValueChangeFinished = {
          appPrefs.densityScale.set(localDensityScale.value)
        },
        colors = SliderDefaults.colors(
          activeTickColor = Color.Transparent,
          inactiveTickColor = Color.Transparent,
        )
      )
    }
  }
}
