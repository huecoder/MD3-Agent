package dev.huecoder.md3assistant.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChatRoute(val chatId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data class ProviderEditorRoute(val providerId: String? = null)

@Serializable
data object AgentSettingsRoute

@Serializable
data object NetworkSettingsRoute

@Serializable
data object ToolSettingsRoute

@Serializable
data object ChatSettingsRoute
