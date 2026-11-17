package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure Unity Ads SDK Manager for G Max Gaming Hub (Unity Ads SDK 4.21.0, Live Mode).
 *
 * Manages all ad formats natively via Unity Ads (`Game ID: 800436845`, `testMode = false`):
 * - Banner (`BP_Banner_Android`) — created with an `Activity` context strictly after `onInitializationComplete`
 * - Interstitial (`BP_Interstitial_Android`) — preloaded after `onInitializationComplete` and loaded on-demand before showing
 * - Rewarded Video (`BP_Rewarded_Android`) — preloaded after `onInitializationComplete` and loaded on-demand before showing
 */
object UnityAdsManager {

    private const val TAG = "AdsDebug"

    const val UNITY_GAME_ID = "800436845"
    const val TEST_MODE = false

    const val BANNER_PLACEMENT_ID = "BP_Banner_Android"
    const val FALLBACK_BANNER_PLACEMENT_ID = "Banner_Android"

    const val INTERSTITIAL_PLACEMENT_ID = "BP_Interstitial_Android"
    const val FALLBACK_INTERSTITIAL_PLACEMENT_ID = "Interstitial_Android"

    const val REWARDED_PLACEMENT_ID = "BP_Rewarded_Android"
    const val FALLBACK_REWARDED_PLACEMENT_ID = "Rewarded_Android"

    private const val PREFS_NAME = "gmax_unity_ads_prefs"
    private const val KEY_PRIVACY_CONSENT_ANSWERED = "ad_privacy_consent_answered"
    private const val KEY_PERSONALIZED_ADS_GRANTED = "ad_personalized_consent_granted"
    private const val KEY_PRO_CONTROLLER_UNLOCK_UNTIL = "pro_controller_unlock_until_ms"
    private const val KEY_HUD_OVERLAY_UNLOCK_UNTIL = "hud_overlay_unlock_until_ms"
    private const val KEY_LAST_POST_ACTION_AD_MS = "last_post_action_ad_ms"
    private const val KEY_DEBUG_FORCE_TEST_MODE = "debug_force_test_mode"

    // Zero cooldown between post-action interstitials so BP_Interstitial_Android shows after every eligible action
    private const val POST_ACTION_COOLDOWN_MS = 0L
    // 24-hour unlock duration for Pro Controller Rewarded Ad
    const val REWARD_UNLOCK_DURATION_MS = 24 * 60 * 60 * 1000L
    // 10-hour unlock duration for FPS & Diag Screen Overlay HUD Controls Rewarded Ad
    const val REWARD_HUD_OVERLAY_UNLOCK_DURATION_MS = 10 * 60 * 60 * 1000L

    data class BannerDiagnosticInfo(
        val placementTag: String,
        val placementId: String,
        val status: String,
        val errorCode: String = "",
        val errorMessage: String = "",
        val updatedAtMs: Long = System.currentTimeMillis()
    )

    enum class InterstitialAdLoadState {
        IDLE,
        LOADING,
        READY,
        FAILED
    }

    enum class RewardedAdLoadState {
        IDLE,
        LOADING,
        READY,
        FAILED
    }

