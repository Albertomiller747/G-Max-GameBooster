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
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.ShoppingBag
import com.example.service.ScreenRecordingService
import com.example.ui.components.SplashScreen
import com.example.ui.screens.AccountProfileScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExtrasScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ScreenRecordScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TopGearScreen
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.lifecycle.lifecycleScope
import com.example.ui.components.ActionFeedbackController
import com.example.ui.components.InstantFeedbackBannerHost
import com.example.ui.components.gmaxInteractivePress
import com.example.ui.theme.GMaxGamingHubTheme
import com.example.viewmodel.GameBoosterViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: GameBoosterViewModel by viewModels()
    private var triggerRecordingFromIntent by mutableStateOf(false)
    private var triggerScreenshotFromIntent by mutableStateOf(false)
    private var requestedTabFromIntent by mutableStateOf<String?>(null)
    private var requestedRecorderSubTab by mutableIntStateOf(0)
    private var requestedExtrasSubTab by mutableIntStateOf(0)

    // Compose-observable tab index so hardware controller L1/R1 bumpers can switch tabs directly
    private var selectedHubTabIndex by mutableIntStateOf(0)
    private var isShowingSplashScreen by mutableStateOf(false)

    private fun isAdServicesIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action.orEmpty()
        val pkg = intent.`package`.orEmpty()
        val componentClass = intent.component?.className.orEmpty()
        return action.startsWith("android.adservices.") ||
            action.contains("MEASUREMENT_SERVICE", ignoreCase = true) ||
            pkg.contains("adservices", ignoreCase = true) ||
            componentClass.startsWith("android.adservices.")
    }

    override fun getSystemServiceName(serviceClass: Class<*>): String? {
        if (serviceClass.name.startsWith("android.adservices.")) {
            return null
        }
        return super.getSystemServiceName(serviceClass)
    }

    override fun getSystemService(name: String): Any? {
        if (name.contains("adservices", ignoreCase = true) || name == "measurement") {
            return null
        }
        val service = super.getSystemService(name)
        if (service != null && service.javaClass.name.startsWith("android.adservices.")) {
            return null
        }
        return service
    }

    override fun bindService(
        service: Intent,
        conn: android.content.ServiceConnection,
        flags: Int
    ): Boolean {
        if (isAdServicesIntent(service)) {
            return false
        }
        return super.bindService(service, conn, flags)
    }

    override fun bindService(
        service: Intent,
        flags: Int,
        executor: java.util.concurrent.Executor,
        conn: android.content.ServiceConnection
    ): Boolean {
        if (isAdServicesIntent(service)) {
            return false
        }
        return super.bindService(service, flags, executor, conn)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        try {
            if (intent.getBooleanExtra("extra_trigger_recording", false)) {
                triggerRecordingFromIntent = true
                intent.removeExtra("extra_trigger_recording")
            }
            if (intent.getBooleanExtra("extra_trigger_screenshot", false)) {
                triggerScreenshotFromIntent = true
                intent.removeExtra("extra_trigger_screenshot")
            }
            val openTabStr = intent.getStringExtra("extra_open_tab")
            val openTabInt = intent.getIntExtra("extra_open_tab", -1)
            val resolvedOpenTab = when {
                !openTabStr.isNullOrEmpty() -> openTabStr
                openTabInt == 1 -> "RECORD"
                openTabInt == 0 -> "LAUNCHER"
                else -> null
            }
            if (!resolvedOpenTab.isNullOrEmpty()) {
                requestedTabFromIntent = resolvedOpenTab
                requestedRecorderSubTab = intent.getIntExtra("extra_recorder_subtab", 0)
                requestedExtrasSubTab = intent.getIntExtra("extra_extras_subtab", 0)
                intent.removeExtra("extra_open_tab")
            }
            if (intent.getBooleanExtra("extra_open_review_prompt", false)) {
                com.example.util.AppReviewManager.onReviewNotificationTapped(this)
                intent.removeExtra("extra_open_review_prompt")
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        com.example.util.ControllerHealthManager.setAppInForeground(true)
        com.example.util.AppReviewManager.onAppResumedCheckVerification(this)
        viewModel.onAppResumed()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                viewModel.refreshEfpsEngine()
                viewModel.refreshControllerState()
                com.example.service.ControllerOverlayService.syncService(this@MainActivity)
            } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        com.example.util.ControllerHealthManager.setAppInForeground(false)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppPausedOrStopped()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.example.util.AppMemoryManager.onTrimMemory(this, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.example.util.AppMemoryManager.onLowMemory(this)
    }

    private var isStickNavigationActive = false

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        viewModel.controllerHealthManager.onGenericMotionEvent(ev)
        // If user is actively running the Stick Drift & Range test, lock controller stick input to the test
        if (viewModel.isControllerTestLocked.value) {
            return true
        }
        val isJoystick = (ev.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
            (ev.source and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD ||
            (ev.source and android.view.InputDevice.SOURCE_DPAD) == android.view.InputDevice.SOURCE_DPAD
        if (isJoystick && ev.action == MotionEvent.ACTION_MOVE) {
            val maxAxis = maxOf(
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_X)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_Y)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_Z)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_RZ)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_HAT_X)),
                kotlin.math.abs(ev.getAxisValue(MotionEvent.AXIS_HAT_Y))
            )
            if (maxAxis >= 0.60f && !isStickNavigationActive) {
                isStickNavigationActive = true
                com.example.util.ConsoleHapticManager.playFocusTick(this)
            } else if (maxAxis < 0.35f) {
                isStickNavigationActive = false
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Record telemetry for Controller Health Check & Real-Time Controller Input Overlay
        viewModel.controllerHealthManager.onKeyEvent(event)

        // If on PS4-style Console Startup / Login Gate, route Controller Home/Start/A button to log in
        if (isShowingSplashScreen) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_MODE,
                    KeyEvent.KEYCODE_HOME,
                    KeyEvent.KEYCODE_BUTTON_START,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        com.example.util.ConsoleHapticManager.playLaunchPulse(this)
                        com.example.util.ControllerHealthManager.notifyHomeLoginButtonPressed()
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }

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

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    com.example.util.ConsoleHapticManager.playFocusTick(this)
                }
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (event.repeatCount == 0) {
                        com.example.util.ConsoleHapticManager.playSelectClick(this)
                    }
                }
            }
        }

        // When the user is inside the Controller Studio (Extras > Controller or Controls Overlay),
        // consume gamepad face/shoulder/mode buttons directly for live controller testing so pressing
        // B, X, Y, L1, or R1 never switches tabs, launches a game, or starts screen recording.
        if (com.example.util.ControllerHealthManager.isControllerStudioActive) {
            if (KeyEvent.isGamepadButton(event.keyCode) ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE
            ) {
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_MODE -> {
                    com.example.util.ConsoleHapticManager.playLaunchPulse(this)
                    selectedHubTabIndex = 0
                    val isCurrentlyConsole = viewModel.isBigScreenModeManual.value
                    if (isCurrentlyConsole) {
                        viewModel.exitConsoleModeImmediately()
                    } else {
                        viewModel.setBigScreenModeManual(true)
                    }
                    Toast.makeText(
                        this,
                        if (!isCurrentlyConsole) {
                            com.example.util.AppStrings.trText(
                                "Big Screen Console Mode Activated",
                                "Modo Consola en Pantalla Grande Activado"
                            )
                        } else {
                            com.example.util.AppStrings.trText(
                                "Exited Big Screen Console Mode",
                                "Modo Consola Desactivado"
                            )
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    com.example.util.ConsoleHapticManager.playTabSwitch(this)
                    selectedHubTabIndex = (selectedHubTabIndex - 1 + 6) % 6
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    com.example.util.ConsoleHapticManager.playTabSwitch(this)
                    selectedHubTabIndex = (selectedHubTabIndex + 1) % 6
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    // Controller B button: exit manual Big Screen mode or return to Launcher tab
                    com.example.util.ConsoleHapticManager.playBackTone(this)
                    if (viewModel.isBigScreenModeManual.value) {
                        viewModel.exitConsoleModeImmediately()
                        return true
                    }
                    if (selectedHubTabIndex != 0) {
                        selectedHubTabIndex = 0
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    if (selectedHubTabIndex == 0) {
                        // Controller Square / X button on Launcher tab: Boost and launch the currently focused game tile directly
                        com.example.util.ConsoleHapticManager.playLaunchPulse(this)
                        val target = viewModel.boostAndLaunchFocusedGame()
                        val gameLabel = target?.appName ?: com.example.util.AppStrings.trText("Selected Game", "Juego Seleccionado")
                        Toast.makeText(
                            this,
                            com.example.util.AppStrings.trText(
                                "Launching $gameLabel with Focus Mode",
                                "Iniciando $gameLabel con Modo Enfoque"
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (selectedHubTabIndex == 0 || selectedHubTabIndex == 1) {
                        // Controller Y button: Toggle Gameplay Screen Recording from Launcher or Recorder tab
                        com.example.util.ConsoleHapticManager.playSelectClick(this)
                        if (ScreenRecordingService.isRecording.value) {
                            val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
                                action = ScreenRecordingService.ACTION_STOP
                            }
                            startService(stopIntent)
                            Toast.makeText(
                                this,
                                com.example.util.AppStrings.trText(
                                    "Recording saved to Videos!",
                                    "¡Grabación guardada en Videos!"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            com.example.util.UnityAdsManager.maybeTriggerPostActionAd(
                                this,
                                "Screen Recording",
                                "Grabación de Pantalla"
                            )
                        } else {
                            triggerRecordingFromIntent = true
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.util.AppStrings.init(this)
        handleIncomingIntent(intent)

        com.example.util.UnityAdsManager.registerActivity(this@MainActivity)
        com.example.util.UnityAdsManager.initializeAndPreload(this@MainActivity)

        // Offload notification channel creation, WorkManager scheduling, Google Analytics, and Squad Referral Loop off the critical launch frame
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            com.example.util.AppAnalyticsManager.initialize(this@MainActivity)
            com.example.util.SquadReferralManager.initialize(this@MainActivity)
            com.example.util.SmartNotificationManager.ensureNotificationChannel(this@MainActivity)
            com.example.util.AlbertoGiftManager.checkAndPostPreLaunchNotification(this@MainActivity)
            com.example.util.SmartNotificationManager.scheduleBackgroundChecks(this@MainActivity)
        }

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
            val activeControllerGlyphs by viewModel.activeControllerGlyphs.collectAsState()
            val controllerOverlayConfig by viewModel.controllerOverlayConfig.collectAsState()
            val overlaySettings by viewModel.overlaySettings.collectAsState()

            GMaxGamingHubTheme(
                appTheme = currentTheme,
                customBgArgb = customBgColor
            ) {
                var securityResult by remember {
                    mutableStateOf(com.example.util.AppSecurityManager.SecurityResult(isPassed = true))
                }
                LaunchedEffect(Unit) {
                    val checked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.example.util.AppSecurityManager.checkIntegrity(this@MainActivity)
                    }
                    securityResult = checked
                }

                if (!securityResult.isPassed) {
                    com.example.ui.screens.IntegrityViolationScreen(
                        violationReason = securityResult.violationReason
                    )
                } else {
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val autoBigScreenLandscape by viewModel.autoBigScreenLandscape.collectAsState()
                    val isBigScreenManual by viewModel.isBigScreenModeManual.collectAsState()
                    val suppressAutoLandscapeConsole by viewModel.suppressAutoLandscapeConsole.collectAsState()
                    val showConsoleStartupAnimation by viewModel.showConsoleStartupAnimation.collectAsState()
                    val socialProvider by viewModel.socialSignedInProvider.collectAsState()
                    val customEmail by viewModel.customEmail.collectAsState()
                    val isUserAlreadySignedIn = currentFirebaseUser != null ||
                        socialProvider.isNotBlank() ||
                        customEmail.isNotBlank()

                    LaunchedEffect(currentFirebaseUser?.email, customEmail) {
                        val activeEmail = currentFirebaseUser?.email?.takeIf { it.isNotBlank() } ?: customEmail
                        if (com.example.util.AlbertoGiftManager.isTargetGiftRecipient(activeEmail)) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                com.example.util.SmartNotificationManager.ensureNotificationChannel(this@MainActivity)
                                com.example.util.AlbertoGiftManager.checkAndPostPreLaunchNotification(
                                    context = this@MainActivity,
                                    explicitEmail = activeEmail
                                )
                            }
                        }
                    }

                    LaunchedEffect(isLandscape) {
                        if (!isLandscape) {
                            viewModel.clearSuppressAutoLandscapeConsole()
                        }
                    }

                    // When manual Console Mode is engaged, rotate to sensor landscape; when exiting Console Mode, snap back to portrait immediately
                    LaunchedEffect(isBigScreenManual, suppressAutoLandscapeConsole) {
                        try {
                            if (isBigScreenManual) {
                                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else if (suppressAutoLandscapeConsole) {
                                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                kotlinx.coroutines.delay(450)
                                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        } catch (_: Exception) {}
                    }

                    // PS4-style Console Startup Animation plays every time Console Mode is entered or toggled
                    val shouldShowConsoleStartup = showConsoleStartupAnimation && !reducedAnimations

                    LaunchedEffect(shouldShowConsoleStartup) {
                        isShowingSplashScreen = shouldShowConsoleStartup
                    }

                    if (shouldShowConsoleStartup) {
                        SplashScreen(
                            onAnimationComplete = { viewModel.completeConsoleStartupAnimation() },
                            controllerGlyphs = activeControllerGlyphs,
                            ultraFastTransition = false
                        )
                    } else if (!hasCompletedAuthStep && !isUserAlreadySignedIn) {
                        // Sign In or Create Account page right after app launch (Splash) and before Welcome page (Onboarding)
                        AuthScreen(
                            initialDateOfBirth = userDateOfBirth,
                            onUpdateDateOfBirth = { dob ->
                                viewModel.setUserDateOfBirth(dob)
                            },
                            onAuthSuccess = {
                                viewModel.onUserSignedInSuccess("Google")
                            },
                            onContinueAsGuest = {
                                viewModel.continueAsGuest()
                            },
                            onSaveCredentials = { fullName, email ->
                                viewModel.setCustomProfileCredentials(fullName, email)
                            },
                            onSocialSignInSuccess = { provider, fullName, email ->
                                viewModel.onSocialAccountSignedIn(provider, fullName, email)
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
                            val dataIntent = result.data
                            if (result.resultCode == Activity.RESULT_OK && dataIntent != null) {
                                try {
                                    val recSettings = com.example.util.ScreenRecordingManager.loadSettings(context)
                                    ScreenRecordingService.startRecording(
                                        context = context,
                                        resultCode = result.resultCode,
                                        resultData = dataIntent,
                                        recordMic = recSettings.recordMic,
                                        audioSourceMode = recSettings.audioSourceMode
                                    )
                                } catch (_: Exception) {}
                            }
                        }

                        val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { _ ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    videoProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        // Screenshot MediaProjection Launcher
                        val screenshotProjectionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            val dataIntent = result.data
                            if (result.resultCode == Activity.RESULT_OK && dataIntent != null) {
                                try {
                                    ScreenRecordingService.captureScreenshot(
                                        context,
                                        result.resultCode,
                                        dataIntent
                                    )
                                } catch (_: Exception) {}
                            }
                        }

                        val requestVideoCapture = {
                            try {
                                val recSettings = com.example.util.ScreenRecordingManager.loadSettings(context)
                                val needsAudioPerm = recSettings.audioSourceMode != com.example.model.AudioSourceMode.MUTE &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (needsAudioPerm) {
                                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    videoProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                }
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
                                "TOP_GEAR" -> {
                                    selectedHubTabIndex = 2
                                    requestedTabFromIntent = null
                                }
                                "EXTRAS" -> {
                                    selectedHubTabIndex = 3
                                    requestedTabFromIntent = null
                                }
                                "SETTINGS" -> {
                                    selectedHubTabIndex = 4
                                    requestedTabFromIntent = null
                                }
                                "PROFILE" -> {
                                    selectedHubTabIndex = 5
                                    requestedTabFromIntent = null
                                }
                                "RIVALS" -> {
                                    selectedHubTabIndex = 6
                                    requestedTabFromIntent = null
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            com.example.util.UnityAdsManager.maybeShowAppOpenAdOnLaunch()
                        }

                        LaunchedEffect(selectedHubTabIndex) {
                            val tabScreenName = when (selectedHubTabIndex) {
                                0 -> "Game_Launcher"
                                1 -> "Screen_Recorder"
                                2 -> "Top_Gear"
                                3 -> "Extras_Studio"
                                4 -> "Settings"
                                5 -> "Gamer_Profile"
                                6 -> "Rivals_Hub"
                                else -> "Hub_Tab_$selectedHubTabIndex"
                            }
                            com.example.util.AppAnalyticsManager.logScreenView(
                                context = context,
                                screenName = tabScreenName
                            )
                        }

                        var manualNetworkRetryTick by remember { mutableIntStateOf(0) }
                        val isDeviceOnlineLive by com.example.util.rememberIsDeviceOnline()
                        LaunchedEffect(manualNetworkRetryTick) {
                            if (manualNetworkRetryTick > 0) {
                                com.example.util.NetworkConnectivityMonitor.triggerImmediateRecheck(context)
                            }
                        }
                        val isDeviceOnline = isDeviceOnlineLive

                        val squadPassState by viewModel.squadPassState.collectAsState()
                        val rivalsState by viewModel.rivalsState.collectAsState()

                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets.safeDrawing,
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
                                        // Real-time high-dopamine Squadmate activation notification banner
                                        com.example.ui.components.SquadDopamineNotificationBanner(
                                            message = squadPassState.highDopamineToastMessage,
                                            onDismiss = {
                                                com.example.util.SquadReferralManager.dismissDopamineToast()
                                            }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    // Smooth, zero-jank screen transitions across Hub tabs
                                    Crossfade(
                                        targetState = selectedHubTabIndex,
                                        animationSpec = tween(durationMillis = if (reducedAnimations) 0 else 180),
                                        label = "hubTabCrossfade"
                                    ) { tabIndex ->
                                        // Tab 0 (Game Launcher) and Tab 1 (Screen Recorder) work offline.
                                        // Top Gear, Extras, Settings, and Profile require internet connectivity.
                                        when (tabIndex) {
                                            0 -> GamesScreen(
                                                viewModel = viewModel,
                                                onOpenSquadPassHub = {
                                                    requestedExtrasSubTab = 2
                                                    selectedHubTabIndex = 3
                                                }
                                            )
                                            1 -> ScreenRecordScreen(
                                                viewModel = viewModel,
                                                onRequestMediaProjection = requestVideoCapture,
                                                onRequestScreenshotProjection = requestScreenshotCapture,
                                                initialSubTab = requestedRecorderSubTab
                                            )
                                            2 -> if (isDeviceOnline) {
                                                TopGearScreen(
                                                    onNavigateToGmaxFeature = { pairsWithText ->
                                                        when {
                                                            pairsWithText.contains("Recorder", ignoreCase = true) -> {
                                                                requestedRecorderSubTab = 0
                                                                selectedHubTabIndex = 1
                                                            }
                                                            pairsWithText.contains("temperature", ignoreCase = true) -> {
                                                                requestedExtrasSubTab = 6
                                                                selectedHubTabIndex = 3
                                                            }
                                                            pairsWithText.contains("Console", ignoreCase = true) -> {
                                                                requestedExtrasSubTab = 4
                                                                selectedHubTabIndex = 3
                                                            }
                                                            pairsWithText.contains("Touch", ignoreCase = true) ||
                                                                pairsWithText.contains("Crosshair", ignoreCase = true) -> {
                                                                requestedExtrasSubTab = 3
                                                                selectedHubTabIndex = 3
                                                            }
                                                            pairsWithText.contains("Battery", ignoreCase = true) -> {
                                                                requestedExtrasSubTab = 1
                                                                selectedHubTabIndex = 3
                                                            }
                                                            else -> {
                                                                requestedExtrasSubTab = 0
                                                                selectedHubTabIndex = 3
                                                            }
                                                        }
                                                    }
                                                )
                                            } else {
                                                com.example.util.OnlineRequiredGate(
                                                    featureNameEn = "Top Gear Curated Gaming Gear",
                                                    featureNameEs = "Equipo Gamer Seleccionado Top Gear",
                                                    onRetryConnection = { manualNetworkRetryTick++ },
                                                    onGoToOfflineLauncher = { selectedHubTabIndex = 0 }
                                                )
                                            }
                                            3 -> if (isDeviceOnline) {
                                                ExtrasScreen(
                                                    viewModel = viewModel,
                                                    initialSubTab = requestedExtrasSubTab
                                                )
                                            } else {
                                                com.example.util.OnlineRequiredGate(
                                                    featureNameEn = "Extras & Hardware Diagnostics",
                                                    featureNameEs = "Extras y Diagnósticos de Hardware",
                                                    onRetryConnection = { manualNetworkRetryTick++ },
                                                    onGoToOfflineLauncher = { selectedHubTabIndex = 0 }
                                                )
                                            }
                                            4 -> if (isDeviceOnline) {
                                                SettingsScreen(
                                                    viewModel = viewModel,
                                                    onOpenAccountProfile = { selectedHubTabIndex = 5 }
                                                )
                                            } else {
                                                com.example.util.OnlineRequiredGate(
                                                    featureNameEn = "Cloud Settings & Preferences",
                                                    featureNameEs = "Ajustes y Preferencias en la Nube",
                                                    onRetryConnection = { manualNetworkRetryTick++ },
                                                    onGoToOfflineLauncher = { selectedHubTabIndex = 0 }
                                                )
                                            }
                                            5 -> if (isDeviceOnline) {
                                                AccountProfileScreen(
                                                    viewModel = viewModel,
                                                    onBack = { selectedHubTabIndex = 0 }
                                                )
                                            } else {
                                                com.example.util.OnlineRequiredGate(
                                                    featureNameEn = "Gamer Profile & Cloud Sync",
                                                    featureNameEs = "Perfil de Jugador y Sincronización",
                                                    onRetryConnection = { manualNetworkRetryTick++ },
                                                    onGoToOfflineLauncher = { selectedHubTabIndex = 0 }
                                                )
                                            }
                                            6 -> {
                                                androidx.activity.compose.BackHandler {
                                                    selectedHubTabIndex = 0
                                                }
                                                com.example.ui.screens.RivalsScreen(
                                                    viewModel = viewModel
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Real-Time In-App Floating Controller Input Overlay (when enabled and System Overlay isn't already drawing on top)
                            if (controllerOverlayConfig.isEnabled && !com.example.service.ControllerOverlayService.isRunning) {
                                InAppControllerOverlayHost(
                                    viewModel = viewModel,
                                    glyphs = activeControllerGlyphs,
                                    config = controllerOverlayConfig
                                )
                            }

                            // Global Instant Visual + Haptic Confirmation Pill for buttons, toggles, and processes
                            InstantFeedbackBannerHost(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                            )

                            // In-App Star Review Popup (directs to Google Play Store review page; permanently suppressed once verified reviewed)
                            val showReviewPopup by viewModel.showReviewPopup.collectAsState()
                            val activeReviewSource by viewModel.activeReviewTriggerSource.collectAsState()
                            val isVerifiedReviewed by viewModel.isVerifiedReviewed.collectAsState()
                            if (showReviewPopup && !isVerifiedReviewed) {
                                com.example.ui.components.AppReviewPromptDialog(
                                    triggerSource = activeReviewSource,
                                    onRateOnPlayStore = { stars ->
                                        com.example.util.AppReviewManager.launchPlayStoreReviewPage(
                                            context = context,
                                            selectedStars = stars
                                        )
                                    },
                                    onDismissLater = {
                                        com.example.util.AppReviewManager.dismissReviewPromptForCooldown(context)
                                    }
                                )
                            }

                            // Unity Ads Privacy & Consent Prompt (Shown once on first run or when reopened from Settings)
                            val needsUnityPrivacyConsent by com.example.util.UnityAdsManager.needsPrivacyConsentPrompt.collectAsState()
                            if (needsUnityPrivacyConsent) {
                                com.example.ui.components.UnityPrivacyConsentDialog(
                                    onAcceptPersonalized = {
                                        com.example.util.UnityAdsManager.submitPrivacyConsent(context, true)
                                    },
                                    onSelectNonPersonalized = {
                                        com.example.util.UnityAdsManager.submitPrivacyConsent(context, false)
                                    }
                                )
                            }

                            // Invite a Squad In-App Popup (appears after 2x Booster, 2x Recording, or 2x Overlay if user has no Squad)
                            if (rivalsState.showInviteSquadPopup && rivalsState.activeSquad == null) {
                                com.example.ui.screens.InviteSquadInAppPopupDialog(
                                    onOpenRivalsTab = {
                                        com.example.util.RivalsManager.dismissInviteSquadPopup()
                                        selectedHubTabIndex = 6
                                    },
                                    onDismiss = {
                                        com.example.util.RivalsManager.dismissInviteSquadPopup()
                                    }
                                )
                            }

                            // Pre-loaded Full-Screen Unity Ad Presentation Host (strictly excluded on Top Gear tab)
                            if (selectedHubTabIndex != 2) {
                                com.example.ui.components.UnityFullScreenPresentationHost()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppControllerOverlayHost(
    viewModel: GameBoosterViewModel,
    glyphs: com.example.model.ControllerGlyphMap,
    config: com.example.model.ControllerOverlayConfig
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controllerHealthState by viewModel.controllerHealthState.collectAsState()
    val isConnected = controllerHealthState.controllerId >= 0 ||
        controllerHealthState.isWiredConnection ||
        com.example.util.ControllerHealthManager.isAnyControllerConnected(context)
    if (!isConnected) return
    com.example.ui.components.FloatingInAppControllerOverlay(
        state = controllerHealthState,
        glyphs = glyphs,
        config = config,
        onToggleLock = {
            viewModel.setControllerOverlayLockTouch(!config.lockTouchPassThrough)
        },
        onClose = {
            viewModel.setControllerOverlayEnabled(false)
        }
    )
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
 * - Active tab rendered inside a rounded pill with rocket/icon + label ("Launcher", "Recorder", "Top Gear", "Extras", "Settings").
 * - Right beside Settings is the Profile icon / avatar button that opens User Account Info (Profile Picture, Most Played Game, Date of Birth, Account & Cloud Sync).
 */
@Composable
private fun RazerInspiredTopHubBar(
    selectedTabIndex: Int,
    currentFirebaseUser: FirebaseUser?,
    cloudPhotoUrl: String,
    onSelectTab: (Int) -> Unit
) {
    val launcherLabel = com.example.util.tr("Launcher", "Lanzador")
    val recorderLabel = com.example.util.tr("Recorder", "Grabador")
    val rivalsLabel = com.example.util.tr("Rivals", "Rivales")
    val topGearLabel = com.example.util.tr("Top Gear", "Top Gear")
    val extrasLabel = com.example.util.tr("Extras", "Extras")
    val settingsLabel = com.example.util.tr("Settings", "Ajustes")

    val tabs = remember(launcherLabel, recorderLabel, rivalsLabel, topGearLabel, extrasLabel, settingsLabel) {
        listOf(
            TopHubNavTab(
                index = 0,
                label = launcherLabel,
                icon = Icons.Default.RocketLaunch,
                testTag = "nav_games",
                activeAccent = HubBlue
            ),
            TopHubNavTab(
                index = 1,
                label = recorderLabel,
                icon = Icons.Default.Videocam,
                testTag = "nav_record",
                activeAccent = RecorderCoral
            ),
            TopHubNavTab(
                index = 6,
                label = rivalsLabel,
                icon = Icons.Default.EmojiEvents,
                testTag = "nav_rivals",
                activeAccent = Color(0xFFFFD54F)
            ),
            TopHubNavTab(
                index = 2,
                label = topGearLabel,
                icon = Icons.Default.ShoppingBag,
                testTag = "nav_top_gear",
                activeAccent = HubBlue
            ),
            TopHubNavTab(
                index = 3,
                label = extrasLabel,
                icon = Icons.Default.Tune,
                testTag = "nav_extras",
                activeAccent = HubBlue
            ),
            TopHubNavTab(
                index = 4,
                label = settingsLabel,
                icon = Icons.Default.Settings,
                testTag = "nav_settings",
                activeAccent = HubBlue
            )
        )
    }

    val isProfileSelected = selectedTabIndex == 5
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
            // Official G Max 3D Hexagonal Controller Emblem on Top Hub Bar
            com.example.ui.components.GMaxBrandIcon(
                size = 36.dp,
                cornerRadius = 10.dp,
                showGlowBorder = true,
                glowColor = Color(0xFF00E5FF),
                contentDescription = "G Max Gaming Hub",
                modifier = Modifier
                    .gmaxInteractivePress { onSelectTab(0) }
                    .testTag("top_bar_gmax_brand_icon")
            )

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
                        .gmaxInteractivePress { onSelectTab(tab.index) }
                        .padding(horizontal = 13.dp, vertical = 8.dp)
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
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                    .gmaxInteractivePress { onSelectTab(5) }
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

