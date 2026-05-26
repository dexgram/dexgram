package chat.simplex.common.views.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.*
import chat.simplex.common.ui.theme.Manrope
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.launch

// Shredgram Typography tokens - EXACT match from TypographyTokens.kt
private const val lineHeightHeadlineM = 1.18f
private const val lineHeightBody = 1.5f

// Font sizes from TypographyTokens.kt
private val font12 = 12.sp
private val font14 = 14.sp
private val font34 = 34.sp

// Letter spacing
private val letterSpacingTight = (-0.02).em

// Shredgram Colors
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val OnSurfaceVariant = Color(0xFF3D4042)

data class FeatureOnboardingData(
  val title: String,
  val description: String,
  val currentStep: Int,
  val totalSteps: Int = 3,
  val nextStage: OnboardingStage,
  val iconName: String = ""
)

/**
 * Shredgram OnboardingPager - EXACT match from OnboardingScreen.kt
 * 
 * Layout structure:
 * - CustomScaffold with scrollable content and fixed bottom
 * - TopBar (no back button)
 * - HorizontalPager with OnboardingPageContent
 * - Bottom: DotsIndicator + PrimaryButton + TermsAndPrivacyText
 * 
 * Typography:
 * - headlineMedium: Manrope Bold 34sp, lineHeight 1.18, letterSpacing -0.02em
 * - bodyMedium: DMSans Normal 14sp, lineHeight 1.5
 * - bodySmall: DMSans Normal 12sp, lineHeight 1.5
 * - labelLarge: DMSans Medium 14sp, lineHeight 1.5
 * 
 * Spacing:
 * - Image: weight(0.5f), ContentScale.Fit
 * - Image → Text: 32dp
 * - Text section: weight(0.5f)
 * - Title → Description: 16dp
 * - Bottom: 32dp → Dots → 32dp → Button → 16dp → Terms
 */
@Composable
fun OnboardingPager(m: ChatModel) {
  val features = listOf(
    FeatureOnboardingData(
      title = "Decentralized\nservers",
      description = "Distributed network architecture that ensures data is stored and processed across multiple nodes, preventing single points of failure and making interception or eavesdropping impossible.",
      currentStep = 1,
      nextStage = OnboardingStage.Step0_2_FeatureEncryption,
      iconName = "decentralized"
    ),
    FeatureOnboardingData(
      title = "Quantum E2E\nencryption",
      description = "End-to-end encryption that leverages quantum-resistant algorithms, providing secure communication that remains safe against future quantum computing threats.",
      currentStep = 2,
      nextStage = OnboardingStage.Step0_3_FeatureDecentralized,
      iconName = "quantum"
    ),
    FeatureOnboardingData(
      title = "Physical Passkey security",
      description = "Utilizes hardware-based authentication keys stored physically, safeguarding against phone hacking, and ensuring secure access without relying on vulnerable software.",
      currentStep = 3,
      nextStage = OnboardingStage.Step2_4_ChooseUnlockMethod,
      iconName = "yubikey"
    )
  )
  
  val pagerState = rememberPagerState(pageCount = { features.size })
  val coroutineScope = rememberCoroutineScope()
  
  // Gray indicator color (Shredgram style - 15% alpha)
  val indicatorColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
  
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
  ) {
    // Main scrollable content area
    // Note: Shredgram uses responsive scaling which effectively gives more room for text
    // Using 20dp horizontal padding to match the visual result
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 20.dp),  // Adjusted to match Shredgram's responsive layout
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // TopBar - Shredgram style (no back button)
      // Row with 32dp vertical padding like Shredgram TopBar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),  // Shredgram: space32DP
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        // Logo - natural size like Shredgram (no explicit size)
        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = "Shredgram Logo"
        )
        
        Spacer(Modifier.width(8.dp))  // Shredgram: space8DP
        
        // Text - Shredgram: titleMedium (Manrope Bold 18sp, lineHeight 1.4)
//        Text(
//          text = "Shredgram",
//          fontFamily = Manrope,
//          fontSize = 18.sp,
//          fontWeight = FontWeight.Bold,
//          color = MaterialTheme.colors.onBackground,
//          lineHeight = (18 * 1.4f).sp
//        )
  }
