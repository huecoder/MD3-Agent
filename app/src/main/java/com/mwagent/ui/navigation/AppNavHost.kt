package com.mwagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mwagent.feature.chat.ChatScreen
import com.mwagent.feature.main.MainScreen
import com.mwagent.feature.settings.AgentEditorScreen
import com.mwagent.feature.settings.AgentsScreen
import com.mwagent.data.files.LibraryLocation
import com.mwagent.data.files.LibraryRoot
import com.mwagent.feature.files.FolderScreen
import com.mwagent.feature.files.MediaScreen
import com.mwagent.feature.playground.PlaygroundScreen
import com.mwagent.feature.settings.AboutScreen
import com.mwagent.feature.settings.AppearanceScreen
import com.mwagent.feature.settings.ModelsScreen
import com.mwagent.feature.settings.RemindersScreen
import com.mwagent.feature.settings.DeviceAccessScreen
import com.mwagent.feature.settings.PhoneUseSettingsScreen
import com.mwagent.feature.settings.MemorySettingsScreen
import com.mwagent.feature.settings.NetworkSettingsScreen
import com.mwagent.feature.settings.ProviderEditorScreen
import com.mwagent.feature.settings.SettingsDestinations
import com.mwagent.feature.settings.ToolSettingsScreen
import com.mwagent.feature.settings.McpServerEditorScreen
import com.mwagent.feature.settings.McpServersScreen
import com.mwagent.feature.settings.VoiceProviderEditorScreen
import com.mwagent.feature.settings.VoiceSettingsScreen
import com.mwagent.ui.haptics.clickHaptics
import androidx.compose.ui.Modifier

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
        modifier = Modifier.clickHaptics(),
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.popEnter,
        popExitTransition = NavTransitions.popExit,
    ) {
        screen<HomeRoute>(navController) {
            MainScreen(
                onOpenChat = { navController.navigate(ChatRoute(it)) },
                onOpenMessage = { chatId, messageId -> navController.navigate(ChatRoute(chatId, focusMessageId = messageId)) },
                onOpenFolder = { root -> navController.navigate(FolderRoute(root.name)) },
                onOpenMedia = { navController.navigate(MediaRoute) },
                settingsDestinations = SettingsDestinations(
                    models = { navController.navigate(ModelsRoute) },
                    appearance = { navController.navigate(AppearanceRoute) },
                    about = { navController.navigate(AboutRoute) },
                    agent = { navController.navigate(AgentsRoute) },
                    memory = { navController.navigate(MemorySettingsRoute) },
                    network = { navController.navigate(NetworkSettingsRoute) },
                    tools = { navController.navigate(ToolSettingsRoute) },
                    reminders = { navController.navigate(RemindersRoute) },
                    voice = { navController.navigate(VoiceSettingsRoute) },
                    access = { navController.navigate(DeviceAccessRoute) },
                    phoneUse = { navController.navigate(PhoneUseRoute) },
                    mcp = { navController.navigate(McpServersRoute) },
                ),
            )
        }
        screen<ChatRoute>(navController) { entry ->
            val route = entry.toRoute<ChatRoute>()
            ChatScreen(chatId = route.chatId, focusMessageId = route.focusMessageId, onBack = navController::popBackStack)
        }
        screen<ProviderEditorRoute>(navController) { entry ->
            ProviderEditorScreen(providerId = entry.toRoute<ProviderEditorRoute>().providerId, onDone = navController::popBackStack)
        }
        screen<AgentsRoute>(navController) {
            AgentsScreen(onBack = navController::popBackStack, onEditAgent = { navController.navigate(AgentEditorRoute(it)) })
        }
        screen<AgentEditorRoute>(navController) { entry ->
            AgentEditorScreen(agentId = entry.toRoute<AgentEditorRoute>().agentId, onDone = navController::popBackStack)
        }
        screen<MemorySettingsRoute>(navController) { MemorySettingsScreen(onBack = navController::popBackStack) }
        screen<NetworkSettingsRoute>(navController) { NetworkSettingsScreen(onBack = navController::popBackStack) }
        screen<ToolSettingsRoute>(navController) { ToolSettingsScreen(onBack = navController::popBackStack) }
        screen<RemindersRoute>(navController) { RemindersScreen(onBack = navController::popBackStack) }
        screen<DeviceAccessRoute>(navController) { DeviceAccessScreen(onBack = navController::popBackStack) }
        screen<PhoneUseRoute>(navController) { PhoneUseSettingsScreen(onBack = navController::popBackStack) }
        screen<AppearanceRoute>(navController) { AppearanceScreen(onBack = navController::popBackStack) }
        screen<AboutRoute>(navController) {
            AboutScreen(onBack = navController::popBackStack, onSecret = { navController.navigate(PlaygroundRoute) })
        }
        screen<PlaygroundRoute>(navController) { PlaygroundScreen() }
        screen<MediaRoute>(navController) { MediaScreen(onBack = navController::popBackStack) }
        screen<FolderRoute>(navController) { entry ->
            val route = entry.toRoute<FolderRoute>()
            FolderScreen(
                location = route.toLocation(),
                onOpen = { navController.navigate(it.toRoute()) },
                onBack = navController::popBackStack,
            )
        }
        screen<ModelsRoute>(navController) {
            ModelsScreen(
                onBack = navController::popBackStack,
                onEditProvider = { navController.navigate(ProviderEditorRoute(it)) },
            )
        }
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

private fun FolderRoute.toLocation() = LibraryLocation(
    root = LibraryRoot.valueOf(root),
    segments = path.split(PATH_SEPARATOR).filter { it.isNotEmpty() },
    fromChats = fromChats,
)

private fun LibraryLocation.toRoute() = FolderRoute(root.name, segments.joinToString(PATH_SEPARATOR), fromChats)

private const val PATH_SEPARATOR = "/"

private inline fun <reified T : Any> NavGraphBuilder.screen(
    navController: NavController,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> BackPreviewCard(entry, navController) { content(entry) } }
