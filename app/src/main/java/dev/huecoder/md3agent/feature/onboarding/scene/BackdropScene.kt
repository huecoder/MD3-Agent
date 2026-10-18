package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
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

    private val morphs = HashMap<Pair<AgentShape, AgentShape>, Morph>()
    private var physics: BackdropPhysics? = null
    private var squashSpeed = 0f

    val canStrike: Boolean get() = physics?.phase == BackdropPhysics.Phase.REST

    fun morph(from: AgentShape, to: AgentShape): Morph = morphs.getOrPut(from to to) { Morph(from.polygon, to.polygon) }

    suspend fun prepare(next: SceneStage, firstShape: AgentShape) {
        val engine = withContext(Dispatchers.Default) {
            ScenePlan.bottomShapes.forEach { ShapeParts.of(it) }
            BackdropPhysics(next, ScenePlan.topShape, firstShape)
        }
        physics = engine
        stage = next
        topX = next.home.x
        topY = next.home.y
        topAngle = 0f
    }

    suspend fun strike(poses: List<ScenePose>, onHit: (power: Float) -> Unit) {
        val engine = physics ?: return
        if (engine.phase != BackdropPhysics.Phase.REST) return
        follow(engine, poses)
        engine.strike()
        var previous = withFrameNanos { it }
        try {
            do {
                val now = withFrameNanos { it }
                val seconds = min((now - previous) / NANOS_PER_SECOND, MAX_FRAME_SECONDS)
                previous = now
                follow(engine, poses)
                val running = engine.step(seconds) { hit -> react(hit, onHit) }
                relaxSquash(seconds)
                publish(engine)
            } while (running)
        } finally {
            engine.rest()
            squash = 0f
            squashSpeed = 0f
            publish(engine)
        }
    }

    private fun follow(engine: BackdropPhysics, poses: List<ScenePose>) {
        val blend = ScenePlan.blend(poses, position)
        engine.reshapeBottom(blend.bottomShape)
        engine.turnBottom(blend.bottomTurn)
    }

    private fun react(hit: BackdropPhysics.Hit, onHit: (Float) -> Unit) {
        val power = (hit.speed / SceneMotion.SQUASH_REFERENCE_SPEED).coerceIn(0f, 1f)
        if (power < SceneMotion.MIN_HIT_POWER) return
        val toward = hit.point - hit.topCenter
        val reach = toward.getDistance()
        hitPoint = hit.point
        hitAxis = if (reach > 0f) toward / reach else Offset.Zero
        squashSpeed += power * SceneMotion.SQUASH_KICK
        onHit(power)
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
