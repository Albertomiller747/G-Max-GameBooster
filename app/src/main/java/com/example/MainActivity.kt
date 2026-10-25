package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ScreenRecordingService
import com.example.ui.components.SplashScreen
import com.example.ui.screens.ExtrasScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.ScreenRecordScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TitanBoosterTheme
import com.example.viewmodel.GameBoosterViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GameBoosterViewModel by viewModels()
    private var triggerRecordingFromIntent by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("extra_trigger_recording", false)) {
            triggerRecordingFromIntent = true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("extra_trigger_recording", false) == true) {
            triggerRecordingFromIntent = true
        }

        setContent {
            TitanBoosterTheme {
                val securityResult = androidx.compose.runtime.remember {
                    com.example.util.AppSecurityManager.checkIntegrity(this@MainActivity)
                }

                if (!securityResult.isPassed) {
                    com.example.ui.screens.IntegrityViolationScreen(
                        violationReason = securityResult.violationReason
                    )
                } else {
                    var showSplash by rememberSaveable { mutableStateOf(true) }

                    if (showSplash) {
                        SplashScreen(onAnimationComplete = { showSplash = false })
                    } else {
                        val context = LocalContext.current
                        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
                        val gamingSessionActive by viewModel.gamingSessionActive.collectAsState()
                        val currentTheme by viewModel.appTheme.collectAsState()
                        val isGamePickerVisible by viewModel.isGamePickerVisible.collectAsState()
                        val installedApps by viewModel.installedApps.collectAsState()
                        val selectedTargetGame by viewModel.selectedTargetGame.collectAsState()

                        val mediaProjectionManager = remember {
                            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        }

                        val mediaProjectionLauncher = rememberLauncherForActivityResult(
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

                        val requestScreenCapture = {
                            try {
                                mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        LaunchedEffect(triggerRecordingFromIntent) {
                            if (triggerRecordingFromIntent) {
                                triggerRecordingFromIntent = false
                                selectedTabIndex = 1
                                requestScreenCapture()
                            }
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = currentTheme.backgroundColor,
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = "G MAX GAMING HUB",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp,
                                                color = Color.White
                                            )
                                        )
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = {
                                                viewModel.refreshMetrics()
                                                viewModel.refreshGamepads()
                                                viewModel.loadInstalledApps()
                                            },
                                            modifier = Modifier.testTag("refresh_dashboard_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Refresh Metrics",
                                                tint = Color(0xFF3B82F6)
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = currentTheme.backgroundColor,
                                        titleContentColor = Color.White
                                    )
                                )
                            },
                            bottomBar = {
                                NavigationBar(
                                    modifier = Modifier
                                        .border(1.dp, currentTheme.borderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .windowInsetsPadding(WindowInsets.navigationBars),
                                    containerColor = currentTheme.surfaceColor,
                                    tonalElevation = 8.dp
                                ) {
                                    // 1. Games tab
                                    NavigationBarItem(
                                        selected = selectedTabIndex == 0,
                                        onClick = { selectedTabIndex = 0 },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.SportsEsports,
                                                contentDescription = "Games",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "Games",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF3B82F6),
                                            selectedTextColor = Color(0xFF3B82F6),
                                            indicatorColor = Color(0xFF1E283D),
                                            unselectedIconColor = Color(0xFF6B7280),
                                            unselectedTextColor = Color(0xFF6B7280)
                                        ),
                                        modifier = Modifier.testTag("nav_games")
                                    )

                                    // 2. Record tab
                                    NavigationBarItem(
                                        selected = selectedTabIndex == 1,
                                        onClick = { selectedTabIndex = 1 },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Record",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "Record",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFFEF4444),
                                            selectedTextColor = Color(0xFFEF4444),
                                            indicatorColor = Color(0xFF2E1B22),
                                            unselectedIconColor = Color(0xFF6B7280),
                                            unselectedTextColor = Color(0xFF6B7280)
                                        ),
                                        modifier = Modifier.testTag("nav_record")
                                    )

                                    // 3. Extras tab (Tweaks + Hardware & Diagnostics)
                                    NavigationBarItem(
                                        selected = selectedTabIndex == 2,
                                        onClick = { selectedTabIndex = 2 },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Extras",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "Extras",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF3B82F6),
                                            selectedTextColor = Color(0xFF3B82F6),
                                            indicatorColor = Color(0xFF1E283D),
                                            unselectedIconColor = Color(0xFF6B7280),
                                            unselectedTextColor = Color(0xFF6B7280)
                                        ),
                                        modifier = Modifier.testTag("nav_extras")
                                    )

                                    // 4. Settings tab (Background Color & Preferences)
                                    NavigationBarItem(
                                        selected = selectedTabIndex == 3,
                                        onClick = { selectedTabIndex = 3 },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "Settings",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedTabIndex == 3) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF3B82F6),
                                            selectedTextColor = Color(0xFF3B82F6),
                                            indicatorColor = Color(0xFF1E283D),
                                            unselectedIconColor = Color(0xFF6B7280),
                                            unselectedTextColor = Color(0xFF6B7280)
                                        ),
                                        modifier = Modifier.testTag("nav_settings")
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (selectedTabIndex) {
                                    0 -> GamesScreen(viewModel = viewModel)
                                    1 -> ScreenRecordScreen(
                                        viewModel = viewModel,
                                        onRequestMediaProjection = requestScreenCapture
                                    )
                                    2 -> ExtrasScreen(viewModel = viewModel)
                                    3 -> SettingsScreen(viewModel = viewModel)
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
