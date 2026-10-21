package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.physics.PhysicsWorld
import dev.huecoder.md3agent.ui.physics.ShapeParts
import kotlin.math.PI

class BackdropPhysics(private val stage: SceneStage, topShape: AgentShape, bottomShape: AgentShape) {
    class Hit(val point: Offset, val metersPerSecond: Float, val topCenter: Offset, val bottomAngle: Float)

    private enum class Phase { WAITING, DIVING, RETURNING }

    private val pixelsPerMeter = stage.base / SceneMotion.METERS_ACROSS_BASE
    private val world = PhysicsWorld(pixelsPerMeter, gravity = SceneMotion.GRAVITY, stepSeconds = SceneMotion.STEP_SECONDS)
    private val rail = stage.bottomCenter - stage.home
    private val railLength = rail.getDistance()
    private val fall = rail / railLength
    private val top = world.addOutline(stage.home, ShapeParts.of(topShape), stage.topSize, SceneMotion.StrikeMaterial, isBullet = true, lockRotation = true)
    private val bottom = world.addOutline(stage.bottomCenter, ShapeParts.of(bottomShape), stage.bottomSize, SceneMotion.StrikeMaterial, isStatic = true)
    private var phase = Phase.WAITING
    private var waitLeft = SceneMotion.FIRST_REST_SECONDS
    private var returnSeconds = 0f
    private var returnReach = 0f
    private var strike: Hit? = null

    var bottomAngle = 0f
        private set

    val topCenter: Offset get() = world.origin(top)

    init {
        world.pointGravity(fall)
        world.onImpact { impact ->
            if (phase == Phase.DIVING && strike == null) {
                strike = Hit(impact.point, impact.speed / pixelsPerMeter, world.origin(top), bottomAngle)
            }
        }
    }

    fun step(seconds: Float, onHit: (Hit) -> Unit) {
        turnBottom(seconds)
        when (phase) {
            Phase.WAITING -> wait(seconds)
            Phase.DIVING -> dive(seconds, onHit)
            Phase.RETURNING -> comeBack(seconds)
        }
    }

    fun reshapeBottom(shape: AgentShape) {
        world.replaceOutline(bottom, ShapeParts.of(shape), stage.bottomSize, SceneMotion.StrikeMaterial)
    }

    private fun turnBottom(seconds: Float) {
        bottomAngle = (bottomAngle + SceneMotion.BOTTOM_TURN_RADIANS_PER_SECOND * seconds) % FULL_TURN
        world.place(bottom, stage.bottomCenter, bottomAngle.toDouble())
    }

    private fun wait(seconds: Float) {
        waitLeft -= seconds
        if (waitLeft <= 0f) launch()
    }

    private fun launch() {
        strike = null
        phase = Phase.DIVING
        world.setVelocity(top, fall * (SceneMotion.LAUNCH_METERS_PER_SECOND * pixelsPerMeter))
    }

    private fun dive(seconds: Float, onHit: (Hit) -> Unit) {
        world.step(seconds)
        world.keepOnLine(top, stage.home, fall)
        if (strike != null || reach() > railLength) startReturn(onHit)
    }

    private fun startReturn(onHit: (Hit) -> Unit) {
        returnReach = reach().coerceIn(0f, railLength)
        returnSeconds = 0f
        phase = Phase.RETURNING
        strike?.let(onHit)
    }

    private fun comeBack(seconds: Float) {
        returnSeconds += seconds
        val left = 1f - returnSeconds / SceneMotion.RETURN_SECONDS
        if (left > 0f) world.place(top, stage.home + fall * (returnReach * left * left), 0.0) else settleHome()
    }

    private fun settleHome() {
        world.place(top, stage.home, 0.0)
        waitLeft = SceneMotion.REST_SECONDS
        phase = Phase.WAITING
    }

    private fun reach(): Float = (world.origin(top) - stage.home).let { it.x * fall.x + it.y * fall.y }

    private companion object {
        const val FULL_TURN = (2 * PI).toFloat()
    }
}
