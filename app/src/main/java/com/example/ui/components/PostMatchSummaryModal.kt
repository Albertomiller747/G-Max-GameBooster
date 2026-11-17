package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MatchRecap
import com.example.service.BlockedCallRecord
import com.example.service.GamingVolumeLockManager
import com.example.service.HardcoreCallBlockerManager
import com.example.service.HardcoreModeState
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.util.tr

/**
 * Compact card embedded in Launcher Options (PreLaunchBoosterPopup) for:
 * 1. Gaming Volume Lock (AudioManager volume snap-back + compact non-blocking notice banner)
 * 2. Hardcore Mode (CallScreeningService auto-reject calls + Post-Match Summary missed call log)
 */
@Composable
fun LauncherVolumeAndHardcoreOptionsCard(
    gamingVolumeLockEnabled: Boolean,
    gamingVolumeLockPercent: Int,
    hardcoreModeEnabled: Boolean,
    blockedCallsCount: Int,
    onToggleVolumeLock: (Boolean) -> Unit,
    onChangeVolumeLockPercent: (Int) -> Unit,
    onToggleHardcoreMode: (Boolean) -> Unit,
    onOpenPostMatchSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var roleCheckTick by remember { mutableStateOf(0) }
    val isRoleHeld = remember(hardcoreModeEnabled, roleCheckTick) {
        HardcoreCallBlockerManager.isCallScreeningRoleHeld(context)
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        roleCheckTick++
        if (result.resultCode == Activity.RESULT_OK) {
            onToggleHardcoreMode(true)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("prelaunch_volume_and_hardcore_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 1. Gaming Volume Lock (AudioManager)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleVolumeLock(!gamingVolumeLockEnabled) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (gamingVolumeLockEnabled) HubBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = tr("Gaming Volume Lock ($gamingVolumeLockPercent%)", "Bloqueo de Volumen Gamer ($gamingVolumeLockPercent%)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tr(
                                "Restores volume if the volume down button is accidentally pressed during gameplay",
                                "Fuerza el volumen a su nivel si presionas bajar volumen por accidente"
                            ),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = gamingVolumeLockEnabled,
                    onCheckedChange = { onToggleVolumeLock(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HubBlue
                    ),
                    modifier = Modifier.testTag("prelaunch_volume_lock_switch")
                )
            }

            if (gamingVolumeLockEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(50, 65, 80, 90, 100).forEach { pct ->
                        val isSelected = gamingVolumeLockPercent == pct
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) HubBlue else Color(0xFF1E293B)
                                )
                                .clickable { onChangeVolumeLockPercent(pct) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                .testTag("prelaunch_vol_pct_$pct")
                        ) {
                            Text(
                                text = "$pct%",
                                color = if (isSelected) Color(0xFF04141F) else Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LaunchGreen.copy(alpha = 0.2f))
                            .border(1.dp, LaunchGreen, RoundedCornerShape(50))
                            .clickable {
                                GamingVolumeLockManager.simulateAccidentalVolumeDropAndSnapBack(context)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("prelaunch_test_volume_snapback_button")
                    ) {
                        Text(
                            text = tr("Test Restore", "Probar Restauración"),
                            color = LaunchGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 2. Hardcore Mode (CallScreeningService Auto-Reject Calls + Post-Match Summary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val nextVal = !hardcoreModeEnabled
                        onToggleHardcoreMode(nextVal)
                        if (nextVal && !isRoleHeld) {
                            val roleIntent = HardcoreCallBlockerManager.createCallScreeningRoleIntent(context)
                            if (roleIntent != null) {
                                try {
                                    roleRequestLauncher.launch(roleIntent)
                                } catch (_: Exception) {
                                    HardcoreCallBlockerManager.openCallScreeningSettingsFallback(context)
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = if (hardcoreModeEnabled) RecorderCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = tr("Hardcore Mode (Drop Calls)", "Modo Hardcore (Rechazar Llamadas)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tr(
                                "Uses CallScreeningService to reject calls before UI appears & logs to Session Summary",
                                "Usa CallScreeningService para rechazar llamadas al instante y guardarlas en el Resumen de Partida"
                            ),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = hardcoreModeEnabled,
                    onCheckedChange = { nextVal ->
                        onToggleHardcoreMode(nextVal)
                        if (nextVal && !isRoleHeld) {
                            val roleIntent = HardcoreCallBlockerManager.createCallScreeningRoleIntent(context)
                            if (roleIntent != null) {
                                try {
                                    roleRequestLauncher.launch(roleIntent)
                                } catch (_: Exception) {
                                    HardcoreCallBlockerManager.openCallScreeningSettingsFallback(context)
                                }
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RecorderCoral
                    ),
                    modifier = Modifier.testTag("prelaunch_hardcore_mode_switch")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0D1422))
                        .border(1.dp, HubBlue.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { onOpenPostMatchSummary() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("prelaunch_open_post_match_summary_button")
                ) {
                    Text(
                        text = tr(
                            "📋 Session Summary ($blockedCallsCount Missed Calls)",
                            "📋 Resumen de Partida ($blockedCallsCount Llamadas)"
                        ),
                        color = HubBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (!isRoleHeld) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(50))
                            .clickable {
                                val roleIntent = HardcoreCallBlockerManager.createCallScreeningRoleIntent(context)
                                if (roleIntent != null) {
                                    try {
                                        roleRequestLauncher.launch(roleIntent)
                                    } catch (_: Exception) {
                                        HardcoreCallBlockerManager.openCallScreeningSettingsFallback(context)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("prelaunch_grant_call_screening_button")
                    ) {
                        Text(
                            text = tr("Grant Call Screening Role", "Activar Rol de Filtro de Llamadas"),
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Post-Match Summary Modal Dialog:
 * Automatically pops up when returning to GMax after a Hardcore Mode match with blocked calls,
 * or opened from Launcher Options / Tweaks. Lists all auto-rejected calls with 1-tap Call Back.
 */
@Composable
fun PostMatchSummaryModalDialog(
    hardcoreState: HardcoreModeState,
    latestRecap: MatchRecap?,
    onCallBack: (String) -> Unit,
    onSimulateBlockedCall: () -> Unit = {},
    onClearCallsLog: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRoleHeld = remember(hardcoreState) {
        HardcoreCallBlockerManager.isCallScreeningRoleHeld(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.5f)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .heightIn(max = 600.dp)
                .testTag("post_match_summary_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RecorderCoral.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = RecorderCoral,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tr("Session Summary", "Resumen de Partida"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr(
                                    "Real Telecom Call Blocker & Session Telemetry",
                                    "Bloqueador Real de Llamadas Telecom y Telemetría"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .testTag("post_match_summary_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Match Telemetry Quick Pill Bar
                if (latestRecap != null && latestRecap.durationSeconds > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1422))
                            .border(1.dp, HubBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = latestRecap.gameTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = tr(
                                    "Avg ${latestRecap.averageFps.toInt()} FPS • Peak ${String.format("%.1f", latestRecap.maxTempC)}°C",
                                    "Prom ${latestRecap.averageFps.toInt()} FPS • Pico ${String.format("%.1f", latestRecap.maxTempC)}°C"
                                ),
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                        Text(
                            text = "${hardcoreState.blockedCalls.size} ${tr("Calls Dropped", "Llamadas Rechazadas")}",
                            color = RecorderCoral,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Missed / Rejected Calls List
                Text(
                    text = tr(
                        "REJECTED CALLS DURING MATCH (${hardcoreState.blockedCalls.size})",
                        "LLAMADAS RECHAZADAS EN PARTIDA (${hardcoreState.blockedCalls.size})"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.7.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hardcoreState.blockedCalls.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(
                                    "No incoming calls were rejected during your recent matches. When Hardcore Mode is active and Call Screening Role is granted, real incoming calls are silenced, rejected, and logged here.",
                                    "No se rechazaron llamadas reales en tus partidas recientes. Con Modo Hardcore y el Rol de Filtro de Llamadas activos, las llamadas entrantes reales se silencian, rechazan y registran aquí."
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        hardcoreState.blockedCalls.forEach { callRecord ->
                            BlockedCallRowItem(
                                record = callRecord,
                                onCallBack = { onCallBack(callRecord.phoneNumber) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRoleHeld) {
                        OutlinedButton(
                            onClick = {
                                HardcoreCallBlockerManager.openCallScreeningSettingsFallback(context)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("post_match_grant_role_button")
                        ) {
                            Text(
                                text = tr("Grant Call Screening Role", "Activar Filtro de Llamadas"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                        }
                    }

                    if (hardcoreState.blockedCalls.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearCallsLog,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("post_match_clear_calls_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = RecorderCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tr("Clear", "Limpiar"),
                                color = RecorderCoral,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HubBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_match_done_button")
                    ) {
                        Text(
                            text = tr("Done", "Listo"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedCallRowItem(
    record: BlockedCallRecord,
    onCallBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, RecorderCoral.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("blocked_call_row_${record.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.callerLabel,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${record.formattedTime}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${record.phoneNumber} • ${tr("Dropped during", "Rechazada en")} ${record.gameName}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Button(
            onClick = onCallBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = LaunchGreen,
                contentColor = Color(0xFF04141F)
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .height(34.dp)
                .testTag("call_back_button_${record.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call Back",
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = tr("Call Back", "Llamar"),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
