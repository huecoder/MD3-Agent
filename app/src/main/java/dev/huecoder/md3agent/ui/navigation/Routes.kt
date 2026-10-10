package dev.huecoder.md3agent.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChatRoute(val chatId: String? = null, val focusMessageId: String? = null)

@Serializable
data class ProviderEditorRoute(val providerId: String? = null)

@Serializable
data object AgentsRoute

@Serializable
data class AgentEditorRoute(val agentId: String? = null)

@Serializable
data object NetworkSettingsRoute

@Serializable
data object MemorySettingsRoute

@Serializable
data object ToolSettingsRoute

@Serializable
data object RemindersRoute

@Serializable
data object AppearanceRoute

@Serializable
data object ModelsRoute

@Serializable
data object AboutRoute

@Serializable
data object PlaygroundRoute

@Serializable
data object VoiceSettingsRoute

@Serializable
data class VoiceProviderEditorRoute(val providerId: String? = null)

@Serializable
data object DeviceAccessRoute

@Serializable
data object McpServersRoute

@Serializable
data class McpServerEditorRoute(val serverId: String? = null)

@Serializable
data class FolderRoute(val root: String, val path: String = "", val fromChats: Boolean = false)

@Serializable
data object MediaRoute
