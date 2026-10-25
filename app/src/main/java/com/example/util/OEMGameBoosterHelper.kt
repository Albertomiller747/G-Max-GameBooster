package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object OEMGameBoosterHelper {

    data class OEMMode(
        val name: String,
        val manufacturer: String,
        val packageCandidates: List<String>,
        val intentAction: String? = null,
        val description: String
    )

    val supportedModes: List<OEMMode> = listOf(
        OEMMode(
            name = "Samsung Game Booster & Game Launcher",
            manufacturer = "samsung",
            packageCandidates = listOf(
                "com.samsung.android.game.gamehome",
                "com.samsung.android.game.gametools",
                "com.samsung.android.game.gos"
            ),
            description = "Provides Game Optimizing Service (GOS), 120Hz lock, and Touch Protection."
        ),
        OEMMode(
            name = "Xiaomi / POCO Game Turbo",
            manufacturer = "xiaomi",
            packageCandidates = listOf(
                "com.miui.gamebooster",
                "com.miui.securityadd",
                "com.xiaomi.security.center"
            ),
            description = "Unlocks GPU frequency boost, enhanced touch sampling, and anti-mistouch."
        ),
        OEMMode(
            name = "ASUS ROG / Zenfone X-Mode",
            manufacturer = "asus",
            packageCandidates = listOf(
                "com.asus.gamecenter",
                "com.asus.armourycreate"
            ),
            description = "Activates Armoury Crate thermal profile, maximum fan speeds, and CPU burst."
        ),
        OEMMode(
            name = "OnePlus / Oppo / Realme HyperBoost & GT Mode",
            manufacturer = "oneplus,oppo,realme",
            packageCandidates = listOf(
                "com.coloros.gamespace",
                "com.oplus.games",
                "com.oplus.gtmode"
            ),
            description = "Stabilizes GPU frame pacing, touch response, and disables notification banners."
        ),
        OEMMode(
            name = "Google Pixel Game Dashboard",
            manufacturer = "google",
            packageCandidates = listOf(
                "com.google.android.apps.gaming.dashboard",
                "com.google.android.apps.nexuslauncher"
            ),
            intentAction = "android.settings.GAME_SETTINGS",
            description = "Android 12+ built-in gaming overlay with Screen Record, FPS HUD, and DND."
        )
    )

    fun launchOEMMode(context: Context, mode: OEMMode): Boolean {
        val pm = context.packageManager
        for (pkg in mode.packageCandidates) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                } catch (_: Exception) {}
            }
        }

        // Try action if specified
        if (mode.intentAction != null) {
            try {
                val actionIntent = Intent(mode.intentAction)
                actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (actionIntent.resolveActivity(pm) != null) {
                    context.startActivity(actionIntent)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Fallback: Open general app manager or settings
        try {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
            Toast.makeText(context, "${mode.name} is OEM specific. Opened system Settings.", Toast.LENGTH_LONG).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open ${mode.name}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
