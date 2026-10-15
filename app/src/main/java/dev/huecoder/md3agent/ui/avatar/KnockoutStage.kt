package dev.huecoder.md3agent.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.huecoder.md3agent.ui.physics.Impact
import dev.huecoder.md3agent.ui.physics.Material
import dev.huecoder.md3agent.ui.physics.PhysicsWorld
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.dyn4j.dynamics.Body
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Stable
class KnockoutStage {
    private class Flight(val world: PhysicsWorld, val bot: Body, val pixelsPerMeter: Float)

    private val obstacles = mutableMapOf<String, Rect>()
    private var area = Rect.Zero
    private var flight: Flight? = null
    private var tumble = Vec3.Zero
    private var knockedLoose = false
    private var aborted = false

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set
    var spin: Float by mutableFloatStateOf(0f)
        private set
    var orientation: Orientation by mutableStateOf(Orientation.Identity)
        private set
    var isDazed: Boolean by mutableStateOf(false)
        private set
    var isPlaying: Boolean by mutableStateOf(false)
        private set

    fun setObstacle(id: String, bounds: Rect) {
        obstacles[id] = bounds
    }

    fun setArea(bounds: Rect) {
        if (isPlaying && bounds != area) aborted = true
        area = bounds
    }

    fun abort() {
        if (isPlaying) aborted = true
    }

    suspend fun play(home: Offset, radius: Float, hit: Offset, power: Float, pixelsPerMeter: Float, onImpact: (Float) -> Unit) {
        if (isPlaying || area == Rect.Zero) return
        isPlaying = true
        isDazed = true
        aborted = false
        try {
            withTimeoutOrNull(MAX_FLIGHT_MS) {
                val world = PhysicsWorld(pixelsPerMeter)
                world.onImpact { impact -> if (impact.speed > MIN_IMPACT_SPEED * pixelsPerMeter) onImpact(impact.strength(pixelsPerMeter)) }
                val bot = world.addBall(home, radius * COLLIDER_SHRINK, Material.Bouncy)
                flight = Flight(world, bot, pixelsPerMeter)
                tumble = spinFrom(hit, LAUNCH_TUMBLE)
                rollAway(world, bot, home, radius, hit, launchBoost(power), pixelsPerMeter)
                flight = null
                if (aborted) return@withTimeoutOrNull
                delay(OFFSCREEN_PAUSE_MS)
                isDazed = false
                orientation = Orientation.Identity
                dropIn(world, bot, home, radius)
                if (aborted) return@withTimeoutOrNull
                settle()
            }
        } finally {
            flight = null
            tumble = Vec3.Zero
            knockedLoose = false
            aborted = false
            orientation = Orientation.Identity
            offset = Offset.Zero
            spin = 0f
            isDazed = false
            isPlaying = false
        }
    }

    fun hit(direction: Offset, power: Float) {
        val current = flight ?: return
        val away = awayFrom(direction)
        val strength = MIDAIR_MIN_SHARE + (1f - MIDAIR_MIN_SHARE) * power
        val push = Offset(away.x, away.y - MIDAIR_LIFT) * (MIDAIR_SPEED * current.pixelsPerMeter * strength)
        current.world.setVelocity(current.bot, current.world.velocity(current.bot) + push)
        current.bot.angularVelocity += (away.x.sign.takeIf { it != 0f } ?: 1f) * MIDAIR_SPIN * strength
        tumble += spinFrom(direction, MIDAIR_TUMBLE * strength)
        knockedLoose = true
    }

    private suspend fun rollAway(world: PhysicsWorld, bot: Body, home: Offset, radius: Float, hit: Offset, boost: Float, pixelsPerMeter: Float) {
        val walls = obstacles.values.map { world.addWall(it, Material.Grippy) }
        val exitSide = (-hit.x).sign.takeIf { it != 0f } ?: if (Random.nextBoolean()) 1f else -1f
        val firstLanding = landingBelow(home)
        world.setVelocity(bot, launchVelocity(home, radius, exitSide, firstLanding, boost, world.gravityPixels, pixelsPerMeter))
        bot.angularVelocity = exitSide * LAUNCH_SPIN * boost
        var wallsDropped = false
        frames { seconds, elapsedMs ->
            world.step(seconds)
            val position = world.position(bot)
            val overFirst = !knockedLoose && firstLanding != null && position.y < firstLanding.bottom
            world.driveSpin(bot, exitSide * if (overFirst) SHUFFLE_SPIN else ROLL_SPIN, ROLL_ACCELERATION)
            bot.linearDamping = if (overFirst) SHUFFLE_DRAG else 0.0
            if (!knockedLoose && firstLanding != null && position.x !in firstLanding.left..firstLanding.right) {
                steerFall(world, bot, position, radius, below = firstLanding, seconds)
            }
            follow(world, bot, home)
            rollEyes(world, bot, radius, seconds)
            if (!wallsDropped && elapsedMs > STUCK_AFTER_MS) {
                walls.forEach(world::remove)
                wallsDropped = true
            }
            !isOffscreen(position, radius) && !aborted
        }
        if (!wallsDropped) walls.forEach(world::remove)
    }

