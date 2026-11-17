package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ConsoleHapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class ActionFeedbackEvent(
    val id: Long,
    val message: String,
    val accentColorArgb: Long = 0xFF00E5FFL,
    val isProcessStart: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Global Instant Action Feedback Controller.
 *
 * Ensures that whenever a user taps a button, toggles a setting, or starts a process,
 * the app provides immediate tactile haptic feedback and a responsive visual confirmation pill.
 */
object ActionFeedbackController {

    private val counter = AtomicLong(1L)
    private val _currentEvent = MutableStateFlow<ActionFeedbackEvent?>(null)
    val currentEvent: StateFlow<ActionFeedbackEvent?> = _currentEvent.asStateFlow()

    fun emitFeedback(
        context: Context?,
        message: String,
        accentColorArgb: Long = 0xFF00E5FFL,
        isProcessStart: Boolean = false,
        playHaptic: Boolean = true
    ) {
        if (playHaptic && context != null) {
            if (isProcessStart) {
                ConsoleHapticManager.playLaunchPulse(context)
            } else {
                ConsoleHapticManager.playSelectClick(context)
            }
        }
        val cleanMessage = message.trim()
        if (cleanMessage.isNotEmpty()) {
            _currentEvent.value = ActionFeedbackEvent(
                id = counter.incrementAndGet(),
                message = cleanMessage,
                accentColorArgb = accentColorArgb,
                isProcessStart = isProcessStart
            )
        }
    }

    fun dismiss(id: Long? = null) {
        val cur = _currentEvent.value ?: return
        if (id == null || cur.id == id) {
            _currentEvent.value = null
        }
    }
}

/**
 * Reusable interactive press modifier that provides:
 * 1. Immediate spring press scaling (`0.96f` while pressed, snappy release) via zero-recomposition `graphicsLayer`.
 * 2. Crisp tactile haptic feedback (`ConsoleHapticManager.playSelectClick` or `playLaunchPulse`).
 * 3. Optional instant status banner feedback message.
 */
fun Modifier.gmaxInteractivePress(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    feedbackMessage: String? = null,
    isProcessStart: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "gmaxInteractivePressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled
        ) {
            if (!feedbackMessage.isNullOrBlank()) {
                ActionFeedbackController.emitFeedback(
                    context = context,
                    message = feedbackMessage,
                    isProcessStart = isProcessStart,
                    playHaptic = true
                )
            } else {
                if (isProcessStart) {
                    ConsoleHapticManager.playLaunchPulse(context)
                } else {
                    ConsoleHapticManager.playSelectClick(context)
                }
            }
            onClick()
        }
}

/**
 * Non-blocking, ultra-responsive floating confirmation pill that acknowledges
 * button taps, setting toggles, cache purges, and background process starts within <16ms.
 */
@Composable
fun InstantFeedbackBannerHost(
    modifier: Modifier = Modifier
) {
    val event by ActionFeedbackController.currentEvent.collectAsState()

    LaunchedEffect(event?.id) {
        val activeId = event?.id ?: return@LaunchedEffect
        delay(1800L)
        ActionFeedbackController.dismiss(activeId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 56.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = event != null,
            enter = fadeIn(animationSpec = tween(130)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(150)
            )
        ) {
            val active = event
            if (active != null) {
                val accent = Color(active.accentColorArgb)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xEE0B111E),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .border(1.2.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .clickable { ActionFeedbackController.dismiss(active.id) }
                        .testTag("instant_action_feedback_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (active.isProcessStart) Icons.Default.Bolt else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = com.example.util.AppStrings.autoTranslateDynamic(active.message, com.example.util.currentAppLanguage()),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
