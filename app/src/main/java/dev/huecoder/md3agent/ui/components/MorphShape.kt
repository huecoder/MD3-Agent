package dev.huecoder.md3agent.ui.components

import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

class MorphShape(private val morph: Morph, private val progress: Float, private val rotationDegrees: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = morph.toPath(progress)
        val matrix = Matrix().apply {
            scale(size.width, size.height)
            if (rotationDegrees != 0f) {
                translate(CENTER, CENTER)
                rotateZ(rotationDegrees)
                translate(-CENTER, -CENTER)
            }
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }

    private companion object {
        const val CENTER = 0.5f
    }
}
