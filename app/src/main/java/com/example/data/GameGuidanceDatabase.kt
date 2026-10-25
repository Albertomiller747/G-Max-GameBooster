package com.example.data

import com.example.model.GameGuidance

object GameGuidanceDatabase {
    val gamesList: List<GameGuidance> = listOf(
        GameGuidance(
            title = "Genshin Impact",
            packageName = "com.miHoYo.GenshinImpact",
            genre = "Open World RPG",
            recommendedFpsCap = "45 / 60 FPS (Avoid 120 FPS on phones)",
            recommendedResolution = "Medium (Render Resolution 0.8x)",
            recommendedGraphics = "Custom: Shadows Low, Visual Effects Low, Crowd Density Low",
            antiAliasingAdvice = "SMAA (Off or TAA causes heavy GPU load and blur)",
            shadowsAdvice = "Set to Lowest. Shadow cascades cause over 35% of thermal throttling.",
            lowLatencyTip = "Choose the nearest server region (Asia/NA/EU). Disable Co-op dynamic effects.",
            thermalRisk = "High",
            suggestedAdbCommand = "cmd game mode performance com.miHoYo.GenshinImpact && cmd game downscale 0.75 com.miHoYo.GenshinImpact"
        ),
        GameGuidance(
            title = "PUBG Mobile / BGMI",
            packageName = "com.tencent.ig",
            genre = "Battle Royale",
            recommendedFpsCap = "Extreme / 90 FPS with Smooth Graphics",
            recommendedResolution = "Native 1080p (Do not upscale)",
            recommendedGraphics = "Smooth (Never choose HD/HDR for competitive matches)",
            antiAliasingAdvice = "Disable Anti-Aliasing (reduces frame drops in smoke/final circle)",
            shadowsAdvice = "Disable Shadows completely to spot enemies in grass and save 15% battery.",
            lowLatencyTip = "Disable Auto-Adjust Graphics. Select matching server region. Use 5GHz Wi-Fi.",
            thermalRisk = "Medium",
            suggestedAdbCommand = "cmd game mode performance com.tencent.ig && cmd game fps 90 com.tencent.ig"
        ),
        GameGuidance(
            title = "Call of Duty: Mobile",
            packageName = "com.activision.callofduty.shooter",
            genre = "FPS / Multiplayer",
            recommendedFpsCap = "Max / Ultra (120 FPS in MP, 90 FPS in BR)",
            recommendedResolution = "Default / Shader Pre-Load enabled",
            recommendedGraphics = "Low Graphics Quality + Max Frame Rate",
            antiAliasingAdvice = "Off to reduce rendering latency by ~8ms",
            shadowsAdvice = "Real-time Shadows OFF. Depth of Field OFF. Bloom OFF.",
            lowLatencyTip = "Run Shader Preload from Audio & Graphics menu before joining Ranked matches.",
            thermalRisk = "Medium",
            suggestedAdbCommand = "cmd game mode performance com.activision.callofduty.shooter"
        ),
        GameGuidance(
            title = "Call of Duty: Warzone Mobile",
            packageName = "com.activision.callofduty.warzone",
            genre = "Battle Royale",
            recommendedFpsCap = "60 FPS Locked (Uncapped causes severe thermal throttling)",
            recommendedResolution = "Medium Resolution Scale",
            recommendedGraphics = "Min / Low Preset",
            antiAliasingAdvice = "Low / Default",
            shadowsAdvice = "Turn off Screen Space Reflections and Ambient Occlusion.",
            lowLatencyTip = "Play with device cooling fan or case removed. Do not charge while playing.",
            thermalRisk = "High",
            suggestedAdbCommand = "cmd game mode performance com.activision.callofduty.warzone && cmd game downscale 0.7 com.activision.callofduty.warzone"
        ),
        GameGuidance(
            title = "Honkai: Star Rail",
            packageName = "com.HoYoverse.hkrpgoversea",
            genre = "Turn-Based RPG",
            recommendedFpsCap = "60 FPS (Avoid uncapped)",
            recommendedResolution = "Medium / High",
            recommendedGraphics = "Custom: Character Quality High, Environment Quality Medium",
            antiAliasingAdvice = "TAA or FXAA (Low impact in turn-based combat)",
            shadowsAdvice = "Medium. Turn off Bloom and Motion Blur.",
            lowLatencyTip = "Turn off combat camera shake for snappier battle responsiveness.",
            thermalRisk = "High",
            suggestedAdbCommand = "cmd game mode performance com.HoYoverse.hkrpgoversea"
        ),
        GameGuidance(
            title = "Mobile Legends: Bang Bang",
            packageName = "com.mobile.legends",
            genre = "MOBA",
            recommendedFpsCap = "Super / Ultra (90 or 120 FPS)",
            recommendedResolution = "High",
            recommendedGraphics = "Smooth or Medium for maximum team-fight clarity",
            antiAliasingAdvice = "Off for crisp skill shot boundaries",
            shadowsAdvice = "Shadows Off removes distraction in jungle brushes.",
            lowLatencyTip = "Enable 'Speed Mode' & 'Network Boost' (Wi-Fi + 4G dual-channel) in game settings.",
            thermalRisk = "Low",
            suggestedAdbCommand = "cmd game mode performance com.mobile.legends"
        ),
        GameGuidance(
            title = "League of Legends: Wild Rift",
            packageName = "com.riotgames.league.wildrift",
            genre = "MOBA",
            recommendedFpsCap = "90 FPS or 120 FPS stable",
            recommendedResolution = "Medium",
            recommendedGraphics = "Quality: Performance mode",
            antiAliasingAdvice = "Off",
            shadowsAdvice = "Low Shadows. Post-processing Off.",
            lowLatencyTip = "Enable Dual-Channel Communication and 60/120Hz lock in Display settings.",
            thermalRisk = "Low",
            suggestedAdbCommand = "cmd game mode performance com.riotgames.league.wildrift"
        ),
        GameGuidance(
            title = "Free Fire MAX",
            packageName = "com.dts.freefiremax",
            genre = "Battle Royale",
            recommendedFpsCap = "High FPS mode enabled",
            recommendedResolution = "High Res: Normal",
            recommendedGraphics = "Smooth graphics with High FPS setting",
            antiAliasingAdvice = "Shadows OFF and Filter Classic",
            shadowsAdvice = "Turn off Dynamic Shadows completely.",
            lowLatencyTip = "Clear in-game cache from Settings > Basic > Clear Cache.",
            thermalRisk = "Low",
            suggestedAdbCommand = "cmd game mode performance com.dts.freefiremax"
        ),
        GameGuidance(
            title = "Fortnite Mobile",
            packageName = "com.epicgames.fortnite",
            genre = "Battle Royale",
            recommendedFpsCap = "60 FPS (Avoid 90 FPS unless using active external cooling)",
            recommendedResolution = "3D Resolution 70%-75%",
            recommendedGraphics = "Low Graphics Preset",
            antiAliasingAdvice = "Low",
            shadowsAdvice = "No shadows in mobile build; ensure High Resolution Textures are cached.",
            lowLatencyTip = "Disable Low Power Mode in-game. Lock screen refresh rate to 60/90Hz.",
            thermalRisk = "High",
            suggestedAdbCommand = "cmd game mode performance com.epicgames.fortnite && cmd game downscale 0.75 com.epicgames.fortnite"
        ),
        GameGuidance(
            title = "Asphalt 9 / Legends Unite",
            packageName = "com.gameloft.android.ANMP.GloftA9HM",
            genre = "Racing",
            recommendedFpsCap = "60 FPS Mode",
            recommendedResolution = "Performance Visuals",
            recommendedGraphics = "Visual Quality: Performance",
            antiAliasingAdvice = "Off",
            shadowsAdvice = "Motion blur can be reduced for crisper cornering vision.",
            lowLatencyTip = "Use a gamepad controller for zero input lag compared to virtual steering.",
            thermalRisk = "Medium",
            suggestedAdbCommand = "cmd game mode performance com.gameloft.android.ANMP.GloftA9HM"
        ),
        GameGuidance(
            title = "Arena Breakout",
            packageName = "com.proximabeta.mf.uamo",
            genre = "Tactical Extraction Shooter",
            recommendedFpsCap = "60 FPS / 90 FPS locked",
            recommendedResolution = "Balanced",
            recommendedGraphics = "Smooth Graphics",
            antiAliasingAdvice = "Off",
            shadowsAdvice = "Shadows Low to spot enemies in dark interiors.",
            lowLatencyTip = "Use wired earphones or 2.4G audio dongle for instant directional footstep audio.",
            thermalRisk = "High",
            suggestedAdbCommand = "cmd game mode performance com.proximabeta.mf.uamo"
        )
    )
}