    sealed class FullScreenAdPresentation(
        val adUnitId: String,
        val titleEn: String,
        val titleEs: String,
        val subtitleEn: String,
        val subtitleEs: String,
        val onDismissedAfterCompletion: () -> Unit = {}
    ) {
        class AppOpen(
            adUnitId: String = INTERSTITIAL_PLACEMENT_ID,
            onDismissedAfterCompletion: () -> Unit = {}
        ) : FullScreenAdPresentation(
            adUnitId = adUnitId,
            titleEn = "GMAX Gaming Hub",
            titleEs = "GMAX Gaming Hub",
            subtitleEn = "Loading Sponsored Ad...",
            subtitleEs = "Cargando anuncio patrocinado...",
            onDismissedAfterCompletion = onDismissedAfterCompletion
        )

        class PostAction(
            adUnitId: String = INTERSTITIAL_PLACEMENT_ID,
            actionNameEn: String,
            actionNameEs: String,
            onDismissedAfterCompletion: () -> Unit = {}
        ) : FullScreenAdPresentation(
            adUnitId = adUnitId,
            titleEn = actionNameEn,
            titleEs = actionNameEs,
            subtitleEn = "Loading Sponsored Ad...",
            subtitleEs = "Cargando anuncio patrocinado...",
            onDismissedAfterCompletion = onDismissedAfterCompletion
        )

        class Rewarded(
            adUnitId: String = REWARDED_PLACEMENT_ID,
            titleEn: String = "Unlock Custom RGB & Overlay (24h)",
            titleEs: String = "Desbloquea RGB y Superposición (24h)",
            subtitleEn: String = "Loading Rewarded Video Ad...",
            subtitleEs: String = "Cargando video recompensado...",
            val onRewardEarned: () -> Unit
        ) : FullScreenAdPresentation(
            adUnitId = adUnitId,
            titleEn = titleEn,
            titleEs = titleEs,
            subtitleEn = subtitleEn,
            subtitleEs = subtitleEs,
            onDismissedAfterCompletion = onRewardEarned
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + AppCrashGuard.coroutineExceptionHandler)

    @Volatile
    private var cachedAppContext: Context? = null

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    private val isInitStarted = AtomicBoolean(false)

    private var isLoadingInterstitial = false
    private var isLoadingRewarded = false
    private var interstitialRetryCount = 0
    private var rewardedRetryCount = 0
    private var isShowingAd = false
    private var hasShownAppOpenThisLaunch = false
    private var pendingColdLaunchAppOpenRequest = false

    @Volatile
    var loadedInterstitialPlacementId: String = INTERSTITIAL_PLACEMENT_ID
        private set

    @Volatile
    var loadedRewardedPlacementId: String = REWARDED_PLACEMENT_ID
        private set

    private val pendingInterstitialCallbacks = mutableListOf<() -> Unit>()
    private val pendingRewardedCallbacks = mutableListOf<() -> Unit>()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSdkReady = MutableStateFlow(false)
    val isSdkReady: StateFlow<Boolean> = _isSdkReady.asStateFlow()

    private val _needsPrivacyConsentPrompt = MutableStateFlow(false)
    val needsPrivacyConsentPrompt: StateFlow<Boolean> = _needsPrivacyConsentPrompt.asStateFlow()

    private val _isPersonalizedAdsEnabled = MutableStateFlow(true)
    val isPersonalizedAdsEnabled: StateFlow<Boolean> = _isPersonalizedAdsEnabled.asStateFlow()

    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()

    private val _interstitialLoadState = MutableStateFlow(InterstitialAdLoadState.IDLE)
    val interstitialLoadState: StateFlow<InterstitialAdLoadState> = _interstitialLoadState.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _rewardedLoadState = MutableStateFlow(RewardedAdLoadState.IDLE)
    val rewardedLoadState: StateFlow<RewardedAdLoadState> = _rewardedLoadState.asStateFlow()

    private val _activeFullScreenAd = MutableStateFlow<FullScreenAdPresentation?>(null)
    val activeFullScreenAd: StateFlow<FullScreenAdPresentation?> = _activeFullScreenAd.asStateFlow()

    private val _proControllerUnlockUntilMs = MutableStateFlow(0L)
    val proControllerUnlockUntilMs: StateFlow<Long> = _proControllerUnlockUntilMs.asStateFlow()

    private val _hudOverlayUnlockUntilMs = MutableStateFlow(0L)
    val hudOverlayUnlockUntilMs: StateFlow<Long> = _hudOverlayUnlockUntilMs.asStateFlow()

    private val _debugForceTestMode = MutableStateFlow(false)
    val debugForceTestMode: StateFlow<Boolean> = _debugForceTestMode.asStateFlow()

    private val _bannerReloadEpoch = MutableStateFlow(0)
    val bannerReloadEpoch: StateFlow<Int> = _bannerReloadEpoch.asStateFlow()

    private val _adIdDiagnosticStatus = MutableStateFlow("Checking AD_ID...")
    val adIdDiagnosticStatus: StateFlow<String> = _adIdDiagnosticStatus.asStateFlow()

    private val _bannerDiagnostics = MutableStateFlow<Map<String, BannerDiagnosticInfo>>(emptyMap())
    val bannerDiagnostics: StateFlow<Map<String, BannerDiagnosticInfo>> = _bannerDiagnostics.asStateFlow()

    fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun registerActivity(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            activityRef = WeakReference(activity)
            cachedAppContext = activity.applicationContext
        }
    }

    private fun resolveHostActivity(context: Context?): Activity? {
        val fromContext = findActivity(context)
        if (fromContext != null && !fromContext.isFinishing && !fromContext.isDestroyed) {
            activityRef = WeakReference(fromContext)
            return fromContext
        }
        val fromRef = activityRef?.get()
        return if (fromRef != null && !fromRef.isFinishing && !fromRef.isDestroyed) {
            fromRef
        } else {
            null
        }
    }

    /**
     * Detects whether the app is running inside an Android Emulator vs. a physical Android device.
     */
    fun isRunningOnEmulator(
        fingerprint: String = Build.FINGERPRINT.orEmpty(),
        model: String = Build.MODEL.orEmpty(),
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
        device: String = Build.DEVICE.orEmpty(),
        product: String = Build.PRODUCT.orEmpty(),
        hardware: String = Build.HARDWARE.orEmpty()
    ): Boolean {
        val fp = fingerprint.lowercase(Locale.ROOT)
        val mdl = model.lowercase(Locale.ROOT)
        val mfr = manufacturer.lowercase(Locale.ROOT)
        val brd = brand.lowercase(Locale.ROOT)
        val dev = device.lowercase(Locale.ROOT)
        val prd = product.lowercase(Locale.ROOT)
        val hw = hardware.lowercase(Locale.ROOT)

        return fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            fp.contains("emulator") ||
            fp.contains("sdk_gphone") ||
            fp.contains("cuttlefish") ||
            fp.contains("vsoc") ||
            fp.contains("robolectric") ||
            mdl.contains("google_sdk") ||
            mdl.contains("emulator") ||
            mdl.contains("android sdk built for") ||
            mdl.contains("sdk_gphone") ||
            mdl.contains("cuttlefish") ||
            mfr.contains("genymotion") ||
            (brd.startsWith("generic") && dev.startsWith("generic")) ||
            dev.contains("vsoc") ||
            dev.contains("cutf") ||
            dev.contains("emulator") ||
            prd.contains("sdk") ||
            prd.contains("emulator") ||
            prd.contains("simulator") ||
            prd.contains("vbox") ||
            prd.contains("cutf") ||
            prd.contains("vsoc") ||
            hw.contains("goldfish") ||
            hw.contains("ranchu") ||
            hw.contains("cutf") ||
            hw.contains("vsoc") ||
            hw.contains("vbox")
    }

