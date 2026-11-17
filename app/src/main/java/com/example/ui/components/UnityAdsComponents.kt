package com.example.ui.components

import android.app.Activity
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnLayout
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaunchGreen
import com.example.ui.theme.SolarGold
import com.example.util.UnityAdsManager
import com.example.util.tr
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private enum class UnityBannerLoadState {
    LOADING,
    LOADED,
    FAILED
}

/**
 * Pure Unity Ads Card-Wrapped Banner slot (`BP_Banner_Android`).
 */
@Composable
fun GMaxUnityBannerCard(
    placementTag: String,
    modifier: Modifier = Modifier,
    topBuffer: Dp = 20.dp,
    bottomBuffer: Dp = 16.dp,
    initialLoadDelayMs: Long = 0L
) {
    GMaxUnityBannerSlot(
        placementTag = placementTag,
        modifier = modifier,
        topBuffer = topBuffer,
        bottomBuffer = bottomBuffer,
        wrapInCard = true,
        initialLoadDelayMs = initialLoadDelayMs
    )
}

/**
 * Pure Unity Ads Anchored Banner slot (`BP_Banner_Android`).
 */
@Composable
fun GMaxUnityAnchoredBanner(
    placementTag: String,
    modifier: Modifier = Modifier,
    topBuffer: Dp = 20.dp,
    initialLoadDelayMs: Long = 0L
) {
    GMaxUnityBannerSlot(
        placementTag = placementTag,
        modifier = modifier,
        topBuffer = topBuffer,
        bottomBuffer = 0.dp,
        wrapInCard = false,
        initialLoadDelayMs = initialLoadDelayMs
    )
}

/**
 * Core Unity Ads `BannerView` host (`BP_Banner_Android`).
 *
 * Key requirements enforced:
 * 1. `BannerView` is created with an `Activity` context (`hostActivity`).
 * 2. `BannerView` is ONLY instantiated and `.load()` is ONLY called AFTER `UnityAds.initialize(...)`
 *    has completed (`onInitializationComplete` / `isSdkReady == true`).
 * 3. Strictly targets `BP_Banner_Android` in Live Ads mode (`testMode = false`).
 */
@Composable
fun GMaxUnityBannerSlot(
    placementTag: String,
    modifier: Modifier = Modifier,
    topBuffer: Dp = 20.dp,
    bottomBuffer: Dp = 0.dp,
    wrapInCard: Boolean = false,
    initialLoadDelayMs: Long = 0L
) {
    val context = LocalContext.current
    val isInitialized by UnityAdsManager.isInitialized.collectAsState()
    val isSdkReady by UnityAdsManager.isSdkReady.collectAsState()
    val bannerReloadEpoch by UnityAdsManager.bannerReloadEpoch.collectAsState()
    val hostActivity = remember(context) { UnityAdsManager.findActivity(context) }

    if (hostActivity == null) return

    LaunchedEffect(hostActivity) {
        UnityAdsManager.registerActivity(hostActivity)
        if (!isInitialized || !isSdkReady) {
            UnityAdsManager.initializeAndPreload(hostActivity)
        }
    }

    var bannerState by remember(placementTag, bannerReloadEpoch) {
        mutableStateOf(UnityBannerLoadState.LOADING)
    }
    var retryTick by remember(placementTag, bannerReloadEpoch) { mutableIntStateOf(0) }

    // Automatic background retry when unfilled so BP_Banner_Android recovers seamlessly
    LaunchedEffect(isSdkReady, bannerState, retryTick, bannerReloadEpoch) {
        if (isSdkReady && bannerState == UnityBannerLoadState.FAILED) {
            delay(10_000L)
            bannerState = UnityBannerLoadState.LOADING
            retryTick++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (wrapInCard) "native_ad_$placementTag" else "adaptive_banner_$placementTag")
    ) {
        if (topBuffer > 0.dp) {
            Spacer(modifier = Modifier.height(topBuffer))
        }

        val renderContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (wrapInCard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                        if (bannerState != UnityBannerLoadState.LOADED) {
                            Text(
                                text = tr("Sponsored", "Patrocinado"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Only create BannerView (with Activity context) and call load() AFTER onInitializationComplete
                if (isSdkReady && isInitialized) {
                    UnityBannerContainerBox(
                        hostActivity = hostActivity,
                        placementTag = placementTag,
                        activePlacementId = UnityAdsManager.BANNER_PLACEMENT_ID,
                        retryTick = retryTick,
                        bannerReloadEpoch = bannerReloadEpoch,
                        initialLoadDelayMs = initialLoadDelayMs,
                        bannerState = bannerState,
                        onBannerLoaded = {
                            bannerState = UnityBannerLoadState.LOADED
                            UnityAdsManager.recordBannerDiagnostic(
                                placementTag = placementTag,
                                placementId = UnityAdsManager.BANNER_PLACEMENT_ID,
                                status = "LOADED"
                            )
                        },
                        onBannerFailed = { failedCode, failedMsg ->
                            UnityAdsManager.recordBannerDiagnostic(
                                placementTag = placementTag,
                                placementId = UnityAdsManager.BANNER_PLACEMENT_ID,
                                status = "FAILED",
                                errorCode = failedCode,
                                errorMessage = failedMsg
                            )
                            bannerState = UnityBannerLoadState.FAILED
                        }
                    )
                } else {
                    BannerLoadingPlaceholderBox()
                }
            }
        }

        if (wrapInCard) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    renderContent()
                }
            }
        } else {
            renderContent()
        }

        if (bottomBuffer > 0.dp) {
            Spacer(modifier = Modifier.height(bottomBuffer))
        }
    }
}

