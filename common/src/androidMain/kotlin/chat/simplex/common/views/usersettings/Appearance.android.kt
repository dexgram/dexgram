package chat.simplex.common.views.usersettings

import SectionBottomSpacer
import SectionDividerSpaced
import SectionSpacer
import SectionView
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import chat.simplex.common.helpers.APPLICATION_ID
import chat.simplex.common.helpers.saveAppLocale
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.delay
import java.util.Locale

enum class AppIcon(val image: ImageResource) {
  DEFAULT(MR.images.ic_simplex_light),
  DARK_BLUE(MR.images.ic_simplex_dark),
}

@Composable
actual fun AppearanceView(m: ChatModel) {
  val appIcon = remember { mutableStateOf(findEnabledIcon()) }
  fun setAppIcon(newIcon: AppIcon) {
    if (appIcon.value == newIcon) return
    val newComponent = ComponentName(APPLICATION_ID, "chat.simplex.app.MainActivity_${newIcon.name.lowercase()}")
    val oldComponent = ComponentName(APPLICATION_ID, "chat.simplex.app.MainActivity_${appIcon.value.name.lowercase()}")
    androidAppContext.packageManager.setComponentEnabledSetting(
      newComponent,
      COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
    )

    androidAppContext.packageManager.setComponentEnabledSetting(
      oldComponent,
      PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
    )

    appIcon.value = newIcon
  }
  AppearanceScope.AppearanceLayout(
    appIcon,
    m.controller.appPrefs.appLanguage,
    m.controller.appPrefs.systemDarkTheme,
    changeIcon = ::setAppIcon,
  )
}

@Composable
fun AppearanceScope.AppearanceLayout(
  icon: MutableState<AppIcon>,
  languagePref: SharedPreference<String?>,
  systemDarkTheme: SharedPreference<String?>,
  changeIcon: (AppIcon) -> Unit,
) {
  BackHandler(onBack = { ModalManager.start.closeModal() })
  val context = LocalContext.current
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
            val activity = context as? Activity
            if (activity != null) {
              if (selected == "system") {
                activity.saveAppLocale(languagePref)
              } else {
                activity.saveAppLocale(languagePref, selected)
              }
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
          .clip(CircleShape)
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

private fun findEnabledIcon(): AppIcon = AppIcon.values().firstOrNull { icon ->
  androidAppContext.packageManager.getComponentEnabledSetting(
    ComponentName(APPLICATION_ID, "chat.simplex.app.MainActivity_${icon.name.lowercase()}")
  ).let { it == COMPONENT_ENABLED_STATE_DEFAULT || it == COMPONENT_ENABLED_STATE_ENABLED }
} ?: AppIcon.DEFAULT

@Preview
@Composable
fun PreviewAppearanceSettings() {
  SimpleXTheme {
    AppearanceScope.AppearanceLayout(
      icon = remember { mutableStateOf(AppIcon.DARK_BLUE) },
      languagePref = SharedPreference({ null }, {}),
      systemDarkTheme = SharedPreference({ null }, {}),
      changeIcon = {},
    )
  }
}
