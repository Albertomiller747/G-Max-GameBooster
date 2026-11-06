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

    val themes = com.example.model.AppBackgroundTheme.entries
    assertEquals(4, themes.size)
    assertNotNull(themes.find { it.name == "AUTO_SYSTEM" })
    assertNotNull(themes.find { it.name == "LIGHT_HUB" })
    assertNotNull(themes.find { it.name == "DARK_HUB" })
    assertNotNull(themes.find { it.name == "CUSTOM_COLOR" })
  }

  @Test
  fun `verify overlay HUD config defaults`() {
    val config = com.example.model.OverlayHudConfig()
    assertTrue(config.showFps)
    assertTrue(config.showTemp)
    assertTrue(config.showBattery)
    assertTrue(config.showTimer)
    assertTrue(config.showRamPct)
    assertTrue(config.showClock)
  }

  @Test
  fun `verify EfpsEngine multi-sensor initialization and non-root explanation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.util.EfpsEngine.init(context)
    com.example.util.EfpsEngine.setTargetCapMode(context, "60")
    val state = com.example.util.EfpsEngine.telemetry.value
    assertNotNull(state)
    assertTrue(state.confidencePercent >= 96)
    assertTrue(state.onePercentLowEfps in 1..state.currentEfps)
    assertEquals("60", state.targetCapMode)
    assertTrue(com.example.util.EfpsEngine.NON_ROOT_EXPLANATION_BODY.isNotBlank())
    com.example.util.EfpsEngine.setTargetCapMode(context, "AUTO")
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

  @Test
  fun `verify 45 premade profile tags and exclusive Albertomiller747 developer verification`() {
    val allTags = com.example.util.PremadeTagsCatalog.ALL_TAGS
    assertTrue("Must have 40+ premade tags", allTags.size >= 45)

    // Verify 4-tag limit and rejection of fake developer tag IDs
    val sanitized = com.example.util.PremadeTagsCatalog.sanitizeSelectedTags(
      listOf(
        "developer",
        "fps_veteran",
        "120hz_enthusiast",
        "esports_ready",
        "night_owl",
        "sniper_main"
      )
    )
    assertEquals(4, sanitized.size)
    assertTrue(!sanitized.contains("developer"))
    assertEquals(listOf("fps_veteran", "120hz_enthusiast", "esports_ready", "night_owl"), sanitized)

    // Verify exclusive Developer Gold-Star email check
    assertTrue(com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail("Albertomiller747@gmail.com"))
    assertTrue(com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail("  albertomiller747@gmail.com "))
    assertTrue(!com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail("randomfaker@gmail.com"))
    assertTrue(!com.example.util.PremadeTagsCatalog.isOfficialDeveloperEmail("Albertomiller747@fake.com"))

    // Verify display name anti-spoofing for non-developers
    val strippedName = com.example.util.PremadeTagsCatalog.sanitizeDisplayName("Random User ★ Verified Developer", false)
    assertTrue(!strippedName.contains("★"))
    assertTrue(!strippedName.lowercase().contains("verified developer"))
  }

  @Test
  fun `verify country catalog flags and username validation rules`() {
    val us = com.example.util.CountryCatalog.getCountryByCode("US")
    assertNotNull(us)
    assertEquals("United States", us?.name)
    assertEquals("🇺🇸", com.example.util.CountryCatalog.codeToFlagEmoji("US"))
    assertEquals("🇯🇵", com.example.util.CountryCatalog.codeToFlagEmoji("JP"))

    val searchResults = com.example.util.CountryCatalog.searchCountries("Japan")
    assertTrue(searchResults.any { it.code == "JP" })

    // Valid usernames
    assertTrue(com.example.util.PremadeTagsCatalog.validateUsername("ProGamer_99", false).isSuccess)
    assertTrue(com.example.util.PremadeTagsCatalog.validateUsername("Albertomiller747", true).isSuccess)

    // Invalid usernames (too short, invalid symbols, or spoofed developer handle)
    assertTrue(com.example.util.PremadeTagsCatalog.validateUsername("ab", false).isFailure)
    assertTrue(com.example.util.PremadeTagsCatalog.validateUsername("bad space", false).isFailure)
    assertTrue(com.example.util.PremadeTagsCatalog.validateUsername("Albertomiller747", false).isFailure)
  }

  @Test
  fun `verify v1_0 launch update controller home label and admob rewarded unlock`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("1.0", BuildConfig.VERSION_NAME)
    assertEquals("Home", com.example.model.ControllerLayoutMode.PLAYSTATION.shortBadge)
    assertEquals("Home", com.example.model.ControllerLayoutResolver.playStationGlyphs.homeGlyph)
    assertEquals("Home", com.example.model.ControllerLayoutResolver.playStationGlyphs.homePromptName)

    com.example.util.AdMobManager.initializeAndPreload(context)
    assertEquals("ca-app-pub-4132895970409418~6845244323", com.example.util.AdMobManager.ADMOB_APP_ID)
    assertTrue(com.example.util.AdMobManager.rewardedAdUnitId.isNotBlank())
    assertTrue(com.example.util.AdMobManager.nativeAdvancedAdUnitId.isNotBlank())
    assertTrue(com.example.util.AdMobManager.bannerAdUnitId.isNotBlank())
    assertTrue(com.example.util.AdMobManager.appOpenAdUnitId.isNotBlank())

    com.example.util.AdMobManager.showRewardedAdForProController(context)
    val activeAd = com.example.util.AdMobManager.activeFullScreenAd.value as? com.example.util.AdMobManager.FullScreenAdPresentation.Rewarded
    assertNotNull(activeAd)
    activeAd?.onRewardEarned?.invoke()
    assertTrue(com.example.util.AdMobManager.isProControllerUnlocked())
    com.example.util.AdMobManager.dismissActiveFullScreenAd()
  }

  @Test
  fun `verify real time controller pass through updates shared overlay state without blocking game`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val passThroughManager = com.example.util.ControllerHealthManager(context)
    val overlayObserverManager = com.example.util.ControllerHealthManager(context)

    // Press L2 trigger button
    val l2Down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_L2)
    assertTrue(passThroughManager.onKeyEvent(l2Down))
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("L2"))
    assertEquals(1f, com.example.util.ControllerHealthManager.liveInputState.value.triggers.leftTrigger, 0.01f)
    assertEquals(1f, overlayObserverManager.state.value.triggers.leftTrigger, 0.01f)

    // Release L2 trigger button
    val l2Up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_L2)
    assertTrue(passThroughManager.onKeyEvent(l2Up))
    assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("L2"))
    assertEquals(0f, com.example.util.ControllerHealthManager.liveInputState.value.triggers.leftTrigger, 0.01f)
  }

  @Test
  fun `verify pre-launch update dpad stick deflection offline 720p lock bubble persistence and console sound toggle`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controllerMgr = com.example.util.ControllerHealthManager(context)

    // 1. Verify D-Pad key presses highlight D-Pad and deflect left stick for real-time overlay movement
    val dpadUpDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP)
    assertTrue(controllerMgr.onKeyEvent(dpadUpDown))
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_UP"))
    assertEquals(-1f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.y, 0.01f)

    val dpadUpRelease = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP)
    assertTrue(controllerMgr.onKeyEvent(dpadUpRelease))
    assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_UP"))

    // 2. Verify offline recording cap locks effective settings to 720p, 60 FPS, 8 Mbps
    val highSpec = com.example.model.VideoQualitySettings(
      resolutionLabel = "1080p",
      width = 1920,
      height = 1080,
      fps = 120,
      bitrateMbps = 16
    )
    com.example.util.ScreenRecordingManager.saveSettings(context, highSpec)
    val effectiveOffline = com.example.util.ScreenRecordingManager.getEffectiveRecordingSettings(context)
    assertEquals("720p", effectiveOffline.resolutionLabel)
    assertEquals(1280, effectiveOffline.width)
    assertEquals(720, effectiveOffline.height)
    assertEquals(60, effectiveOffline.fps)
    assertEquals(8, effectiveOffline.bitrateMbps)

    // 3. Verify recording bubble position persistence
    com.example.util.ScreenRecordingManager.saveBubblePosition(context, 142, 388)
    val savedBubblePos = com.example.util.ScreenRecordingManager.loadBubblePosition(context)
    assertNotNull(savedBubblePos)
    assertEquals(142, savedBubblePos?.first)
    assertEquals(388, savedBubblePos?.second)

    // 4. Verify Console Mode UI Sound toggle persistence
    assertTrue(com.example.util.ConsoleUiSoundManager.isConsoleUiSoundsEnabled(context))
    com.example.util.ConsoleUiSoundManager.setConsoleUiSoundsEnabled(context, false)
    assertTrue(!com.example.util.ConsoleUiSoundManager.isConsoleUiSoundsEnabled(context))
    com.example.util.ConsoleUiSoundManager.setConsoleUiSoundsEnabled(context, true)
    assertTrue(com.example.util.ConsoleUiSoundManager.isConsoleUiSoundsEnabled(context))

    // 5. Verify raw HID scanCode D-Pad events (e.g., BTN_DPAD_UP = 544 with KEYCODE_UNKNOWN)
    val rawDpadEvent = android.view.KeyEvent(
      0L,
      0L,
      android.view.KeyEvent.ACTION_DOWN,
      android.view.KeyEvent.KEYCODE_UNKNOWN,
      0,
      0,
      0,
      544
    )
    assertTrue(controllerMgr.onKeyEvent(rawDpadEvent))
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_UP"))
    assertEquals(-1f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.y, 0.01f)

    val rawDpadUpRelease = android.view.KeyEvent(
      0L,
      0L,
      android.view.KeyEvent.ACTION_UP,
      android.view.KeyEvent.KEYCODE_UNKNOWN,
      0,
      0,
      0,
      544
    )
    assertTrue(controllerMgr.onKeyEvent(rawDpadUpRelease))
    assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_UP"))

    // 6. Verify non-gamepad system keys (e.g. Volume Up) never trigger phantom overlay buttons
    val volUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)
    assertTrue(!controllerMgr.onKeyEvent(volUp))
    assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("VOLUME_UP"))

    // 7. Verify pressing face button A in-game does NOT fabricate random stick deflection
    com.example.util.ControllerHealthManager.setAppInForeground(false)
    val btnADown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A)
    assertTrue(controllerMgr.onKeyEvent(btnADown))
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("A"))
    assertEquals(0f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.x, 0.01f)
    assertEquals(0f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.y, 0.01f)
    val btnAUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_A)
    assertTrue(controllerMgr.onKeyEvent(btnAUp))
    com.example.util.ControllerHealthManager.setAppInForeground(true)
  }

  @Test
  fun `verify top gear curated catalog bundled json tier grouping active filtering and anonymous analytics`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.TopGearRepository(context)
    val bundled = repo.loadBundledProducts()

    // Must contain all 15 user-provided Amazon affiliate products with official Amazon media image URLs
    assertEquals(15, bundled.size)
    assertTrue(bundled.all { it.affiliateUrl.startsWith("https://link.amazon/") })
    assertTrue(bundled.all { it.imageUrl.startsWith("https://m.media-amazon.com/images/I/") })
    assertTrue(bundled.all { it.priceLevel in listOf("$", "$$", "$$$") })

    val catalog = repo.buildCatalogState(
      rawProducts = bundled,
      selectedCategory = "All",
      syncSource = com.example.data.repository.TopGearSyncSource.BUNDLED,
      isOffline = false
    )

    // Power has 0 active products, so it must be hidden from category chips
    assertEquals(
      listOf("All", "Cooling", "Controllers", "Finger Sleeves", "Audio", "Accessories"),
      catalog.availableCategories
    )

    // Verify 3-tier group (Phone Coolers: Budget -> Best Value -> Premium)
    val phoneCoolers = catalog.groups.find { it.groupTitle == "Phone Coolers" }
    assertNotNull(phoneCoolers)
    assertEquals(3, phoneCoolers?.products?.size)
    assertEquals("budget", phoneCoolers?.products?.get(0)?.tier)
    assertEquals("value", phoneCoolers?.products?.get(1)?.tier)
    assertEquals("Best Pick", phoneCoolers?.products?.get(1)?.badge)
    assertEquals("premium", phoneCoolers?.products?.get(2)?.tier)

    // Verify 2-tier group (Desk & Streaming Accessories)
    val deskAcc = catalog.groups.find { it.groupTitle == "Desk & Streaming Accessories" }
    assertNotNull(deskAcc)
    assertEquals(2, deskAcc?.products?.size)

    // Verify 1-product Editor's Pick group (Touchscreen Finger Sleeves)
    val fingerSleeves = catalog.groups.find { it.groupTitle == "Touchscreen Finger Sleeves" }
    assertNotNull(fingerSleeves)
    assertEquals(1, fingerSleeves?.products?.size)
    assertEquals("Editor's Pick", fingerSleeves?.products?.first()?.badge)

    // Verify inactive products are hidden
    val withInactive = bundled.map { if (it.category == "Audio") it.copy(active = false) else it }
    val filteredCatalog = repo.buildCatalogState(
      rawProducts = withInactive,
      selectedCategory = "All",
      syncSource = com.example.data.repository.TopGearSyncSource.BUNDLED,
      isOffline = false
    )
    assertTrue(!filteredCatalog.availableCategories.contains("Audio"))

    // Verify anonymous tap analytics opt-out toggle
    com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(context, true)
    assertTrue(com.example.data.repository.TopGearAnalyticsManager.isAnalyticsEnabled(context))
    com.example.data.repository.TopGearAnalyticsManager.logAnonymousTap(context, "cooler_neveika_core", "value")
    assertTrue((com.example.data.repository.TopGearAnalyticsManager.getAnonymousTierTapSummary(context)["value"] ?: 0) >= 1)

    com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(context, false)
    assertTrue(!com.example.data.repository.TopGearAnalyticsManager.isAnalyticsEnabled(context))
    com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(context, true)
  }

  @Test
  fun `verify real time dpad and analog stick hold does not decay and controller style switches`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controllerMgr = com.example.util.ControllerHealthManager(context)

    // Hold D-Pad Right and step in-game stick animation multiple times: must stay at +1f and not decay
    val dpadRightDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
    assertTrue(controllerMgr.onKeyEvent(dpadRightDown))
    repeat(10) {
      controllerMgr.stepInGameStickAnimation()
    }
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_RIGHT"))
    assertEquals(1f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.x, 0.01f)

    // Release D-Pad Right: must return cleanly to 0f
    val dpadRightUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
    assertTrue(controllerMgr.onKeyEvent(dpadRightUp))
    assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_RIGHT"))
    assertEquals(0f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.x, 0.01f)

    // Verify controller style switching resolves distinct layouts (PlayStation, Xbox, Nintendo Switch)
    val psGlyphs = com.example.model.ControllerLayoutResolver.getGlyphMap(
      mode = com.example.model.ControllerLayoutMode.PLAYSTATION,
      gamepads = emptyList()
    )
    val xboxGlyphs = com.example.model.ControllerLayoutResolver.getGlyphMap(
      mode = com.example.model.ControllerLayoutMode.XBOX,
      gamepads = emptyList()
    )
    val switchGlyphs = com.example.model.ControllerLayoutResolver.getGlyphMap(
      mode = com.example.model.ControllerLayoutMode.NINTENDO,
      gamepads = emptyList()
    )
    assertEquals(com.example.model.ResolvedControllerBrand.PLAYSTATION, psGlyphs.brand)
    assertEquals("✕", psGlyphs.southGlyph)
    assertEquals(com.example.model.ResolvedControllerBrand.XBOX, xboxGlyphs.brand)
    assertEquals("A", xboxGlyphs.southGlyph)
    assertEquals(com.example.model.ResolvedControllerBrand.NINTENDO, switchGlyphs.brand)
    assertEquals("B", switchGlyphs.brand.let { switchGlyphs.southGlyph })
  }

  @Test
  fun `verify out of app stick tracking mode persistence and foreground package classification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controllerMgr = com.example.util.ControllerHealthManager(context)

    // 1. Verify StickTrackingMode persistence in ControllerOverlayService
    val fullLockConfig = com.example.service.ControllerOverlayService.loadConfig(context).copy(
      stickTrackingMode = com.example.model.StickTrackingMode.FULL_120HZ_LOCK
    )
    com.example.service.ControllerOverlayService.saveConfig(context, fullLockConfig)
    assertEquals(
      com.example.model.StickTrackingMode.FULL_120HZ_LOCK,
      com.example.service.ControllerOverlayService.loadConfig(context).stickTrackingMode
    )

    val smartHybridConfig = fullLockConfig.copy(
      stickTrackingMode = com.example.model.StickTrackingMode.SMART_HYBRID
    )
    com.example.service.ControllerOverlayService.saveConfig(context, smartHybridConfig)
    assertEquals(
      com.example.model.StickTrackingMode.SMART_HYBRID,
      com.example.service.ControllerOverlayService.loadConfig(context).stickTrackingMode
    )

    // 2. Verify foreground package classification (GMAX App vs Home Screen Launcher vs External Game)
    com.example.util.ControllerHealthManager.updateForegroundPackage(context, context.packageName)
    assertTrue(com.example.util.ControllerHealthManager.isAppInForeground)
    assertTrue(!com.example.util.ControllerHealthManager.isLauncherOrSystemInForeground)
    assertTrue(!com.example.util.ControllerHealthManager.isGameInForeground)

    com.example.util.ControllerHealthManager.updateForegroundPackage(context, "com.google.android.apps.nexuslauncher")
    assertTrue(!com.example.util.ControllerHealthManager.isAppInForeground)
    assertTrue(com.example.util.ControllerHealthManager.isLauncherOrSystemInForeground)
    assertTrue(!com.example.util.ControllerHealthManager.isGameInForeground)

    com.example.util.ControllerHealthManager.updateForegroundPackage(context, "com.activision.callofduty.shooter")
    assertTrue(!com.example.util.ControllerHealthManager.isAppInForeground)
    assertTrue(!com.example.util.ControllerHealthManager.isLauncherOrSystemInForeground)
    assertTrue(com.example.util.ControllerHealthManager.isGameInForeground)

    // 3. Verify 2D focus vector updates D-Pad and Left Stick when not touched, and switching packages clears stuck inputs
    controllerMgr.onAccessibilityDirectionalPulse(dx = -1f, dy = 0f, triggerDpad = true)
    assertEquals(-1f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.x, 0.01f)
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_LEFT"))

    com.example.util.ControllerHealthManager.updateForegroundPackage(context, context.packageName)
    assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.isEmpty())
    assertEquals(0f, com.example.util.ControllerHealthManager.liveInputState.value.leftStick.x, 0.01f)
  }

  @Test
  fun `verify controller overlay requires wired USB connection and triggers warning popup when not wired`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    try {
      // 1. Simulate wireless / disconnected state (no wired USB controller attached)
      com.example.util.ControllerHealthManager.simulatedWiredOverrideForTests = false
      assertTrue(!com.example.util.ControllerHealthManager.isWiredControllerConnected(app))

      val vm = com.example.viewmodel.GameBoosterViewModel(app)
      vm.dismissWiredControllerRequiredPopup()
      assertTrue(!vm.showWiredControllerRequiredPopup.value)

      // Attempting to enable Controller Overlay when not wired must block activation and show the warning popup
      vm.setControllerOverlayEnabled(true)
      assertTrue("Overlay must remain disabled when not wired via USB", !vm.controllerOverlayConfig.value.isEnabled)
      assertTrue("Warning popup must be triggered when attempting to enable without wired USB", vm.showWiredControllerRequiredPopup.value)

      // Dismiss popup
      vm.dismissWiredControllerRequiredPopup()
      assertTrue(!vm.showWiredControllerRequiredPopup.value)

      // 2. Simulate wired USB controller connected
      com.example.util.ControllerHealthManager.simulatedWiredOverrideForTests = true
      assertTrue(com.example.util.ControllerHealthManager.isWiredControllerConnected(app))

      vm.setControllerOverlayEnabled(true)
      assertTrue("Overlay must enable when wired USB controller is connected", vm.controllerOverlayConfig.value.isEnabled)
      assertTrue("Warning popup must not show when wired USB controller is connected", !vm.showWiredControllerRequiredPopup.value)

      // 3. Simulate unplugging wired USB controller: disableIfNotWired must turn off the overlay
      com.example.util.ControllerHealthManager.simulatedWiredOverrideForTests = false
      com.example.service.ControllerOverlayService.disableIfNotWired(app)
      assertTrue("Overlay config must auto-disable when wired USB controller is unplugged", !com.example.service.ControllerOverlayService.loadConfig(app).isEnabled)

      // 4. Verify Sony USB-C HID button & scanCode mappings (BUTTON_C / 306 = B, BUTTON_Z / 309 = R1, BUTTON_13 / 316 = MODE)
      val mgr = com.example.util.ControllerHealthManager(app)
      val btnCDown = android.view.KeyEvent(0L, 0L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_C, 0, 0, -1, 306)
      assertTrue(mgr.onKeyEvent(btnCDown))
      assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("B"))
      val btnCUp = android.view.KeyEvent(0L, 10L, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_C, 0, 0, -1, 306)
      mgr.onKeyEvent(btnCUp)
      assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("B"))

      val btnZDown = android.view.KeyEvent(0L, 20L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_Z, 0, 0, -1, 309)
      assertTrue(mgr.onKeyEvent(btnZDown))
      assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("R1"))
      val btnZUp = android.view.KeyEvent(0L, 30L, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_Z, 0, 0, -1, 309)
      mgr.onKeyEvent(btnZUp)
      assertTrue(!com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("R1"))

      // 5. Verify leaving MainActivity defaults isLauncherOrSystemInForeground to true so out-of-app 120Hz overlay focus engages immediately
      com.example.util.ControllerHealthManager.setAppInForeground(true)
      com.example.util.ControllerHealthManager.setAppInForeground(false)
      assertTrue(!com.example.util.ControllerHealthManager.isAppInForeground)
      assertTrue(com.example.util.ControllerHealthManager.isLauncherOrSystemInForeground)

      // 6. Verify pressing Square ("X") on a third-party PS-mode controller with simultaneous companion Triangle ("Y" / scanCode 308)
      // lights up ONLY Square ("X") and never Triangle ("Y")
      com.example.util.ControllerHealthManager.clearTransientHeldInputs()
      val squareDown = android.view.KeyEvent(100L, 100L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_X, 0, 0, -1, 308)
      val companionTriangleDown = android.view.KeyEvent(100L, 102L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_Y, 0, 0, -1, 308)
      mgr.onKeyEvent(squareDown)
      mgr.onKeyEvent(companionTriangleDown)
      val activeAfterSquare = com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons
      assertTrue("Square ('X') must be active", activeAfterSquare.contains("X"))
      assertTrue("Triangle ('Y') must NOT light up when pressing Square", !activeAfterSquare.contains("Y"))

      val squareUp = android.view.KeyEvent(100L, 150L, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_X, 0, 0, -1, 308)
      mgr.onKeyEvent(squareUp)
      assertTrue(com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.isEmpty())

      // 7. Verify moving the analog joystick does NOT light up D-Pad from synthetic ViewRootImpl fallback DPAD events
      mgr.setSimulatedSticks(0.82f, -0.35f, 0f, 0f)
      val fallbackDpadRight = android.view.KeyEvent(
        200L,
        200L,
        android.view.KeyEvent.ACTION_DOWN,
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        0,
        0,
        -1,
        0,
        android.view.KeyEvent.FLAG_FALLBACK
      )
      assertTrue(!mgr.onKeyEvent(fallbackDpadRight))
      assertTrue(
        "D-Pad must not light up when only moving the joystick",
        !com.example.util.ControllerHealthManager.liveInputState.value.pressedButtons.contains("DPAD_RIGHT")
      )
    } finally {
      com.example.util.ControllerHealthManager.simulatedWiredOverrideForTests = null
    }
  }

  @Test
  fun `verify multi language support for pt-BR es-MX id vi tl and en`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    try {
      // 1. Auto-detection of locales
      assertEquals(
        com.example.util.AppLanguage.PT_BR,
        com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale("pt", "BR"))
      )
      assertEquals(
        com.example.util.AppLanguage.ES_MX,
        com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale("es", "MX"))
      )
      assertEquals(
        com.example.util.AppLanguage.ID,
        com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale("id", "ID"))
      )
      assertEquals(
        com.example.util.AppLanguage.VI,
        com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale("vi", "VN"))
      )
      assertEquals(
        com.example.util.AppLanguage.TL,
        com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale("tl", "PH"))
      )

      // 2. Manual selection & translation lookup for each language
      com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.PT_BR)
      assertEquals("Iniciador", com.example.util.AppStrings.trText("Launcher", "Lanzador"))

      com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.ES_MX)
      assertEquals("Lanzador", com.example.util.AppStrings.trText("Launcher", "Lanzador"))

      com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.ID)
      assertEquals("Peluncur", com.example.util.AppStrings.trText("Launcher", "Lanzador"))

      com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.VI)
      assertEquals("Trình khởi chạy", com.example.util.AppStrings.trText("Launcher", "Lanzador"))

      com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.TL)
      assertEquals("Magdagdag ng Laro", com.example.util.AppStrings.trText("Add Games", "Agregar Juegos"))
    } finally {
      com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.AUTO)
    }
  }

  @Test
  fun `verify smart push notifications 6 triggers send time modeling and 4 per week frequency cap`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    com.example.util.SmartNotificationManager.resetQuotaAndTelemetryForTesting(context)
    com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.EN)

    val baseTimeMs = 1_750_000_000_000L

    // 1. Verify exact message templates for all 6 notification triggers
    assertEquals(
      "Forgetting the Time after a long gaming session? Turn on your clock overlay to keep up with the time",
      com.example.util.SmartNotificationManager.getTimerTriggerMessage(com.example.util.AppLanguage.EN)
    )
    assertEquals(
      "Don't miss a clutch moment. Enable the up to 4K 120fps screen recorder before you launch.",
      com.example.util.SmartNotificationManager.getContentCreatorNudgeMessage(com.example.util.AppLanguage.EN)
    )
    assertEquals(
      "Time to play? Launch through GMAX to clear background RAM and lock in your frame rate.",
      com.example.util.SmartNotificationManager.getSendTimeModelingMessage(com.example.util.AppLanguage.EN)
    )
    assertEquals(
      "Session complete! 1.2GB of RAM cleared and temperatures kept stable.",
      com.example.util.SmartNotificationManager.getPostSessionWrapUpMessage(1.2, com.example.util.AppLanguage.EN)
    )
    assertEquals(
      "Your games are missing the boost. Jump back in for a lag free session.",
      com.example.util.SmartNotificationManager.getDay3LapsedMessage(com.example.util.AppLanguage.EN)
    )
    assertEquals(
      "Gameplay stuttering lately? Run a quick diagnostic to optimize your device right now.",
      com.example.util.SmartNotificationManager.getDay7LapsedMessage(com.example.util.AppLanguage.EN)
    )
    assertEquals(
      "You gamed for 14 hours this week! Tap to see how much RAM was optimized and your increased gamer score.",
      com.example.util.SmartNotificationManager.getWeeklySummaryMessage(14, com.example.util.AppLanguage.EN)
    )

    // 2. Verify Send Time Modeling: 8:00 PM (hour 20) peak -> 7:55 PM (19:55) prompt
    com.example.util.SmartNotificationManager.recordGamingHourActivity(context, hourOfDay24 = 20, nowMs = baseTimeMs)
    com.example.util.SmartNotificationManager.recordGamingHourActivity(context, hourOfDay24 = 20, nowMs = baseTimeMs + 1000L)
    com.example.util.SmartNotificationManager.recordGamingHourActivity(context, hourOfDay24 = 15, nowMs = baseTimeMs + 2000L)
    val modeled = com.example.util.SmartNotificationManager.computeOptimalSendTime(context)
    assertEquals(20, modeled.peakGamingHour24)
    assertEquals(19, modeled.sendHour24)
    assertEquals(55, modeled.sendMinute)
    assertEquals("7:55 PM", modeled.formattedSendTime)

    // 3. Verify 4x/week frequency cap across triggers
    // Notification #1: Timer Trigger after 3 overlay uses without clock
    assertTrue(com.example.util.SmartNotificationManager.onOverlayUsed(context, showClockEnabled = false, nowMs = baseTimeMs + 10_000L) == null)
    assertTrue(com.example.util.SmartNotificationManager.onOverlayUsed(context, showClockEnabled = false, nowMs = baseTimeMs + 20_000L) == null)
    val n1 = com.example.util.SmartNotificationManager.onOverlayUsed(context, showClockEnabled = false, nowMs = baseTimeMs + 30_000L)
    assertTrue(n1 != null)
    assertEquals(1, com.example.util.SmartNotificationManager.getWeeklySentCount(context, baseTimeMs + 30_000L))

    // Notification #2: Content Creator Nudge after 3 booster starts with 0 recorded videos
    assertTrue(com.example.util.SmartNotificationManager.onBoosterSessionStarted(context, freedRamMb = 1228L, hasRecordedVideos = false, nowMs = baseTimeMs + 40_000L) == null)
    assertTrue(com.example.util.SmartNotificationManager.onBoosterSessionStarted(context, freedRamMb = 1228L, hasRecordedVideos = false, nowMs = baseTimeMs + 50_000L) == null)
    val n2 = com.example.util.SmartNotificationManager.onBoosterSessionStarted(context, freedRamMb = 1228L, hasRecordedVideos = false, nowMs = baseTimeMs + 60_000L)
    assertTrue(n2 != null)
    assertEquals(2, com.example.util.SmartNotificationManager.getWeeklySentCount(context, baseTimeMs + 60_000L))

    // Notification #3: Post-Session Wrap-Up
    val n3 = com.example.util.SmartNotificationManager.triggerPostSessionWrapUp(context, clearedRamGbOverride = 1.2, nowMs = baseTimeMs + 70_000L)
    assertTrue(n3 != null)
    assertEquals(3, com.example.util.SmartNotificationManager.getWeeklySentCount(context, baseTimeMs + 70_000L))

    // Notification #4: Weekly Milestone Summary
    val n4 = com.example.util.SmartNotificationManager.triggerWeeklyMilestoneSummary(context, weeklyHoursOverride = 14, nowMs = baseTimeMs + 80_000L)
    assertTrue(n4 != null)
    assertEquals(4, com.example.util.SmartNotificationManager.getWeeklySentCount(context, baseTimeMs + 80_000L))
    assertEquals(0, com.example.util.SmartNotificationManager.getRemainingWeeklyQuota(context, baseTimeMs + 80_000L))

    // Notification #5 within the same 7-day window MUST be blocked by the 4x/week frequency cap!
    val n5Blocked = com.example.util.SmartNotificationManager.triggerSendTimeModelingPrompt(context, nowMs = baseTimeMs + 90_000L)
    assertTrue("5th notification in the same week must be blocked by 4x/week cap", n5Blocked == null)

    // After 7 days + 1 minute, the rolling window frees up and notifications are allowed again
    val nextWeekMs = baseTimeMs + com.example.util.SmartNotificationManager.SEVEN_DAYS_MS + 100_000L
    val n6AllowedNextWeek = com.example.util.SmartNotificationManager.triggerSendTimeModelingPrompt(context, nowMs = nextWeekMs)
    assertTrue("Notification must be allowed again in the following week", n6AllowedNextWeek != null)

    com.example.util.AppStrings.setLanguageMode(context, com.example.util.AppLanguage.AUTO)
  }

  @Test
  fun `verify feedback loops background battery idle pause and memory cache auto trim`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    // 1. Verify ActionFeedbackController immediate feedback loop registration & dismissal
    com.example.ui.components.ActionFeedbackController.dismiss()
    assertTrue(com.example.ui.components.ActionFeedbackController.currentEvent.value == null)
    com.example.ui.components.ActionFeedbackController.emitFeedback(
      context = app,
      message = "Game Boost Activated • 420 MB Freed",
      accentColorArgb = 0xFF00E676L,
      isProcessStart = true
    )
    val emitted = com.example.ui.components.ActionFeedbackController.currentEvent.value
    assertNotNull("Feedback event must be immediately published", emitted)
    assertEquals("Game Boost Activated • 420 MB Freed", emitted?.message)
    assertEquals(true, emitted?.isProcessStart)
    com.example.ui.components.ActionFeedbackController.dismiss()
    assertTrue(com.example.ui.components.ActionFeedbackController.currentEvent.value == null)

    // 2. Verify EfpsEngine background battery conservation: pauseForIdle() stops Choreographer & hardware polling, resumeFromIdle() restores it
    com.example.util.EfpsEngine.start(app)
    assertTrue("EfpsEngine must be running after start", com.example.util.EfpsEngine.isEngineRunning())
    assertTrue(!com.example.util.EfpsEngine.isIdlePaused())

    com.example.util.EfpsEngine.pauseForIdle()
    assertTrue("EfpsEngine must stop frame callbacks when paused for idle", !com.example.util.EfpsEngine.isEngineRunning())
    assertTrue("EfpsEngine must mark isIdlePaused = true", com.example.util.EfpsEngine.isIdlePaused())

    com.example.util.EfpsEngine.resumeFromIdle(app)
    assertTrue("EfpsEngine must resume when returning from idle", com.example.util.EfpsEngine.isEngineRunning())
    assertTrue(!com.example.util.EfpsEngine.isIdlePaused())

    // 3. Verify AppMemoryManager cache cleanup and RAM trim
    val dummyCacheFile = java.io.File(app.cacheDir, "temp_gmax_test_cache.bin")
    dummyCacheFile.writeBytes(ByteArray(4096) { 0x5A })
    assertTrue(dummyCacheFile.exists())

    val trimResult = kotlinx.coroutines.runBlocking {
      com.example.util.AppMemoryManager.performAutoTrim(
        context = app,
        reason = "Unit Test Trim",
        cleanBackgroundProcesses = true,
        force = true
      )
    }
    assertTrue("Dummy cache file must be purged by performAutoTrim", !dummyCacheFile.exists())
    assertTrue("Cleared cache bytes must be >= 4096", trimResult.cacheClearedBytes >= 4096L)
    assertEquals("Unit Test Trim", com.example.util.AppMemoryManager.lastTrimResult.value?.reason)

    // 4. Verify GameBoosterViewModel auto-pauses telemetry when app goes to background with no active boost or overlay
    val vm = com.example.viewmodel.GameBoosterViewModel(app)
    vm.onAppPausedOrStopped()
    assertTrue(
      "Background telemetry must be marked paused when idle with no active boost or overlay",
      com.example.util.AppMemoryManager.isBackgroundTelemetryPaused.value
    )
    vm.onAppResumed()
    assertTrue(
      "Background telemetry must resume when app returns to foreground",
      !com.example.util.AppMemoryManager.isBackgroundTelemetryPaused.value
    )
  }

  @Test
  fun `verify app review popup and push notifications after boost 30m recording stop and permanent verification suppression`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    com.example.util.AppReviewManager.resetForTesting(app)
    com.example.util.SmartNotificationManager.resetQuotaAndTelemetryForTesting(app)

    val baseTimeMs = 1_800_000_000_000L

    // 1. Verify accumulating < 30 minutes (20 minutes) of active boost usage does NOT trigger the popup yet
    com.example.util.AppReviewManager.onNewBoostSessionStarted(app)
    val triggeredAt20m = com.example.util.AppReviewManager.recordBoostUsageMs(
      context = app,
      deltaMs = 20L * 60L * 1000L,
      nowMs = baseTimeMs
    )
    assertTrue(!triggeredAt20m)
    assertTrue(!com.example.util.AppReviewManager.showReviewPopup.value)

    // 2. Reaching 30 minutes of active boost usage MUST trigger the in-app popup and review notification
    val triggeredAt30m = com.example.util.AppReviewManager.recordBoostUsageMs(
      context = app,
      deltaMs = 10L * 60L * 1000L,
      nowMs = baseTimeMs + 1000L
    )
    assertTrue("30 minutes of active boost usage must trigger the review flow", triggeredAt30m)
    assertTrue("In-app review popup must be shown", com.example.util.AppReviewManager.showReviewPopup.value)
    assertEquals(
      com.example.util.ReviewTriggerSource.BOOST_30_MIN_MILESTONE,
      com.example.util.AppReviewManager.activeTriggerSource.value
    )

    // 3. Dismissing the popup enforces a 3-day cooldown
    com.example.util.AppReviewManager.dismissReviewPromptForCooldown(app, nowMs = baseTimeMs + 2000L)
    assertTrue(!com.example.util.AppReviewManager.showReviewPopup.value)
    assertTrue(!com.example.util.AppReviewManager.isEligibleToShowReview(app, nowMs = baseTimeMs + 3600_000L))
    assertTrue("Boost end during 3-day cooldown must not show popup", !com.example.util.AppReviewManager.onBoostSessionEnded(app, nowMs = baseTimeMs + 3600_000L))

    // 4. After 3 days + 1 minute, review prompt is eligible again; stopping a screen recording triggers review flow & notification
    val after3DaysMs = baseTimeMs + com.example.util.AppReviewManager.DISMISS_COOLDOWN_MS + 60_000L
    assertTrue(com.example.util.AppReviewManager.isEligibleToShowReview(app, nowMs = after3DaysMs))
    val triggeredOnRecStop = com.example.util.AppReviewManager.onScreenRecordingStopped(app, nowMs = after3DaysMs)
    assertTrue("Stopping screen recording must trigger review flow", triggeredOnRecStop)
    assertTrue(com.example.util.AppReviewManager.showReviewPopup.value)
    assertEquals(
      com.example.util.ReviewTriggerSource.SCREEN_RECORDING_STOPPED,
      com.example.util.AppReviewManager.activeTriggerSource.value
    )
    val recNotifPayload = com.example.util.SmartNotificationManager.triggerReviewNotificationAfterRecording(app, nowMs = after3DaysMs)
    assertNotNull("Review push notification after recording must be created", recNotifPayload)

    // 5. User taps "Rate on Google Play" (opens Play Store review page) and then returns to the app (`onAppResumedCheckVerification`)
    com.example.util.AppReviewManager.launchPlayStoreReviewPage(app, selectedStars = 5, nowMs = after3DaysMs + 5000L)
    assertTrue("Popup hides when opening Play Store", !com.example.util.AppReviewManager.showReviewPopup.value)

    val verifiedOnReturn = com.example.util.AppReviewManager.onAppResumedCheckVerification(app, nowMs = after3DaysMs + 25_000L)
    assertTrue("Returning to app after opening Play Store review page must verify review", verifiedOnReturn)
    assertTrue("User must now be marked verified reviewed", com.example.util.AppReviewManager.isVerifiedReviewed(app))

    // 6. Once verified reviewed, NEVER show the popup or review notifications again (even 30 days later)
    val futureMs = after3DaysMs + 30L * 24L * 60L * 60L * 1000L
    assertTrue(!com.example.util.AppReviewManager.isEligibleToShowReview(app, nowMs = futureMs))
    assertTrue(!com.example.util.AppReviewManager.onBoostSessionEnded(app, nowMs = futureMs))
    assertTrue(!com.example.util.AppReviewManager.onScreenRecordingStopped(app, nowMs = futureMs))
    assertTrue(!com.example.util.AppReviewManager.showReviewPopup.value)
    assertTrue(com.example.util.SmartNotificationManager.triggerReviewNotificationAfterBoost(app, nowMs = futureMs) == null)
    assertTrue(com.example.util.SmartNotificationManager.triggerReviewNotificationAfterRecording(app, nowMs = futureMs) == null)

    com.example.util.AppReviewManager.resetForTesting(app)
  }
}





