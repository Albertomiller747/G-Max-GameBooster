package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.util.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class GamingVolumeLockState(
    val isEnabled: Boolean = false,
    val lockedVolumePercent: Int = 80,
    val isGameInForeground: Boolean = false,
    val snapBackCount: Int = 0,
    val lastNoticeMessage: String? = null,
    val lastSnapBackTimestampMs: Long = 0L
)

/**
 * GamingVolumeLockManager:
 * Uses Android's AudioManager (STREAM_MUSIC) to prevent accidental volume-down mashing during intense firefights.
 * While a game is in the foreground (or when Volume Lock monitoring is active), if the device detects the volume
 * changing away from the locked level, it immediately forces it back to the exact locked level and displays a
 * compact, non-blocking overlay notice banner telling the player that Gaming Volume Lock forced it back and they
 * must leave their game to re-enter GMax and turn it off if they want to change the volume.
 */
object GamingVolumeLockManager {

    private const val PREFS_NAME = "gmax_gaming_volume_lock_prefs"
    private const val KEY_ENABLED = "volume_lock_enabled"
    private const val KEY_PERCENT = "volume_lock_percent"
    private const val KEY_SNAP_COUNT = "volume_lock_snap_count"
    private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(GamingVolumeLockState())
    val state: StateFlow<GamingVolumeLockState> = _state.asStateFlow()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isEnforcingVolume = false

    private var volumeReceiver: BroadcastReceiver? = null
    private var settingsObserver: ContentObserver? = null
    private var activeBannerView: View? = null
    private var activeWindowManager: WindowManager? = null
    private var bannerDismissRunnable: Runnable? = null

    fun init(context: Context) {
        if (isInitialized) return
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val pct = prefs.getInt(KEY_PERCENT, 80).coerceIn(10, 100)
        val snaps = prefs.getInt(KEY_SNAP_COUNT, 0)
        _state.value = _state.value.copy(
            isEnabled = enabled,
            lockedVolumePercent = pct,
            snapBackCount = snaps
        )
        isInitialized = true
        registerVolumeObservers(appCtx)
        if (enabled && _state.value.isGameInForeground) {
            applyLockedVolumeImmediately(appCtx, showNotice = false)
        }
    }

    fun loadState(context: Context): GamingVolumeLockState {
        init(context)
        return _state.value
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        init(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        _state.value = _state.value.copy(isEnabled = enabled)
        if (enabled) {
            applyLockedVolumeImmediately(appCtx, showNotice = false)
        } else {
            dismissOverlayNoticeBanner()
        }
    }

    fun setLockedVolumePercent(context: Context, percent: Int) {
        val appCtx = context.applicationContext
        init(appCtx)
        val clamped = percent.coerceIn(10, 100)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PERCENT, clamped)
            .apply()
        _state.value = _state.value.copy(lockedVolumePercent = clamped)
        if (_state.value.isEnabled) {
            applyLockedVolumeImmediately(appCtx, showNotice = false)
        }
    }

    fun setGameInForeground(context: Context, inForeground: Boolean) {
        val appCtx = context.applicationContext
        init(appCtx)
        _state.value = _state.value.copy(isGameInForeground = inForeground)
        if (inForeground && _state.value.isEnabled) {
            applyLockedVolumeImmediately(appCtx, showNotice = false)
        }
    }

    fun computeTargetStreamIndex(audioManager: AudioManager, percent: Int): Int {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
        val clampedPct = percent.coerceIn(10, 100)
        val computed = ((clampedPct / 100f) * maxVol).roundToInt()
        return computed.coerceIn(minVol.coerceAtLeast(1), maxVol)
    }

    fun applyLockedVolumeImmediately(context: Context, showNotice: Boolean = false): Int {
        val appCtx = context.applicationContext
        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        val targetIndex = computeTargetStreamIndex(audioManager, _state.value.lockedVolumePercent)
        try {
            isEnforcingVolume = true
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        } catch (_: Exception) {
        } finally {
            isEnforcingVolume = false
        }
        if (showNotice) {
            recordAndShowSnapBackNotice(appCtx)
        }
        return targetIndex
    }

    /**
     * Checks if the current STREAM_MUSIC volume differs from the locked target volume while Gaming Volume Lock
     * is active, and if so, forces it back to the exact locked level and displays the notice popup.
     */
    fun enforceLockedVolumeIfChanged(
        context: Context,
        requireForegroundGame: Boolean = true
    ): Boolean {
        val appCtx = context.applicationContext
        init(appCtx)
        val currentState = _state.value
        if (!currentState.isEnabled) return false
        if (requireForegroundGame && !currentState.isGameInForeground) return false
        if (isEnforcingVolume) return false

        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetVol = computeTargetStreamIndex(audioManager, currentState.lockedVolumePercent)
        if (currentVol != targetVol) {
            try {
                isEnforcingVolume = true
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            } catch (_: Exception) {
            } finally {
                isEnforcingVolume = false
            }
            recordAndShowSnapBackNotice(appCtx)
            return true
        }
        return false
    }

