package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

class SceneStage(val width: Float, val height: Float) {
    val base: Float = min(width, height * SceneMotion.BASE_HEIGHT_SHARE)
    val topSize: Float = base * SceneMotion.TOP_SIZE
    val bottomSize: Float = base * SceneMotion.BOTTOM_SIZE
    val home = Offset(width * SceneMotion.TOP_HOME.x, height * SceneMotion.TOP_HOME.y)
    val bottomCenter = Offset(width * SceneMotion.BOTTOM_CENTER.x, height * SceneMotion.BOTTOM_CENTER.y)
}
