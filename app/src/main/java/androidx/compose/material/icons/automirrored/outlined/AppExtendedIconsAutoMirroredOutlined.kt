package androidx.compose.material.icons.automirrored.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
private var _automirrored_outlined_HelpOutline: ImageVector? = null
val Icons.AutoMirrored.Outlined.HelpOutline: ImageVector
    get() {
        _automirrored_outlined_HelpOutline?.let { return it }
        val vector = ImageVector.Builder(
            name = "AutoMirrored.Outlined.HelpOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.0f, 18.0f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2f)
                close()
                moveTo(12.0f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2f, 12.0f, 2f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.41f, 0f, -8.0f, -3.59f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
                close()
                moveTo(12.0f, 6.0f)
                curveToRelative(-2.21f, 0f, -4.0f, 1.79f, -4.0f, 4.0f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -1.1f, 0.9f, -2.0f, 2f, -2.0f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                curveToRelative(0f, 2f, -3.0f, 1.75f, -3.0f, 5.0f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -2.25f, 3.0f, -2.5f, 3.0f, -5.0f)
                curveToRelative(0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                close()
            }
        }.build()
        _automirrored_outlined_HelpOutline = vector
        return vector
    }