    /**
     * Simulates an accidental volume-down button press during a match so the user can test
     * the instant AudioManager snap-back and compact notice popup directly from Launcher Options or Tweaks.
     */
    fun simulateAccidentalVolumeDropAndSnapBack(context: Context): Boolean {
        val appCtx = context.applicationContext
        init(appCtx)
        if (!_state.value.isEnabled) {
            setEnabled(appCtx, true)
        }
        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val targetVol = computeTargetStreamIndex(audioManager, _state.value.lockedVolumePercent)
        val droppedVol = (targetVol - 3).coerceAtLeast(0)
        try {
            isEnforcingVolume = true
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, droppedVol, 0)
        } catch (_: Exception) {
        } finally {
            isEnforcingVolume = false
        }
        return enforceLockedVolumeIfChanged(appCtx, requireForegroundGame = false)
    }

    private fun recordAndShowSnapBackNotice(context: Context) {
        val pct = _state.value.lockedVolumePercent
        val newCount = _state.value.snapBackCount + 1
        val noticeText = AppStrings.trText(
            "🔒 Gaming Volume Locked at $pct%: Forced back! Leave game & open GMax to turn off Volume Lock if you want to change volume.",
            "🔒 Volumen Gamer Bloqueado al $pct%: ¡Restaurado! Sal del juego y abre GMax para desactivar el bloqueo si deseas cambiar el volumen."
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SNAP_COUNT, newCount)
            .apply()
        _state.value = _state.value.copy(
            snapBackCount = newCount,
            lastNoticeMessage = noticeText,
            lastSnapBackTimestampMs = System.currentTimeMillis()
        )
        mainHandler.post {
            showCompactNonBlockingNoticeBanner(context, noticeText, pct)
        }
    }

    private fun showCompactNonBlockingNoticeBanner(
        context: Context,
        noticeText: String,
        lockedPercent: Int
    ) {
        val appCtx = context.applicationContext
        if (!Settings.canDrawOverlays(appCtx)) {
            try {
                Toast.makeText(appCtx, noticeText, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            return
        }

        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        dismissOverlayNoticeBanner()

        try {
            val density = appCtx.resources.displayMetrics.density
            val container = LinearLayout(appCtx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val hPad = (14 * density).toInt()
                val vPad = (9 * density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f * density
                    setColor(Color.parseColor("#EC0B1322"))
                    setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#FF00E5FF"))
                }
            }

            val titleTv = TextView(appCtx).apply {
                text = AppStrings.trText(
                    "🔒 GAMING VOLUME LOCKED AT $lockedPercent% • FORCED BACK",
                    "🔒 VOLUMEN GAMER FIJADO AL $lockedPercent% • RESTAURADO"
                )
                setTextColor(Color.parseColor("#FF00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val subtitleTv = TextView(appCtx).apply {
                text = AppStrings.trText(
                    "Leave your game & open GMax to turn off Gaming Volume Lock to change volume.",
                    "Sal del juego y abre GMax para desactivar el Bloqueo de Volumen si quieres cambiarlo."
                )
                setTextColor(Color.parseColor("#FFE2E8F0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
            }

            container.addView(titleTv)
            container.addView(subtitleTv)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val maxBannerWidthPx = (appCtx.resources.displayMetrics.widthPixels * 0.88f).toInt()
            val params = WindowManager.LayoutParams(
                maxBannerWidthPx.coerceAtMost((420 * density).toInt()),
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (52 * density).toInt()
            }

            wm.addView(container, params)
            activeWindowManager = wm
            activeBannerView = container

            val runnable = Runnable {
                dismissOverlayNoticeBanner()
            }
            bannerDismissRunnable = runnable
            mainHandler.postDelayed(runnable, 3400L)
        } catch (_: Exception) {
            try {
                Toast.makeText(appCtx, noticeText, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    fun dismissOverlayNoticeBanner() {
        bannerDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        bannerDismissRunnable = null
        val view = activeBannerView
        val wm = activeWindowManager
        activeBannerView = null
        activeWindowManager = null
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
    }

    private fun registerVolumeObservers(context: Context) {
        try {
            if (volumeReceiver == null) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (ctx == null || intent == null) return
                        if (intent.action == VOLUME_CHANGED_ACTION) {
                            val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                            if (streamType == AudioManager.STREAM_MUSIC) {
                                enforceLockedVolumeIfChanged(ctx, requireForegroundGame = true)
                            }
                        }
                    }
                }
                val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                volumeReceiver = receiver
            }
        } catch (_: Exception) {}

        try {
            if (settingsObserver == null) {
                val observer = object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        enforceLockedVolumeIfChanged(context, requireForegroundGame = true)
                    }
                }
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    observer
                )
                settingsObserver = observer
            }
        } catch (_: Exception) {}
    }
}
