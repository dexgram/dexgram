package chat.simplex.common.views.onboarding

import SectionBottomSpacer
import SectionTextFooter
import SectionView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.helpers.ShredgramInlineSpinner
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

// Shredgram Typography tokens
private const val lineHeightHeadlineS = 1.12f
private const val lineHeightBody = 1.5f

// Font sizes
private val font12 = 12.sp
private val font14 = 14.sp
private val font30 = 30.sp

// Shredgram Colors
private val ElectricBlue500 = Color(0xFF1F4CFF)
private val OnSurfaceVariant = Color(0xFF3D4042)
private val Green500 = Color(0xFF11994A)
private val WarningOrange = Color(0xFFFF9500)

@Composable
fun YubiKeyFactoryResetScreen(m: ChatModel) {
    // Back to YubiKey setup screen
    BackHandler {
        // Clear waiting state if user goes back
        m.yubiKeyDetected.value = false
        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
    }
    
    val scope = rememberCoroutineScope()
    
    // Reset state
    val waitingForTap = remember { mutableStateOf(false) }
    val isResetting = remember { mutableStateOf(false) }
    val resetComplete = remember { mutableStateOf(false) }
    val currentStep = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val detailedSteps = remember { mutableStateListOf<String>() }
    
    // Watch for YubiKey tap using snapshotFlow to avoid LaunchedEffect restart delays
    // This reacts IMMEDIATELY when YubiKey is detected, preventing NFC connection timeout
    LaunchedEffect(Unit) {
        // Clear any stale detection on screen load
        m.yubiKeyDetected.value = false
        
        // Use snapshotFlow to observe state changes without restarting the coroutine
        snapshotFlow { waitingForTap.value to m.yubiKeyDetected.value }
            .filter { pair: Pair<Boolean, Boolean> -> pair.first && pair.second }
            .collectLatest { _ ->
                // Fresh tap detected, proceed with reset IMMEDIATELY
                waitingForTap.value = false
                isResetting.value = true
                currentStep.value = generalGetString(MR.strings.yubikey_factory_reset_resetting)
                m.yubiKeyDetected.value = false  // Clear immediately to prevent re-entry
                
                try {
                    val result = chat.simplex.common.platform.YubiKeyBridge.resetToFactoryDefaults()
                    
                    if (result.isSuccess) {
                        resetComplete.value = true
                        isResetting.value = false
                    } else {
                        val error = result.exceptionOrNull()?.message ?: generalGetString(MR.strings.yubikey_factory_reset_unknown_error)
                        // Provide clearer error messages for common issues
                        errorMessage.value = when {
                            error.contains("connection", ignoreCase = true) || 
                            error.contains("lost", ignoreCase = true) ||
                            error.contains("tap", ignoreCase = true) ||
                            error.contains("stale", ignoreCase = true) ->
                                generalGetString(MR.strings.yubikey_factory_reset_removed_too_soon)
                            error.contains("timeout", ignoreCase = true) ->
                                generalGetString(MR.strings.yubikey_factory_reset_timed_out)
                            error.contains("blocked", ignoreCase = true) ->
                                generalGetString(MR.strings.yubikey_factory_reset_blocked)
                            else -> error
                        }
                        isResetting.value = false
                    }
                } catch (e: Exception) {
                    val error = e.message ?: generalGetString(MR.strings.yubikey_factory_reset_unknown_error)
                    errorMessage.value = when {
                        error.contains("connection", ignoreCase = true) || 
                        error.contains("lost", ignoreCase = true) ->
                            generalGetString(MR.strings.yubikey_factory_reset_hold_firmly)
                        else -> error
                    }
                    isResetting.value = false
                }
            }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),  // Shredgram: space24DP
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TopBar - Shredgram style with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                    }
                ) {
                    Icon(
                        painter = painterResource(MR.images.ic_arrow_back_ios_new),
                        contentDescription = stringResource(MR.strings.back),
                        tint = MaterialTheme.colors.onBackground
                    )
                }
            }
            
            // Warning icon - Shredgram: space24DP
            Icon(
                painter = painterResource(MR.images.ic_warning),
                contentDescription = stringResource(MR.strings.yubikey_factory_reset_cd_warning),
                modifier = Modifier.size(24.dp),
                tint = WarningOrange
            )
            
            Spacer(Modifier.height(16.dp))  // Shredgram: space16DP
            
            // Title - Shredgram: headlineSmall (Manrope Bold 30sp, lineHeight 1.12)
            Text(
                text = stringResource(MR.strings.yubikey_factory_reset_title),
                fontFamily = Manrope,
                fontSize = font30,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (font30.value * lineHeightHeadlineS).sp
            )
            
            Spacer(Modifier.height(8.dp))  // Shredgram: space8DP
            
            // Description - Shredgram: bodyMedium (DMSans Normal 14sp, lineHeight 1.5)
            Text(
                text = stringResource(MR.strings.yubikey_factory_reset_description),
                fontFamily = DMSans,
                fontSize = font14,
                fontWeight = FontWeight.Normal,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = (font14.value * lineHeightBody).sp
            )
            
            Spacer(Modifier.height(32.dp))  // Shredgram: space32DP
        
        Spacer(Modifier.height(24.dp))
        
        if (!resetComplete.value) {
            // Warning card - Shredgram style
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colors.error.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_warning_header),
                        fontFamily = Manrope,
                        fontSize = font14,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.error
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(MR.strings.yubikey_factory_reset_pin_default), fontFamily = DMSans, fontSize = font12, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(MR.strings.yubikey_factory_reset_puk_default), fontFamily = DMSans, fontSize = font12, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(MR.strings.yubikey_factory_reset_mgmt_reset), fontFamily = DMSans, fontSize = font12, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(MR.strings.yubikey_factory_reset_certs_deleted), fontFamily = DMSans, fontSize = font12, color = MaterialTheme.colors.error)
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Waiting for tap display
            if (waitingForTap.value) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Key icon
                    Image(
                        painter = painterResource(MR.images.ic_passkey),
                        contentDescription = stringResource(MR.strings.yubikey_cd_yubikey),
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_hold_key),
                        fontFamily = DMSans,
                        fontSize = font14,
                        fontWeight = FontWeight.Medium,
                        color = ElectricBlue500,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_place_instructions),
                        fontFamily = DMSans,
                        fontSize = font12,
                        fontWeight = FontWeight.Normal,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_dont_lift),
                        fontFamily = DMSans,
                        fontSize = font12,
                        fontWeight = FontWeight.Bold,
                        color = WarningOrange,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Current step display (during reset)
            if (isResetting.value && !waitingForTap.value) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShredgramInlineSpinner(size = 48.dp)
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        currentStep.value,
                        fontFamily = DMSans,
                        fontSize = font14,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_keep_in_place),
                        fontFamily = DMSans,
                        fontSize = font12,
                        fontWeight = FontWeight.Normal,
                        color = WarningOrange,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Error display
            errorMessage.value?.let { error ->
                Spacer(Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colors.error.copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(MR.strings.yubikey_factory_reset_unable),
                            fontFamily = Manrope,
                            fontSize = font14,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.error
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            error,
                            fontFamily = DMSans,
                            fontSize = font12,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Try Again button
                        Button(
                            onClick = {
                                // Clear error and wait for fresh tap
                                errorMessage.value = null
                                m.yubiKeyDetected.value = false
                                waitingForTap.value = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(360.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = ElectricBlue500,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Text(
                                stringResource(MR.strings.yubikey_factory_reset_btn_try_again),
                                fontFamily = DMSans,
                                fontSize = font14,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Cancel button
                        TextButton(
                            onClick = {
                                errorMessage.value = null
                                m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                stringResource(MR.strings.yubikey_factory_reset_btn_cancel),
                                fontFamily = DMSans,
                                fontSize = font12,
                                fontWeight = FontWeight.Normal,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            // Bottom buttons
            if (!isResetting.value && !waitingForTap.value && errorMessage.value == null) {
                Spacer(Modifier.height(32.dp))
                
                // Reset button - Shredgram PrimaryButton style (error color)
                Button(
                    onClick = {
                        // Clear any stale detection and wait for fresh tap
                        m.yubiKeyDetected.value = false
                        errorMessage.value = null
                        detailedSteps.clear()
                        waitingForTap.value = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(360.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_btn_reset),
                        fontFamily = DMSans,
                        fontSize = font14,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(
                    onClick = {
                        m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_btn_cancel),
                        fontFamily = DMSans,
                        fontSize = font12,
                        fontWeight = FontWeight.Normal,
                        color = OnSurfaceVariant
                    )
                }
            }
            
            // Cancel button when waiting for tap
            if (waitingForTap.value) {
                Spacer(Modifier.height(32.dp))
                
                TextButton(
                    onClick = {
                        waitingForTap.value = false
                        m.yubiKeyDetected.value = false
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        stringResource(MR.strings.yubikey_factory_reset_btn_cancel),
                        fontFamily = DMSans,
                        fontSize = font12,
                        fontWeight = FontWeight.Normal,
                        color = OnSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
        } else {
            // Success screen - Shredgram style
            Icon(
                painter = painterResource(MR.images.ic_check_circle_filled),
                contentDescription = stringResource(MR.strings.yubikey_cd_success),
                modifier = Modifier.size(92.dp),
                tint = Green500
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                stringResource(MR.strings.yubikey_factory_reset_success_title),
                fontFamily = Manrope,
                fontSize = font30,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = (font30.value * lineHeightHeadlineS).sp
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                stringResource(MR.strings.yubikey_factory_reset_success_description),
                fontFamily = DMSans,
                fontSize = font14,
                fontWeight = FontWeight.Normal,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = (font14.value * lineHeightBody).sp
            )
            
            Spacer(Modifier.weight(1f))
            
            // Start fresh button
            Button(
                onClick = {
                    // SECURITY: Clear encrypted storage
                    DatabaseUtils.ksYubiKeyChallenge.remove()
                    DatabaseUtils.ksYubiKeyManagementKey.remove()
                    
                    m.controller.appPrefs.yubiKeyPinSet.set(false)
                    m.controller.appPrefs.yubiKeyPukSet.set(false)
                    m.controller.appPrefs.yubiKeyManagementKeySet.set(false)
                    // Clear legacy plaintext preferences
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyManagementKey.set(null)
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyPin.set(null)
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyPuk.set(null)
                    @Suppress("DEPRECATION")
                    m.controller.appPrefs.yubiKeyChallenge.set(null)
                    m.controller.appPrefs.yubiKeyUid.set(null)
                    m.controller.appPrefs.useYubiKeyForDB.set(false)
                    // SECURITY: Clear PIN from secure container
                    m.secureYubiKeyPin.clear()
                    
                    m.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_4_5_SetupYubiKey)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ElectricBlue500,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Text(
                    stringResource(MR.strings.yubikey_factory_reset_btn_continue),
                    fontFamily = DMSans,
                    fontSize = font14,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ShredgramTermsTextReset() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
        text = stringResource(MR.strings.yubikey_terms_prefix),
        fontFamily = DMSans,
        fontSize = font12,
        fontWeight = FontWeight.Normal,
        color = OnSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = (font12.value * lineHeightBody).sp
    )
    
    Row {
        Text(
            text = stringResource(MR.strings.yubikey_terms_of_service),
            fontFamily = DMSans,
            fontSize = font12,
            fontWeight = FontWeight.Normal,
            color = ElectricBlue500,
            modifier = Modifier.clickable { }
        )
        Text(
            text = stringResource(MR.strings.yubikey_terms_and),
            fontFamily = DMSans,
            fontSize = font12,
            fontWeight = FontWeight.Normal,
            color = OnSurfaceVariant
        )
        Text(
            text = stringResource(MR.strings.yubikey_privacy_policy),
            fontFamily = DMSans,
            fontSize = font12,
            fontWeight = FontWeight.Normal,
            color = ElectricBlue500,
            modifier = Modifier.clickable { }
        )
    }
    }
}

