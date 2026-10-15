package dev.huecoder.md3agent.system.control

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUseSessionTest {
    private val session = PhoneUseSession()

    @Test
    fun `beginning a chat resets the cursor and ending clears the chat`() {
        session.begin("chat")
        runBlocking { session.aim(10, 20, "Tapping") }
        assertEquals(10f, session.cursor.value.x)
        session.begin("other")
        assertEquals("other", session.chatId.value)
        assertNull(session.cursor.value.x)
        session.end()
        assertNull(session.chatId.value)
    }

    @Test
    fun `beginning the same chat again keeps the cursor`() {
        session.begin("chat")
        runBlocking { session.aim(5, 6, null) }
        session.begin("chat")
        assertEquals(5f, session.cursor.value.x)
    }

    @Test
    fun `a new chat keeps counting tokens so old acknowledgements cannot satisfy new waits`() {
        session.begin("chat")
        runBlocking { session.aim(5, 6, "One") }
        val before = session.cursor.value
        session.begin("other")

        assertEquals(before.aimToken, session.cursor.value.aimToken)
        assertEquals(before.captionToken, session.cursor.value.captionToken)
    }

    @Test
    fun `aiming off screen does not wait`() {
        val started = System.nanoTime()
        runBlocking { session.aim(1, 2, "Tapping") }
        assertTrue((System.nanoTime() - started) / NANOS_PER_MS < FAST_MS)
    }

    @Test
    fun `a caption bumps the token only when given`() {
        runBlocking { session.aim(1, 2, "First") }
        val first = session.cursor.value.captionToken
        runBlocking { session.aim(3, 4, null) }
        assertEquals(first, session.cursor.value.captionToken)
        session.say("Second")
        assertEquals(first + 1, session.cursor.value.captionToken)
        assertEquals("Second", session.cursor.value.caption)
    }

    @Test
    fun `touches pass only while the gesture runs`() = runBlocking {
        assertFalse(session.isPassingTouches.first())
        session.passingTouches(TouchArea(1, 1, 2, 2)) { assertTrue(session.isPassingTouches.first()) }
        assertFalse(session.isPassingTouches.first())
    }

    @Test
    fun `the overlay is hidden around a capture only when it is on screen`() = runBlocking {
        session.withOverlayHidden { assertFalse(session.isOverlayHidden.first()) }
        session.setOnScreen(true)
        session.withOverlayHidden { assertTrue(session.isOverlayHidden.first()) }
        assertFalse(session.isOverlayHidden.first())
    }

    @Test
    fun `waiting is tracked`() {
        session.waiting(true)
        assertTrue(session.cursor.value.waiting)
    }

    @Test
    fun `a click runs its action with the cursor pressed and releases it afterwards`() = runBlocking {
        var pressedDuring = false
        var areaDuring: List<TouchArea> = emptyList()

        val result = session.click(30, 40, "Tapping") {
            pressedDuring = session.cursor.value.pressed
            areaDuring = session.gestures.value
            "tapped"
        }

        assertEquals("tapped", result)
        assertTrue(pressedDuring)
        assertEquals(listOf(TouchArea(30, 40, 30, 40)), areaDuring)
        assertFalse(session.cursor.value.pressed)
        assertTrue(session.gestures.value.isEmpty())
    }

    @Test
    fun `a failing action still releases the cursor`() = runBlocking {
        runCatching { session.click(1, 2, null) { error("gesture failed") } }

        assertFalse(session.cursor.value.pressed)
        assertTrue(session.gestures.value.isEmpty())
    }

    @Test
    fun `a click waits for the cursor to arrive and the press to settle before acting`() = runTest {
        session.setOnScreen(true)
        fakeOverlay(travelMs = 300, pressMs = 120)
        var actedAt = -1L

        session.click(10, 20, "Tapping") { actedAt = currentTime }

        assertEquals(420L, actedAt)
        assertFalse(session.cursor.value.pressed)
    }

    @Test
    fun `a click does not wait for ever for an overlay that never answers`() = runTest {
        session.setOnScreen(true)
        var actedAt = -1L

        session.click(10, 20, "Tapping") { actedAt = currentTime }

        assertEquals(900L + 400L, actedAt)
    }

    @Test
    fun `an acknowledgement for an older aim does not release a newer one`() = runTest {
        session.setOnScreen(true)
        backgroundScope.launch {
            delay(100)
            session.arrived(session.cursor.value.aimToken - 1)
        }
        var aimedAt = -1L

        session.aim(1, 1, null)
        aimedAt = currentTime

        assertEquals(900L, aimedAt)
    }

    @Test
    fun `a drag holds the cursor at the start and moves it only when the gesture begins`() = runBlocking {
        var atStart: Pair<Float?, Float?>? = null
        var travelAtStart: Int? = null

        session.dragging(1, 2, 30, 40, 320, "Swiping") {
            atStart = session.cursor.value.x to session.cursor.value.y
            travelAtStart = session.cursor.value.travelMs
        }

        assertEquals(30f to 40f, atStart)
        assertEquals(320, travelAtStart)
        assertEquals(TouchArea(1, 2, 30, 40), TouchArea.around(1, 2, 30, 40))
    }

    @Test
    fun `a double click presses the cursor twice while one action runs`() = runTest {
        var pressesSeenByAction = 0
        var pressedAtEnd = true

        session.doubleClick(5, 6, "Double tapping") {
            delay(400)
            pressesSeenByAction = session.cursor.value.pressToken
            pressedAtEnd = session.cursor.value.pressed
        }

        assertEquals(2, pressesSeenByAction)
        assertTrue(pressedAtEnd)
        assertFalse(session.cursor.value.pressed)
    }

    @Test
    fun `touch areas know when they overlap`() {
        val panel = TouchArea(0, 100, 50, 200)

        assertTrue(panel.intersects(TouchArea(40, 190, 60, 210)))
        assertFalse(panel.intersects(TouchArea(60, 0, 80, 90)))
        assertTrue(panel.inflated(20).intersects(TouchArea(60, 0, 80, 90).copy(bottom = 85)))
    }

    private fun TestScope.fakeOverlay(travelMs: Long, pressMs: Long) {
        backgroundScope.launch {
            var lastAim = 0
            var lastPress = 0
            while (true) {
                val cursor = session.cursor.first { it.aimToken > lastAim || (it.pressed && it.pressToken > lastPress) }
                if (cursor.aimToken > lastAim) {
                    lastAim = cursor.aimToken
                    launch {
                        delay(travelMs)
                        session.arrived(cursor.aimToken)
                    }
                }
                if (cursor.pressed && cursor.pressToken > lastPress) {
                    lastPress = cursor.pressToken
                    launch {
                        delay(pressMs)
                        session.pressSettled(cursor.pressToken)
                    }
                }
            }
        }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val FAST_MS = 200L
    }
}
