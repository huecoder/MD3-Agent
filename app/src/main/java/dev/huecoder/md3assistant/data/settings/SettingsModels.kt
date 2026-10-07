package dev.huecoder.md3assistant.data.settings

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
data class AgentProfile(
    val name: String = DEFAULT_AGENT_NAME,
    val systemPromptTemplate: String = DEFAULT_SYSTEM_PROMPT,
) {
    companion object {
        const val DEFAULT_AGENT_NAME = "Ассистент"

        val DEFAULT_SYSTEM_PROMPT = """
            Ты {agent_name}, персональный ассистент на Android-устройстве пользователя.
            Сейчас {weekday}, {date}, {time} ({timezone}).
            Устройство: {device}, Android {android}, батарея {battery}, язык {locale}.
            Отвечай по делу и используй markdown, когда он помогает читать ответ.
        """.trimIndent()
    }
}

@Serializable
data class NetworkSettings(
    val dohEnabled: Boolean = false,
    val dohUrl: String = "",
)

data class ModelSelection(val providerId: String, val modelId: String)

data class AppSettings(
    val providers: List<ProviderProfile>,
    val activeSelection: ModelSelection?,
    val agent: AgentProfile,
    val network: NetworkSettings,
    val tools: ToolSettings,
    val chat: ChatSettings,
    val speech: SpeechSettings,
) {
    fun provider(id: String?): ProviderProfile? = providers.firstOrNull { it.id == id }
}
