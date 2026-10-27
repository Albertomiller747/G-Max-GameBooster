package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.model.AppBackgroundTheme
import com.example.model.CustomBackgroundPreset

data class HubColorTokens(
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primaryAccent: Color = HubBlueLight,
    val launchGreen: Color = LaunchGreen,
    val recorderCoral: Color = RecorderCoral
)

val LocalHubColors = staticCompositionLocalOf {
    HubColorTokens(
        isLight = false,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        border = DarkSurfaceBorder,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textMuted = TextMuted
    )
}

val CuratedBackgroundPresets = listOf(
    CustomBackgroundPreset("Console Charcoal", 0xFF121417, 0xFF1C1F24, 0xFF2E333D, isLight = false),
    CustomBackgroundPreset("Pure Alabaster", 0xFFF8FAFC, 0xFFFFFFFF, 0xFFE2E8F0, isLight = true),
    CustomBackgroundPreset("Midnight Obsidian", 0xFF0A0A0C, 0xFF141418, 0xFF26262E, isLight = false),
    CustomBackgroundPreset("Deep Ocean Slate", 0xFF0F172A, 0xFF1E293B, 0xFF334155, isLight = false),
    CustomBackgroundPreset("Emerald Matrix", 0xFF071712, 0xFF0F2920, 0xFF1A4234, isLight = false),
    CustomBackgroundPreset("Crimson Studio", 0xFF1A0D10, 0xFF29151A, 0xFF42222A, isLight = false),
    CustomBackgroundPreset("Royal Indigo", 0xFF131124, 0xFF1E1B36, 0xFF312C54, isLight = false),
    CustomBackgroundPreset("Warm Sand Light", 0xFFFDFBF7, 0xFFFFFFFF, 0xFFE6E1D6, isLight = true)
)

fun deriveHubTokens(
    appTheme: AppBackgroundTheme,
    systemIsDark: Boolean,
    customBgArgb: Long
): HubColorTokens {
    return when (appTheme) {
        AppBackgroundTheme.AUTO_SYSTEM -> {
            if (systemIsDark) {
                HubColorTokens(
                    isLight = false,
                    background = DarkBackground,
                    surface = DarkSurface,
                    surfaceVariant = DarkSurfaceVariant,
                    border = DarkSurfaceBorder,
                    textPrimary = TextPrimary,
                    textSecondary = TextSecondary,
                    textMuted = TextMuted
                )
            } else {
                HubColorTokens(
                    isLight = true,
                    background = LightBackground,
                    surface = LightSurface,
                    surfaceVariant = LightSurfaceVariant,
                    border = LightSurfaceBorder,
                    textPrimary = LightTextPrimary,
                    textSecondary = LightTextSecondary,
                    textMuted = LightTextMuted,
                    primaryAccent = HubBlue
                )
            }
        }
        AppBackgroundTheme.LIGHT_HUB -> {
            HubColorTokens(
                isLight = true,
                background = LightBackground,
                surface = LightSurface,
                surfaceVariant = LightSurfaceVariant,
                border = LightSurfaceBorder,
                textPrimary = LightTextPrimary,
                textSecondary = LightTextSecondary,
                textMuted = LightTextMuted,
                primaryAccent = HubBlue
            )
        }
        AppBackgroundTheme.DARK_HUB -> {
            HubColorTokens(
                isLight = false,
                background = DarkBackground,
                surface = DarkSurface,
                surfaceVariant = DarkSurfaceVariant,
                border = DarkSurfaceBorder,
                textPrimary = TextPrimary,
                textSecondary = TextSecondary,
                textMuted = TextMuted
            )
        }
        AppBackgroundTheme.CUSTOM_COLOR -> {
            val bgColor = Color(customBgArgb)
            val isLightBg = bgColor.luminance() > 0.45f
            val surfaceColor = if (isLightBg) {
                Color(
                    red = (bgColor.red + 0.06f).coerceAtMost(1f),
                    green = (bgColor.green + 0.06f).coerceAtMost(1f),
                    blue = (bgColor.blue + 0.06f).coerceAtMost(1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (bgColor.red + 0.05f).coerceAtMost(1f),
                    green = (bgColor.green + 0.05f).coerceAtMost(1f),
                    blue = (bgColor.blue + 0.06f).coerceAtMost(1f),
                    alpha = 1f
                )
            }
            val variantColor = if (isLightBg) {
                Color(
                    red = (bgColor.red - 0.04f).coerceAtLeast(0f),
                    green = (bgColor.green - 0.04f).coerceAtLeast(0f),
                    blue = (bgColor.blue - 0.04f).coerceAtLeast(0f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (bgColor.red + 0.09f).coerceAtMost(1f),
                    green = (bgColor.green + 0.09f).coerceAtMost(1f),
                    blue = (bgColor.blue + 0.11f).coerceAtMost(1f),
                    alpha = 1f
                )
            }
            val borderColor = if (isLightBg) {
                Color(0xFFD5DCE6)
            } else {
                Color(
                    red = (bgColor.red + 0.15f).coerceAtMost(1f),
                    green = (bgColor.green + 0.15f).coerceAtMost(1f),
                    blue = (bgColor.blue + 0.18f).coerceAtMost(1f),
                    alpha = 1f
                )
            }
            HubColorTokens(
                isLight = isLightBg,
                background = bgColor,
                surface = surfaceColor,
                surfaceVariant = variantColor,
                border = borderColor,
                textPrimary = if (isLightBg) LightTextPrimary else TextPrimary,
                textSecondary = if (isLightBg) LightTextSecondary else TextSecondary,
                textMuted = if (isLightBg) LightTextMuted else TextMuted,
                primaryAccent = if (isLightBg) HubBlue else HubBlueLight
            )
        }
    }
}

@Composable
fun TitanBoosterTheme(
    appTheme: AppBackgroundTheme = AppBackgroundTheme.AUTO_SYSTEM,
    customBgArgb: Long = 0xFF121417L,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val tokens = deriveHubTokens(appTheme, systemIsDark, customBgArgb)

    val colorScheme = if (tokens.isLight) {
        lightColorScheme(
            primary = tokens.primaryAccent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A),
            secondary = RecorderCoral,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFEE2E2),
            onSecondaryContainer = Color(0xFF7F1D1D),
            tertiary = LaunchGreen,
            onTertiary = Color.White,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            error = RecorderCoral,
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = tokens.primaryAccent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1E3A8A),
            onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = RecorderCoral,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF7F1D1D),
            onSecondaryContainer = Color(0xFFFEE2E2),
            tertiary = LaunchGreen,
            onTertiary = Color.Black,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            error = RecorderCoral,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalHubColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
