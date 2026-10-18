package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.physics.PhysicsWorld
import dev.huecoder.md3agent.ui.physics.ShapeParts
import kotlin.random.Random

class BackdropPhysics(private val stage: SceneStage, topShape: AgentShape, bottomShape: AgentShape, private val random: Random = Random) {
    class Hit(val point: Offset, val metersPerSecond: Float, val topCenter: Offset, val topAngle: Float)

    private val pixelsPerMeter = stage.base / SceneMotion.METERS_ACROSS_BASE
    private val world = PhysicsWorld(pixelsPerMeter, gravity = SceneMotion.GRAVITY, stepSeconds = SceneMotion.STEP_SECONDS)
    private val fall = (stage.bottomCenter - stage.home).let { it / it.getDistance() }
    private val hits = ArrayList<Hit>()
    private var quietSeconds = 0f

    private val top = world.addOutline(stage.home, ShapeParts.of(topShape), stage.topSize, SceneMotion.BounceMaterial, isBullet = true)

    val topCenter: Offset get() = world.origin(top)

    val topAngle: Float get() = world.angle(top)

    init {
        world.addOutline(stage.bottomCenter, ShapeParts.of(bottomShape), stage.bottomSize, SceneMotion.BounceMaterial, isStatic = true)
        world.pointGravity(fall)
        top.angularDamping = SceneMotion.SPIN_DAMPING
        world.onImpact { hits += Hit(it.point, it.speed / pixelsPerMeter, world.origin(top), world.angle(top)) }
    }

    fun step(seconds: Float, onHit: (Hit) -> Unit) {
        world.step(seconds)
        world.keepOnLine(top, stage.home, fall)
        val strongest = hits.maxByOrNull { it.metersPerSecond }
        hits.clear()
        strongest?.let(onHit)
        if (hasSettled(strongest, seconds)) jump()
    }

    private fun hasSettled(strongest: Hit?, seconds: Float): Boolean {
        val metersPerSecond = world.velocity(top).getDistance() / pixelsPerMeter
        quietSeconds = if (metersPerSecond < SceneMotion.QUIET_METERS_PER_SECOND) quietSeconds + seconds else 0f
        return quietSeconds >= SceneMotion.QUIET_HOLD_SECONDS || (strongest != null && strongest.metersPerSecond < SceneMotion.SETTLED_HIT_METERS_PER_SECOND)
    }

    private fun jump() {
        quietSeconds = 0f
        val spread = 1f + random.nextFloat() * 2 * SceneMotion.JUMP_SPREAD - SceneMotion.JUMP_SPREAD
        world.setVelocity(top, fall * -(SceneMotion.JUMP_METERS_PER_SECOND * spread * pixelsPerMeter))
        top.angularVelocity = (random.nextFloat() * 2 - 1) * SceneMotion.JUMP_SPIN.toDouble()
    }
}
