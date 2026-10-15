package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendEntranceTest {
    private var clock = 10_000L
    private val entrance = SendEntrance { clock }

    @Test
    fun `nothing enters without a send`() {
        assertFalse(entrance.consume())
    }

    @Test
    fun `one send animates one message`() {
        entrance.arm()

        assertTrue(entrance.consume())
        assertFalse(entrance.consume())
    }

    @Test
    fun `two quick sends animate two messages`() {
        entrance.arm()
        clock += 300
        entrance.arm()

        assertTrue(entrance.consume())
        assertTrue(entrance.consume())
        assertFalse(entrance.consume())
    }

    @Test
    fun `a send whose message never arrived does not animate a later one`() {
        entrance.arm()
        clock += SendEntrance.EXPIRES_AFTER_MS + 1

        assertFalse(entrance.consume())
    }

    @Test
    fun `an old unconsumed send does not stack onto a new one`() {
        entrance.arm()
        clock += SendEntrance.EXPIRES_AFTER_MS + 1
        entrance.arm()

        assertTrue(entrance.consume())
        assertFalse(entrance.consume())
    }
}
