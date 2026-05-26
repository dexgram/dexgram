package chat.simplex.common.views.chatlist

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "UsernameAnimation"
private const val ANIMATION_DURATION = 30 * 60 * 1000L // 30 minutes in milliseconds
private const val BREAK_DURATION = 3000L // 3 seconds for breaking animation
private const val RISE_DISTANCE = 100f // How far up the letters rise

/**
 * State for the username breaking animation
 */
enum class AnimationState {
    NORMAL,      // Username displays normally
    DEGRADING,   // Letters gradually breaking pieces
    RISING,      // Username starts rising and fading
    BREAKING,    // Letters break apart and fall
    REGENERATING // New username being generated
}

/**
 * Represents a single letter particle with physics
 */
data class LetterParticle(
    val char: String,
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var rotation: Float,
    var rotationVelocity: Float,
    var alpha: Float,
    val textStyle: TextStyle,
    val isChunk: Boolean = false  // True if it's a piece breaking off
)

/**
 * Represents the state of each letter in the username
 */
data class LetterState(
    val char: Char,
    val index: Int,
    var damageLevel: Float = 0f,  // 0.0 = perfect, 1.0 = destroyed
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var rotation: Float = 0f
)

/**
 * Controller for username breaking animation
 */
class UsernameAnimationController {
    var animationState = mutableStateOf(AnimationState.NORMAL)
    var timeRemaining = mutableStateOf(ANIMATION_DURATION)
    var particles = mutableStateOf<List<LetterParticle>>(emptyList())
    var letterStates = mutableStateOf<List<LetterState>>(emptyList())
    var baselineY = mutableStateOf(0f)
    var lastBreakTime = mutableStateOf(0L)
    var breakInterval = mutableStateOf(5000L) // 5 seconds between breaks
    
    /**
     * Trigger the breaking animation manually
     */
    fun triggerBreak() {
        animationState.value = AnimationState.RISING
        timeRemaining.value = 0L
    }
    
    /**
     * Start degradation mode (continuous breaking)
     */
    fun startDegradation() {
        animationState.value = AnimationState.DEGRADING
    }
    
    /**
     * Reset animation and start new cycle
     */
    fun reset() {
        animationState.value = AnimationState.NORMAL
        timeRemaining.value = ANIMATION_DURATION
        particles.value = emptyList()
        letterStates.value = emptyList()
        lastBreakTime.value = 0L
    }
    
    /**
     * Skip to regeneration (used after animation completes)
     */
    fun startRegeneration() {
        animationState.value = AnimationState.REGENERATING
    }
}

/**
 * Calculate dynamic font size based on username length
 * Short names (< 12 chars): 18sp
 * Medium names (12-18 chars): 15sp
 * Long names (> 18 chars): 12sp
 */
private fun calculateFontSize(username: String): Int {
    return when {
        username.length <= 12 -> 18
        username.length <= 18 -> 15
        username.length <= 24 -> 13
        else -> 11
    }
}

/**
 * Animated username display with breaking effect
 * 
 * @param username Current username to display
 * @param controller Animation controller for external control
 * @param onRegenerateUsername Callback when animation completes and new username should be generated
 * @param fontSize Optional font size override (if null, calculated dynamically)
 * @param modifier Modifier for the composable
 */
@Composable
fun UsernameBreakingAnimation(
    username: String,
    controller: UsernameAnimationController = remember { UsernameAnimationController() },
    onRegenerateUsername: () -> Unit,
    fontSize: Int? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val dynamicFontSize = fontSize ?: calculateFontSize(username)
    val textStyle = TextStyle(
        color = MaterialTheme.colors.onBackground,
        fontWeight = FontWeight.SemiBold,
        fontSize = dynamicFontSize.sp
    )
    
    // Log initial state
    LaunchedEffect(Unit) {
    }
    
    // Timer countdown - continues during degradation
    LaunchedEffect(username) {
        controller.reset()
        
        while (isActive && (controller.animationState.value == AnimationState.NORMAL || 
                           controller.animationState.value == AnimationState.DEGRADING)) {
            delay(1000L) // Update every second
            controller.timeRemaining.value = (controller.timeRemaining.value - 1000L).coerceAtLeast(0L)
            
            val secondsLeft = controller.timeRemaining.value / 1000L
            if (secondsLeft <= 10 || secondsLeft % 10 == 0L) {
            }
            
            if (controller.timeRemaining.value <= 0L) {
                controller.animationState.value = AnimationState.REGENERATING
            }
        }
    }
    
    Box(modifier = modifier) {
        when (controller.animationState.value) {
            AnimationState.NORMAL -> {
                // Normal username display - start degradation after 3 seconds
                NormalUsername(username, textStyle, controller)
                
                LaunchedEffect(Unit) {
                    delay(3000L) // Wait 3 seconds before starting
                    controller.startDegradation()
                    initializeLetterStates(username, controller)
                }
            }
            AnimationState.DEGRADING -> {
                // Username with pieces breaking off periodically
                DegradingUsername(username, textStyle, textMeasurer, controller, onRegenerateUsername)
            }
            AnimationState.RISING -> {
                // Rising and starting to break
                RisingUsername(username, textStyle, textMeasurer, controller)
            }
            AnimationState.BREAKING -> {
                // Falling letter particles
                FallingLetters(controller, textMeasurer)
            }
            AnimationState.REGENERATING -> {
                // Trigger regeneration and reset
                LaunchedEffect(Unit) {
                    onRegenerateUsername()
                    delay(500L) // Wait for new username to be set
                    controller.reset()
                }
                Text(
                    "Generating...",
                    style = textStyle.copy(color = MaterialTheme.colors.secondary)
                )
            }
        }
    }
}

