package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TitanColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFF80F0FF),
    secondary = ElectricCrimson,
    onSecondary = Color(0xFF3B000F),
    secondaryContainer = Color(0xFF6B0E23),
    onSecondaryContainer = Color(0xFFFFB3C1),
    tertiary = SolarGold,
    onTertiary = Color(0xFF332000),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
    error = ElectricCrimson,
    onError = Color.White
)

@Composable
fun TitanBoosterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TitanColorScheme,
        typography = Typography,
        content = content
    )
}