    private suspend fun dropIn(world: PhysicsWorld, bot: Body, home: Offset, radius: Float) {
        val pedestal = world.addWall(
            Rect(home.x - radius * PEDESTAL_HALF_WIDTH, home.y + radius * COLLIDER_SHRINK, home.x + radius * PEDESTAL_HALF_WIDTH, home.y + radius * 2),
            Material(restitution = LANDING_BOUNCE, friction = LANDING_FRICTION),
        )
        world.moveTo(bot, Offset(home.x, area.top - radius * 2), angle = Random.nextDouble(-DROP_TILT, DROP_TILT))
        bot.angularVelocity = Random.nextDouble(-DROP_SPIN, DROP_SPIN)
        var calmMs = 0L
        frames { seconds, elapsedMs ->
            world.step(seconds)
            standUp(bot)
            follow(world, bot, home)
            val calm = world.velocity(bot).getDistance() < radius * CALM_SPEED_RADII && abs(bot.angularVelocity) < CALM_SPIN
            calmMs = if (calm) calmMs + (seconds * MILLIS_PER_SECOND).toLong() else 0L
            calmMs < CALM_HOLD_MS && elapsedMs < DROP_TIMEOUT_MS && !aborted
        }
        world.remove(pedestal)
    }

    private fun rollEyes(world: PhysicsWorld, bot: Body, radius: Float, seconds: Float) {
        val angle = world.angle(bot)
        val velocity = world.velocity(bot)
        val alongX = velocity.x * cos(angle) + velocity.y * sin(angle)
        val alongY = velocity.y * cos(angle) - velocity.x * sin(angle)
        val rolling = Vec3(-alongY, alongX, 0f) * (EYE_ROLL / radius)
        tumble *= exp(-TUMBLE_DRAG * seconds)
        orientation = orientation.spun(rolling + tumble, seconds)
    }

    private fun launchVelocity(home: Offset, radius: Float, exitSide: Float, below: Rect?, boost: Float, gravity: Float, pixelsPerMeter: Float): Offset {
        val up = -LAUNCH_UP * boost * pixelsPerMeter
        if (below == null) return Offset(exitSide * LAUNCH_SIDE * boost * pixelsPerMeter, up)
        val drop = below.top - radius - home.y
        val flightSeconds = (-up + sqrt(up * up + 2 * gravity * drop)) / gravity
        val landingX = home.x + exitSide * below.width / 2f * (LANDING_REACH * boost).coerceAtMost(MAX_LANDING_REACH)
        return Offset((landingX - home.x) / flightSeconds, up)
    }

    private fun steerFall(world: PhysicsWorld, bot: Body, position: Offset, radius: Float, below: Rect, seconds: Float) {
        val next = obstacles.values.filter { it.top > below.bottom }.minByOrNull { it.top } ?: return
        if (position.y > next.top - radius) return
        val inset = radius * LANDING_INSET_RADII
        val targetX = position.x.coerceIn(next.left + inset, next.right - inset)
        val velocity = world.velocity(bot)
        val wanted = (targetX - position.x) * STEER_RATE
        val blend = (STEER_RESPONSE * seconds).coerceAtMost(1f)
        world.setVelocity(bot, Offset(velocity.x + (wanted - velocity.x) * blend, velocity.y))
    }

    private fun landingBelow(point: Offset): Rect? =
        obstacles.values.filter { point.x in it.left..it.right && it.top > point.y }.minByOrNull { it.top }

    private fun standUp(bot: Body) {
        val tilt = uprightError(bot.transform.rotationAngle)
        val acceleration = -RIGHTING_STIFFNESS * tilt - RIGHTING_DAMPING * bot.angularVelocity
        bot.applyTorque(acceleration * bot.mass.inertia)
    }

    private suspend fun settle() = coroutineScope {
        val position = Animatable(offset, Offset.VectorConverter)
        val angle = Animatable(nearestUpright(spin))
        launch { position.animateTo(Offset.Zero, SETTLE_OFFSET_SPEC) { offset = value } }
        angle.animateTo(0f, SETTLE_SPIN_SPEC) { spin = value }
    }

