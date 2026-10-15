package dev.huecoder.md3agent.feature.phoneuse

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import dev.huecoder.md3agent.data.settings.CursorShape

class CursorGlyph(val path: Path, val strokeWidth: Float, val tip: Offset) {
    companion object {
        fun of(shape: CursorShape, sizePx: Float): CursorGlyph = when (shape) {
            CursorShape.BIBATA_MODERN -> bibata(BIBATA_MODERN_PATH, sizePx)
            CursorShape.BIBATA_ORIGINAL -> bibata(BIBATA_ORIGINAL_PATH, sizePx)
            CursorShape.ROUNDED -> dot(sizePx)
        }

        private fun bibata(pathData: String, sizePx: Float): CursorGlyph {
            val unit = sizePx * BIBATA_CANVAS_SCALE / BIBATA_CANVAS_UNITS
            val path = PathParser().parsePathString(pathData).toPath()
            path.transform(Matrix().apply { scale(unit, unit) })
            return CursorGlyph(path, BIBATA_STROKE_UNITS * unit, BIBATA_HOTSPOT * unit)
        }

        private fun dot(size: Float): CursorGlyph {
            val radius = size * DOT_RADIUS_SHARE
            val center = Offset(radius, radius)
            return CursorGlyph(Path().apply { addOval(Rect(center, radius)) }, NO_STROKE, center)
        }
    }
}

fun DrawScope.drawCursor(at: Offset, glyph: CursorGlyph, pressScale: Float, color: Color) {
    translate(at.x - glyph.tip.x, at.y - glyph.tip.y) {
        scale(pressScale, pressScale, pivot = glyph.tip) {
            translate(SHADOW_SHIFT, SHADOW_SHIFT) { drawGlyph(glyph, Color.Black.copy(alpha = SHADOW_ALPHA)) }
            drawGlyph(glyph, color)
        }
    }
}

private fun DrawScope.drawGlyph(glyph: CursorGlyph, color: Color) {
    drawPath(glyph.path, color, style = Fill)
    if (glyph.strokeWidth > NO_STROKE) drawPath(glyph.path, color, style = Stroke(glyph.strokeWidth, join = StrokeJoin.Round))
}

fun DrawScope.drawRipple(at: Offset, progress: Float, color: Color, base: Float) {
    if (progress >= 1f) return
    drawCircle(color.copy(alpha = RIPPLE_ALPHA * (1f - progress)), radius = base * (RIPPLE_START + RIPPLE_GROWTH * progress), center = at)
}

object CursorMotion {
    const val PRESSED_SCALE = 0.72f
    const val PRESS_MS = 120
    const val RELEASE_DAMPING = 0.38f
    const val RELEASE_STIFFNESS = 520f
    const val RIPPLE_MS = 480
}

const val BIBATA_CANVAS_UNITS = 256f
const val BIBATA_STROKE_UNITS = 17f
const val BIBATA_CANVAS_SCALE = 1.35f
val BIBATA_HOTSPOT = Offset(55f, 17f)

const val BIBATA_MODERN_PATH = "M201.163 133.54L201.149 133.528L201.134 133.515L91.6855 36.4935C86.5144 31.7659 81.4269 27.9549 76.5421 25.525C71.7671 23.1497 66.0861 21.5569 60.4133 23.1213C54.3118 24.8039 50.4875 29.4674 48.3639 34.759C46.3122 39.8715 45.4999 46.2787 45.4999 53.5383L45.4999 200.431V200.493L45.5008 200.555C45.6218 208.862 50.4279 217.843 55.9963 223.894C58.8934 227.043 62.5163 229.986 66.6704 231.742C70.9172 233.537 76.217 234.254 81.4691 231.884C85.7536 229.951 89.6754 226.055 92.8565 222.651C94.6841 220.695 96.8336 218.252 99.0355 215.749C100.71 213.847 102.414 211.91 104.03 210.126C112.189 201.122 121.346 192.286 132.161 187.407C143.013 182.511 155.809 181.375 167.963 181.146C170.959 181.089 173.85 181.087 176.65 181.085H176.663H176.686C179.447 181.083 182.164 181.081 184.662 181.019C189.231 180.906 194.643 180.609 198.777 178.88C208.711 174.723 210.972 163.838 210.753 156.445C210.521 148.596 207.57 139.272 201.163 133.54Z"
const val BIBATA_ORIGINAL_PATH = "M76.6287 46.6308L62.4993 34.144L62.5 53.0003L62.5058 208L62.5066 229.287L77.1681 213.855L122.261 166.392L188.193 164.895L209.894 164.403L193.629 150.028L76.6287 46.6308Z"

private const val DOT_RADIUS_SHARE = 0.45f
private const val NO_STROKE = 0f
private const val SHADOW_SHIFT = 3f
private const val SHADOW_ALPHA = 0.22f
private const val RIPPLE_ALPHA = 0.4f
private const val RIPPLE_START = 0.3f
private const val RIPPLE_GROWTH = 1.4f
