package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.ui.theme.SolarGold
import com.example.util.AdMobManager
import com.example.util.ConsoleHapticManager
import com.example.util.DailyStreakManager
import com.example.util.DailyStreakState
import com.example.util.StreakDayStatus
import com.example.util.tr
import kotlinx.coroutines.delay

private val FlameOrange = Color(0xFFFF6D00)
private val FlameAmber = Color(0xFFFFAB00)
private val CryoCyan = Color(0xFF00E5FF)

/**
 * Compact Flame Streak & G-Tokens Pill displayed in the Top Launcher Bar on `GamesScreen`.
 */
@Composable
fun DailyStreakTopBarPill(
    streakState: DailyStreakState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRepairNeeded = streakState.isStreakBrokenRepairable
    val isActiveToday = streakState.completedToday && !isRepairNeeded

    val borderColor = when {
        isRepairNeeded -> RecorderCoral
        isActiveToday -> FlameOrange
        streakState.currentStreak > 0 -> FlameAmber.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val containerColor = when {
        isRepairNeeded -> RecorderCoral.copy(alpha = 0.16f)
        isActiveToday -> FlameOrange.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }

    Surface(
        onClick = {
            ConsoleHapticManager.playSelectClick(context)
            onClick()
        },
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier.testTag("launcher_daily_streak_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isRepairNeeded) Icons.Default.Warning else Icons.Default.LocalFireDepartment,
                contentDescription = "Daily Streak",
                tint = when {
                    isRepairNeeded -> RecorderCoral
                    isActiveToday || streakState.currentStreak > 0 -> FlameOrange
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (isRepairNeeded) {
                    "${streakState.lostStreakCount}"
                } else {
                    "${streakState.currentStreak}"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = if (isRepairNeeded) RecorderCoral else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * Exclusive 30-Day Streak VIP Badge (`⚡ 30D VIP`) displayed next to the player's username.
 * Automatically disappears if the player's streak breaks and resets below 30 days.
 */
@Composable
fun VipStreakUsernameBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SolarGold.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.85f)),
        modifier = modifier.testTag("vip_30d_streak_username_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "30-Day Streak VIP",
                tint = SolarGold,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "VIP",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = SolarGold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp
                )
            )
        }
    }
}

/**
 * Dedicated Streak & G-Tokens Summary Card on `AccountProfileScreen`.
 */
