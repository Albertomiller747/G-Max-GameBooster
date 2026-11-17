package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.SquadMilestoneCatalog
import com.example.data.repository.SquadMilestoneTier
import com.example.data.repository.SquadPassSummaryState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.SolarGold
import com.example.util.DeviceFingerprintGenerator
import com.example.util.SquadReferralManager
import kotlin.math.cos
import kotlin.math.sin

private val ReferralAmberPill = Color(0xFFF59E0B)
private val ReferralObsidianSurface = Color(0xFF0D1526)
private val ReferralDeepPillBg = Color(0xFF080E1A)
private val ReferralPillBorder = Color(0xFF26354D)

@Composable
fun SquadDopamineNotificationBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLang = com.example.util.currentAppLanguage()
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF06281E),
                            Color(0xFF0A3628),
                            Color(0xFF082238)
                        )
                    )
                )
                .border(1.5.dp, LaunchGreen, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("squad_dopamine_toast_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LaunchGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = com.example.util.tr("Squadmate Connected", "Compañero Conectado"),
                        tint = LaunchGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.example.util.tr(
                            "SQUAD CODE ACTIVATED • REWARD UNLOCKED",
                            "CÓDIGO DE ESCUADRÓN ACTIVADO • RECOMPENSA DESBLOQUEADA"
                        ),
                        color = LaunchGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(message.orEmpty(), activeLang),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("dismiss_squad_dopamine_toast_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = com.example.util.tr("Dismiss notification", "Cerrar notificación"),
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardSquadPassBannerCard(
    squadState: SquadPassSummaryState,
    referrerUsername: String,
    onOpenSquadPassHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeLang = com.example.util.currentAppLanguage()

    CyberCard(
        borderColor = SolarGold.copy(alpha = 0.65f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_squad_pass_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SolarGold.copy(alpha = 0.16f))
                        .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = com.example.util.tr("Pro Guest Passes", "Pases de Invitado Pro"),
                        tint = SolarGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.example.util.tr(
                            "SQUAD INVITE • PRO GUEST PASS VAULT",
                            "INVITACIÓN DE ESCUADRÓN • BÓVEDA DE PASES PRO"
                        ),
                        color = SolarGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(
                            "You have ${squadState.guestPassesRemaining} Pro Guest Passes left to gift your squad",
                            activeLang
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SolarGold.copy(alpha = 0.16f))
                    .border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${squadState.validatedSquadmatesCount} ${com.example.util.tr("SQUAD", "ESCUADRÓN")}",
                    color = SolarGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = com.example.util.tr(
                "Every claimed pass gives your friend 14 Days of Full Pro Access + Starter Crosshair, and unlocks a permanent HUD module for you.",
                "Cada pase canjeado le da a tu amigo 14 Días de Acceso Pro + Mira Inicial, y desbloquea un módulo HUD permanente para ti."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    SquadReferralManager.shareGuestPass(
                        context = context,
                        referrerUsername = referrerUsername,
                        targetChannelPackage = "com.discord"
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5865F2),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_gift_discord_button")
            ) {
                Text("Discord", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Button(
                onClick = {
                    SquadReferralManager.shareGuestPass(
                        context = context,
                        referrerUsername = referrerUsername,
                        targetChannelPackage = "com.whatsapp"
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_gift_whatsapp_button")
            ) {
                Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Button(
                onClick = {
                    SquadReferralManager.shareGuestPass(
                        context = context,
                        referrerUsername = referrerUsername,
                        targetChannelPackage = null
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReferralAmberPill,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_gift_universal_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = com.example.util.tr("Share", "Compartir"), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(com.example.util.tr("Share", "Compartir"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            OutlinedButton(
                onClick = onOpenSquadPassHub,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("dashboard_open_squad_hub_button")
            ) {
                Text(
                    text = com.example.util.tr("Ladder", "Niveles"),
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PostMatchGuestPassPromptCard(
    squadState: SquadPassSummaryState,
    referrerUsername: String,
    gameTitle: String,
    onOpenSquadPassHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeLang = com.example.util.currentAppLanguage()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0E1D34),
                        Color(0xFF111A2E),
                        Color(0xFF0B242B)
                    )
                )
            )
            .border(1.dp, SolarGold.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("post_match_guest_pass_prompt")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = com.example.util.tr("Duo Guest Pass", "Pase de Escuadrón"),
                        tint = SolarGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(
                            "SQUAD DUO PASS • ${squadState.guestPassesRemaining} GUEST PASSES LEFT",
                            activeLang
                        ),
                        color = SolarGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "${com.example.util.tr("CODE", "CÓDIGO")}: ${squadState.myReferralCode}",
                    color = CyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = com.example.util.tr(
                    "Running with a duo? Send them a 14 day Pro Guest Pass and unlock the Pro HUD for yourself.",
                    "¿Juegas en dúo? Envíales un Pase de Invitado Pro de 14 días y desbloquea el HUD Pro para ti."
                ),
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = com.example.util.AppStrings.autoTranslateDynamic(
                    "You have ${squadState.guestPassesRemaining} Pro Guest Passes left to gift your squad. Every claimed pass unlocks a permanent HUD module for you.",
                    activeLang
                ),
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        SquadReferralManager.shareGuestPass(
                            context = context,
                            referrerUsername = referrerUsername,
                            targetChannelPackage = "com.discord",
                            gameTitle = gameTitle
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5865F2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_match_share_discord_button")
                ) {
                    Text("Discord", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = {
                        SquadReferralManager.shareGuestPass(
                            context = context,
                            referrerUsername = referrerUsername,
                            targetChannelPackage = "com.whatsapp",
                            gameTitle = gameTitle
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_match_share_whatsapp_button")
                ) {
                    Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = {
                        SquadReferralManager.shareGuestPass(
                            context = context,
                            referrerUsername = referrerUsername,
                            targetChannelPackage = null,
                            gameTitle = gameTitle
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReferralAmberPill,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_match_share_universal_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = com.example.util.tr("Share", "Compartir"), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(com.example.util.tr("Share", "Compartir"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        SquadReferralManager.copyGuestPassLinkToClipboard(context, referrerUsername)
                        onOpenSquadPassHub()
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("post_match_copy_pass_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = com.example.util.tr("Copy Code", "Copiar Código"), tint = CyberCyan, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun SquadPassHubContent(
    referrerUsername: String,
    referrerDisplayName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeLang = com.example.util.currentAppLanguage()
    val squadState by SquadReferralManager.state.collectAsState()
    var guestPassInput by remember { mutableStateOf("") }
    var showHowItWorksModal by remember { mutableStateOf(false) }
    var showCelebrationModal by remember { mutableStateOf(false) }
    val hwFingerprint = remember(context) {
        DeviceFingerprintGenerator.getDeviceFingerprintHash(context)
    }

    // Automatically surface the celebration popup when a new reward notification arrives
    LaunchedEffect(squadState.highDopamineToastMessage) {
        if (!squadState.highDopamineToastMessage.isNullOrBlank()) {
            showCelebrationModal = true
        }
    }

    if (showHowItWorksModal) {
        HowItWorksReferralDialog(
            onDismiss = { showHowItWorksModal = false }
        )
    }

    if (showCelebrationModal) {
        ReferralCelebrationModal(
            rewardDaysText = "+${if (squadState.totalStackingProDaysEarned > 0) squadState.totalStackingProDaysEarned else 14} ${com.example.util.tr("DAYS", "DÍAS")}",
            customMessage = squadState.highDopamineToastMessage,
            onDismiss = {
                showCelebrationModal = false
                if (!squadState.highDopamineToastMessage.isNullOrBlank()) {
                    SquadReferralManager.dismissDopamineToast()
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("squad_pass_hub_container"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Real-time dopamine notification banner if active
        if (!squadState.highDopamineToastMessage.isNullOrBlank()) {
            item {
                SquadDopamineNotificationBanner(
                    message = squadState.highDopamineToastMessage,
                    onDismiss = { SquadReferralManager.dismissDopamineToast() }
                )
            }
        }

        // 1. Top Hero Stats & Invite Card (Reference Top-Left Screen: "Invite Friends, Get Pro Free!")
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF111C32),
                                ReferralObsidianSurface
                            )
                        )
                    )
                    .border(1.dp, SolarGold.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Subtle top badge showing remaining passes + reward preview trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CyberCyan.copy(alpha = 0.14f))
                                .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${squadState.guestPassesRemaining}/${squadState.guestPassesMaxBatch} ${com.example.util.tr("LEFT", "RESTANTES")}",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(SolarGold.copy(alpha = 0.15f))
                                .border(1.dp, SolarGold.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .clickable { showCelebrationModal = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("squad_celebration_preview_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = SolarGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = com.example.util.tr("Preview Reward", "Ver Recompensa"),
                                    color = SolarGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = com.example.util.tr(
                            "Invite Friends, Get Pro Free!",
                            "¡Invita Amigos y Obtén Pro Gratis!"
                        ),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 27.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = com.example.util.tr(
                            "Let's invite your squad to experience G Max Gaming Hub and unlock free Pro rewards together.",
                            "Invita a tu escuadrón a probar G Max Gaming Hub y desbloqueen recompensas Pro gratis juntos."
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2-Column Stat Summary (Total Earned & Friends Referred)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(ReferralDeepPillBg)
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Stat: Total Earned
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SolarGold.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = SolarGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "+${squadState.totalStackingProDaysEarned}d",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = com.example.util.tr("Total Earned", "Total Ganado"),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(54.dp)
                                .background(Color(0xFF1E293B))
                        )

                        // Right Stat: Friends Referred
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberCyan.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${squadState.validatedSquadmatesCount}",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = com.example.util.tr("Friends Referred", "Amigos Invitados"),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Full-width Solar Gold Pill CTA Button ("Invite Friends ->")
                    Button(
                        onClick = {
                            SquadReferralManager.shareGuestPass(
                                context = context,
                                referrerUsername = referrerUsername,
                                targetChannelPackage = null
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReferralAmberPill,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("squad_hero_invite_button")
                    ) {
                        Text(
                            text = com.example.util.tr("Invite Friends", "Invitar Amigos"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "How does it work?" interactive link
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showHowItWorksModal = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("squad_how_it_works_button")
                    ) {
                        Text(
                            text = com.example.util.tr("How does it work?", "¿Cómo funciona?"),
                            color = ReferralAmberPill,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Illustrated Referral Code Share Card (Reference Center Screen: "Here's your referral code. Share it!")
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ReferralObsidianSurface)
                    .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Custom Cyber-Console Loot Bag, Pass Card & Gift Box Illustration
                    ReferralHeroIllustrationCanvas(
                        modifier = Modifier
                            .size(width = 210.dp, height = 145.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = com.example.util.tr(
                            "Here's your referral code. Share it!",
                            "¡Aquí tienes tu código de referido. Compártelo!"
                        ),
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = com.example.util.tr(
                            "Share it with your friends and unlock Pro rewards.",
                            "Compártelo con tus amigos y desbloquea recompensas Pro."
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Rounded Pill Referral Code Box with Inline Copy Icon Button (No URLs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(ReferralDeepPillBg)
                            .border(1.5.dp, ReferralPillBorder, RoundedCornerShape(50))
                            .padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = squadState.myReferralCode.ifBlank { "GMAX-SQUAD-0001" },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("squad_pass_deep_link_text")
                        )

                        IconButton(
                            onClick = {
                                SquadReferralManager.copyGuestPassLinkToClipboard(context, referrerUsername)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.14f))
                                .testTag("copy_guest_pass_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = com.example.util.tr("COPY CODE", "COPIAR"),
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Full-width Solar Gold "Share Code" Pill Button
                    Button(
                        onClick = {
                            SquadReferralManager.shareGuestPass(
                                context = context,
                                referrerUsername = referrerUsername,
                                targetChannelPackage = null
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReferralAmberPill,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("squad_share_code_pill_button")
                    ) {
                        Text(
                            text = com.example.util.tr("Share Code", "Compartir Código"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Direct Squad Channel Pill Row (Discord / WhatsApp / More)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                SquadReferralManager.shareGuestPass(
                                    context = context,
                                    referrerUsername = referrerUsername,
                                    targetChannelPackage = "com.discord"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5865F2),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("squad_hub_share_discord_button")
                        ) {
                            Text("Discord", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                SquadReferralManager.shareGuestPass(
                                    context = context,
                                    referrerUsername = referrerUsername,
                                    targetChannelPackage = "com.whatsapp"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("squad_hub_share_whatsapp_button")
                        ) {
                            Text("WhatsApp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                SquadReferralManager.shareGuestPass(
                                    context = context,
                                    referrerUsername = referrerUsername,
                                    targetChannelPackage = null
                                )
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("squad_hub_share_universal_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(com.example.util.tr("Share", "Compartir"), color = CyberCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "? How does it work?" trigger at the bottom of the code card
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showHowItWorksModal = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = ReferralAmberPill,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = com.example.util.tr("How does it work?", "¿Cómo funciona?"),
                            color = ReferralAmberPill,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Referral Reward Card (Reference Top-Right Card with Gift Box Illustration)
        item {
            ReferralRewardGiftCard(
                squadState = squadState,
                onClickHowItWorks = { showHowItWorksModal = true }
            )
        }

        // 4. Invitee Guest Pass Claim & Hardware Anti-Cheat Verification Gate
        item {
            CyberCard(borderColor = HubBlue.copy(alpha = 0.5f)) {
                SectionTitle(
                    title = com.example.util.tr("Guest Pass Claim & Verification", "Canje y Verificación de Pase de Invitado"),
                    subtitle = com.example.util.tr(
                        "Google Sign-In + Device bound (1 claim per physical device) • Unlocks on 15m focus play or 1 HUD match",
                        "Cuenta Google + Vinculado al dispositivo (1 canje por dispositivo físico) • Se desbloquea con 15m de juego o 1 partida HUD"
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Hardware Fingerprint Binding Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(ReferralDeepPillBg)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = com.example.util.tr("Hardware Fingerprint Bound", "Huella de Hardware Vinculada"),
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${com.example.util.tr("Hardware ID Bound", "ID de Hardware Vinculado")}: ${hwFingerprint.take(20)}...",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = com.example.util.tr("DEVICE VERIFIED", "DISPOSITIVO VERIFICADO"),
                        color = LaunchGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val myInvitee = squadState.myInviteeActivation
                if (myInvitee != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (myInvitee.isActivated) LaunchGreen.copy(alpha = 0.12f)
                                else SolarGold.copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                if (myInvitee.isActivated) LaunchGreen else SolarGold,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                            .testTag("invitee_activation_status_box")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (myInvitee.isActivated) {
                                        com.example.util.tr(
                                            "✓ VERIFIED CORE ACTION COMPLETE • 14 DAYS PRO ACTIVE",
                                            "✓ ACCIÓN VERIFICADA COMPLETA • 14 DÍAS PRO ACTIVOS"
                                        )
                                    } else {
                                        com.example.util.tr(
                                            "⏳ GUEST PASS CLAIMED • PENDING CORE ACTION",
                                            "⏳ PASE DE INVITADO CANJEADO • ACCIÓN PENDIENTE"
                                        )
                                    },
                                    color = if (myInvitee.isActivated) LaunchGreen else SolarGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = myInvitee.referrerCode,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = com.example.util.AppStrings.autoTranslateDynamic(
                                    "Focus Gameplay: ${myInvitee.boostedMinutesCompleted}/15 min   •   HUD Match: ${if (myInvitee.hudMatchCompleted) "Completed ✓" else "0/1 Match"}",
                                    activeLang
                                ),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (!myInvitee.isActivated) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = com.example.util.tr(
                                        "Starter Crosshair & HUD Theme is unlocked! Run GMax alongside a game for 15 minutes or finish 1 full match with the Floating HUD active to unlock your 14 Day Pro Guest Pass.",
                                        "¡Mira Inicial y Tema HUD desbloqueados! Usa GMax junto con un juego por 15 minutos o termina 1 partida completa con el HUD Flotante activo para activar tus 14 Días Pro."
                                    ),
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = guestPassInput,
                            onValueChange = { guestPassInput = it },
                            placeholder = {
                                Text(
                                    com.example.util.tr(
                                        "Enter Squad Referral Code (e.g. GMAX-SQUAD-1234)",
                                        "Ingresa el Código de Escuadrón (ej. GMAX-SQUAD-1234)"
                                    ),
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ReferralPillBorder,
                                focusedContainerColor = ReferralDeepPillBg,
                                unfocusedContainerColor = ReferralDeepPillBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("claim_guest_pass_input")
                        )

                        Button(
                            onClick = {
                                SquadReferralManager.claimGuestPassCode(
                                    context = context,
                                    rawCodeOrLink = guestPassInput,
                                    inviteeUsername = referrerUsername,
                                    inviteeDisplayName = referrerDisplayName
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = Color(0xFF06101E)
                            ),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                            modifier = Modifier.testTag("claim_guest_pass_button")
                        ) {
                            Text(com.example.util.tr("CLAIM PASS", "CANJEAR"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (!squadState.statusBannerMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(squadState.statusBannerMessage.orEmpty(), activeLang),
                        color = CyberCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("squad_pass_status_message")
                    )
                }
            }
        }

        // 5. Squad Milestone Ladder (1, 3, 5, 10+ Squadmates)
        item {
            CyberCard(borderColor = SolarGold.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(
                        title = com.example.util.tr("4 Step Squad Milestone Ladder", "Escalera de 4 Niveles de Escuadrón"),
                        subtitle = com.example.util.tr(
                            "Verified Core Action required: 15m focus play OR 1 full HUD match",
                            "Acción verificada requerida: 15 min de juego en enfoque O 1 partida completa con HUD"
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LaunchGreen.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${squadState.validatedSquadmatesCount} ${com.example.util.tr("VERIFIED", "VERIFICADOS")}",
                            color = LaunchGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SquadMilestoneCatalog.TIERS.forEach { tier ->
                        val isUnlocked = squadState.validatedSquadmatesCount >= tier.referralsNeeded
                        val progress = (squadState.validatedSquadmatesCount.toFloat() / tier.referralsNeeded.toFloat())
                            .coerceIn(0f, 1f)

                        MilestoneTierRowCard(
                            tier = tier,
                            validatedCount = squadState.validatedSquadmatesCount,
                            isUnlocked = isUnlocked,
                            progress = progress,
                            squadState = squadState,
                            onToggleModule = { enabled ->
                                when (tier.moduleKey) {
                                    SquadMilestoneCatalog.MODULE_CROSSHAIR_PACK ->
                                        SquadReferralManager.toggleEquipCrosshairPack(context, enabled)
                                    SquadMilestoneCatalog.MODULE_ADVANCED_HUD ->
                                        SquadReferralManager.toggleEquipAdvancedHud(context, enabled)
                                    SquadMilestoneCatalog.MODULE_CONSOLE_VANGUARD ->
                                        SquadReferralManager.toggleEquipConsoleVanguardBadge(context, enabled)
                                }
                            }
                        )
                    }
                }
            }
        }

        // 6. Connected Squad Roster
        if (squadState.squadRoster.isNotEmpty()) {
            item {
                CyberCard(borderColor = LaunchGreen.copy(alpha = 0.5f)) {
                    SectionTitle(
                        title = "${com.example.util.tr("Connected Squadmates", "Compañeros Conectados")} (${squadState.squadRoster.size})",
                        subtitle = com.example.util.tr(
                            "Live Firestore activation status for your invited squad",
                            "Estado de activación en vivo de Firestore para tu escuadrón invitado"
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        squadState.squadRoster.forEach { mate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0B1324))
                                    .border(
                                        1.dp,
                                        if (mate.isActivated) LaunchGreen.copy(alpha = 0.45f) else SolarGold.copy(alpha = 0.4f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "@${mate.inviteeUsername.removePrefix("@")}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = com.example.util.AppStrings.autoTranslateDynamic(
                                            if (mate.isActivated) {
                                                "Verified (${mate.activationTrigger}) • HW Bound"
                                            } else {
                                                "Pending Core Action (${mate.boostedMinutesCompleted}/15m focus play)"
                                            },
                                            activeLang
                                        ),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (mate.isActivated) LaunchGreen.copy(alpha = 0.16f)
                                            else SolarGold.copy(alpha = 0.16f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (mate.isActivated) com.example.util.tr("VERIFIED", "VERIFICADO") else com.example.util.tr("PENDING", "PENDIENTE"),
                                        color = if (mate.isActivated) LaunchGreen else SolarGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reference Top-Right "Referral Reward" Card with custom Gift Box vector art on the right edge.
 */
@Composable
private fun ReferralRewardGiftCard(
    squadState: SquadPassSummaryState? = null,
    onClickHowItWorks: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ReferralObsidianSurface)
            .border(1.dp, SolarGold.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .let { base ->
                if (onClickHowItWorks != null) base.clickable { onClickHowItWorks() } else base
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = com.example.util.tr("+14 Days Pro", "+14 Días Pro"),
                        color = SolarGold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = com.example.util.tr("Total", "Total"),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = com.example.util.tr("Referral Reward", "Recompensa por Referido"),
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = com.example.util.tr(
                        "Every time you successfully refer a friend to our app, they get 14 Days Full Pro + Starter Crosshair and you unlock permanent HUD modules.",
                        "Cada vez que refieras con éxito a un amigo, él recibe 14 Días Pro Completo + Mira Inicial y tú desbloqueas módulos HUD permanentes."
                    ),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right-edge Gift Box Vector Illustration (matches reference image's yellow/line-art gift box)
            ReferralGiftBoxCanvas(
                modifier = Modifier.size(width = 96.dp, height = 104.dp)
            )
        }
    }
}

/**
 * Reference Bottom-Left & Top-Right Modal: "What counts as a referral?" with 4-step vertical timeline
 * and the embedded Referral Reward card.
 */
@Composable
fun HowItWorksReferralDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0A101D))
                .border(1.5.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .padding(20.dp)
                .testTag("squad_how_it_works_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Back Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131E33))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = com.example.util.tr("Close", "Cerrar"),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SolarGold.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = com.example.util.tr("SQUAD PASS", "PASE DE ESCUADRÓN"),
                            color = SolarGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = com.example.util.tr(
                        "What counts as a referral?",
                        "¿Qué cuenta como un referido?"
                    ),
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 4-Step Vertical Timeline (matching reference image's glowing amber nodes + vertical line)
                ReferralTimelineStepRow(
                    title = com.example.util.tr("Share your referral code", "Comparte tu código de referido"),
                    description = com.example.util.tr(
                        "First, share your GMAX referral code with your friend or duo squadmate.",
                        "Primero, comparte tu código GMAX con tu amigo o compañero de escuadrón."
                    ),
                    isFilledNode = true,
                    showBottomLine = true
                )

                ReferralTimelineStepRow(
                    title = com.example.util.tr("Make your friend sign in", "Haz que tu amigo inicie sesión"),
                    description = com.example.util.tr(
                        "Second, make your friend sign in with Google and claim your code in Squad Pass.",
                        "Segundo, tu amigo inicia sesión con Google y canjea tu código en Pase de Escuadrón."
                    ),
                    isFilledNode = true,
                    showBottomLine = true
                )

                ReferralTimelineStepRow(
                    title = com.example.util.tr("Your friend completes 1 match or 15m focus session", "Tu amigo completa 1 partida o 15m de sesión"),
                    description = com.example.util.tr(
                        "Third, your friend runs a GMax focus session for 15 minutes or finishes 1 HUD match.",
                        "Tercero, tu amigo juega con GMax por 15 minutos o completa 1 partida con HUD."
                    ),
                    isFilledNode = false,
                    showBottomLine = true
                )

                ReferralTimelineStepRow(
                    title = com.example.util.tr("Finally, you both unlock Pro rewards", "Finalmente, ambos desbloquean recompensas Pro"),
                    description = com.example.util.tr(
                        "Finally, you both get 14 Days Full Pro access and permanent HUD modules for free.",
                        "Finalmente, ambos obtienen 14 Días de Acceso Pro Completo y módulos HUD permanentes gratis."
                    ),
                    isFilledNode = false,
                    showBottomLine = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Embedded Referral Reward Gift Card (matches top-right reference screen)
                ReferralRewardGiftCard()

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReferralAmberPill,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = com.example.util.tr("Close", "Cerrar"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferralTimelineStepRow(
    title: String,
    description: String,
    isFilledNode: Boolean,
    showBottomLine: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left Timeline Node + Connecting Vertical Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isFilledNode) ReferralAmberPill else Color(0xFF0A101D))
                    .border(2.dp, ReferralAmberPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isFilledNode) Color.White else ReferralAmberPill)
                )
            }
            if (showBottomLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(ReferralAmberPill.copy(alpha = 0.45f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showBottomLine) 20.dp else 4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Reference Bottom-Right Celebration Confetti Modal:
 * Radial geometric confetti burst + concentric rings around a central circular badge
 * ("You received +14 DAYS PRO ACCESS") and a full-width Solar Gold "Great, thank you! ✓" pill button.
 */
@Composable
fun ReferralCelebrationModal(
    rewardDaysText: String,
    customMessage: String?,
    onDismiss: () -> Unit
) {
    val activeLang = com.example.util.currentAppLanguage()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0F192D),
                            Color(0xFF090F1C)
                        )
                    )
                )
                .border(1.5.dp, SolarGold.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("squad_celebration_modal")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Confetti Burst + Center Circular Badge
                Box(
                    modifier = Modifier.size(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ReferralConfettiBurstCanvas(
                        modifier = Modifier.fillMaxSize()
                    )

                    // Central Elevated Circular Reward Badge
                    Box(
                        modifier = Modifier
                            .size(138.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131F38))
                            .border(2.dp, SolarGold.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = com.example.util.tr("You received", "Recibiste"),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = rewardDaysText,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = com.example.util.tr("PRO ACCESS", "ACCESO PRO"),
                                color = SolarGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (!customMessage.isNullOrBlank()) {
                        com.example.util.AppStrings.autoTranslateDynamic(customMessage, activeLang)
                    } else {
                        com.example.util.tr(
                            "Congratulations! You have earned +14 Days Pro and permanent Squad HUD rewards from referring your squad.",
                            "¡Felicidades! Has ganado +14 Días Pro y recompensas permanentes de HUD por referir a tu escuadrón."
                        )
                    },
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReferralAmberPill,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("squad_celebration_confirm_button")
                ) {
                    Text(
                        text = com.example.util.tr("Great, thank you!", "¡Genial, gracias!"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom Vector Illustration for the "Here's your referral code. Share it!" card,
 * inspired by the reference image's editorial line-art shopping bag + % coupon card + gift box,
 * styled for G Max Gaming Hub in crisp white/slate line art, Solar Gold, and Cyber Cyan.
 */
@Composable
private fun ReferralHeroIllustrationCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle background platform ellipse
        drawOval(
            color = Color(0xFF16243E),
            topLeft = Offset(w * 0.12f, h * 0.78f),
            size = Size(w * 0.76f, h * 0.16f)
        )

        // Tilted Pro Coupon / Pass Card protruding from the top-left of the bag
        rotate(degrees = -16f, pivot = Offset(w * 0.36f, h * 0.38f)) {
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(w * 0.20f, h * 0.08f),
                size = Size(w * 0.26f, h * 0.42f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawRoundRect(
                color = CyberCyan,
                topLeft = Offset(w * 0.20f, h * 0.08f),
                size = Size(w * 0.26f, h * 0.42f),
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 3f)
            )
            // Stripe & emblem inside card
            drawRect(
                color = SolarGold.copy(alpha = 0.85f),
                topLeft = Offset(w * 0.24f, h * 0.14f),
                size = Size(w * 0.06f, h * 0.30f)
            )
            drawCircle(
                color = CyberCyan,
                radius = 9f,
                center = Offset(w * 0.37f, h * 0.24f),
                style = Stroke(width = 2.5f)
            )
        }

        // Floating Gift Box on the top-right (held/presented beside the loot bag)
        val giftLeft = w * 0.54f
        val giftTop = h * 0.10f
        val giftW = w * 0.24f
        val giftH = h * 0.24f
        drawRoundRect(
            color = Color(0xFF162238),
            topLeft = Offset(giftLeft, giftTop),
            size = Size(giftW, giftH),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = SolarGold,
            topLeft = Offset(giftLeft, giftTop),
            size = Size(giftW, giftH),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3f)
        )
        // Gift cross ribbons
        drawLine(
            color = SolarGold,
            start = Offset(giftLeft + giftW / 2f, giftTop),
            end = Offset(giftLeft + giftW / 2f, giftTop + giftH),
            strokeWidth = 3f
        )
        drawLine(
            color = SolarGold,
            start = Offset(giftLeft, giftTop + giftH / 2f),
            end = Offset(giftLeft + giftW, giftTop + giftH / 2f),
            strokeWidth = 3f
        )

        // Main Loot Vault Bag (trapezoid body in crisp editorial style)
        val bagPath = Path().apply {
            moveTo(w * 0.24f, h * 0.36f)
            lineTo(w * 0.58f, h * 0.36f)
            lineTo(w * 0.62f, h * 0.86f)
            lineTo(w * 0.20f, h * 0.86f)
            close()
        }
        drawPath(path = bagPath, color = Color(0xFF1A2942))
        drawPath(path = bagPath, color = Color(0xFFE2E8F0), style = Stroke(width = 3f))

        // Side gusset of the bag
        val gussetPath = Path().apply {
            moveTo(w * 0.58f, h * 0.36f)
            lineTo(w * 0.66f, h * 0.40f)
            lineTo(w * 0.69f, h * 0.86f)
            lineTo(w * 0.62f, h * 0.86f)
            close()
        }
        drawPath(path = gussetPath, color = Color(0xFF0F172A))
        drawPath(path = gussetPath, color = Color(0xFF94A3B8), style = Stroke(width = 2.5f))

        // Bag arched handle & hanging % / Pro tag
        drawArc(
            color = SolarGold,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.33f, h * 0.24f),
            size = Size(w * 0.16f, h * 0.22f),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )

        // Hanging hexagon/badge tag on front of bag
        val tagCenter = Offset(w * 0.40f, h * 0.62f)
        drawRoundRect(
            color = SolarGold.copy(alpha = 0.2f),
            topLeft = Offset(tagCenter.x - 22f, tagCenter.y - 26f),
            size = Size(44f, 52f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = SolarGold,
            topLeft = Offset(tagCenter.x - 22f, tagCenter.y - 26f),
            size = Size(44f, 52f),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 2.5f)
        )
        // "%" symbol inside the tag
        drawLine(
            color = Color.White,
            start = Offset(tagCenter.x - 9f, tagCenter.y + 9f),
            end = Offset(tagCenter.x + 9f, tagCenter.y - 9f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        drawCircle(color = CyberCyan, radius = 3.5f, center = Offset(tagCenter.x - 6f, tagCenter.y - 6f))
        drawCircle(color = CyberCyan, radius = 3.5f, center = Offset(tagCenter.x + 6f, tagCenter.y + 6f))
    }
}

/**
 * Custom Vector Gift Box for the bottom-right corner of the "Referral Reward" card
 * (matches the top-right screen in the reference image).
 */
@Composable
private fun ReferralGiftBoxCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val boxLeft = w * 0.14f
        val boxTop = h * 0.36f
        val boxW = w * 0.78f
        val boxH = h * 0.58f

        // Solar Gold highlight block on the right half of the gift box (like the yellow fill in the reference)
        drawRoundRect(
            color = SolarGold.copy(alpha = 0.28f),
            topLeft = Offset(boxLeft + boxW * 0.35f, boxTop + 6f),
            size = Size(boxW * 0.65f, boxH - 6f),
            cornerRadius = CornerRadius(12f, 12f)
        )

        // Gift box main body outline
        drawRoundRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2.5f)
        )

        // Vertical & horizontal ribbons
        val midX = boxLeft + boxW * 0.5f
        val midY = boxTop + boxH * 0.45f
        drawLine(
            color = SolarGold,
            start = Offset(midX, boxTop),
            end = Offset(midX, boxTop + boxH),
            strokeWidth = 3f
        )
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(boxLeft, midY),
            end = Offset(boxLeft + boxW, midY),
            strokeWidth = 2f
        )

        // Dual ribbon bow loops on top of the gift box
        val leftBow = Path().apply {
            moveTo(midX, boxTop)
            cubicTo(
                midX - 28f, boxTop - 28f,
                midX - 36f, boxTop - 4f,
                midX, boxTop
            )
        }
        val rightBow = Path().apply {
            moveTo(midX, boxTop)
            cubicTo(
                midX + 28f, boxTop - 28f,
                midX + 36f, boxTop - 4f,
                midX, boxTop
            )
        }
        drawPath(path = leftBow, color = SolarGold, style = Stroke(width = 2.5f))
        drawPath(path = rightBow, color = SolarGold, style = Stroke(width = 2.5f))
    }
}

/**
 * Custom Confetti Burst & Concentric Rings Canvas for the Celebration Modal
 * (matches the bottom-right screen in the reference image).
 */
@Composable
private fun ReferralConfettiBurstCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f

        // Concentric thin rings around the central badge
        drawCircle(
            color = Color(0xFF1E293B),
            radius = maxR * 0.95f,
            center = center,
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color(0xFF1E293B),
            radius = maxR * 0.78f,
            center = center,
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color(0xFF1E293B),
            radius = maxR * 0.62f,
            center = center,
            style = Stroke(width = 1.5f)
        )

        val confettiColors = listOf(
            SolarGold,
            CyberCyan,
            LaunchGreen,
            ElectricCrimson,
            Color(0xFFA855F7),
            Color(0xFF38BDF8)
        )

        // 16 geometric confetti particles around the outer and middle rings
        for (i in 0 until 16) {
            val angleDeg = i * 22.5f
            val rad = Math.toRadians(angleDeg.toDouble())
            val ringFactor = if (i % 2 == 0) 0.84f else 0.70f
            val px = center.x + (maxR * ringFactor * cos(rad)).toFloat()
            val py = center.y + (maxR * ringFactor * sin(rad)).toFloat()
            val color = confettiColors[i % confettiColors.size]

            when (i % 3) {
                0 -> {
                    // Tilted confetti rectangle
                    rotate(degrees = angleDeg + 35f, pivot = Offset(px, py)) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(px - 8f, py - 5f),
                            size = Size(16f, 10f),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }
                1 -> {
                    // Confetti circle dot
                    drawCircle(
                        color = color,
                        radius = if (i % 4 == 1) 6.5f else 4.5f,
                        center = Offset(px, py)
                    )
                }
                else -> {
                    // Confetti streamer pennant triangle
                    rotate(degrees = angleDeg, pivot = Offset(px, py)) {
                        val tri = Path().apply {
                            moveTo(px, py - 10f)
                            lineTo(px + 9f, py + 8f)
                            lineTo(px - 9f, py + 8f)
                            close()
                        }
                        drawPath(path = tri, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneTierRowCard(
    tier: SquadMilestoneTier,
    validatedCount: Int,
    isUnlocked: Boolean,
    progress: Float,
    squadState: SquadPassSummaryState,
    onToggleModule: (Boolean) -> Unit
) {
    val activeLang = com.example.util.currentAppLanguage()
    val accentColor = when {
        isUnlocked -> LaunchGreen
        tier.tierNumber == 4 -> SolarGold
        else -> CyberCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B1324))
            .border(
                width = if (isUnlocked) 1.5.dp else 1.dp,
                color = if (isUnlocked) LaunchGreen else Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
            .testTag("milestone_tier_${tier.tierNumber}_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.VerifiedUser else Icons.Default.Lock,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.example.util.AppStrings.autoTranslateDynamic(tier.tierTagline, activeLang),
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isUnlocked) {
                            com.example.util.tr("UNLOCKED", "DESBLOQUEADO")
                        } else {
                            "${validatedCount.coerceAtMost(tier.referralsNeeded)}/${tier.referralsNeeded} ${com.example.util.tr("SQUAD", "ESCUADRÓN")}"
                        },
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = com.example.util.AppStrings.autoTranslateDynamic(tier.rewardTitle, activeLang),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = com.example.util.AppStrings.autoTranslateDynamic(tier.whyItWorks, activeLang),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = accentColor,
                trackColor = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            if (isUnlocked && tier.tierNumber in 1..3) {
                Spacer(modifier = Modifier.height(8.dp))
                val isChecked = when (tier.moduleKey) {
                    SquadMilestoneCatalog.MODULE_CROSSHAIR_PACK -> squadState.equippedCrosshairPack
                    SquadMilestoneCatalog.MODULE_ADVANCED_HUD -> squadState.equippedAdvancedHud
                    SquadMilestoneCatalog.MODULE_CONSOLE_VANGUARD -> squadState.equippedConsoleVanguardBadge
                    else -> true
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.tr("Equip Permanent Module Now", "Equipar Módulo Permanente Ahora"),
                        color = LaunchGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = isChecked,
                        onCheckedChange = onToggleModule,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LaunchGreen
                        )
                    )
                }
            }
        }
    }
}
