package dev.huecoder.md3agent.data.settings

import dev.huecoder.md3agent.llm.models.ReasoningEffort
import kotlinx.serialization.Serializable

@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val extraHeaders: List<HeaderEntry> = emptyList(),
    val models: List<String> = emptyList(),
    val defaultModel: String = "",
)

@Serializable
data class HeaderEntry(val name: String, val value: String)

@Serializable
enum class AvatarStyle { CLASSIC, BOT }

@Serializable
enum class AvatarRestShape { CIRCLE, HEXAGON }

@Serializable
data class AgentProfile(
    val name: String = DEFAULT_AGENT_NAME,
    val systemPromptTemplate: String = DEFAULT_SYSTEM_PROMPT,
    val avatar: AvatarStyle = AvatarStyle.CLASSIC,
    val restShape: AvatarRestShape = AvatarRestShape.CIRCLE,
) {
    companion object {
        const val DEFAULT_AGENT_NAME = "Assistant"

        val DEFAULT_SYSTEM_PROMPT = """
            You are {agent_name}, a personal assistant on the user's Android device.
            Today is {weekday}, {date} ({timezone}). Device: {device}, Android {android}, language {locale}.
            Each user message ends with a <context> block holding the time and battery level at the moment it was sent.
            Keep answers to the point, reply in the user's language, and use markdown when it makes the answer easier to read.
        """.trimIndent()

        private val LEGACY_SYSTEM_PROMPT = """
            You are {agent_name}, a personal assistant on the user's Android device.
            It is {weekday}, {date}, {time} ({timezone}).
            Device: {device}, Android {android}, battery {battery}, language {locale}.
            Keep answers to the point, reply in the user's language, and use markdown when it makes the answer easier to read.
        """.trimIndent()
    }

    fun migrated(): AgentProfile =
        if (systemPromptTemplate == LEGACY_SYSTEM_PROMPT) copy(systemPromptTemplate = DEFAULT_SYSTEM_PROMPT) else this
}

@Serializable
data class NetworkSettings(
    val dohEnabled: Boolean = false,
    val dohUrl: String = "",
)

data class ModelSelection(val providerId: String, val modelId: String, val reasoningEffort: ReasoningEffort? = null)

data class AppSettings(
    val providers: List<ProviderProfile>,
    val activeSelection: ModelSelection?,
    val agent: AgentProfile,
    val network: NetworkSettings,
    val tools: ToolSettings,
    val chat: ChatSettings,
    val voice: VoiceSettings,
) {
    fun provider(id: String?): ProviderProfile? = providers.firstOrNull { it.id == id }
}
