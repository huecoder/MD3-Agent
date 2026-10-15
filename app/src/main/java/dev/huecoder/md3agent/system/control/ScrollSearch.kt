package dev.huecoder.md3agent.system.control

sealed interface SearchOutcome {
    data class Found(val swipes: Int) : SearchOutcome
    data class EndReached(val swipes: Int) : SearchOutcome
    data class LimitReached(val swipes: Int) : SearchOutcome
    data class GestureFailed(val swipes: Int) : SearchOutcome
}

class ScrollSearch(
    private val isVisible: suspend () -> Boolean,
    private val fingerprint: suspend () -> Long,
    private val scrollOnce: suspend (SwipeDirection) -> Boolean,
) {
    suspend fun run(directions: List<SwipeDirection>, maxSwipes: Int): SearchOutcome {
        if (isVisible()) return SearchOutcome.Found(0)
        var swipes = 0
        for (direction in directions) {
            var before = fingerprint()
            while (swipes < maxSwipes) {
                if (!scrollOnce(direction)) return SearchOutcome.GestureFailed(swipes)
                swipes++
                if (isVisible()) return SearchOutcome.Found(swipes)
                val after = fingerprint()
                if (after == before) break
                before = after
            }
            if (swipes >= maxSwipes) return SearchOutcome.LimitReached(swipes)
        }
        return SearchOutcome.EndReached(swipes)
    }
}
