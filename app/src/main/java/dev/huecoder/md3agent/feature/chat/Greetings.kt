package dev.huecoder.md3agent.feature.chat

import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringArrayResource
import dev.huecoder.md3agent.R
import java.time.LocalTime
import kotlin.random.Random

enum class DayPart(@ArrayRes val greetings: Int, private val firstHour: Int) {
    MORNING(R.array.greetings_morning, firstHour = 5),
    DAY(R.array.greetings_day, firstHour = 12),
    EVENING(R.array.greetings_evening, firstHour = 18),
    NIGHT(R.array.greetings_night, firstHour = 23);

    companion object {
        fun at(hour: Int): DayPart = entries.lastOrNull { hour >= it.firstHour } ?: NIGHT
    }
}

object Greetings {
    fun pick(timed: List<String>, common: List<String>, seed: Int): String {
        val pool = timed + common
        return pool[Math.floorMod(seed, pool.size)]
    }
}

@Composable
fun rememberGreeting(): String {
    val hour = rememberSaveable { LocalTime.now().hour }
    val seed = rememberSaveable { Random.nextInt() }
    return Greetings.pick(
        timed = stringArrayResource(DayPart.at(hour).greetings).asList(),
        common = stringArrayResource(R.array.greetings_any).asList(),
        seed = seed,
    )
}
