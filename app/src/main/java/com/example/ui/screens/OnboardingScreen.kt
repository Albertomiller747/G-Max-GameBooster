package com.example.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.HubBlue
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.RecorderCoral

private data class WelcomeFeatureHighlight(
    val badge: String,
    val summary: String, // Strictly 5-7 words as requested
    val icon: ImageVector,
    val accentColor: Color
)

private data class HubPermissionItem(
    val id: String,
    val title: String,
    val featureBadge: String,
    val whatItGrants: String,
    val whyFeatureNeedsIt: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isGranted: Boolean,
    val onGrantClick: () -> Unit
)

@Composable
fun OnboardingScreen(
    startOnPermissionsStep: Boolean = false,
    onFinishOnboarding: () -> Unit
) {
    var currentStep by remember(startOnPermissionsStep) {
        mutableIntStateOf(if (startOnPermissionsStep) 1 else 0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentStep,
            label = "onboarding_step_transition"
        ) { step ->
            if (step == 0) {
                WelcomeStepContent(
                    onContinueClick = { currentStep = 1 }
                )
            } else {
                UnifiedPermissionsStepContent(
                    onEnterHub = onFinishOnboarding
                )
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(
    onContinueClick: () -> Unit
) {
    val highlights = remember {
        listOf(
            WelcomeFeatureHighlight(
                badge = "BOOST",
                summary = "Maximize frame rates and cool hardware",
                icon = Icons.Default.RocketLaunch,
                accentColor = LaunchGreen
            ),
            WelcomeFeatureHighlight(
                badge = "RECORD",
                summary = "Capture crisp gameplay videos and screenshots",
                icon = Icons.Default.Videocam,
                accentColor = RecorderCoral
            ),
            WelcomeFeatureHighlight(
                badge = "CONTROLLER",
                summary = "Navigate everything seamlessly with your gamepad",
                icon = Icons.Default.SportsEsports,
                accentColor = HubBlue
            )
        )
    }

    var continueFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Brand Identity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.example.ui.components.GMaxBrandIcon(
                size = 82.dp,
                cornerRadius = 22.dp,
                showGlowBorder = true,
                glowColor = Color(0xFF00E5FF),
                contentDescription = "G Max Gaming Hub"
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to G Max Hub",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your unified launcher, recorder, and controller command center",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // 3 Highlighted 5-7 Word Feature Summaries
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            highlights.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.badge == "CONTROLLER") {
                            com.example.ui.components.GMaxBrandIcon(
                                size = 48.dp,
                                cornerRadius = 14.dp,
                                showGlowBorder = true,
                                glowColor = item.accentColor,
                                contentDescription = item.badge
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(item.accentColor.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.badge,
                                    tint = item.accentColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(item.accentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.badge,
                                    color = item.accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.summary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Continue Button with full controller focus ring
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onContinueClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .onFocusChanged { continueFocused = it.isFocused }
                    .border(
                        width = if (continueFocused) 3.dp else 0.dp,
                        color = if (continueFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("welcome_continue_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubBlue,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Controller Ready • Press A or Tap Continue",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnifiedPermissionsStepContent(
    onEnterHub: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notifGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var micGranted by remember { mutableStateOf(checkMicPermission(context)) }
    var usageGranted by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var dndGranted by remember { mutableStateOf(checkDndPermission(context)) }

    fun refreshAllPermissions() {
        overlayGranted = Settings.canDrawOverlays(context)
        notifGranted = checkNotificationPermission(context)
        micGranted = checkMicPermission(context)
        usageGranted = checkUsageStatsPermission(context)
        dndGranted = checkDndPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notifGranted = isGranted
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        micGranted = isGranted
    }

    val permissionItems = listOf(
        HubPermissionItem(
            id = "overlay",
            title = "Display Over Other Apps",
            featureBadge = "HUD & RECORDER BUBBLE",
            whatItGrants = "Allows floating HUD gauges, tactical crosshairs, and the radial recorder bubble to appear over games.",
            whyFeatureNeedsIt = "Needed so you can monitor live FPS/temperature and start or stop recording without leaving gameplay.",
            icon = Icons.Default.Layers,
            accentColor = RecorderCoral,
            isGranted = overlayGranted,
            onGrantClick = {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        ),
        HubPermissionItem(
            id = "notifications",
            title = "Recorder & Boost Notifications",
            featureBadge = "QUICK NOTIFICATION BAR",
            whatItGrants = "Allows the quick-control recorder notification bar and active booster status alerts.",
            whyFeatureNeedsIt = "Needed for the 1-tap notification bar ('Recorder is ready') to trigger recordings and screenshots anytime.",
            icon = Icons.Default.Notifications,
            accentColor = HubBlue,
            isGranted = notifGranted,
            onGrantClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notifGranted = true
                }
            }
        ),
        HubPermissionItem(
            id = "microphone",
            title = "Microphone Audio Capture",
            featureBadge = "VOICE COMMENTARY",
            whatItGrants = "Grants optional microphone audio input while recording gameplay clips.",
            whyFeatureNeedsIt = "Needed only when you enable Microphone Audio in Recorder Settings to capture voice chat or commentary.",
            icon = Icons.Default.Mic,
            accentColor = LaunchGreen,
            isGranted = micGranted,
            onGrantClick = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        ),
        HubPermissionItem(
            id = "usage",
            title = "Usage Access Telemetry",
            featureBadge = "GAME DETECTION",
            whatItGrants = "Lets the hub detect which game is actively running in the foreground.",
            whyFeatureNeedsIt = "Needed to automatically track game session duration, FPS match recaps, and active boost profiles.",
            icon = Icons.Default.QueryStats,
            accentColor = Color(0xFFF59E0B),
            isGranted = usageGranted,
            onGrantClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {}
            }
        ),
        HubPermissionItem(
            id = "dnd",
            title = "Priority Do Not Disturb",
            featureBadge = "INTERRUPT SHIELD",
            whatItGrants = "Allows toggling Priority Do Not Disturb filter while gaming and restoring it afterward.",
            whyFeatureNeedsIt = "Needed to block pop-up call and message interruptions during competitive matches without muting game audio.",
            icon = Icons.Default.DoNotDisturbOn,
            accentColor = Color(0xFF8B5CF6),
            isGranted = dndGranted,
            onGrantClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } catch (_: Exception) {}
            }
        )
    )

    val grantedCount = permissionItems.count { it.isGranted }
    val allGranted = grantedCount == permissionItems.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HubBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = HubBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Permissions Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$grantedCount of ${permissionItems.size} enabled • Grant only what you need",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Top "Not Now" quick skip button
            OutlinedButton(
                onClick = onEnterHub,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("permissions_not_now_top_button")
            ) {
                Text(
                    text = "Not Now",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scrollable list of permissions with clear explanations
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(permissionItems, key = { it.id }) { item ->
                PermissionExplanationCard(item = item)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Action Bar: "Not Now" & "Enter Hub"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var notNowFocused by remember { mutableStateOf(false) }
            var enterFocused by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = onEnterHub,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .onFocusChanged { notNowFocused = it.isFocused }
                    .border(
                        width = if (notNowFocused) 2.5.dp else 0.dp,
                        color = if (notNowFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .testTag("permissions_not_now_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Not Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = onEnterHub,
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .onFocusChanged { enterFocused = it.isFocused }
                    .border(
                        width = if (enterFocused) 2.5.dp else 0.dp,
                        color = if (enterFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .testTag("permissions_continue_hub_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allGranted) LaunchGreen else HubBlue,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (allGranted) "All Set • Enter Hub" else "Continue to Hub",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionExplanationCard(item: HubPermissionItem) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(item.accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.featureBadge,
                        color = item.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (item.isGranted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(LaunchGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = LaunchGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Granted",
                            color = LaunchGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = item.onGrantClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = item.accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("grant_permission_${item.id}")
                    ) {
                        Text(
                            text = "Grant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // What it grants
            Text(
                text = "What it grants: ${item.whatItGrants}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Why the feature needs it
            Text(
                text = "Why it's needed: ${item.whyFeatureNeedsIt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkMicPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

private fun checkDndPermission(context: Context): Boolean {
    return try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.isNotificationPolicyAccessGranted
    } catch (_: Exception) {
        false
    }
}
