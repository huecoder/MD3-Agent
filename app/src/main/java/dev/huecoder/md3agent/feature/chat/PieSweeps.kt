package dev.huecoder.md3agent.feature.chat

internal fun pieSweeps(values: List<Double>, minSweep: Float, fullCircle: Float = FULL_TURN): List<Float> {
    val shown = values.indices.filter { values[it] > 0 }
    if (shown.isEmpty()) return values.map { 0f }
    if (shown.size * minSweep >= fullCircle) return values.map { if (it > 0) fullCircle / shown.size else 0f }
    val pinned = mutableSetOf<Int>()
    while (true) {
        val free = shown.filterNot(pinned::contains)
        val budget = fullCircle - pinned.size * minSweep
        val freeTotal = free.sumOf { values[it] }
        val tooSmall = free.filter { values[it] / freeTotal * budget < minSweep }
        if (tooSmall.isEmpty()) {
            return values.indices.map { index ->
                when {
                    index in pinned -> minSweep
                    index in free -> (values[index] / freeTotal * budget).toFloat()
                    else -> 0f
                }
            }
        }
        pinned += tooSmall
    }
}

private const val FULL_TURN = 360f
