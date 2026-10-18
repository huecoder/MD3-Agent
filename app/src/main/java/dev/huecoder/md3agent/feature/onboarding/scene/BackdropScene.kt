package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.polygon
import dev.huecoder.md3agent.ui.physics.ShapeParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

@Stable
class BackdropScene {
    var position by mutableFloatStateOf(0f)
    var topX by mutableFloatStateOf(0f)
    var topY by mutableFloatStateOf(0f)
    var topAngle by mutableFloatStateOf(0f)
    var squash by mutableFloatStateOf(0f)
    var stage by mutableStateOf<SceneStage?>(null)
        private set

    var hitPoint = Offset.Zero
        private set

    var hitAxis = Offset.Zero
        private set

    private val paths = HashMap<AgentShape, Path>()
    private var physics: BackdropPhysics? = null
    private var squashSpeed = 0f

    fun path(shape: AgentShape): Path = paths.getOrPut(shape) { Morph(shape.polygon, shape.polygon).toPath(0f) }

    suspend fun prepare(next: SceneStage) {
        val engine = withContext(Dispatchers.Default) {
            ShapeParts.of(ScenePlan.topShape)
            BackdropPhysics(next, ScenePlan.topShape, ScenePlan.bottomShape)
        }
        physics = engine
        stage = next
        squash = 0f
        squashSpeed = 0f
        publish(engine)
    }

    suspend fun run() {
        val engine = physics ?: return
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = min((now - previous) / NANOS_PER_SECOND, MAX_FRAME_SECONDS)
            previous = now
            engine.step(seconds, ::react)
            relaxSquash(seconds)
            publish(engine)
        }
    }

    private fun react(hit: BackdropPhysics.Hit) {
        val power = (hit.metersPerSecond / SceneMotion.SQUASH_REFERENCE_SPEED).coerceIn(0f, 1f)
        if (power < SceneMotion.MIN_HIT_POWER) return
        val toward = hit.point - hit.topCenter
        val reach = toward.getDistance()
        hitPoint = hit.point
        hitAxis = if (reach > 0f) toward / reach else Offset.Zero
        squashSpeed += power * SceneMotion.SQUASH_KICK
    }

    private fun relaxSquash(seconds: Float) {
        val pull = -SceneMotion.SQUASH_STIFFNESS * squash - SceneMotion.SQUASH_DRAG * squashSpeed
        squashSpeed += pull * seconds
        squash += squashSpeed * seconds
    }

    private fun publish(engine: BackdropPhysics) {
        val center = engine.topCenter
        topX = center.x
        topY = center.y
        topAngle = engine.topAngle
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MAX_FRAME_SECONDS = 1f / 30f
    }
}
