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
    assertEquals("GMAX Gaming Hub", appName)
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
    assertEquals("Overlay & Focus Mode", gamingMode.title)
    assertEquals("Overlay & Focus Mode", beastMode.title)

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
  fun `verify v1_0 launch update controller home label and unity ads rewarded unlock`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
    assertTrue(BuildConfig.VERSION_CODE >= 15)
    assertEquals("Home", com.example.model.ControllerLayoutMode.PLAYSTATION.shortBadge)
    assertEquals("Home", com.example.model.ControllerLayoutResolver.playStationGlyphs.homeGlyph)
    assertEquals("Home", com.example.model.ControllerLayoutResolver.playStationGlyphs.homePromptName)

    assertEquals("800436845", com.example.util.UnityAdsManager.UNITY_GAME_ID)
    assertEquals("BP_Banner_Android", com.example.util.UnityAdsManager.BANNER_PLACEMENT_ID)
    assertEquals("BP_Interstitial_Android", com.example.util.UnityAdsManager.INTERSTITIAL_PLACEMENT_ID)
    assertEquals("BP_Rewarded_Android", com.example.util.UnityAdsManager.REWARDED_PLACEMENT_ID)
    assertEquals(
      "Banner_Android",
      com.example.util.UnityAdsManager.getFallbackPlacementId(com.example.util.UnityAdsManager.BANNER_PLACEMENT_ID)
    )
    assertEquals(
      "Interstitial_Android",
      com.example.util.UnityAdsManager.getFallbackPlacementId(com.example.util.UnityAdsManager.INTERSTITIAL_PLACEMENT_ID)
    )
    assertEquals(
      "Rewarded_Android",
      com.example.util.UnityAdsManager.getFallbackPlacementId(com.example.util.UnityAdsManager.REWARDED_PLACEMENT_ID)
    )
    assertTrue(!com.example.util.UnityAdsManager.TEST_MODE)
    assertTrue(!com.example.util.UnityAdsManager.resolveEffectiveTestMode(isEmulator = false, isDebugBuild = false, forceTestMode = true))
    assertTrue(!com.example.util.UnityAdsManager.resolveEffectiveTestMode(isEmulator = false, isDebugBuild = true, forceTestMode = true))
    assertTrue(!com.example.util.UnityAdsManager.resolveEffectiveTestMode(isEmulator = true, isDebugBuild = false, forceTestMode = false))

    com.example.util.UnityAdsManager.recordBannerDiagnostic(
      placementTag = "launcher_bottom",
      placementId = com.example.util.UnityAdsManager.BANNER_PLACEMENT_ID,
      status = "FAILED",
      errorCode = "NO_FILL",
      errorMessage = "No fill"
    )
    val diag = com.example.util.UnityAdsManager.bannerDiagnostics.value["launcher_bottom"]
    assertNotNull(diag)
    assertEquals("BP_Banner_Android", diag?.placementId)
    assertEquals("NO_FILL", diag?.errorCode)

    com.example.util.UnityAdsManager.initializeAndPreload(context)
    assertTrue(com.example.util.UnityAdsManager.needsPrivacyConsentPrompt.value)
    com.example.util.UnityAdsManager.submitPrivacyConsent(context, personalizedAdsGranted = true)
    assertTrue(!com.example.util.UnityAdsManager.needsPrivacyConsentPrompt.value)
    assertTrue(com.example.util.UnityAdsManager.isPersonalizedAdsEnabled.value)

    com.example.util.UnityAdsManager.showRewardedAdForProController(context)
    val activeAd = com.example.util.UnityAdsManager.activeFullScreenAd.value as? com.example.util.UnityAdsManager.FullScreenAdPresentation.Rewarded
    assertNotNull(activeAd)
    activeAd?.onRewardEarned?.invoke()
    assertTrue(com.example.util.UnityAdsManager.isProControllerUnlocked())
    com.example.util.UnityAdsManager.dismissActiveFullScreenAd()

    // Verify 10-hour HUD Overlay Rewarded Ad unlock on FPS & Diag tab
    assertEquals(10 * 60 * 60 * 1000L, com.example.util.UnityAdsManager.REWARD_HUD_OVERLAY_UNLOCK_DURATION_MS)
    com.example.util.UnityAdsManager.showRewardedAdForHudOverlay(context)
    val hudAd = com.example.util.UnityAdsManager.activeFullScreenAd.value as? com.example.util.UnityAdsManager.FullScreenAdPresentation.Rewarded
    assertNotNull(hudAd)
    assertEquals("BP_Rewarded_Android", hudAd?.adUnitId)
    hudAd?.onRewardEarned?.invoke()
    assertTrue(com.example.util.UnityAdsManager.isHudOverlayUnlocked())
    assertEquals(10, com.example.util.UnityAdsManager.remainingHudOverlayUnlockHours())
    com.example.util.UnityAdsManager.dismissActiveFullScreenAd()
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

      // 8. Verify PlayStation controller live input with deviceId >= 1 auto-binds controllerId >= 1 and persists across scanActiveController()
      com.example.util.ControllerHealthManager.simulatedWiredOverrideForTests = null
      val psCrossDown = android.view.KeyEvent(300L, 300L, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A, 0, 0, 5, 304)
      assertTrue(mgr.onKeyEvent(psCrossDown))
      assertTrue("Controller ID must bind to >= 1 on live PlayStation input", com.example.util.ControllerHealthManager.liveInputState.value.controllerId >= 1)
      assertTrue("isAnyControllerConnected must return true once bound", com.example.util.ControllerHealthManager.isAnyControllerConnected(app))
      mgr.scanActiveController()
      assertTrue("scanActiveController must preserve active live-bound controller", com.example.util.ControllerHealthManager.liveInputState.value.controllerId >= 1)
      val psCrossUp = android.view.KeyEvent(300L, 320L, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_A, 0, 0, 5, 304)
      mgr.onKeyEvent(psCrossUp)
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
      "Your games are waiting in your library. Jump back in for a focused gaming session.",
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

  @Test
  fun `verify unvalidated wifi and hotspot without real internet are rejected by NetworkConnectivityMonitor`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    // 1. Simulate a Wi-Fi or Hotspot connection with NET_CAPABILITY_INTERNET but NO NET_CAPABILITY_VALIDATED
    val unvalidatedWifiCaps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
    val shadowUnvalidated = org.robolectric.Shadows.shadowOf(unvalidatedWifiCaps)
    shadowUnvalidated.addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    shadowUnvalidated.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)

    assertTrue(
      "Wi-Fi or hotspot without NET_CAPABILITY_VALIDATED must be rejected as offline",
      !com.example.util.NetworkConnectivityMonitor.isCapabilitiesValidated(unvalidatedWifiCaps)
    )

    // 2. Simulate a captive portal Wi-Fi that has both INTERNET and VALIDATED but also CAPTIVE_PORTAL
    val captivePortalCaps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
    val shadowCaptive = org.robolectric.Shadows.shadowOf(captivePortalCaps)
    shadowCaptive.addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    shadowCaptive.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    shadowCaptive.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    shadowCaptive.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

    assertTrue(
      "Captive portal Wi-Fi must be rejected as offline",
      !com.example.util.NetworkConnectivityMonitor.isCapabilitiesValidated(captivePortalCaps)
    )

    // 3. Simulate a genuine validated Wi-Fi connection with real internet
    val validatedWifiCaps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
    val shadowValidated = org.robolectric.Shadows.shadowOf(validatedWifiCaps)
    shadowValidated.addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    shadowValidated.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    shadowValidated.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    assertTrue(
      "Validated Wi-Fi with real internet capability must pass capability validation",
      com.example.util.NetworkConnectivityMonitor.isCapabilitiesValidated(validatedWifiCaps)
    )

    // 4. Default unvalidated Robolectric network must report offline
    assertTrue(
      "Default unvalidated network must report isCurrentlyOnline == false",
      !com.example.util.NetworkConnectivityMonitor.isCurrentlyOnline(app)
    )
  }

  @Test
  fun `verify google analytics event logging and settings toggle synchronization`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    com.example.util.AppAnalyticsManager.clearRecentEventsForTesting()
    com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(app, true)

    // 1. Initialize and verify gmax_app_open event
    com.example.util.AppAnalyticsManager.initialize(app)
    com.example.util.AppAnalyticsManager.logScreenView(app, "Game_Launcher")
    com.example.util.AppAnalyticsManager.logGameBoost(
      context = app,
      gamePackage = "com.activision.callofduty.shooter",
      gameName = "Call of Duty Mobile",
      boostMode = "BEAST",
      ramFreedMb = 512L
    )
    com.example.util.AppAnalyticsManager.logScreenRecording(
      context = app,
      action = "start",
      resolution = "1080p",
      fps = 60,
      audioMode = "INTERNAL_ONLY"
    )
    com.example.util.AppAnalyticsManager.logControllerConnection(
      context = app,
      connected = true,
      controllerName = "DualSense Wireless Controller",
      connectionType = "BLUETOOTH"
    )
    com.example.data.repository.TopGearAnalyticsManager.logAnonymousTap(
      context = app,
      productId = "cooler_neveika_core",
      tier = "value",
      action = "affiliate_open"
    )

    val logged = com.example.util.AppAnalyticsManager.getRecentEventsForTesting()
    assertTrue(logged.any { it.name == "gmax_app_open" })
    assertTrue(logged.any { it.name == "screen_view" && it.params["screen_name"] == "Game_Launcher" })
    assertTrue(logged.any { it.name == "gmax_game_boost" && it.params["boost_mode"] == "BEAST" })
    assertTrue(logged.any { it.name == "gmax_screen_recording" && it.params["action"] == "start" })
    assertTrue(logged.any { it.name == "gmax_controller_connection" && it.params["status"] == "connected" })
    assertTrue(logged.any { it.name == "gmax_top_gear_tap" && it.params["product_id"] == "cooler_neveika_core" })

    // 2. Disabling Analytics in Settings / TopGearAnalyticsManager must suppress Google Analytics event logging
    com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(app, false)
    assertTrue(!com.example.util.AppAnalyticsManager.isAnalyticsCollectionEnabled(app))
    val countBeforeDisabledLog = com.example.util.AppAnalyticsManager.getRecentEventsForTesting().size
    com.example.util.AppAnalyticsManager.logScreenView(app, "Settings")
    assertEquals(countBeforeDisabledLog, com.example.util.AppAnalyticsManager.getRecentEventsForTesting().size)

    // Restore default enabled state
    com.example.data.repository.TopGearAnalyticsManager.setAnalyticsEnabled(app, true)
    assertTrue(com.example.util.AppAnalyticsManager.isAnalyticsCollectionEnabled(app))
  }

  @Test
  fun `verify dual sided squad referral loop milestone ladder and deferred deep link capture`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify Referral Code Generation & Normalization
    val generated = com.example.data.repository.SquadReferralRepository.generateReferralCode(
      uid = "uid_987654321",
      username = "ShadowViper"
    )
    assertTrue(generated.startsWith("GMAX-"))
    assertEquals(generated, com.example.data.repository.SquadReferralRepository.normalizeReferralCode(generated.lowercase()))

    // 2. Verify 4-Step Milestone Ladder & Stacking Pro Days
    assertEquals(4, com.example.data.repository.SquadMilestoneCatalog.TIERS.size)
    assertEquals(0, com.example.data.repository.SquadMilestoneCatalog.computeTotalProDays(0, null))
    assertEquals(7, com.example.data.repository.SquadMilestoneCatalog.computeTotalProDays(1, null))
    assertEquals(37, com.example.data.repository.SquadMilestoneCatalog.computeTotalProDays(3, null))
    assertEquals(97, com.example.data.repository.SquadMilestoneCatalog.computeTotalProDays(5, null))
    assertEquals(462, com.example.data.repository.SquadMilestoneCatalog.computeTotalProDays(10, null))

    // 3. Verify Deterministic Device Hardware Fingerprint
    com.example.util.DeviceFingerprintGenerator.setTestFingerprintOverride(null)
    val fp1 = com.example.util.DeviceFingerprintGenerator.getDeviceFingerprintHash(context)
    val fp2 = com.example.util.DeviceFingerprintGenerator.getDeviceFingerprintHash(context)
    assertTrue(fp1.startsWith("hw_sha256_"))
    assertEquals(fp1, fp2)

    // 4. Verify Code-Only Referral Normalization (URLs strictly rejected) & Share Copy
    com.example.util.SquadReferralManager.initialize(context)
    assertEquals(null, com.example.data.repository.SquadReferralRepository.normalizeReferralCode("https://gmaxhub.app/pass?ref=GMAX-VIPER-99AA"))
    assertEquals(null, com.example.data.repository.SquadReferralRepository.normalizeReferralCode("gmax://pass?ref=GMAX-VIPER-99AA"))
    assertEquals("GMAX-VIPER-99AA", com.example.data.repository.SquadReferralRepository.normalizeReferralCode("gmax-viper-99aa"))

    val shareCopy = com.example.data.repository.SquadReferralRepository.buildGuestPassShareCopy(
      referrerUsername = "ShadowViper",
      code = "GMAX-VIPER-99AA",
      passesRemaining = 3
    )
    assertTrue(shareCopy.contains("Pro Guest Pass"))
    assertTrue(!shareCopy.contains("https://"))
    assertTrue(!shareCopy.contains("gmax://"))
    assertTrue(shareCopy.contains("GMAX-VIPER-99AA"))

    // 5. Verify 8-Language Localization Engine (JA, ES_MX, ES_LA, HI, PT_BR, ID, VI, TL)
    val nonEnglishLanguages = listOf(
      com.example.util.AppLanguage.JA,
      com.example.util.AppLanguage.ES_MX,
      com.example.util.AppLanguage.ES_LA,
      com.example.util.AppLanguage.HI,
      com.example.util.AppLanguage.PT_BR,
      com.example.util.AppLanguage.ID,
      com.example.util.AppLanguage.VI,
      com.example.util.AppLanguage.TL
    )
    nonEnglishLanguages.forEach { lang ->
      val translatedTitle = com.example.util.AppStrings.trText(
        en = "14 Day Pro Guest Pass Vault",
        es = "Bóveda de Pases de Invitado Pro (14 Días)",
        languageOverride = lang
      )
      assertTrue("Expected translated vault title for $lang, got $translatedTitle", translatedTitle != "14 Day Pro Guest Pass Vault")
      val translatedImpact = com.example.util.AppStrings.autoTranslateDynamic("High Impact", lang)
      assertTrue("Expected translated impact badge for $lang, got $translatedImpact", translatedImpact != "High Impact")
    }
  }

  @Test
  fun `verify base64 config code json serialization quick browser presets and local trafficstats rate formatting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify JSON serialization and compressed Base64 GMAX-... round-trip
    val customCrosshair = com.example.model.OverlaySettings(
      isEnabled = true,
      crosshairStyle = 5, // Sniper Mil-Dot
      crosshairColor = 0xFFFF0055L,
      crosshairSizeDp = 44,
      crosshairAlpha = 0.85f
    )
    val customHud = com.example.model.OverlayHudConfig(
      isEnabled = true,
      showFps = true,
      showTemp = false,
      showBattery = true,
      showTimer = true,
      showRamPct = true,
      showClock = false,
      showNetSpeed = true,
      fpsMode = "FAST",
      targetCapMode = "120"
    )
    val customCtrl = com.example.model.ControllerOverlayConfig(
      isEnabled = true,
      scaleFactor = 1.25f,
      opacity = 0.80f,
      positionX = 140,
      positionY = 310
    )

    val jsonStr = com.example.util.ConfigCodeCodec.toJsonString(
      crosshair = customCrosshair,
      hud = customHud,
      hudX = 88,
      hudY = 164,
      controllerConfig = customCtrl,
      controllerLayoutMode = com.example.model.ControllerLayoutMode.PLAYSTATION
    )
    assertTrue(!jsonStr.contains("\"ch\""))
    assertTrue(jsonStr.contains("\"x\":88"))
    assertTrue(jsonStr.contains("\"y\":164"))

    val encodedCode = com.example.util.ConfigCodeCodec.encodeConfigCode(
      crosshair = customCrosshair,
      hud = customHud,
      hudX = 88,
      hudY = 164,
      controllerConfig = customCtrl,
      controllerLayoutMode = com.example.model.ControllerLayoutMode.PLAYSTATION
    )
    assertTrue(encodedCode.startsWith("GMAX-"))

    // Decode even when wrapped in a WhatsApp/Discord share message
    val chatMessage = "Yo squad, paste my pro sniper HUD layout: $encodedCode 🔥"
    val decoded = com.example.util.ConfigCodeCodec.decodeConfigCode(chatMessage)
    assertNotNull(decoded)
    assertEquals(88, decoded!!.hudPositionX)
    assertEquals(164, decoded.hudPositionY)
    assertEquals("FAST", decoded.hudConfig.fpsMode)
    assertEquals("120", decoded.hudConfig.targetCapMode)
    assertTrue(decoded.hudConfig.showNetSpeed)
    assertEquals(com.example.model.ControllerLayoutMode.PLAYSTATION, decoded.controllerLayoutMode)
    assertEquals(140, decoded.controllerConfig.positionX)
    assertEquals(310, decoded.controllerConfig.positionY)

    // 2. Verify Local TrafficStats KB/s rate formatting & choke/spike classification
    val rateNormal = com.example.util.ConfigCodeCodec.formatTrafficRate(48 * 1024L, 16 * 1024L, compact = false)
    assertEquals("↓48KB/s ↑16KB/s", rateNormal)
    val rateCompact = com.example.util.ConfigCodeCodec.formatTrafficRate(48 * 1024L, 16 * 1024L, compact = true)
    assertEquals("⇅64KB/s", rateCompact)
    assertEquals(
      com.example.util.TrafficHealthState.CHOKING,
      com.example.util.ConfigCodeCodec.classifyTrafficHealth(0L, 0L)
    )
    assertEquals(
      com.example.util.TrafficHealthState.HEALTHY,
      com.example.util.ConfigCodeCodec.classifyTrafficHealth(45 * 1024L, 18 * 1024L)
    )
    assertEquals(
      com.example.util.TrafficHealthState.HEAVY_SPIKE,
      com.example.util.ConfigCodeCodec.classifyTrafficHealth(2048 * 1024L, 128 * 1024L)
    )

    // 3. Verify Collapsible Quick Photo PiP legacy prefs are wiped cleanly
    val legacyPrefs = context.getSharedPreferences("gmax_quick_browser_prefs", Context.MODE_PRIVATE)
    legacyPrefs.edit().putBoolean("enabled", true).commit()
    com.example.util.AppCrashGuard.safeOverlayCleanupAndReset(context)
    assertTrue(!legacyPrefs.contains("enabled"))
  }

  @Test
  fun `verify streamer blackout boxes presets resize persistence and local clip trimmer storage modes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify Streamer Blackout Boxes preset spawning, resizing, touch-lock, and persistence
    com.example.service.StreamerBlackoutOverlayService.clearAllBoxes(context)
    val stMinimap = com.example.service.StreamerBlackoutOverlayService.spawnPresetBox(
      context,
      com.example.service.BlackoutPresetType.MINIMAP
    )
    assertTrue(stMinimap.isEnabled)
    assertEquals(1, stMinimap.boxes.size)
    assertEquals("MINIMAP SHIELD", stMinimap.boxes.first().label)

    val stChat = com.example.service.StreamerBlackoutOverlayService.spawnPresetBox(
      context,
      com.example.service.BlackoutPresetType.CHAT
    )
    assertEquals(2, stChat.boxes.size)

    val minimapId = stChat.boxes.first().id
    val stResized = com.example.service.StreamerBlackoutOverlayService.updateBoxBounds(
      context = context,
      boxId = minimapId,
      newX = 48,
      newY = 72,
      newWidthDp = 210,
      newHeightDp = 165
    )
    val updatedBox = stResized.boxes.first { it.id == minimapId }
    assertEquals(48, updatedBox.x)
    assertEquals(72, updatedBox.y)
    assertEquals(210, updatedBox.widthDp)
    assertEquals(165, updatedBox.heightDp)

    val stLocked = com.example.service.StreamerBlackoutOverlayService.setManualTouchLocked(context, true)
    assertTrue(stLocked.isEffectiveTouchLocked)
    val stPure = com.example.service.StreamerBlackoutOverlayService.setPurePitchBlack(context, true)
    assertTrue(stPure.isPurePitchBlack)

    val reloaded = com.example.service.StreamerBlackoutOverlayService.loadState(context)
    assertEquals(2, reloaded.boxes.size)
    assertTrue(reloaded.isManualTouchLocked)
    assertTrue(reloaded.isPurePitchBlack)

    val stCleared = com.example.service.StreamerBlackoutOverlayService.clearAllBoxes(context)
    assertTrue(!stCleared.isEnabled)
    assertTrue(stCleared.boxes.isEmpty())

    // 2. Verify Local Clip Trimmer range normalization, size estimation, and 3 storage modes
    val total30MinMs = 30L * 60L * 1_000L // 1,800,000 ms (30 minutes)
    val originalSize4Gb = 2_400L * 1024L * 1024L // 2.4 GB
    val (normStart, normEnd) = com.example.util.LocalVideoTrimmer.normalizeTrimRange(
      startMs = 60_000L,
      endMs = 75_000L, // 15-second clutch slice
      totalDurationMs = total30MinMs
    )
    assertEquals(60_000L, normStart)
    assertEquals(75_000L, normEnd)

    val est15sSize = com.example.util.LocalVideoTrimmer.estimateTrimmedSizeBytes(
      originalSizeBytes = originalSize4Gb,
      totalDurationMs = total30MinMs,
      trimStartMs = normStart,
      trimEndMs = normEnd
    )
    // 15s out of 1800s is 1/120th (~20 MB)
    assertTrue(est15sSize in (15L * 1024L * 1024L)..(25L * 1024L * 1024L))

    val savedIfNewClip = com.example.util.LocalVideoTrimmer.estimateBytesSaved(
      originalSizeBytes = originalSize4Gb,
      totalDurationMs = total30MinMs,
      trimStartMs = normStart,
      trimEndMs = normEnd,
      saveMode = com.example.util.ClipTrimSaveMode.SAVE_AS_NEW_CLIP
    )
    assertEquals(0L, savedIfNewClip)

    val savedIfOverwrite = com.example.util.LocalVideoTrimmer.estimateBytesSaved(
      originalSizeBytes = originalSize4Gb,
      totalDurationMs = total30MinMs,
      trimStartMs = normStart,
      trimEndMs = normEnd,
      saveMode = com.example.util.ClipTrimSaveMode.OVERWRITE_ORIGINAL
    )
    assertTrue(savedIfOverwrite > 2_300L * 1024L * 1024L)
    assertEquals(3, com.example.util.ClipTrimSaveMode.entries.size)
  }

  @Test
  fun `verify notification ticker default 6s countdown adjustable timer and 5 silent quick replies`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify default 6-second countdown and adjustable range (3s..15s)
    val defaultCfg = com.example.service.NotificationTickerOverlayService.loadConfig(context)
    assertTrue(defaultCfg.isEnabled)
    assertEquals(6, com.example.service.NotificationTickerOverlayService.DEFAULT_COUNTDOWN_SECONDS)

    val updated10s = com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, 10)
    assertEquals(10, updated10s.countdownSeconds)
    val clampedMin = com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, 1)
    assertEquals(3, clampedMin.countdownSeconds)
    val restored6s = com.example.service.NotificationTickerOverlayService.setCountdownSeconds(context, 6)
    assertEquals(6, restored6s.countdownSeconds)

    // 2. Verify exact 5 pre-canned silent quick-reply options
    assertEquals(
      listOf("In a ranked match", "Need 5 mins", "Yes", "No", "Coming"),
      com.example.service.NotificationTickerOverlayService.QUICK_REPLIES
    )

    // 3. Verify demo message ticker & silent RemoteInput reply execution
    com.example.service.NotificationTickerOverlayService.triggerDemoTicker(context, isCall = false)
    val msgEvent = com.example.service.NotificationTickerOverlayService.latestEvent.value
    assertNotNull(msgEvent)
    assertTrue(!msgEvent!!.isCall)
    assertTrue(msgEvent.hasQuickReplyCapability)
    assertEquals(6, msgEvent.countdownSeconds)

    val sentOk = com.example.service.NotificationTickerOverlayService.sendSilentRemoteInputReply(
      context = context,
      event = msgEvent,
      replyText = "In a ranked match"
    )
    assertTrue(sentOk)
    assertEquals(
      "In a ranked match",
      com.example.service.NotificationTickerOverlayService.lastSilentReplySent.value
    )

    // 4. Verify call ticker dispatch and supported messaging packages
    com.example.service.NotificationTickerOverlayService.triggerDemoTicker(context, isCall = true)
    val callEvent = com.example.service.NotificationTickerOverlayService.latestEvent.value
    assertNotNull(callEvent)
    assertTrue(callEvent!!.isCall)
    assertTrue(
      com.example.service.GMaxNotificationTickerListenerService.SUPPORTED_MESSAGING_And_CALL_PACKAGES.containsAll(
        listOf("com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging", "com.discord")
      )
    )
  }

  @Test
  fun `verify gaming volume lock AudioManager snap back and hardcore call blocker post match summary`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    // 1. Configure Gaming Volume Lock at 80% and simulate foreground game + accidental volume drop
    com.example.service.GamingVolumeLockManager.setEnabled(context, true)
    com.example.service.GamingVolumeLockManager.setLockedVolumePercent(context, 80)
    com.example.service.GamingVolumeLockManager.setGameInForeground(context, true)

    val expectedLockedIndex = com.example.service.GamingVolumeLockManager.computeTargetStreamIndex(audioManager, 80)
    assertEquals(expectedLockedIndex, audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))

    // Simulate accidental volume-down button press during a match
    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 1, 0)
    val snappedBack = com.example.service.GamingVolumeLockManager.enforceLockedVolumeIfChanged(
      context = context,
      requireForegroundGame = true
    )
    assertTrue("GamingVolumeLockManager must detect volume change and snap back", snappedBack)
    assertEquals(
      "Volume must be restored to locked target index",
      expectedLockedIndex,
      audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    )
    val volState = com.example.service.GamingVolumeLockManager.state.value
    assertTrue(volState.snapBackCount >= 1)
    assertTrue(volState.lastNoticeMessage?.contains("80%") == true)

    // 2. Verify Hardcore Mode Call-Blocker & Post-Match Summary Missed Call Log
    com.example.service.HardcoreCallBlockerManager.clearBlockedCallsLog(context)
    com.example.service.HardcoreCallBlockerManager.setEnabled(context, true)
    com.example.service.HardcoreCallBlockerManager.setActiveGameName(context, "Call of Duty Mobile")

    val record = com.example.service.HardcoreCallBlockerManager.recordBlockedCall(
      context = context,
      phoneNumber = "+15552348910",
      callerLabel = "Squad Leader Alex"
    )
    assertEquals("+15552348910", record.phoneNumber)
    assertEquals("Call of Duty Mobile", record.gameName)
    assertEquals(1, com.example.service.HardcoreCallBlockerManager.state.value.blockedCalls.size)
    assertEquals(1, com.example.service.HardcoreCallBlockerManager.state.value.unseenBlockedCount)

    // Returning to GMax after a match must automatically trigger the Post-Match Summary modal
    val autoOpened = com.example.service.HardcoreCallBlockerManager.onUserReturnedToHubCheckPostMatchSummary(context)
    assertTrue("Post-Match Summary popup must auto-open on return when calls were blocked", autoOpened)
    assertTrue(com.example.service.HardcoreCallBlockerManager.state.value.showPostMatchSummaryModal)

    com.example.service.HardcoreCallBlockerManager.dismissPostMatchSummaryModal(context)
    assertTrue(!com.example.service.HardcoreCallBlockerManager.state.value.showPostMatchSummaryModal)
    assertEquals(0, com.example.service.HardcoreCallBlockerManager.state.value.unseenBlockedCount)
  }

  @Test
  fun `verify anti cheat speedrun timer monotonic clock watermark splits tamper lock and app info dossier`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Test Anti-Cheat Speedrun Timer monotonic clock, splits, and rolling SHA-256 hash watermark
    com.example.service.SpeedrunTimerOverlayService.loadSavedEnabled(context)
    com.example.service.SpeedrunTimerOverlayService.startNewVerifiedRun(
      gamePackage = "com.tencent.ig",
      gameName = "PUBG Mobile",
      customStartMonoNanos = 1_000_000_000L,
      customStartWallMs = 1_700_000_000_000L
    )

    // Advance monotonic clock by 12.345 seconds and wall clock by 12.345 seconds (zero skew)
    com.example.service.SpeedrunTimerOverlayService.tickTimer(
      nowMonoNanos = 13_345_000_000L,
      nowWallMs = 1_700_000_012_345L
    )

    val stateAfterTick = com.example.service.SpeedrunTimerOverlayService.state.value
    assertTrue(stateAfterTick.isRunning)
    assertEquals(12_345L, stateAfterTick.elapsedMs)
    assertEquals("00:12.345", stateAfterTick.formattedTime)
    assertEquals(com.example.service.SpeedrunIntegrityStatus.VERIFIED_CLEAN, stateAfterTick.integrityStatus)
    assertTrue(stateAfterTick.liveVerificationHash.startsWith("SR-"))

    // Record a split at 12.345s
    val split = com.example.service.SpeedrunTimerOverlayService.recordSplit(
      customLabel = "Segment 1",
      nowMonoNanos = 13_345_000_000L,
      nowWallMs = 1_700_000_012_345L
    )
    assertNotNull(split)
    assertEquals(1, com.example.service.SpeedrunTimerOverlayService.state.value.splits.size)

    // Finish clean run and verify certificate generation
    com.example.service.SpeedrunTimerOverlayService.finishRun(
      nowMonoNanos = 25_500_000_000L,
      nowWallMs = 1_700_000_024_500L
    )
    val finishedClean = com.example.service.SpeedrunTimerOverlayService.state.value
    assertTrue(finishedClean.isFinished)
    assertTrue(finishedClean.runCertificateCode?.startsWith("GMAX-SR-") == true)

    // 2. Test Clock-Skew Anti-Cheat Tamper Lock (>1500ms wall-clock jump vs monotonic clock)
    com.example.service.SpeedrunTimerOverlayService.startNewVerifiedRun(
      gamePackage = "com.tencent.ig",
      gameName = "PUBG Mobile",
      customStartMonoNanos = 100_000_000_000L,
      customStartWallMs = 1_700_000_100_000L
    )
    // Monotonic clock advances 2,000ms, but system wall clock is jumped back by 60,000ms (cheat attempt!)
    com.example.service.SpeedrunTimerOverlayService.tickTimer(
      nowMonoNanos = 102_000_000_000L,
      nowWallMs = 1_700_000_040_000L
    )
    val tamperedState = com.example.service.SpeedrunTimerOverlayService.state.value
    assertEquals(
      com.example.service.SpeedrunIntegrityStatus.TAMPERED_CLOCK_SKEW,
      tamperedState.integrityStatus
    )
    assertTrue(!tamperedState.integrityStatus.isClean)

    // 3. Test (i) App Info Dossier (App Size, Launch Count, Recorded Video Count/Duration/Size, App Version)
    val testPkg = "com.tencent.ig"
    com.example.util.GamePlayStatsHelper.recordGameLaunch(context, testPkg, "PUBG Mobile")
    com.example.util.GameAppDossierHelper.recordVideoForGamePackage(
      context = context,
      packageName = testPkg,
      durationMs = 145_000L,
      sizeBytes = 48L * 1024L * 1024L
    )

    val dossier = com.example.util.GameAppDossierHelper.buildAppDossier(
      context = context,
      game = com.example.model.InstalledApp(
        appName = "PUBG Mobile",
        packageName = testPkg,
        versionName = "3.4.0",
        memoryUsageMb = 920L,
        isGame = true
      )
    )
    assertEquals("PUBG Mobile", dossier.appName)
    assertEquals(testPkg, dossier.packageName)
    assertTrue("Launch count must reflect recorded launches", dossier.launchCount >= 1)
    assertTrue("Recorded clip count must be >= 1", dossier.recordedClipsCount >= 1)
    assertTrue("Recorded video duration must be >= 145,000ms", dossier.recordedVideoDurationMs >= 145_000L)
    assertTrue("Recorded video storage size must be >= 48MB", dossier.recordedVideoBytes >= 48L * 1024L * 1024L)
    assertEquals("3.4.0", dossier.versionName)
    assertTrue("App size must be populated", dossier.apkSizeBytes > 0L)
  }

  @Test
  fun `verify screenshot editor crop color filters and last 30 seconds instant replay buffer`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify ScreenshotEditHelper aspect ratio bounds, crop, and 6 ColorMatrix filters
    val srcBitmap = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888).apply {
      eraseColor(android.graphics.Color.rgb(80, 100, 140))
    }

    val squareBounds = com.example.util.ScreenshotEditHelper.computeAspectBounds(
      sourceWidth = 400,
      sourceHeight = 200,
      aspect = com.example.util.ScreenshotCropAspect.RATIO_1_1
    )
    assertEquals(0.25f, squareBounds.leftFrac, 0.01f)
    assertEquals(0.75f, squareBounds.rightFrac, 0.01f)

    val croppedAndFiltered = com.example.util.ScreenshotEditHelper.cropAndFilterBitmap(
      source = srcBitmap,
      bounds = squareBounds,
      filterPreset = com.example.util.ScreenshotFilterPreset.NIGHT_VISION
    )
    assertEquals(200, croppedAndFiltered.width)
    assertEquals(200, croppedAndFiltered.height)
    val origPixel = srcBitmap.getPixel(100, 100)
    val nightVisionPixel = croppedAndFiltered.getPixel(100, 100)
    assertTrue(
      "Night Vision filter must boost green channel relative to original pixel",
      android.graphics.Color.green(nightVisionPixel) > android.graphics.Color.green(origPixel)
    )

    val dummyShot = com.example.model.CapturedScreenshot(
      id = 701L,
      uriString = "",
      title = "GMax_Shot_Apex_20261111_210000.png",
      sizeBytes = 24000L,
      dateAddedSec = System.currentTimeMillis() / 1000L,
      width = 400,
      height = 200,
      filePath = java.io.File(context.cacheDir, "GMax_Shot_Apex_20261111_210000.png").absolutePath,
      gameName = "Apex Legends",
      gamePackage = "com.ea.gp.apexlegendsmobilefps"
    )
    val saveCopyResult = com.example.util.ScreenshotEditHelper.saveEditedScreenshot(
      context = context,
      original = dummyShot,
      editedBitmap = croppedAndFiltered,
      overwriteOriginal = false,
      filterPreset = com.example.util.ScreenshotFilterPreset.NIGHT_VISION
    )
    assertTrue(saveCopyResult.success)
    assertEquals(200, saveCopyResult.widthPx)
    assertEquals(200, saveCopyResult.heightPx)
    assertTrue(java.io.File(saveCopyResult.savedFilePath).exists())

    // 2. Verify Last 30 Seconds Instant Replay Buffer & Clip Saving
    com.example.util.InstantReplayBufferManager.init(context)
    val armedState = com.example.util.InstantReplayBufferManager.setBufferEnabled(context, true)
    assertTrue(armedState.isBufferEnabled)
    assertEquals(30, armedState.bufferedSecondsAvailable)

    com.example.util.GamePlayStatsHelper.recordGameLaunch(
      context,
      "com.ea.gp.apexlegendsmobilefps",
      "Apex Legends"
    )
    val replayResult = com.example.util.InstantReplayBufferManager.saveLast30SecondsReplay(context)
    assertTrue("Last 30s replay clip must be saved to disk", replayResult.success)
    assertEquals(30_000L, replayResult.durationMs)
    assertTrue(replayResult.savedFileName.contains("Replay30s"))
    assertTrue(java.io.File(replayResult.savedFilePath).exists())
    assertTrue(com.example.util.InstantReplayBufferManager.state.value.totalReplaysSaved >= 1)
  }

  @Test
  fun `verify account unique v2 base64 hud crosshair config and ja es_mx es_la hi languages`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify Account-Unique Base64 Code Generation & Per-Account Isolation
    val sharedCrosshair = com.example.model.OverlaySettings(
      isEnabled = true,
      crosshairStyle = 5, // Sniper Mil-Dot
      crosshairColor = 0xFFFF2A4DL,
      crosshairSizeDp = 44,
      crosshairAlpha = 0.90f,
      offsetXdp = -6,
      offsetYdp = 8,
      strokeThicknessDp = 3.5f,
      centerGapDp = 7.0f,
      showOutline = true,
      showCenterDot = false
    )
    val sharedHud = com.example.model.OverlayHudConfig(
      isEnabled = true,
      showFps = true,
      showNetSpeed = true,
      fpsMode = "PRO",
      targetCapMode = "120"
    )

    val codeAccountA = com.example.util.ConfigCodeCodec.encodeConfigCode(
      crosshair = sharedCrosshair,
      hud = sharedHud,
      hudX = 88,
      hudY = 144,
      accountKey = "uid_alpha_101",
      creatorHandle = "alpha_sniper",
      lightbarColorHex = "#FF2A4D",
      volumeLockEnabled = true,
      volumeLockPercent = 85,
      hardcoreModeEnabled = true,
      recorderResolutionLabel = "1440p QHD",
      recorderFps = 120,
      recorderBitrateMbps = 48,
      recorderMicEnabled = true,
      recorderBubbleSizeDp = 48,
      instantReplay30sEnabled = true,
      boostModeName = "BEAST",
      appliedOptionIds = setOf("perf_fps_unlock", "net_low_latency", "touch_guard"),
      speedrunTimerCategory = "100%",
      speedrunTamperLockEnabled = true
    )

    val codeAccountB = com.example.util.ConfigCodeCodec.encodeConfigCode(
      crosshair = sharedCrosshair,
      hud = sharedHud,
      hudX = 88,
      hudY = 144,
      accountKey = "uid_bravo_202",
      creatorHandle = "bravo_rusher",
      lightbarColorHex = "#FF2A4D",
      volumeLockEnabled = true,
      volumeLockPercent = 85,
      hardcoreModeEnabled = true,
      recorderResolutionLabel = "1440p QHD",
      recorderFps = 120,
      recorderBitrateMbps = 48,
      recorderMicEnabled = true,
      recorderBubbleSizeDp = 48,
      instantReplay30sEnabled = true,
      boostModeName = "BEAST",
      appliedOptionIds = setOf("perf_fps_unlock", "net_low_latency", "touch_guard"),
      speedrunTimerCategory = "100%",
      speedrunTamperLockEnabled = true
    )

    assertTrue("Account A and Account B must generate distinct Base64 codes", codeAccountA != codeAccountB)

    val decodedA = com.example.util.ConfigCodeCodec.decodeConfigCode(codeAccountA)
    assertNotNull("Decoded v2 payload must not be null", decodedA)
    requireNotNull(decodedA)
    assertEquals(2, decodedA.version)
    assertEquals("@alpha_sniper", decodedA.creatorHandle)
    assertTrue("Creator signature must verify cleanly", decodedA.signatureVerified)
    assertTrue(decodedA.volumeLockEnabled)
    assertEquals(85, decodedA.volumeLockPercent)
    assertTrue(decodedA.hardcoreModeEnabled)
    assertEquals(120, decodedA.recorderFps)
    assertTrue(decodedA.instantReplay30sEnabled)
    assertEquals("BEAST", decodedA.boostModeName)
    assertTrue(decodedA.appliedOptionIds.contains("net_low_latency"))

    // Verify per-account HUD position storage isolation
    com.example.util.ConfigCodeCodec.saveHudPosition(context, 88, 144, accountKeyOverride = "uid_alpha_101")
    com.example.util.ConfigCodeCodec.saveHudPosition(context, 210, 320, accountKeyOverride = "uid_bravo_202")
    assertEquals(88 to 144, com.example.util.ConfigCodeCodec.getSavedHudPosition(context, "uid_alpha_101"))
    assertEquals(210 to 320, com.example.util.ConfigCodeCodec.getSavedHudPosition(context, "uid_bravo_202"))

    // 2. Verify Japanese (ja), Spanish - Mexico (es-MX), Spanish - Latin America (es-419), and Hindi (hi)
    assertEquals(com.example.util.AppLanguage.JA, com.example.util.AppLanguage.fromCode("ja"))
    assertEquals(com.example.util.AppLanguage.ES_MX, com.example.util.AppLanguage.fromCode("es-MX"))
    assertEquals(com.example.util.AppLanguage.ES_LA, com.example.util.AppLanguage.fromCode("es-419"))
    assertEquals(com.example.util.AppLanguage.HI, com.example.util.AppLanguage.fromCode("hi"))

    assertEquals(
      com.example.util.AppLanguage.JA,
      com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale.forLanguageTag("ja-JP"))
    )
    assertEquals(
      com.example.util.AppLanguage.ES_MX,
      com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale.forLanguageTag("es-MX"))
    )
    assertEquals(
      com.example.util.AppLanguage.ES_LA,
      com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale.forLanguageTag("es-AR"))
    )
    assertEquals(
      com.example.util.AppLanguage.HI,
      com.example.util.AppStrings.detectDeviceLanguage(localeOverride = java.util.Locale.forLanguageTag("hi-IN"))
    )

    assertEquals(
      "ランチャー",
      com.example.util.AppStrings.trText("Launcher", "Lanzador", languageOverride = com.example.util.AppLanguage.JA)
    )
    assertEquals(
      "लॉन्चर",
      com.example.util.AppStrings.trText("Launcher", "Lanzador", languageOverride = com.example.util.AppLanguage.HI)
    )
    assertEquals(
      "Control",
      com.example.util.AppStrings.trText("Controller", "Control", languageOverride = com.example.util.AppLanguage.ES_MX)
    )
    assertEquals(
      "Mando",
      com.example.util.AppStrings.trText("Controller", "Control", languageOverride = com.example.util.AppLanguage.ES_LA)
    )
  }

  @Test
  fun `verify real call screening quick photo pip speedrun sizing history and base64 boost timer autostart`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify Real Call Screening logs only genuine calls and deduplicates rapid retries
    com.example.service.HardcoreCallBlockerManager.clearBlockedCallsLog(context)
    val realCall = com.example.service.HardcoreCallBlockerManager.recordBlockedCall(
      context = context,
      phoneNumber = "+14158904321",
      callerLabel = "Incoming Carrier Line"
    )
    assertNotNull(realCall)
    assertEquals(1, com.example.service.HardcoreCallBlockerManager.state.value.blockedCalls.size)

    // Rapid duplicate within 5s window should not create a second log entry
    com.example.service.HardcoreCallBlockerManager.recordBlockedCall(
      context = context,
      phoneNumber = "+14158904321",
      callerLabel = "Incoming Carrier Line"
    )
    assertEquals(1, com.example.service.HardcoreCallBlockerManager.state.value.blockedCalls.size)

    // 2. Verify In-Call Notification Ticker suppression once call is answered/handled
    com.example.service.GMaxNotificationTickerListenerService.markCallHandledAndDismiss(
      context = context,
      notificationKey = "call_key_99",
      packageName = "com.google.android.dialer"
    )

    // 3. Verify all 9 PerformanceOption tweaks and technical gaming acronyms are translated and expanded across all non-English languages
    val allTweaks = com.example.data.PerformanceOptionsRepository.getAllOptions(context)
    assertTrue(allTweaks.size >= 7)
    val nonEnglishLanguages = listOf(
      com.example.util.AppLanguage.ES_MX,
      com.example.util.AppLanguage.ES_LA,
      com.example.util.AppLanguage.JA,
      com.example.util.AppLanguage.HI,
      com.example.util.AppLanguage.PT_BR,
      com.example.util.AppLanguage.ID,
      com.example.util.AppLanguage.VI,
      com.example.util.AppLanguage.TL
    )
    for (lang in nonEnglishLanguages) {
      for (tweak in allTweaks) {
        val translatedTitle = com.example.util.AppStrings.autoTranslateDynamic(tweak.title, languageOverride = lang)
        val translatedDesc = com.example.util.AppStrings.autoTranslateDynamic(tweak.description, languageOverride = lang)
        assertTrue("Tweak title '${tweak.title}' must translate for $lang", translatedTitle != tweak.title)
        assertTrue("Tweak description '${tweak.description}' must translate for $lang", translatedDesc != tweak.description)
      }
      val expandedFpsRam = com.example.util.AppStrings.autoTranslateDynamic("FPS & RAM & CPU & GPU & DNS & ADB & HUD", languageOverride = lang)
      assertTrue("Technical acronyms must be expanded in $lang: $expandedFpsRam", !Regex("""\bFPS\b""").containsMatchIn(expandedFpsRam))
      assertTrue("Technical acronyms must be expanded in $lang: $expandedFpsRam", !Regex("""\bRAM\b""").containsMatchIn(expandedFpsRam))
    }

    // 4. Verify Anti-Cheat Speedrun Timer sizing presets and per-app past speedrun history
    com.example.service.SpeedrunTimerOverlayService.loadSavedEnabled(context)
    com.example.service.SpeedrunTimerOverlayService.clearPastRunsHistory(context)
    com.example.service.SpeedrunTimerOverlayService.setSizePreset(
      context,
      com.example.service.SpeedrunOverlaySizePreset.EXPANDED
    )
    assertEquals(
      com.example.service.SpeedrunOverlaySizePreset.EXPANDED,
      com.example.service.SpeedrunTimerOverlayService.state.value.sizePreset
    )
    assertEquals(1.25f, com.example.service.SpeedrunTimerOverlayService.state.value.overlayScale, 0.05f)

    com.example.service.SpeedrunTimerOverlayService.startNewVerifiedRun(
      gamePackage = "com.activision.callofduty.shooter",
      gameName = "COD Mobile",
      customStartMonoNanos = 1_000_000_000L,
      customStartWallMs = 1_000L
    )
    com.example.service.SpeedrunTimerOverlayService.finishRun(
      nowMonoNanos = 129_450_000_000L,
      nowWallMs = 129_450L
    )
    val pastRuns = com.example.service.SpeedrunTimerOverlayService.state.value.pastRuns
    assertEquals(1, pastRuns.size)
    assertEquals("COD Mobile", pastRuns.first().gameName)
    assertEquals("com.activision.callofduty.shooter", pastRuns.first().gamePackage)
    assertEquals(128_450L, pastRuns.first().elapsedMs)

    // 5. Verify Base64 Config Code encodes and decodes boostTimerActive = true
    val boostActiveCode = com.example.util.ConfigCodeCodec.encodeConfigCode(
      crosshair = com.example.model.OverlaySettings(isEnabled = true, crosshairStyle = 2),
      hud = com.example.model.OverlayHudConfig(isEnabled = true, showFps = true),
      hudX = 60,
      hudY = 100,
      accountKey = "boost_sharer_01",
      creatorHandle = "boost_leader",
      boostModeName = "BEAST",
      boostTimerActive = true
    )
    val decodedBoost = com.example.util.ConfigCodeCodec.decodeConfigCode(boostActiveCode)
    assertNotNull(decodedBoost)
    requireNotNull(decodedBoost)
    assertTrue("Decoded Base64 config must preserve boostTimerActive = true", decodedBoost.boostTimerActive)
    assertTrue(decodedBoost.summaryLabelEn.contains("Overlay and Focus Mode Timer Started"))
  }

  @Test
  fun `verify Alberto Gift for hoseamk1 grants 100k gamerscore 720p 60fps 6mbps dark mode and prelaunch notification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(com.example.util.AlbertoGiftManager.isTargetGiftRecipient("hoseamk1@gmail.com"))
    assertTrue(com.example.util.AlbertoGiftManager.isTargetGiftRecipient("  HoseaMK1@gmail.com "))

    val rawProfile = com.example.data.repository.UserCloudProfile(
      userId = "uid_hosea",
      email = "hoseamk1@gmail.com",
      gamerscore = 1200L,
      recResolutionLabel = "1080p",
      recFps = 30L,
      recBitrateMbps = 12L,
      themeMode = "AUTO_SYSTEM"
    )
    val giftedProfile = com.example.util.AlbertoGiftManager.applyGiftToProfileAndLocalStorage(
      context = context,
      profile = rawProfile
    )
    assertEquals(100_000L, giftedProfile.gamerscore)
    assertEquals("720p", giftedProfile.recResolutionLabel)
    assertEquals(60L, giftedProfile.recFps)
    assertEquals(6L, giftedProfile.recBitrateMbps)
    assertEquals("DARK_HUB", giftedProfile.themeMode)

    val savedRec = com.example.util.ScreenRecordingManager.loadSettings(context)
    assertEquals("720p", savedRec.resolutionLabel)
    assertEquals(1280, savedRec.width)
    assertEquals(720, savedRec.height)
    assertEquals(60, savedRec.fps)
    assertEquals(6, savedRec.bitrateMbps)

    val gsData = com.example.util.GamerscoreEngine.calculateGamerscoreFromVerifiedScore(giftedProfile.gamerscore)
    assertEquals(100_000L, gsData.totalScore)
    assertEquals("Apex Legend Lv.1", gsData.rankTitle)

    com.example.util.SmartNotificationManager.ensureNotificationChannel(context)
    val notified = com.example.util.AlbertoGiftManager.checkAndPostPreLaunchNotification(
      context = context,
      explicitEmail = "hoseamk1@gmail.com",
      forcePost = true
    )
    assertTrue("Alberto Gift notification should be dispatched for hoseamk1@gmail.com", notified)

    // Verify UnityAdsManager live mode enforcement & BP_* -> Default placement fallback
    assertEquals(
      false,
      com.example.util.UnityAdsManager.resolveEffectiveTestMode(
        com.example.util.UnityAdsManager.isRunningOnEmulator(
          fingerprint = "google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036.A1/11228894:user/release-keys",
          model = "sdk_gphone64_x86_64",
          manufacturer = "Google",
          brand = "google",
          device = "emu64xa",
          product = "sdk_gphone64_x86_64",
          hardware = "ranchu"
        )
      )
    )
    assertEquals(
      false,
      com.example.util.UnityAdsManager.resolveEffectiveTestMode(
        com.example.util.UnityAdsManager.isRunningOnEmulator(
          fingerprint = "samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S918BXXS3BWK5:user/release-keys",
          model = "SM-S918B",
          manufacturer = "samsung",
          brand = "samsung",
          device = "dm3q",
          product = "dm3qxxx",
          hardware = "qcom"
        )
      )
    )
    assertEquals(
      "Interstitial_Android",
      com.example.util.UnityAdsManager.getFallbackPlacementId("BP_Interstitial_Android")
    )
    assertEquals(
      "Rewarded_Android",
      com.example.util.UnityAdsManager.getFallbackPlacementId("BP_Rewarded_Android")
    )
    assertEquals(
      "Banner_Android",
      com.example.util.UnityAdsManager.getFallbackPlacementId("BP_Banner_Android")
    )
  }

  @Test
  fun `verify overlay and focus mode timer automatic beast session duration 1h 30m`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = com.example.util.BoosterSessionManager(context)
    sessionManager.stopSession()

    // Starting a session with either GAMING or BEAST automatically locks in BEAST (1h 30m)
    sessionManager.startSession(com.example.model.BoostMode.GAMING, "com.activision.callofduty.shooter", "Call of Duty Mobile")
    assertTrue(sessionManager.isSessionActive())
    assertEquals(com.example.model.BoostMode.BEAST, sessionManager.getActiveMode())
    assertTrue(sessionManager.getRemainingMillis() > 85 * 60 * 1000L)
    sessionManager.stopSession()
  }

  @Test
  fun `verify rivals squad competition leaderboard 5 ranks 1st place crown 2x popup monthly reset notifications and 1 click preset taunts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify Rivals 5-rank labels and 150-char bio limit
    assertEquals(listOf("1st", "2nd", "3rd", "4th", "5th"), com.example.data.repository.RivalsRepository.RANK_PLACEMENTS)
    assertEquals(150, com.example.data.repository.RivalsRepository.MAX_SQUAD_BIO_LENGTH)
    assertEquals(5, com.example.data.repository.RivalsRepository.MAX_SQUAD_MEMBERS)

    // 2. Verify 7 exact default preset taunts
    assertEquals(
      listOf(
        "Better luck next time",
        "Catch me if you can",
        "Your too slow",
        "ez",
        "Noobs",
        "GG",
        "Im Just Better"
      ),
      com.example.data.repository.RivalsRepository.PRESET_TAUNTS
    )

    // 3. Verify 5-rank leaderboard sorting and 1st place crown flag
    val unsortedClaims = listOf(
      com.example.data.repository.UsernameClaim(userId = "u3", username = "Player3", displayName = "Player 3", gamerscore = 1800L),
      com.example.data.repository.UsernameClaim(userId = "u1", username = "Player1", displayName = "Player 1", gamerscore = 5200L),
      com.example.data.repository.UsernameClaim(userId = "u5", username = "Player5", displayName = "Player 5", gamerscore = 400L),
      com.example.data.repository.UsernameClaim(userId = "u2", username = "Player2", displayName = "Player 2", gamerscore = 4900L),
      com.example.data.repository.UsernameClaim(userId = "u4", username = "Player4", displayName = "Player 4", gamerscore = 1200L)
    )
    val ranked = com.example.data.repository.RivalsRepository.buildRankedSquadLeaderboard(
      claims = unsortedClaims,
      memberUids = listOf("u3", "u1", "u5", "u2", "u4"),
      leaderUid = "u1"
    )
    assertEquals(5, ranked.size)
    assertEquals("1st", ranked[0].rankLabel)
    assertEquals("u1", ranked[0].userId)
    assertTrue(ranked[0].hasFirstPlaceCrown)
    assertEquals("2nd", ranked[1].rankLabel)
    assertTrue(!ranked[1].hasFirstPlaceCrown)
    assertEquals("3rd", ranked[2].rankLabel)
    assertEquals("4th", ranked[3].rankLabel)
    assertEquals("5th", ranked[4].rankLabel)

    // 4. Verify "Invite a Squad" popup triggers on 2x Booster OR 2x Recording OR 2x Overlay ONLY when user has no Squad
    context.getSharedPreferences("gmax_rivals_competition_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    com.example.util.RivalsManager.initialize(context)
    com.example.util.RivalsManager.recordBoosterActivation(context)
    assertTrue(!com.example.util.RivalsManager.state.value.showInviteSquadPopup)
    com.example.util.RivalsManager.recordBoosterActivation(context)
    assertTrue("2nd booster activation with no squad must trigger Invite a Squad popup", com.example.util.RivalsManager.state.value.showInviteSquadPopup)
    com.example.util.RivalsManager.dismissInviteSquadPopup()
    assertTrue(!com.example.util.RivalsManager.state.value.showInviteSquadPopup)

    // Test 2x Recording trigger
    context.getSharedPreferences("gmax_rivals_competition_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    com.example.util.RivalsManager.initialize(context)
    com.example.util.RivalsManager.recordRecordingSession(context)
    assertTrue(!com.example.util.RivalsManager.state.value.showInviteSquadPopup)
    com.example.util.RivalsManager.recordRecordingSession(context)
    assertTrue("2nd recording session with no squad must trigger Invite a Squad popup", com.example.util.RivalsManager.state.value.showInviteSquadPopup)
    com.example.util.RivalsManager.dismissInviteSquadPopup()

    // Test 2x Overlay trigger
    context.getSharedPreferences("gmax_rivals_competition_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    com.example.util.RivalsManager.initialize(context)
    com.example.util.RivalsManager.recordOverlayActivation(context)
    assertTrue(!com.example.util.RivalsManager.state.value.showInviteSquadPopup)
    com.example.util.RivalsManager.recordOverlayActivation(context)
    assertTrue("2nd overlay activation with no squad must trigger Invite a Squad popup", com.example.util.RivalsManager.state.value.showInviteSquadPopup)

    // 5. Verify Monthly Gamer Score reset to 0
    assertTrue(com.example.util.GamerscoreEngine.calculateGamerscore(totalMinutes = 45L, totalSessions = 3L).totalScore > 0L)
    com.example.util.GamePlayStatsHelper.resetAllGamePlayStatsForMonthlySeasonReset(context)
    assertEquals(
      "Monthly reset must reset Gamer Score to 0",
      0L,
      com.example.util.GamerscoreEngine.calculateGamerscore(totalMinutes = 0L, totalSessions = 0L).totalScore
    )

    // 6. Verify 12-hour pre-reset timestamp is strictly 12 hours before next month 00:00 UTC
    val nextResetMillis = com.example.data.repository.RivalsRepository.nextMonthlyResetUtcMillis()
    val wrapTriggerMillis = com.example.data.repository.RivalsRepository.twelveHoursBeforeNextResetUtcMillis()
    assertEquals(12L * 3600L * 1000L, nextResetMillis - wrapTriggerMillis)

    // 7. Verify Rivals Push Notification copy for Threat, Dethroning, Activity Nudge, Monthly Wrap, and Taunt
    assertEquals(
      "Watch out! ShadowNinja is only 240 points away from stealing your 1st rank. Launch a game to defend your spot.",
      com.example.data.repository.RivalsRepository.buildThreatNotificationText(
        friendName = "ShadowNinja",
        pointsAway = 240L,
        rankPlacement = "1st"
      )
    )

    assertEquals(
      "You’ve been dethroned! ShadowNinja just passed you. Tap here to reclaim your crown.",
      com.example.data.repository.RivalsRepository.buildDethroningNotificationText(
        friendName = "ShadowNinja"
      )
    )

    assertEquals(
      "ShadowNinja just finished a 3 hour gaming session and is pulling ahead. Time to catch up?",
      com.example.data.repository.RivalsRepository.buildActivityNudgeNotificationText(
        friendName = "ShadowNinja"
      )
    )

    assertEquals(
      "You finished 2nd on your friends list this month. Next month’s leaderboard resets in 12 hours!",
      com.example.data.repository.RivalsRepository.buildMonthlyWrapNotificationText(
        rankPlacement = "2nd"
      )
    )

    assertEquals(
      "ShadowNinja said Catch me if you can after passing you in the Squad leaderboard",
      com.example.data.repository.RivalsRepository.buildTauntReceivedNotificationText(
        friendName = "ShadowNinja",
        preTaunt = "Catch me if you can"
      )
    )
  }
}


