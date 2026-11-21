package com.myvault.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultTheme

/** The handful of glyphs the app needs, so no icon library has to be bundled. */
object VaultIcons {
    val Visibility: ImageVector by lazy {
        icon("Visibility", "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z")
    }
    val VisibilityOff: ImageVector by lazy {
        icon("VisibilityOff", "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z")
    }
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }
    val Check: ImageVector by lazy {
        icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    }
    val Lock: ImageVector by lazy {
        icon("Lock", "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}

/** The MyVault mark: a shield with a keyhole. */
@Composable
fun VaultLogo(size: Dp, modifier: Modifier = Modifier) {
    val palette = VaultTheme.palette
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val brush = Brush.verticalGradient(listOf(palette.accent, palette.accentDeep))
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.05f)
            lineTo(w * 0.88f, h * 0.19f)
            lineTo(w * 0.88f, h * 0.50f)
            cubicTo(w * 0.88f, h * 0.73f, w * 0.72f, h * 0.89f, w * 0.50f, h * 0.96f)
            cubicTo(w * 0.28f, h * 0.89f, w * 0.12f, h * 0.73f, w * 0.12f, h * 0.50f)
            lineTo(w * 0.12f, h * 0.19f)
            close()
        }
        drawPath(shield, brush, alpha = 0.12f)
        drawPath(shield, brush, style = Stroke(width = w * 0.05f, join = StrokeJoin.Round))
        drawCircle(brush, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.43f))
        val stem = Path().apply {
            moveTo(w * 0.46f, h * 0.47f)
            lineTo(w * 0.54f, h * 0.47f)
            lineTo(w * 0.57f, h * 0.67f)
            lineTo(w * 0.43f, h * 0.67f)
            close()
        }
        drawPath(stem, brush)
    }
}

/** A stylised fingerprint made of concentric ridges. */
@Composable
fun FingerprintGlyph(size: Dp, modifier: Modifier = Modifier) {
    val palette = VaultTheme.palette
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        val brush = Brush.verticalGradient(listOf(palette.accent, palette.accentDeep))
        val centre = Offset(w * 0.5f, w * 0.54f)
        for (ring in 0 until 4) {
            val radius = w * (0.11f + ring * 0.105f)
            val gap = 42f - ring * 6f
            drawArc(
                brush = brush,
                startAngle = 90f + gap + ring * 14f,
                sweepAngle = 360f - 2 * gap - ring * 10f,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
        drawLine(brush, Offset(centre.x, centre.y - w * 0.02f), Offset(centre.x, centre.y + w * 0.10f), stroke.width, StrokeCap.Round)
    }
}
