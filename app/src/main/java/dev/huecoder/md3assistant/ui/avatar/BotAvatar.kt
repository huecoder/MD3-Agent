package dev.huecoder.md3assistant.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import dev.huecoder.md3assistant.data.settings.AvatarRestShape

@Composable
fun BotAvatar(
    isWorking: Boolean,
    restShape: AvatarRestShape,
    size: Dp,
    modifier: Modifier = Modifier,
    bodyColor: Color = MaterialTheme.colorScheme.primary,
    eyeColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val shapes = remember(restShape) { BotShapes.of(restShape) }
    val motion = remember(shapes) { BotMotion(shapes, startWorking = isWorking) }

    LaunchedEffect(motion, isWorking) { if (isWorking) motion.work() else motion.rest() }
    LaunchedEffect(motion) { motion.blinkForever() }

    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        rotate(motion.rotation.value) { drawBody(motion, side, bodyColor) }
        val look = motion.gaze.value
        val (left, right) = BotGeometry.eyes(BotGeometry.gazeTowards(look.x, look.y), motion.blink.value, motion.wobble, shapes.safeRadius)
        drawEye(left, side, eyeColor)
        drawEye(right, side, eyeColor)
    }
}

private fun DrawScope.drawBody(motion: BotMotion, side: Float, color: Color) {
    val path = motion.morph.toPath(motion.progress.value)
    path.transform(Matrix().apply { scale(side, side) })
    drawPath(path, color)
}

private fun DrawScope.drawEye(eye: Eye, side: Float, color: Color) {
    val outline = Path()
    eye.xs.indices.forEach { index ->
        val x = center.x + eye.xs[index] * side
        val y = center.y + eye.ys[index] * side
        if (index == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
    }
    outline.close()
    drawPath(outline, color)
}
