package com.mwagent.data.settings

import com.mwagent.llm.models.ReasoningEffort
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

data class AppSettings(
    val providers: List<ProviderProfile>,
    val activeSelection: ModelSelection?,
    val agents: AgentSettings,
    val network: NetworkSettings,
    val tools: ToolSettings,
    val chat: ChatSettings,
    val voice: VoiceSettings,
    val ui: InterfaceSettings,
) {
    fun provider(id: String?): ProviderProfile? = providers.firstOrNull { it.id == id }

    fun agent(id: String?): AgentProfile = agents.resolve(id)
}
