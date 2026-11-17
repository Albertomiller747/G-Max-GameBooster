package com.example.util

import android.content.Context
import android.util.Base64
import com.example.model.ControllerLayoutMode
import com.example.model.ControllerOverlayConfig
import com.example.model.OverlayHudConfig
import com.example.model.OverlaySettings
import com.example.model.StickTrackingMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class TrafficHealthState {
    CHOKING,
    HEALTHY,
    HEAVY_SPIKE
}

data class DecodedConfigPayload(
    val version: Int = 2,
    val rawJson: String,
    val creatorUidHash: String = "LOCAL001",
    val creatorHandle: String = "@guest_gamer",
    val signatureHex: String = "",
    val signatureVerified: Boolean = true,
    val bundledSettingsCount: Int = 36,
    val crosshairSettings: OverlaySettings,
    val hudConfig: OverlayHudConfig,
    val hudPositionX: Int,
    val hudPositionY: Int,
    val controllerConfig: ControllerOverlayConfig,
    val controllerLayoutMode: ControllerLayoutMode,
    val lightbarColorHex: String = "#00E5FF",
    val volumeLockEnabled: Boolean = false,
    val volumeLockPercent: Int = 80,
    val hardcoreModeEnabled: Boolean = false,
    val recorderResolutionLabel: String = "1080p FHD",
    val recorderFps: Int = 60,
    val recorderBitrateMbps: Int = 24,
    val recorderMicEnabled: Boolean = true,
    val recorderBubbleSizeDp: Int = 42,
    val instantReplay30sEnabled: Boolean = true,
    val boostModeName: String = "GAMING",
    val boostTimerActive: Boolean = false,
    val appliedOptionIds: Set<String> = emptySet(),
    val speedrunTimerCategory: String = "Any%",
    val speedrunTamperLockEnabled: Boolean = true,
    val summaryLabelEn: String,
    val summaryLabelEs: String
)

/**
 * v2 Creator-Signed Serializer & Compressed Base64 Codec for G-Max Account-Unique HUD, Crosshair,
 * Gameplay Protection, Recorder/30s Replay, and Performance Tweak Loadouts.
 *
 * - Embeds a deterministic 8-hex account fingerprint (`uid`) + `@creatorHandle` + 8-hex SHA-256
 *   signature (`sig`) so every account produces a cryptographically unique `GMAX-...` Base64 code
 *   even when slider values are identical.
 * - Isolates HUD positions and saved config codes per account (`accountKey`).
 * - Bundles 36+ competitive settings across:
 *   1. Crosshair + Fine-Tuning (style, color, size, alpha, X/Y offset, stroke width, center gap, outline, center dot)
 *   2. Floating HUD + X/Y coordinates + FPS sampling + Target Cap + Net Speed
 *   3. Controller Overlay (scale, opacity, X/Y, layout mode, stick mode, highlight & RGB lightbar)
 *   4. Gameplay Protection (Gaming Volume Lock + Locked %, Hardcore Call-Blocker Mode)
 *   5. Screen Recorder & Last 30s Instant Replay Buffer
 *   6. Boost Mode + Applied Performance Tweak IDs + Speedrun Anti-Cheat Timer preferences
 */
object ConfigCodeCodec {

    const val CODE_PREFIX = "GMAX-"
    private const val HUD_PREFS_NAME = "gmax_hud_prefs"
    private const val KEY_ACTIVE_ACCOUNT = "active_account_key"
    const val KEY_HUD_POS_X = "hud_pos_x"
    const val KEY_HUD_POS_Y = "hud_pos_y"
    const val KEY_HUD_SHOW_NET_SPEED = "hud_show_net_speed"
    private const val KEY_LAST_CONFIG_CODE = "account_last_gmax_code"

