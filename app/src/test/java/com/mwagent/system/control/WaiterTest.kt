package com.mwagent.system.control

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaiterTest {
    private fun probe(fingerprint: Long, vararg labels: String, app: String = "app") = ScreenProbe(app, fingerprint, labels.toList())

    private fun TestScope.waiter(timeline: (elapsedMs: Long) -> ScreenProbe) = Waiter(probe = { timeline(currentTime) }, clock = { currentTime })

    @Test
    fun `text that is already there is met at once`() = runTest {
        val outcome = waiter { probe(1, "Connected to Home") }.await(WaitCondition.TextVisible("connected"), 5_000)

        assertTrue(outcome.met)
        assertEquals(0L, outcome.waitedMs)
    }

    @Test
    fun `text that appears later is met at the first poll after it shows`() = runTest {
        val outcome = waiter { if (it < 1_000) probe(1, "Connecting") else probe(2, "Connected") }.await(WaitCondition.TextVisible("Connected"), 5_000)

        assertTrue(outcome.met)
        assertTrue("waited ${outcome.waitedMs}", outcome.waitedMs in 1_000..1_000 + Waiter.POLL_MS)
    }

    @Test
    fun `text that never shows times out with a clear message`() = runTest {
        val outcome = waiter { probe(1, "Connecting") }.await(WaitCondition.TextVisible("Connected"), 2_000)

        assertFalse(outcome.met)
        assertEquals(2_000L, outcome.waitedMs)
        assertTrue(outcome.detail.contains("\"Connected\" never appeared"))
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `text that goes away is met when it is gone`() = runTest {
        val outcome = waiter { if (it < 600) probe(1, "Loading…") else probe(2, "Done") }.await(WaitCondition.TextGone("loading"), 5_000)

        assertTrue(outcome.met)
        assertTrue(outcome.waitedMs in 600..600 + Waiter.POLL_MS)
    }

    @Test
    fun `an app in front is matched by package`() = runTest {
        val outcome = waiter { if (it < 400) probe(1, app = "com.launcher") else probe(2, app = "com.android.settings") }
            .await(WaitCondition.AppInFront("com.android.settings"), 5_000)

        assertTrue(outcome.met)
    }

    @Test
    fun `idle needs the screen to stay still for the quiet time`() = runTest {
        val outcome = waiter { if (it < 1_000) probe(it / 100) else probe(999) }.await(WaitCondition.Idle(400), 10_000)

        assertTrue(outcome.met)
        assertTrue("waited ${outcome.waitedMs}", outcome.waitedMs >= 1_000 + 400)
        assertTrue(outcome.waitedMs <= 1_000 + 400 + 2 * Waiter.POLL_MS)
    }

    @Test
    fun `idle is met quickly on a still screen`() = runTest {
        val outcome = waiter { probe(7) }.await(WaitCondition.Idle(400), 10_000)

        assertTrue(outcome.met)
        assertTrue(outcome.waitedMs in 400..400 + Waiter.POLL_MS)
    }

    @Test
    fun `idle times out when the screen never settles`() = runTest {
        val outcome = waiter { probe(it) }.await(WaitCondition.Idle(400), 1_500)

        assertFalse(outcome.met)
        assertTrue(outcome.detail.contains("kept changing"))
    }

    @Test
    fun `screen_changed waits for any difference from the first look`() = runTest {
        val outcome = waiter { if (it < 800) probe(1) else probe(2) }.await(WaitCondition.ScreenChanged, 5_000)

        assertTrue(outcome.met)
        assertTrue(outcome.waitedMs in 800..800 + Waiter.POLL_MS)
    }

    @Test
    fun `screen_changed times out on a still screen`() = runTest {
        val outcome = waiter { probe(1) }.await(WaitCondition.ScreenChanged, 1_000)

        assertFalse(outcome.met)
        assertTrue(outcome.detail.contains("did not change"))
    }

    @Test
    fun `waiting never overshoots the timeout by a whole poll`() = runTest {
        waiter { probe(1) }.await(WaitCondition.TextVisible("x"), 250)

        assertEquals(250L, currentTime)
    }
}
