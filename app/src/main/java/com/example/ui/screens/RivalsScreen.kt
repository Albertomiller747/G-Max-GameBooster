package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.repository.RivalsPendingPassTauntOpportunity
import com.example.data.repository.RivalsRepository
import com.example.data.repository.RivalsSquad
import com.example.data.repository.RivalsSquadInvite
import com.example.data.repository.RivalsSquadMemberRank
import com.example.data.repository.UserAccountRepository
import com.example.util.RivalsManager
import com.example.util.tr
import com.example.viewmodel.GameBoosterViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RivalsScreen(
    viewModel: GameBoosterViewModel,
    onInspectPlayerProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val rivalsState by RivalsManager.state.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val cloudProfile by viewModel.cloudProfile.collectAsState()

    val currentUid = currentUser?.uid.orEmpty()
    val myUsername = cloudProfile?.username?.takeIf { it.isNotBlank() }
        ?: viewModel.getSavedCustomUsername().ifBlank { "player" }
    val myDisplayName = cloudProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: myUsername

    var createSquadName by rememberSaveable { mutableStateOf("") }
    var createSquadBio by rememberSaveable { mutableStateOf("") }
    var squadCodeInput by rememberSaveable { mutableStateOf("") }
    var searchUsernameQuery by rememberSaveable { mutableStateOf("") }

    var showSquadManagementDialog by rememberSaveable { mutableStateOf(false) }
    var showEditSquadDialog by rememberSaveable { mutableStateOf(false) }
    var editSquadNameInput by rememberSaveable { mutableStateOf("") }
    var editSquadBioInput by rememberSaveable { mutableStateOf("") }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }

    // Live countdown refresh every 30 seconds for the Monthly UTC Gamer Score Reset
    LaunchedEffect(Unit) {
        while (true) {
            RivalsManager.refreshSeasonResetCountdown(context)
            delay(30_000L)
        }
    }

    // Sync real-time Firestore listeners when authenticated
    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank()) {
            RivalsManager.syncAuthenticatedUser(context, currentUid)
        }
    }

    val activeSquad = rivalsState.activeSquad
    val isSquadLeader = activeSquad != null && activeSquad.leaderUid == currentUid

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("rivals_screen_root"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // 1. Duolingo-Style Centered Top Header (Squad Name + Bio + Orange Countdown + Divider)
            item {
                DuolingoStyleRivalsHeader(
                    activeSquad = activeSquad,
                    resetCountdown = rivalsState.formattedResetCountdown.ifBlank {
                        RivalsRepository.formatRemainingResetCountdown()
                    },
                    onOpenSquadManagement = {
                        showSquadManagementDialog = true
                    }
                )
            }

            // Status / Feedback Banner
            if (!rivalsState.statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("rivals_status_banner"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (rivalsState.isErrorStatus) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        border = BorderStroke(
                            1.dp,
                            if (rivalsState.isErrorStatus) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = rivalsState.statusMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (rivalsState.isErrorStatus) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { RivalsManager.clearStatusMessage() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = tr("Dismiss", "Cerrar"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Google Sign-In Gate Card if unauthenticated
            if (currentUser == null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("rivals_sign_in_gate_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = tr(
                                        "Sign In with Google for Real-Time Rivals",
                                        "Inicia sesión con Google para Rivals en Tiempo Real"
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = tr(
                                    "Connect your Google account to create or join a 5-player Rivals Squad, compete for the 1st Place Crown, and send one-click taunts when you pass friends.",
                                    "Conecta tu cuenta de Google para crear o unirte a un Escuadrón Rivals de 5 jugadores, competir por la Corona del 1.º Lugar y enviar burlas de un toque al superar a tus amigos."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    UserAccountRepository.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                                            if (!uid.isNullOrBlank()) {
                                                RivalsManager.syncAuthenticatedUser(context, uid)
                                            }
                                        },
                                        onAuthError = {},
                                        scope = scope
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("rivals_google_sign_in_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = tr("Sign In with Google", "Iniciar sesión con Google"),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // 3. WHEN USER IS IN A SQUAD:
            // Render the clean Duolingo-Style 5-Rank Leaderboard List front and center!
            if (activeSquad != null) {
                // One-Click Pre-Set Taunts Banner (when user officially passed a squad member)
                item {
                    val myMemberRank = rivalsState.leaderboard.firstOrNull { it.userId == currentUid }
                    val effectiveTauntOpportunities = remember(
                        rivalsState.pendingPassTauntOpportunities,
                        rivalsState.leaderboard,
                        currentUid,
                        activeSquad.id
                    ) {
                        val list = rivalsState.pendingPassTauntOpportunities.toMutableList()
                        if (myMemberRank != null) {
                            rivalsState.leaderboard
                                .filter { it.userId != currentUid && it.rankPosition > myMemberRank.rankPosition }
                                .forEach { passedMate ->
                                    val key = "${activeSquad.id}_${currentUid}_passed_${passedMate.userId}_${RivalsRepository.currentUtcSeasonMonthKey()}"
                                    if (list.none { it.passedMemberUid == passedMate.userId }) {
                                        list.add(
                                            RivalsPendingPassTauntOpportunity(
                                                passEventKey = key,
                                                squadId = activeSquad.id,
                                                passedMemberUid = passedMate.userId,
                                                passedMemberUsername = passedMate.username,
                                                passedMemberDisplayName = passedMate.displayName.ifBlank { passedMate.username },
                                                newMyRankLabel = myMemberRank.rankLabel
                                            )
                                        )
                                    }
                                }
                        }
                        list
                    }

                    if (effectiveTauntOpportunities.isNotEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            RivalsOneClickTauntCard(
                                opportunities = effectiveTauntOpportunities,
                                onSendPresetTaunt = { opportunity, presetTaunt ->
                                    RivalsManager.sendOneClickPresetTaunt(
                                        context = context,
                                        opportunity = opportunity,
                                        senderUsername = myUsername,
                                        senderDisplayName = myDisplayName,
                                        presetTaunt = presetTaunt
                                    )
                                }
                            )
                        }
                    }
                }

                // Duolingo-Style 5-Rank Leaderboard List (Ranks 1st, 2nd, 3rd, 4th, 5th)
                item {
                    DuolingoStyleFiveRankLeaderboardList(
                        leaderboard = rivalsState.leaderboard,
                        currentUid = currentUid,
                        onMemberClicked = { member ->
                            if (member.username.isNotBlank()) {
                                onInspectPlayerProfile(member.username)
                            }
                        },
                        onInviteSlotClicked = {
                            showSquadManagementDialog = true
                        }
                    )
                }

                // Recent Squad Taunts Feed (compact footer card if any taunts received)
                if (rivalsState.recentIncomingTaunts.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("rivals_incoming_taunts_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = tr("Recent Squad Taunts", "Burlas Recientes del Escuadrón"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                rivalsState.recentIncomingTaunts.take(4).forEach { taunt ->
                                    val sender = taunt.senderDisplayName.ifBlank { taunt.senderUsername }
                                    Text(
                                        text = "• " + RivalsRepository.buildTauntReceivedNotificationText(
                                            friendName = sender,
                                            preTaunt = taunt.tauntText
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 4. WHEN USER IS NOT IN A SQUAD:
                // (a) Squad Code Entry Section (disappears once user joins a squad, reappears if they leave)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("rivals_squad_code_section"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = tr("Join with Squad Code", "Unirse con Código de Escuadrón"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tr(
                                            "Enter a friend's Squad Code to join their Rivals Squad automatically.",
                                            "Ingresa el Código de Escuadrón de un amigo para unirte automáticamente."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = squadCodeInput,
                                onValueChange = { squadCodeInput = it.uppercase(Locale.US).take(24) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rivals_squad_code_input"),
                                singleLine = true,
                                label = {
                                    Text(tr("Squad Code (e.g. RIVALS-APEX-1234)", "Código de Escuadrón (ej. RIVALS-APEX-1234)"))
                                }
                            )

                            Button(
                                onClick = {
                                    RivalsManager.joinSquadByCode(
                                        context = context,
                                        rawCode = squadCodeInput
                                    ) { res ->
                                        if (res.isSuccess) {
                                            squadCodeInput = ""
                                        }
                                    }
                                },
                                enabled = squadCodeInput.trim().length >= 8 && !rivalsState.isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("rivals_join_by_code_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Join Squad Automatically", "Unirse al Escuadrón Automáticamente"),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // (b) Pending Squad Invites (from Username Search — requires user acceptance)
                if (rivalsState.incomingInvites.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("rivals_pending_invites_section"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFF9600).copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = tr("Squad Invitations (Accept Required)", "Invitaciones de Escuadrón (Requiere Aceptar)"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF9600)
                                )
                                rivalsState.incomingInvites.forEach { invite ->
                                    RivalsInviteItemRow(
                                        invite = invite,
                                        onAccept = {
                                            RivalsManager.acceptSquadInvite(context, invite.id)
                                        },
                                        onDecline = {
                                            RivalsManager.declineSquadInvite(context, invite.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // (c) Create a Squad Card (Squad Name + 150-char Bio + Unity Rewarded Ad Gate)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("rivals_create_squad_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = tr("Create a Squad", "Crear un Escuadrón"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tr(
                                            "Set your Squad Name and a 150-character Bio. Watch a short Unity Rewarded Ad to launch your 5-player Squad.",
                                            "Configura el Nombre del Escuadrón y una Biografía de 150 caracteres. Mira un anuncio recompensado de Unity para crear tu Escuadrón de 5 jugadores."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = createSquadName,
                                onValueChange = {
                                    if (it.length <= RivalsRepository.MAX_SQUAD_NAME_LENGTH) {
                                        createSquadName = it
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rivals_squad_name_input"),
                                singleLine = true,
                                label = {
                                    Text(tr("Squad Name", "Nombre del Escuadrón"))
                                },
                                supportingText = {
                                    Text("${createSquadName.length} / ${RivalsRepository.MAX_SQUAD_NAME_LENGTH}")
                                }
                            )

                            OutlinedTextField(
                                value = createSquadBio,
                                onValueChange = {
                                    if (it.length <= RivalsRepository.MAX_SQUAD_BIO_LENGTH) {
                                        createSquadBio = it
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rivals_squad_bio_input"),
                                minLines = 2,
                                maxLines = 4,
                                label = {
                                    Text(tr("Squad Bio (Max 150 Characters)", "Biografía del Escuadrón (Máx. 150 Caracteres)"))
                                },
                                supportingText = {
                                    Text(
                                        text = "${createSquadBio.length} / ${RivalsRepository.MAX_SQUAD_BIO_LENGTH}",
                                        modifier = Modifier.testTag("rivals_squad_bio_counter")
                                    )
                                }
                            )

                            Button(
                                onClick = {
                                    RivalsManager.createSquadAfterRewardedAd(
                                        context = context,
                                        squadName = createSquadName,
                                        bio = createSquadBio,
                                        leaderUsername = myUsername,
                                        leaderDisplayName = myDisplayName
                                    ) { res ->
                                        if (res.isSuccess) {
                                            createSquadName = ""
                                            createSquadBio = ""
                                        }
                                    }
                                },
                                enabled = createSquadName.trim().isNotEmpty() &&
                                    createSquadBio.length <= RivalsRepository.MAX_SQUAD_BIO_LENGTH &&
                                    !rivalsState.isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("rivals_create_squad_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Watch Rewarded Ad & Create Squad", "Ver Anuncio y Crear Escuadrón"),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Top-Action Squad Management Modal Dialog (Squad Code, Invite by Username, Edit Name/Bio, Leave/Delete)
    if (showSquadManagementDialog && activeSquad != null) {
        RivalsSquadManagementDialog(
            squad = activeSquad,
            isLeader = isSquadLeader,
            searchQuery = searchUsernameQuery,
            onSearchQueryChanged = { q ->
                searchUsernameQuery = q
                RivalsManager.searchPlayersToInvite(context, q)
            },
            isSearching = rivalsState.isSearchingPlayers,
            candidates = rivalsState.searchCandidates,
            onInviteUsername = { targetUser ->
                RivalsManager.invitePlayerByUsernameAsLeader(
                    context = context,
                    squadId = activeSquad.id,
                    targetUsername = targetUser,
                    inviterUsername = myUsername,
                    inviterDisplayName = myDisplayName
                ) { res ->
                    if (res.isSuccess) {
                        searchUsernameQuery = ""
                    }
                }
            },
            onCopyCode = {
                RivalsManager.copySquadCodeToClipboard(context, activeSquad.squadCode)
            },
            onShareCode = {
                RivalsManager.shareSquadCode(context, activeSquad.name, activeSquad.squadCode)
            },
            onEditClicked = {
                editSquadNameInput = activeSquad.name
                editSquadBioInput = activeSquad.bio
                showSquadManagementDialog = false
                showEditSquadDialog = true
            },
            onDeleteClicked = {
                showSquadManagementDialog = false
                showDeleteConfirmDialog = true
            },
            onLeaveClicked = {
                showSquadManagementDialog = false
                RivalsManager.leaveSquad(context, activeSquad.id)
            },
            onDismiss = {
                showSquadManagementDialog = false
            }
        )
    }

    // Leader Real-Time Edit Squad Name & 150-Char Bio Dialog
    if (showEditSquadDialog && activeSquad != null && isSquadLeader) {
        AlertDialog(
            onDismissRequest = { showEditSquadDialog = false },
            title = {
                Text(
                    text = tr("Edit Squad Name & Bio", "Editar Nombre y Biografía del Escuadrón"),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editSquadNameInput,
                        onValueChange = {
                            if (it.length <= RivalsRepository.MAX_SQUAD_NAME_LENGTH) {
                                editSquadNameInput = it
                            }
                        },
                        singleLine = true,
                        label = { Text(tr("Squad Name", "Nombre del Escuadrón")) },
                        supportingText = {
                            Text("${editSquadNameInput.length} / ${RivalsRepository.MAX_SQUAD_NAME_LENGTH}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rivals_edit_squad_name_input")
                    )
                    OutlinedTextField(
                        value = editSquadBioInput,
                        onValueChange = {
                            if (it.length <= RivalsRepository.MAX_SQUAD_BIO_LENGTH) {
                                editSquadBioInput = it
                            }
                        },
                        minLines = 2,
                        maxLines = 4,
                        label = { Text(tr("Squad Bio (Max 150 chars)", "Biografía (Máx. 150 caracteres)")) },
                        supportingText = {
                            Text("${editSquadBioInput.length} / ${RivalsRepository.MAX_SQUAD_BIO_LENGTH}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rivals_edit_squad_bio_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        RivalsManager.updateSquadDetailsAsLeader(
                            context = context,
                            squadId = activeSquad.id,
                            newName = editSquadNameInput,
                            newBio = editSquadBioInput
                        ) { res ->
                            if (res.isSuccess) {
                                showEditSquadDialog = false
                            }
                        }
                    },
                    enabled = editSquadNameInput.trim().isNotEmpty() &&
                        editSquadBioInput.length <= RivalsRepository.MAX_SQUAD_BIO_LENGTH,
                    modifier = Modifier.testTag("rivals_confirm_edit_squad_button")
                ) {
                    Text(tr("Save Changes", "Guardar Cambios"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSquadDialog = false }) {
                    Text(tr("Cancel", "Cancelar"))
                }
            }
        )
    }

    // Leader Delete Squad Confirmation Dialog
    if (showDeleteConfirmDialog && activeSquad != null && isSquadLeader) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = tr("Delete Squad from Database?", "¿Eliminar Escuadrón de la Base de Datos?"),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Deleting '${activeSquad.name}' will permanently remove the Squad and its Squad Code from Firebase so all members can join or create a new Squad.",
                        "Eliminar '${activeSquad.name}' borrará permanentemente el Escuadrón y su Código de Firebase para que todos los miembros puedan unirse o crear otro."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        RivalsManager.deleteSquadAsLeader(context, activeSquad.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("rivals_confirm_delete_squad_button")
                ) {
                    Text(tr("Delete Squad", "Eliminar Escuadrón"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(tr("Cancel", "Cancelar"))
                }
            }
        )
    }
}

/**
 * Duolingo-Inspired Centered Leaderboard Header:
 * - Centered bold Squad Title (like "French 2 Period 4" in the Duolingo screenshot)
 * - Optional Squad Bio subtitle
 * - Centered bold orange countdown ("7 days" / time left until Monthly 00:00 UTC Reset)
 * - Top-right Squad Management / Invite button when in a Squad
 * - Clean full-width bottom divider
 */
@Composable
private fun DuolingoStyleRivalsHeader(
    activeSquad: RivalsSquad?,
    resetCountdown: String,
    onOpenSquadManagement: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("rivals_squad_dashboard_header")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = activeSquad?.name?.ifBlank { tr("Rivals Squad", "Escuadrón Rivals") }
                        ?: tr("Rivals Leaderboard", "Clasificación Rivals"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("rivals_active_squad_name")
                )

                if (activeSquad != null && activeSquad.bio.isNotBlank()) {
                    Text(
                        text = activeSquad.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("rivals_active_squad_bio")
                    )
                }

                // Duolingo-style bold orange countdown ("7 days")
                Text(
                    text = resetCountdown,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF9600),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("rivals_monthly_reset_countdown")
                )
            }

            if (activeSquad != null) {
                Surface(
                    onClick = onOpenSquadManagement,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .testTag("rivals_open_squad_management_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = tr("Manage Squad & Invite", "Gestionar Escuadrón e Invitar"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 1.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    }
}

/**
 * Duolingo-Style 5-Rank Squad Leaderboard List (`1st`, `2nd`, `3rd`, `4th`, `5th`).
 * - Ranks 1, 2, 3 display circular Gold, Silver, and Bronze ribbon medal badges.
 * - Ranks 4, 5 display bold rank numbers (`4`, `5`).
 * - The player at `1st` has a Golden Crown rendered on top of their profile picture in the Squad dashboard.
 * - Right-aligned Gamer Score (matching Duolingo's "40 XP" layout).
 */
@Composable
private fun DuolingoStyleFiveRankLeaderboardList(
    leaderboard: List<RivalsSquadMemberRank>,
    currentUid: String,
    onMemberClicked: (RivalsSquadMemberRank) -> Unit,
    onInviteSlotClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("rivals_leaderboard_card")
    ) {
        RivalsRepository.SQUAD_RANK_LABELS.forEachIndexed { index, rankLabel ->
            val rankNumber = index + 1
            val member = leaderboard.firstOrNull { it.rankPosition == rankNumber }
            DuolingoStyleRankRow(
                rankPosition = rankNumber,
                rankLabel = rankLabel,
                member = member,
                isCurrentUser = member?.userId == currentUid && currentUid.isNotBlank(),
                onClick = {
                    if (member != null) {
                        onMemberClicked(member)
                    } else {
                        onInviteSlotClicked()
                    }
                }
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun DuolingoStyleRankRow(
    rankPosition: Int,
    rankLabel: String,
    member: RivalsSquadMemberRank?,
    isCurrentUser: Boolean,
    onClick: () -> Unit
) {
    val isFirstPlace = rankPosition == 1 && member != null
    val rowBackground = if (isCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val avatarPalette = listOf(
        Color(0xFF1CB0F6), // Duolingo Blue
        Color(0xFFCE82FF), // Duolingo Purple
        Color(0xFFFF4B4B), // Duolingo Coral Red
        Color(0xFF58CC02), // Duolingo Green
        Color(0xFFFF9600)  // Duolingo Orange
    )
    val avatarColor = avatarPalette[(rankPosition - 1).coerceIn(0, avatarPalette.lastIndex)]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("rivals_rank_row_$rankLabel"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left Column: Duolingo Ribbon Medal for 1, 2, 3 or Bold Number for 4, 5
        Box(
            modifier = Modifier.width(42.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rankPosition in 1..3) {
                DuolingoRankMedalBadge(
                    rankNumber = rankPosition,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Text(
                    text = rankPosition.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        if (member != null) {
            // 2. Avatar Column with 1st-Place Golden Crown on top of the profile picture
            Box(
                modifier = Modifier.size(width = 52.dp, height = 58.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = avatarColor,
                    border = BorderStroke(
                        width = if (isFirstPlace) 2.dp else 1.5.dp,
                        color = if (isFirstPlace) Color(0xFFFFC800) else avatarColor.copy(alpha = 0.85f)
                    )
                ) {
                    if (member.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = member.photoUrl,
                            contentDescription = member.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = member.displayName.ifBlank { member.username }
                                    .take(1)
                                    .uppercase(Locale.US)
                                    .ifBlank { "G" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                // Golden Crown strictly on top of the 1st-place player's profile picture in Squad Dashboard
                if (isFirstPlace && member.hasFirstPlaceCrown) {
                    RivalsCrownIcon(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-2).dp)
                            .testTag("rivals_first_place_crown")
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 3. Player Name Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.displayName.ifBlank { member.username },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (member.isSquadLeader) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = tr("LEADER", "LÍDER"),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 4. Right Column: Gamer Score (matching Duolingo's "40 XP" right-aligned column)
            Text(
                text = "${NumberFormat.getIntegerInstance(Locale.US).format(member.gamerscore)} XP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.semiboldOrBold(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Open Squad Slot (Rank 2..5 not yet filled)
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = tr("Invite Friend", "Invitar Amigo"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("Invite Squadmate", "Invitar Compañero"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tr("Tap to share Squad Code or search username", "Toca para compartir Código o buscar usuario"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "0 XP",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun FontWeight.Companion.semiboldOrBold(): FontWeight = FontWeight.SemiBold

/**
 * Custom Duolingo-Style Circular Ribbon Medal Badge for Ranks 1 (Gold), 2 (Silver), and 3 (Bronze).
 */
@Composable
private fun DuolingoRankMedalBadge(
    rankNumber: Int,
    modifier: Modifier = Modifier
) {
    val (outerColor, innerColor, textColor) = when (rankNumber) {
        1 -> Triple(Color(0xFFFFB100), Color(0xFFFFD900), Color(0xFFB86E00))
        2 -> Triple(Color(0xFF99B4C6), Color(0xFFC6D8E4), Color(0xFF5A7586))
        else -> Triple(Color(0xFFC98349), Color(0xFFE3A26B), Color(0xFF7D491E))
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val medalRadius = w * 0.42f
            val center = Offset(w * 0.5f, medalRadius + h * 0.04f)

            // Left & right bottom ribbons under the medal circle
            val ribbonPath = Path().apply {
                moveTo(w * 0.24f, center.y + medalRadius * 0.55f)
                lineTo(w * 0.18f, h * 0.96f)
                lineTo(w * 0.36f, h * 0.85f)
                lineTo(w * 0.46f, h * 0.96f)
                lineTo(w * 0.46f, center.y + medalRadius * 0.65f)
                close()

                moveTo(w * 0.76f, center.y + medalRadius * 0.55f)
                lineTo(w * 0.82f, h * 0.96f)
                lineTo(w * 0.64f, h * 0.85f)
                lineTo(w * 0.54f, h * 0.96f)
                lineTo(w * 0.54f, center.y + medalRadius * 0.65f)
                close()
            }
            drawPath(path = ribbonPath, color = outerColor)

            // Outer medal ring
            drawCircle(
                color = outerColor,
                radius = medalRadius,
                center = center
            )
            // Inner medal face
            drawCircle(
                color = innerColor,
                radius = medalRadius * 0.76f,
                center = center
            )
        }

        Text(
            text = rankNumber.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = textColor,
            modifier = Modifier.offset(y = 5.dp)
        )
    }
}

/**
 * Squad Management Dialog (opened via the top-right button on the Duolingo-style Rivals page):
 * - Share or copy Squad Code (`RIVALS-...`)
 * - Leader: Invite players by searching `@username` in Firestore
 * - Leader: Edit Squad Name & 150-char Bio in real time, or Delete Squad from Firebase
 * - Member: Leave Squad
 */
@Composable
private fun RivalsSquadManagementDialog(
    squad: RivalsSquad,
    isLeader: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    candidates: List<com.example.data.repository.UsernameClaim>,
    onInviteUsername: (String) -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onLeaveClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .testTag("rivals_squad_management_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = squad.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${squad.memberUids.size} / ${RivalsRepository.MAX_SQUAD_MEMBERS} " +
                                tr("Squad Members", "Miembros del Escuadrón"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Close", "Cerrar"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Shareable Squad Code Bar (Instant Auto-Join)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("SQUAD CODE (INSTANT AUTO-JOIN)", "CÓDIGO DE ESCUADRÓN (UNIÓN AUTOMÁTICA)"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = squad.squadCode,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("rivals_active_squad_code_text")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onCopyCode,
                                modifier = Modifier.testTag("rivals_copy_squad_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = tr("Copy Squad Code", "Copiar Código"),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = onShareCode,
                                modifier = Modifier.testTag("rivals_share_squad_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = tr("Share Squad Code", "Compartir Código"),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Leader: Search & Invite Players by Username (when < 5 members)
                if (isLeader && squad.memberUids.size < RivalsRepository.MAX_SQUAD_MEMBERS) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = tr(
                            "Invite Players by Username (${squad.memberUids.size}/5 Slots)",
                            "Invitar Jugadores por Usuario (${squad.memberUids.size}/5 Cupos)"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tr(
                            "Players invited by username search must accept the request to join. Friends using your Squad Code join automatically.",
                            "Los jugadores invitados por búsqueda deben aceptar la solicitud. Los amigos con tu Código de Escuadrón se unen automáticamente."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rivals_search_username_input"),
                            singleLine = true,
                            label = { Text(tr("Search @username", "Buscar @usuario")) }
                        )
                        Button(
                            onClick = { onInviteUsername(searchQuery) },
                            enabled = searchQuery.trim().length >= 2,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("rivals_invite_username_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Invite", "Invitar"), fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.CenterHorizontally),
                            strokeWidth = 2.dp
                        )
                    } else if (candidates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            candidates.forEach { claim ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = claim.displayName.ifBlank { claim.username },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "@${claim.username} • ${NumberFormat.getIntegerInstance(Locale.US).format(claim.gamerscore)} XP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Button(
                                            onClick = { onInviteUsername(claim.username) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(tr("Invite", "Invitar"), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Leader Edit / Delete or Member Leave Actions
                if (isLeader) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEditClicked,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rivals_leader_edit_squad_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Edit Name & Bio", "Editar Nombre/Bio"), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDeleteClicked,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rivals_leader_delete_squad_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Delete Squad", "Eliminar"),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isLeader || squad.memberUids.size > 1) {
                    OutlinedButton(
                        onClick = onLeaveClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("rivals_leave_squad_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Leave Squad", "Salir del Escuadrón"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RivalsOneClickTauntCard(
    opportunities: List<RivalsPendingPassTauntOpportunity>,
    onSendPresetTaunt: (RivalsPendingPassTauntOpportunity, String) -> Unit
) {
    val activeOpportunity = opportunities.firstOrNull() ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rivals_one_click_taunt_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = tr(
                            "You Passed ${activeOpportunity.passedMemberDisplayName}! Send One-Click Taunt",
                            "¡Superaste a ${activeOpportunity.passedMemberDisplayName}! Envía una Burla de Un Toque"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = tr(
                            "Tap a preset taunt below to send immediately (1 taunt allowed per passing):",
                            "Toca una burla predeterminada para enviarla al instante (1 permitida por rebase):"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RivalsRepository.PRESET_TAUNTS.forEachIndexed { idx, preset ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSendPresetTaunt(activeOpportunity, preset)
                            }
                            .testTag("rivals_preset_taunt_chip_$idx"),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "\"$preset\"",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RivalsInviteItemRow(
    invite: RivalsSquadInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                    text = invite.squadName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tr(
                        "Invited by @${invite.inviterUsername}",
                        "Invitado por @${invite.inviterUsername}"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.testTag("rivals_decline_invite_${invite.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(tr("Decline", "Rechazar"), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.testTag("rivals_accept_invite_${invite.id}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tr("Accept", "Aceptar"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Custom Vector Golden Crown drawn above the 1st-place player's avatar in the Rivals Squad Dashboard.
 */
@Composable
fun RivalsCrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.82f)
            lineTo(w * 0.06f, h * 0.30f)
            lineTo(w * 0.32f, h * 0.54f)
            lineTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.68f, h * 0.54f)
            lineTo(w * 0.94f, h * 0.30f)
            lineTo(w * 0.88f, h * 0.82f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFEA00), Color(0xFFFFA000))
            )
        )
        drawPath(
            path = path,
            color = Color(0xFFFFF8E1),
            style = Stroke(width = 1.6f)
        )
        // Crown jewels
        drawCircle(
            color = Color(0xFFFF1744),
            radius = w * 0.065f,
            center = Offset(w * 0.50f, h * 0.58f)
        )
    }
}

/**
 * In-App Popup ("Invite a Squad") that appears when the user activates any Booster Mode 2 times,
 * OR records 2 times, OR activates an Overlay 2 times — strictly ONLY if the user does not
 * currently have a Squad.
 */
@Composable
fun InviteSquadInAppPopupDialog(
    onOpenRivalsTab: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("invite_squad_in_app_popup"),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RivalsCrownIcon(modifier = Modifier.size(28.dp))
                Text(
                    text = tr("Invite a Squad • Compete in Rivals!", "Invita a un Escuadrón • ¡Compite en Rivals!"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr(
                        "You're racking up Gamer Score! Create or join a 5-player Rivals Squad to compete with your friends in real time for the 1st Place Crown.",
                        "¡Estás acumulando Puntuación Gamer! Crea o únete a un Escuadrón Rivals de 5 jugadores para competir con tus amigos en tiempo real por la Corona del 1.º Lugar."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = tr(
                        "• Share your Squad Code for instant friend joins\n• Unlock One-Click Taunts when you pass rivals\n• Monthly leaderboard resets every month at 00:00 UTC",
                        "• Comparte tu Código de Escuadrón para unión instantánea\n• Desbloquea Burlas de Un Toque al superar rivales\n• El marcador se reinicia cada mes a las 00:00 UTC"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenRivalsTab,
                modifier = Modifier.testTag("invite_squad_popup_cta_button")
            ) {
                Text(
                    text = tr("Invite a Squad", "Invitar a un Escuadrón"),
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("invite_squad_popup_dismiss_button")
            ) {
                Text(
                    text = tr("Later", "Más tarde"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
