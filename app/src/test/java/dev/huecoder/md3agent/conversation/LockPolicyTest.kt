package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.settings.LockGuard
import dev.huecoder.md3agent.tools.phone.PhoneActionTool
import dev.huecoder.md3agent.tools.web.WebSearchTool
import org.junit.Assert.assertEquals
import org.junit.Test

class LockPolicyTest {
    private fun need(mode: LockGuard, tool: String, locked: Boolean = true, confirmed: Boolean = false) =
        LockPolicy.requirement(mode, locked, confirmed, tool)

    @Test
    fun `an unlocked phone asks for nothing`() {
        assertEquals(LockRequirement.NONE, need(LockGuard.BIOMETRIC, PhoneActionTool.NAME, locked = false))
    }

    @Test
    fun `basic tools run on a locked phone`() {
        assertEquals(LockRequirement.NONE, need(LockGuard.BIOMETRIC, WebSearchTool.NAME))
    }

    @Test
    fun `sensitive tools ask for biometrics or an unlock by mode`() {
        assertEquals(LockRequirement.CONFIRM, need(LockGuard.BIOMETRIC, PhoneActionTool.NAME))
        assertEquals(LockRequirement.UNLOCK, need(LockGuard.UNLOCK, PhoneActionTool.NAME))
        assertEquals(LockRequirement.NONE, need(LockGuard.OFF, PhoneActionTool.NAME))
    }

    @Test
    fun `a fresh confirmation lets sensitive tools through`() {
        assertEquals(LockRequirement.NONE, need(LockGuard.BIOMETRIC, PhoneActionTool.NAME, confirmed = true))
    }

    @Test
    fun `memory and chats count as private`() {
        assertEquals(LockRequirement.CONFIRM, need(LockGuard.BIOMETRIC, "memory_search"))
        assertEquals(LockRequirement.CONFIRM, need(LockGuard.BIOMETRIC, "read_chat"))
    }
}