@Composable
private fun BannerLoadingPlaceholderBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .width(320.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A101D).copy(alpha = 0.72f))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = tr("Loading Sponsored Ad...", "Cargando anuncio patrocinado..."),
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UnityBannerContainerBox(
    hostActivity: Activity,
    placementTag: String,
    activePlacementId: String,
    retryTick: Int,
    bannerReloadEpoch: Int,
    initialLoadDelayMs: Long,
    bannerState: UnityBannerLoadState,
    onBannerLoaded: () -> Unit,
    onBannerFailed: (errorCode: String, errorMsg: String) -> Unit
) {
    val latestOnLoaded by rememberUpdatedState(onBannerLoaded)
    val latestOnFailed by rememberUpdatedState(onBannerFailed)

    val loadKey = "$placementTag|$activePlacementId|$retryTick|$bannerReloadEpoch"
    val hasRequestedLoad = remember(loadKey) { AtomicBoolean(false) }
    var isStaggerDelayElapsed by remember(loadKey) {
        mutableStateOf(initialLoadDelayMs <= 0L)
    }
    var attachedContainer by remember(loadKey) {
        mutableStateOf<FrameLayout?>(null)
    }

    LaunchedEffect(loadKey, initialLoadDelayMs) {
        if (initialLoadDelayMs > 0L) {
            delay(initialLoadDelayMs)
            isStaggerDelayElapsed = true
        }
    }

    // BannerView is created strictly with Activity context (hostActivity) after onInitializationComplete
    val bannerView = remember(loadKey, hostActivity) {
        BannerView(
            hostActivity,
            activePlacementId,
            UnityBannerSize(320, 50)
        ).apply {
            listener = object : BannerView.Listener() {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    Log.d(
                        "AdsDebug",
                        "Unity Banner onBannerLoaded: slot=$placementTag, placement=$activePlacementId"
                    )
                    hostActivity.runOnUiThread { latestOnLoaded() }
                }

                override fun onBannerShown(bannerAdView: BannerView?) {
                    Log.d(
                        "AdsDebug",
                        "Unity Banner onBannerShown: slot=$placementTag, placement=$activePlacementId"
                    )
                }

                override fun onBannerClick(bannerAdView: BannerView?) {
                    Log.d(
                        "AdsDebug",
                        "Unity Banner onBannerClick: slot=$placementTag, placement=$activePlacementId"
                    )
                }

                override fun onBannerFailedToLoad(
                    bannerAdView: BannerView?,
                    errorInfo: BannerErrorInfo?
                ) {
                    val codeLabel = errorInfo?.errorCode?.name ?: "UNKNOWN"
                    val msgLabel = errorInfo?.errorMessage.orEmpty()
                    Log.i(
                        "AdsDebug",
                        "Unity Banner failed: slot=$placementTag, placement=$activePlacementId, code=$codeLabel, msg=$msgLabel"
                    )
                    hostActivity.runOnUiThread {
                        latestOnFailed(codeLabel, msgLabel)
                    }
                }
            }
        }
    }

    DisposableEffect(bannerView) {
        onDispose {
            try {
                (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                bannerView.destroy()
            } catch (_: Throwable) {}
        }
    }

    val requestBannerLoadIfReady: (FrameLayout) -> Unit = remember(loadKey, bannerView, hostActivity) {
        { container ->
            val sdkReadyNow = try {
                UnityAdsManager.isSdkReady.value && (UnityAds.isInitialized || UnityAdsManager.isInitialized.value)
            } catch (_: Throwable) {
                UnityAdsManager.isSdkReady.value
            }
            if (sdkReadyNow && hasRequestedLoad.compareAndSet(false, true)) {
                try {
                    Log.d(
                        "AdsDebug",
                        "Calling BannerView.load(): slot=$placementTag, placement=$activePlacementId, w=${container.width}, h=${container.height}, testMode=false"
                    )
                    bannerView.load()
                } catch (t: Throwable) {
                    Log.w("AdsDebug", "Unity Banner load() exception: ${t.message}")
                    hostActivity.runOnUiThread {
                        latestOnFailed("LOAD_EXCEPTION", t.message.orEmpty())
                    }
                }
            }
        }
    }

    LaunchedEffect(loadKey, isStaggerDelayElapsed, attachedContainer) {
        val container = attachedContainer ?: return@LaunchedEffect
        if (isStaggerDelayElapsed) {
            if (container.isAttachedToWindow && container.width > 0 && container.height > 0) {
                container.post { requestBannerLoadIfReady(container) }
            } else {
                container.doOnLayout { laidOut ->
                    if (laidOut is FrameLayout) {
                        laidOut.post { requestBannerLoadIfReady(laidOut) }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bannerState != UnityBannerLoadState.LOADED) {
            BannerLoadingPlaceholderBox()
        }

        AndroidView(
            modifier = Modifier
                .width(320.dp)
                .height(50.dp),
            factory = {
                // Always construct FrameLayout with the Activity context (hostActivity)
                FrameLayout(hostActivity).apply {
                    clipChildren = false
                    clipToPadding = false
                }
            },
            update = { container ->
                val density = hostActivity.resources.displayMetrics.density
                val widthPx = (320 * density).roundToInt()
                val heightPx = (50 * density).roundToInt()

                if (bannerView.parent !== container) {
                    (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                    container.removeAllViews()
                    container.addView(
                        bannerView,
                        FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
                    )
                }
                attachedContainer = container

                if (isStaggerDelayElapsed) {
                    if (container.isAttachedToWindow && container.width > 0 && container.height > 0) {
                        container.post { requestBannerLoadIfReady(container) }
                    } else {
                        container.doOnLayout { laidOut ->
                            if (laidOut.isAttachedToWindow) {
                                laidOut.post { requestBannerLoadIfReady(container) }
                            } else {
                                laidOut.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) {
                                        v.removeOnAttachStateChangeListener(this)
                                        v.post { requestBannerLoadIfReady(container) }
                                    }

                                    override fun onViewDetachedFromWindow(v: View) {
                                        v.removeOnAttachStateChangeListener(this)
                                    }
                                })
                            }
                        }
                    }
                }
            }
        )
    }
}

/**
 * Unity-Native GDPR & Ad Privacy Consent Dialog.
 */
@Composable
fun UnityPrivacyConsentDialog(
    onAcceptPersonalized: () -> Unit,
    onSelectNonPersonalized: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* User explicitly picks Personalized or Non-Personalized */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF111C32),
                            Color(0xFF090F1C)
                        )
                    )
                )
                .border(1.5.dp, CyberCyan.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
                .padding(24.dp)
                .testTag("unity_privacy_consent_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.14f))
                        .border(1.5.dp, CyberCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = tr("Ad Privacy & Preferences", "Privacidad y Preferencias de Anuncios"),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(
                        "G Max Gaming Hub uses Unity Ads to keep core optimization features free. Choose whether to allow personalized gaming ads using your device Advertising ID, or use non-personalized ads only.",
                        "G Max Gaming Hub usa Unity Ads para mantener gratis las funciones de optimización. Elige si deseas permitir anuncios personalizados con tu ID de publicidad o solo anuncios no personalizados."
                    ),
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF080E1A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = tr(
                            "You can update this privacy preference at any time inside Settings → Ad Privacy & Consent.",
                            "Puedes cambiar esta preferencia en cualquier momento desde Ajustes → Privacidad y Consentimiento de Anuncios."
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onAcceptPersonalized,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SolarGold,
                        contentColor = Color(0xFF0A101D)
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unity_consent_accept_button")
                ) {
                    Text(
                        text = tr("Accept Personalized Ads", "Aceptar Anuncios Personalizados"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onSelectNonPersonalized,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(vertical = 13.dp, horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unity_consent_decline_button")
                ) {
                    Text(
                        text = tr("Non-Personalized Ads Only", "Solo Anuncios No Personalizados"),
                        color = CyberCyan,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Opt-in 24-Hour Rewarded Ad Card for the Controller Tab to unlock
 * Custom RGB Lightbar Mixer and Controller Input Screen Overlay via Unity Ads.
 */
@Composable
fun GMaxProControllerRewardedUnlockCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unlockUntilMs by UnityAdsManager.proControllerUnlockUntilMs.collectAsState()
    val nowMs = System.currentTimeMillis()
    val isUnlocked = unlockUntilMs > nowMs
    val remainingHours = UnityAdsManager.remainingUnlockHours(nowMs)

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
                    UnityAdsManager.showRewardedAdForProController(context)
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
 * Opt-in 10-Hour Rewarded Ad Card for the FPS & Diag Tab (`BP_Rewarded_Android`)
 * to unlock Screen Overlay HUD Controls (master switch and all HUD metric controls) for 10 hours.
 */
@Composable
fun GMaxHudOverlayRewardedUnlockCard(
    modifier: Modifier = Modifier,
    onUnlocked: () -> Unit = {}
) {
    val context = LocalContext.current
    val unlockUntilMs by UnityAdsManager.hudOverlayUnlockUntilMs.collectAsState()
    val nowMs = System.currentTimeMillis()
    val isUnlocked = unlockUntilMs > nowMs
    val remainingHours = UnityAdsManager.remainingHudOverlayUnlockHours(nowMs)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rewarded_ad_hud_overlay_unlock_card"),
        shape = RoundedCornerShape(18.dp),
        color = if (isUnlocked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            if (isUnlocked) LaunchGreen.copy(alpha = 0.6f)
            else SolarGold.copy(alpha = 0.5f)
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
                        color = (if (isUnlocked) LaunchGreen else SolarGold).copy(alpha = 0.16f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isUnlocked) LaunchGreen else SolarGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isUnlocked) {
                                tr(
                                    "Screen Overlay HUD Unlocked ($remainingHours h left)",
                                    "HUD en Pantalla Desbloqueado ($remainingHours h restantes)"
                                )
                            } else {
                                tr(
                                    "Unlock Screen Overlay HUD Controls (10 Hours)",
                                    "Desbloquear Controles HUD en Pantalla (10 Horas)"
                                )
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isUnlocked) {
                                tr(
                                    "Full access to Floating Screen Overlay & all telemetry HUD metrics is active for 10 hours.",
                                    "Acceso completo activo a la Superposición Flotante y todas las métricas del HUD por 10 horas."
                                )
                            } else {
                                tr(
                                    "Watch a short ad to unlock Screen Overlay HUD controls for 10 hours",
                                    "Mira un anuncio corto para desbloquear los controles del HUD en pantalla por 10 horas"
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
                    UnityAdsManager.showRewardedAdForHudOverlay(context, onUnlocked = onUnlocked)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("watch_rewarded_ad_hud_unlock_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUnlocked) LaunchGreen else SolarGold,
                    contentColor = Color(0xFF0A101D)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isUnlocked) {
                        tr("Extend Unlock for 10 Hours (Watch Ad)", "Extender 10 Horas (Ver Anuncio)")
                    } else {
                        tr(
                            "Watch a short ad to unlock Screen Overlay HUD for 10 hours",
                            "Mira un anuncio corto para desbloquear el HUD (10h)"
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
 * Presentation Host for Unity Full-Screen Ads (`BP_Interstitial_Android` and `BP_Rewarded_Android`).
 * Ensures both Interstitials and Rewarded ads load after `onInitializationComplete` before calling `UnityAds.show`.
 */
@Composable
fun UnityFullScreenPresentationHost() {
    val context = LocalContext.current
    val activeAd by UnityAdsManager.activeFullScreenAd.collectAsState()
    val interstitialLoadState by UnityAdsManager.interstitialLoadState.collectAsState()
    val isInterstitialReady by UnityAdsManager.isInterstitialReady.collectAsState()
    val rewardedLoadState by UnityAdsManager.rewardedLoadState.collectAsState()
    val isRewardedReady by UnityAdsManager.isRewardedReady.collectAsState()

    val presentation = activeAd ?: return

    LaunchedEffect(presentation, isInterstitialReady, isRewardedReady) {
        val activity = UnityAdsManager.findActivity(context)
        if (activity != null) {
            UnityAdsManager.presentFullScreenAdInActivity(activity, presentation)
        }
    }

    val isWaitingForInterstitial = (presentation is UnityAdsManager.FullScreenAdPresentation.AppOpen ||
        presentation is UnityAdsManager.FullScreenAdPresentation.PostAction) && !isInterstitialReady
    val isWaitingForRewarded = presentation is UnityAdsManager.FullScreenAdPresentation.Rewarded && !isRewardedReady

    if (isWaitingForInterstitial || isWaitingForRewarded) {
        val isFailed = if (presentation is UnityAdsManager.FullScreenAdPresentation.Rewarded) {
            rewardedLoadState == UnityAdsManager.RewardedAdLoadState.FAILED
        } else {
            interstitialLoadState == UnityAdsManager.InterstitialAdLoadState.FAILED
        }

        Dialog(
            onDismissRequest = {
                if (presentation is UnityAdsManager.FullScreenAdPresentation.Rewarded) {
                    UnityAdsManager.cancelActiveFullScreenAd()
                } else {
                    UnityAdsManager.dismissActiveFullScreenAd()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .testTag("unity_fullscreen_modal"),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(presentation.titleEn, presentation.titleEs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (presentation is UnityAdsManager.FullScreenAdPresentation.Rewarded) {
                                    UnityAdsManager.cancelActiveFullScreenAd()
                                } else {
                                    UnityAdsManager.dismissActiveFullScreenAd()
                                }
                            },
                            modifier = Modifier.testTag("close_fullscreen_ad_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = tr("Close", "Cerrar")
                            )
                        }
                    }

                    if (isFailed) {
                        Text(
                            text = tr(
                                "Unity Ads could not load an ad right now. Please check your connection and tap Retry.",
                                "Unity Ads no pudo cargar un anuncio en este momento. Verifica tu conexión y toca Reintentar."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (presentation is UnityAdsManager.FullScreenAdPresentation.Rewarded) {
                                        UnityAdsManager.cancelActiveFullScreenAd()
                                    } else {
                                        UnityAdsManager.dismissActiveFullScreenAd()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = tr("Cancel", "Cancelar"),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (presentation is UnityAdsManager.FullScreenAdPresentation.Rewarded) {
                                        UnityAdsManager.retryActiveRewardedAd(context)
                                    } else {
                                        UnityAdsManager.retryActiveInterstitialAd(context)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("retry_rewarded_ad_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr("Retry Ad", "Reintentar"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = tr(presentation.subtitleEn, presentation.subtitleEs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
