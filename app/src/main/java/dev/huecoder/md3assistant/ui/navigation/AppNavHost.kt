package dev.huecoder.md3assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.huecoder.md3assistant.feature.chat.ChatScreen
import dev.huecoder.md3assistant.feature.home.HomeScreen
import dev.huecoder.md3assistant.feature.settings.AgentSettingsScreen
import dev.huecoder.md3assistant.feature.settings.ChatSettingsScreen
import dev.huecoder.md3assistant.feature.settings.NetworkSettingsScreen
import dev.huecoder.md3assistant.feature.settings.ProviderEditorScreen
import dev.huecoder.md3assistant.feature.settings.SettingsScreen
import dev.huecoder.md3assistant.feature.settings.ToolSettingsScreen

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
        composable<HomeRoute> {
            HomeScreen(
                onOpenChat = { navController.navigate(ChatRoute(it)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<ChatRoute> { entry ->
            ChatScreen(chatId = entry.toRoute<ChatRoute>().chatId, onBack = navController::popBackStack)
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = navController::popBackStack,
                onEditProvider = { navController.navigate(ProviderEditorRoute(it)) },
                onOpenAgent = { navController.navigate(AgentSettingsRoute) },
                onOpenNetwork = { navController.navigate(NetworkSettingsRoute) },
                onOpenTools = { navController.navigate(ToolSettingsRoute) },
                onOpenChat = { navController.navigate(ChatSettingsRoute) },
            )
        }
        composable<ProviderEditorRoute> { entry ->
            ProviderEditorScreen(providerId = entry.toRoute<ProviderEditorRoute>().providerId, onDone = navController::popBackStack)
        }
        composable<AgentSettingsRoute> { AgentSettingsScreen(onBack = navController::popBackStack) }
        composable<NetworkSettingsRoute> { NetworkSettingsScreen(onBack = navController::popBackStack) }
        composable<ToolSettingsRoute> { ToolSettingsScreen(onBack = navController::popBackStack) }
        composable<ChatSettingsRoute> { ChatSettingsScreen(onBack = navController::popBackStack) }
    }
}
