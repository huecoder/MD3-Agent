package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.util.lerp
import dev.huecoder.md3agent.data.settings.AgentShape
import kotlin.math.floor

enum class SceneColor { PRIMARY, SECONDARY, TERTIARY }

class ScenePose(
    val bottomShape: AgentShape,
    val bottomTurn: Float,
    val intensity: Float,
    val topColor: SceneColor,
    val bottomColor: SceneColor,
)

class PoseBlend(val from: ScenePose, val to: ScenePose, val fraction: Float) {
    val bottomTurn: Float get() = lerp(from.bottomTurn, to.bottomTurn, fraction)
    val intensity: Float get() = lerp(from.intensity, to.intensity, fraction)
    val bottomShape: AgentShape get() = if (fraction < HALF) from.bottomShape else to.bottomShape

    private companion object {
        const val HALF = 0.5f
    }
}

object ScenePlan {
    val topShape = AgentShape.COOKIE_9

    val bottomShapes = listOf(
        AgentShape.COOKIE_6,
        AgentShape.COOKIE_7,
        AgentShape.COOKIE_4,
    )

    fun pose(step: Int, calm: Boolean): ScenePose = ScenePose(
        bottomShape = bottomShapes[step.mod(bottomShapes.size)],
        bottomTurn = step * TURN_PER_STEP,
        intensity = if (calm) CALM_INTENSITY else FULL_INTENSITY,
        topColor = TOP_COLORS[step.mod(TOP_COLORS.size)],
        bottomColor = BOTTOM_COLORS[step.mod(BOTTOM_COLORS.size)],
    )

    fun blend(poses: List<ScenePose>, position: Float): PoseBlend {
        val last = poses.lastIndex
        val clamped = position.coerceIn(0f, last.toFloat())
        val from = floor(clamped).toInt().coerceAtMost(last)
        val to = (from + 1).coerceAtMost(last)
        return PoseBlend(poses[from], poses[to], clamped - from)
    }

    private const val TURN_PER_STEP = 0.55f
    private const val CALM_INTENSITY = 0.45f
    private const val FULL_INTENSITY = 1f
    private val TOP_COLORS = listOf(SceneColor.PRIMARY, SceneColor.TERTIARY, SceneColor.SECONDARY)
    private val BOTTOM_COLORS = listOf(SceneColor.TERTIARY, SceneColor.SECONDARY, SceneColor.PRIMARY)
}
