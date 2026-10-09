package dev.huecoder.md3agent.ui.avatar

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

object GazeSprings {
    val Saccade = spring<Offset>(dampingRatio = 0.85f, stiffness = 420f)
    val Glance = spring<Offset>(dampingRatio = 0.8f, stiffness = 50f)
    val Home = spring<Offset>(dampingRatio = 0.8f, stiffness = 70f)
    val Bored = spring<Offset>(dampingRatio = 0.9f, stiffness = 28f)
}

suspend fun BotMotion.watchTyping(typing: TypingAttention, toGaze: (Offset) -> Offset?) = coroutineScope {
    var seen = typing.keystrokes
    var sessionStart = 0L
    var lastKeyAt = 0L
    var bored: Job? = null
    var gazeJob: Job? = null
    fun look(target: Offset, spec: AnimationSpec<Offset>) {
        gazeJob?.cancel()
        gazeJob = launch { lookTo(target, spec) }
    }

    while (true) {
        val keystroke = withTimeoutOrNull(IDLE_CHECK_MS) { snapshotFlow { typing.keystrokes }.first { it != seen } }
        val now = SystemClock.uptimeMillis()
        if (keystroke == null) {
            if (lastKeyAt != 0L && now - lastKeyAt > PAUSE_MS) {
                bored?.cancel()
                bored = null
                lastKeyAt = 0L
                look(Offset.Zero, GazeSprings.Home)
            }
            continue
        }
        seen = keystroke
        if (now - lastKeyAt > PAUSE_MS) sessionStart = now
        lastKeyAt = now
        if (now - sessionStart < BORED_AFTER_MS) {
            typing.letter?.let(toGaze)?.let { look(it, GazeSprings.Saccade) }
        } else if (bored?.isActive != true) {
            gazeJob?.cancel()
            bored = launch { actBored(typing, toGaze) }
        }
    }
}

private suspend fun BotMotion.actBored(typing: TypingAttention, toGaze: (Offset) -> Offset?) {
    var side = if (Random.nextBoolean()) 1f else -1f
    while (true) {
        val away = Offset(side * Random.nextDouble(AWAY_MIN_X, 1.0).toFloat(), -Random.nextDouble(0.0, AWAY_MAX_UP).toFloat())
        lookTo(away, GazeSprings.Bored)
        delay(Random.nextLong(AWAY_MIN_MS, AWAY_MAX_MS))
        typing.letter?.let(toGaze)?.let { lookTo(it, GazeSprings.Saccade) }
        delay(PEEK_MS)
        if (Random.nextFloat() < SWITCH_SIDE_CHANCE) side = -side
    }
}

class BotListening {
    val headTilt = Animatable(0f)
    val swell = Animatable(0f)

    suspend fun listen(level: () -> Float) = coroutineScope {
        val sway = launch { swayHead() }
        try {
            while (true) {
                val loudness = level().coerceIn(0f, 1f)
                swell.animateTo(loudness * MAX_SWELL, SWELL_SPEC)
                delay(LEVEL_POLL_MS)
            }
        } finally {
            sway.cancel()
        }
    }

    suspend fun stop() = coroutineScope {
        launch { headTilt.animateTo(0f, SETTLE_SPEC) }
        swell.animateTo(0f, SETTLE_SPEC)
    }

    private suspend fun swayHead() {
        var tiltRight = Random.nextBoolean()
        while (true) {
            val degrees = Random.nextDouble(MIN_TILT_DEGREES, MAX_TILT_DEGREES).toFloat()
            headTilt.animateTo(if (tiltRight) degrees else -degrees, SWAY_SPEC)
            delay(Random.nextLong(SWAY_HOLD_MIN_MS, SWAY_HOLD_MAX_MS))
            if (Random.nextFloat() < SWITCH_SIDE_CHANCE) tiltRight = !tiltRight
        }
    }

    private companion object {
        const val MAX_SWELL = 0.12f
        const val LEVEL_POLL_MS = 60L
        const val MIN_TILT_DEGREES = 6.0
        const val MAX_TILT_DEGREES = 14.0
        const val SWAY_HOLD_MIN_MS = 900L
        const val SWAY_HOLD_MAX_MS = 2_200L
        val SWELL_SPEC = spring<Float>(dampingRatio = 0.55f, stiffness = 500f)
        val SWAY_SPEC = spring<Float>(dampingRatio = 0.5f, stiffness = 60f)
        val SETTLE_SPEC = spring<Float>(dampingRatio = 0.5f, stiffness = 200f)
    }
}

private const val IDLE_CHECK_MS = 250L
private const val PAUSE_MS = 1_800L
private const val BORED_AFTER_MS = 10_000L
private const val AWAY_MIN_X = 0.7
private const val AWAY_MAX_UP = 0.8
private const val AWAY_MIN_MS = 2_200L
private const val AWAY_MAX_MS = 4_200L
private const val PEEK_MS = 450L
private const val SWITCH_SIDE_CHANCE = 0.35f