/**
 * Normal username display with countdown
 */
@Composable
private fun NormalUsername(
    username: String,
    textStyle: TextStyle,
    controller: UsernameAnimationController
) {
    // Just show username, no extra text
    Text(username, style = textStyle)
}

/**
 * Initialize letter states for degradation animation
 */
private fun initializeLetterStates(username: String, controller: UsernameAnimationController) {
    val states = username.mapIndexed { index, char ->
        LetterState(
            char = char,
            index = index,
            damageLevel = 0f
        )
    }
    controller.letterStates.value = states
    controller.lastBreakTime.value = System.currentTimeMillis()
}

/**
 * Username with gradual degradation - pieces break off every 5-10 seconds
 */
@Composable
private fun DegradingUsername(
    username: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    controller: UsernameAnimationController,
    onRegenerateUsername: () -> Unit
) {
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    val letterStates = controller.letterStates
    val fallingChunks = controller.particles
    
    // Update time and check if we should break a piece
    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime.value = System.currentTimeMillis()
            val timeSinceLastBreak = currentTime.value - controller.lastBreakTime.value
            
            // Break a random piece every 5-10 seconds
            if (timeSinceLastBreak >= controller.breakInterval.value) {
                breakRandomLetterPiece(username, textStyle, textMeasurer, controller)
                controller.lastBreakTime.value = currentTime.value
                // Random interval between 5-10 seconds
                controller.breakInterval.value = (5000L + Random.nextLong(5000L))
            }
            
            // Degradation continues until timer expires
            // Timer handled by main countdown in UsernameBreakingAnimation
            
            delay(100L) // Check every 100ms
        }
    }
    
    // Update falling chunks physics
    LaunchedEffect(Unit) {
        while (isActive) {
            if (fallingChunks.value.isNotEmpty()) {
                val gravity = 300f
                val drag = 0.98f
                
                fallingChunks.value = fallingChunks.value.mapNotNull { particle ->
                    val newVelocityY = (particle.velocityY + gravity * 0.016f) * drag
                    val newVelocityX = particle.velocityX * drag
                    val newY = particle.y + newVelocityY * 0.016f
                    val newX = particle.x + newVelocityX * 0.016f
                    val newRotation = particle.rotation + particle.rotationVelocity * 5f
                    val newAlpha = (particle.alpha - 0.01f).coerceAtLeast(0f)
                    
                    // Remove if off screen or faded
                    if (newY > 250f || newAlpha <= 0f) {
                        null
                    } else {
                        particle.copy(
                            x = newX,
                            y = newY,
                            velocityX = newVelocityX,
                            velocityY = newVelocityY,
                            rotation = newRotation,
                            alpha = newAlpha
                        )
                    }
                }
            }
            delay(16L) // ~60 FPS
        }
    }
    
    // Render username with damage + falling chunks
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw falling chunks
            fallingChunks.value.forEach { particle ->
                drawParticle(particle, textMeasurer)
            }
            
            // Username Y position with 16dp top margin
            val usernameY = 48f // 16dp * 3 (density conversion)
            
            // Draw username letters with damage effects
            var xOffset = 0f
            letterStates.value.forEach { letterState ->
                val charText = letterState.char.toString()
                val charLayout = textMeasurer.measure(charText, textStyle)
                val charWidth = charLayout.size.width.toFloat()
                
                // Apply damage effects
                val damageAlpha = (1f - letterState.damageLevel).coerceAtLeast(0.2f)
                val shakeX = if (letterState.damageLevel > 0.3f) {
                    sin(currentTime.value / 100f + letterState.index) * letterState.damageLevel * 3f
                } else 0f
                val shakeY = if (letterState.damageLevel > 0.5f) {
                    cos(currentTime.value / 100f + letterState.index) * letterState.damageLevel * 2f
                } else 0f
                
                val damagedStyle = textStyle.copy(
                    color = textStyle.color.copy(alpha = damageAlpha)
                )
                val damagedLayout = textMeasurer.measure(charText, damagedStyle)
                
                rotate(
                    degrees = letterState.rotation,
                    pivot = Offset(xOffset + charWidth / 2f + shakeX, usernameY + shakeY)
                ) {
                    drawText(
                        textLayoutResult = damagedLayout,
                        topLeft = Offset(xOffset + shakeX, usernameY + shakeY)
                    )
                }
                
                xOffset += charWidth + 2f
            }
            
            // Timer Y position - 2dp gap below username (username height ~18sp + 2dp gap)
            val timerY = usernameY + 60f // Username + gap
            
            // Show timer below username - "New Name in 30:00"
            val timeRemainingMs = controller.timeRemaining.value
            val minutes = (timeRemainingMs / 60000L).toInt()
            val seconds = ((timeRemainingMs % 60000L) / 1000L).toInt()
            val timeString = String.format("%02d:%02d", minutes, seconds)
            
            // "New Name in" text in gray
            val labelText = "New Name in "
            val labelLayout = textMeasurer.measure(
                labelText,
                TextStyle(color = Color.Gray, fontSize = 14.sp)
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(0f, timerY)
            )
            
            // Timer in red
            val timerLayout = textMeasurer.measure(
                timeString,
                TextStyle(color = Color.Red, fontSize = 14.sp)
            )
            drawText(
                textLayoutResult = timerLayout,
                topLeft = Offset(labelLayout.size.width.toFloat(), timerY)
            )
        }
    }
}

