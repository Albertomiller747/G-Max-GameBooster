package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.service.ScreenRecordingService
import com.example.ui.components.SplashScreen
import com.example.ui.screens.AccountProfileScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExtrasScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ScreenRecordScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import com.example.ui.theme.TitanBoosterTheme
import com.example.viewmodel.GameBoosterViewModel
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {

    private val viewModel: GameBoosterViewModel by viewModels()
    private var triggerRecordingFromIntent by mutableStateOf(false)
    private var triggerScreenshotFromIntent by mutableStateOf(false)
    private var requestedTabFromIntent by mutableStateOf<String?>(null)
    private var requestedRecorderSubTab by mutableIntStateOf(0)

    // Compose-observable tab index so hardware controller L1/R1 bumpers can switch tabs directly
    private var selectedHubTabIndex by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("extra_trigger_recording", false)) {
            triggerRecordingFromIntent = true
            intent.removeExtra("extra_trigger_recording")
        }
        if (intent.getBooleanExtra("extra_trigger_screenshot", false)) {
            triggerScreenshotFromIntent = true
            intent.removeExtra("extra_trigger_screenshot")
        }
        val openTab = intent.getStringExtra("extra_open_tab")
        if (!openTab.isNullOrEmpty()) {
            requestedTabFromIntent = openTab
            requestedRecorderSubTab = intent.getIntExtra("extra_recorder_subtab", 0)
            intent.removeExtra("extra_open_tab")
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEfpsEngine()
        viewModel.refreshControllerState()
        viewModel.onAppResumed()
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        viewModel.controllerHealthManager.onGenericMotionEvent(ev)
        // If user is actively running the Stick Drift & Range test, lock controller stick input to the test
        if (viewModel.isControllerTestLocked.value) {
            return true
        }
        // Prevent idle analog stick micro-drift from triggering focus jumps or vibrations
        val isJoystick = (ev.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
                (ev.source and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
        if (isJoystick && ev.action == MotionEvent.ACTION_MOVE) {
            val maxAxis = maxOf(
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_X)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_Y)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_Z)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_RZ)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_HAT_X)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_HAT_Y))
            )
            if (maxAxis < 0.55f) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Record telemetry for Controller Health Check tab
        viewModel.controllerHealthManager.onKeyEvent(event)

        // If Stick Drift & Range test lock is active, consume controller inputs so UI doesn't scroll
        if (viewModel.isControllerTestLocked.value) {
            if (event.action == KeyEvent.ACTION_DOWN &&
                (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_B)
            ) {
                viewModel.setControllerTestLocked(false)
                return true
            }
            val isControllerOrDpad = KeyEvent.isGamepadButton(event.keyCode) ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            if (isControllerOrDpad) {
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_HOME -> {
                    // Controller Home / Guide button forces Big Screen Mode on even if Auto toggle is off
                    com.example.util.ConsoleHapticManager.playLaunchPulse(this)
                    selectedHubTabIndex = 0
                    viewModel.setBigScreenModeManual(true)
                    Toast.makeText(this, "Big Screen Console Mode Activated", Toast.LENGTH_SHORT).show()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    com.example.util.ConsoleHapticManager.playSelectClick(this)
                    selectedHubTabIndex = (selectedHubTabIndex - 1 + 5) % 5
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    com.example.util.ConsoleHapticManager.playSelectClick(this)
                    selectedHubTabIndex = (selectedHubTabIndex + 1) % 5
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    // Controller B button: exit manual Big Screen mode or return to Launcher tab
                    if (viewModel.isBigScreenModeManual.value) {
                        com.example.util.ConsoleHapticManager.playSelectClick(this)
                        viewModel.setBigScreenModeManual(false)
                        return true
                    }
                    if (selectedHubTabIndex != 0) {
                        com.example.util.ConsoleHapticManager.playSelectClick(this)
                        selectedHubTabIndex = 0
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    // Controller X button: Apply Quick Boost anywhere in the Hub
                    com.example.util.ConsoleHapticManager.playLaunchPulse(this)
                    viewModel.quickBoost(
                        targetApp = viewModel.selectedTargetGame.value,
                        mode = viewModel.selectedBoostMode.value
                    )
                    Toast.makeText(this, "Controller Quick Boost Activated!", Toast.LENGTH_SHORT).show()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    // Controller Y button: Toggle Gameplay Screen Recording anywhere in the Hub
                    com.example.util.ConsoleHapticManager.playSelectClick(this)
                    if (ScreenRecordingService.isRecording.value) {
                        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
                            action = ScreenRecordingService.ACTION_STOP
                        }
                        startService(stopIntent)
                        Toast.makeText(this, "Recording saved to Videos!", Toast.LENGTH_SHORT).show()
                    } else {
                        triggerRecordingFromIntent = true
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val currentTheme by viewModel.appTheme.collectAsState()
            val customBgColor by viewModel.customBackgroundColor.collectAsState()
            val hasCompletedAuthStep by viewModel.hasCompletedAuthStep.collectAsState()
            val currentFirebaseUser by viewModel.currentFirebaseUser.collectAsState()
            val cloudProfile by viewModel.cloudProfile.collectAsState()
            val userDateOfBirth by viewModel.userDateOfBirth.collectAsState()
            val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
            val showPermissionsPage by viewModel.showPermissionsPage.collectAsState()
            val reducedAnimations by viewModel.reducedAnimations.collectAsState()

            TitanBoosterTheme(
                appTheme = currentTheme,
                customBgArgb = customBgColor
            ) {
                val securityResult = remember {
                    com.example.util.AppSecurityManager.checkIntegrity(this@MainActivity)
                }

                if (!securityResult.isPassed) {
                    com.example.ui.screens.IntegrityViolationScreen(
                        violationReason = securityResult.violationReason
                    )
                } else {
                    var showSplash by rememberSaveable { mutableStateOf(true) }

                    if (showSplash && !reducedAnimations) {
                        SplashScreen(onAnimationComplete = { showSplash = false })
                    } else if (!hasCompletedAuthStep && currentFirebaseUser == null) {
                        // Sign In or Create Account page right after app launch (Splash) and before Welcome page (Onboarding)
                        AuthScreen(
                            initialDateOfBirth = userDateOfBirth,
                            onUpdateDateOfBirth = { dob ->
                                viewModel.setUserDateOfBirth(dob)
                            },
                            onAuthSuccess = {
                                viewModel.onUserSignedInSuccess()
                            },
                            onContinueAsGuest = {
                                viewModel.continueAsGuest()
                            },
                            onSaveCredentials = { fullName, email ->
                                viewModel.setCustomProfileCredentials(fullName, email)
                            }
                        )
                    } else if (!hasCompletedOnboarding || showPermissionsPage) {
                        // First-Launch Welcome Page -> Unified Permissions Page with "Not Now"
                        OnboardingScreen(
                            startOnPermissionsStep = showPermissionsPage && hasCompletedOnboarding,
                            onFinishOnboarding = {
                                viewModel.completeOnboarding()
                            }
                        )
                    } else {
                        val context = LocalContext.current
                        val isGamePickerVisible by viewModel.isGamePickerVisible.collectAsState()
                        val installedApps by viewModel.installedApps.collectAsState()
                        val selectedTargetGame by viewModel.selectedTargetGame.collectAsState()
                        val controllerAlert by viewModel.controllerAlert.collectAsState()

                        val mediaProjectionManager = remember {
                            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        }

                        // Video Recording MediaProjection Launcher
                        val videoProjectionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                                ScreenRecordingService.startRecording(
                                    context,
                                    result.resultCode,
                                    result.data!!
                                )
                            }
                        }

                        // Screenshot MediaProjection Launcher
                        val screenshotProjectionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                                ScreenRecordingService.captureScreenshot(
                                    context,
                                    result.resultCode,
                                    result.data!!
                                )
                            }
                        }

                        val requestVideoCapture = {
                            try {
                                videoProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val requestScreenshotCapture = {
                            try {
                                screenshotProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        LaunchedEffect(triggerRecordingFromIntent) {
                            if (triggerRecordingFromIntent) {
                                triggerRecordingFromIntent = false
                                selectedHubTabIndex = 1
                                requestVideoCapture()
                            }
                        }

                        LaunchedEffect(triggerScreenshotFromIntent) {
                            if (triggerScreenshotFromIntent) {
                                triggerScreenshotFromIntent = false
                                requestScreenshotCapture()
                            }
                        }

                        LaunchedEffect(requestedTabFromIntent) {
                            when (requestedTabFromIntent) {
                                "RECORD" -> {
                                    selectedHubTabIndex = 1
                                    requestedTabFromIntent = null
                                }
                                "LAUNCHER" -> {
                                    selectedHubTabIndex = 0
                                    requestedTabFromIntent = null
                                }
                                "EXTRAS" -> {
                                    selectedHubTabIndex = 2
                                    requestedTabFromIntent = null
                                }
                                "SETTINGS" -> {
                                    selectedHubTabIndex = 3
                                    requestedTabFromIntent = null
                                }
                                "PROFILE" -> {
                                    selectedHubTabIndex = 4
                                    requestedTabFromIntent = null
                                }
                            }
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    RazerInspiredTopHubBar(
                                        selectedTabIndex = selectedHubTabIndex,
                                        currentFirebaseUser = currentFirebaseUser,
                                        cloudPhotoUrl = cloudProfile?.photoUrl ?: "",
                                        onSelectTab = {
                                            com.example.util.ConsoleHapticManager.playSelectClick(context)
                                            selectedHubTabIndex = it
                                        }
                                    )
                                    // Animated top in-app controller connection/disconnection notification banner
                                    com.example.ui.components.ControllerConnectionBanner(
                                        alert = controllerAlert,
                                        onDismiss = { viewModel.dismissControllerAlert() }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (selectedHubTabIndex) {
                                    0 -> GamesScreen(viewModel = viewModel)
                                    1 -> ScreenRecordScreen(
                                        viewModel = viewModel,
                                        onRequestMediaProjection = requestVideoCapture,
                                        onRequestScreenshotProjection = requestScreenshotCapture,
                                        initialSubTab = requestedRecorderSubTab
                                    )
                                    2 -> ExtrasScreen(viewModel = viewModel)
                                    3 -> SettingsScreen(
                                        viewModel = viewModel,
                                        onOpenAccountProfile = { selectedHubTabIndex = 4 }
                                    )
                                    4 -> AccountProfileScreen(
                                        viewModel = viewModel,
                                        onBack = { selectedHubTabIndex = 0 }
                                    )
                                }
                            }

                            if (isGamePickerVisible) {
                                com.example.ui.components.GamePickerDialog(
                                    installedApps = installedApps,
                                    currentSelectedPackage = selectedTargetGame?.packageName,
                                    onGameSelected = { app ->
                                        viewModel.quickBoost(targetApp = app)
                                    },
                                    onDismiss = {
                                        viewModel.setGamePickerVisible(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TopHubNavTab(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val activeAccent: Color
)

/**
 * Razer Launcher-Inspired Top Hub Pill Navigation Bar:
 * - Active tab rendered inside a rounded pill with rocket/icon + label ("Launcher", "Recorder", "Extras", "Settings").
 * - Right beside Settings is the Profile icon / avatar button that opens User Account Info (Profile Picture, Most Played Game, Date of Birth, Account & Cloud Sync).
 */
@Composable
private fun RazerInspiredTopHubBar(
    selectedTabIndex: Int,
    currentFirebaseUser: FirebaseUser?,
    cloudPhotoUrl: String,
    onSelectTab: (Int) -> Unit
) {
    val tabs = remember {
        listOf(
            TopHubNavTab(
                index = 0,
                label = "Launcher",
                icon = Icons.Default.RocketLaunch,
                testTag = "nav_games",
                activeAccent = HubBlue
            ),
            TopHubNavTab(
                index = 1,
                label = "Recorder",
                icon = Icons.Default.Videocam,
                testTag = "nav_record",
                activeAccent = RecorderCoral
            ),
            TopHubNavTab(
                index = 2,
                label = "Extras",
                icon = Icons.Default.Tune,
                testTag = "nav_extras",
                activeAccent = HubBlue
            ),
            TopHubNavTab(
                index = 3,
                label = "Settings",
                icon = Icons.Default.Settings,
                testTag = "nav_settings",
                activeAccent = HubBlue
            )
        )
    }

    val isProfileSelected = selectedTabIndex == 4
    val isSignedIn = currentFirebaseUser != null
    val resolvedPhotoUrl = currentFirebaseUser?.photoUrl?.toString()?.takeIf { it.isNotBlank() }
        ?: cloudPhotoUrl.takeIf { it.isNotBlank() }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top navigation pills are touch-clickable and switched via L1/R1 on controller;
            // intentionally not D-Pad focusable so controller focus starts directly on game tiles.
            tabs.forEach { tab ->
                val isSelected = selectedTabIndex == tab.index

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onSelectTab(tab.index) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag(tab.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            // Profile Icon / Avatar Badge beside Settings on the navigation bar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isProfileSelected) {
                            HubBlue.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        }
                    )
                    .border(
                        width = if (isProfileSelected) 2.dp else 1.dp,
                        color = when {
                            isProfileSelected -> HubBlue
                            isSignedIn -> LaunchGreen.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        },
                        shape = CircleShape
                    )
                    .clickable { onSelectTab(4) }
                    .testTag("nav_profile"),
                contentAlignment = Alignment.Center
            ) {
                if (resolvedPhotoUrl != null) {
                    AsyncImage(
                        model = resolvedPhotoUrl,
                        contentDescription = "User Account Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = if (isSignedIn) Icons.Default.Person else Icons.Default.AccountCircle,
                        contentDescription = "User Account Profile",
                        tint = when {
                            isProfileSelected -> HubBlue
                            isSignedIn -> LaunchGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Cloud Sync Status Dot in bottom-right of Profile Icon when signed in
                if (isSignedIn) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(LaunchGreen)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
            }
        }
    }
}