@Composable
fun ProfileDailyStreakCard(
    streakState: DailyStreakState,
    onOpenStreakHub: () -> Unit,
    onOpenRepairDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    CyberCard(modifier = modifier.testTag("profile_daily_streak_card")) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (streakState.isStreakBrokenRepairable) RecorderCoral.copy(alpha = 0.2f)
                                else FlameOrange.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Daily Streak",
                            tint = if (streakState.isStreakBrokenRepairable) RecorderCoral else FlameOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tr("DAILY ENGAGEMENT STREAK", "RACHA DIARIA DE JUEGO"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlameOrange,
                                    letterSpacing = 0.8.sp
                                )
                            )
                            if (streakState.isVipBadgeUnlocked) {
                                VipStreakUsernameBadge()
                            }
                        }
                        Text(
                            text = if (streakState.isStreakBrokenRepairable) {
                                tr(
                                    "⚠️ ${streakState.lostStreakCount}-Day Streak Lost (12h Repair Active)",
                                    "⚠️ Racha de ${streakState.lostStreakCount} días perdida (12h para reparar)"
                                )
                            } else {
                                tr(
                                    "${streakState.currentStreak} Day Streak • Best: ${streakState.longestStreak}d",
                                    "Racha de ${streakState.currentStreak} días • Mejor: ${streakState.longestStreak}d"
                                )
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Button(
                    onClick = {
                        ConsoleHapticManager.playSelectClick(context)
                        if (streakState.isStreakBrokenRepairable) onOpenRepairDialog() else onOpenStreakHub()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (streakState.isStreakBrokenRepairable) RecorderCoral else FlameOrange,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("profile_open_streak_hub_button")
                ) {
                    Text(
                        text = if (streakState.isStreakBrokenRepairable) tr("Repair", "Reparar") else tr("Streak Hub", "Ver Racha"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            // Economy & Perks Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🪙 ${streakState.gTokensBalance}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = SolarGold
                            )
                        )
                        Text(
                            text = tr("G-Tokens", "G-Tokens"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "❄️ ${streakState.streakFreezesOwned}/${DailyStreakManager.MAX_STREAK_FREEZES}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = CryoCyan
                            )
                        )
                        Text(
                            text = tr("Freezes", "Congeladores"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when {
                                streakState.isVipBadgeUnlocked -> "30D VIP ✅"
                                streakState.isHudNeonUnlocked -> "7D NEON ✅"
                                else -> "${streakState.currentStreak}/7D"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = if (streakState.isHudNeonUnlocked) LaunchGreen else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = tr("Milestones", "Logros"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full Interactive Daily Engagement Streak Hub, G-Tokens Shop & Milestones Modal.
 */
@Composable
fun DailyStreakHubModalDialog(
    streakState: DailyStreakState,
    feedbackMessage: String?,
    onBuyStreakFreeze: () -> Unit,
    onSelectNeonColor: (String) -> Unit,
    onOpenRepairModal: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("daily_streak_hub_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, FlameOrange.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with prominent "X" close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(FlameOrange, FlameAmber))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak Flame",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(
                                    "${streakState.currentStreak} DAY STREAK",
                                    "RACHA DE ${streakState.currentStreak} DÍAS"
                                ),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (streakState.completedToday) {
                                    tr("✅ Today's goal completed! Keep it up tomorrow.", "✅ ¡Meta de hoy completada! Sigue así mañana.")
                                } else {
                                    tr(
                                        "⏳ Launch a game with Boost or play with the live HUD before midnight!",
                                        "⏳ ¡Inicia un juego con Boost o juega con el HUD activo antes de medianoche!"
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (streakState.completedToday) LaunchGreen else FlameAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            ConsoleHapticManager.playSelectClick(context)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_streak_hub_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Close Streak Hub", "Cerrar Racha"),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Broken Streak Alert Banner inside Hub if applicable
                if (streakState.isStreakBrokenRepairable) {
                    Surface(
                        onClick = onOpenRepairModal,
                        shape = RoundedCornerShape(14.dp),
                        color = RecorderCoral.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, RecorderCoral),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr(
                                        "🚨 ${streakState.lostStreakCount}-Day Streak Broken!",
                                        "🚨 ¡Racha de ${streakState.lostStreakCount} días rota!"
                                    ),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        color = RecorderCoral
                                    )
                                )
                                Text(
                                    text = tr(
                                        "Tap to repair via Rewarded Video Ad (${streakState.formatRepairCountdown()} left)",
                                        "Toca para reparar con anuncio de video (${streakState.formatRepairCountdown()} restantes)"
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Text(
                                text = tr("REPAIR", "REPARAR"),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = RecorderCoral
                                )
                            )
                        }
                    }
                }

                // 7-Day Flame & Freeze Calendar Strip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        streakState.recentDays.forEach { day ->
                            val (bg, iconText, labelColor) = when (day.status) {
                                StreakDayStatus.COMPLETED -> Triple(FlameOrange, "🔥", FlameOrange)
                                StreakDayStatus.FROZEN -> Triple(CryoCyan, "❄️", CryoCyan)
                                StreakDayStatus.TODAY_PENDING -> Triple(
                                    FlameAmber.copy(alpha = 0.25f),
                                    "⏳",
                                    MaterialTheme.colorScheme.onSurface
                                )
                                StreakDayStatus.MISSED -> Triple(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    "•",
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StreakDayStatus.UPCOMING -> Triple(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    "○",
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = day.dayShortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = labelColor,
                                        fontSize = 10.sp
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(bg.copy(alpha = if (day.status == StreakDayStatus.COMPLETED || day.status == StreakDayStatus.FROZEN) 0.22f else 0.12f))
                                        .border(
                                            width = 1.dp,
                                            color = bg,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = iconText, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Economy: G-Tokens & Streak Freeze Protection Shop
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, CryoCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AcUnit,
                                    contentDescription = "Streak Freeze",
                                    tint = CryoCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = tr("STREAK FREEZE PROTECTION", "PROTECCIÓN CONGELADOR DE RACHA"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CryoCyan
                                        )
                                    )
                                    Text(
                                        text = tr(
                                            "Auto-activates at midnight if you miss a day (Max 2 held)",
                                            "Se activa automáticamente a medianoche si olvidas jugar (Máx. 2)"
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CryoCyan.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, CryoCyan)
                            ) {
                                Text(
                                    text = "❄️ ${streakState.streakFreezesOwned}/${DailyStreakManager.MAX_STREAK_FREEZES}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = CryoCyan
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Passive G-Token Earning Progress
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tr(
                                        "🪙 Balance: ${streakState.gTokensBalance} G-Tokens",
                                        "🪙 Saldo: ${streakState.gTokensBalance} G-Tokens"
                                    ),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SolarGold
                                    )
                                )
                                Text(
                                    text = tr(
                                        "+10 G-Tokens per 5m of active HUD play (${streakState.secondsUntilNextTokenBatch}s left)",
                                        "+10 G-Tokens cada 5m con HUD activo (${streakState.secondsUntilNextTokenBatch}s restantes)"
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            LinearProgressIndicator(
                                progress = { streakState.nextTokenProgressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = SolarGold,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }

                        val canBuyFreeze = streakState.streakFreezesOwned < DailyStreakManager.MAX_STREAK_FREEZES &&
                            streakState.gTokensBalance >= DailyStreakManager.STREAK_FREEZE_COST_TOKENS
                        Button(
                            onClick = {
                                ConsoleHapticManager.playLaunchPulse(context)
                                onBuyStreakFreeze()
                            },
                            enabled = streakState.streakFreezesOwned < DailyStreakManager.MAX_STREAK_FREEZES,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canBuyFreeze) CryoCyan else MaterialTheme.colorScheme.surface,
                                contentColor = if (canBuyFreeze) Color(0xFF041019) else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("buy_streak_freeze_button")
                        ) {
                            Text(
                                text = if (streakState.streakFreezesOwned >= DailyStreakManager.MAX_STREAK_FREEZES) {
                                    tr("Inventory Full (2/2 Streak Freezes Equipped)", "Inventario Lleno (2/2 Congeladores Equipados)")
                                } else {
                                    tr(
                                        "Buy 1 Streak Freeze • 100 G-Tokens",
                                        "Comprar 1 Congelador de Racha • 100 G-Tokens"
                                    )
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }

                // Milestones Section: 7-Day Custom Neon HUD & 30-Day VIP Username Badge
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = tr("STREAK MILESTONE REWARDS", "RECOMPENSAS DE HITOS DE RACHA"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = HubBlue,
                        letterSpacing = 0.8.sp
                    )
                )

                // 7-Day Milestone: Custom Neon HUD Color
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (streakState.isHudNeonUnlocked) LaunchGreen.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (streakState.isHudNeonUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = "7-Day Milestone",
                                    tint = if (streakState.isHudNeonUnlocked) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = tr("7-Day Streak: Custom Neon HUD Color", "Racha de 7 Días: Color Neón para el HUD"),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = if (streakState.isHudNeonUnlocked) {
                                            tr("Unlocked! Tap a neon color below to style your live HUD.", "¡Desbloqueado! Toca un color neón para personalizar tu HUD.")
                                        } else {
                                            tr(
                                                "Progress: ${streakState.currentStreak.coerceAtMost(7)}/7 days (Lost if streak breaks)",
                                                "Progreso: ${streakState.currentStreak.coerceAtMost(7)}/7 días (Se pierde si la racha se rompe)"
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DailyStreakManager.NEON_HUD_PRESETS.forEach { preset ->
                                val isSelected = streakState.selectedHudNeonColorHex.equals(preset.hexColor, ignoreCase = true)
                                val swatchColor = Color(preset.argb)
                                Surface(
                                    onClick = {
                                        ConsoleHapticManager.playSelectClick(context)
                                        onSelectNeonColor(preset.hexColor)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = swatchColor.copy(alpha = if (streakState.isHudNeonUnlocked) 0.2f else 0.08f),
                                    border = BorderStroke(
                                        width = if (isSelected && streakState.isHudNeonUnlocked) 2.dp else 1.dp,
                                        color = if (streakState.isHudNeonUnlocked) swatchColor else swatchColor.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.testTag("streak_neon_hud_${preset.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                        Text(
                                            text = tr(preset.labelEn, preset.labelEs),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (streakState.isHudNeonUnlocked) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 30-Day Milestone: VIP Badge Next to Username
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (streakState.isVipBadgeUnlocked) SolarGold.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (streakState.isVipBadgeUnlocked) Icons.Default.Stars else Icons.Default.Lock,
                                contentDescription = "30-Day VIP Badge",
                                tint = if (streakState.isVipBadgeUnlocked) SolarGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = tr("30-Day Streak: VIP Username Badge", "Racha de 30 Días: Insignia VIP junto al Usuario"),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = if (streakState.isVipBadgeUnlocked) {
                                        tr("Active! Your ⚡ VIP badge is displayed next to your username.", "¡Activa! Tu insignia ⚡ VIP se muestra junto a tu nombre de usuario.")
                                    } else {
                                        tr(
                                            "Progress: ${streakState.currentStreak.coerceAtMost(30)}/30 days (Revoked if streak breaks)",
                                            "Progreso: ${streakState.currentStreak.coerceAtMost(30)}/30 días (Se revoca si la racha se rompe)"
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        VipStreakUsernameBadge()
                    }
                }

                AnimatedVisibility(visible = !feedbackMessage.isNullOrBlank()) {
                    Text(
                        text = feedbackMessage.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = HubBlue,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * "Streak Lost!" 12-Hour Grace Period Screen with Google AdMob Rewarded Video Ad Repair.
 */
@Composable
fun StreakLostRepairModalDialog(
    streakState: DailyStreakState,
    onRepairViaRewardedAd: () -> Unit,
    onLetStreakReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(streakState.isStreakBrokenRepairable) {
        while (streakState.isStreakBrokenRepairable) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val countdownStr = streakState.formatRepairCountdown(nowEpochMs)
    val lostDays = streakState.lostStreakCount.coerceAtLeast(1)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("streak_lost_repair_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, RecorderCoral)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(RecorderCoral.copy(alpha = 0.18f))
                        .border(2.dp, RecorderCoral, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak Lost",
                        tint = RecorderCoral,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = tr("STREAK LOST!", "¡RACHA PERDIDA!"),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = RecorderCoral,
                        letterSpacing = 1.sp
                    )
                )

                Text(
                    text = tr(
                        "You missed a day with 0 Streak Freezes left! Your $lostDays-day streak (and any 7-Day Neon HUD or 30-Day VIP perks) has been locked.",
                        "¡Perdiste un día sin Congeladores de Racha! Tu racha de $lostDays días (y tus ventajas de 7 y 30 días) se ha bloqueado."
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                )

                // Live 12-Hour Grace Period Countdown Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = RecorderCoral.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tr("12-HOUR REPAIR GRACE PERIOD", "PERIODO DE GRACIA DE 12 HORAS"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = RecorderCoral
                            )
                        )
                        Text(
                            text = "⏳ $countdownStr",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                // Primary Monetization CTA: Watch Google AdMob Rewarded Video Ad
                Button(
                    onClick = {
                        ConsoleHapticManager.playLaunchPulse(context)
                        AdMobManager.showRewardedAdForStreakRepair(
                            context = context,
                            lostStreakDays = lostDays,
                            onRepaired = onRepairViaRewardedAd
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FlameOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("repair_streak_rewarded_ad_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr(
                            "Watch Video Ad to Repair $lostDays-Day Streak",
                            "Ver Anuncio para Reparar Racha de $lostDays Días"
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        ConsoleHapticManager.playSelectClick(context)
                        onLetStreakReset()
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_broken_streak_button")
                ) {
                    Text(
                        text = tr("No Thanks, Reset Streak to 0", "No gracias, reiniciar racha a 0"),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