/**
 * Break a random piece off a letter
 */
private fun breakRandomLetterPiece(
    username: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    controller: UsernameAnimationController
) {
    if (controller.letterStates.value.isEmpty()) return
    
    // Pick a random letter that isn't too damaged yet
    val availableLetters = controller.letterStates.value.filter { it.damageLevel < 0.9f }
    if (availableLetters.isEmpty()) return
    
    val targetLetter = availableLetters.random()
    val letterIndex = controller.letterStates.value.indexOf(targetLetter)
    
    // Damage the letter
    val newStates = controller.letterStates.value.toMutableList()
    newStates[letterIndex] = targetLetter.copy(
        damageLevel = (targetLetter.damageLevel + 0.15f).coerceAtMost(1f),
        rotation = targetLetter.rotation + (Random.nextFloat() * 10f - 5f)
    )
    controller.letterStates.value = newStates
    
    // Create falling chunk particle
    var xOffset = 0f
    for (i in 0 until letterIndex) {
        val charLayout = textMeasurer.measure(username[i].toString(), textStyle)
        xOffset += charLayout.size.width.toFloat() + 2f
    }
    
    val chunkParticle = LetterParticle(
        char = "▪", // Small square piece
        x = xOffset + Random.nextFloat() * 10f,
        y = 20f,
        velocityX = (Random.nextFloat() * 4f - 2f) * 5f,
        velocityY = Random.nextFloat() * 20f + 10f,
        rotation = Random.nextFloat() * 360f,
        rotationVelocity = Random.nextFloat() * 4f - 2f,
        alpha = 1f,
        textStyle = textStyle.copy(fontSize = 8.sp),
        isChunk = true
    )
    
    controller.particles.value = controller.particles.value + chunkParticle
    
}

/**
 * Username rising and fading before breaking
 */
@Composable
private fun RisingUsername(
    username: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    controller: UsernameAnimationController
) {
    val riseProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Rise up over 1 second
        riseProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = EaseInOut)
        )
        
        // After rising, break into letters
        createLetterParticles(username, textStyle, textMeasurer, controller)
        controller.animationState.value = AnimationState.BREAKING
    }
    
    // Calculate rise offset and fade
    val offsetY = -RISE_DISTANCE * riseProgress.value
    val alpha = 1f - (riseProgress.value * 0.5f) // Fade to 50% opacity
    
    Box(modifier = Modifier.offset(y = offsetY.dp)) {
        Text(
            username,
            style = textStyle.copy(color = textStyle.color.copy(alpha = alpha))
        )
    }
}

/**
 * Falling letter particles with physics
 */
