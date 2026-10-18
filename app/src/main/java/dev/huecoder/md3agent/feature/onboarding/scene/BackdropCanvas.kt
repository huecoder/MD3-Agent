package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2

@Composable
fun BackdropCanvas(scene: BackdropScene, poses: List<ScenePose>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors) { ScenePalette(colors.primary, colors.secondary, colors.tertiary) }
    val density = LocalDensity.current
    val topStroke = with(density) { SceneMotion.TOP_STROKE_DP.dp.toPx() }
    val bottomStroke = with(density) { SceneMotion.BOTTOM_STROKE_DP.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val topPath = remember(scene) { scene.morph(ScenePlan.topShape, ScenePlan.topShape).toPath(0f) }
    val bottomPath = remember { Path() }

    LaunchedEffect(size) {
        if (size.width > 0 && size.height > 0) scene.prepare(SceneStage(size.width.toFloat(), size.height.toFloat()), poses.first().bottomShape)
    }

    Canvas(modifier.onSizeChanged { size = it }) {
        val stage = scene.stage ?: return@Canvas
        val blend = ScenePlan.blend(poses, scene.position)
        drawBottom(scene, stage, blend, palette, bottomPath, bottomStroke)
        drawTop(scene, stage, blend, palette, topPath, topStroke)
    }
}

class ScenePalette(private val primary: Color, private val secondary: Color, private val tertiary: Color) {
    fun of(color: SceneColor): Color = when (color) {
        SceneColor.PRIMARY -> primary
        SceneColor.SECONDARY -> secondary
        SceneColor.TERTIARY -> tertiary
    }
}

private fun DrawScope.drawBottom(scene: BackdropScene, stage: SceneStage, blend: PoseBlend, palette: ScenePalette, path: Path, strokeWidth: Float) {
    scene.morph(blend.from.bottomShape, blend.to.bottomShape).toPath(blend.fraction, path)
    val color = lerp(palette.of(blend.from.bottomColor), palette.of(blend.to.bottomColor), blend.fraction)
    val recoil = scene.hitAxis * (scene.squash * SceneMotion.BOTTOM_RECOIL_SHARE * stage.base)
    withTransform({
        translate(stage.bottomCenter.x + recoil.x, stage.bottomCenter.y + recoil.y)
        rotate(Math.toDegrees(blend.bottomTurn.toDouble()).toFloat(), Offset.Zero)
        scale(stage.bottomSize, stage.bottomSize, Offset.Zero)
        translate(-CENTER, -CENTER)
    }) {
        drawPath(path, color.copy(alpha = SceneMotion.BOTTOM_ALPHA * blend.intensity), style = outline(strokeWidth / stage.bottomSize))
    }
}

private fun DrawScope.drawTop(scene: BackdropScene, stage: SceneStage, blend: PoseBlend, palette: ScenePalette, path: Path, strokeWidth: Float) {
    val color = lerp(palette.of(blend.from.topColor), palette.of(blend.to.topColor), blend.fraction)
    val axisDegrees = Math.toDegrees(atan2(scene.hitAxis.y.toDouble(), scene.hitAxis.x.toDouble())).toFloat()
    withTransform({
        translate(scene.hitPoint.x, scene.hitPoint.y)
        rotate(axisDegrees, Offset.Zero)
        scale(1f - scene.squash, 1f + scene.squash * SceneMotion.SQUASH_BULGE, Offset.Zero)
        rotate(-axisDegrees, Offset.Zero)
        translate(-scene.hitPoint.x, -scene.hitPoint.y)
        translate(scene.topX, scene.topY)
        rotate(Math.toDegrees(scene.topAngle.toDouble()).toFloat(), Offset.Zero)
        scale(stage.topSize, stage.topSize, Offset.Zero)
        translate(-CENTER, -CENTER)
    }) {
        drawPath(path, color.copy(alpha = blend.intensity), style = outline(strokeWidth / stage.topSize))
    }
}

private fun outline(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private const val CENTER = 0.5f
