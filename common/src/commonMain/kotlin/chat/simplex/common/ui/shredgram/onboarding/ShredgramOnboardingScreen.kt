package chat.simplex.common.ui.shredgram.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.stringResource
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.ui.shredgram.components.*
import chat.simplex.common.ui.shredgram.theme.*
import kotlinx.coroutines.launch

/**
 * Main onboarding screen with swipeable pages
 * 
 * @param pages List of onboarding pages to display
 * @param onFinished Callback when onboarding is complete
 * @param logo Optional logo painter to show in top bar
 * @param appName Optional app name to show in top bar
 * @param termsText Optional terms and privacy text
 * @param onTermsClick Optional callback for terms click
 * @param onPrivacyClick Optional callback for privacy click
 */
@Composable
fun ShredgramOnboardingScreen(
    pages: List<OnboardingPageModel>,
    onFinished: () -> Unit,
    logo: Painter? = null,
    appName: String? = null,
    termsText: String? = null,
    onTermsClick: (() -> Unit)? = null,
    onPrivacyClick: (() -> Unit)? = null,
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors

    CustomScaffold(
        bottomContent = {
            Spacer(Modifier.height(Dimensions.space32DP))

            DotsIndicator(
                totalDots = pages.size,
                selectedIndex = pagerState.currentPage
            )

            Spacer(Modifier.height(Dimensions.space32DP))

            PrimaryButton(
                text = if (pagerState.currentPage == pages.lastIndex) stringResource(MR.strings.shredgram_action_get_started) else stringResource(MR.strings.shredgram_action_next),
                onClick = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        onFinished()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }
            )

            Spacer(Modifier.height(Dimensions.space16DP))

            // Terms and privacy text
            if (termsText != null) {
                Text(
                    text = termsText,
                    style = typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(Modifier.height(Dimensions.space16DP))
        }
    ) {
        // Top bar with logo
        ShredgramTopBar(
            showBack = false,
            title = appName,
            logo = logo
        )

        // Pager for onboarding pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) { pageIndex ->
            OnboardingPageContent(
                page = pages[pageIndex],
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Content for a single onboarding page
 */
@Composable
fun OnboardingPageContent(
    page: OnboardingPageModel,
    modifier: Modifier = Modifier
) {
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.space243DP),
            contentAlignment = Alignment.Center
        ) {
            if (page.image != null) {
                Image(
                    painter = page.image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.space243DP),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(Modifier.height(Dimensions.space32DP))

        // Text content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.space248DP)
        ) {
            Column {
                Text(
                    text = page.title,
                    style = typography.headlineMedium,
                    color = colors.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Dimensions.space16DP))

                Text(
                    text = page.description,
                    style = typography.bodyMedium,
                    color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Simplified onboarding screen without pager (single page)
 */
@Composable
fun SimpleOnboardingPage(
    title: String,
    description: String,
    image: Painter? = null,
    buttonText: String = generalGetString(MR.strings.shredgram_action_continue),
    onContinue: () -> Unit,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val typography = ShredgramTheme.typography
    val colors = ShredgramTheme.colors

    CustomScaffold(
        bottomContent = {
            Spacer(Modifier.height(Dimensions.space32DP))

            PrimaryButton(
                text = buttonText,
                onClick = onContinue
            )

            Spacer(Modifier.height(Dimensions.space32DP))
        }
    ) {
        ShredgramTopBar(
            showBack = showBack,
            onBack = onBack
        )

        // Image
        if (image != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.space200DP),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(Dimensions.space32DP))
        }

        // Title
        Text(
            text = title,
            style = typography.headlineMedium,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Dimensions.space16DP))

        // Description
        Text(
            text = description,
            style = typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

