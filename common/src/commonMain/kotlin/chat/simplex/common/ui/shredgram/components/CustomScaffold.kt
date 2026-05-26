package chat.simplex.common.ui.shredgram.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import chat.simplex.common.ui.shredgram.theme.*

/**
 * Custom scaffold layout for Shredgram screens
 * 
 * Provides a scrollable content area with a fixed bottom content area.
 * Handles safe area insets and consistent padding.
 * 
 * @param bottomContent Content to display at the bottom (buttons, terms, etc.)
 * @param modifier Modifier for the scaffold
 * @param currentStep Current onboarding step (0-based, null to hide progress bar)
 * @param totalSteps Total onboarding steps (null to hide progress bar)
 * @param content Main scrollable content
 */
@Composable
fun CustomScaffold(
    bottomContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    currentStep: Int? = null,
    totalSteps: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = ShredgramTheme.colors
    val showProgress = currentStep != null && totalSteps != null && totalSteps > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Progress bar at the top (if enabled)
        if (showProgress) {
            SegmentedProgressBar(
                currentStep = currentStep!!,
                totalSteps = totalSteps!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.horizontalPadding)
                    .padding(top = Dimensions.space8DP)
            )
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = Dimensions.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }

        // Fixed bottom content area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Dimensions.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            bottomContent()
        }
    }
}

/**
 * Simplified scaffold without bottom content
 */
@Composable
fun SimpleScaffold(
    modifier: Modifier = Modifier,
    horizontalPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = ShredgramTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .then(
                if (horizontalPadding) Modifier.padding(horizontal = Dimensions.horizontalPadding)
                else Modifier
            )
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

/**
 * Scaffold without scrolling - for screens that manage their own scroll behavior
 */
@Composable
fun FixedScaffold(
    bottomContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    currentStep: Int? = null,
    totalSteps: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ShredgramTheme.colors
    val showProgress = currentStep != null && totalSteps != null && totalSteps > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Progress bar at the top (if enabled)
        if (showProgress) {
            SegmentedProgressBar(
                currentStep = currentStep!!,
                totalSteps = totalSteps!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.horizontalPadding)
                    .padding(top = Dimensions.space8DP)
            )
        }

        // Main content area
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimensions.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }

        // Fixed bottom content area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Dimensions.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            bottomContent()
        }
    }
}