    /**
     * Strictly returns `false` (Live Ads mode) so Unity Ads always serves live placements
     * (`BP_Banner_Android`, `BP_Interstitial_Android`, `BP_Rewarded_Android`) with test mode disabled.
     */
    @Suppress("UNUSED_PARAMETER")
    fun resolveEffectiveTestMode(
        isEmulator: Boolean = false,
        isDebugBuild: Boolean = false,
        forceTestMode: Boolean = false
    ): Boolean {
        return TEST_MODE
    }

    fun recordBannerDiagnostic(
        placementTag: String,
        placementId: String,
        status: String,
        errorCode: String = "",
        errorMessage: String = ""
    ) {
        val current = _bannerDiagnostics.value.toMutableMap()
        current[placementTag] = BannerDiagnosticInfo(
            placementTag = placementTag,
            placementId = placementId,
            status = status,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
        _bannerDiagnostics.value = current
    }

    fun triggerBannerReloadEpoch() {
        _bannerReloadEpoch.value = _bannerReloadEpoch.value + 1
    }

    @Suppress("UNUSED_PARAMETER")
    fun setDebugForceTestMode(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEBUG_FORCE_TEST_MODE, false)
            .apply()
        _debugForceTestMode.value = false

        isInitStarted.set(false)
        _isInitialized.value = false
        _isSdkReady.value = false
        _isInterstitialReady.value = false
        _interstitialLoadState.value = InterstitialAdLoadState.IDLE
        _isRewardedReady.value = false
        _rewardedLoadState.value = RewardedAdLoadState.IDLE
        isLoadingInterstitial = false
        isLoadingRewarded = false
        _bannerDiagnostics.value = emptyMap()
        triggerBannerReloadEpoch()

        initialize(context, _isPersonalizedAdsEnabled.value)
    }

    private fun refreshAdIdDiagnosticAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val hasPerm = ContextCompat.checkSelfPermission(
                appContext,
                "com.google.android.gms.permission.AD_ID"
            ) == PackageManager.PERMISSION_GRANTED
            val statusText = try {
                val clazz = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
                val method = clazz.getMethod("getAdvertisingIdInfo", Context::class.java)
                val info = method.invoke(null, appContext)
                val limitMethod = info?.javaClass?.getMethod("isLimitAdTrackingEnabled")
                val idMethod = info?.javaClass?.getMethod("getId")
                val isLimited = (limitMethod?.invoke(info) as? Boolean) ?: false
                val idStr = idMethod?.invoke(info) as? String
                when {
                    !hasPerm -> "AD_ID Denied"
                    isLimited -> "LAT Enabled"
                    idStr.isNullOrBlank() || idStr.startsWith("00000000-") -> "Zeroed AD_ID"
                    else -> "AD_ID Active"
                }
            } catch (t: Throwable) {
                val causeMsg = t.cause?.javaClass?.simpleName ?: t.javaClass.simpleName
                if (hasPerm) "AD_ID Perm OK ($causeMsg)" else "No AD_ID ($causeMsg)"
            }
            _adIdDiagnosticStatus.value = statusText
        }
    }

    /**
     * Kept for test compatibility; runtime requests strictly use the exact dashboard IDs
     * (`BP_Banner_Android`, `BP_Interstitial_Android`, `BP_Rewarded_Android`).
     */
    fun getFallbackPlacementId(primaryPlacementId: String): String? {
        return when (primaryPlacementId) {
            INTERSTITIAL_PLACEMENT_ID -> FALLBACK_INTERSTITIAL_PLACEMENT_ID
            REWARDED_PLACEMENT_ID -> FALLBACK_REWARDED_PLACEMENT_ID
            BANNER_PLACEMENT_ID -> FALLBACK_BANNER_PLACEMENT_ID
            else -> null
        }
    }

    private fun configureUnitySdkFlags() {
        try {
            UnityAds.debugMode = false
        } catch (_: Throwable) {
        }
    }

    /**
     * Commits Unity-native GDPR, Privacy, PIPL, and non-behavioral flags before SDK initialization.
     */
    fun syncPrivacyMetaData(context: Context, personalizedAdsGranted: Boolean) {
        val appContext = context.applicationContext
        try {
            val gdprMetaData = MetaData(appContext)
            gdprMetaData.set("gdpr.consent", personalizedAdsGranted)
            gdprMetaData.commit()

            val privacyMetaData = MetaData(appContext)
            privacyMetaData.set("privacy.consent", personalizedAdsGranted)
            privacyMetaData.set("pipl.consent", personalizedAdsGranted)
            privacyMetaData.commit()

            val userMetaData = MetaData(appContext)
            userMetaData.set("user.nonbehavioral", !personalizedAdsGranted)
            userMetaData.commit()

            Log.d(
                TAG,
                "UnityAds Privacy MetaData committed: gdpr.consent=$personalizedAdsGranted, privacy.consent=$personalizedAdsGranted, user.nonbehavioral=${!personalizedAdsGranted}"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "UnityAds Privacy MetaData commit failed: ${t.message}")
        }
    }

    fun initializeAndPreload(context: Context) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        findActivity(context)?.let { registerActivity(it) }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _proControllerUnlockUntilMs.value = prefs.getLong(KEY_PRO_CONTROLLER_UNLOCK_UNTIL, 0L)
        _hudOverlayUnlockUntilMs.value = prefs.getLong(KEY_HUD_OVERLAY_UNLOCK_UNTIL, 0L)
        _debugForceTestMode.value = false
        refreshAdIdDiagnosticAsync(appContext)

        val consentAnswered = prefs.getBoolean(KEY_PRIVACY_CONSENT_ANSWERED, false)
        val personalizedGranted = prefs.getBoolean(KEY_PERSONALIZED_ADS_GRANTED, true)
        _isPersonalizedAdsEnabled.value = personalizedGranted
        _needsPrivacyConsentPrompt.value = !consentAnswered

        // Always initialize UnityAds immediately so onInitializationComplete finishes as early as possible
        initialize(context, personalizedGranted)
    }

    fun submitPrivacyConsent(context: Context, personalizedAdsGranted: Boolean) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        findActivity(context)?.let { registerActivity(it) }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRIVACY_CONSENT_ANSWERED, true)
            .putBoolean(KEY_PERSONALIZED_ADS_GRANTED, personalizedAdsGranted)
            .apply()

        _isPersonalizedAdsEnabled.value = personalizedAdsGranted
        _needsPrivacyConsentPrompt.value = false
        syncPrivacyMetaData(appContext, personalizedAdsGranted)

        if (_isSdkReady.value && _isInitialized.value) {
            triggerBannerReloadEpoch()
            if (pendingColdLaunchAppOpenRequest && !hasShownAppOpenThisLaunch) {
                pendingColdLaunchAppOpenRequest = false
                hasShownAppOpenThisLaunch = true
                val presentation = FullScreenAdPresentation.AppOpen(INTERSTITIAL_PLACEMENT_ID)
                _activeFullScreenAd.value = presentation
                resolveHostActivity(context)?.let { presentFullScreenAdInActivity(it, presentation) }
            } else {
                preloadInterstitial()
            }
            preloadRewarded()
        } else {
            initialize(context, personalizedAdsGranted)
        }
    }

    fun openPrivacyConsentDialog() {
        _needsPrivacyConsentPrompt.value = true
    }

    fun initialize(
        context: Context,
        personalizedAdsGranted: Boolean = _isPersonalizedAdsEnabled.value,
        onInitFinished: (success: Boolean) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        val hostActivity = resolveHostActivity(context)
        val initContext: Context = hostActivity ?: appContext
        syncPrivacyMetaData(appContext, personalizedAdsGranted)
        configureUnitySdkFlags()

        val sdkAlreadyInitialized = try {
            UnityAds.isInitialized
        } catch (_: Throwable) {
            false
        }

        if (sdkAlreadyInitialized || (_isInitialized.value && _isSdkReady.value)) {
            _isInitialized.value = true
            _isSdkReady.value = true
            onInitFinished(true)
            triggerBannerReloadEpoch()
            preloadInterstitial()
            preloadRewarded()
            return
        }

        if (!isInitStarted.compareAndSet(false, true)) return

        try {
            Log.d(
                TAG,
                "Initializing UnityAds SDK (LIVE MODE): gameId=$UNITY_GAME_ID, testMode=false, context=${initContext.javaClass.simpleName}"
            )
            UnityAds.initialize(
                initContext,
                UNITY_GAME_ID,
                false, // Strictly Live Ads mode (testMode = false)
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        Log.d(
                            TAG,
                            "UnityAds onInitializationComplete: gameId=$UNITY_GAME_ID, testMode=false"
                        )
                        _isInitialized.value = true
                        _isSdkReady.value = true
                        onInitFinished(true)
                        scope.launch {
                            triggerBannerReloadEpoch()
                            preloadInterstitial()
                            preloadRewarded()
                        }
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError?,
                        message: String?
                    ) {
                        Log.w(
                            TAG,
                            "UnityAds onInitializationFailed: gameId=$UNITY_GAME_ID, error=$error, message=$message"
                        )
                        isInitStarted.set(false)
                        _isInitialized.value = false
                        _isSdkReady.value = false
                        onInitFinished(false)
                    }
                }
            )
        } catch (t: Throwable) {
            isInitStarted.set(false)
            _isInitialized.value = false
            _isSdkReady.value = false
            Log.w(TAG, "UnityAds.initialize threw exception: ${t.message}")
            onInitFinished(false)
        }
    }

    /**
     * Preloads or loads on-demand `BP_Interstitial_Android` strictly after `onInitializationComplete`.
     */
    @Suppress("UNUSED_PARAMETER")
    fun preloadInterstitial(
        placementId: String = INTERSTITIAL_PLACEMENT_ID,
        allowFallback: Boolean = false,
        onReady: (() -> Unit)? = null
    ) {
        val exactPlacementId = INTERSTITIAL_PLACEMENT_ID
        val sdkReady = try {
            _isSdkReady.value && (_isInitialized.value || UnityAds.isInitialized)
        } catch (_: Throwable) {
            _isSdkReady.value && _isInitialized.value
        }

        if (!sdkReady) {
            Log.d(TAG, "Unity Interstitial load deferred until onInitializationComplete ($exactPlacementId).")
            if (onReady != null) {
                pendingInterstitialCallbacks.add(onReady)
            }
            cachedAppContext?.let { initialize(it, _isPersonalizedAdsEnabled.value) }
            return
        }

        if (_isInterstitialReady.value) {
            _interstitialLoadState.value = InterstitialAdLoadState.READY
            onReady?.invoke()
            return
        }

        if (onReady != null) {
            pendingInterstitialCallbacks.add(onReady)
        }

        if (isLoadingInterstitial) return
        isLoadingInterstitial = true
        _interstitialLoadState.value = InterstitialAdLoadState.LOADING

        try {
            Log.d(TAG, "Calling UnityAds.load for Interstitial placement=$exactPlacementId")
            UnityAds.load(
                exactPlacementId,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(loadedPlacement: String) {
                        Log.d(TAG, "Unity Interstitial onUnityAdsAdLoaded: placement=$loadedPlacement")
                        isLoadingInterstitial = false
                        interstitialRetryCount = 0
                        loadedInterstitialPlacementId = exactPlacementId
                        _isInterstitialReady.value = true
                        _interstitialLoadState.value = InterstitialAdLoadState.READY

                        val callbacks = pendingInterstitialCallbacks.toList()
                        pendingInterstitialCallbacks.clear()
                        callbacks.forEach { it.invoke() }

                        if (pendingColdLaunchAppOpenRequest &&
                            !hasShownAppOpenThisLaunch &&
                            !_needsPrivacyConsentPrompt.value &&
                            _activeFullScreenAd.value == null
                        ) {
                            pendingColdLaunchAppOpenRequest = false
                            hasShownAppOpenThisLaunch = true
                            val presentation = FullScreenAdPresentation.AppOpen(exactPlacementId)
                            _activeFullScreenAd.value = presentation
                            resolveHostActivity(cachedAppContext)?.let { activity ->
                                presentFullScreenAdInActivity(activity, presentation)
                            }
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        failedPlacement: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        isLoadingInterstitial = false
                        _isInterstitialReady.value = false
                        _interstitialLoadState.value = InterstitialAdLoadState.FAILED
                        pendingInterstitialCallbacks.clear()

                        Log.i(
                            TAG,
                            "Unity Interstitial failed to load: placement=$failedPlacement, reason=$error ($message)"
                        )

                        // If an interstitial presentation was waiting on this load, release the UI cleanly
                        val currentActive = _activeFullScreenAd.value
                        if (currentActive is FullScreenAdPresentation.AppOpen ||
                            currentActive is FullScreenAdPresentation.PostAction
                        ) {
                            dismissActiveFullScreenAd()
                        }

                        if (interstitialRetryCount < 5) {
                            interstitialRetryCount++
                            scope.launch {
                                delay(8_000L * interstitialRetryCount)
                                if (!_isInterstitialReady.value && !isLoadingInterstitial) {
                                    preloadInterstitial(placementId = INTERSTITIAL_PLACEMENT_ID)
                                }
                            }
                        } else {
                            pendingColdLaunchAppOpenRequest = false
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            isLoadingInterstitial = false
            _isInterstitialReady.value = false
            _interstitialLoadState.value = InterstitialAdLoadState.FAILED
            pendingInterstitialCallbacks.clear()
            Log.w(TAG, "UnityAds.load(Interstitial) exception: ${t.message}")
            val currentActive = _activeFullScreenAd.value
            if (currentActive is FullScreenAdPresentation.AppOpen ||
                currentActive is FullScreenAdPresentation.PostAction
            ) {
                dismissActiveFullScreenAd()
            }
        }
    }

    /**
     * Preloads or loads on-demand `BP_Rewarded_Android` strictly after `onInitializationComplete`.
     */
    @Suppress("UNUSED_PARAMETER")
    fun preloadRewarded(
        placementId: String = REWARDED_PLACEMENT_ID,
        allowFallback: Boolean = false,
        onReady: (() -> Unit)? = null
    ) {
        val exactPlacementId = REWARDED_PLACEMENT_ID
        val sdkReady = try {
            _isSdkReady.value && (_isInitialized.value || UnityAds.isInitialized)
        } catch (_: Throwable) {
            _isSdkReady.value && _isInitialized.value
        }

        if (!sdkReady) {
            Log.d(TAG, "Unity Rewarded load deferred until onInitializationComplete ($exactPlacementId).")
            if (onReady != null) {
                pendingRewardedCallbacks.add(onReady)
            }
            cachedAppContext?.let { initialize(it, _isPersonalizedAdsEnabled.value) }
            return
        }

        if (_isRewardedReady.value) {
            _rewardedLoadState.value = RewardedAdLoadState.READY
            onReady?.invoke()
            return
        }

        if (onReady != null) {
            pendingRewardedCallbacks.add(onReady)
        }

        if (isLoadingRewarded) return
        isLoadingRewarded = true
        _rewardedLoadState.value = RewardedAdLoadState.LOADING

        try {
            Log.d(TAG, "Calling UnityAds.load for Rewarded placement=$exactPlacementId")
            UnityAds.load(
                exactPlacementId,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(loadedPlacement: String) {
                        Log.d(TAG, "Unity Rewarded onUnityAdsAdLoaded: placement=$loadedPlacement")
                        isLoadingRewarded = false
                        rewardedRetryCount = 0
                        loadedRewardedPlacementId = exactPlacementId
                        _isRewardedReady.value = true
                        _rewardedLoadState.value = RewardedAdLoadState.READY

                        val callbacks = pendingRewardedCallbacks.toList()
                        pendingRewardedCallbacks.clear()
                        callbacks.forEach { it.invoke() }
                    }

                    override fun onUnityAdsFailedToLoad(
                        failedPlacement: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        isLoadingRewarded = false
                        _isRewardedReady.value = false
                        _rewardedLoadState.value = RewardedAdLoadState.FAILED
                        pendingRewardedCallbacks.clear()

                        Log.i(
                            TAG,
                            "Unity Rewarded failed to load: placement=$failedPlacement, reason=$error ($message)"
                        )

                        if (rewardedRetryCount < 5) {
                            rewardedRetryCount++
                            scope.launch {
                                delay(8_000L * rewardedRetryCount)
                                if (!_isRewardedReady.value && !isLoadingRewarded) {
                                    preloadRewarded(placementId = REWARDED_PLACEMENT_ID)
                                }
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            isLoadingRewarded = false
            _isRewardedReady.value = false
            _rewardedLoadState.value = RewardedAdLoadState.FAILED
            pendingRewardedCallbacks.clear()
            Log.w(TAG, "UnityAds.load(Rewarded) exception: ${t.message}")
        }
    }

    fun showInterstitial(
        activity: Activity,
        onFinished: () -> Unit
    ) {
        if (isShowingAd || !_isSdkReady.value || !_isInterstitialReady.value || activity.isFinishing || activity.isDestroyed) {
            if (_isSdkReady.value && !_isInterstitialReady.value) {
                preloadInterstitial()
            }
            onFinished()
            return
        }
        isShowingAd = true
        _isInterstitialReady.value = false
        _interstitialLoadState.value = InterstitialAdLoadState.IDLE
        val placementToShow = INTERSTITIAL_PLACEMENT_ID

        try {
            Log.d(TAG, "Calling UnityAds.show for Interstitial placement=$placementToShow")
            UnityAds.show(
                activity,
                placementToShow,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        Log.w(
                            TAG,
                            "Unity Interstitial onUnityAdsShowFailure: placement=$placementId, error=$error, message=$message"
                        )
                        isShowingAd = false
                        onFinished()
                        preloadInterstitial()
                    }

                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.d(TAG, "Unity Interstitial onUnityAdsShowStart: placement=$placementId")
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.d(TAG, "Unity Interstitial onUnityAdsShowClick: placement=$placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        Log.d(TAG, "Unity Interstitial onUnityAdsShowComplete: placement=$placementId, state=$state")
                        isShowingAd = false
                        onFinished()
                        preloadInterstitial()
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "UnityAds.show(Interstitial) exception: ${t.message}")
            isShowingAd = false
            onFinished()
            preloadInterstitial()
        }
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onClosed: () -> Unit,
        onShowFailed: () -> Unit
    ) {
        if (isShowingAd || !_isSdkReady.value || !_isRewardedReady.value || activity.isFinishing || activity.isDestroyed) {
            onShowFailed()
            return
        }
        isShowingAd = true
        _isRewardedReady.value = false
        _rewardedLoadState.value = RewardedAdLoadState.IDLE
        val placementToShow = REWARDED_PLACEMENT_ID

        var rewardDelivered = false
        try {
            Log.d(TAG, "Calling UnityAds.show for Rewarded placement=$placementToShow")
            UnityAds.show(
                activity,
                placementToShow,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        Log.w(
                            TAG,
                            "Unity Rewarded onUnityAdsShowFailure: placement=$placementId, error=$error, message=$message"
                        )
                        isShowingAd = false
                        _rewardedLoadState.value = RewardedAdLoadState.FAILED
                        onShowFailed()
                        preloadRewarded()
                    }

                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.d(TAG, "Unity Rewarded onUnityAdsShowStart: placement=$placementId")
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.d(TAG, "Unity Rewarded onUnityAdsShowClick: placement=$placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        Log.d(TAG, "Unity Rewarded onUnityAdsShowComplete: placement=$placementId, state=$state")
                        isShowingAd = false
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED && !rewardDelivered) {
                            rewardDelivered = true
                            Log.d(TAG, "Unity Rewarded reward earned: placement=$placementId")
                            onRewardEarned()
                        }
                        onClosed()
                        preloadRewarded()
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "UnityAds.show(Rewarded) exception: ${t.message}")
            isShowingAd = false
            _rewardedLoadState.value = RewardedAdLoadState.FAILED
            onShowFailed()
            preloadRewarded()
        }
    }

    fun isProControllerUnlocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        return _proControllerUnlockUntilMs.value > nowMs
    }

    fun remainingUnlockHours(nowMs: Long = System.currentTimeMillis()): Int {
        val diff = _proControllerUnlockUntilMs.value - nowMs
        if (diff <= 0L) return 0
        return ((diff + 3_599_999L) / 3_600_000L).toInt().coerceAtLeast(1)
    }

    fun isHudOverlayUnlocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        return _hudOverlayUnlockUntilMs.value > nowMs
    }

    fun remainingHudOverlayUnlockHours(nowMs: Long = System.currentTimeMillis()): Int {
        val diff = _hudOverlayUnlockUntilMs.value - nowMs
        if (diff <= 0L) return 0
        return ((diff + 3_599_999L) / 3_600_000L).toInt().coerceIn(1, 10)
    }

    fun maybeShowAppOpenAdOnLaunch() {
        if (hasShownAppOpenThisLaunch) return
        if (!_needsPrivacyConsentPrompt.value) {
            hasShownAppOpenThisLaunch = true
            pendingColdLaunchAppOpenRequest = false
            val presentation = FullScreenAdPresentation.AppOpen(INTERSTITIAL_PLACEMENT_ID)
            _activeFullScreenAd.value = presentation
            resolveHostActivity(cachedAppContext)?.let { activity ->
                presentFullScreenAdInActivity(activity, presentation)
            }
        } else {
            pendingColdLaunchAppOpenRequest = true
            if (_isSdkReady.value) {
                preloadInterstitial()
            }
        }
    }

    /**
     * Triggers an Interstitial Ad (`BP_Interstitial_Android`) after an action with zero cooldown.
     * If `BP_Interstitial_Android` is already preloaded, shows it immediately; if still loading or not yet loaded,
     * loads it on demand after `onInitializationComplete` and shows it as soon as `onUnityAdsAdLoaded` fires.
     */
    fun maybeTriggerPostActionAd(
        context: Context,
        actionNameEn: String = "",
        actionNameEs: String = "",
        actionLabelEn: String = actionNameEn,
        actionLabelEs: String = actionNameEs,
        onAdFinished: () -> Unit = {}
    ): Boolean {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        findActivity(context)?.let { registerActivity(it) }
        val resolvedEn = actionLabelEn.ifBlank { actionNameEn.ifBlank { "Optimization" } }
        val resolvedEs = actionLabelEs.ifBlank { actionNameEs.ifBlank { "Optimización" } }

        val activity = resolveHostActivity(context)
        if (_needsPrivacyConsentPrompt.value || activity == null) {
            onAdFinished()
            return false
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (POST_ACTION_COOLDOWN_MS > 0L) {
            val lastShown = prefs.getLong(KEY_LAST_POST_ACTION_AD_MS, 0L)
            if (now - lastShown < POST_ACTION_COOLDOWN_MS) {
                onAdFinished()
                return false
            }
        }

        prefs.edit().putLong(KEY_LAST_POST_ACTION_AD_MS, now).apply()
        val presentation = FullScreenAdPresentation.PostAction(
            adUnitId = INTERSTITIAL_PLACEMENT_ID,
            actionNameEn = resolvedEn,
            actionNameEs = resolvedEs,
            onDismissedAfterCompletion = onAdFinished
        )
        _activeFullScreenAd.value = presentation
        presentFullScreenAdInActivity(activity, presentation)
        return true
    }

    fun showRewardedAdForProController(
        context: Context,
        onUnlocked: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        findActivity(context)?.let { registerActivity(it) }
        val presentation = FullScreenAdPresentation.Rewarded(
            adUnitId = REWARDED_PLACEMENT_ID,
            onRewardEarned = {
                val unlockUntil = System.currentTimeMillis() + REWARD_UNLOCK_DURATION_MS
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_PRO_CONTROLLER_UNLOCK_UNTIL, unlockUntil)
                    .apply()
                _proControllerUnlockUntilMs.value = unlockUntil
                onUnlocked()
                preloadRewarded()
            }
        )
        _activeFullScreenAd.value = presentation
        resolveHostActivity(context)?.let { activity ->
            presentFullScreenAdInActivity(activity, presentation)
        }
    }

    fun showRewardedAdForHudOverlay(
        context: Context,
        onUnlocked: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        findActivity(context)?.let { registerActivity(it) }
        val presentation = FullScreenAdPresentation.Rewarded(
            adUnitId = REWARDED_PLACEMENT_ID,
            titleEn = "Unlock Screen Overlay HUD (10h)",
            titleEs = "Desbloquea HUD en Pantalla (10h)",
            subtitleEn = "Loading Rewarded Video Ad...",
            subtitleEs = "Cargando video recompensado...",
            onRewardEarned = {
                val unlockUntil = System.currentTimeMillis() + REWARD_HUD_OVERLAY_UNLOCK_DURATION_MS
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_HUD_OVERLAY_UNLOCK_UNTIL, unlockUntil)
                    .apply()
                _hudOverlayUnlockUntilMs.value = unlockUntil
                onUnlocked()
                preloadRewarded()
            }
        )
        _activeFullScreenAd.value = presentation
        resolveHostActivity(context)?.let { activity ->
            presentFullScreenAdInActivity(activity, presentation)
        }
    }

    fun requestHudOverlayRewardedUnlock(
        context: Context,
        onUnlocked: () -> Unit = {}
    ) {
        showRewardedAdForHudOverlay(context = context, onUnlocked = onUnlocked)
    }

    fun showRewardedAdForStreakRepair(
        context: Context,
        lostStreakDays: Int,
        onRepaired: () -> Unit = {}
    ) {
        cachedAppContext = context.applicationContext
        findActivity(context)?.let { registerActivity(it) }
        val presentation = FullScreenAdPresentation.Rewarded(
            adUnitId = REWARDED_PLACEMENT_ID,
            titleEn = "Repair $lostStreakDays-Day Streak",
            titleEs = "Reparar Racha de $lostStreakDays Días",
            subtitleEn = "Loading Rewarded Video Ad...",
            subtitleEs = "Cargando video recompensado...",
            onRewardEarned = {
                onRepaired()
                preloadRewarded()
            }
        )
        _activeFullScreenAd.value = presentation
        resolveHostActivity(context)?.let { activity ->
            presentFullScreenAdInActivity(activity, presentation)
        }
    }

    fun requestProControllerRewardedUnlock(
        context: Context,
        onUnlocked: () -> Unit = {}
    ) {
        showRewardedAdForProController(context = context, onUnlocked = onUnlocked)
    }

    fun showRewardedAdForRivalsSquadCreation(
        context: Context,
        onRewardEarned: () -> Unit = {}
    ) {
        cachedAppContext = context.applicationContext
        findActivity(context)?.let { registerActivity(it) }
        val presentation = FullScreenAdPresentation.Rewarded(
            adUnitId = REWARDED_PLACEMENT_ID,
            titleEn = "Create Rivals Squad",
            titleEs = "Crear Escuadrón Rivals",
            subtitleEn = "Loading Unity Rewarded Video Ad...",
            subtitleEs = "Cargando video recompensado...",
            onRewardEarned = {
                onRewardEarned()
                preloadRewarded()
            }
        )
        _activeFullScreenAd.value = presentation
        resolveHostActivity(context)?.let { activity ->
            presentFullScreenAdInActivity(activity, presentation)
        }
    }

    fun presentFullScreenAdInActivity(
        activity: Activity,
        presentation: FullScreenAdPresentation
    ) {
        if (isShowingAd || _needsPrivacyConsentPrompt.value) return
        registerActivity(activity)

        when (presentation) {
            is FullScreenAdPresentation.AppOpen,
            is FullScreenAdPresentation.PostAction -> {
                if (_isInterstitialReady.value) {
                    showInterstitial(activity) {
                        dismissActiveFullScreenAd()
                    }
                } else {
                    preloadInterstitial {
                        val activeActivity = resolveHostActivity(activity)
                        if (activeActivity != null &&
                            _activeFullScreenAd.value === presentation &&
                            !activeActivity.isFinishing &&
                            !activeActivity.isDestroyed
                        ) {
                            presentFullScreenAdInActivity(activeActivity, presentation)
                        }
                    }
                }
            }

            is FullScreenAdPresentation.Rewarded -> {
                if (_isRewardedReady.value) {
                    showRewarded(
                        activity = activity,
                        onRewardEarned = { presentation.onRewardEarned.invoke() },
                        onClosed = {
                            _activeFullScreenAd.value = null
                        },
                        onShowFailed = {
                            // Keep retry dialog visible on failure
                        }
                    )
                } else {
                    preloadRewarded {
                        val activeActivity = resolveHostActivity(activity)
                        if (activeActivity != null &&
                            _activeFullScreenAd.value === presentation &&
                            !activeActivity.isFinishing &&
                            !activeActivity.isDestroyed
                        ) {
                            presentFullScreenAdInActivity(activeActivity, presentation)
                        }
                    }
                }
            }
        }
    }

    fun retryActiveInterstitialAd(context: Context) {
        val presentation = _activeFullScreenAd.value
        if (presentation !is FullScreenAdPresentation.AppOpen &&
            presentation !is FullScreenAdPresentation.PostAction
        ) return
        val activity = resolveHostActivity(context)
        if (!_isSdkReady.value) {
            initialize(context) { success ->
                if (success && activity != null && _activeFullScreenAd.value === presentation) {
                    preloadInterstitial {
                        val activeActivity = resolveHostActivity(activity)
                        if (activeActivity != null &&
                            _activeFullScreenAd.value === presentation &&
                            !activeActivity.isFinishing &&
                            !activeActivity.isDestroyed
                        ) {
                            presentFullScreenAdInActivity(activeActivity, presentation)
                        }
                    }
                }
            }
            return
        }
        preloadInterstitial {
            val activeActivity = resolveHostActivity(activity)
            if (activeActivity != null &&
                _activeFullScreenAd.value === presentation &&
                !activeActivity.isFinishing &&
                !activeActivity.isDestroyed
            ) {
                presentFullScreenAdInActivity(activeActivity, presentation)
            }
        }
    }

    fun retryActiveRewardedAd(context: Context) {
        val presentation = _activeFullScreenAd.value as? FullScreenAdPresentation.Rewarded ?: return
        val activity = resolveHostActivity(context)
        if (!_isSdkReady.value) {
            initialize(context) { success ->
                if (success && activity != null && _activeFullScreenAd.value === presentation) {
                    preloadRewarded {
                        val activeActivity = resolveHostActivity(activity)
                        if (activeActivity != null &&
                            _activeFullScreenAd.value === presentation &&
                            !activeActivity.isFinishing &&
                            !activeActivity.isDestroyed
                        ) {
                            presentFullScreenAdInActivity(activeActivity, presentation)
                        }
                    }
                }
            }
            return
        }
        preloadRewarded {
            val activeActivity = resolveHostActivity(activity)
            if (activeActivity != null &&
                _activeFullScreenAd.value === presentation &&
                !activeActivity.isFinishing &&
                !activeActivity.isDestroyed
            ) {
                presentFullScreenAdInActivity(activeActivity, presentation)
            }
        }
    }

    fun cancelActiveFullScreenAd() {
        isShowingAd = false
        pendingInterstitialCallbacks.clear()
        pendingRewardedCallbacks.clear()
        _activeFullScreenAd.value = null
    }

    fun dismissActiveFullScreenAd() {
        isShowingAd = false
        val finishedPresentation = _activeFullScreenAd.value
        _activeFullScreenAd.value = null
        if (finishedPresentation !is FullScreenAdPresentation.Rewarded) {
            finishedPresentation?.onDismissedAfterCompletion?.invoke()
        }
    }

    fun clearPendingCallbacks() {
        pendingInterstitialCallbacks.clear()
        pendingRewardedCallbacks.clear()
        isShowingAd = false
    }
}
