package dev.huecoder.md3agent.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgentSettingsTest {
    private val main = AgentProfile()
    private val coder = AgentProfile(id = "coder", name = "Coder")
    private val agents = AgentSettings(agents = listOf(main, coder), activeAgentId = "coder")

    @Test
    fun `a chat keeps its own agent no matter which one is the default`() {
        assertEquals(main, agents.forChat(AgentProfile.MAIN_AGENT_ID))
        assertEquals(coder, agents.forChat("coder"))
    }

    @Test
    fun `a chat from before agents existed belongs to the main agent`() {
        assertEquals(main, agents.forChat(null))
    }

    @Test
    fun `a chat whose agent was deleted falls back to the default agent`() {
        assertEquals(coder, agents.forChat("deleted"))
        assertEquals(coder, AgentSettings(agents = listOf(coder), activeAgentId = "coder").forChat(null))
    }

    @Test
    fun `activating an agent makes it the default for new chats`() {
        val switched = agents.activate(AgentProfile.MAIN_AGENT_ID)
        assertEquals(main, switched.active)
        assertEquals(coder, switched.activate("coder").active)
    }

    @Test
    fun `activating an unknown agent changes nothing`() {
        assertSame(agents, agents.activate("ghost"))
    }
}
