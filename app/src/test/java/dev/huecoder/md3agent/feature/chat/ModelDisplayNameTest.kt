package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameTest {
    @Test
    fun `compact date is dropped`() {
        assertEquals("claude-sonnet-4-5", ModelDisplayName.short("claude-sonnet-4-5-20250929"))
    }

    @Test
    fun `dashed date is dropped`() {
        assertEquals("gpt-4o", ModelDisplayName.short("gpt-4o-2024-08-06"))
    }

    @Test
    fun `at sign date is dropped`() {
        assertEquals("claude-3-opus", ModelDisplayName.short("claude-3-opus@20240229"))
    }

    @Test
    fun `names without a date stay`() {
        assertEquals("kimi-k3", ModelDisplayName.short("kimi-k3"))
    }

    @Test
    fun `a bare date is not erased`() {
        assertEquals("20250929", ModelDisplayName.short("20250929"))
    }
}
