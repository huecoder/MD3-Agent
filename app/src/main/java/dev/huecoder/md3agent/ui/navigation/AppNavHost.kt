package dev.huecoder.md3agent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.huecoder.md3agent.feature.chat.ChatScreen
import dev.huecoder.md3agent.feature.main.MainScreen
import dev.huecoder.md3agent.feature.settings.AgentSettingsScreen
import dev.huecoder.md3agent.feature.settings.ChatSettingsScreen
import dev.huecoder.md3agent.feature.settings.DeviceAccessScreen
import dev.huecoder.md3agent.feature.settings.MemorySettingsScreen
import dev.huecoder.md3agent.feature.settings.NetworkSettingsScreen
import dev.huecoder.md3agent.feature.settings.ProviderEditorScreen
import dev.huecoder.md3agent.feature.settings.SettingsDestinations
import dev.huecoder.md3agent.feature.settings.ToolSettingsScreen
import dev.huecoder.md3agent.feature.settings.McpServerEditorScreen
import dev.huecoder.md3agent.feature.settings.McpServersScreen
import dev.huecoder.md3agent.feature.settings.VoiceProviderEditorScreen
import dev.huecoder.md3agent.feature.settings.VoiceSettingsScreen

@Composable
fun AppNavHost(chatToOpen: String?, onChatOpened: () -> Unit) {
    val navController = rememberNavController()
    LaunchedEffect(chatToOpen) {
        val chatId = chatToOpen ?: return@LaunchedEffect
        navController.navigate(ChatRoute(chatId)) { popUpTo(HomeRoute) }
        onChatOpened()
    }
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.popEnter,
        popExitTransition = NavTransitions.popExit,
    ) {
        screen<HomeRoute>(navController) {
            MainScreen(
                onOpenChat = { navController.navigate(ChatRoute(it)) },
                settingsDestinations = SettingsDestinations(
                    editProvider = { navController.navigate(ProviderEditorRoute(it)) },
                    agent = { navController.navigate(AgentSettingsRoute) },
                    memory = { navController.navigate(MemorySettingsRoute) },
                    network = { navController.navigate(NetworkSettingsRoute) },
                    tools = { navController.navigate(ToolSettingsRoute) },
                    chat = { navController.navigate(ChatSettingsRoute) },
                    voice = { navController.navigate(VoiceSettingsRoute) },
                    access = { navController.navigate(DeviceAccessRoute) },
                    mcp = { navController.navigate(McpServersRoute) },
                ),
            )
        }
        screen<ChatRoute>(navController) { entry ->
            ChatScreen(chatId = entry.toRoute<ChatRoute>().chatId, onBack = navController::popBackStack)
        }
        screen<ProviderEditorRoute>(navController) { entry ->
            ProviderEditorScreen(providerId = entry.toRoute<ProviderEditorRoute>().providerId, onDone = navController::popBackStack)
        }
        screen<AgentSettingsRoute>(navController) { AgentSettingsScreen(onBack = navController::popBackStack) }
        screen<MemorySettingsRoute>(navController) { MemorySettingsScreen(onBack = navController::popBackStack) }
        screen<NetworkSettingsRoute>(navController) { NetworkSettingsScreen(onBack = navController::popBackStack) }
        screen<ToolSettingsRoute>(navController) { ToolSettingsScreen(onBack = navController::popBackStack) }
        screen<DeviceAccessRoute>(navController) { DeviceAccessScreen(onBack = navController::popBackStack) }
        screen<ChatSettingsRoute>(navController) { ChatSettingsScreen(onBack = navController::popBackStack) }
        screen<VoiceSettingsRoute>(navController) {
            VoiceSettingsScreen(
                onBack = navController::popBackStack,
                onEditProvider = { navController.navigate(VoiceProviderEditorRoute(it)) },
            )
        }
        screen<McpServersRoute>(navController) {
            McpServersScreen(
                onBack = navController::popBackStack,
                onEditServer = { navController.navigate(McpServerEditorRoute(it)) },
            )
        }
        screen<McpServerEditorRoute>(navController) { entry ->
            McpServerEditorScreen(serverId = entry.toRoute<McpServerEditorRoute>().serverId, onDone = navController::popBackStack)
        }
        screen<VoiceProviderEditorRoute>(navController) { entry ->
            VoiceProviderEditorScreen(providerId = entry.toRoute<VoiceProviderEditorRoute>().providerId, onDone = navController::popBackStack)
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.screen(
    navController: NavController,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> BackPreviewCard(entry, navController) { content(entry) } }