    fun sanitizeAccountKey(accountKey: String?): String {
        val raw = accountKey.orEmpty().trim()
        return raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "guest_local" }
    }

    fun setActiveAccount(context: Context, accountKey: String) {
        val sanitized = sanitizeAccountKey(accountKey)
        context.getSharedPreferences(HUD_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_ACCOUNT, sanitized)
            .apply()
    }

    fun getActiveAccount(context: Context): String {
        val prefs = context.getSharedPreferences(HUD_PREFS_NAME, Context.MODE_PRIVATE)
        return sanitizeAccountKey(prefs.getString(KEY_ACTIVE_ACCOUNT, "guest_local"))
    }

    fun getSavedHudPosition(context: Context, accountKeyOverride: String? = null): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(HUD_PREFS_NAME, Context.MODE_PRIVATE)
        val density = context.resources.displayMetrics.density
        val defaultX = (20 * density).toInt()
        val defaultY = (60 * density).toInt()
        val acct = sanitizeAccountKey(accountKeyOverride ?: getActiveAccount(context))
        val sfx = "_$acct"
        val x = if (prefs.contains("$KEY_HUD_POS_X$sfx")) {
            prefs.getInt("$KEY_HUD_POS_X$sfx", defaultX)
        } else {
            prefs.getInt(KEY_HUD_POS_X, defaultX)
        }
        val y = if (prefs.contains("$KEY_HUD_POS_Y$sfx")) {
            prefs.getInt("$KEY_HUD_POS_Y$sfx", defaultY)
        } else {
            prefs.getInt(KEY_HUD_POS_Y, defaultY)
        }
        return x to y
    }

    fun saveHudPosition(context: Context, x: Int, y: Int, accountKeyOverride: String? = null) {
        val acct = sanitizeAccountKey(accountKeyOverride ?: getActiveAccount(context))
        val sfx = "_$acct"
        context.getSharedPreferences(HUD_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HUD_POS_X, x)
            .putInt(KEY_HUD_POS_Y, y)
            .putInt("$KEY_HUD_POS_X$sfx", x)
            .putInt("$KEY_HUD_POS_Y$sfx", y)
            .apply()
    }

    fun saveAccountConfigCode(context: Context, accountKey: String, code: String) {
        val acct = sanitizeAccountKey(accountKey)
        context.getSharedPreferences(HUD_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("${KEY_LAST_CONFIG_CODE}_$acct", code)
            .apply()
    }

    fun getSavedAccountConfigCode(context: Context, accountKey: String): String? {
        val acct = sanitizeAccountKey(accountKey)
        return context.getSharedPreferences(HUD_PREFS_NAME, Context.MODE_PRIVATE)
            .getString("${KEY_LAST_CONFIG_CODE}_$acct", null)
    }

    /**
     * Computes a deterministic 8-hex uppercase account fingerprint from [accountKey].
     */
    fun computeAccountUidHash(accountKey: String): String {
        val normalized = accountKey.trim().ifBlank { "guest_local" }
        return sha256Hex("GMAX-ACCT-SALT::$normalized").take(8).uppercase(Locale.US)
    }

    /**
     * Computes a deterministic 8-hex creator signature binding the account fingerprint and loadout settings.
     */
    fun computePayloadSignature(
        uidHash: String,
        creatorHandle: String,
        crosshairSummary: String,
        hudSummary: String,
        protectionSummary: String,
        recorderSummary: String,
        tweaksSummary: String
    ): String {
        val canonical = "GMAX-SIG-V2|$uidHash|${creatorHandle.lowercase(Locale.US)}|$crosshairSummary|$hudSummary|$protectionSummary|$recorderSummary|$tweaksSummary"
        return sha256Hex(canonical).take(8).uppercase(Locale.US)
    }

    private fun sha256Hex(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toUInt().toString(16).padStart(8, '0')
        }
    }

    /**
     * Builds the canonical v2 JSON string representing the user's account-signed HUD layout,
     * fine-tuned Tactical Crosshair, Controller Overlay, Volume Lock & Hardcore Mode,
     * Recorder & 30s Replay Buffer, and Performance Tweak loadout.
     */
    fun toJsonString(
        crosshair: OverlaySettings,
        hud: OverlayHudConfig,
        hudX: Int,
        hudY: Int,
        controllerConfig: ControllerOverlayConfig = ControllerOverlayConfig(),
        controllerLayoutMode: ControllerLayoutMode = ControllerLayoutMode.AUTO,
        accountKey: String = "guest_local",
        creatorHandle: String = "guest_gamer",
        lightbarColorHex: String = "#00E5FF",
        volumeLockEnabled: Boolean = false,
        volumeLockPercent: Int = 80,
        hardcoreModeEnabled: Boolean = false,
        recorderResolutionLabel: String = "1080p FHD",
        recorderFps: Int = 60,
        recorderBitrateMbps: Int = 24,
        recorderMicEnabled: Boolean = true,
        recorderBubbleSizeDp: Int = 42,
        instantReplay30sEnabled: Boolean = true,
        boostModeName: String = "GAMING",
        boostTimerActive: Boolean = false,
        appliedOptionIds: Set<String> = emptySet(),
        speedrunTimerCategory: String = "Any%",
        speedrunTamperLockEnabled: Boolean = true,
        includeCrosshair: Boolean = false
    ): String {
        val root = JSONObject()
        root.put("v", 2)

        val cleanHandle = creatorHandle.trim().removePrefix("@").ifBlank { "guest_gamer" }
        val formattedHandle = "@$cleanHandle"
        val uidHash = computeAccountUidHash(accountKey)

        // 1. Crosshair block (omitted in new Config Codes when includeCrosshair == false)
        val chStyle = crosshair.crosshairStyle.coerceIn(0, 7)
        val chColorHex = String.format(Locale.US, "#%08X", crosshair.crosshairColor)
        val chSize = crosshair.crosshairSizeDp.coerceIn(8, 100)
        val chAlpha = ((crosshair.crosshairAlpha.coerceIn(0.2f, 1.0f) * 100).toInt()) / 100.0
        val chOx = crosshair.offsetXdp.coerceIn(-24, 24)
        val chOy = crosshair.offsetYdp.coerceIn(-24, 24)
        val chStroke = ((crosshair.strokeThicknessDp.coerceIn(1.0f, 6.0f) * 10).toInt()) / 10.0
        val chGap = ((crosshair.centerGapDp.coerceIn(0.0f, 16.0f) * 10).toInt()) / 10.0

        if (includeCrosshair) {
            val ch = JSONObject().apply {
                put("en", crosshair.isEnabled)
                put("st", chStyle)
                put("cl", chColorHex)
                put("sz", chSize)
                put("al", chAlpha)
                put("ox", chOx)
                put("oy", chOy)
                put("sw", chStroke)
                put("gp", chGap)
                put("ol", crosshair.showOutline)
                put("dot", crosshair.showCenterDot)
            }
            root.put("ch", ch)
        }

        // 2. HUD object (13 settings)
        val hd = JSONObject().apply {
            put("en", hud.isEnabled)
            put("fps", hud.showFps)
            put("tmp", hud.showTemp)
            put("bat", hud.showBattery)
            put("tmr", hud.showTimer)
            put("ram", hud.showRamPct)
            put("clk", hud.showClock)
            put("net", hud.showNetSpeed)
            put("asc", hud.autoScaleSmallScreens)
            put("fmd", hud.fpsMode)
            put("cap", hud.targetCapMode)
            put("x", hudX)
            put("y", hudY)
        }
        root.put("hud", hd)

        // 3. Controller Overlay + RGB Lightbar object (9 settings)
        val ct = JSONObject().apply {
            put("en", controllerConfig.isEnabled)
            put("sc", ((controllerConfig.scaleFactor.coerceIn(0.55f, 1.65f) * 100).toInt()) / 100.0)
            put("op", ((controllerConfig.opacity.coerceIn(0.25f, 1.0f) * 100).toInt()) / 100.0)
            put("x", controllerConfig.positionX)
            put("y", controllerConfig.positionY)
            put("ly", controllerLayoutMode.name)
            put("stk", controllerConfig.stickTrackingMode.name)
            put("col", String.format(Locale.US, "#%08X", controllerConfig.highlightColorArgb))
            put("rgb", lightbarColorHex.ifBlank { "#00E5FF" })
        }
        root.put("ctrl", ct)

        // 4. Gameplay Protection: Gaming Volume Lock & Hardcore Mode + Speedrun Security (5 settings)
        val safeVolPct = volumeLockPercent.coerceIn(10, 100)
        val prot = JSONObject().apply {
            put("vlock", volumeLockEnabled)
            put("vpct", safeVolPct)
            put("hc", hardcoreModeEnabled)
            put("srcat", speedrunTimerCategory.ifBlank { "Any%" })
            put("srtlk", speedrunTamperLockEnabled)
        }
        root.put("prot", prot)

        // 5. Screen Recorder & Last 30s Instant Replay Buffer (6 settings)
        val rec = JSONObject().apply {
            put("res", recorderResolutionLabel.ifBlank { "1080p FHD" })
            put("fps", recorderFps.coerceIn(15, 240))
            put("br", recorderBitrateMbps.coerceIn(1, 100))
            put("mic", recorderMicEnabled)
            put("bub", recorderBubbleSizeDp.coerceIn(28, 64))
            put("r30", instantReplay30sEnabled)
        }
        root.put("rec", rec)

        // 6. Performance Tweaks & Boost Profile
        val sortedTweaks = appliedOptionIds
            .filterNot { it.startsWith("streak_active:") || it.startsWith("streak_none:") }
            .sorted()
        val tweaksArr = JSONArray()
        sortedTweaks.forEach { tweaksArr.put(it) }
        val twk = JSONObject().apply {
            put("bm", boostModeName.ifBlank { "GAMING" })
            put("bta", boostTimerActive)
            put("ids", tweaksArr)
        }
        root.put("twk", twk)

        // 7. Account-Unique Creator Signature block
        val chSigPart = if (includeCrosshair) {
            "$chStyle:$chColorHex:$chSize:$chAlpha:$chOx:$chOy:$chStroke:$chGap:${crosshair.showOutline}:${crosshair.showCenterDot}"
        } else {
            "NONE"
        }
        val hudSigPart = "${hud.isEnabled}:${hud.showFps}:${hud.showNetSpeed}:${hud.fpsMode}:${hud.targetCapMode}:$hudX:$hudY"
        val protSigPart = "$volumeLockEnabled:$safeVolPct:$hardcoreModeEnabled:$speedrunTimerCategory:$speedrunTamperLockEnabled"
        val recSigPart = "$recorderResolutionLabel:$recorderFps:$recorderBitrateMbps:$instantReplay30sEnabled"
        val twkSigPart = "$boostModeName:${sortedTweaks.joinToString(",")}"
        val sigHex = computePayloadSignature(
            uidHash = uidHash,
            creatorHandle = formattedHandle,
            crosshairSummary = chSigPart,
            hudSummary = hudSigPart,
            protectionSummary = protSigPart,
            recorderSummary = recSigPart,
            tweaksSummary = twkSigPart
        )

        val acctObj = JSONObject().apply {
            put("uid", uidHash)
            put("tag", formattedHandle)
            put("sig", sigHex)
            put("cnt", (if (includeCrosshair) 36 else 26) + sortedTweaks.size)
        }
        root.put("acct", acctObj)

        return root.toString()
    }

    /**
     * Serializes the account-unique configuration into a v2 JSON string and compresses it into a `GMAX-...` Base64 code.
     */
    fun encodeConfigCode(
        crosshair: OverlaySettings,
        hud: OverlayHudConfig,
        hudX: Int,
        hudY: Int,
        controllerConfig: ControllerOverlayConfig = ControllerOverlayConfig(),
        controllerLayoutMode: ControllerLayoutMode = ControllerLayoutMode.AUTO,
        accountKey: String = "guest_local",
        creatorHandle: String = "guest_gamer",
        lightbarColorHex: String = "#00E5FF",
        volumeLockEnabled: Boolean = false,
        volumeLockPercent: Int = 80,
        hardcoreModeEnabled: Boolean = false,
        recorderResolutionLabel: String = "1080p FHD",
        recorderFps: Int = 60,
        recorderBitrateMbps: Int = 24,
        recorderMicEnabled: Boolean = true,
        recorderBubbleSizeDp: Int = 42,
        instantReplay30sEnabled: Boolean = true,
        boostModeName: String = "GAMING",
        boostTimerActive: Boolean = false,
        appliedOptionIds: Set<String> = emptySet(),
        speedrunTimerCategory: String = "Any%",
        speedrunTamperLockEnabled: Boolean = true
    ): String {
        val json = toJsonString(
            crosshair = crosshair,
            hud = hud,
            hudX = hudX,
            hudY = hudY,
            controllerConfig = controllerConfig,
            controllerLayoutMode = controllerLayoutMode,
            accountKey = accountKey,
            creatorHandle = creatorHandle,
            lightbarColorHex = lightbarColorHex,
            volumeLockEnabled = volumeLockEnabled,
            volumeLockPercent = volumeLockPercent,
            hardcoreModeEnabled = hardcoreModeEnabled,
            recorderResolutionLabel = recorderResolutionLabel,
            recorderFps = recorderFps,
            recorderBitrateMbps = recorderBitrateMbps,
            recorderMicEnabled = recorderMicEnabled,
            recorderBubbleSizeDp = recorderBubbleSizeDp,
            instantReplay30sEnabled = instantReplay30sEnabled,
            boostModeName = boostModeName,
            boostTimerActive = boostTimerActive,
            appliedOptionIds = appliedOptionIds,
            speedrunTimerCategory = speedrunTimerCategory,
            speedrunTamperLockEnabled = speedrunTamperLockEnabled
        )
        return encodeJsonToBase64Code(json)
    }

    fun encodeJsonToBase64Code(json: String): String {
        val rawBytes = json.toByteArray(StandardCharsets.UTF_8)
        val compressedBytes = try {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { gzip ->
                gzip.write(rawBytes)
            }
            bos.toByteArray()
        } catch (_: Exception) {
            rawBytes
        }
        val payloadToEncode = if (compressedBytes.size < rawBytes.size) compressedBytes else rawBytes
        val base64 = Base64.encodeToString(
            payloadToEncode,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$CODE_PREFIX$base64"
    }

    /**
     * Extracts and decodes a v1 or v2 `GMAX-...` Base64 config code (or raw JSON string) into a [DecodedConfigPayload].
     */
    fun decodeConfigCode(
        rawInput: String,
        fallbackCrosshair: OverlaySettings = OverlaySettings(),
        fallbackHud: OverlayHudConfig = OverlayHudConfig(),
        fallbackController: ControllerOverlayConfig = ControllerOverlayConfig()
    ): DecodedConfigPayload? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val jsonString = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed
        } else {
            val extractedToken = extractCodeToken(trimmed) ?: return null
            val base64Body = extractedToken.removePrefix(CODE_PREFIX).trim()
            if (base64Body.isEmpty()) return null
            val decodedBytes = try {
                Base64.decode(base64Body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } catch (_: Exception) {
                try {
                    Base64.decode(base64Body, Base64.DEFAULT)
                } catch (_: Exception) {
                    return null
                }
            }
            decompressOrString(decodedBytes) ?: return null
        }

        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("v", 1)

            // 1. Parse Crosshair + Fine-Tuning
            val chObj = root.optJSONObject("ch")
            val parsedStyle = chObj?.optInt("st", fallbackCrosshair.crosshairStyle)?.coerceIn(0, 7)
                ?: fallbackCrosshair.crosshairStyle
            val parsedColor = parseHexOrLongColor(
                chObj?.opt("cl"),
                fallbackCrosshair.crosshairColor
            )
            val parsedSize = chObj?.optInt("sz", fallbackCrosshair.crosshairSizeDp)?.coerceIn(8, 100)
                ?: fallbackCrosshair.crosshairSizeDp
            val parsedAlpha = chObj?.optDouble("al", fallbackCrosshair.crosshairAlpha.toDouble())
                ?.toFloat()
                ?.coerceIn(0.2f, 1.0f)
                ?: fallbackCrosshair.crosshairAlpha
            val parsedChEnabled = chObj?.optBoolean("en", true) ?: fallbackCrosshair.isEnabled
            val parsedOffsetX = chObj?.optInt("ox", fallbackCrosshair.offsetXdp)?.coerceIn(-24, 24)
                ?: fallbackCrosshair.offsetXdp
            val parsedOffsetY = chObj?.optInt("oy", fallbackCrosshair.offsetYdp)?.coerceIn(-24, 24)
                ?: fallbackCrosshair.offsetYdp
            val parsedStroke = chObj?.optDouble("sw", fallbackCrosshair.strokeThicknessDp.toDouble())
                ?.toFloat()
                ?.coerceIn(1.0f, 6.0f)
                ?: fallbackCrosshair.strokeThicknessDp
            val parsedGap = chObj?.optDouble("gp", fallbackCrosshair.centerGapDp.toDouble())
                ?.toFloat()
                ?.coerceIn(0.0f, 16.0f)
                ?: fallbackCrosshair.centerGapDp
            val parsedOutline = chObj?.optBoolean("ol", fallbackCrosshair.showOutline)
                ?: fallbackCrosshair.showOutline
            val parsedCenterDot = chObj?.optBoolean("dot", fallbackCrosshair.showCenterDot)
                ?: fallbackCrosshair.showCenterDot

            val decodedCrosshair = fallbackCrosshair.copy(
                isEnabled = parsedChEnabled,
                crosshairStyle = parsedStyle,
                crosshairColor = parsedColor,
                crosshairSizeDp = parsedSize,
                crosshairAlpha = parsedAlpha,
                offsetXdp = parsedOffsetX,
                offsetYdp = parsedOffsetY,
                strokeThicknessDp = parsedStroke,
                centerGapDp = parsedGap,
                showOutline = parsedOutline,
                showCenterDot = parsedCenterDot,
                antiCheatNoticeAcknowledged = true
            )

            // 2. Parse HUD
            val hudObj = root.optJSONObject("hud")
            val decodedHud = fallbackHud.copy(
                isEnabled = hudObj?.optBoolean("en", fallbackHud.isEnabled) ?: fallbackHud.isEnabled,
                showFps = hudObj?.optBoolean("fps", fallbackHud.showFps) ?: fallbackHud.showFps,
                showTemp = hudObj?.optBoolean("tmp", fallbackHud.showTemp) ?: fallbackHud.showTemp,
                showBattery = hudObj?.optBoolean("bat", fallbackHud.showBattery) ?: fallbackHud.showBattery,
                showTimer = hudObj?.optBoolean("tmr", fallbackHud.showTimer) ?: fallbackHud.showTimer,
                showRamPct = hudObj?.optBoolean("ram", fallbackHud.showRamPct) ?: fallbackHud.showRamPct,
                showClock = hudObj?.optBoolean("clk", fallbackHud.showClock) ?: fallbackHud.showClock,
                showNetSpeed = hudObj?.optBoolean("net", fallbackHud.showNetSpeed) ?: fallbackHud.showNetSpeed,
                autoScaleSmallScreens = hudObj?.optBoolean("asc", fallbackHud.autoScaleSmallScreens)
                    ?: fallbackHud.autoScaleSmallScreens,
                fpsMode = hudObj?.optString("fmd", fallbackHud.fpsMode).orEmpty().ifBlank { fallbackHud.fpsMode },
                targetCapMode = hudObj?.optString("cap", fallbackHud.targetCapMode).orEmpty()
                    .ifBlank { fallbackHud.targetCapMode }
            )
            val hudX = hudObj?.optInt("x", 40) ?: 40
            val hudY = hudObj?.optInt("y", 120) ?: 120

            // 3. Parse Controller + RGB Lightbar
            val ctrlObj = root.optJSONObject("ctrl")
            val layoutName = ctrlObj?.optString("ly", ControllerLayoutMode.AUTO.name).orEmpty()
            val parsedLayoutMode = try {
                ControllerLayoutMode.valueOf(layoutName)
            } catch (_: Exception) {
                ControllerLayoutMode.AUTO
            }
            val stickModeName = ctrlObj?.optString("stk", fallbackController.stickTrackingMode.name).orEmpty()
            val parsedStickMode = try {
                StickTrackingMode.valueOf(stickModeName)
            } catch (_: Exception) {
                fallbackController.stickTrackingMode
            }
            val parsedCtrlColor = parseHexOrLongColor(
                ctrlObj?.opt("col"),
                fallbackController.highlightColorArgb
            )
            val parsedLightbarHex = ctrlObj?.optString("rgb", "#00E5FF").orEmpty().ifBlank { "#00E5FF" }
            val decodedController = fallbackController.copy(
                isEnabled = ctrlObj?.optBoolean("en", fallbackController.isEnabled) ?: fallbackController.isEnabled,
                scaleFactor = ctrlObj?.optDouble("sc", fallbackController.scaleFactor.toDouble())
                    ?.toFloat()
                    ?.coerceIn(0.55f, 1.65f)
                    ?: fallbackController.scaleFactor,
                opacity = ctrlObj?.optDouble("op", fallbackController.opacity.toDouble())
                    ?.toFloat()
                    ?.coerceIn(0.25f, 1.0f)
                    ?: fallbackController.opacity,
                positionX = ctrlObj?.optInt("x", fallbackController.positionX) ?: fallbackController.positionX,
                positionY = ctrlObj?.optInt("y", fallbackController.positionY) ?: fallbackController.positionY,
                stickTrackingMode = parsedStickMode,
                highlightColorArgb = parsedCtrlColor
            )

            // 4. Parse Gameplay Protection (Volume Lock, Hardcore Mode, Speedrun Security)
            val protObj = root.optJSONObject("prot")
            val parsedVolLockEnabled = protObj?.optBoolean("vlock", false) ?: false
            val parsedVolLockPct = (protObj?.optInt("vpct", 80) ?: 80).coerceIn(10, 100)
            val parsedHardcoreEnabled = protObj?.optBoolean("hc", false) ?: false
            val parsedSrCat = protObj?.optString("srcat", "Any%").orEmpty().ifBlank { "Any%" }
            val parsedSrTamperLock = protObj?.optBoolean("srtlk", true) ?: true

            // 5. Parse Screen Recorder & 30s Instant Replay Buffer
            val recObj = root.optJSONObject("rec")
            val parsedRecRes = recObj?.optString("res", "1080p FHD").orEmpty().ifBlank { "1080p FHD" }
            val parsedRecFps = (recObj?.optInt("fps", 60) ?: 60).coerceIn(15, 240)
            val parsedRecBitrate = (recObj?.optInt("br", 24) ?: 24).coerceIn(1, 100)
            val parsedRecMic = recObj?.optBoolean("mic", true) ?: true
            val parsedRecBubble = (recObj?.optInt("bub", 42) ?: 42).coerceIn(28, 64)
            val parsedReplay30s = recObj?.optBoolean("r30", true) ?: true

            // 6. Parse Performance Tweaks & Boost Mode + Active Boost Timer state
            val twkObj = root.optJSONObject("twk")
            val parsedBoostMode = twkObj?.optString("bm", "GAMING").orEmpty().ifBlank { "GAMING" }
            val parsedBoostTimerActive = twkObj?.optBoolean("bta", false) ?: false
            val parsedTweaks = mutableSetOf<String>()
            val idsArr = twkObj?.optJSONArray("ids")
            if (idsArr != null) {
                for (i in 0 until idsArr.length()) {
                    val id = idsArr.optString(i).trim()
                    if (id.isNotBlank()) parsedTweaks.add(id)
                }
            }

            // 7. Parse Account Creator Metadata & Verify Signature
            val acctObj = root.optJSONObject("acct")
            val creatorUidHash = acctObj?.optString("uid", "LEGACY01").orEmpty().ifBlank { "LEGACY01" }
            val rawCreatorTag = acctObj?.optString("tag", "@player").orEmpty().ifBlank { "@player" }
            val creatorHandle = if (rawCreatorTag.startsWith("@")) rawCreatorTag else "@$rawCreatorTag"
            val providedSig = acctObj?.optString("sig", "").orEmpty()
            val bundledCount = acctObj?.optInt("cnt", 36 + parsedTweaks.size) ?: (36 + parsedTweaks.size)

            val chColorHex = String.format(Locale.US, "#%08X", parsedColor)
            val chAlphaRounded = ((parsedAlpha * 100).toInt()) / 100.0
            val chStrokeRounded = ((parsedStroke * 10).toInt()) / 10.0
            val chGapRounded = ((parsedGap * 10).toInt()) / 10.0
            val expectedSig = if (providedSig.isNotBlank()) {
                val chSummary = if (chObj != null) {
                    "$parsedStyle:$chColorHex:$parsedSize:$chAlphaRounded:$parsedOffsetX:$parsedOffsetY:$chStrokeRounded:$chGapRounded:$parsedOutline:$parsedCenterDot"
                } else {
                    "NONE"
                }
                computePayloadSignature(
                    uidHash = creatorUidHash,
                    creatorHandle = creatorHandle,
                    crosshairSummary = chSummary,
                    hudSummary = "${decodedHud.isEnabled}:${decodedHud.showFps}:${decodedHud.showNetSpeed}:${decodedHud.fpsMode}:${decodedHud.targetCapMode}:$hudX:$hudY",
                    protectionSummary = "$parsedVolLockEnabled:$parsedVolLockPct:$parsedHardcoreEnabled:$parsedSrCat:$parsedSrTamperLock",
                    recorderSummary = "$parsedRecRes:$parsedRecFps:$parsedRecBitrate:$parsedReplay30s",
                    tweaksSummary = "$parsedBoostMode:${parsedTweaks.sorted().joinToString(",")}"
                )
            } else {
                ""
            }
            val sigVerified = providedSig.isBlank() || providedSig.equals(expectedSig, ignoreCase = true)

            val styleNames = listOf(
                "Classic Cross", "Precision Dot", "Circle + Dot", "Tactical Chevron",
                "T-Pose Reticle", "Sniper Mil-Dot", "Diamond Box", "Circle + Cross"
            )
            val styleLabel = styleNames.getOrElse(parsedStyle) { "Custom" }
            val creatorBadge = if (version >= 2) "$creatorHandle (#$creatorUidHash)" else "v1 Loadout"
            val timerSuffixEn = if (parsedBoostTimerActive) " + Overlay and Focus Mode Timer Started" else ""
            val timerSuffixEs = if (parsedBoostTimerActive) " + Temporizador de Superposición y Modo Enfoque Iniciado" else ""
            val summaryEn = "Applied $creatorBadge • $styleLabel (${parsedSize}dp) + HUD @ ($hudX, $hudY) + $bundledCount Settings$timerSuffixEn"
            val summaryEs = "Aplicado $creatorBadge • $styleLabel (${parsedSize}dp) + HUD en ($hudX, $hudY) + $bundledCount Ajustes$timerSuffixEs"

            DecodedConfigPayload(
                version = version,
                rawJson = jsonString,
                creatorUidHash = creatorUidHash,
                creatorHandle = creatorHandle,
                signatureHex = providedSig,
                signatureVerified = sigVerified,
                bundledSettingsCount = bundledCount,
                crosshairSettings = decodedCrosshair,
                hudConfig = decodedHud,
                hudPositionX = hudX,
                hudPositionY = hudY,
                controllerConfig = decodedController,
                controllerLayoutMode = parsedLayoutMode,
                lightbarColorHex = parsedLightbarHex,
                volumeLockEnabled = parsedVolLockEnabled,
                volumeLockPercent = parsedVolLockPct,
                hardcoreModeEnabled = parsedHardcoreEnabled,
                recorderResolutionLabel = parsedRecRes,
                recorderFps = parsedRecFps,
                recorderBitrateMbps = parsedRecBitrate,
                recorderMicEnabled = parsedRecMic,
                recorderBubbleSizeDp = parsedRecBubble,
                instantReplay30sEnabled = parsedReplay30s,
                boostModeName = parsedBoostMode,
                boostTimerActive = parsedBoostTimerActive,
                appliedOptionIds = parsedTweaks,
                speedrunTimerCategory = parsedSrCat,
                speedrunTamperLockEnabled = parsedSrTamperLock,
                summaryLabelEn = summaryEn,
                summaryLabelEs = summaryEs
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCodeToken(input: String): String? {
        val regex = Regex("""GMAX-[A-Za-z0-9+/=_-]+""", RegexOption.IGNORE_CASE)
        val match = regex.find(input)
        if (match != null) {
            val raw = match.value
            return CODE_PREFIX + raw.substring(5)
        }
        return if (input.length >= 8 && input.all { it.isLetterOrDigit() || it in "+/=_-" }) {
            "$CODE_PREFIX$input"
        } else {
            null
        }
    }

    private fun decompressOrString(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val isGzip = bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0x1F &&
            (bytes[1].toInt() and 0xFF) == 0x8B
        if (isGzip) {
            try {
                ByteArrayInputStream(bytes).use { bis ->
                    GZIPInputStream(bis).use { gis ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(512)
                        var read: Int
                        while (gis.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                        return String(out.toByteArray(), StandardCharsets.UTF_8)
                    }
                }
            } catch (_: Exception) {}
        }
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHexOrLongColor(raw: Any?, fallback: Long): Long {
        if (raw == null) return fallback
        if (raw is Number) return raw.toLong()
        val str = raw.toString().trim()
        if (str.startsWith("#")) {
            return try {
                val hex = str.removePrefix("#")
                val parsed = hex.toULong(16).toLong()
                if (hex.length <= 6) (parsed or 0xFF000000L) else parsed
            } catch (_: Exception) {
                fallback
            }
        }
        return str.toLongOrNull() ?: fallback
    }

    /**
     * Formats a byte-per-second throughput rate (from Android's `TrafficStats`) into a compact
     * KB/s or MB/s string for the Floating Gaming HUD bar.
     */
    fun formatTrafficRate(rxBytesPerSec: Long, txBytesPerSec: Long, compact: Boolean = false): String {
        val safeRx = rxBytesPerSec.coerceAtLeast(0L)
        val safeTx = txBytesPerSec.coerceAtLeast(0L)
        val totalBytesPerSec = safeRx + safeTx
        return if (compact) {
            "⇅${formatSingleByteRate(totalBytesPerSec)}"
        } else {
            "↓${formatSingleByteRate(safeRx)} ↑${formatSingleByteRate(safeTx)}"
        }
    }

    fun formatSingleByteRate(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        return when {
            kb >= 1024.0 -> String.format(Locale.US, "%.1fMB/s", kb / 1024.0)
            kb >= 10.0 -> String.format(Locale.US, "%.0fKB/s", kb)
            kb >= 0.1 -> String.format(Locale.US, "%.1fKB/s", kb)
            else -> "0KB/s"
        }
    }

    /**
     * Classifies local `TrafficStats` throughput so players can spot connection choking (0 B/s drop)
     * or heavy background bandwidth hogging (> 1.5 MB/s) before pushing an enemy squad.
     */
    fun classifyTrafficHealth(rxBytesPerSec: Long, txBytesPerSec: Long): TrafficHealthState {
        val total = (rxBytesPerSec.coerceAtLeast(0L) + txBytesPerSec.coerceAtLeast(0L))
        return when {
            total == 0L -> TrafficHealthState.CHOKING
            total >= 1_536L * 1024L -> TrafficHealthState.HEAVY_SPIKE
            else -> TrafficHealthState.HEALTHY
        }
    }
}
