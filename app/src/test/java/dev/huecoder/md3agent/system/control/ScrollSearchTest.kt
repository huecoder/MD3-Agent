package dev.huecoder.md3agent.system.control

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollSearchTest {
    private class FakeList(private val size: Int, private val target: Int, private var top: Int = 0, private val windowSize: Int = 3, private val breaksAfter: Int = Int.MAX_VALUE) {
        var swipes = 0
        val visible: Boolean get() = target in top until top + windowSize
        val fingerprint: Long get() = top.toLong()

        fun scroll(direction: SwipeDirection): Boolean {
            if (swipes >= breaksAfter) return false
            swipes++
            val next = if (direction == SwipeDirection.DOWN) top + 1 else top - 1
            top = next.coerceIn(0, size - windowSize)
            return true
        }
    }

    private fun search(list: FakeList) = ScrollSearch(
        isVisible = { list.visible },
        fingerprint = { list.fingerprint },
        scrollOnce = list::scroll,
    )

    @Test
    fun `a target that is already visible costs no swipes`() = runTest {
        val list = FakeList(size = 20, target = 1)

        assertEquals(SearchOutcome.Found(0), search(list).run(listOf(SwipeDirection.DOWN), maxSwipes = 10))
        assertEquals(0, list.swipes)
    }

    @Test
    fun `a target further down is found after the swipes it takes`() = runTest {
        val list = FakeList(size = 20, target = 7)

        assertEquals(SearchOutcome.Found(5), search(list).run(listOf(SwipeDirection.DOWN), maxSwipes = 10))
    }

    @Test
    fun `reaching the end of the list is recognised by the screen not changing`() = runTest {
        val list = FakeList(size = 6, target = 99)

        val outcome = search(list).run(listOf(SwipeDirection.DOWN), maxSwipes = 30)

        assertEquals(SearchOutcome.EndReached(4), outcome)
    }

    @Test
    fun `the swipe limit stops a long search`() = runTest {
        val list = FakeList(size = 500, target = 400)

        assertEquals(SearchOutcome.LimitReached(10), search(list).run(listOf(SwipeDirection.DOWN), maxSwipes = 10))
    }

    @Test
    fun `without a direction the search goes down first and then back up`() = runTest {
        val list = FakeList(size = 10, target = 0, top = 5)

        val outcome = search(list).run(listOf(SwipeDirection.DOWN, SwipeDirection.UP), maxSwipes = 30)

        assertEquals(SearchOutcome.Found(3 + 7), outcome)
    }

    @Test
    fun `a failed gesture is reported with the swipes done so far`() = runTest {
        val list = FakeList(size = 50, target = 40, breaksAfter = 3)

        assertEquals(SearchOutcome.GestureFailed(3), search(list).run(listOf(SwipeDirection.DOWN), maxSwipes = 30))
    }
}
