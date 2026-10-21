package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import dev.huecoder.md3agent.data.settings.AgentShape
import kotlin.math.floor

enum class SceneColor { PRIMARY, SECONDARY, TERTIARY }

class Scenery(val shape: AgentShape, val center: Offset, val size: Float, val turn: Float, val parallax: Float)

class ScenePose(
    val intensity: Float,
    val topColor: SceneColor,
    val bottomColor: SceneColor,
    val scenery: List<Scenery>,
)

class PoseBlend(val from: ScenePose, val to: ScenePose, val fraction: Float) {
    val intensity: Float get() = lerp(from.intensity, to.intensity, fraction)
}

object ScenePlan {
    val topShape = AgentShape.COOKIE_9
    val bottomShapes = listOf(AgentShape.COOKIE_6, AgentShape.COOKIE_7, AgentShape.COOKIE_4)

    private class Slot(val center: Offset, val size: Float, val parallax: Float)

    private val sceneryShapes = listOf(AgentShape.COOKIE_4, AgentShape.COOKIE_7, AgentShape.COOKIE_9, AgentShape.COOKIE_6)

    private val layouts = listOf(
        listOf(Slot(Offset(0.80f, 0.22f), 0.50f, FAR), Slot(Offset(0.14f, 0.70f), 0.38f, NEAR)),
        listOf(Slot(Offset(0.22f, 0.18f), 0.44f, NEAR), Slot(Offset(0.84f, 0.64f), 0.56f, FAR)),
        listOf(Slot(Offset(0.52f, 0.36f), 0.62f, FAR), Slot(Offset(0.08f, 0.52f), 0.34f, NEAR)),
    )

    fun pose(step: Int, calm: Boolean): ScenePose = ScenePose(
        intensity = if (calm) CALM_INTENSITY else FULL_INTENSITY,
        topColor = TOP_COLORS[step.mod(TOP_COLORS.size)],
        bottomColor = BOTTOM_COLORS[step.mod(BOTTOM_COLORS.size)],
        scenery = layouts[step.mod(layouts.size)].mapIndexed { index, slot ->
            val number = step * layouts.first().size + index
            Scenery(sceneryShapes[number.mod(sceneryShapes.size)], slot.center, slot.size, number * TURN_PER_SCENERY, slot.parallax)
        },
    )

    fun blend(poses: List<ScenePose>, position: Float): PoseBlend {
        val last = poses.lastIndex
        val clamped = position.coerceIn(0f, last.toFloat())
        val from = floor(clamped).toInt().coerceAtMost(last)
        val to = (from + 1).coerceAtMost(last)
        return PoseBlend(poses[from], poses[to], clamped - from)
    }

    private const val FAR = 0.3f
    private const val NEAR = 0.6f
    private const val TURN_PER_SCENERY = 0.9f
    private const val CALM_INTENSITY = 0.4f
    private const val FULL_INTENSITY = 1f
    private val TOP_COLORS = listOf(SceneColor.PRIMARY, SceneColor.TERTIARY, SceneColor.SECONDARY)
    private val BOTTOM_COLORS = listOf(SceneColor.TERTIARY, SceneColor.SECONDARY, SceneColor.PRIMARY)
}
