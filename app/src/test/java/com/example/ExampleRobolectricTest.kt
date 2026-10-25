package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AdbCommandsRepository
import com.example.data.GameGuidanceDatabase
import com.example.data.PerformanceOptionsRepository
import com.example.util.SecureSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("G Max Gaming Hub", appName)
  }

  @Test
  fun `verify game guidance database loaded with popular titles`() {
    val games = GameGuidanceDatabase.gamesList
    assertTrue("Games list should not be empty", games.isNotEmpty())
    val genshin = games.find { it.title.contains("Genshin") }
    assertNotNull("Genshin Impact should be in guidance database", genshin)
    val pubg = games.find { it.title.contains("PUBG") }
    assertNotNull("PUBG should be in guidance database", pubg)
  }

  @Test
  fun `verify adb tweaks repository has critical commands`() {
    val tweaks = AdbCommandsRepository.tweaksList
    assertTrue("ADB tweaks should not be empty", tweaks.isNotEmpty())
    val downscaler = tweaks.find { it.id == "wm_size_downscale" }
    assertNotNull("wm size downscaler should be present", downscaler)
    val artCompile = tweaks.find { it.id == "art_compile_speed" }
    assertNotNull("ART compile tweak should be present", artCompile)
  }

  @Test
  fun `verify performance options repository has core runnable tweaks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val options = PerformanceOptionsRepository.getAllOptions(context)
    assertTrue("Options list should not be empty", options.isNotEmpty())
    assertNotNull("RAM clean option must exist", options.find { it.id == "pop_3_ram" })
    assertNotNull("DND focus option must exist", options.find { it.id == "pop_2_dnd" })
    assertNotNull("Overlay HUD option must exist", options.find { it.id == "ctrl_overlay_hud" })
    assertNotNull("Low-latency Wi-Fi option must exist", options.find { it.id == "net_low_latency" })
  }

  @Test
  fun `verify secure settings grant command is properly formatted`() {
    val cmd = SecureSettingsManager.GRANT_PERMISSION_COMMAND
    assertTrue(cmd.contains("WRITE_SECURE_SETTINGS"))
    assertTrue(cmd.contains("com.aistudio.gamebooster.kxmpzq"))
  }

  @Test
  fun `verify boost modes and themes exist and are configured`() {
    val gamingMode = com.example.model.BoostMode.GAMING
    val beastMode = com.example.model.BoostMode.BEAST
    assertEquals("Gaming Mode", gamingMode.title)
    assertEquals("Beast Mode", beastMode.title)

    val themes = com.example.model.AppBackgroundTheme.values()
    assertEquals(4, themes.size)
    assertNotNull(themes.find { it.name == "DARK_SLATE" })
    assertNotNull(themes.find { it.name == "OLED_BLACK" })
    assertNotNull(themes.find { it.name == "DEEP_NAVY" })
    assertNotNull(themes.find { it.name == "CARBON_GREY" })
  }

  @Test
  fun `verify overlay HUD config defaults`() {
    val config = com.example.model.OverlayHudConfig()
    assertTrue(config.showFps)
    assertTrue(config.showTemp)
    assertTrue(config.showBattery)
    assertTrue(config.showTimer)
  }

  @Test
  fun `verify ShizukuManager status query`() {
    val info = com.example.util.ShizukuManager.checkStatus()
    assertNotNull(info)
    assertNotNull(info.state)
    assertTrue(info.explanation.isNotBlank())
  }

  @Test
  fun `verify booster session manager optimization intervals`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = com.example.util.BoosterSessionManager(context)

    // Beast Mode should purge at an aggressive 3-minute interval (180,000 ms)
    val beastInterval = sessionManager.getPurgeIntervalMs(com.example.model.BoostMode.BEAST)
    assertEquals(3 * 60 * 1000L, beastInterval)

    // Normal Gaming Boost should purge at a 12-minute interval (720,000 ms)
    val normalInterval = sessionManager.getPurgeIntervalMs(com.example.model.BoostMode.GAMING)
    assertEquals(12 * 60 * 1000L, normalInterval)

    // Beast Mode must be significantly more aggressive (lower interval) than Normal Boost
    assertTrue("Beast mode interval must be shorter/harder than normal boost", beastInterval < normalInterval)
  }

  @Test
  fun `verify booster session lifecycle and remaining countdown`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = com.example.util.BoosterSessionManager(context)

    sessionManager.stopSession()
    assertTrue("Session should be inactive after stop", !sessionManager.isSessionActive())

    sessionManager.startSession(com.example.model.BoostMode.BEAST, "com.pubg.imobile", "BGMI / PUBG")
    assertTrue("Session should be active after start", sessionManager.isSessionActive())
    assertEquals(com.example.model.BoostMode.BEAST, sessionManager.getActiveMode())
    assertEquals("com.pubg.imobile", sessionManager.getLastTargetGame().first)

    val remainingMs = sessionManager.getRemainingMillis()
    assertTrue("Remaining time should be positive", remainingMs > 0L)
    val formatted = sessionManager.formatCountdown(remainingMs)
    assertTrue("Formatted countdown should be HH:mm:ss format", formatted.contains(":"))

    sessionManager.stopSession()
    assertTrue("Session should be inactive after second stop", !sessionManager.isSessionActive())
  }

  @Test
  fun `verify system settings helper handles battery and power saver checks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val isPowerSave = com.example.util.SystemSettingsHelper.isPowerSaveMode(context)
    // In Robolectric default state, power save mode should be queryable without crashing
    assertNotNull(isPowerSave)

    val isIgnoringOpt = com.example.util.SystemSettingsHelper.isIgnoringBatteryOptimizations(context)
    assertNotNull(isIgnoringOpt)
  }

  @Test
  fun `verify library game packages persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = com.example.util.BoosterSessionManager(context)

    sessionManager.addLibraryGamePackage("com.test.game")
    assertTrue(sessionManager.getLibraryGamePackages().contains("com.test.game"))

    sessionManager.removeLibraryGamePackage("com.test.game")
    assertTrue(!sessionManager.getLibraryGamePackages().contains("com.test.game"))
  }

  @Test
  fun `verify screen recording default and custom settings persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val defaultSettings = com.example.util.ScreenRecordingManager.loadSettings(context)

    assertNotNull(defaultSettings)
    assertTrue("Bitrate should be within 1 to 16 Mbps", defaultSettings.bitrateMbps in 1..16)
    assertTrue("FPS should be valid", defaultSettings.fps in listOf(30, 60, 90, 120))

    // Save customized setting
    val custom = com.example.model.VideoQualitySettings(
      resolutionLabel = "720p",
      width = 1280,
      height = 720,
      fps = 60,
      bitrateMbps = 12,
      recordMic = true,
      showTimerHud = true,
      isOverlayEnabled = true
    )
    com.example.util.ScreenRecordingManager.saveSettings(context, custom)

    val loaded = com.example.util.ScreenRecordingManager.loadSettings(context)
    assertEquals("720p", loaded.resolutionLabel)
    assertEquals(1280, loaded.width)
    assertEquals(720, loaded.height)
    assertEquals(60, loaded.fps)
    assertEquals(12, loaded.bitrateMbps)
    assertTrue(loaded.recordMic)
    assertTrue(loaded.showTimerHud)
    assertTrue(loaded.isOverlayEnabled)
  }

  @Test
  fun `verify screen recording auto-configure ideal settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ideal = com.example.util.ScreenRecordingManager.getAutoConfiguredIdealSettings(context)

    assertNotNull(ideal)
    assertTrue("Ideal bitrate should be within 1 to 16 Mbps", ideal.bitrateMbps in 1..16)
    assertTrue("Ideal FPS should be at least 30", ideal.fps >= 30)
    assertTrue(ideal.resolutionLabel.isNotBlank())
  }

  @Test
  fun `verify screen recording supported resolutions and fps querying`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val supportedRes = com.example.util.ScreenRecordingManager.getSupportedResolutions(context)
    assertTrue("Supported resolutions should not be empty", supportedRes.isNotEmpty())

    val supportedFps = com.example.util.ScreenRecordingManager.getSupportedFps(context)
    assertTrue("Supported FPS should at least include 30 and 60", supportedFps.contains(30) && supportedFps.contains(60))
  }

  @Test
  fun `verify screen recording overlay state toggle`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.util.ScreenRecordingManager.setOverlayEnabled(context, true)
    assertTrue(com.example.util.ScreenRecordingManager.loadSettings(context).isOverlayEnabled)

    com.example.util.ScreenRecordingManager.setOverlayEnabled(context, false)
    assertTrue(!com.example.util.ScreenRecordingManager.loadSettings(context).isOverlayEnabled)
  }
}


