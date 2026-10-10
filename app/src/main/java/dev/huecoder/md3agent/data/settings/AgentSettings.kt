package dev.huecoder.md3agent.data.settings

import dev.huecoder.md3agent.data.chat.ChatTint
import kotlinx.serialization.Serializable

@Serializable
enum class AgentShape {
    CIRCLE, HEXAGON, SQUARE, SLANTED, ARCH, FAN, ARROW, SEMI_CIRCLE, OVAL, PILL, TRIANGLE, DIAMOND, CLAM_SHELL,
    PENTAGON, GEM, SUNNY, VERY_SUNNY, COOKIE_4, COOKIE_6, COOKIE_7, COOKIE_9, COOKIE_12, GHOSTISH, CLOVER_4,
    CLOVER_8, BURST, SOFT_BURST, BOOM, SOFT_BOOM, FLOWER, PUFFY, PUFFY_DIAMOND, PIXEL_CIRCLE, PIXEL_TRIANGLE, BUN, HEART,
}

@Serializable
data class AgentProfile(
    val id: String = MAIN_AGENT_ID,
    val name: String = DEFAULT_AGENT_NAME,
    val systemPromptTemplate: String = DEFAULT_SYSTEM_PROMPT,
    val shape: AgentShape = AgentShape.CIRCLE,
    val tint: ChatTint = ChatTint.THEME,
    val workingShapes: List<AgentShape> = DEFAULT_WORKING_SHAPES,
    val toolPermissions: Map<String, ToolPermission> = emptyMap(),
) {
    fun tools(base: ToolSettings): ToolSettings = base.copy(permissions = base.permissions + toolPermissions)

    companion object {
        const val MAIN_AGENT_ID = "main"
        const val DEFAULT_AGENT_NAME = "Assistant"
        const val MIN_WORKING_SHAPES = 2

        val DEFAULT_WORKING_SHAPES = listOf(
            AgentShape.COOKIE_7, AgentShape.COOKIE_9, AgentShape.GEM, AgentShape.COOKIE_12, AgentShape.CLOVER_8, AgentShape.COOKIE_6,
        )

        val DEFAULT_SYSTEM_PROMPT = """
            You are {agent_name}, a personal assistant on the user's Android device.
            Today is {weekday}, {date} ({timezone}). Device: {device}, Android {android}, language {locale}.
            Each user message ends with a <context> block holding the time and battery level at the moment it was sent.
            Keep answers to the point, reply in the user's language, and use markdown when it makes the answer easier to read.
        """.trimIndent()
    }
}

@Serializable
data class AgentSettings(
    val agents: List<AgentProfile> = listOf(AgentProfile()),
    val activeAgentId: String = AgentProfile.MAIN_AGENT_ID,
) {
    val active: AgentProfile
        get() = find(activeAgentId) ?: agents.first()

    fun find(id: String?): AgentProfile? = agents.firstOrNull { it.id == id }

    fun resolve(id: String?): AgentProfile = find(id) ?: active

    fun forChat(agentId: String?): AgentProfile = find(agentId ?: AgentProfile.MAIN_AGENT_ID) ?: active

    fun activate(id: String): AgentSettings = if (find(id) == null) this else copy(activeAgentId = id)

    fun upsert(agent: AgentProfile): AgentSettings {
        val index = agents.indexOfFirst { it.id == agent.id }
        return copy(agents = if (index == -1) agents + agent else agents.toMutableList().also { it[index] = agent })
    }

    fun remove(id: String): AgentSettings {
        val remaining = agents.filterNot { it.id == id }
        if (remaining.isEmpty()) return this
        return copy(agents = remaining, activeAgentId = if (activeAgentId == id) remaining.first().id else activeAgentId)
    }
}

@Serializable
internal data class LegacyAgentProfile(
    val name: String = AgentProfile.DEFAULT_AGENT_NAME,
    val systemPromptTemplate: String = AgentProfile.DEFAULT_SYSTEM_PROMPT,
    val restShape: String = LEGACY_CIRCLE,
) {
    fun toAgents(): AgentSettings {
        val prompt = if (systemPromptTemplate == LEGACY_SYSTEM_PROMPT) AgentProfile.DEFAULT_SYSTEM_PROMPT else systemPromptTemplate
        val shape = if (restShape == LEGACY_HEXAGON) AgentShape.HEXAGON else AgentShape.CIRCLE
        return AgentSettings(listOf(AgentProfile(name = name, systemPromptTemplate = prompt, shape = shape)))
    }
}

private const val LEGACY_CIRCLE = "CIRCLE"
private const val LEGACY_HEXAGON = "HEXAGON"

private val LEGACY_SYSTEM_PROMPT = """
    You are {agent_name}, a personal assistant on the user's Android device.
    It is {weekday}, {date}, {time} ({timezone}).
    Device: {device}, Android {android}, battery {battery}, language {locale}.
    Keep answers to the point, reply in the user's language, and use markdown when it makes the answer easier to read.
""".trimIndent()
