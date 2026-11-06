package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControllerGlyphMap
import com.example.model.ControllerLayoutResolver
import com.example.util.ConsoleHapticManager
import com.example.util.ConsoleStartupSoundEngine
import com.example.util.ControllerHealthManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SmoothConsoleEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

/**
 * Smooth Console Startup Sequence with G Max Gaming Hub Emblem & Cyber-Blue/Neon-Cyan Palette.
 * Uses hardware-accelerated graphicsLayer transitions and overlapping crossfades for 60/120fps fluidity,
 * with a compact, sleek "Log Into Console" pill button.
 */
@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit,
    controllerGlyphs: ControllerGlyphMap = ControllerLayoutResolver.playStationGlyphs,
    ultraFastTransition: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loginButtonFocusRequester = remember { FocusRequester() }

    val gamerName = remember {
        try {
            val fbName = FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() }
            val prefs = context.getSharedPreferences("gmax_hub_ui_prefs", Context.MODE_PRIVATE)
            val savedUsername = prefs.getString("hub_user_username", null)?.takeIf { it.isNotBlank() }
            val savedFullName = prefs.getString("hub_user_custom_name", null)?.takeIf { it.isNotBlank() }
            savedUsername ?: fbName ?: savedFullName ?: "Player One"
        } catch (_: Exception) {
            "Player One"
        }
    }

    // Phase 1: Centered G Max Emblem on Pure Black
    val phase1EmblemAlpha = remember { Animatable(if (ultraFastTransition) 0.35f else 0f) }
    val phase1EmblemScale = remember { Animatable(if (ultraFastTransition) 0.96f else 0.88f) }

    // Phase 2: Flowing Blue/Cyan Wave Backdrop & Interactive Login Screen
    val waveBackdropAlpha = remember { Animatable(0f) }
    val loginContentAlpha = remember { Animatable(0f) }
    val loginContentScale = remember { Animatable(0.94f) }
    val loginContentOffsetY = remember { Animatable(18f) }

    var isLoginGateReady by remember { mutableStateOf(ultraFastTransition) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Listen to physical Controller Home / Guide / Start / A button signal from ControllerHealthManager
    val homeSignalTimestamp by ControllerHealthManager.homeLoginSignal.collectAsState()
    val initialHomeSignal = remember { homeSignalTimestamp }

    val triggerConsoleLogin: () -> Unit = {
        if (isLoginGateReady && !isLoggingIn) {
            isLoggingIn = true
            ConsoleHapticManager.playLaunchPulse(context)
            ConsoleStartupSoundEngine.playLoginChime()
            scope.launch {
                val exitMs = if (ultraFastTransition) 120 else 250
                launch {
                    loginContentScale.animateTo(1.03f, tween(exitMs, easing = SmoothConsoleEasing))
                }
                launch {
                    loginContentAlpha.animateTo(0f, tween(exitMs, easing = SmoothConsoleEasing))
                }
                launch {
                    waveBackdropAlpha.animateTo(0f, tween(exitMs, easing = SmoothConsoleEasing))
                }
                delay(exitMs.toLong())
                onAnimationComplete()
            }
        }
    }

    // Trigger login immediately when physical controller Home/Start/Primary button is pressed during Phase 2
    LaunchedEffect(homeSignalTimestamp, isLoginGateReady) {
        if (isLoginGateReady && homeSignalTimestamp > initialHomeSignal && !isLoggingIn) {
            triggerConsoleLogin()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ps4WaveAnimation")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val homePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homePulseScale"
    )
    val homeGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homeGlowAlpha"
    )

    LaunchedEffect(ultraFastTransition) {
        if (ultraFastTransition) {
            // Instant launch with ultra-fast 250ms transition
            ConsoleHapticManager.playBootPulse(context, stage = 1)
            launch {
                phase1EmblemScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh
                    )
                )
            }
            phase1EmblemAlpha.animateTo(1f, tween(140, easing = SmoothConsoleEasing))
            delay(60)
            phase1EmblemAlpha.animateTo(0f, tween(50, easing = SmoothConsoleEasing))
            onAnimationComplete()
            return@LaunchedEffect
        }

        // Full console sequence when ultraFastTransition == false
        ConsoleHapticManager.playBootPulse(context, stage = 1)
        launch {
            phase1EmblemScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        phase1EmblemAlpha.animateTo(1f, tween(480, easing = SmoothConsoleEasing))
        delay(420)

        // Start fading in Phase 2 wave backdrop while Phase 1 emblem smoothly dissolves
        ConsoleStartupSoundEngine.playStartupAmbientChord()
        ConsoleHapticManager.playBootPulse(context, stage = 2)

        launch {
            phase1EmblemAlpha.animateTo(0f, tween(450, easing = SmoothConsoleEasing))
        }
        launch {
            waveBackdropAlpha.animateTo(1f, tween(650, easing = SmoothConsoleEasing))
        }
        delay(220)
        isLoginGateReady = true
        launch {
            loginContentAlpha.animateTo(1f, tween(480, easing = SmoothConsoleEasing))
        }
        launch {
            loginContentScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            loginContentOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(480, easing = SmoothConsoleEasing)
            )
        }
        delay(100)
        try {
            loginButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && isLoginGateReady) {
                    when (keyEvent.key) {
                        Key.Home,
                        Key.ButtonMode,
                        Key.ButtonStart,
                        Key.ButtonA,
                        Key.Enter,
                        Key.DirectionCenter -> {
                            triggerConsoleLogin()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .testTag("ps4_console_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // PHASE 2: Flowing Blue & Cyan Ribbon Wave Backdrop
        if (waveBackdropAlpha.value > 0.005f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = waveBackdropAlpha.value }
            ) {
                val w = size.width
                val h = size.height

                // 1. Deep G Max Console Blue Atmospheric Gradient
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF030A18),
                            Color(0xFF071F46),
                            Color(0xFF0B3B82),
                            Color(0xFF051836),
                            Color(0xFF030914)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )

                // 2. Soft Radial Cyan/Blue Horizon Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.22f),
                            Color(0xFF2979FF).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.52f, h * 0.48f),
                        radius = maxOf(w, h) * 0.62f
                    ),
                    radius = maxOf(w, h) * 0.62f,
                    center = Offset(w * 0.52f, h * 0.48f)
                )

                // 3. High-Resolution Smooth Flowing Ribbon Waves (Seamless Infinite 2π Loop)
                val ribbonCount = 4
                for (layer in 0 until ribbonCount) {
                    val path = Path()
                    val strokePath = Path()
                    val baseHeightRatio = 0.44f + layer * 0.06f
                    val amp1 = h * (0.055f + layer * 0.01f)
                    val amp2 = h * (0.026f + layer * 0.007f)
                    val phaseShift1 = wavePhase + layer * 0.65f
                    val phaseShift2 = (wavePhase * 2f) + layer * 0.85f

                    val steps = 72
                    for (i in 0..steps) {
                        val frac = i.toFloat() / steps
                        val x = frac * w
                        val y = h * baseHeightRatio +
                            sin(frac * 3.2f + phaseShift1) * amp1 +
                            cos(frac * 5.4f - phaseShift2) * amp2

                        if (i == 0) {
                            path.moveTo(x, y)
                            strokePath.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            strokePath.lineTo(x, y)
                        }
                    }

                    path.lineTo(w, h)
                    path.lineTo(0f, h)
                    path.close()

                    val ribbonColor = if (layer % 2 == 0) Color(0xFF00E5FF) else Color(0xFF2979FF)
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ribbonColor.copy(alpha = 0.13f - layer * 0.02f),
                                Color(0xFF061633).copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            startY = h * 0.25f,
                            endY = h
                        )
                    )

                    drawPath(
                        path = strokePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00E5FF).copy(alpha = 0.40f - layer * 0.05f),
                                Color.White.copy(alpha = 0.30f - layer * 0.04f),
                                Color(0xFF2979FF).copy(alpha = 0.36f - layer * 0.05f),
                                Color.Transparent
                            )
                        ),
                        style = Stroke(width = (2.0f - layer * 0.2f).dp.toPx())
                    )
                }
            }

            // Top-Left G Max Gaming Hub Console Emblem & Wordmark
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 24.dp, top = 20.dp)
                    .graphicsLayer { alpha = loginContentAlpha.value },
                verticalAlignment = Alignment.CenterVertically
            ) {
                GMaxBrandIcon(
                    size = 30.dp,
                    cornerRadius = 8.dp,
                    showGlowBorder = true,
                    glowColor = Color(0xFF00E5FF),
                    contentDescription = "G Max Gaming Hub Header Logo"
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "G MAX GAMING HUB",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp
                )
            }

            // Centered "Welcome Back" & Compact "Log Into Console" Button
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        scaleX = loginContentScale.value
                        scaleY = loginContentScale.value
                        translationY = loginContentOffsetY.value
                        alpha = loginContentAlpha.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = com.example.util.tr(
                        "Welcome Back to G Max Gaming Hub",
                        "Bienvenido de Nuevo a G Max Gaming Hub"
                    ),
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // "Press the [Controller Home] button to use the controller."
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = com.example.util.tr("Press the ", "Presiona el botón "),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = homePulseScale
                                scaleY = homePulseScale
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF0A192F))
                            .border(
                                width = 1.2.dp,
                                color = Color(0xFF00E5FF).copy(alpha = homeGlowAlpha),
                                shape = CircleShape
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = controllerGlyphs.homeGlyph,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Text(
                        text = com.example.util.tr(
                            " button to use the controller.",
                            " para usar el mando."
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Compact, Sleek "Log Into Console" Button (zero shadowElevation or rectangular focus box)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF091B38).copy(alpha = 0.92f), RoundedCornerShape(50))
                        .border(
                            BorderStroke(
                                width = 1.2.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00E5FF).copy(alpha = homeGlowAlpha),
                                        Color(0xFF2979FF),
                                        Color(0xFF00E5FF).copy(alpha = homeGlowAlpha)
                                    )
                                )
                            ),
                            RoundedCornerShape(50)
                        )
                        .focusRequester(loginButtonFocusRequester)
                        .clickable { triggerConsoleLogin() }
                        .testTag("console_home_login_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.35f),
                                            Color(0xFF2979FF).copy(alpha = 0.15f)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0xFF00E5FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "Controller Home Button",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(9.dp))

                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = if (isLoggingIn) {
                                    com.example.util.tr(
                                        "Logging in as $gamerName...",
                                        "Iniciando sesión como $gamerName..."
                                    )
                                } else {
                                    com.example.util.tr(
                                        "Log Into Console ($gamerName)",
                                        "Iniciar Sesión en Consola ($gamerName)"
                                    )
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = com.example.util.tr(
                                    "Press ${controllerGlyphs.homePromptName} / ${controllerGlyphs.southGlyph} or Tap",
                                    "Presiona ${controllerGlyphs.homePromptName} / ${controllerGlyphs.southGlyph} o Toca"
                                ),
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom subtle layout indicator badge
            AnimatedVisibility(
                visible = isLoginGateReady,
                enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.95f),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 18.dp)
            ) {
                Text(
                    text = com.example.util.tr(
                        "Active Controller Layout: ${controllerGlyphs.brandTitle} (${controllerGlyphs.southGlyph} ${controllerGlyphs.eastGlyph} ${controllerGlyphs.westGlyph} ${controllerGlyphs.northGlyph})",
                        "Diseño de Mando Activo: ${controllerGlyphs.brandTitle} (${controllerGlyphs.southGlyph} ${controllerGlyphs.eastGlyph} ${controllerGlyphs.westGlyph} ${controllerGlyphs.northGlyph})"
                    ),
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // PHASE 1: Centered G Max Emblem on Pure Black (Smoothly crossfades into Phase 2)
        if (phase1EmblemAlpha.value > 0.005f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = phase1EmblemScale.value
                    scaleY = phase1EmblemScale.value
                    alpha = phase1EmblemAlpha.value
                }
            ) {
                GMaxBrandIcon(
                    size = 76.dp,
                    cornerRadius = 20.dp,
                    showGlowBorder = true,
                    glowColor = Color(0xFF00E5FF),
                    contentDescription = "G Max Boot Emblem"
                )
            }
        }
    }
}
