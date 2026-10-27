package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adaptive scaling utility to automatically scale fonts and icons to fit the user device screen size.
 * Baseline standard Android viewport width is 392.dp.
 */
object AdaptiveScaling {

    @Composable
    fun getScaleFactor(): Float {
        val config = LocalConfiguration.current
        val widthDp = config.screenWidthDp
        // Baseline 392dp, clamped safely to prevent over-shrinking or over-expansion
        return (widthDp / 392f).coerceIn(0.85f, 1.25f)
    }

    @Composable
    fun scaleDp(value: Float): Dp {
        val factor = getScaleFactor()
        return (value * factor).dp
    }

    @Composable
    fun scaleSp(value: Float): TextUnit {
        val factor = getScaleFactor()
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