    private fun follow(world: PhysicsWorld, bot: Body, home: Offset) {
        offset = world.position(bot) - home
        spin = Math.toDegrees(world.angle(bot).toDouble()).toFloat()
    }

    private fun isOffscreen(center: Offset, radius: Float): Boolean {
        val margin = radius * OFFSCREEN_RADII
        return center.x < area.left - margin || center.x > area.right + margin || center.y > area.bottom + margin
    }

    private companion object {
        const val MIN_LAUNCH_BOOST = 1.15f
        const val MAX_LAUNCH_BOOST = 1.5f
        const val MAX_LANDING_REACH = 0.8f
        const val CENTER_HIT = 0.08f
        const val MIDAIR_SPEED = 6f
        const val MIDAIR_LIFT = 0.6f
        const val MIDAIR_MIN_SHARE = 0.5f
        const val MIDAIR_SPIN = 8.0
        const val LAUNCH_TUMBLE = 10f
        const val MIDAIR_TUMBLE = 12f
        const val TUMBLE_DRAG = 0.8f
        const val EYE_ROLL = 1f
        const val COLLIDER_SHRINK = 0.92f
        const val MIN_IMPACT_SPEED = 1.2f
        const val HARD_IMPACT_SPEED = 9f
        const val LAUNCH_SIDE = 4.5f
        const val LAUNCH_UP = 7f
        const val LAUNCH_SPIN = 9.0
        const val ROLL_SPIN = 6.0
        const val SHUFFLE_SPIN = 1.6
        const val SHUFFLE_DRAG = 1.5
        const val LANDING_INSET_RADII = 0.6f
        const val STEER_RATE = 3f
        const val STEER_RESPONSE = 12f
        const val LANDING_REACH = 0.35f
        const val ROLL_ACCELERATION = 30.0
        const val STUCK_AFTER_MS = 6_000L
        const val OFFSCREEN_RADII = 1.5f
        const val OFFSCREEN_PAUSE_MS = 1_000L
        const val PEDESTAL_HALF_WIDTH = 1.4f
        const val LANDING_BOUNCE = 0.45
        const val LANDING_FRICTION = 0.0
        const val DROP_TILT = 0.7
        const val DROP_SPIN = 4.0
        const val CALM_SPEED_RADII = 0.4f
        const val CALM_SPIN = 0.6
        const val CALM_HOLD_MS = 350L
        const val DROP_TIMEOUT_MS = 4_000L
        const val MAX_FLIGHT_MS = 30_000L
        const val RIGHTING_STIFFNESS = 60.0
        const val RIGHTING_DAMPING = 5.0
        const val FULL_TURN = 360f
        val SETTLE_OFFSET_SPEC = spring<Offset>(dampingRatio = 0.55f, stiffness = 380f)
        val SETTLE_SPIN_SPEC = spring<Float>(dampingRatio = 0.45f, stiffness = 260f)

        fun uprightError(radians: Double): Double {
            val turn = 2 * PI
            return radians - (radians / turn).roundToInt() * turn
        }

        fun nearestUpright(degrees: Float): Float = degrees - (degrees / FULL_TURN).roundToInt() * FULL_TURN

        fun launchBoost(power: Float): Float = MIN_LAUNCH_BOOST + (MAX_LAUNCH_BOOST - MIN_LAUNCH_BOOST) * power.coerceIn(0f, 1f)

        fun awayFrom(hit: Offset): Offset {
            val distance = hit.getDistance()
            return if (distance > CENTER_HIT) -hit / distance else Offset(0f, -1f)
        }

        fun spinFrom(hit: Offset, strength: Float): Vec3 = Vec3(hit.y, -hit.x, 0f) * strength
    }

    private fun Impact.strength(pixelsPerMeter: Float): Float = (speed / (HARD_IMPACT_SPEED * pixelsPerMeter)).coerceIn(0f, 1f)
}

private suspend fun frames(onFrame: (seconds: Float, elapsedMs: Long) -> Boolean) {
    val start = withFrameNanos { it }
    var last = start
    while (true) {
        val now = withFrameNanos { it }
        if (!onFrame((now - last) / NANOS_PER_SECOND, (now - start) / NANOS_PER_MILLI)) return
        last = now
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000f

fun Modifier.knockoutObstacle(stage: KnockoutStage?, id: String): Modifier =
    if (stage == null) this else onGloballyPositioned { stage.setObstacle(id, it.boundsInRoot()) }

fun Modifier.knockoutArea(stage: KnockoutStage): Modifier = onGloballyPositioned { stage.setArea(it.boundsInRoot()) }