//
      // HorizontalPager for swipeable content
      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        userScrollEnabled = true
      ) { pageIndex ->
        val feature = features[pageIndex]
        
        // OnboardingPageContent - EXACT match from Shredgram
        // Note: horizontal padding already applied by parent Column
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Image section - weight(0.5f), centered, ContentScale.Fit
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(0.5f),
            contentAlignment = Alignment.Center
          ) {
            val iconResource = when (feature.iconName) {
              "decentralized" -> MR.images.ic_decentralized_servers
              "quantum" -> MR.images.ic_quantum_encryption
              "yubikey" -> MR.images.ic_yubikey
              else -> null
            }
            if (iconResource != null) {
              Image(
                painter = painterResource(iconResource),
                contentDescription = feature.title,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
              )
            }
          }
          
          Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
          
          // Text section - weight(0.5f)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(0.5f)
          ) {
            Column {
              // Title - Shredgram: headlineMedium (Manrope Bold 34sp, lineHeight 1.18)
              Text(
                text = feature.title,
                fontFamily = Manrope,
                fontSize = font34,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground,
                lineHeight = (font34.value * lineHeightHeadlineM).sp,
                letterSpacing = letterSpacingTight,
                modifier = Modifier.fillMaxWidth()
              )
              
              Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
              
              // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
              Text(
                text = feature.description,
                fontFamily = DMSans,
                fontSize = font14,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colors.onSurface,
                lineHeight = (font14.value * lineHeightBody).sp,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        }
      }
    }
    
    // Fixed bottom section
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 20.dp),  // Adjusted to match Shredgram's responsive layout
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // DotsIndicator - EXACT match from Shredgram
      Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
      ) {
        repeat(features.size) { index ->
          val isSelected = index == pagerState.currentPage
          // Animated width transition
          val width by animateDpAsState(
            targetValue = if (isSelected) 32.dp else 8.dp,  // Shredgram: space32DP / space8DP
            animationSpec = tween(durationMillis = 300),
            label = "dotWidth"
          )
          Box(
            modifier = Modifier
              .padding(horizontal = 4.dp)  // Shredgram: space4DP
              .width(width)
              .height(8.dp)  // Shredgram: space8DP
              .clip(RoundedCornerShape(50))  // RadiusPill / RadiusCircle
              .background(indicatorColor)
          )
        }
      }
      
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // PrimaryButton - Shredgram style
      Button(
        onClick = {
          val currentFeature = features[pagerState.currentPage]
          if (pagerState.currentPage < features.size - 1) {
            coroutineScope.launch {
              pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
          } else {
            m.controller.appPrefs.onboardingStage.set(currentFeature.nextStage)
          }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(360.dp),  // RadiusPill
        colors = ButtonDefaults.buttonColors(
          backgroundColor = ElectricBlue500,
          contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
      ) {
        // Shredgram: labelLarge (DMSans Medium 14sp, lineHeight 1.5)
        Text(
          text = "Next",
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Medium,
          lineHeight = (font14.value * lineHeightBody).sp
        )
      }
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
    }
  }
}

/**
 * Single page onboarding view - used for individual feature screens
 */
