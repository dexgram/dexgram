package chat.simplex.common.views.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.Log

/**
 * Demo screen for testing the username breaking animation
 * This is a standalone demo that can be used for testing without affecting the main app
 */
@Composable
fun UsernameAnimationDemoScreen() {
    val controller = remember { UsernameAnimationController() }
    var username by remember { mutableStateOf("DemoUser123") }
    var regenerationCount by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Username Animation Demo") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Animation Status",
                        style = MaterialTheme.typography.h6
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("State:")
                        Text(
                            controller.animationState.value.name,
                            color = when (controller.animationState.value) {
                                AnimationState.NORMAL -> Color.Green
                                AnimationState.DEGRADING -> Color(0xFFFFA500) // Orange
                                AnimationState.RISING -> Color.Yellow
                                AnimationState.BREAKING -> Color.Red
                                AnimationState.REGENERATING -> Color.Blue
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    )
                    {
                        Text("Time Remaining:")
                        Text("${(controller.timeRemaining.value / 1000L)}s")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    )
                    {
                        Text("Particles:")
                        Text("${controller.particles.value.size}")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    )
                    {
                        Text("Regenerations:")
                        Text("$regenerationCount")
                    }
                }
            }
            
            // Animation Display Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                elevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    UsernameBreakingAnimationTest(
                        username = username,
                        onRegenerateUsername = {
                            regenerationCount++
                            // Simulate username change
                            username = "User${(1000..9999).random()}"
                        },
                        testDurationSeconds = 10, // 10 seconds for demo
                        modifier = Modifier
                    )
                }
            }
            
            // Control Buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Controls",
                        style = MaterialTheme.typography.h6
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { controller.triggerBreak() },
                            modifier = Modifier.weight(1f),
                            enabled = controller.animationState.value == AnimationState.NORMAL
                        ) {
                            Text("Break Now")
                        }
                        
                        Button(
                            onClick = { controller.reset() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                    }
                    
                    Button(
                        onClick = {
                            username = "NewUser${(100..999).random()}"
                            controller.reset()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Username")
                    }
                }
            }
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "How It Works",
                        style = MaterialTheme.typography.h6
                    )
                    
                    Text(
                        "1. Username displays normally for 10 seconds (demo mode)",
                        style = MaterialTheme.typography.body2
                    )
                    
                    Text(
                        "2. Username rises and fades (1 second)",
                        style = MaterialTheme.typography.body2
                    )
                    
                    Text(
                        "3. Letters break apart and fall with physics (3 seconds)",
                        style = MaterialTheme.typography.body2
                    )
                    
                    Text(
                        "4. New username generates and cycle restarts",
                        style = MaterialTheme.typography.body2
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        "Production mode: 30 minutes instead of 10 seconds",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
            }
        }
    }
}

/**
 * Minimal demo for quick testing
 */
@Composable
fun UsernameAnimationQuickDemo() {
    var username by remember { mutableStateOf("TestUser") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UsernameBreakingAnimationTest(
                username = username,
                onRegenerateUsername = {
                    username = "User${(1000..9999).random()}"
                },
                testDurationSeconds = 5 // 5 seconds for quick demo
            )
            
            Text(
                "Animation will trigger in 5 seconds",
                style = MaterialTheme.typography.caption
            )
        }
    }
}

