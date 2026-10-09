package dev.huecoder.md3agent.feature.playground

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.huecoder.md3agent.ui.physics.Impact
import dev.huecoder.md3agent.ui.physics.Material
import dev.huecoder.md3agent.ui.physics.PhysicsWorld
import org.dyn4j.dynamics.Body
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class Piece(val body: Body, val shape: PlayShape, val size: Float, val colors: PieceColors, val face: Playground.Face?)

class Playground(
    private val pixelsPerMeter: Float,
    private val palette: PlaygroundPalette,
    private val pieceSizes: ClosedFloatingPointRange<Float>,
    private val onImpact: (Float) -> Unit,
) {
    private val world = PhysicsWorld(pixelsPerMeter)
    private val owners = HashMap<Body, Piece>()
    private val grabs = HashMap<Long, PhysicsWorld.Grab>()
    private val pendingSpawns = ArrayDeque<Long>()
    private var walls: List<Body> = emptyList()
    private var area = Size.Zero
    private var clockMs = 0L
    private var colorIndex = 0
    private var lastHapticAt = Long.MIN_VALUE / 2

    val pieces: List<Piece> get() = owners.values.toList()

    init {
        world.onImpact(::react)
    }

    fun resize(size: Size) {
        if (size == area) return
        val first = area == Size.Zero
        area = size
        walls.forEach(world::remove)
        val thickness = size.width
        val reach = size.height * WALL_HEIGHT_SCREENS
        walls = listOf(
            Rect(-thickness, size.height, size.width + thickness, size.height + thickness),
            Rect(-thickness, -reach, 0f, size.height + thickness),
            Rect(size.width, -reach, size.width + thickness, size.height + thickness),
        ).map { world.addWall(it, Material.Soft) }
        if (first) repeat(POPULATION) { index -> pendingSpawns.addLast(index * INITIAL_SPAWN_GAP_MS) }
    }

    fun pointGravity(direction: Offset) = world.pointGravity(direction)

    fun step(seconds: Float) {
        if (area == Size.Zero) return
        clockMs += (seconds * MILLIS_PER_SECOND).toLong()
        while (pendingSpawns.firstOrNull()?.let { it <= clockMs } == true) {
            pendingSpawns.removeFirst()
            spawn()
        }
        world.step(seconds)
        owners.values.toList().forEach { piece ->
            if (isThrownAway(piece)) remove(piece) else piece.face?.update(piece, seconds, clockMs)
        }
    }

    fun grab(pointer: Long, at: Offset): Boolean {
        val body = world.bodyAt(at) ?: return false
        grabs[pointer]?.release()
        grabs[pointer] = world.grab(body, at)
        owners[body]?.face?.startle(at, clockMs)
        return true
    }

    fun drag(pointer: Long, to: Offset) {
        grabs[pointer]?.moveTo(to)
    }

    fun release(pointer: Long) {
        grabs.remove(pointer)?.release()
    }

    fun origin(piece: Piece): Offset = world.origin(piece.body)

    fun angle(piece: Piece): Float = world.angle(piece.body)

    private fun spawn() {
        val isBot = Random.nextFloat() < BOT_SHARE
        val shape = (if (isBot) PlayShape.faces else PlayShape.all).random()
        val size = pieceSizes.start + (pieceSizes.endInclusive - pieceSizes.start) * Random.nextFloat().pow(SIZE_SKEW)
        val center = Offset(Random.nextFloat() * (area.width - size) + size / 2f, -size)
        val body = world.addHull(center, shape.hull.map { it * size }, Material.Bouncy)
        body.rotate(Random.nextDouble(0.0, FULL_TURN), body.worldCenter)
        body.angularVelocity = Random.nextDouble(-SPAWN_SPIN, SPAWN_SPIN)
        world.setVelocity(body, Offset(0f, Random.nextFloat() * SPAWN_DROP_SPEED * pixelsPerMeter))
        val face = if (isBot) Face(clockMs) else null
        owners[body] = Piece(body, shape, size, palette.colors(colorIndex++), face)
    }

    private fun isThrownAway(piece: Piece): Boolean {
        val center = world.position(piece.body)
        return center.y < -piece.size * REMOVE_MARGIN_SIZES && world.velocity(piece.body).y < 0f
    }

    private fun remove(piece: Piece) {
        grabs.entries.removeAll { (_, grab) -> (grab.body == piece.body).also { if (it) grab.release() } }
        world.remove(piece.body)
        owners.remove(piece.body)
        pendingSpawns.addLast(clockMs + RESPAWN_DELAY_MS)
    }

    private fun react(impact: Impact) {
        val power = (impact.speed / (HARD_IMPACT_SPEED * pixelsPerMeter)).coerceIn(0f, 1f)
        if (power < MIN_IMPACT_POWER) return
        listOfNotNull(owners[impact.first], owners[impact.second]).forEach { piece ->
            if (power > FLINCH_POWER) piece.face?.startle(impact.point, clockMs)
        }
        if (clockMs - lastHapticAt >= HAPTIC_GAP_MS) {
            lastHapticAt = clockMs
            onImpact(power)
        }
    }

    internal fun pickLookTarget(self: Piece): Piece? {
        val others = owners.values.filter { it !== self }
        val roll = Random.nextFloat()
        return when {
            roll < LOOK_AT_BOT_CHANCE -> others.filter { it.face != null }.minByOrNull { (origin(it) - origin(self)).getDistanceSquared() }
            roll < LOOK_AT_ANY_CHANCE -> others.randomOrNull()
            else -> null
        }
    }

    inner class Face(createdAt: Long) {
        var gaze = Offset.Zero
            private set
        var blink = 0f
            private set
        private var gazeVelocity = Offset.Zero
        private var target: Piece? = null
        private var startledAt: Offset? = null
        private var startledUntil = Long.MIN_VALUE
        private var nextLookAt = createdAt
        private var blinkStartedAt = Long.MIN_VALUE / 2
        private var blinkDepth = 1f
        private var nextBlinkAt = createdAt + Random.nextLong(BLINK_MIN_GAP_MS, BLINK_MAX_GAP_MS)

        fun startle(at: Offset, now: Long) {
            startledAt = at
            startledUntil = now + STARTLE_MS
            blinkStartedAt = now
            blinkDepth = STARTLE_SQUEEZE
        }

        fun update(self: Piece, seconds: Float, now: Long) {
            if (now >= nextLookAt) {
                target = pickLookTarget(self)
                nextLookAt = now + Random.nextLong(LOOK_MIN_MS, LOOK_MAX_MS)
            }
            if (now >= nextBlinkAt) {
                blinkStartedAt = now
                blinkDepth = 1f
                nextBlinkAt = now + Random.nextLong(BLINK_MIN_GAP_MS, BLINK_MAX_GAP_MS)
            }
            val point = startledAt?.takeIf { now < startledUntil } ?: target?.takeIf { it.body in owners }?.let(::origin)
            val wanted = point?.let { toLocalGaze(self, it) } ?: Offset.Zero
            val acceleration = (wanted - gaze) * GAZE_STIFFNESS - gazeVelocity * GAZE_DAMPING
            gazeVelocity += acceleration * seconds
            gaze += gazeVelocity * seconds
            blink = blinkCurve(now - blinkStartedAt) * blinkDepth
        }

        private fun toLocalGaze(self: Piece, point: Offset): Offset {
            val toward = point - origin(self)
            val angle = -angle(self)
            val local = Offset(toward.x * cos(angle) - toward.y * sin(angle), toward.x * sin(angle) + toward.y * cos(angle))
            val reach = self.size * GAZE_REACH_SIZES
            return Offset((local.x / reach).coerceIn(-1f, 1f), (local.y / reach).coerceIn(-1f, 1f))
        }

        private fun blinkCurve(elapsedMs: Long): Float = when {
            elapsedMs < 0 -> 0f
            elapsedMs < BLINK_CLOSE_MS -> elapsedMs / BLINK_CLOSE_MS.toFloat()
            elapsedMs < BLINK_CLOSE_MS + BLINK_OPEN_MS -> 1f - (elapsedMs - BLINK_CLOSE_MS) / BLINK_OPEN_MS.toFloat()
            else -> 0f
        }
    }

    private companion object {
        const val POPULATION = 16
        const val INITIAL_SPAWN_GAP_MS = 140L
        const val RESPAWN_DELAY_MS = 1_200L
        const val WALL_HEIGHT_SCREENS = 6f
        const val BOT_SHARE = 0.35f
        const val SIZE_SKEW = 2.2f
        const val FULL_TURN = 2 * Math.PI
        const val SPAWN_SPIN = 2.0
        const val SPAWN_DROP_SPEED = 3f
        const val REMOVE_MARGIN_SIZES = 1.5f
        const val HARD_IMPACT_SPEED = 10f
        const val MIN_IMPACT_POWER = 0.12f
        const val FLINCH_POWER = 0.4f
        const val HAPTIC_GAP_MS = 40L
        const val LOOK_AT_BOT_CHANCE = 0.6f
        const val LOOK_AT_ANY_CHANCE = 0.85f
        const val LOOK_MIN_MS = 1_200L
        const val LOOK_MAX_MS = 3_600L
        const val STARTLE_MS = 380L
        const val STARTLE_SQUEEZE = 0.8f
        const val BLINK_MIN_GAP_MS = 2_200L
        const val BLINK_MAX_GAP_MS = 5_500L
        const val BLINK_CLOSE_MS = 70L
        const val BLINK_OPEN_MS = 120L
        const val GAZE_STIFFNESS = 260f
        const val GAZE_DAMPING = 26f
        const val GAZE_REACH_SIZES = 1.6f
        const val MILLIS_PER_SECOND = 1_000f
    }
}