@Composable
fun FeatureOnboardingView(
  feature: FeatureOnboardingData,
  onNext: () -> Unit,
  onSkip: () -> Unit
) {
  // Gray indicator color (Shredgram style - 15% alpha)
  val indicatorColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
  
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .statusBarsPadding()
  ) {
    // Main scrollable content
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),  // Adjusted to match Shredgram's responsive layout
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // TopBar - Shredgram style (no back button)
      // Row with 32dp vertical padding like Shredgram TopBar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 32.dp),  // Shredgram: space32DP
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        // Logo - natural size like Shredgram (no explicit size)
        Image(
          painter = painterResource(MR.images.ic_logo),
          contentDescription = "Shredgram Logo"
        )
        
        Spacer(Modifier.width(8.dp))  // Shredgram: space8DP
        
        // Text - Shredgram: titleMedium (Manrope Bold 18sp, lineHeight 1.4)
        Text(
          text = "Shredgram",
          fontFamily = Manrope,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onBackground,
          lineHeight = (18 * 1.4f).sp
        )
      }
      
      // Image section - centered, ContentScale.Fit
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.5f, fill = false)
          .defaultMinSize(minHeight = 200.dp),
        contentAlignment = Alignment.Center
      ) {
        val iconResource = when (feature.iconName) {
          "decentralized" -> MR.images.ic_decentralized_servers
          "quantum" -> MR.images.ic_quantum_encryption
          "yubikey" -> MR.images.ic_yubikey
          else -> null
        }
        if (iconResource != null) {
          Image(
            painter = painterResource(iconResource),
            contentDescription = feature.title,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
          )
        }
      }
      
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // Text section
      Column {
        // Title - Shredgram: headlineMedium (Manrope Bold 34sp, lineHeight 1.18)
        Text(
          text = feature.title,
          fontFamily = Manrope,
          fontSize = font34,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onBackground,
          lineHeight = (font34.value * lineHeightHeadlineM).sp,
          letterSpacing = letterSpacingTight,
          modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
        
        // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
        Text(
          text = feature.description,
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colors.onSurface,
          lineHeight = (font14.value * lineHeightBody).sp,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
    
    // Bottom section - fixed
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 20.dp),  // Adjusted to match Shredgram's responsive layout
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // DotsIndicator - EXACT match from Shredgram
      Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
      ) {
        repeat(feature.totalSteps) { index ->
          val isSelected = index == feature.currentStep - 1
          // Animated width transition
          val width by animateDpAsState(
            targetValue = if (isSelected) 32.dp else 8.dp,  // Shredgram: space32DP / space8DP
            animationSpec = tween(durationMillis = 300),
            label = "dotWidth"
          )
          Box(
            modifier = Modifier
              .padding(horizontal = 4.dp)  // Shredgram: space4DP
              .width(width)
              .height(8.dp)  // Shredgram: space8DP
              .clip(RoundedCornerShape(50))  // RadiusPill / RadiusCircle
              .background(indicatorColor)
          )
        }
      }
      
      Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
      
      // PrimaryButton - Shredgram style
      Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(360.dp),  // RadiusPill
        colors = ButtonDefaults.buttonColors(
          backgroundColor = ElectricBlue500,
          contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
      ) {
        // Shredgram: labelLarge (DMSans Medium 14sp, lineHeight 1.5)
        Text(
          text = "Next",
          fontFamily = DMSans,
          fontSize = font14,
          fontWeight = FontWeight.Medium,
          lineHeight = (font14.value * lineHeightBody).sp
        )
      }
      
      Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
    }
  }
}

@Composable
fun FeatureNoIdentityOnboarding(m: ChatModel) {
  // First screen - no back navigation (would exit app)
  val feature = FeatureOnboardingData(
    title = "Decentralized\nservers",
    description = "Distributed network architecture that ensures data is stored and processed across multiple nodes, preventing single points of failure and making interception or eavesdropping impossible.",
    currentStep = 1,
    nextStage = OnboardingStage.Step0_2_FeatureEncryption,
    iconName = "decentralized"
  )
  
  FeatureOnboardingView(
    feature = feature,
    onNext = {
      m.controller.appPrefs.onboardingStage.set(feature.nextStage)
    },
    onSkip = {
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_5_SetupDatabasePassphrase)
    }
  )
}

@Composable
fun FeatureEncryptionOnboarding(m: ChatModel) {
  // Back to first feature screen
  BackHandler {
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step0_1_FeatureNoIdentity)
  }
  
  val feature = FeatureOnboardingData(
    title = "Quantum E2E\nencryption",
    description = "End-to-end encryption that leverages quantum-resistant algorithms, providing secure communication that remains safe against future quantum computing threats.",
    currentStep = 2,
    nextStage = OnboardingStage.Step0_3_FeatureDecentralized,
    iconName = "quantum"
  )
  
  FeatureOnboardingView(
    feature = feature,
    onNext = {
      m.controller.appPrefs.onboardingStage.set(feature.nextStage)
    },
    onSkip = {
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_5_SetupDatabasePassphrase)
    }
  )
}

@Composable
fun FeatureDecentralizedOnboarding(m: ChatModel) {
  // Back to second feature screen
  BackHandler {
    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step0_2_FeatureEncryption)
  }
  
  val feature = FeatureOnboardingData(
    title = "Physical Passkey security",
    description = "Utilizes hardware-based authentication keys stored physically, safeguarding against phone hacking, and ensuring secure access without relying on vulnerable software.",
    currentStep = 3,
    nextStage = OnboardingStage.Step2_4_ChooseUnlockMethod,
    iconName = "yubikey"
  )
  
  FeatureOnboardingView(
    feature = feature,
    onNext = {
      m.controller.appPrefs.onboardingStage.set(feature.nextStage)
    },
    onSkip = {
      m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_ChooseUnlockMethod)
    }
  )
}
