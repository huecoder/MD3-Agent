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
    val format: ApiFormat = ApiFormat.OPENAI,
)

@Serializable
data class HeaderEntry(val name: String, val value: String)

@Serializable
data class NetworkSettings(
    val dohEnabled: Boolean = false,
    val dohUrl: String = "",
)

data class ModelSelection(val providerId: String, val modelId: String, val reasoningEffort: ReasoningEffort? = null)

@Serializable
data class RememberedModel(val modelId: String, val reasoningEffort: String? = null) {
    fun selectionIn(providerId: String): ModelSelection =
        ModelSelection(providerId, modelId, reasoningEffort?.let(ReasoningEffort::fromWire))

    companion object {
        fun of(selection: ModelSelection) = RememberedModel(selection.modelId, selection.reasoningEffort?.wireName)
    }
}

data class AppSettings(
    val providers: List<ProviderProfile>,
    val activeSelection: ModelSelection?,
    val rememberedModels: Map<String, RememberedModel> = emptyMap(),
    val agents: AgentSettings,
    val network: NetworkSettings,
    val tools: ToolSettings,
    val chat: ChatSettings,
    val voice: VoiceSettings,
    val ui: InterfaceSettings,
    val notifications: NotificationSettings = NotificationSettings(),
    val assistant: AssistantSettings = AssistantSettings(),
    val mode: ModeSettings = ModeSettings(),
) {
    fun provider(id: String?): ProviderProfile? = providers.firstOrNull { it.id == id }

    fun rememberedIn(providerId: String?): ModelSelection? = rememberedModels[providerId]?.selectionIn(providerId.orEmpty())

    fun agent(id: String?): AgentProfile = agents.resolve(id)
}
