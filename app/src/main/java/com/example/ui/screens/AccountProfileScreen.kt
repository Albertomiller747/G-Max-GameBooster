package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.repository.LeaderboardEntry
import com.example.data.repository.UserAccountRepository
import com.example.ui.components.CyberCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.ui.theme.SolarGold
import com.example.util.CountryCatalog
import com.example.util.CountryOption
import com.example.util.DailyPlayTime
import com.example.util.GamerscoreData
import com.example.util.GamerscoreEngine
import com.example.util.PremadeProfileTag
import com.example.util.PremadeTagsCatalog
import com.example.util.ProfileTagCategory
import com.example.viewmodel.GameBoosterViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val DeveloperGold = Color(0xFFFFD700)
private val DeveloperAmber = Color(0xFFF59E0B)

const val PLAY_DATA_SAFETY_DELETION_URL = "https://ais-pre-c5zo6oshmza3ybshjj5eut-82366898686.us-east1.run.app/delete-account.html"

private fun Int.scaledSp(scale: Float): TextUnit = (this * scale).sp
private fun Int.scaledDp(scale: Float): Dp = (this * scale).dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountProfileScreen(
    viewModel: GameBoosterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLeaderboardTab by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (showLeaderboardTab) {
            showLeaderboardTab = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPlayStats()
        viewModel.startWorldwideLeaderboardSync()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by viewModel.currentFirebaseUser.collectAsState()
    val socialSignedInProvider by viewModel.socialSignedInProvider.collectAsState()
    val cloudProfile by viewModel.cloudProfile.collectAsState()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val userDateOfBirth by viewModel.userDateOfBirth.collectAsState()
    val userBio by viewModel.userBio.collectAsState()
    val userUsername by viewModel.userUsername.collectAsState()
    val userCountryCode by viewModel.userCountryCode.collectAsState()
    val userCountryName by viewModel.userCountryName.collectAsState()
    val leaderboardEntries by viewModel.leaderboardEntries.collectAsState()
    val isLeaderboardLoading by viewModel.isLeaderboardLoading.collectAsState()
    val isCheckingUsername by viewModel.isCheckingUsername.collectAsState()
    val usernameError by viewModel.usernameError.collectAsState()
    val customDisplayName by viewModel.customDisplayName.collectAsState()
    val customEmail by viewModel.customEmail.collectAsState()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsState()
    val userBannerUrl by viewModel.userBannerUrl.collectAsState()
    val selectedProfileTagIds by viewModel.selectedProfileTags.collectAsState()
    val isVerifiedDeveloper by viewModel.isVerifiedDeveloperAccount.collectAsState()
    val mostPlayedGames by viewModel.mostPlayedGames.collectAsState()
    val totalPlayTimeSummary by viewModel.totalPlayTimeSummary.collectAsState()
    val selectedTargetGame by viewModel.selectedTargetGame.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val gamerscoreData by viewModel.gamerscoreData.collectAsState()
    val customDeletionUrl by viewModel.customDeletionUrl.collectAsState()

    var showDobDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showCountryPickerDialog by remember { mutableStateOf(false) }
    var showTagPickerDialog by remember { mutableStateOf(false) }
    var showBannerPickerDialog by remember { mutableStateOf(false) }
    var showGamerscoreInfoDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }
    var showEditDeletionUrlDialog by remember { mutableStateOf(false) }
    var showGoogleFormHelpDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var activeProfileSocialPickerProvider by remember { mutableStateOf<String?>(null) }

    // Android Zero-Permission Photo Pickers for Avatar and Banner
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setUserAvatar(uri.toString())
        }
    }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setUserBanner(uri.toString())
            showBannerPickerDialog = false
        }
    }

    val isSignedIn = currentUser != null || isVerifiedDeveloper || socialSignedInProvider.isNotBlank()
    val rawDisplayName = customDisplayName.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: cloudProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: if (isVerifiedDeveloper) PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME
        else if (isSignedIn) "G-Max Gamer" else "Guest Gamer"
    val displayName = PremadeTagsCatalog.sanitizeDisplayName(rawDisplayName, isVerifiedDeveloper)

    val resolvedUsername = remember(userUsername, cloudProfile?.username, displayName, isVerifiedDeveloper) {
        val candidate = userUsername.takeIf { it.isNotBlank() }
            ?: cloudProfile?.username?.takeIf { it.isNotBlank() }
            ?: ""
        PremadeTagsCatalog.sanitizeUsername(candidate, displayName, isVerifiedDeveloper)
    }

    val resolvedCountryCode = remember(userCountryCode, cloudProfile?.countryCode) {
        val code = userCountryCode.takeIf { it.isNotBlank() }
            ?: cloudProfile?.countryCode?.takeIf { it.isNotBlank() }
            ?: "US"
        code.uppercase(Locale.US)
    }
    val resolvedCountryOption = remember(resolvedCountryCode, userCountryName, cloudProfile?.countryName) {
        CountryCatalog.getCountryByCode(resolvedCountryCode)
            ?: CountryOption(
                code = resolvedCountryCode,
                name = userCountryName.ifBlank { cloudProfile?.countryName ?: "United States" },
                flagEmoji = CountryCatalog.codeToFlagEmoji(resolvedCountryCode)
            )
    }

    val emailText = currentUser?.email?.takeIf { it.isNotBlank() }
        ?: customEmail.takeIf { it.isNotBlank() }
        ?: cloudProfile?.email?.takeIf { it.isNotBlank() }
        ?: if (socialSignedInProvider.isNotBlank()) "Signed in via $socialSignedInProvider"
        else if (currentUser != null) "Signed in via Google" else "Guest Mode (Local Storage Only)"

    val resolvedBio = userBio.takeIf { it.isNotBlank() }
        ?: cloudProfile?.bio?.takeIf { it.isNotBlank() }
        ?: ""

    val equippedTags = remember(selectedProfileTagIds) {
        PremadeTagsCatalog.resolveTags(selectedProfileTagIds)
    }

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

    val sevenDayHistory = remember(totalMinutesAllSessions) {
        viewModel.get7DayGamingHistory()
    }

    val topLocalStat = mostPlayedGames.firstOrNull()
    val mostPlayedTitle = when {
        topLocalStat != null -> topLocalStat.game.appName
        !cloudProfile?.mostPlayedGameName.isNullOrBlank() &&
            cloudProfile?.mostPlayedGameName != "No games played yet" -> cloudProfile!!.mostPlayedGameName
        selectedTargetGame != null -> selectedTargetGame!!.appName
        else -> "No games played yet"
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

    // Merge worldwide leaderboard entries with current user's live profile entry so the user always sees their rank
    val mergedLeaderboard = remember(
        leaderboardEntries,
        currentUser?.uid,
        resolvedUsername,
        userAvatarUrl,
        resolvedCountryOption,
        gamerscoreData.totalScore
    ) {
        val myUid = currentUser?.uid ?: "local_self_user"
        val selfEntry = LeaderboardEntry(
            id = myUid,
            userId = myUid,
            username = resolvedUsername,
            photoUrl = userAvatarUrl,
            countryCode = resolvedCountryOption.code,
            countryName = resolvedCountryOption.name,
            gamerscore = gamerscoreData.totalScore
        )
        val filteredRemote = leaderboardEntries.filter {
            it.userId != myUid && !it.username.equals(resolvedUsername, ignoreCase = true)
        }
        (filteredRemote + selfEntry).sortedByDescending { it.gamerscore }
    }

    val currentUserRank = remember(mergedLeaderboard, currentUser?.uid, resolvedUsername) {
        val myUid = currentUser?.uid ?: "local_self_user"
        val idx = mergedLeaderboard.indexOfFirst {
            it.userId == myUid || it.username.equals(resolvedUsername, ignoreCase = true)
        }
        if (idx >= 0) idx + 1 else 1
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val scale = (maxWidth.value / 390f).coerceIn(0.82f, 1.22f)
        val horizontalPad = 16.scaledDp(scale)

        if (showLeaderboardTab) {
            WorldwideLeaderboardContent(
                entries = mergedLeaderboard,
                currentUserUid = currentUser?.uid ?: "local_self_user",
                currentUsername = resolvedUsername,
                isLoading = isLeaderboardLoading,
                scale = scale,
                onBackToProfile = { showLeaderboardTab = false },
                onEditUsername = {
                    viewModel.clearUsernameError()
                    showUsernameDialog = true
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPad),
                contentPadding = PaddingValues(top = 12.scaledDp(scale), bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.scaledDp(scale))
            ) {
                // 1. Rebuilt Hero Profile Section (Reference IMG_0371.webp structure in Cyber Theme)
                item {
                    RebuiltCyberProfileHero(
                        displayName = displayName,
                        username = resolvedUsername,
                        emailText = emailText,
                        userAvatarUrl = userAvatarUrl,
                        userBannerUrl = userBannerUrl,
                        countryOption = resolvedCountryOption,
                        isVerifiedDeveloper = isVerifiedDeveloper,
                        isSignedIn = isSignedIn,
                        hasCloudUser = currentUser != null,
                        equippedTags = equippedTags,
                        gamerscoreData = gamerscoreData,
                        formattedAllSessionsTime = formattedAllSessionsTime,
                        totalSessionsCount = totalSessionsCount,
                        scale = scale,
                        onBack = onBack,
                        onPickAvatar = {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onChangeBanner = { showBannerPickerDialog = true },
                        onEditUsername = {
                            viewModel.clearUsernameError()
                            showUsernameDialog = true
                        },
                        onSelectCountry = { showCountryPickerDialog = true },
                        onOpenGamerscoreInfo = { showGamerscoreInfoDialog = true },
                        onOpenTagPicker = { showTagPickerDialog = true }
                    )
                }

                // 2. Worldwide Leaderboard Section Card (Carries user to Leaderboard Tab when pressed)
                item {
                    WorldwideLeaderboardCard(
                        username = resolvedUsername,
                        countryOption = resolvedCountryOption,
                        gamerscore = gamerscoreData.totalScore,
                        rankPosition = currentUserRank,
                        totalCompetitors = mergedLeaderboard.size,
                        scale = scale,
                        onOpenLeaderboard = {
                            viewModel.startWorldwideLeaderboardSync()
                            showLeaderboardTab = true
                        }
                    )
                }

                // 3. Gamer Bio Card (Moved ABOVE Total Gaming Time as requested)
                item {
                    CyberCard(
                        modifier = Modifier
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
                                        .size(44.scaledDp(scale))
                                        .clip(RoundedCornerShape(13.scaledDp(scale)))
                                        .background(HubBlue.copy(alpha = 0.15f))
                                        .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(13.scaledDp(scale))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Profile Bio",
                                        tint = HubBlue,
                                        modifier = Modifier.size(22.scaledDp(scale))
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "GAMER BIO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = HubBlue,
                                            fontSize = 11.scaledSp(scale),
                                            letterSpacing = 0.8.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.scaledDp(scale)))
                                    Text(
                                        text = if (resolvedBio.isNotBlank()) resolvedBio else "No bio added yet — tap Edit to write your gamer bio",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (resolvedBio.isNotBlank()) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.scaledSp(scale),
                                            color = if (resolvedBio.isNotBlank()) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.scaledDp(scale)))

                            OutlinedButton(
                                onClick = { showBioDialog = true },
                                shape = RoundedCornerShape(12.scaledDp(scale)),
                                border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(
                                    horizontal = 11.scaledDp(scale),
                                    vertical = 6.scaledDp(scale)
                                ),
                                modifier = Modifier.testTag("edit_bio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Bio",
                                    tint = HubBlue,
                                    modifier = Modifier.size(14.scaledDp(scale))
                                )
                                Spacer(modifier = Modifier.width(4.scaledDp(scale)))
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.scaledSp(scale),
                                        color = HubBlue
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. Performance Overview & Total Gaming Time (Smooth Area Chart matching IMG_0371.webp)
                item {
                    CyberCard(borderColor = HubBlue.copy(alpha = 0.55f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_total_gaming_time_card"),
                            verticalArrangement = Arrangement.spacedBy(12.scaledDp(scale))
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
                                            .size(44.scaledDp(scale))
                                            .clip(RoundedCornerShape(12.scaledDp(scale)))
                                            .background(HubBlue.copy(alpha = 0.16f))
                                            .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(12.scaledDp(scale))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BarChart,
                                            contentDescription = "Total Gaming Time",
                                            tint = HubBlue,
                                            modifier = Modifier.size(24.scaledDp(scale))
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "TOTAL GAMING TIME",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = HubBlue,
                                                fontSize = 11.scaledSp(scale),
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = formattedAllSessionsTime,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 20.scaledSp(scale),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.scaledDp(scale)))
                                            Text(
                                                text = "across $totalSessionsCount ${if (totalSessionsCount == 1L) "session" else "sessions"}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.scaledSp(scale),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.padding(bottom = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Smooth Cubic Bezier Area Chart inspired by IMG_0371.webp "Performance Overview"
                            SmoothPlaytimeAreaChart(
                                dailyStats = sevenDayHistory,
                                scale = scale,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 5. Uncapped Complex Gamerscore Card
                item {
                    val primaryGamerscoreColor = Color(android.graphics.Color.parseColor(gamerscoreData.primaryColorHex))
                    CyberCard(
                        borderColor = primaryGamerscoreColor.copy(alpha = 0.65f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_gamerscore_card"),
                            verticalArrangement = Arrangement.spacedBy(10.scaledDp(scale))
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
                                            .size(44.scaledDp(scale))
                                            .clip(RoundedCornerShape(12.scaledDp(scale)))
                                            .background(primaryGamerscoreColor.copy(alpha = 0.18f))
                                            .border(
                                                1.dp,
                                                primaryGamerscoreColor.copy(alpha = 0.5f),
                                                RoundedCornerShape(12.scaledDp(scale))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Gamerscore",
                                            tint = primaryGamerscoreColor,
                                            modifier = Modifier.size(22.scaledDp(scale))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.scaledDp(scale)))
                                    Column {
                                        Text(
                                            text = "GAMERSCORE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = primaryGamerscoreColor,
                                                fontSize = 10.scaledSp(scale),
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "${gamerscoreData.totalScore}",
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 24.scaledSp(scale),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.scaledDp(scale)))
                                            Text(
                                                text = "GS",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.scaledSp(scale),
                                                    color = primaryGamerscoreColor
                                                ),
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            )
                                        }
                                    }
                                }

                                // Rank Badge Pill
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(primaryGamerscoreColor.copy(alpha = 0.16f))
                                        .border(
                                            1.dp,
                                            primaryGamerscoreColor.copy(alpha = 0.45f),
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { showGamerscoreInfoDialog = true }
                                        .padding(
                                            horizontal = 10.scaledDp(scale),
                                            vertical = 5.scaledDp(scale)
                                        )
                                ) {
                                    Text(
                                        text = "[${gamerscoreData.rankBadgeText}] ${gamerscoreData.rankTitle.uppercase(Locale.US)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = primaryGamerscoreColor,
                                            fontSize = 10.scaledSp(scale)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.scaledDp(scale)))
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Gamerscore info",
                                        tint = primaryGamerscoreColor,
                                        modifier = Modifier.size(13.scaledDp(scale))
                                    )
                                }
                            }

                            // Progress bar towards next tier
                            Column(verticalArrangement = Arrangement.spacedBy(4.scaledDp(scale))) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${gamerscoreData.rankTier} • Progress to Next Rank",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.scaledSp(scale),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Text(
                                        text = "${gamerscoreData.totalScore} / ${gamerscoreData.nextTierMinScore} GS",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = primaryGamerscoreColor,
                                            fontSize = 11.scaledSp(scale),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { gamerscoreData.progressRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.scaledDp(scale))
                                        .clip(RoundedCornerShape(50)),
                                    color = primaryGamerscoreColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    strokeCap = StrokeCap.Round
                                )
                            }

                            // Point distribution breakdown chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
                            ) {
                                GamerscoreMetricChip(
                                    label = "Play Time",
                                    value = "+${gamerscoreData.playTimePoints} pts",
                                    accent = HubBlue,
                                    scale = scale,
                                    modifier = Modifier.weight(1f)
                                )
                                GamerscoreMetricChip(
                                    label = "Sessions",
                                    value = "+${gamerscoreData.sessionsPoints} pts",
                                    accent = LaunchGreen,
                                    scale = scale,
                                    modifier = Modifier.weight(1f)
                                )
                                GamerscoreMetricChip(
                                    label = "Milestones",
                                    value = "+${gamerscoreData.milestoneBonusPoints} pts",
                                    accent = SolarGold,
                                    scale = scale,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 6. Most Played Game Card
                item {
                    CyberCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.example.ui.components.GMaxBrandIcon(
                                size = 46.scaledDp(scale),
                                cornerRadius = 14.scaledDp(scale),
                                showGlowBorder = true,
                                glowColor = LaunchGreen,
                                contentDescription = "Most Played Game"
                            )

                            Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MOST PLAYED GAME",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LaunchGreen,
                                        fontSize = 10.scaledSp(scale),
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.scaledDp(scale)))
                                Text(
                                    text = mostPlayedTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.scaledSp(scale),
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedPlayTime,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.scaledSp(scale),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // 7. Profile Tags & Customization Card
                item {
                    CyberCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.scaledDp(scale))) {
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
                                            .size(44.scaledDp(scale))
                                            .clip(RoundedCornerShape(12.scaledDp(scale)))
                                            .background(HubBlue.copy(alpha = 0.15f))
                                            .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(12.scaledDp(scale))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalOffer,
                                            contentDescription = "Profile Tags",
                                            tint = HubBlue,
                                            modifier = Modifier.size(20.scaledDp(scale))
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.scaledDp(scale)))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.scaledDp(scale))
                                        ) {
                                            Text(
                                                text = "PROFILE TAGS",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = HubBlue,
                                                    fontSize = 10.scaledSp(scale),
                                                    letterSpacing = 0.8.sp
                                                )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(LaunchGreen.copy(alpha = 0.16f))
                                                    .padding(horizontal = 6.scaledDp(scale), vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${equippedTags.size} / ${PremadeTagsCatalog.MAX_EQUIPPED_TAGS} EQUIPPED",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = LaunchGreen,
                                                        fontSize = 9.scaledSp(scale)
                                                    )
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Equip up to 4 tags to showcase on your gamer profile",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.scaledSp(scale),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showTagPickerDialog = true },
                                    shape = RoundedCornerShape(12.scaledDp(scale)),
                                    border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(
                                        horizontal = 10.scaledDp(scale),
                                        vertical = 6.scaledDp(scale)
                                    ),
                                    modifier = Modifier.testTag("edit_profile_tags_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Choose Profile Tags",
                                        tint = HubBlue,
                                        modifier = Modifier.size(14.scaledDp(scale))
                                    )
                                    Spacer(modifier = Modifier.width(4.scaledDp(scale)))
                                    Text(
                                        text = "Choose Tags",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.scaledSp(scale),
                                            color = HubBlue
                                        )
                                    )
                                }
                            }

                            // Equipped Tags FlowRow
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.scaledDp(scale)),
                                verticalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
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
                                            .clip(RoundedCornerShape(10.scaledDp(scale)))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .clickable { showTagPickerDialog = true }
                                            .padding(horizontal = 12.scaledDp(scale), vertical = 8.scaledDp(scale))
                                    ) {
                                        Text(
                                            text = "+ Tap 'Choose Tags' to equip gamer tags",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.scaledSp(scale),
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
                }

                // 8. Date of Birth Card
                item {
                    CyberCard {
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
                                        .size(44.scaledDp(scale))
                                        .clip(RoundedCornerShape(13.scaledDp(scale)))
                                        .background(HubBlue.copy(alpha = 0.15f))
                                        .border(1.dp, HubBlue.copy(alpha = 0.45f), RoundedCornerShape(13.scaledDp(scale))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cake,
                                        contentDescription = "Date of Birth",
                                        tint = HubBlue,
                                        modifier = Modifier.size(22.scaledDp(scale))
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                                Column {
                                    Text(
                                        text = "DATE OF BIRTH",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = HubBlue,
                                            fontSize = 10.scaledSp(scale),
                                            letterSpacing = 0.8.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.scaledDp(scale)))
                                    Text(
                                        text = userDateOfBirth,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.scaledSp(scale),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "Saved to your G-Max gamer profile",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.scaledSp(scale),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showDobDialog = true },
                                shape = RoundedCornerShape(12.scaledDp(scale)),
                                border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(
                                    horizontal = 11.scaledDp(scale),
                                    vertical = 6.scaledDp(scale)
                                ),
                                modifier = Modifier.testTag("edit_dob_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Date of Birth",
                                    tint = HubBlue,
                                    modifier = Modifier.size(14.scaledDp(scale))
                                )
                                Spacer(modifier = Modifier.width(4.scaledDp(scale)))
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.scaledSp(scale),
                                        color = HubBlue
                                    )
                                )
                            }
                        }
                    }
                }

                // 9. Account & Cloud Sync Card
                item {
                    CyberCard {
                        SectionTitle(
                            title = "Account & Cloud Sync",
                            subtitle = "Automatically remembers and restores your previous settings, tags, avatars, and chosen options"
                        )

                        Spacer(modifier = Modifier.height(12.scaledDp(scale)))

                        // Sync Status Banner
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.scaledDp(scale)),
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
                                    .padding(14.scaledDp(scale)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isSignedIn) LaunchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.scaledDp(scale))
                                )
                                Spacer(modifier = Modifier.width(12.scaledDp(scale)))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cloudSyncStatus,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.scaledSp(scale),
                                            color = if (isSignedIn) LaunchGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = if (isSignedIn) "Last backup: $lastSyncedFormatted"
                                        else "Sign in to backup settings, tags, avatar & game playtime",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.scaledSp(scale),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

                        // Synced Categories Checklist
                        Column(verticalArrangement = Arrangement.spacedBy(8.scaledDp(scale))) {
                            SyncedCategoryRow(
                                icon = Icons.Default.Settings,
                                accent = HubBlue,
                                label = "Hub Theme & Custom Colors",
                                currentValue = appTheme.title
                            )
                            SyncedCategoryRow(
                                icon = Icons.Default.RocketLaunch,
                                accent = LaunchGreen,
                                label = "Boost Profile & System Optimizers",
                                currentValue = "${selectedBoostMode.title} Mode"
                            )
                            SyncedCategoryRow(
                                icon = Icons.Default.SportsEsports,
                                accent = HubBlue,
                                label = "EFPS HUD & Target Calibration",
                                currentValue = "${overlayConfig.fpsMode} • Cap: ${overlayConfig.targetCapMode}"
                            )
                            SyncedCategoryRow(
                                icon = Icons.Default.Wallpaper,
                                accent = SolarGold,
                                label = "Gamer Avatar & Cyber Banner",
                                currentValue = "Cloud Backed"
                            )
                        }

                        Spacer(modifier = Modifier.height(16.scaledDp(scale)))

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
                                horizontalArrangement = Arrangement.spacedBy(10.scaledDp(scale))
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
                                    Text(
                                        text = "Sync Settings Now",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.scaledSp(scale),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    Text(
                                        text = "Sign Out",
                                        color = RecorderCoral,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.scaledSp(scale)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.scaledDp(scale)))

                            // Google Play Console Data Safety: Account Deletion URL Resource
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("play_account_deletion_link_card"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (customDeletionUrl.isNotBlank()) LaunchGreen.copy(alpha = 0.5f) else HubBlue.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                tint = if (customDeletionUrl.isNotBlank()) LaunchGreen else HubBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "PLAY CONSOLE DATA SAFETY LINK",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (customDeletionUrl.isNotBlank()) LaunchGreen else HubBlue,
                                                    letterSpacing = 0.6.sp
                                                )
                                            )
                                        }

                                        if (customDeletionUrl.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(LaunchGreen.copy(alpha = 0.15f))
                                                    .border(1.dp, LaunchGreen.copy(alpha = 0.4f), RoundedCornerShape(50))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "PUBLIC URL READY",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = LaunchGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Google Play Console's Data Safety form requires a public, 24/7 web link for account & data deletion requests. Internal AI Studio preview links fail with a 404 error because Google's crawlers cannot pass the container login barrier. Use a free Google Form or public web page.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF090D16))
                                            .border(
                                                1.dp,
                                                if (customDeletionUrl.isNotBlank()) LaunchGreen.copy(alpha = 0.35f) else Color(0xFF1E293B),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = if (customDeletionUrl.isNotBlank()) customDeletionUrl else "No public link configured (Tap 'Configure URL' below)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (customDeletionUrl.isNotBlank()) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontWeight = FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showEditDeletionUrlDialog = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("edit_deletion_url_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = HubBlue.copy(alpha = 0.25f),
                                                contentColor = HubBlue
                                            ),
                                            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Set URL",
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (customDeletionUrl.isNotBlank()) "Edit URL" else "Configure URL",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { showGoogleFormHelpDialog = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("google_form_guide_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, SolarGold.copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = SolarGold
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "60s Setup Guide",
                                                tint = SolarGold,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Setup Guide",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }

                                    if (customDeletionUrl.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    val clip = ClipData.newPlainText("Account Deletion URL", customDeletionUrl)
                                                    clipboard?.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Public deletion link copied!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                                    .testTag("copy_deletion_url_button"),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = LaunchGreen.copy(alpha = 0.18f),
                                                    contentColor = LaunchGreen
                                                ),
                                                border = BorderStroke(1.dp, LaunchGreen.copy(alpha = 0.4f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Link",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Copy Link",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(customDeletionUrl))
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                                    .testTag("open_deletion_page_button"),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInNew,
                                                    contentDescription = "Open Web Page",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Test Link",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // In-App Account & Data Deletion Button
                            OutlinedButton(
                                onClick = { showDeleteAccountConfirmDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("delete_account_and_data_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RecorderCoral.copy(alpha = 0.7f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Account & Data",
                                    tint = RecorderCoral,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Delete Account & Data (In-App)",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = RecorderCoral,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
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
                                                viewModel.onUserSignedInSuccess("Google")
                                            },
                                            onAuthError = {
                                                isSigningIn = false
                                                activeProfileSocialPickerProvider = "Google"
                                            },
                                            scope = coroutineScope,
                                            onAuthCancelled = {
                                                isSigningIn = false
                                            },
                                            onRequireFallbackPicker = {
                                                isSigningIn = false
                                                activeProfileSocialPickerProvider = "Google"
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
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "G",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF4285F4)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sign In / Create Account with Google",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                                    )
                                }

                                Button(
                                    onClick = {
                                        authError = null
                                        isSigningIn = true
                                        UserAccountRepository.onFacebookSignInClicked(
                                            context = context,
                                            onAuthSuccess = {
                                                isSigningIn = false
                                                viewModel.onUserSignedInSuccess("Facebook")
                                            },
                                            onAuthError = {
                                                isSigningIn = false
                                                activeProfileSocialPickerProvider = "Facebook"
                                            },
                                            scope = coroutineScope,
                                            onAuthCancelled = {
                                                isSigningIn = false
                                            },
                                            onRequireFallbackPicker = {
                                                isSigningIn = false
                                                activeProfileSocialPickerProvider = "Facebook"
                                            }
                                        )
                                    },
                                    enabled = !isSigningIn,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("profile_facebook_signin_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1877F2),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "f",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1877F2)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Continue with Facebook",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeProfileSocialPickerProvider?.let { provider ->
        SocialAccountPickerFallbackDialog(
            providerName = provider,
            initialName = customDisplayName,
            initialEmail = customEmail,
            onDismiss = { activeProfileSocialPickerProvider = null },
            onConfirmAccount = { chosenName, chosenEmail ->
                activeProfileSocialPickerProvider = null
                viewModel.onSocialAccountSignedIn(provider, chosenName, chosenEmail)
                Toast.makeText(context, "Signed in with $provider as $chosenName", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialogs
    if (showUsernameDialog) {
        EditUsernameDialog(
            currentUsername = resolvedUsername,
            isVerifiedDeveloper = isVerifiedDeveloper,
            isChecking = isCheckingUsername,
            externalError = usernameError,
            onDismiss = {
                viewModel.clearUsernameError()
                showUsernameDialog = false
            },
            onSaveUsername = { newHandle ->
                viewModel.updateUsername(newHandle) { success, errMsg ->
                    if (success) {
                        showUsernameDialog = false
                        Toast.makeText(context, "Username @$newHandle saved!", Toast.LENGTH_SHORT).show()
                    } else if (!errMsg.isNullOrBlank()) {
                        Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showCountryPickerDialog) {
        CountryPickerDialog(
            selectedCountryCode = resolvedCountryOption.code,
            onDismiss = { showCountryPickerDialog = false },
            onSelectCountry = { country ->
                viewModel.setUserCountry(country.code, country.name)
                showCountryPickerDialog = false
                Toast.makeText(context, "${country.flagEmoji} ${country.name} selected", Toast.LENGTH_SHORT).show()
            }
        )
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

    if (showBannerPickerDialog) {
        BannerChooserDialog(
            currentBanner = userBannerUrl,
            onDismiss = { showBannerPickerDialog = false },
            onSelectPreset = { presetId ->
                viewModel.setUserBanner(presetId)
                showBannerPickerDialog = false
            },
            onPickCustomPhoto = {
                bannerPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    if (showGamerscoreInfoDialog) {
        GamerscoreInfoDialog(
            gamerscoreData = gamerscoreData,
            onDismiss = { showGamerscoreInfoDialog = false }
        )
    }

    if (showDeleteAccountConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = RecorderCoral,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Account & All Data?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will permanently purge your gamer profile, Gamerscore, leaderboard entry, claimed username, playtime telemetry, custom tags, and all cloud-synced settings from Google Cloud Firestore.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "This action is immediate and cannot be undone.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RecorderCoral,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        viewModel.deleteCurrentAccount { success, errorMsg ->
                            isDeletingAccount = false
                            showDeleteAccountConfirmDialog = false
                            if (success) {
                                Toast.makeText(context, "Account and cloud data permanently deleted", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, errorMsg ?: "Deletion failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecorderCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Permanently Delete", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirmDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDeletionUrlDialog) {
        EditDeletionUrlDialog(
            initialUrl = customDeletionUrl,
            onDismiss = { showEditDeletionUrlDialog = false },
            onSaveUrl = { updatedUrl ->
                viewModel.setCustomDeletionUrl(updatedUrl)
                showEditDeletionUrlDialog = false
                Toast.makeText(context, "Public deletion link saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showGoogleFormHelpDialog) {
        GoogleFormGuideDialog(
            onDismiss = { showGoogleFormHelpDialog = false }
        )
    }
}

/**
 * Rebuilt Cyber Profile Hero matching the centered avatar, flanking quick-action icons,
 * country flag badge + country subtitle, and floating 3-column summary stat bar from IMG_0371.webp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RebuiltCyberProfileHero(
    displayName: String,
    username: String,
    emailText: String,
    userAvatarUrl: String,
    userBannerUrl: String,
    countryOption: CountryOption,
    isVerifiedDeveloper: Boolean,
    isSignedIn: Boolean,
    hasCloudUser: Boolean,
    equippedTags: List<PremadeProfileTag>,
    gamerscoreData: GamerscoreData,
    formattedAllSessionsTime: String,
    totalSessionsCount: Long,
    scale: Float,
    onBack: () -> Unit,
    onPickAvatar: () -> Unit,
    onChangeBanner: () -> Unit,
    onEditUsername: () -> Unit,
    onSelectCountry: () -> Unit,
    onOpenGamerscoreInfo: () -> Unit,
    onOpenTagPicker: () -> Unit
) {
    val accentBorder = if (isVerifiedDeveloper) DeveloperGold else HubBlue
    val avatarSize = 92.scaledDp(scale)
    val flagBadgeSize = 30.scaledDp(scale)
    val sideActionSize = 42.scaledDp(scale)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_hero_card")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Main Hero Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.scaledDp(scale)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0B111A)
                ),
                border = BorderStroke(
                    width = 1.4.dp,
                    color = if (isVerifiedDeveloper) DeveloperGold.copy(alpha = 0.6f)
                    else HubBlue.copy(alpha = 0.45f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 44.scaledDp(scale))
                ) {
                    // Dynamic Banner Background
                    ProfileBannerBackground(
                        bannerKeyOrUri = userBannerUrl,
                        modifier = Modifier.matchParentSize()
                    )

                    // Dark gradient vignette for crisp text readability (like IMG_0371 mountain overlay)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x66060A12),
                                        Color(0xB3080E18),
                                        Color(0xF2090F1A)
                                    )
                                )
                            )
                    )

                    // Subtle cyber scanline overlay
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val lineSpacing = 6.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = Color(0x16000000),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                            y += lineSpacing
                        }
                    }

                    // Foreground Hero Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.scaledDp(scale),
                                vertical = 14.scaledDp(scale)
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Row: Back Button (Left), Title Pill (Center), Change Banner Button (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.scaledDp(scale))
                                    .clip(CircleShape)
                                    .background(Color(0xCC0B111A))
                                    .border(1.dp, Color(0x40FFFFFF), CircleShape)
                                    .testTag("account_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.scaledDp(scale))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSignedIn) LaunchGreen.copy(alpha = 0.2f)
                                        else HubBlue.copy(alpha = 0.2f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSignedIn) LaunchGreen.copy(alpha = 0.55f) else HubBlue.copy(alpha = 0.55f),
                                        RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 10.scaledDp(scale), vertical = 4.scaledDp(scale))
                            ) {
                                Text(
                                    text = if (hasCloudUser) "CLOUD SYNCED" else "GUEST PROFILE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSignedIn) LaunchGreen else HubBlue,
                                        fontSize = 10.scaledSp(scale),
                                        letterSpacing = 0.6.sp
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xCC0B111A))
                                    .border(1.dp, HubBlue.copy(alpha = 0.7f), RoundedCornerShape(50))
                                    .clickable { onChangeBanner() }
                                    .padding(
                                        horizontal = 11.scaledDp(scale),
                                        vertical = 6.scaledDp(scale)
                                    )
                                    .testTag("change_banner_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.scaledDp(scale))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wallpaper,
                                        contentDescription = "Change Background Banner",
                                        tint = HubBlue,
                                        modifier = Modifier.size(14.scaledDp(scale))
                                    )
                                    Text(
                                        text = "Banner",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 11.scaledSp(scale)
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

                        // Centered Circular Avatar flanked by Left (Camera) & Right (Country) Quick Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.scaledDp(scale))
                        ) {
                            // Left Flanking Action: Upload Profile Photo
                            IconButton(
                                onClick = onPickAvatar,
                                modifier = Modifier
                                    .size(sideActionSize)
                                    .clip(CircleShape)
                                    .background(Color(0xCC0F172A))
                                    .border(1.2.dp, HubBlue.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Upload Profile Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.scaledDp(scale))
                                )
                            }

                            // Center Avatar with Country Flag Badge on Bottom-Right Corner
                            Box(
                                modifier = Modifier.size(avatarSize + 6.scaledDp(scale)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A))
                                        .border(
                                            width = 3.dp,
                                            color = accentBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { onPickAvatar() }
                                        .testTag("upload_avatar_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (userAvatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = userAvatarUrl,
                                            contentDescription = "User Profile Picture",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "Default Avatar",
                                            tint = accentBorder,
                                            modifier = Modifier.size(64.scaledDp(scale))
                                        )
                                    }
                                }

                                // Country Flag Badge on Bottom-Right of Profile Picture
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(flagBadgeSize)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0B111A))
                                        .border(1.6.dp, accentBorder, CircleShape)
                                        .clickable { onSelectCountry() }
                                        .testTag("avatar_country_flag_badge"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = countryOption.flagEmoji,
                                        fontSize = 15.scaledSp(scale),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Right Flanking Action: Change Country / Region
                            IconButton(
                                onClick = onSelectCountry,
                                modifier = Modifier
                                    .size(sideActionSize)
                                    .clip(CircleShape)
                                    .background(Color(0xCC0F172A))
                                    .border(1.2.dp, LaunchGreen.copy(alpha = 0.65f), CircleShape)
                                    .testTag("select_country_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Choose Country",
                                    tint = LaunchGreen,
                                    modifier = Modifier.size(20.scaledDp(scale))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.scaledDp(scale)))

                        // Centered Username Handle + Inline Edit Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onEditUsername() }
                                .padding(horizontal = 10.scaledDp(scale), vertical = 2.scaledDp(scale))
                                .testTag("edit_username_button")
                        ) {
                            Text(
                                text = "@$username",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 21.scaledSp(scale),
                                    color = if (isVerifiedDeveloper) DeveloperGold else Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isVerifiedDeveloper) {
                                Spacer(modifier = Modifier.width(5.scaledDp(scale)))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Official Developer",
                                    tint = DeveloperGold,
                                    modifier = Modifier.size(18.scaledDp(scale))
                                )
                            }
                            Spacer(modifier = Modifier.width(6.scaledDp(scale)))
                            Box(
                                modifier = Modifier
                                    .size(22.scaledDp(scale))
                                    .clip(CircleShape)
                                    .background(HubBlue.copy(alpha = 0.22f))
                                    .border(1.dp, HubBlue.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Username",
                                    tint = HubBlue,
                                    modifier = Modifier.size(12.scaledDp(scale))
                                )
                            }
                        }

                        // Country Subtitle (Flag + Country Name, matching "San Francisco, CA" in IMG_0371.webp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(top = 3.scaledDp(scale))
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x660F172A))
                                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(50))
                                .clickable { onSelectCountry() }
                                .padding(horizontal = 12.scaledDp(scale), vertical = 4.scaledDp(scale))
                                .testTag("country_subtitle_pill")
                        ) {
                            Text(
                                text = "${countryOption.flagEmoji} ${countryOption.name}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.scaledSp(scale),
                                    color = Color(0xFF7DD3FC)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(5.scaledDp(scale)))
                            Text(
                                text = "• Change",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.scaledSp(scale),
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.scaledDp(scale)))

                        // Display Name & Email Subtitle
                        Text(
                            text = "$displayName • $emailText",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.scaledSp(scale)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.scaledDp(scale)))

                        // Compact Tag / Badge Cluster (inspired by avatar/badge cluster below location in IMG_0371)
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(6.scaledDp(scale)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isVerifiedDeveloper) {
                                VerifiedDeveloperGoldStarBadge(
                                    compact = true,
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .testTag("hero_verified_developer_badge")
                                )
                            }
                            equippedTags.take(3).forEach { tag ->
                                val tagAccent = Color(tag.category.accentColorHex)
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(tagAccent.copy(alpha = 0.2f))
                                        .border(1.dp, tagAccent.copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .clickable { onOpenTagPicker() }
                                        .padding(
                                            horizontal = 9.scaledDp(scale),
                                            vertical = 3.scaledDp(scale)
                                        )
                                ) {
                                    Text(
                                        text = tag.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = tagAccent,
                                            fontSize = 10.scaledSp(scale)
                                        )
                                    )
                                }
                            }
                            if (equippedTags.size > 3) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, HubBlue.copy(alpha = 0.5f), CircleShape)
                                        .clickable { onOpenTagPicker() }
                                        .padding(horizontal = 8.scaledDp(scale), vertical = 3.scaledDp(scale))
                                ) {
                                    Text(
                                        text = "+${equippedTags.size - 3}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            fontSize = 10.scaledSp(scale)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating 3-Column Summary Stat Bar Overlapping Bottom of Hero Card (matching IMG_0371.webp)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.scaledDp(scale))
                .offset(y = (-32).scaledDp(scale))
                .testTag("floating_profile_stats_bar"),
            shape = RoundedCornerShape(18.scaledDp(scale)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF111927)
            ),
            border = BorderStroke(1.2.dp, HubBlue.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 13.scaledDp(scale),
                        horizontal = 8.scaledDp(scale)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Column 1: Gamerscore
                FloatingStatColumn(
                    value = "${gamerscoreData.totalScore}",
                    label = "Gamerscore",
                    valueColor = SolarGold,
                    scale = scale,
                    onClick = onOpenGamerscoreInfo,
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(
                    modifier = Modifier.height(32.scaledDp(scale)),
                    thickness = 1.dp,
                    color = Color(0xFF26334D)
                )

                // Column 2: Rank Tier
                FloatingStatColumn(
                    value = gamerscoreData.rankBadgeText,
                    label = gamerscoreData.rankTitle,
                    valueColor = HubBlue,
                    scale = scale,
                    onClick = onOpenGamerscoreInfo,
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(
                    modifier = Modifier.height(32.scaledDp(scale)),
                    thickness = 1.dp,
                    color = Color(0xFF26334D)
                )

                // Column 3: Play Time
                FloatingStatColumn(
                    value = formattedAllSessionsTime,
                    label = "$totalSessionsCount Sessions",
                    valueColor = LaunchGreen,
                    scale = scale,
                    onClick = null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FloatingStatColumn(
    value: String,
    label: String,
    valueColor: Color,
    scale: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 4.scaledDp(scale))
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.scaledSp(scale),
                color = valueColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.scaledDp(scale)))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.scaledSp(scale),
                color = Color(0xFF94A3B8)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Leaderboard section card in the Profile tab. Pressing it carries the user to the Worldwide Leaderboard Tab.
 */
@Composable
private fun WorldwideLeaderboardCard(
    username: String,
    countryOption: CountryOption,
    gamerscore: Long,
    rankPosition: Int,
    totalCompetitors: Int,
    scale: Float,
    onOpenLeaderboard: () -> Unit
) {
    CyberCard(
        borderColor = SolarGold.copy(alpha = 0.65f),
        modifier = Modifier
            .clickable { onOpenLeaderboard() }
            .testTag("profile_leaderboard_section_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.scaledDp(scale))
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
                            .size(46.scaledDp(scale))
                            .clip(RoundedCornerShape(13.scaledDp(scale)))
                            .background(SolarGold.copy(alpha = 0.18f))
                            .border(1.dp, SolarGold.copy(alpha = 0.55f), RoundedCornerShape(13.scaledDp(scale))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = "Worldwide Leaderboard",
                            tint = SolarGold,
                            modifier = Modifier.size(24.scaledDp(scale))
                        )
                    }

                    Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.scaledDp(scale))
                        ) {
                            Text(
                                text = "WORLDWIDE LEADERBOARD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SolarGold,
                                    fontSize = 11.scaledSp(scale),
                                    letterSpacing = 0.8.sp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SolarGold.copy(alpha = 0.2f))
                                    .padding(horizontal = 7.scaledDp(scale), vertical = 2.dp)
                            ) {
                                Text(
                                    text = "RANK #$rankPosition",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SolarGold,
                                        fontSize = 9.scaledSp(scale)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.scaledDp(scale)))

                        Text(
                            text = "${countryOption.flagEmoji} @$username • $gamerscore GS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.scaledSp(scale),
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Tap to view global rankings by profile Gamerscore ($totalCompetitors players)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.scaledSp(scale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.scaledDp(scale)))

                Button(
                    onClick = onOpenLeaderboard,
                    shape = RoundedCornerShape(12.scaledDp(scale)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SolarGold,
                        contentColor = Color(0xFF090D16)
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 12.scaledDp(scale),
                        vertical = 8.scaledDp(scale)
                    ),
                    modifier = Modifier.testTag("open_leaderboard_button")
                ) {
                    Text(
                        text = "Rankings",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.scaledSp(scale)
                        )
                    )
                    Spacer(modifier = Modifier.width(2.scaledDp(scale)))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Leaderboard",
                        modifier = Modifier.size(16.scaledDp(scale))
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Worldwide Leaderboard Tab View.
 * Strictly displays ONLY:
 *  - Profile Image (with country flag badge on corner)
 *  - Username (@username)
 *  - Game Score (Gamerscore)
 */
@Composable
private fun WorldwideLeaderboardContent(
    entries: List<LeaderboardEntry>,
    currentUserUid: String,
    currentUsername: String,
    isLoading: Boolean,
    scale: Float,
    onBackToProfile: () -> Unit,
    onEditUsername: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.scaledDp(scale), vertical = 12.scaledDp(scale))
            .testTag("worldwide_leaderboard_tab")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.scaledDp(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackToProfile,
                    modifier = Modifier
                        .size(42.scaledDp(scale))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("leaderboard_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Profile",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(12.scaledDp(scale)))
                Column {
                    Text(
                        text = "Worldwide Leaderboard",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.scaledSp(scale),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Ranked by worldwide profile Gamerscore",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.scaledSp(scale),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = onEditUsername,
                shape = RoundedCornerShape(12.scaledDp(scale)),
                border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(
                    horizontal = 10.scaledDp(scale),
                    vertical = 6.scaledDp(scale)
                ),
                modifier = Modifier.testTag("leaderboard_edit_username_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "Edit Username",
                    tint = HubBlue,
                    modifier = Modifier.size(14.scaledDp(scale))
                )
                Spacer(modifier = Modifier.width(4.scaledDp(scale)))
                Text(
                    text = "Username",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.scaledSp(scale),
                        color = HubBlue
                    )
                )
            }
        }

        if (isLoading && entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HubBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.scaledDp(scale))
            ) {
                itemsIndexed(
                    items = entries,
                    key = { index, item -> "${item.userId}_${item.username}_$index" }
                ) { index, entry ->
                    val rank = index + 1
                    val isCurrentUser = entry.userId == currentUserUid ||
                        entry.username.equals(currentUsername, ignoreCase = true)

                    WorldwideLeaderboardRow(
                        rank = rank,
                        entry = entry,
                        isCurrentUser = isCurrentUser,
                        scale = scale
                    )
                }
            }
        }
    }
}

/**
 * Individual row in the Worldwide Leaderboard Tab.
 * Strictly shows ONLY:
 *  - Rank number (#1, #2, #3...)
 *  - Profile picture (with country flag badge on corner)
 *  - Username (@username)
 *  - Game score (Gamerscore)
 */
@Composable
private fun WorldwideLeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    scale: Float
) {
    val rankAccent = when (rank) {
        1 -> DeveloperGold
        2 -> Color(0xFFE2E8F0)
        3 -> Color(0xFFF59E0B)
        else -> if (isCurrentUser) HubBlue else Color(0xFF64748B)
    }

    val flagEmoji = remember(entry.countryCode) {
        CountryCatalog.codeToFlagEmoji(entry.countryCode)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank"),
        shape = RoundedCornerShape(16.scaledDp(scale)),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) Color(0xFF112236) else Color(0xFF0E1522)
        ),
        border = BorderStroke(
            width = if (rank <= 3 || isCurrentUser) 1.4.dp else 1.dp,
            color = when {
                rank == 1 -> DeveloperGold.copy(alpha = 0.8f)
                rank == 2 -> Color(0xFFE2E8F0).copy(alpha = 0.65f)
                rank == 3 -> Color(0xFFF59E0B).copy(alpha = 0.65f)
                isCurrentUser -> HubBlue.copy(alpha = 0.75f)
                else -> Color(0xFF1E293B)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.scaledDp(scale),
                    vertical = 11.scaledDp(scale)
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Rank Pill
                Box(
                    modifier = Modifier
                        .width(36.scaledDp(scale))
                        .height(28.scaledDp(scale))
                        .clip(RoundedCornerShape(8.scaledDp(scale)))
                        .background(rankAccent.copy(alpha = 0.16f))
                        .border(1.dp, rankAccent.copy(alpha = 0.5f), RoundedCornerShape(8.scaledDp(scale))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.scaledSp(scale),
                            color = rankAccent
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                // User Profile Image with Country Flag on Corner
                Box(
                    modifier = Modifier.size(48.scaledDp(scale)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.scaledDp(scale))
                            .clip(CircleShape)
                            .background(Color(0xFF0B111A))
                            .border(1.5.dp, rankAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = entry.photoUrl,
                                contentDescription = entry.username,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = entry.username,
                                tint = rankAccent,
                                modifier = Modifier.size(32.scaledDp(scale))
                            )
                        }
                    }

                    // Country Flag Badge on Profile Image Corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.scaledDp(scale))
                            .clip(CircleShape)
                            .background(Color(0xFF090D16))
                            .border(1.dp, rankAccent.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = flagEmoji,
                            fontSize = 10.scaledSp(scale)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                // Username ONLY
                Text(
                    text = "@${entry.username}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.scaledSp(scale),
                        color = if (rank == 1) DeveloperGold else Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(10.scaledDp(scale)))

            // Game Score ONLY
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.scaledDp(scale)))
                    .background(rankAccent.copy(alpha = 0.15f))
                    .border(1.dp, rankAccent.copy(alpha = 0.45f), RoundedCornerShape(10.scaledDp(scale)))
                    .padding(
                        horizontal = 10.scaledDp(scale),
                        vertical = 6.scaledDp(scale)
                    )
            ) {
                Text(
                    text = "${entry.gamerscore} GS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.scaledSp(scale),
                        color = if (rank == 1) DeveloperGold else LaunchGreen
                    )
                )
            }
        }
    }
}

/**
 * Smooth Cubic Bezier Area Chart inspired by IMG_0371.webp ("Performance Overview" chart
 * with Average / Peak stats, layered translucent blue waves, glowing cyan line, and dark peak callout pill).
 */
@Composable
private fun SmoothPlaytimeAreaChart(
    dailyStats: List<DailyPlayTime>,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val stats = remember(dailyStats) {
        if (dailyStats.isNotEmpty()) dailyStats else listOf(
            DailyPlayTime("MON", "Mon", 0L, 0L, 0L, false),
            DailyPlayTime("TUE", "Tue", 0L, 0L, 0L, false),
            DailyPlayTime("WED", "Wed", 0L, 0L, 0L, false),
            DailyPlayTime("THU", "Thu", 0L, 0L, 0L, false),
            DailyPlayTime("FRI", "Fri", 0L, 0L, 0L, false),
            DailyPlayTime("SAT", "Sat", 0L, 0L, 0L, false),
            DailyPlayTime("SUN", "Sun", 0L, 0L, 0L, true)
        )
    }

    val maxMinutes = remember(stats) {
        (stats.maxOfOrNull { it.totalMinutes } ?: 0L).coerceAtLeast(120L)
    }
    val avgMinutes = remember(stats) {
        if (stats.isEmpty()) 0L else stats.sumOf { it.totalMinutes } / stats.size
    }
    val peakIndex = remember(stats) {
        val maxVal = stats.maxOfOrNull { it.totalMinutes } ?: 0L
        val idx = stats.indexOfLast { it.totalMinutes == maxVal }
        if (idx >= 0) idx else stats.lastIndex.coerceAtLeast(0)
    }

    var selectedIndex by remember(peakIndex) { mutableIntStateOf(peakIndex) }
    val activeDay = stats.getOrNull(selectedIndex) ?: stats.last()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
    ) {
        // Sub-header: "Performance Overview" style Average & Peak metrics (matching IMG_0371.webp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.scaledDp(scale))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Average ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.scaledSp(scale)
                        )
                    )
                    Text(
                        text = "${avgMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = LaunchGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.scaledSp(scale)
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Peak ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.scaledSp(scale)
                        )
                    )
                    Text(
                        text = "${stats.getOrNull(peakIndex)?.totalMinutes ?: 0L}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RecorderCoral,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.scaledSp(scale)
                        )
                    )
                }
            }

            // Selected Day Floating Pill Callout (Dark navy pill with glowing gold indicator like "215" in IMG_0371)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.scaledDp(scale)),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.scaledDp(scale)))
                    .background(Color(0xFF0A192F))
                    .border(1.2.dp, HubBlue.copy(alpha = 0.65f), RoundedCornerShape(10.scaledDp(scale)))
                    .padding(horizontal = 10.scaledDp(scale), vertical = 4.scaledDp(scale))
            ) {
                Box(
                    modifier = Modifier
                        .size(8.scaledDp(scale))
                        .clip(CircleShape)
                        .background(SolarGold)
                )
                Text(
                    text = "${activeDay.dayLabel}: ${activeDay.totalMinutes}m (${activeDay.hours}h ${activeDay.minutesPart}m)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 11.scaledSp(scale)
                    )
                )
            }
        }

        // Smooth Area Chart Box with Y-Axis Labels & Cubic Bezier Waves
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.scaledDp(scale))
                .clip(RoundedCornerShape(16.scaledDp(scale)))
                .background(Color(0xFF09111E))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.scaledDp(scale)))
                .padding(
                    start = 8.scaledDp(scale),
                    end = 10.scaledDp(scale),
                    top = 14.scaledDp(scale),
                    bottom = 10.scaledDp(scale)
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Y-Axis scale labels (matching 200, 150, 100, 50, 0 in IMG_0371.webp)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 6.scaledDp(scale), bottom = 18.scaledDp(scale)),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val yLabels = listOf(
                        "${maxMinutes}m",
                        "${(maxMinutes * 3) / 4}m",
                        "${maxMinutes / 2}m",
                        "${maxMinutes / 4}m",
                        "0m"
                    )
                    yLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.scaledSp(scale),
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Canvas + X-Axis Day Selector
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val width = size.width
                        val height = size.height
                        val count = stats.size.coerceAtLeast(2)
                        val stepX = width / (count - 1).toFloat()

                        // Horizontal subtle grid lines
                        for (i in 0..4) {
                            val y = height * (i / 4f)
                            drawLine(
                                color = Color(0x1FFFFFFF),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f
                            )
                        }

                        // Compute primary data points
                        val points = stats.mapIndexed { idx, day ->
                            val x = idx * stepX
                            val normalized = (day.totalMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.08f, 0.92f)
                            val y = height * (1f - normalized)
                            Offset(x, y)
                        }

                        // Secondary background wave (like the dual translucent blue wave in IMG_0371.webp)
                        val secondaryWavePath = Path().apply {
                            val firstY = (points.first().y * 0.85f + height * 0.12f).coerceIn(height * 0.15f, height * 0.9f)
                            moveTo(0f, height)
                            lineTo(0f, firstY)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val y0 = (p0.y * 0.85f + height * 0.12f).coerceIn(height * 0.15f, height * 0.9f)
                                val y1 = (p1.y * 0.85f + height * 0.12f).coerceIn(height * 0.15f, height * 0.9f)
                                val controlX1 = (p0.x + p1.x) / 2f
                                cubicTo(controlX1, y0, controlX1, y1, p1.x, y1)
                            }
                            lineTo(width, height)
                            close()
                        }

                        drawPath(
                            path = secondaryWavePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x552563EB),
                                    Color(0x082563EB)
                                )
                            )
                        )

                        // Primary Cubic Bezier Area Path
                        val areaPath = Path().apply {
                            moveTo(points.first().x, height)
                            lineTo(points.first().x, points.first().y)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val controlX = (p0.x + p1.x) / 2f
                                cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                            }
                            lineTo(points.last().x, height)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    HubBlue.copy(alpha = 0.52f),
                                    Color(0xFF3B82F6).copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            )
                        )

                        // Glowing Top Curve Stroke
                        val strokePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val controlX = (p0.x + p1.x) / 2f
                                cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                            }
                        }

                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(HubBlue, LaunchGreen, HubBlue)
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Highlight Selected/Peak Point with Glowing Gold Ring & Vertical Guide
                        val activePt = points.getOrNull(selectedIndex) ?: points.last()
                        drawLine(
                            color = SolarGold.copy(alpha = 0.45f),
                            start = Offset(activePt.x, activePt.y),
                            end = Offset(activePt.x, height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawCircle(
                            color = SolarGold.copy(alpha = 0.3f),
                            radius = 9.dp.toPx(),
                            center = activePt
                        )
                        drawCircle(
                            color = SolarGold,
                            radius = 5.dp.toPx(),
                            center = activePt
                        )
                        drawCircle(
                            color = Color(0xFF09111E),
                            radius = 2.2.dp.toPx(),
                            center = activePt
                        )
                    }

                    Spacer(modifier = Modifier.height(6.scaledDp(scale)))

                    // X-Axis Day Labels (Tappable to inspect any day on the curve)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        stats.forEachIndexed { idx, day ->
                            val isSelected = idx == selectedIndex
                            Text(
                                text = day.dayLabel.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.scaledSp(scale),
                                    fontWeight = if (isSelected || day.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> SolarGold
                                        day.isToday -> LaunchGreen
                                        else -> Color(0xFF94A3B8)
                                    }
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedIndex = idx }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for creating or updating a unique @username across the worldwide leaderboard.
 */
@Composable
private fun EditUsernameDialog(
    currentUsername: String,
    isVerifiedDeveloper: Boolean,
    isChecking: Boolean,
    externalError: String?,
    onDismiss: () -> Unit,
    onSaveUsername: (String) -> Unit
) {
    var draftUsername by remember(currentUsername) { mutableStateOf(currentUsername) }
    val localValidationError: String? = remember(draftUsername, isVerifiedDeveloper) {
        PremadeTagsCatalog.validateUsername(draftUsername, isVerifiedDeveloper).exceptionOrNull()?.message
    }

    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = null,
                    tint = HubBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create / Edit Username",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Your @username appears on your gamer profile and the Worldwide Leaderboard. Usernames are unique worldwide (3–20 letters, numbers, or underscores).",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = draftUsername,
                    onValueChange = { input ->
                        val cleaned = input.removePrefix("@").take(PremadeTagsCatalog.MAX_USERNAME_LENGTH)
                        draftUsername = cleaned
                    },
                    label = { Text("Username") },
                    prefix = { Text("@", fontWeight = FontWeight.Bold, color = HubBlue) },
                    singleLine = true,
                    enabled = !isChecking,
                    isError = localValidationError != null || !externalError.isNullOrBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_text_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                val errorToShow = externalError ?: localValidationError
                if (!errorToShow.isNullOrBlank()) {
                    Text(
                        text = errorToShow,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RecorderCoral,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Text(
                        text = "Handle preview: @${draftUsername.trim()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = LaunchGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveUsername(draftUsername.trim()) },
                enabled = !isChecking && localValidationError == null && draftUsername.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF081018)
                ),
                modifier = Modifier.testTag("confirm_username_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF081018),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Save Username", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isChecking
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Searchable Country Picker Dialog to attach a country flag and country subtitle to the user's profile picture.
 */
@Composable
private fun CountryPickerDialog(
    selectedCountryCode: String,
    onDismiss: () -> Unit,
    onSelectCountry: (CountryOption) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        CountryCatalog.searchCountries(searchQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = LaunchGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Select Profile Country",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Your country flag badge will appear on your profile picture and below your username.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country or code (e.g. US, Japan, Brazil)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Country",
                            tint = HubBlue
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("country_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredCountries, key = { it.code }) { country ->
                        val isSelected = country.code.equals(selectedCountryCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) HubBlue.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) HubBlue else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectCountry(country) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("country_option_${country.code}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = country.flagEmoji,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = country.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = country.code,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = LaunchGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun EditDeletionUrlDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = HubBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configure Deletion URL",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your public link for Google Play Console Data Safety (Google Form or hosted page):",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Public URL") },
                    placeholder = { Text("https://forms.gle/...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deletion_url_text_field"),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val item = clipboard?.primaryClip?.getItemAt(0)
                            val text = item?.text?.toString()?.trim()
                            if (!text.isNullOrBlank()) {
                                urlText = text
                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Clipboard", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val raw = urlText.trim()
                    val formatted = if (raw.isNotBlank() && !raw.startsWith("http://") && !raw.startsWith("https://")) {
                        "https://$raw"
                    } else raw
                    onSaveUrl(formatted)
                },
                modifier = Modifier.testTag("save_deletion_url_button")
            ) {
                Text("Save Link", fontWeight = FontWeight.Bold)
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
private fun GoogleFormGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val templateQuestions = """
Form Title: TitanBooster Account & Data Deletion Request
Description: Submit this request to permanently delete your TitanBooster gamer account, Gamerscore, and cloud profile.
Question 1 (Required): Google Account Email used in TitanBooster
Question 2 (Required): Request Confirmation - "Please permanently delete my account and associated cloud data."
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SolarGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play Console Data Safety Guide",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Why preview container links returned 404:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = RecorderCoral)
                )
                Text(
                    text = "Google Play Console's verification crawler checks URLs from outside. AI Studio preview environments have an authentication barrier, so Google Play receives a 404 or redirect. Google Play requires a 24/7 public link.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Text(
                    text = "Fastest Fix (Google Forms - 60 seconds):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = LaunchGreen)
                )
                Text(
                    text = "Google Play officially approves Google Forms for account deletion!\n1. Open forms.google.com and create a new blank form.\n2. Title: 'TitanBooster Account & Data Deletion Request'.\n3. Add 2 fields:\n   • 'Your Google Account Email' (Short answer, Required)\n   • 'Request Confirmation' (Checkbox, Required)\n4. Click 'Send' > Link icon > 'Shorten URL' > Copy link.\n5. Paste that link into Google Play Console and into this app!",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Text(
                    text = "Alternative: Google Sites / GitHub Pages:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = HubBlue)
                )
                Text(
                    text = "You can also paste the included delete-account.html template onto Google Sites (sites.google.com) or GitHub Pages for a custom branded page.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Google Form Template", templateQuestions))
                    Toast.makeText(context, "Form template text copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy Form Template", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Renders the hero background banner based on preset ID or custom URI.
 */
@Composable
private fun ProfileBannerBackground(
    bannerKeyOrUri: String,
    modifier: Modifier = Modifier
) {
    val preset = remember(bannerKeyOrUri) {
        GamerscoreEngine.CYBER_BANNER_PRESETS.find { it.id.equals(bannerKeyOrUri, ignoreCase = true) }
    }

    if (preset != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(preset.startColorArgb),
                            Color(preset.endColorArgb)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 600f)
                    )
                )
        )
    } else if (bannerKeyOrUri.isNotBlank()) {
        AsyncImage(
            model = bannerKeyOrUri,
            contentDescription = "Custom Gamer Banner",
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        // Default cyberpunk synthwave gradient
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF311568),
                            Color(0xFF0B111A)
                        )
                    )
                )
        )
    }
}

@Composable
private fun GamerscoreMetricChip(
    label: String,
    value: String,
    accent: Color,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.scaledDp(scale)))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.scaledDp(scale)))
            .padding(horizontal = 8.scaledDp(scale), vertical = 6.scaledDp(scale)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.scaledSp(scale),
                fontWeight = FontWeight.Bold,
                color = accent
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.scaledSp(scale),
                color = MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Banner selector modal offering curated cyber presets and device photo upload.
 */
@Composable
private fun BannerChooserDialog(
    currentBanner: String,
    onDismiss: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onPickCustomPhoto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wallpaper,
                    contentDescription = null,
                    tint = HubBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gamer Profile Banner",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Upload a photo from your device or choose a curated Cyber Theme banner:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Button(
                    onClick = onPickCustomPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("upload_custom_banner_photo_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HubBlue,
                        contentColor = Color(0xFF071018)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Device Photo", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CURATED CYBER PRESETS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = HubBlue,
                        letterSpacing = 0.8.sp
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GamerscoreEngine.CYBER_BANNER_PRESETS) { preset ->
                        val isSelected = currentBanner.equals(preset.id, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(preset.startColorArgb),
                                            Color(preset.endColorArgb)
                                        )
                                    )
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(preset.accentColorArgb) else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectPreset(preset.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(preset.accentColorArgb),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Explains the uncapped Gamerscore algorithm and rank milestones.
 */
@Composable
private fun GamerscoreInfoDialog(
    gamerscoreData: GamerscoreData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(android.graphics.Color.parseColor(gamerscoreData.primaryColorHex)),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gamerscore System",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Your Gamerscore is uncapped and calculated in real-time based on cumulative play telemetry across all game sessions:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                GamerscoreRuleRow(title = "Total Playing Time", rule = "+10 GS per minute played")
                GamerscoreRuleRow(title = "Game Sessions", rule = "+50 GS per session completed")
                GamerscoreRuleRow(title = "Ten-Hour Milestone", rule = "+500 GS bonus every 10 hours")
                GamerscoreRuleRow(title = "Session Milestone", rule = "+250 GS bonus every 10 sessions")

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current Rank: [${gamerscoreData.rankBadgeText}] ${gamerscoreData.rankTitle} (${gamerscoreData.totalScore} GS). Advance through Recruit, Bronze, Silver, Gold, Platinum, Diamond, Master, Grandmaster, and Apex Legend without limits!",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(android.graphics.Color.parseColor(gamerscoreData.primaryColorHex)),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF071018)
                )
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun GamerscoreRuleRow(title: String, rule: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Text(
            text = rule,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = LaunchGreen
            )
        )
    }
}

/**
 * Sleek Gold-Star Developer Badge (retained exclusively for Albertomiller747@gmail.com).
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
                width = 1.4.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(DeveloperGold, DeveloperAmber, DeveloperGold)
                ),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 3.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Gold Star Developer",
            tint = DeveloperGold,
            modifier = Modifier.size(if (compact) 12.dp else 15.dp)
        )
        Text(
            text = "★ DEVELOPER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = DeveloperGold,
                fontSize = if (compact) 10.sp else 11.sp,
                letterSpacing = 0.6.sp
            )
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
                    text = "Select up to 4 tags to showcase on your profile.",
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
                    text = "Write a short bio for your gamer profile (up to 300 characters):",
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
