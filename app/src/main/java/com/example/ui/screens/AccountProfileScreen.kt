package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.repository.UserAccountRepository
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.ui.theme.SolarGold
import com.example.util.PremadeProfileTag
import com.example.util.PremadeTagsCatalog
import com.example.util.ProfileTagCategory
import com.example.viewmodel.GameBoosterViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val DeveloperGold = Color(0xFFFFD700)
private val DeveloperAmber = Color(0xFFF59E0B)
private val DeveloperDeepBg = Color(0xFF261D05)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountProfileScreen(
    viewModel: GameBoosterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPlayStats()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by viewModel.currentFirebaseUser.collectAsState()
    val cloudProfile by viewModel.cloudProfile.collectAsState()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val userDateOfBirth by viewModel.userDateOfBirth.collectAsState()
    val userBio by viewModel.userBio.collectAsState()
    val customDisplayName by viewModel.customDisplayName.collectAsState()
    val customEmail by viewModel.customEmail.collectAsState()
    val selectedProfileTagIds by viewModel.selectedProfileTags.collectAsState()
    val isVerifiedDeveloper by viewModel.isVerifiedDeveloperAccount.collectAsState()
    val mostPlayedGames by viewModel.mostPlayedGames.collectAsState()
    val totalPlayTimeSummary by viewModel.totalPlayTimeSummary.collectAsState()
    val selectedTargetGame by viewModel.selectedTargetGame.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()

    var showDobDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var showTagPickerDialog by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val isSignedIn = currentUser != null || isVerifiedDeveloper
    val rawDisplayName = customDisplayName.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: cloudProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: if (isVerifiedDeveloper) PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME
        else if (currentUser != null) "G-Max Cloud Gamer" else "Guest Gamer"
    val displayName = PremadeTagsCatalog.sanitizeDisplayName(rawDisplayName, isVerifiedDeveloper)

    val emailText = currentUser?.email?.takeIf { it.isNotBlank() }
        ?: customEmail.takeIf { it.isNotBlank() }
        ?: cloudProfile?.email?.takeIf { it.isNotBlank() }
        ?: if (currentUser != null) "Signed in via Google" else "Not signed in — settings stored on device only"
    val photoUrl = currentUser?.photoUrl?.toString()?.takeIf { it.isNotBlank() }
        ?: cloudProfile?.photoUrl?.takeIf { it.isNotBlank() }

    val resolvedBio = userBio.takeIf { it.isNotBlank() }
        ?: cloudProfile?.bio?.takeIf { it.isNotBlank() }
        ?: ""

    val equippedTags = remember(selectedProfileTagIds) {
        PremadeTagsCatalog.resolveTags(selectedProfileTagIds)
    }

    // Cumulative playtime across EVERY game session together
    val totalMinutesAllSessions = maxOf(
        totalPlayTimeSummary.totalMinutes,
        cloudProfile?.totalPlayTimeMinutes ?: 0L
    )
    val totalSessionsCount = maxOf(
        totalPlayTimeSummary.totalSessions.toLong(),
        cloudProfile?.totalSessionsCount ?: 0L
    )
    val totalHoursPart = totalMinutesAllSessions / 60L
    val totalMinutesPart = totalMinutesAllSessions % 60L
    val formattedAllSessionsTime = "${totalHoursPart}h ${totalMinutesPart}m"

    // Resolve Most Played Game from live stats -> cloud profile -> target game
    val topLocalStat = mostPlayedGames.firstOrNull()
    val mostPlayedTitle = when {
        topLocalStat != null -> topLocalStat.game.appName
        !cloudProfile?.mostPlayedGameName.isNullOrBlank() &&
            cloudProfile?.mostPlayedGameName != "No games played yet" -> cloudProfile!!.mostPlayedGameName
        selectedTargetGame != null -> selectedTargetGame!!.appName
        else -> "No games played yet"
    }
    val mostPlayedPkg = when {
        topLocalStat != null -> topLocalStat.game.packageName
        !cloudProfile?.mostPlayedGamePkg.isNullOrBlank() -> cloudProfile!!.mostPlayedGamePkg
        selectedTargetGame != null -> selectedTargetGame!!.packageName
        else -> ""
    }
    val mostPlayedMinutes = when {
        topLocalStat != null -> (topLocalStat.totalPlayTimeMs / 60_000L).coerceAtLeast(1L)
        (cloudProfile?.mostPlayedMinutes ?: 0L) > 0L -> cloudProfile!!.mostPlayedMinutes
        else -> 0L
    }
    val formattedPlayTime = remember(mostPlayedMinutes) {
        if (mostPlayedMinutes <= 0L) {
            "Launch any game to track play time"
        } else if (mostPlayedMinutes >= 60L) {
            val hrs = mostPlayedMinutes / 60L
            val mins = mostPlayedMinutes % 60L
            "${hrs}h ${mins}m play time on top title"
        } else {
            "$mostPlayedMinutes mins play time on top title"
        }
    }

    val lastSyncedFormatted = remember(cloudProfile?.updatedAt) {
        val ts = cloudProfile?.updatedAt ?: cloudProfile?.createdAt
        if (ts != null) {
            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(ts.toDate())
        } else {
            "Synced on change"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header with Back Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("account_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "User Account & Profile",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Profile tags, bio, cumulative hours & minutes played, most played game & cloud sync",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        // 2. Gamer Profile Hero Card (Profile Picture, Name, Email, Verified Developer Gold-Star Seal)
        item {
            CyberCard(
                borderColor = if (isVerifiedDeveloper) DeveloperGold.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Picture with glowing ring (Gold Star Ring for Verified Developer)
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isVerifiedDeveloper) {
                                            listOf(
                                                DeveloperGold.copy(alpha = 0.32f),
                                                DeveloperAmber.copy(alpha = 0.25f)
                                            )
                                        } else {
                                            listOf(
                                                HubBlue.copy(alpha = 0.25f),
                                                LaunchGreen.copy(alpha = 0.25f)
                                            )
                                        }
                                    )
                                )
                                .border(
                                    width = 2.5.dp,
                                    brush = Brush.linearGradient(
                                        when {
                                            isVerifiedDeveloper -> listOf(DeveloperGold, DeveloperAmber)
                                            isSignedIn -> listOf(LaunchGreen, HubBlue)
                                            else -> listOf(HubBlue, MaterialTheme.colorScheme.outline)
                                        }
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "User Profile Picture",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = if (isVerifiedDeveloper) Icons.Default.Star else Icons.Default.AccountCircle,
                                    contentDescription = "Default Profile Picture",
                                    tint = when {
                                        isVerifiedDeveloper -> DeveloperGold
                                        isSignedIn -> LaunchGreen
                                        else -> HubBlue
                                    },
                                    modifier = Modifier.size(if (isVerifiedDeveloper) 42.dp else 52.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (isSignedIn) LaunchGreen.copy(alpha = 0.16f)
                                            else HubBlue.copy(alpha = 0.16f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (currentUser != null) "CLOUD SYNC ACTIVE"
                                        else if (isVerifiedDeveloper) "VERIFIED CREATOR ACCOUNT"
                                        else "GUEST ACCOUNT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSignedIn) LaunchGreen else HubBlue,
                                            fontSize = 10.sp
                                        )
                                    )
                                }

                                // Exclusive Gold-Star Developer Badge in Hero Header
                                if (isVerifiedDeveloper) {
                                    VerifiedDeveloperGoldStarBadge(
                                        compact = true,
                                        modifier = Modifier.testTag("hero_verified_developer_badge")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isVerifiedDeveloper) DeveloperGold else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isVerifiedDeveloper) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Official Gold Star Developer",
                                        tint = DeveloperGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Developer",
                                        tint = DeveloperGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = emailText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Authenticity Proof Banner for Albertomiller747@gmail.com
                    if (isVerifiedDeveloper) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DeveloperDeepBg.copy(alpha = 0.85f))
                                .border(1.dp, DeveloperGold.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("verified_developer_proof_banner"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = DeveloperGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "★ OFFICIAL G-MAX DEVELOPER • AUTHENTICATED SEAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = DeveloperGold,
                                        letterSpacing = 0.6.sp
                                    )
                                )
                                Text(
                                    text = "Exclusively bound to Albertomiller747@gmail.com — protected against spoofing by Firestore Security Rules.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Profile Tags Showcase Card (40+ Premade Tags, Up to 4 Equipped + Permanent 5th Gold-Star Developer Tag)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CyberCard(
                    borderColor = if (isVerifiedDeveloper) DeveloperGold.copy(alpha = 0.55f)
                    else HubBlue.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_tags_card"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HubBlue.copy(alpha = 0.15f))
                                        .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalOffer,
                                        contentDescription = "Profile Tags",
                                        tint = HubBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "PROFILE TAGS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = HubBlue,
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(LaunchGreen.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${equippedTags.size} / ${PremadeTagsCatalog.MAX_EQUIPPED_TAGS} EQUIPPED",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = LaunchGreen,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isVerifiedDeveloper) {
                                            "Permanent ★ Developer Gold-Star badge + up to 4 custom tags from ${PremadeTagsCatalog.ALL_TAGS.size} premade tags"
                                        } else {
                                            "Choose up to 4 tags from ${PremadeTagsCatalog.ALL_TAGS.size} premade gamer tags to display on your profile"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { showTagPickerDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.55f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("edit_profile_tags_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Choose Profile Tags",
                                    tint = HubBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Choose Tags",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = HubBlue
                                    )
                                )
                            }
                        }

                        // Equipped Tags FlowRow (Permanent 5th Gold-Star Developer Badge + up to 4 custom tags)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isVerifiedDeveloper) {
                                VerifiedDeveloperGoldStarBadge(
                                    compact = false,
                                    modifier = Modifier.testTag("permanent_fifth_developer_tag")
                                )
                            }

                            if (equippedTags.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .clickable { showTagPickerDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "+ Tap 'Choose Tags' to equip up to 4 gamer tags (${PremadeTagsCatalog.ALL_TAGS.size} available)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            } else {
                                equippedTags.forEach { tag ->
                                    PremadeTagChip(
                                        tag = tag,
                                        isSelected = true,
                                        onClick = { showTagPickerDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Gamer Bio Card
                var bioFocused by remember { mutableStateOf(false) }
                CyberCard(
                    modifier = Modifier
                        .onFocusChanged { bioFocused = it.isFocused }
                        .focusable()
                        .clickable { showBioDialog = true }
                        .testTag("profile_edit_bio_card")
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
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(HubBlue.copy(alpha = 0.15f))
                                    .border(
                                        width = if (bioFocused) 2.dp else 1.dp,
                                        color = if (bioFocused) HubBlue else HubBlue.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Profile Bio",
                                    tint = HubBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GAMER BIO",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = HubBlue,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (resolvedBio.isNotBlank()) resolvedBio else "No bio added yet — tap Edit to write your gamer bio",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (resolvedBio.isNotBlank()) FontWeight.Bold else FontWeight.Medium,
                                        color = if (resolvedBio.isNotBlank()) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedButton(
                            onClick = { showBioDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("edit_bio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Bio",
                                tint = HubBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = HubBlue
                                )
                            )
                        }
                    }
                }

                // Cumulative Playtime Card (Hours & Minutes Played Across Every Game Session Together)
                CyberCard(borderColor = SolarGold.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_total_playtime_card"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SolarGold.copy(alpha = 0.16f))
                                .border(1.dp, SolarGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Total Playtime",
                                tint = SolarGold,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HOURS & MINUTES PLAYED (ALL SESSIONS)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SolarGold,
                                    letterSpacing = 0.8.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formattedAllSessionsTime,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "${totalHoursPart} hrs ${totalMinutesPart} mins ($totalMinutesAllSessions total mins) across $totalSessionsCount game ${if (totalSessionsCount == 1L) "session" else "sessions"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // Most Played Game Card
                CyberCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(LaunchGreen.copy(alpha = 0.15f))
                                .border(1.dp, LaunchGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Most Played Game",
                                tint = LaunchGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MOST PLAYED GAME",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LaunchGreen,
                                    letterSpacing = 0.8.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mostPlayedTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formattedPlayTime,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            if (mostPlayedPkg.isNotBlank()) {
                                Text(
                                    text = mostPlayedPkg,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = HubBlue.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Date of Birth Card
                var dobFocused by remember { mutableStateOf(false) }
                CyberCard(
                    modifier = Modifier
                        .onFocusChanged { dobFocused = it.isFocused }
                        .focusable()
                        .clickable { showDobDialog = true }
                        .testTag("profile_edit_dob_card")
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
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(HubBlue.copy(alpha = 0.15f))
                                    .border(
                                        width = if (dobFocused) 2.dp else 1.dp,
                                        color = if (dobFocused) HubBlue else HubBlue.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cake,
                                    contentDescription = "Date of Birth",
                                    tint = HubBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "DATE OF BIRTH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = HubBlue,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = userDateOfBirth,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "Saved to your G-Max gamer profile",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showDobDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("edit_dob_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Date of Birth",
                                tint = HubBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = HubBlue
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Account & Cloud Sync Card
        item {
            CyberCard {
                SectionTitle(
                    title = "Account & Cloud Sync",
                    subtitle = "Automatically remembers and restores your previous settings, tags, and chosen options"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Sync Status Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSignedIn) LaunchGreen.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSignedIn) LaunchGreen.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isSignedIn) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isVerifiedDeveloper && currentUser == null) {
                                    "Verified Developer Account Active (Albertomiller747@gmail.com)"
                                } else {
                                    cloudSyncStatus
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (currentUser != null) "Last cloud checkpoint: $lastSyncedFormatted"
                                else if (isVerifiedDeveloper) "Official Developer Profile & Gold-Star Badge active."
                                else "Sign in with Google to back up and restore your settings across devices.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Synced Settings Snapshot Summary
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SyncedCategoryRow(
                        icon = Icons.Default.LocalOffer,
                        accent = if (isVerifiedDeveloper) DeveloperGold else HubBlue,
                        label = "Equipped Profile Tags",
                        currentValue = if (isVerifiedDeveloper) {
                            "★ Developer + ${equippedTags.size}/4 Tags"
                        } else {
                            "${equippedTags.size}/4 Tags Equipped"
                        }
                    )
                    SyncedCategoryRow(
                        icon = Icons.Default.Settings,
                        accent = HubBlue,
                        label = "Hub Theme & Display Options",
                        currentValue = appTheme.title
                    )
                    SyncedCategoryRow(
                        icon = Icons.Default.RocketLaunch,
                        accent = LaunchGreen,
                        label = "Boost Profile & Active Tweaks",
                        currentValue = "${selectedBoostMode.title} Mode"
                    )
                    SyncedCategoryRow(
                        icon = Icons.Default.SportsEsports,
                        accent = HubBlue,
                        label = "EFPS HUD & FPS Cap Calibration",
                        currentValue = "${overlayConfig.fpsMode} • Cap: ${overlayConfig.targetCapMode}"
                    )
                    SyncedCategoryRow(
                        icon = Icons.Default.Videocam,
                        accent = RecorderCoral,
                        label = "Recorder Studio & Bubble Size",
                        currentValue = "Synced to Account"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(visible = authError != null) {
                    Text(
                        text = authError ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RecorderCoral,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                if (isSignedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncAllSettingsToCloudNow() },
                            enabled = !isCloudSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_settings_now_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HubBlue,
                                contentColor = Color(0xFF081018)
                            )
                        ) {
                            if (isCloudSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF081018),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Settings Now", fontWeight = FontWeight.ExtraBold)
                        }

                        OutlinedButton(
                            onClick = {
                                UserAccountRepository.signOut(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {
                                        viewModel.onUserSignedOut()
                                    },
                                    scope = coroutineScope
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("sign_out_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = RecorderCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", color = RecorderCoral, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                authError = null
                                isSigningIn = true
                                UserAccountRepository.onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isSigningIn = false
                                        viewModel.onUserSignedInSuccess()
                                    },
                                    onAuthError = { err ->
                                        isSigningIn = false
                                        authError = err
                                    },
                                    scope = coroutineScope,
                                    onAuthCancelled = {
                                        isSigningIn = false
                                    }
                                )
                            },
                            enabled = !isSigningIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("profile_google_signin_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LaunchGreen,
                                contentColor = Color(0xFF081018)
                            )
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF081018),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign In / Create Account with Google",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }

                        // Pre-created Developer Account quick sign-in for Albertomiller747@gmail.com
                        OutlinedButton(
                            onClick = {
                                viewModel.setCustomProfileCredentials(
                                    fullName = PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME,
                                    email = PremadeTagsCatalog.OFFICIAL_DEVELOPER_DISPLAY_email_DISPLAY
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("activate_developer_account_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, DeveloperGold.copy(alpha = 0.75f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = DeveloperGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign In Pre-Created Dev: Albertomiller747@gmail.com",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DeveloperGold
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

    if (showDobDialog) {
        DateOfBirthPickerDialog(
            currentDob = userDateOfBirth,
            onDismiss = { showDobDialog = false },
            onConfirmDob = { newDob ->
                viewModel.setUserDateOfBirth(newDob)
                showDobDialog = false
            }
        )
    }

    if (showBioDialog) {
        EditBioDialog(
            currentBio = resolvedBio,
            onDismiss = { showBioDialog = false },
            onConfirmBio = { updatedBio ->
                viewModel.setUserBio(updatedBio)
                showBioDialog = false
            }
        )
    }

    if (showTagPickerDialog) {
        PremadeTagPickerDialog(
            initialSelectedIds = selectedProfileTagIds,
            isVerifiedDeveloper = isVerifiedDeveloper,
            onDismiss = { showTagPickerDialog = false },
            onSaveTags = { updatedIds ->
                viewModel.setSelectedProfileTags(updatedIds)
                showTagPickerDialog = false
            }
        )
    }
}

/**
 * Unforgeable Gold-Star Developer Badge rendered exclusively when
 * PremadeTagsCatalog.isOfficialDeveloperEmail(email) == true (Albertomiller747@gmail.com).
 */
@Composable
fun VerifiedDeveloperGoldStarBadge(
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF3A2B05),
                        Color(0xFF523B06)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(DeveloperGold, DeveloperAmber, DeveloperGold)
                ),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (compact) 10.dp else 13.dp,
                vertical = if (compact) 3.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Gold Star Developer",
            tint = DeveloperGold,
            modifier = Modifier.size(if (compact) 13.dp else 16.dp)
        )
        Text(
            text = if (compact) "★ DEVELOPER" else "★ DEVELOPER • VERIFIED",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = DeveloperGold,
                fontSize = if (compact) 10.sp else 11.sp,
                letterSpacing = 0.6.sp
            )
        )
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Verified Authentic",
            tint = DeveloperGold,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
    }
}

@Composable
private fun PremadeTagChip(
    tag: PremadeProfileTag,
    isSelected: Boolean,
    isDisabled: Boolean = false,
    onClick: () -> Unit
) {
    val accent = Color(tag.category.accentColorHex)
    val bgAlpha = when {
        isSelected -> 0.22f
        isDisabled -> 0.05f
        else -> 0.10f
    }
    val borderAlpha = when {
        isSelected -> 0.85f
        isDisabled -> 0.15f
        else -> 0.35f
    }
    val textColor = when {
        isSelected -> accent
        isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = bgAlpha))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = accent.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = !isDisabled || isSelected) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("premade_tag_chip_${tag.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = tag.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = textColor
            )
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Equipped",
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PremadeTagPickerDialog(
    initialSelectedIds: List<String>,
    isVerifiedDeveloper: Boolean,
    onDismiss: () -> Unit,
    onSaveTags: (List<String>) -> Unit
) {
    val draftSelected = remember(initialSelectedIds) {
        mutableStateListOf<String>().apply {
            addAll(PremadeTagsCatalog.sanitizeSelectedTags(initialSelectedIds))
        }
    }
    var selectedCategory by remember { mutableStateOf<ProfileTagCategory?>(null) }

    val filteredTags = remember(selectedCategory) {
        if (selectedCategory == null) {
            PremadeTagsCatalog.ALL_TAGS
        } else {
            PremadeTagsCatalog.ALL_TAGS.filter { it.category == selectedCategory }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = HubBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Choose Profile Tags",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (draftSelected.size >= PremadeTagsCatalog.MAX_EQUIPPED_TAGS) {
                                    SolarGold.copy(alpha = 0.2f)
                                } else {
                                    LaunchGreen.copy(alpha = 0.18f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${draftSelected.size} / ${PremadeTagsCatalog.MAX_EQUIPPED_TAGS}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (draftSelected.size >= PremadeTagsCatalog.MAX_EQUIPPED_TAGS) SolarGold else LaunchGreen
                            )
                        )
                    }
                }
                Text(
                    text = "Select up to 4 tags from ${PremadeTagsCatalog.ALL_TAGS.size} premade tags to showcase on your profile.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Exclusive Gold-Star Developer Tag Authenticity Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeveloperDeepBg.copy(alpha = 0.75f))
                        .border(1.dp, DeveloperGold.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVerifiedDeveloper) Icons.Default.Star else Icons.Default.Lock,
                        contentDescription = null,
                        tint = DeveloperGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isVerifiedDeveloper) {
                                "★ DEVELOPER GOLD-STAR TAG ACTIVE (PERMANENT 5TH BADGE)"
                            } else {
                                "★ DEVELOPER GOLD-STAR TAG (RESTRICTED • VERIFIED ONLY)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = DeveloperGold,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = if (isVerifiedDeveloper) {
                                "Verified for Albertomiller747@gmail.com. You can still equip 4 additional custom tags below."
                            } else {
                                "Reserved exclusively for Albertomiller747@gmail.com with a gold star so it can never be faked."
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFDE68A),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Category Filter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryFilterPill(
                        label = "All (${PremadeTagsCatalog.ALL_TAGS.size})",
                        isSelected = selectedCategory == null,
                        accent = HubBlue,
                        onClick = { selectedCategory = null }
                    )
                    ProfileTagCategory.entries.forEach { category ->
                        CategoryFilterPill(
                            label = category.title,
                            isSelected = selectedCategory == category,
                            accent = Color(category.accentColorHex),
                            onClick = { selectedCategory = category }
                        )
                    }
                }

                if (draftSelected.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap an equipped tag to deselect it",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        TextButton(
                            onClick = { draftSelected.clear() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Clear All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RecorderCoral,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Scrollable List of 45 Premade Tags
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val categoriesToShow = if (selectedCategory != null) {
                        listOf(selectedCategory!!)
                    } else {
                        ProfileTagCategory.entries
                    }

                    items(categoriesToShow) { category ->
                        val categoryTags = filteredTags.filter { it.category == category }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = category.title.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(category.accentColorHex),
                                    letterSpacing = 0.8.sp
                                )
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categoryTags.forEach { tag ->
                                    val isSelected = tag.id in draftSelected
                                    val isMaxReached = draftSelected.size >= PremadeTagsCatalog.MAX_EQUIPPED_TAGS && !isSelected
                                    PremadeTagChip(
                                        tag = tag,
                                        isSelected = isSelected,
                                        isDisabled = isMaxReached,
                                        onClick = {
                                            if (isSelected) {
                                                draftSelected.remove(tag.id)
                                            } else if (draftSelected.size < PremadeTagsCatalog.MAX_EQUIPPED_TAGS) {
                                                draftSelected.add(tag.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveTags(draftSelected.toList()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF081018)
                ),
                modifier = Modifier.testTag("confirm_profile_tags_button")
            ) {
                Text("Save Tags (${draftSelected.size}/4)", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoryFilterPill(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun EditBioDialog(
    currentBio: String,
    onDismiss: () -> Unit,
    onConfirmBio: (String) -> Unit
) {
    var draftBio by remember(currentBio) { mutableStateOf(currentBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = HubBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Gamer Bio",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Write a short bio for your G-Max gamer profile (up to 300 characters):",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = draftBio,
                    onValueChange = { if (it.length <= 300) draftBio = it },
                    placeholder = { Text("e.g. 120Hz FPS & MOBA player • Beast Mode main") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bio_text_input")
                )
                Text(
                    text = "${draftBio.length} / 300",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmBio(draftBio.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF081018)
                ),
                modifier = Modifier.testTag("confirm_bio_button")
            ) {
                Text("Save Bio", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SyncedCategoryRow(
    icon: ImageVector,
    accent: Color,
    label: String,
    currentValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currentValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = LaunchGreen,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
