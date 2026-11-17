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
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.launch

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
    var showEditProfilePage by rememberSaveable { mutableStateOf(false) }
    var selectedExternalProfile by remember { mutableStateOf<com.example.util.ExternalPlayerProfileData?>(null) }
    var usernameSearchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchingUsername by remember { mutableStateOf(false) }
    var usernameSearchError by remember { mutableStateOf<String?>(null) }
    var closestSearchResults by remember { mutableStateOf<List<com.example.util.ExternalPlayerProfileData>>(emptyList()) }

    BackHandler {
        when {
            selectedExternalProfile != null -> selectedExternalProfile = null
            showEditProfilePage -> showEditProfilePage = false
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPlayStats()
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
    val isCheckingUsername by viewModel.isCheckingUsername.collectAsState()
    val usernameError by viewModel.usernameError.collectAsState()
    val customDisplayName by viewModel.customDisplayName.collectAsState()
    val customEmail by viewModel.customEmail.collectAsState()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsState()
    val userBannerUrl by viewModel.userBannerUrl.collectAsState()
    val selectedProfileTagIds by viewModel.selectedProfileTags.collectAsState()
    val isVerifiedDeveloper by viewModel.isVerifiedDeveloperAccount.collectAsState()
    val mostPlayedGames by viewModel.mostPlayedGames.collectAsState()
    val libraryGames by viewModel.libraryGames.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val totalPlayTimeSummary by viewModel.totalPlayTimeSummary.collectAsState()
    val selectedTargetGame by viewModel.selectedTargetGame.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val gamerscoreData by viewModel.gamerscoreData.collectAsState()
    val customDeletionUrl by viewModel.customDeletionUrl.collectAsState()
    val seasonHalvingCount by viewModel.seasonHalvingCount.collectAsState()
    val dailyStreakState by viewModel.dailyStreakState.collectAsState()
    val streakFeedback by viewModel.streakFeedbackMessage.collectAsState()

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
    var showProfileStreakHubModal by remember { mutableStateOf(false) }
    var showProfileStreakLostModal by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

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

    val isSignedIn = currentUser != null || isVerifiedDeveloper || socialSignedInProvider.isNotBlank() || customEmail.isNotBlank()
    val rawDisplayName = customDisplayName.takeIf { it.isNotBlank() }
        ?: currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: cloudProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: if (isVerifiedDeveloper) PremadeTagsCatalog.OFFICIAL_DEVELOPER_NAME
        else if (isSignedIn) "G Max Gamer" else "Guest Gamer"
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
    val lastSessionTarget = remember(totalMinutesAllSessions, mostPlayedGames.size, selectedTargetGame?.packageName) {
        com.example.util.BoosterSessionManager(context).getLastTargetGame()
    }
    val lastSessionPkg = lastSessionTarget.first.orEmpty()
    val lastSessionName = lastSessionTarget.second.orEmpty()
    val mostPlayedTitle: String = when {
        topLocalStat != null && topLocalStat.game.appName.isNotBlank() -> topLocalStat.game.appName
        lastSessionName.isNotBlank() && lastSessionName != "All Games" -> lastSessionName
        !cloudProfile?.mostPlayedGameName.isNullOrBlank() &&
            cloudProfile?.mostPlayedGameName != "No games played yet" &&
            cloudProfile?.mostPlayedGameName != "All Games" -> cloudProfile?.mostPlayedGameName.orEmpty()
        !selectedTargetGame?.appName.isNullOrBlank() -> selectedTargetGame?.appName.orEmpty()
        libraryGames.isNotEmpty() -> libraryGames.first().appName
        installedApps.any { it.isGame } -> installedApps.first { it.isGame }.appName
        else -> "No games played yet"
    }
    val mostPlayedGamePkg = remember(
        topLocalStat?.game?.packageName,
        lastSessionPkg,
        cloudProfile?.mostPlayedGamePkg,
        selectedTargetGame?.packageName,
        mostPlayedTitle,
        libraryGames,
        installedApps
    ) {
        val candidates = listOfNotNull(
            topLocalStat?.game?.packageName?.takeIf { it.isNotBlank() },
            lastSessionPkg.takeIf { it.isNotBlank() },
            cloudProfile?.mostPlayedGamePkg?.takeIf { it.isNotBlank() },
            selectedTargetGame?.packageName?.takeIf { it.isNotBlank() },
            libraryGames.firstOrNull { it.appName.equals(mostPlayedTitle, ignoreCase = true) }?.packageName,
            installedApps.firstOrNull { it.appName.equals(mostPlayedTitle, ignoreCase = true) }?.packageName,
            installedApps.firstOrNull {
                mostPlayedTitle != "No games played yet" &&
                    (it.appName.contains(mostPlayedTitle, ignoreCase = true) ||
                        mostPlayedTitle.contains(it.appName, ignoreCase = true))
            }?.packageName,
            libraryGames.firstOrNull()?.packageName,
            installedApps.firstOrNull { it.isGame }?.packageName
        )
        candidates.firstOrNull { pkg ->
            try {
                context.packageManager.getApplicationInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        } ?: candidates.firstOrNull().orEmpty()
    }
    val mostPlayedMinutes = when {
        topLocalStat != null -> (topLocalStat.totalPlayTimeMs / 60_000L).coerceAtLeast(1L)
        (cloudProfile?.mostPlayedMinutes ?: 0L) > 0L -> cloudProfile?.mostPlayedMinutes ?: 0L
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

    val seasonState = remember(seasonHalvingCount) {
        com.example.util.SeasonLeaderboardEngine.evaluateSeasonState(
            context = context,
            currentRank = 0
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val scale = (maxWidth.value / 390f).coerceIn(0.82f, 1.22f)
        val horizontalPad = 16.scaledDp(scale)

        val currentExternalProfile = selectedExternalProfile
        if (currentExternalProfile != null) {
            com.example.ui.components.ExternalUserProfileScreen(
                profile = currentExternalProfile,
                scale = scale,
                onBack = { selectedExternalProfile = null },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showEditProfilePage) {
            com.example.ui.components.EditProfileSubScreen(
                initialDisplayName = displayName,
                initialUsername = resolvedUsername,
                initialEmail = currentUser?.email ?: customEmail.ifBlank { emailText },
                initialBio = resolvedBio,
                initialDob = if (userDateOfBirth == "Not Set") "18.May.2001" else userDateOfBirth,
                userAvatarUrl = userAvatarUrl,
                countryOption = resolvedCountryOption,
                activeTopFiveBadge = seasonState.activeTopFiveBadge,
                isEmailVerified = currentUser?.isEmailVerified == true || isSignedIn,
                scale = scale,
                onBack = { showEditProfilePage = false },
                onPickAvatar = {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenCountryPicker = { showCountryPickerDialog = true },
                onOpenDobPicker = { showDobDialog = true },
                onVerifyEmailClicked = { emailToVerify ->
                    viewModel.setCustomProfileCredentials(displayName, emailToVerify)
                    if (currentUser == null) {
                        isSigningIn = true
                        UserAccountRepository.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                viewModel.onUserSignedInSuccess("Google")
                                Toast.makeText(context, "Google Account verified & linked!", Toast.LENGTH_SHORT).show()
                            },
                            onAuthError = { errMsg ->
                                isSigningIn = false
                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                isSigningIn = false
                                Toast.makeText(context, "Google Sign-In cancelled", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Email verified via Google Account!", Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveProfileChanges = { newName, newNickname, newEmail, newBio, newDob ->
                    val isVerifiedEmail = currentUser?.isEmailVerified == true || isSignedIn
                    val effectiveEmail = if (isVerifiedEmail) {
                        currentUser?.email ?: customEmail.ifBlank { newEmail }
                    } else {
                        newEmail
                    }
                    viewModel.setCustomProfileCredentials(newName, effectiveEmail)
                    viewModel.setUserBio(newBio)
                    viewModel.setUserDateOfBirth(newDob.ifBlank { "Not Set" })
                    if (newNickname.isNotBlank() && !newNickname.equals(resolvedUsername, ignoreCase = true)) {
                        viewModel.updateUsername(newNickname) { _, _ -> }
                    }
                    showEditProfilePage = false
                    Toast.makeText(context, "Profile settings saved!", Toast.LENGTH_SHORT).show()
                },
                equippedTags = equippedTags,
                currentBanner = userBannerUrl,
                onOpenTagPicker = { showTagPickerDialog = true },
                onOpenBannerPicker = { showBannerPickerDialog = true },
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
                // 1. PROFILE SETTING Header + Identity Summary Card
                item {
                    com.example.ui.components.ProfileSettingHeaderAndIdentityCard(
                        displayName = displayName,
                        username = resolvedUsername,
                        emailText = emailText,
                        bio = resolvedBio,
                        userAvatarUrl = userAvatarUrl,
                        countryOption = resolvedCountryOption,
                        isVerifiedDeveloper = isVerifiedDeveloper,
                        activeTopFiveBadge = seasonState.activeTopFiveBadge,
                        equippedTags = equippedTags,
                        scale = scale,
                        onBack = onBack,
                        onOpenEditProfile = { showEditProfilePage = true },
                        onOpenTagPicker = { showTagPickerDialog = true },
                        isVipStreakUnlocked = dailyStreakState.isVipBadgeUnlocked
                    )
                }

                // 1A. Daily Engagement Streak & G-Tokens Economy Profile Card
                item {
                    com.example.ui.components.ProfileDailyStreakCard(
                        streakState = dailyStreakState,
                        onOpenStreakHub = { showProfileStreakHubModal = true },
                        onOpenRepairDialog = { showProfileStreakLostModal = true }
                    )
                }

                // 1B. Search Player by Username / Display Name Card -> Ranked Closest Matches & Opens ExternalUserProfileScreen
                item {
                    CyberCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Player",
                                    tint = HubBlue,
                                    modifier = Modifier.size(18.scaledDp(scale))
                                )
                                Column {
                                    Text(
                                        text = com.example.util.tr("FIND PLAYER BY USERNAME OR NAME", "BUSCAR JUGADOR POR USUARIO O NOMBRE"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = HubBlue,
                                            fontSize = 11.scaledSp(scale),
                                            letterSpacing = 0.8.sp
                                        )
                                    )
                                    Text(
                                        text = com.example.util.tr(
                                            "Matches closest @username (unique) or display name (shared)",
                                            "Busca el @usuario (único) o nombre (compartido) más cercano"
                                        ),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.scaledSp(scale)
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.scaledDp(scale))
                            ) {
                                OutlinedTextField(
                                    value = usernameSearchQuery,
                                    onValueChange = { nextVal ->
                                        usernameSearchQuery = nextVal.removePrefix("@").take(32)
                                        usernameSearchError = null
                                        val q = usernameSearchQuery.trim()
                                        if (q.length >= 2) {
                                            coroutineScope.launch {
                                                val res = viewModel.searchClosestPlayerProfiles(q, maxResults = 5)
                                                closestSearchResults = res.getOrDefault(emptyList())
                                            }
                                        } else {
                                            closestSearchResults = emptyList()
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            text = com.example.util.tr("Enter @username or display name", "Ingresa @usuario o nombre"),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    prefix = {
                                        Text(
                                            text = "@",
                                            color = HubBlue,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.scaledDp(scale)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_player_username_input")
                                )

                                Button(
                                    onClick = {
                                        val q = usernameSearchQuery.trim()
                                        if (q.length < 2) {
                                            usernameSearchError = com.example.util.AppStrings.trText(
                                                "Enter at least 2 characters.",
                                                "Ingresa al menos 2 caracteres."
                                            )
                                            return@Button
                                        }
                                        isSearchingUsername = true
                                        usernameSearchError = null
                                        coroutineScope.launch {
                                            val res = viewModel.searchClosestPlayerProfiles(q, maxResults = 6)
                                            isSearchingUsername = false
                                            val matches = res.getOrDefault(emptyList())
                                            closestSearchResults = matches
                                            val topMatch = matches.firstOrNull()
                                            if (topMatch != null) {
                                                selectedExternalProfile = topMatch
                                            } else {
                                                usernameSearchError = res.exceptionOrNull()?.localizedMessage
                                                    ?: com.example.util.AppStrings.trText(
                                                        "No close player match found for \"$q\".",
                                                        "No se encontró ningún jugador cercano a \"$q\"."
                                                    )
                                            }
                                        }
                                    },
                                    enabled = !isSearchingUsername,
                                    shape = RoundedCornerShape(14.scaledDp(scale)),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HubBlue,
                                        contentColor = Color(0xFF06101E)
                                    ),
                                    modifier = Modifier
                                        .height(52.scaledDp(scale))
                                        .testTag("search_player_username_button")
                                ) {
                                    if (isSearchingUsername) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.scaledDp(scale)),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF06101E)
                                        )
                                    } else {
                                        Text(
                                            text = com.example.util.tr("View", "Ver"),
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        )
                                    }
                                }
                            }

                            if (closestSearchResults.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.scaledDp(scale)),
                                    verticalArrangement = Arrangement.spacedBy(6.scaledDp(scale))
                                ) {
                                    Text(
                                        text = com.example.util.tr("CLOSEST MATCHING ACCOUNTS", "CUENTAS MÁS CERCANAS"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.scaledSp(scale)
                                        )
                                    )
                                    closestSearchResults.forEachIndexed { idx, candidate ->
                                        Surface(
                                            onClick = { selectedExternalProfile = candidate },
                                            shape = RoundedCornerShape(12.scaledDp(scale)),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (idx == 0) HubBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("search_player_match_item_$idx")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.scaledDp(scale), vertical = 9.scaledDp(scale)),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${candidate.flagEmoji} ${candidate.displayName}",
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        )
                                                        if (idx == 0) {
                                                            Text(
                                                                text = com.example.util.tr("BEST MATCH", "MEJOR COINCIDENCIA"),
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    color = HubBlue,
                                                                    fontWeight = FontWeight.Black,
                                                                    fontSize = 9.sp
                                                                )
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "@${candidate.username} • ${candidate.gamerscore} GS",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                }
                                                Text(
                                                    text = com.example.util.tr("Open", "Abrir"),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        color = HubBlue,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val currentSearchError = usernameSearchError
                            if (!currentSearchError.isNullOrBlank()) {
                                Text(
                                    text = currentSearchError,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RecorderCoral,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                // 2. General Settings Card
                item {
                    com.example.ui.components.ProfileSettingSectionCard(
                        sectionTitle = com.example.util.tr("General", "General"),
                        scale = scale
                    ) {
                        com.example.ui.components.ProfileSettingActionRow(
                            icon = Icons.Default.AccountCircle,
                            iconTint = HubBlue,
                            title = com.example.util.tr("Edit Profile", "Editar Perfil"),
                            subtitle = com.example.util.tr(
                                "Change avatar, name, bio, DOB, profile tags & cyber banner",
                                "Cambiar avatar, nombre, biografía, fecha de nacimiento, etiquetas y banner"
                            ),
                            scale = scale,
                            testTag = "open_edit_profile_row",
                            onClick = { showEditProfilePage = true }
                        )
                        HorizontalDivider(color = Color(0xFF1A2639))
                        com.example.ui.components.ProfileSettingActionRow(
                            icon = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            iconTint = if (isSignedIn) LaunchGreen else HubBlue,
                            title = if (isSignedIn) {
                                com.example.util.tr("Google Account Connected", "Cuenta de Google Conectada")
                            } else {
                                com.example.util.tr("Connect Google Account", "Conectar Cuenta de Google")
                            },
                            subtitle = if (isSignedIn) {
                                com.example.util.tr(
                                    "${currentUser?.email ?: emailText} • Tap to sync settings to cloud",
                                    "${currentUser?.email ?: emailText} • Toca para sincronizar ajustes en la nube"
                                )
                            } else {
                                com.example.util.tr(
                                    "Sign in with Google for cloud backup & verified leaderboard identity",
                                    "Inicia sesión con Google para respaldo en la nube e identidad verificada en clasificación"
                                )
                            },
                            badgeText = if (isSignedIn) com.example.util.tr("LINKED", "VINCULADO") else com.example.util.tr("SIGN IN", "ENTRAR"),
                            badgeColor = if (isSignedIn) LaunchGreen else HubBlue,
                            scale = scale,
                            testTag = "general_google_account_row",
                            onClick = {
                                if (isSignedIn) {
                                    viewModel.syncAllSettingsToCloudNow()
                                    Toast.makeText(
                                        context,
                                        com.example.util.AppStrings.trText(
                                            "Syncing profile settings to Google Cloud...",
                                            "Sincronizando ajustes de perfil con Google Cloud..."
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    authError = null
                                    isSigningIn = true
                                    UserAccountRepository.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            viewModel.onUserSignedInSuccess("Google")
                                            Toast.makeText(
                                                context,
                                                com.example.util.AppStrings.trText(
                                                    "Connected to Google Account!",
                                                    "¡Conectado a la cuenta de Google!"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onAuthError = { errMsg ->
                                            isSigningIn = false
                                            authError = errMsg
                                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                        },
                                        scope = coroutineScope,
                                        onAuthCancelled = {
                                            isSigningIn = false
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

                // 3. Performance Overview & Total Gaming Time (Smooth Area Chart)
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
                                            text = com.example.util.tr("TOTAL GAMING TIME", "TIEMPO TOTAL DE JUEGO"),
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
                                                text = com.example.util.tr(
                                                    "across $totalSessionsCount ${if (totalSessionsCount == 1L) "session" else "sessions"}",
                                                    "en $totalSessionsCount ${if (totalSessionsCount == 1L) "sesión" else "sesiones"}"
                                                ),
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
                                            text = com.example.util.tr("GAMERSCORE", "PUNTUACIÓN GAMER"),
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
                                        text = com.example.util.tr(
                                            "${gamerscoreData.rankTier} • Progress to Next Rank",
                                            "${gamerscoreData.rankTier} • Progreso al Siguiente Rango"
                                        ),
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
                                    label = com.example.util.tr("Play Time", "Tiempo"),
                                    value = "+${gamerscoreData.playTimePoints} pts",
                                    accent = HubBlue,
                                    scale = scale,
                                    modifier = Modifier.weight(1f)
                                )
                                GamerscoreMetricChip(
                                    label = com.example.util.tr("Sessions", "Sesiones"),
                                    value = "+${gamerscoreData.sessionsPoints} pts",
                                    accent = LaunchGreen,
                                    scale = scale,
                                    modifier = Modifier.weight(1f)
                                )
                                GamerscoreMetricChip(
                                    label = com.example.util.tr("Milestones", "Logros"),
                                    value = "+${gamerscoreData.milestoneBonusPoints} pts",
                                    accent = SolarGold,
                                    scale = scale,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 6. Most Played Game Card (renders real installed game icon when available)
                item {
                    CyberCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (mostPlayedGamePkg.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(46.scaledDp(scale))
                                        .clip(RoundedCornerShape(14.scaledDp(scale)))
                                        .border(
                                            width = 1.5.dp,
                                            color = LaunchGreen.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(14.scaledDp(scale))
                                        )
                                        .testTag("most_played_game_real_icon")
                                ) {
                                    AppIconImage(
                                        packageName = mostPlayedGamePkg,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                com.example.ui.components.GMaxBrandIcon(
                                    size = 46.scaledDp(scale),
                                    cornerRadius = 14.scaledDp(scale),
                                    showGlowBorder = true,
                                    glowColor = LaunchGreen,
                                    contentDescription = "Most Played Game"
                                )
                            }

                            Spacer(modifier = Modifier.width(12.scaledDp(scale)))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = com.example.util.tr("MOST PLAYED GAME", "JUEGO MÁS JUGADO"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LaunchGreen,
                                        fontSize = 10.scaledSp(scale),
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.scaledDp(scale)))
                                Text(
                                    text = if (mostPlayedTitle == "No games played yet") {
                                        com.example.util.tr("No games played yet", "Aún no se han jugado juegos")
                                    } else {
                                        mostPlayedTitle
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.scaledSp(scale),
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (mostPlayedMinutes <= 0L) {
                                        com.example.util.tr("Launch any game to track play time", "Inicia cualquier juego para registrar el tiempo")
                                    } else {
                                        formattedPlayTime
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.scaledSp(scale),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // 7. Preferences Card (Log Out)
                item {
                    com.example.ui.components.ProfileSettingSectionCard(
                        sectionTitle = com.example.util.tr("Preferences", "Preferencias"),
                        scale = scale
                    ) {
                        com.example.ui.components.ProfileSettingActionRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = RecorderCoral,
                            title = com.example.util.tr("Log Out", "Cerrar Sesión"),
                            subtitle = com.example.util.tr("Securely log out of Account", "Cerrar sesión de forma segura"),
                            titleColor = RecorderCoral,
                            scale = scale,
                            testTag = "preferences_logout_row",
                            onClick = {
                                UserAccountRepository.signOut(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {
                                        viewModel.onUserSignedOut()
                                        Toast.makeText(
                                            context,
                                            com.example.util.AppStrings.trText(
                                                "Logged out of your account",
                                                "Sesión cerrada correctamente"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    scope = coroutineScope
                                )
                            }
                        )
                    }
                }

                // 7b. Base64 HUD & Crosshair Config Codes (Shareable in WhatsApp, Discord & TikTok)
                item(key = "profile_base64_config_codes_card") {
                    Base64ConfigCodesProfileCard(
                        viewModel = viewModel,
                        scale = scale
                    )
                }

                // 8. Account & Cloud Sync Card
                item {
                    CyberCard {
                        SectionTitle(
                            title = com.example.util.tr("Account & Cloud Sync", "Cuenta y Sincronización en la Nube"),
                            subtitle = com.example.util.tr(
                                "Automatically remembers and restores your previous settings, tags, avatars, and chosen options",
                                "Recuerda y restaura automáticamente tus ajustes anteriores, etiquetas, avatares y opciones elegidas"
                            )
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
                                        text = if (isSignedIn) {
                                            com.example.util.tr("Last backup: $lastSyncedFormatted", "Último respaldo: $lastSyncedFormatted")
                                        } else {
                                            com.example.util.tr(
                                                "Sign in to backup settings, tags, avatar & game playtime",
                                                "Inicia sesión para respaldar ajustes, etiquetas, avatar y tiempo de juego"
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.scaledSp(scale),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.scaledDp(scale)))

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
                                        text = com.example.util.tr("Sync Settings Now", "Sincronizar Ahora"),
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
                                        text = com.example.util.tr("Sign Out", "Salir"),
                                        color = RecorderCoral,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.scaledSp(scale)
                                    )
                                }
                            }

                            if (!isSignedIn) {
                                Spacer(modifier = Modifier.height(10.scaledDp(scale)))
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
                                                Toast.makeText(
                                                    context,
                                                    com.example.util.AppStrings.trText(
                                                        "Google Account linked for Cloud Backup!",
                                                        "¡Cuenta de Google vinculada para respaldo en la nube!"
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onAuthError = { errMsg ->
                                                isSigningIn = false
                                                authError = errMsg
                                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
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
                                        .height(48.dp)
                                        .testTag("link_real_google_account_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LaunchGreen,
                                        contentColor = Color(0xFF081018)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "G",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF4285F4)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = com.example.util.tr("Link Google Account for Cloud Sync", "Vincular Cuenta de Google para la Nube"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                                    )
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
                                    text = com.example.util.tr("Delete Account & Data (In-App)", "Eliminar Cuenta y Datos (En la App)"),
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
                                            onAuthError = { errMsg ->
                                                isSigningIn = false
                                                authError = errMsg
                                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
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
                                        text = com.example.util.tr("Sign In / Create Account with Google", "Iniciar Sesión / Crear Cuenta con Google"),
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

    if (showProfileStreakHubModal) {
        com.example.ui.components.DailyStreakHubModalDialog(
            streakState = dailyStreakState,
            feedbackMessage = streakFeedback,
            onBuyStreakFreeze = {
                viewModel.buyStreakFreeze()
            },
            onSelectNeonColor = { hexColor ->
                viewModel.selectStreakHudNeonColor(hexColor)
            },
            onOpenRepairModal = {
                showProfileStreakHubModal = false
                showProfileStreakLostModal = true
            },
            onDismiss = {
                viewModel.clearStreakFeedbackMessage()
                showProfileStreakHubModal = false
            }
        )
    }

    if (showProfileStreakLostModal && dailyStreakState.isStreakBrokenRepairable) {
        com.example.ui.components.StreakLostRepairModalDialog(
            streakState = dailyStreakState,
            onRepairViaRewardedAd = {
                viewModel.repairBrokenStreakAfterRewardedAd()
                showProfileStreakLostModal = false
                showProfileStreakHubModal = true
            },
            onLetStreakReset = {
                viewModel.resetBrokenStreakToZero()
                showProfileStreakLostModal = false
            },
            onDismiss = {
                showProfileStreakLostModal = false
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
                    text = "Your @username appears on your gamer profile and the Worldwide Leaderboard. Usernames are unique worldwide (3 to 20 letters, numbers, or underscores).",
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
                    val activeCategory = selectedCategory
                    val categoriesToShow = if (activeCategory != null) {
                        listOf(activeCategory)
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
                    placeholder = { Text("e.g. 120Hz FPS & MOBA player • Console Controller main") },
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

@Composable
private fun Base64ConfigCodesProfileCard(
    viewModel: GameBoosterViewModel,
    scale: Float
) {
    val context = LocalContext.current
    val overlaySettings by viewModel.overlaySettings.collectAsState()
    val overlayConfig by viewModel.overlayConfig.collectAsState()
    val controllerOverlayConfig by viewModel.controllerOverlayConfig.collectAsState()
    val controllerLayoutMode by viewModel.controllerLayoutMode.collectAsState()
    val currentFirebaseUser by viewModel.currentFirebaseUser.collectAsState()
    val userUsername by viewModel.userUsername.collectAsState()
    val appliedOptionIds by viewModel.appliedOptionIds.collectAsState()
    val selectedBoostMode by viewModel.selectedBoostMode.collectAsState()

    val activeAccountKey = remember(currentFirebaseUser, userUsername) {
        viewModel.getAccountKey()
    }
    val accountHash = remember(activeAccountKey) {
        com.example.util.ConfigCodeCodec.computeAccountUidHash(activeAccountKey)
    }
    val creatorTag = remember(userUsername, activeAccountKey) {
        val raw = userUsername.ifBlank { activeAccountKey }.removePrefix("@")
        "@$raw"
    }

    val currentCode = remember(
        overlaySettings,
        overlayConfig,
        controllerOverlayConfig,
        controllerLayoutMode,
        activeAccountKey,
        userUsername,
        appliedOptionIds,
        selectedBoostMode
    ) {
        viewModel.generateCurrentConfigCode()
    }
    val currentJson = remember(
        overlaySettings,
        overlayConfig,
        controllerOverlayConfig,
        controllerLayoutMode,
        activeAccountKey,
        userUsername,
        appliedOptionIds,
        selectedBoostMode
    ) {
        viewModel.generateCurrentConfigJson()
    }

    var showJsonPreview by rememberSaveable { mutableStateOf(false) }
    var importCodeInput by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isStatusError by rememberSaveable { mutableStateOf(false) }

    CyberCard(
        modifier = Modifier.testTag("profile_base64_config_card"),
        borderColor = HubBlue.copy(alpha = 0.45f)
    ) {
        SectionTitle(
            title = com.example.util.tr(
                "Base64 HUD & Crosshair Config Codes",
                "Códigos Base64 de HUD y Mira"
            ),
            subtitle = com.example.util.tr(
                "Account-unique creator-signed v2 code bundling HUD X/Y, Controller, Volume Lock, Hardcore Mode, Recorder, Tweaks & Overlay and Focus Mode Timer Auto-Start",
                "Código v2 único por cuenta con HUD X/Y, Control, Bloqueo de Volumen, Modo Hardcore, Grabadora, Ajustes y Auto-Inicio de Temporizador de Superposición y Modo Enfoque"
            ),
            badge = "V2 • #$accountHash",
            badgeColor = HubBlue
        )

        Spacer(modifier = Modifier.height(10.scaledDp(scale)))

        // Account Creator Signature Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LaunchGreen.copy(alpha = 0.12f))
                .border(1.dp, LaunchGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("config_code_account_signature_badge"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.example.util.tr(
                    "Creator Signed: $creatorTag • ID #$accountHash",
                    "Firmado por: $creatorTag • ID #$accountHash"
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = LaunchGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.scaledSp(scale)
                )
            )
            Text(
                text = "${36 + appliedOptionIds.size} Settings",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SolarGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.scaledSp(scale)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.scaledDp(scale)))

        // 1. Live Generated GMAX-... Config Code Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.scaledDp(scale)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = BorderStroke(1.dp, HubBlue.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(12.scaledDp(scale))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.tr("YOUR ACTIVE CONFIG CODE", "TU CÓDIGO DE CONFIGURACIÓN ACTIVO"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = HubBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.scaledSp(scale)
                        )
                    )
                    Text(
                        text = if (showJsonPreview) {
                            com.example.util.tr("Hide JSON", "Ocultar JSON")
                        } else {
                            com.example.util.tr("View JSON", "Ver JSON")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SolarGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.scaledSp(scale)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showJsonPreview = !showJsonPreview }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("toggle_config_json_preview_button")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentCode,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.scaledSp(scale)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("generated_gmax_config_code_text")
                )

                AnimatedVisibility(visible = showJsonPreview) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0F1D))
                                .border(1.dp, HubBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = currentJson,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.testTag("generated_gmax_config_json_text")
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.scaledDp(scale)))

        // 2. Copy & Share Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.scaledDp(scale))
        ) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("GMAX Config Code", currentCode))
                    isStatusError = false
                    statusMessage = com.example.util.AppStrings.trText(
                        "Copied GMAX Config Code to clipboard!",
                        "¡Código GMAX copiado al portapapeles!"
                    )
                    com.example.ui.components.ActionFeedbackController.emitFeedback(
                        context = context,
                        message = "Config Code Copied",
                        accentColorArgb = 0xFF00E5FFL
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("copy_config_code_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color(0xFF081018)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = com.example.util.tr("Copy Code", "Copiar Código"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.scaledSp(scale)
                )
            }

            OutlinedButton(
                onClick = {
                    val shareText = "Check out my G-Max HUD & Crosshair loadout! Paste this code in Account Profile:\n$currentCode"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    try {
                        context.startActivity(
                            Intent.createChooser(sendIntent, "Share G-Max Config Code").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("share_config_code_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LaunchGreen.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = LaunchGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = com.example.util.tr("Share Code", "Compartir"),
                    color = LaunchGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.scaledSp(scale)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.scaledDp(scale)))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(12.scaledDp(scale)))

        // 3. Paste & Decode Another Player's GMAX-... Code
        Text(
            text = com.example.util.tr(
                "Import Squadmate's Config Code",
                "Importar Código de Compañero"
            ),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.scaledSp(scale)
            )
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = importCodeInput,
            onValueChange = {
                importCodeInput = it
                statusMessage = null
            },
            singleLine = true,
            placeholder = {
                Text(
                    text = com.example.util.tr(
                        "Paste GMAX-8f7B2... code or JSON string",
                        "Pega el código GMAX-8f7B2... o JSON"
                    ),
                    fontSize = 12.sp
                )
            },
            trailingIcon = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (clipText.isNotBlank()) {
                            importCodeInput = clipText.trim()
                        }
                    }
                ) {
                    Text(
                        text = com.example.util.tr("Paste", "Pegar"),
                        color = HubBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("paste_config_code_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                val decoded = viewModel.applyDecodedConfigCode(importCodeInput)
                if (decoded != null) {
                    isStatusError = false
                    statusMessage = com.example.util.AppStrings.trText(
                        "✅ ${decoded.summaryLabelEn}",
                        "✅ ${decoded.summaryLabelEs}"
                    )
                } else {
                    isStatusError = true
                    statusMessage = com.example.util.AppStrings.trText(
                        "Invalid GMAX-... config code. Make sure you copied the full code.",
                        "Código GMAX-... inválido. Asegúrate de copiar el código completo."
                    )
                }
            },
            enabled = importCodeInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("apply_config_code_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LaunchGreen,
                contentColor = Color(0xFF081018)
            )
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = com.example.util.tr(
                    "Decode & Apply Layout Instantly",
                    "Decodificar y Aplicar al Instante"
                ),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.scaledSp(scale)
            )
        }

        AnimatedVisibility(visible = statusMessage != null) {
            Text(
                text = statusMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isStatusError) RecorderCoral else LaunchGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.scaledSp(scale)
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("config_code_status_message")
            )
        }
    }
}