@Composable
private fun FallingLetters(
    controller: UsernameAnimationController,
    textMeasurer: TextMeasurer
) {
    val animationTime = remember { mutableStateOf(0f) }
    val particles = controller.particles
    
    // Physics simulation - runs every frame
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        
        while (isActive) {
            val currentTime = System.currentTimeMillis()
            val elapsed = (currentTime - startTime) / 1000f // Time in seconds
            animationTime.value = elapsed
            
            // Update each particle with physics
            particles.value = particles.value.map { particle ->
                val gravity = 500f // Pixels per second squared
                val drag = 0.98f // Air resistance
                
                // Update velocities
                val newVelocityY = (particle.velocityY + gravity * 0.016f) * drag
                val newVelocityX = particle.velocityX * drag
                
                // Update positions
                val newY = particle.y + newVelocityY * 0.016f
                val newX = particle.x + newVelocityX * 0.016f
                
                // Update rotation
                val newRotation = particle.rotation + particle.rotationVelocity * 5f
                
                // Fade out as it falls
                val newAlpha = (1f - (elapsed / 3f)).coerceAtLeast(0f)
                
                particle.copy(
                    x = newX,
                    y = newY,
                    velocityX = newVelocityX,
                    velocityY = newVelocityY,
                    rotation = newRotation,
                    alpha = newAlpha
                )
            }
            
            // Check if animation is complete (all particles faded or off screen)
            if (elapsed > 3f || particles.value.all { it.alpha <= 0f }) {
                controller.startRegeneration()
                break
            }
            
            delay(16L) // ~60 FPS
        }
    }
    
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        // Draw grey strip at bottom
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset(0f, size.height - 40f),
            size = androidx.compose.ui.geometry.Size(size.width, 40f)
        )
        
        // Draw particles
        particles.value.forEach { particle ->
            drawParticle(particle, textMeasurer)
        }
    }
}

/**
 * Draw a single letter particle
 */
private fun DrawScope.drawParticle(
    particle: LetterParticle,
    textMeasurer: TextMeasurer
) {
    val textLayoutResult = textMeasurer.measure(
        text = particle.char,
        style = particle.textStyle.copy(
            color = particle.textStyle.color.copy(alpha = particle.alpha)
        )
    )
    
    rotate(degrees = particle.rotation, pivot = Offset(particle.x, particle.y)) {
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                particle.x - textLayoutResult.size.width / 2f,
                particle.y
            )
        )
    }
}

/**
 * Create letter particles from username string
 */
private fun createLetterParticles(
    username: String,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    controller: UsernameAnimationController
) {
    val particles = mutableListOf<LetterParticle>()
    val startX = 100f // Starting X position (adjust based on layout)
    var xOffset = startX
    val startY = 50f // Starting Y position (after rising)
    
    username.forEach { char ->
        val charLayout = textMeasurer.measure(char.toString(), textStyle)
        val charWidth = charLayout.size.width.toFloat()
        
        // Create particle with random physics properties
        particles.add(
            LetterParticle(
                char = char.toString(),
                x = xOffset + charWidth / 2f,
                y = startY,
                velocityX = (Random.nextFloat() * 4f - 2f) * 10f, // Random horizontal velocity
                velocityY = Random.nextFloat() * 50f + 50f, // Initial downward velocity
                rotation = Random.nextFloat() * 20f - 10f, // Initial slight rotation
                rotationVelocity = Random.nextFloat() * 4f - 2f, // Random spin
                alpha = 1f,
                textStyle = textStyle
            )
        )
        
        xOffset += charWidth + 2f // Spacing between letters
    }
    
    controller.particles.value = particles
    controller.baselineY.value = startY
}

/**
 * Composable wrapper that includes timer display
 */
@Composable
fun UsernameWithTimer(
    username: String,
    controller: UsernameAnimationController = remember { UsernameAnimationController() },
    onRegenerateUsername: () -> Unit,
    showTimer: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        UsernameBreakingAnimation(
            username = username,
            controller = controller,
            onRegenerateUsername = onRegenerateUsername
        )
        
        // Optional timer display
        if (showTimer && controller.animationState.value == AnimationState.NORMAL) {
            val minutes = (controller.timeRemaining.value / 60000L).toInt()
            val seconds = ((controller.timeRemaining.value % 60000L) / 1000L).toInt()
            
            Text(
                text = "New name in ${minutes}:${seconds.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.caption.copy(
                    color = MaterialTheme.colors.secondary,
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * Simplified version for testing (shorter timer)
 * Pieces break off every 5-10 seconds, full regeneration after timer expires
 * Font size is automatically calculated based on username length
 */
@Composable
fun UsernameBreakingAnimationTest(
    username: String,
    onRegenerateUsername: () -> Unit,
    testDurationSeconds: Int = 30 * 60, // 30 minutes default (1800 seconds)
    fontSize: Int? = null, // Optional font size override
    modifier: Modifier = Modifier
) {
    val controller = remember { UsernameAnimationController().apply {
        timeRemaining.value = testDurationSeconds * 1000L
        breakInterval.value = 5000L // Break every 5 seconds initially
    }}
    
    UsernameBreakingAnimation(
        username = username,
        controller = controller,
        onRegenerateUsername = onRegenerateUsername,
        fontSize = fontSize,
        modifier = modifier
    )
}

