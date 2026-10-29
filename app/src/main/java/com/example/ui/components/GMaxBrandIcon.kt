package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Renders the official G-Max Neon Hexagonal "G" + Controller Brand Icon (@drawable/gmax_brand_icon)
 * across the entire app (Sign-In, Console Boot, Launcher, Onboarding, Profile, and Controller badges).
 */
@Composable
fun GMaxBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    cornerRadius: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    showGlowBorder: Boolean = false,
    showNeonBorder: Boolean = showGlowBorder,
    glowColor: Color = Color(0xFF00E5FF),
    borderColor: Color = glowColor,
    borderWidth: Dp = 1.5.dp,
    elevation: Dp = 0.dp,
    innerPadding: Dp = 0.dp,
    contentDescription: String? = "G-Max Gaming Hub"
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        ambientColor = Color(0xFF00E5FF),
                        spotColor = Color(0xFF2979FF)
                    )
                } else Modifier
            )
            .clip(shape)
            .background(Color(0xFF060A14))
            .then(
                if (showNeonBorder || showGlowBorder) {
                    Modifier.border(
                        width = borderWidth,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                borderColor,
                                Color(0xFF2979FF)
                            )
                        ),
                        shape = shape
                    )
                } else Modifier
            )
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.gmax_brand_icon),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Compact circular G-Max Controller emblem for inline badges, rows, and headers.
 */
@Composable
fun GMaxControllerBadge(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    accentColor: Color = Color(0xFF00E5FF),
    showBorder: Boolean = true,
    contentDescription: String? = null
) {
    GMaxBrandIcon(
        modifier = modifier,
        size = size,
        shape = CircleShape,
        showGlowBorder = showBorder,
        glowColor = accentColor.copy(alpha = 0.75f),
        borderWidth = 1.dp,
        contentDescription = contentDescription
    )
}
