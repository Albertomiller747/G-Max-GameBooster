package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ConsoleHapticManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rebuilt Xbox One-Inspired Landscape Console Startup Sequence fitted for G-Max:
 * - Deep cyber blue and neon cyan radial console backdrop (#050B18 -> #0A192F)
 * - Stage 1 (Power-On Core): Centered glowing G-Max hexagonal controller emblem scales in with
 *   shockwave pulse rings and synchronized console boot haptic pulse.
 * - Stage 2 (Brand Reveal & Light Sweep): Bold futuristic "G-MAX" console wordmark fades in below
 *   the emblem with a crisp diagonal light-sweep shimmer across the emblem and typography.
 * - Stage 3 (Xbox One-Style Gamer Toast): Iconic pill-shaped "Welcome back, [User]" notification
 *   banner slides in and expands horizontally near the bottom of the screen with the G-Max emblem.
 */
@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val gamerName = remember {
        try {
            val fbName = FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() }
            val prefs = context.getSharedPreferences("gmax_hub_prefs", Context.MODE_PRIVATE)
            val savedUsername = prefs.getString("hub_custom_username", null)?.takeIf { it.isNotBlank() }
            val savedFullName = prefs.getString("hub_custom_full_name", null)?.takeIf { it.isNotBlank() }
            savedUsername ?: fbName ?: savedFullName ?: "Player One"
        } catch (_: Exception) {
            "Player One"
        }
    }

    val backdropAlpha = remember { Animatable(0f) }
    val emblemScale = remember { Animatable(0.45f) }
    val emblemAlpha = remember { Animatable(0f) }

    val ring1Expand = remember { Animatable(0.25f) }
    val ring1Alpha = remember { Animatable(0.9f) }

    val ring2Expand = remember { Animatable(0.25f) }
    val ring2Alpha = remember { Animatable(0f) }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(18f) }
    val lightSweep = remember { Animatable(-0.35f) }
    val bootBarProgress = remember { Animatable(0f) }

    var showWelcomeToast by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "xboxInspiredConsolePulse")
    val coreBreathingScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreBreathingScale"
    )
    val haloGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloGlowAlpha"
    )

    LaunchedEffect(Unit) {
        // Stage 1 (0ms): Console Core Power-On + Neon Cyan Shockwave + Haptic Ignition
        ConsoleHapticManager.playBootPulse(context, stage = 1)

        launch {
            backdropAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
        launch {
            emblemAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
        launch {
            emblemScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            ring1Expand.animateTo(1.95f, tween(980, easing = FastOutSlowInEasing))
        }
        launch {
            ring1Alpha.animateTo(0f, tween(980, easing = LinearEasing))
        }
        launch {
            bootBarProgress.animateTo(1f, tween(2100, easing = FastOutSlowInEasing))
        }

        // Stage 2 (540ms): "G-MAX" Console Typography Reveal + Light Sweep Shimmer
        delay(540)
        ConsoleHapticManager.playBootPulse(context, stage = 2)

        launch {
            titleAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            ring2Alpha.snapTo(0.8f)
            ring2Expand.animateTo(2.15f, tween(950, easing = FastOutSlowInEasing))
        }
        launch {
            ring2Alpha.animateTo(0f, tween(950, easing = LinearEasing))
        }
        launch {
            lightSweep.animateTo(1.35f, tween(880, easing = FastOutSlowInEasing))
        }

        // Stage 3 (1150ms): Xbox One-Style Bottom Pill Toast ("Welcome back, [Gamer]")
        delay(610)
        showWelcomeToast = true
        ConsoleHapticManager.playSelectClick(context)

        delay(1050)
        onAnimationComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A192F),
                        Color(0xFF071122),
                        Color(0xFF050B18)
                    )
                )
            )
            .testTag("xbox_console_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Cyber Blue & Neon Cyan Halo + Dual Expanding Shockwave Rings + Light Sweep
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val minDim = size.minDimension
            val baseRadius = minDim * 0.24f

            // 1. Deep Cyber Blue & Neon Cyan Radial Spotlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.34f * backdropAlpha.value * haloGlowAlpha),
                        Color(0xFF2979FF).copy(alpha = 0.20f * backdropAlpha.value),
                        Color(0xFF0A192F).copy(alpha = 0.08f * backdropAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.6f
                ),
                radius = baseRadius * 2.6f,
                center = center
            )

            // 2. Primary Neon Cyan Power-On Shockwave Ring
            if (ring1Alpha.value > 0.01f) {
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = ring1Alpha.value),
                    radius = baseRadius * ring1Expand.value,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // 3. Secondary Electric Blue Harmonic Ring
            if (ring2Alpha.value > 0.01f) {
                drawCircle(
                    color = Color(0xFF2979FF).copy(alpha = ring2Alpha.value),
                    radius = baseRadius * ring2Expand.value,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 4. Diagonal Console Light-Sweep Shimmer
            val sweepPos = lightSweep.value
            if (sweepPos in -0.25f..1.25f) {
                val sweepX = size.width * sweepPos
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00E5FF).copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.20f),
                            Color(0xFF2979FF).copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        start = Offset(sweepX - 180f, 0f),
                        end = Offset(sweepX + 180f, size.height)
                    )
                )
            }
        }

        // Center Console Emblem & Brand Typography (Xbox One Centerpiece Style)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(emblemScale.value * coreBreathingScale)
                    .alpha(emblemAlpha.value)
            ) {
                // Outer Neon Cyan Halo Ring around G-Max Emblem
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.25f),
                                    Color(0xFF2979FF).copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF2979FF),
                                    Color(0xFF00E5FF)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                )

                // Official G-Max 3D Hexagonal Controller Emblem
                GMaxBrandIcon(
                    size = 104.dp,
                    cornerRadius = 26.dp,
                    showGlowBorder = true,
                    glowColor = Color(0xFF00E5FF),
                    contentDescription = "G-Max Console Core Emblem"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bold Futuristic "G-MAX" Wordmark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .padding(top = titleOffsetY.value.coerceAtLeast(0f).dp)
            ) {
                Text(
                    text = "G-MAX",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "GAMING HUB & CONSOLE BOOSTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.6.sp,
                    color = Color(0xFF00E5FF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sleek Xbox One-style subtle horizontal boot progress line
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bootBarProgress.value.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF2979FF),
                                        Color(0xFF00E5FF)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Bottom Xbox One-Inspired Slide-Out Gamer Greeting Pill ("Welcome back, [User]")
        AnimatedVisibility(
            visible = showWelcomeToast,
            enter = slideInVertically(initialOffsetY = { it / 2 }) +
                    expandHorizontally(expandFrom = Alignment.CenterHorizontally) +
                    fadeIn(tween(280)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF0B192E).copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00E5FF),
                            Color(0xFF2979FF),
                            Color(0xFF00E5FF)
                        )
                    )
                ),
                shadowElevation = 14.dp,
                modifier = Modifier.testTag("console_welcome_toast")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glowing G-Max Controller Badge inside the pill left circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF060A14))
                            .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        GMaxBrandIcon(
                            size = 28.dp,
                            cornerRadius = 14.dp,
                            showGlowBorder = false
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Welcome back, $gamerName",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "G-MAX CONSOLE MODE READY",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
