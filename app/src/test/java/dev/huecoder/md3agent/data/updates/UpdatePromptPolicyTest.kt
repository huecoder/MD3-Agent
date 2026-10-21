package dev.huecoder.md3agent.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class UpdatePromptPolicyTest {
    private val available = UpdateStatus.Available("0.9.6", "page", "apk")

    @Test
    fun checksOnlyAfterTheInterval() {
        val last = 1_000_000L
        assertFalse(UpdatePromptPolicy.shouldCheck(last + 11.hours.inWholeMilliseconds, last))
        assertTrue(UpdatePromptPolicy.shouldCheck(last + 12.hours.inWholeMilliseconds, last))
    }

    @Test
    fun offersAnAvailableVersionOnce() {
        assertEquals(available, UpdatePromptPolicy.offerFor(available, ""))
        assertNull(UpdatePromptPolicy.offerFor(available, "0.9.6"))
    }

    @Test
    fun otherStatusesOfferNothing() {
        assertNull(UpdatePromptPolicy.offerFor(UpdateStatus.UpToDate("0.9.5"), ""))
        assertNull(UpdatePromptPolicy.offerFor(UpdateStatus.Unreachable, ""))
    }
}
