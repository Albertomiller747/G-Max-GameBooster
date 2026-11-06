package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.util.AdMobManager
import com.example.util.tr
import kotlinx.coroutines.delay

/**
 * Medium Native Advanced Ad Card styled to match G Max Gaming Hub's rounded cards
 * with a clear "Ad" / "Anuncio" badge and a mandatory 32.dp safety clearance buffer
 * from interactive sliders, buttons, and toggles.
 */
@Composable
fun GMaxNativeAdCard(
    placementTag: String,
    modifier: Modifier = Modifier,
    topBuffer: Dp = 32.dp,
    bottomBuffer: Dp = 24.dp,
    headlineEn: String = "G Max Pro Cooling Clip & Low-Latency Gamepad",
    headlineEs: String = "Clip de Enfriamiento Pro y Mando de Baja Latencia",
    bodyEn: String = "Keep thermal throttling at bay during 120 FPS sessions with zero-lag Hall Effect triggers.",
    bodyEs: String = "Evita el sobrecalentamiento durante sesiones a 120 FPS con gatillos Hall Effect sin latencia.",
    ctaEn: String = "Learn More",
    ctaEs: String = "Más Información"
) {
    val unitId = AdMobManager.nativeAdvancedAdUnitId
    val modeLabel = if (BuildConfig.DEBUG) {
        tr("Test Native Ad", "Anuncio Nativo de Prueba")
    } else {
        tr("Sponsored", "Patrocinado")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("native_ad_$placementTag")
    ) {
        if (topBuffer > 0.dp) {
            Spacer(modifier = Modifier.height(topBuffer))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = tr("Ad", "Anuncio"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "AdMob • ${unitId.takeLast(10)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(headlineEn, headlineEs),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tr(bodyEn, bodyEs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { /* Non-disruptive native CTA */ },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = tr(ctaEn, ctaEs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (bottomBuffer > 0.dp) {
            Spacer(modifier = Modifier.height(bottomBuffer))
        }
    }
}

/**
 * Anchored Adaptive Banner Ad for read-only screens or safe bottom containers
 * with a mandatory 32.dp top clearance from interactive controls.
 */
@Composable
fun GMaxAnchoredAdaptiveBanner(
    placementTag: String,
    modifier: Modifier = Modifier,
    topBuffer: Dp = 32.dp
) {
    val unitId = AdMobManager.bannerAdUnitId
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("adaptive_banner_$placementTag")
    ) {
        if (topBuffer > 0.dp) {
            Spacer(modifier = Modifier.height(topBuffer))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = tr("Ad", "Anuncio"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(
                                "Adaptive Banner • Ultra-Fast Fiber Gaming Ping",
                                "Banner Adaptativo • Fibra Gaming de Ultra Baja Latencia"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "AdMob Banner • ${unitId.takeLast(10)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Opt-in 24-Hour Rewarded Ad Card for the Controller Tab to unlock
 * Custom RGB Lightbar Mixer and Controller Input Screen Overlay.
 */
@Composable
fun GMaxProControllerRewardedUnlockCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unlockUntilMs by AdMobManager.proControllerUnlockUntilMs.collectAsState()
    val nowMs = System.currentTimeMillis()
    val isUnlocked = unlockUntilMs > nowMs
    val remainingHours = AdMobManager.remainingUnlockHours(nowMs)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rewarded_ad_controller_unlock_card"),
        shape = RoundedCornerShape(18.dp),
        color = if (isUnlocked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            if (isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isUnlocked) {
                                tr(
                                    "Custom RGB & Overlay Unlocked ($remainingHours h left)",
                                    "RGB Personalizado y Superposición Desbloqueados ($remainingHours h restantes)"
                                )
                            } else {
                                tr(
                                    "Unlock Custom RGB & Overlay (24 Hours)",
                                    "Desbloquear RGB Personalizado y Superposición (24 Horas)"
                                )
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isUnlocked) {
                                tr(
                                    "Full access to Custom RGB Lightbar Mixer and Floating Controller Input Overlay is active.",
                                    "Acceso completo activo al Mezclador de Luz RGB y la Superposición Flotante de Mando."
                                )
                            } else {
                                tr(
                                    "Watch a short ad to unlock Custom RGB & Overlay for 24 hours",
                                    "Mira un anuncio corto para desbloquear RGB Personalizado y Superposición por 24 horas"
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    AdMobManager.showRewardedAdForProController(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("watch_rewarded_ad_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isUnlocked) {
                        tr("Extend Unlock for 24 Hours (Watch Ad)", "Extender 24 Horas (Ver Anuncio)")
                    } else {
                        tr(
                            "Watch a short ad to unlock Custom RGB & Overlay for 24 hours",
                            "Mira un anuncio corto para desbloquear RGB y Superposición (24h)"
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Host Dialog for App Open, Post-Action (12m cap), and Opt-in Rewarded Ads.
 * Never shown over other apps or on tab switches.
 */
@Composable
fun AdMobFullScreenPresentationHost() {
    val activeAd by AdMobManager.activeFullScreenAd.collectAsState()
    val presentation = activeAd ?: return

    var remainingSeconds by remember(presentation) { mutableIntStateOf(presentation.countdownSeconds) }

    LaunchedEffect(presentation) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    Dialog(
        onDismissRequest = {
            if (remainingSeconds <= 0) {
                AdMobManager.dismissActiveFullScreenAd()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = remainingSeconds <= 0,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("admob_fullscreen_modal"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = tr("Ad • Google AdMob", "Anuncio • Google AdMob"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (remainingSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = tr("Reward in ${remainingSeconds}s", "Listo en ${remainingSeconds}s"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { AdMobManager.dismissActiveFullScreenAd() },
                            modifier = Modifier.testTag("close_fullscreen_ad_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Close Ad", "Cerrar Anuncio")
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(84.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = tr(presentation.titleEn, presentation.titleEs),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = tr(presentation.subtitleEn, presentation.subtitleEs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Unit ID: ${presentation.adUnitId}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Button(
                    onClick = { AdMobManager.dismissActiveFullScreenAd() },
                    enabled = remainingSeconds <= 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("continue_after_ad_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (remainingSeconds > 0) {
                            tr("Please wait ${remainingSeconds}s...", "Espera ${remainingSeconds}s...")
                        } else {
                            tr("Continue to G Max Gaming Hub", "Continuar a G Max Gaming Hub")
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
