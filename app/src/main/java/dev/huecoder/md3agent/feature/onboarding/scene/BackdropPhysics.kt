package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.physics.PhysicsWorld
import dev.huecoder.md3agent.ui.physics.ShapeParts
import kotlin.math.PI
import kotlin.math.abs

class BackdropPhysics(private val stage: SceneStage, topShape: AgentShape, bottomShape: AgentShape) {
    enum class Phase { REST, STRIKE, RETURN }

    class Hit(val point: Offset, val speed: Float, val topCenter: Offset, val topAngle: Float)

    private val pixelsPerMeter = stage.base / SceneMotion.METERS_ACROSS_BASE
    private val world = PhysicsWorld(pixelsPerMeter, gravity = 0.0, stepSeconds = SceneMotion.STEP_SECONDS)
    private val hits = ArrayList<Hit>()
    private var tether: PhysicsWorld.Grab? = null
    private var phaseSeconds = 0f
    private var settledSeconds = 0f

    private val top = world.addOutline(stage.home, ShapeParts.of(topShape), stage.topSize, SceneMotion.TopMaterial, isBullet = true)
    private val bottom = world.addOutline(stage.bottomCenter, ShapeParts.of(bottomShape), stage.bottomSize, SceneMotion.BottomMaterial, isStatic = true)

    var phase = Phase.REST
        private set

    var bottomShape = bottomShape
        private set

    val topCenter: Offset get() = world.origin(top)

    val topAngle: Float get() = world.angle(top)

    init {
        world.onImpact { hits += Hit(it.point, it.speed, world.origin(top), world.angle(top)) }
    }

    fun reshapeBottom(shape: AgentShape) {
        if (shape == bottomShape) return
        bottomShape = shape
        world.replaceOutline(bottom, ShapeParts.of(shape), stage.bottomSize, SceneMotion.BottomMaterial)
    }

    fun turnBottom(radians: Float) {
        world.place(bottom, stage.bottomCenter, radians.toDouble())
    }

    fun strike() {
        if (phase != Phase.REST) return
        val direction = (stage.bottomCenter - stage.home).let { it / it.getDistance() }
        world.place(top, stage.home, 0.0)
        world.setVelocity(top, direction * SceneMotion.LAUNCH_METERS_PER_SECOND * pixelsPerMeter)
        top.angularVelocity = SceneMotion.LAUNCH_SPIN.toDouble()
        enter(Phase.STRIKE)
    }

    fun step(seconds: Float, onHit: (Hit) -> Unit): Boolean {
        if (phase == Phase.REST) return false
        phaseSeconds += seconds
        if (phase == Phase.RETURN) {
            tether?.moveTo(stage.home)
            world.driveSpin(top, -wrapped(topAngle) * SceneMotion.ANGLE_GAIN, SceneMotion.MAX_SPIN_ACCELERATION)
        }
        world.step(seconds)
        hits.maxByOrNull { it.speed }?.let(onHit)
        val struck = hits.isNotEmpty()
        hits.clear()
        when {
            phase == Phase.STRIKE && (struck || phaseSeconds > SceneMotion.STRIKE_TIMEOUT_SECONDS) -> beginReturn()
            phase == Phase.RETURN -> trackSettling(seconds)
        }
        return phase != Phase.REST
    }

    fun rest() {
        tether?.release()
        tether = null
        world.place(top, stage.home, 0.0)
        world.setVelocity(top, Offset.Zero)
        top.angularVelocity = 0.0
        enter(Phase.REST)
    }

    private fun beginReturn() {
        tether = world.grab(top, world.origin(top), SceneMotion.ReturnSpring)
        enter(Phase.RETURN)
    }

    private fun trackSettling(seconds: Float) {
        val near = (topCenter - stage.home).getDistance() < stage.base * SceneMotion.SETTLE_DISTANCE_SHARE
        val slow = world.velocity(top).getDistance() / pixelsPerMeter < SceneMotion.SETTLE_METERS_PER_SECOND &&
            abs(top.angularVelocity) < SceneMotion.SETTLE_SPIN
        settledSeconds = if (near && slow) settledSeconds + seconds else 0f
        if (settledSeconds >= SceneMotion.SETTLE_HOLD_SECONDS || phaseSeconds > SceneMotion.RETURN_TIMEOUT_SECONDS) rest()
    }

    private fun enter(next: Phase) {
        phase = next
        phaseSeconds = 0f
        settledSeconds = 0f
    }

    private fun wrapped(angle: Float): Double {
        val turn = 2 * PI
        return ((angle + PI) % turn + turn) % turn - PI
    }
}
