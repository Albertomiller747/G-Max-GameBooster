package com.example.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.util.ConsoleHapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private data class ConsoleSparkParticle(
    val angleRad: Float,
    val speedFactor: Float,
    val radiusDp: Float,
    val color: Color
)

/**
 * Upgraded Multi-Stage Console Boot Reveal with 3D Emblem Flip, Particle Light Sweep,
 * Dual Shockwave Rings, Rotating Orbital Arcs, and Synchronized Haptic Pulses.
 */
@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val emblemScale = remember { Animatable(0.32f) }
    val emblemRotationY = remember { Animatable(-85f) }
    val contentAlpha = remember { Animatable(0f) }

    val ring1Expand = remember { Animatable(0.15f) }
    val ring1Alpha = remember { Animatable(0.95f) }

    val ring2Expand = remember { Animatable(0.15f) }
    val ring2Alpha = remember { Animatable(0f) }

    val particleBurst = remember { Animatable(0f) }
    val lightSweep = remember { Animatable(-0.4f) }
    val progress = remember { Animatable(0f) }

    var activeStage by remember { mutableIntStateOf(1) }
    var statusLabel by remember { mutableStateOf("STAGE 01 // IGNITING CONSOLE CORE") }

    val particles = remember {
        List(24) { idx ->
            val angle = (idx * (360f / 24f)) * (Math.PI.toFloat() / 180f)
            val speed = 0.55f + (idx % 5) * 0.14f
            val size = if (idx % 3 == 0) 4.2f else 2.8f
            val col = when (idx % 3) {
                0 -> Color(0xFF38BDF8)
                1 -> LaunchGreen
                else -> HubBlue
            }
            ConsoleSparkParticle(angle, speed, size, col)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "consoleBootInfinite")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val orbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitalAngle"
    )

    LaunchedEffect(Unit) {
        // Stage 1 (0ms): 3D Emblem Ignition + Shockwave Ring 1 + Haptic Pulse 1
        ConsoleHapticManager.playBootPulse(context, stage = 1)

        launch {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            )
        }
        launch {
            emblemScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            emblemRotationY.animateTo(
                targetValue = 0f,
                animationSpec = tween(680, easing = FastOutSlowInEasing)
            )
        }
        launch {
            ring1Expand.animateTo(
                targetValue = 1.85f,
                animationSpec = tween(920, easing = FastOutSlowInEasing)
            )
        }
        launch {
            ring1Alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(920, easing = LinearEasing)
            )
        }
        launch {
            particleBurst.animateTo(
                targetValue = 1f,
                animationSpec = tween(1350, easing = FastOutSlowInEasing)
            )
        }
        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1900, easing = FastOutSlowInEasing)
            )
        }

        // Stage 2 (580ms): Secondary Shockwave + Particle Light Sweep + Haptic Pulse 2
        delay(580)
        activeStage = 2
        statusLabel = "STAGE 02 // SYNCING GAMEPAD & TELEMETRY"
        ConsoleHapticManager.playBootPulse(context, stage = 2)

        launch {
            ring2Alpha.snapTo(0.85f)
            ring2Expand.animateTo(
                targetValue = 2.05f,
                animationSpec = tween(950, easing = FastOutSlowInEasing)
            )
        }
        launch {
            ring2Alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(950, easing = LinearEasing)
            )
        }
        launch {
            lightSweep.animateTo(
                targetValue = 1.4f,
                animationSpec = tween(900, easing = FastOutSlowInEasing)
            )
        }

        // Stage 3 (1250ms): Boost Engine Online Lock-in
        delay(670)
        activeStage = 3
        statusLabel = "STAGE 03 // G-MAX CONSOLE ENGINE ONLINE"

        delay(720)
        onAnimationComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Multi-Layer Canvas: Ambient Spotlight, Dual Shockwave Rings, Radial Particle Burst & Light Sweep
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val baseRadius = size.minDimension * 0.27f

            // 1. Ambient Console Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HubBlue.copy(alpha = 0.28f * contentAlpha.value),
                        Color(0xFF38BDF8).copy(alpha = 0.14f * contentAlpha.value),
                        LaunchGreen.copy(alpha = 0.08f * contentAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.5f
                ),
                radius = baseRadius * 2.5f,
                center = center
            )

            // 2. Primary Shockwave Ring (Cyan)
            if (ring1Alpha.value > 0.01f) {
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = ring1Alpha.value),
                    radius = baseRadius * ring1Expand.value,
                    center = center,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }

            // 3. Secondary Shockwave Ring (Emerald-Sapphire)
            if (ring2Alpha.value > 0.01f) {
                drawCircle(
                    color = LaunchGreen.copy(alpha = ring2Alpha.value),
                    radius = baseRadius * ring2Expand.value,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 4. Radial Particle Light Burst
            val burstProgress = particleBurst.value
            if (burstProgress in 0.02f..0.99f) {
                val particleAlpha = (1f - burstProgress).coerceIn(0f, 1f)
                particles.forEach { p ->
                    val dist = baseRadius * (0.35f + burstProgress * 1.65f * p.speedFactor)
                    val px = center.x + cos(p.angleRad) * dist
                    val py = center.y + sin(p.angleRad) * dist
                    drawCircle(
                        color = p.color.copy(alpha = particleAlpha * contentAlpha.value),
                        radius = p.radiusDp.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            // 5. Diagonal Shimmer Light Sweep Across Screen Center
            val sweepPos = lightSweep.value
            if (sweepPos in -0.2f..1.2f) {
                val sweepX = size.width * sweepPos
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF38BDF8).copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.18f),
                            LaunchGreen.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        start = Offset(sweepX - 180f, 0f),
                        end = Offset(sweepX + 180f, size.height)
                    )
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(emblemScale.value)
                .alpha(contentAlpha.value)
        ) {
            // 3D Multi-Stage Console Emblem with Rotating Orbital Energy Arcs
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(156.dp)
                    .scale(glowPulse)
                    .graphicsLayer {
                        rotationY = emblemRotationY.value
                        cameraDistance = 14f * density
                    }
            ) {
                // Counter-Rotating Orbital Energy Arcs Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 3.dp.toPx()
                    val inset = 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                    // Outer clockwise cyber arc
                    drawArc(
                        color = Color(0xFF38BDF8),
                        startAngle = orbitalAngle,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = LaunchGreen,
                        startAngle = orbitalAngle + 180f,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Inner counter-clockwise ring ticks
                    val innerInset = 15.dp.toPx()
                    val innerSize = Size(size.width - innerInset * 2, size.height - innerInset * 2)
                    drawArc(
                        color = HubBlue.copy(alpha = 0.75f),
                        startAngle = -orbitalAngle * 1.3f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(innerInset, innerInset),
                        size = innerSize,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF38BDF8).copy(alpha = 0.75f),
                        startAngle = (-orbitalAngle * 1.3f) + 180f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(innerInset, innerInset),
                        size = innerSize,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Middle Console Armor Ring
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.sweepGradient(
                                listOf(HubBlue, Color(0xFF38BDF8), LaunchGreen, HubBlue)
                            ),
                            shape = CircleShape
                        )
                )

                // Core Sapphire-Emerald Console Emblem with Light Sweep Overlay
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1D4ED8),
                                    Color(0xFF2563EB),
                                    Color(0xFF0EA5E9),
                                    if (activeStage >= 3) LaunchGreen else Color(0xFF0284C7)
                                )
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "G MAX Console Boot",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Console Title
            Text(
                text = "G-MAX CONSOLE HUB",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.6.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "NEXT-GEN ANDROID PERFORMANCE LAUNCHER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                color = HubBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Console Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        if (activeStage >= 3) LaunchGreen.copy(alpha = 0.6f) else HubBlue.copy(alpha = 0.45f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = if (activeStage >= 3) LaunchGreen else Color(0xFF38BDF8),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 3-Stage Console Boot Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (stageIdx in 1..3) {
                    val isDone = activeStage >= stageIdx
                    Box(
                        modifier = Modifier
                            .width(if (activeStage == stageIdx) 26.dp else 10.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    activeStage == stageIdx -> Color(0xFF38BDF8)
                                    isDone -> LaunchGreen
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Console Boot Progress Bar with Percentage
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.60f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(HubBlue, Color(0xFF38BDF8), LaunchGreen)
                            )
                        )
                )
            }
        }
    }
}
