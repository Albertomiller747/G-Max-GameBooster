package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.currentAppLanguage

/**
 * Adaptive scaling utility to automatically scale fonts, cards, and icons to fit
 * both the device screen width and the active language's character density.
 * Baseline standard Android viewport width is 392.dp.
 */
object AdaptiveScaling {

    @Composable
    fun getScaleFactor(): Float {
        val config = LocalConfiguration.current
        val widthDp = config.screenWidthDp.coerceAtLeast(320)
        // Baseline 392dp, clamped safely to prevent over-shrinking or over-expansion
        return (widthDp / 392f).coerceIn(0.86f, 1.12f)
    }

    @Composable
    fun getEffectiveFontScale(activeLanguage: AppLanguage = currentAppLanguage()): Float {
        val config = LocalConfiguration.current
        val widthDp = config.screenWidthDp.coerceAtLeast(320)
        val viewportFactor = (widthDp / 392f).coerceIn(0.88f, 1.04f)
        val systemFontScale = config.fontScale.coerceIn(0.85f, 1.06f)
        val languageFactor = if (AppStrings.isVerboseLanguage(activeLanguage)) 0.94f else 0.98f
        return (viewportFactor * systemFontScale * languageFactor).coerceIn(0.82f, 1.04f)
    }

    @Composable
    fun scaleDp(value: Float): Dp {
        val factor = getScaleFactor()
        return (value * factor).dp
    }

    @Composable
    fun scaleSp(value: Float): TextUnit {
        val factor = getEffectiveFontScale()
        return (value * factor).sp
    }
}

@Composable
fun Float.scaledDp(): Dp = AdaptiveScaling.scaleDp(this)

@Composable
fun Int.scaledDp(): Dp = AdaptiveScaling.scaleDp(this.toFloat())

@Composable
fun Float.scaledSp(): TextUnit = AdaptiveScaling.scaleSp(this)

@Composable
fun Int.scaledSp(): TextUnit = AdaptiveScaling.scaleSp(this.toFloat())
