package dev.huecoder.md3agent.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.feature.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.huecoder.md3agent.feature.chat.ChatScreen
import dev.huecoder.md3agent.feature.main.MainScreen
import dev.huecoder.md3agent.feature.mode.ModeScreen
import dev.huecoder.md3agent.feature.settings.AgentEditorScreen
import dev.huecoder.md3agent.feature.settings.AssistantInvokeSettingsScreen
import dev.huecoder.md3agent.feature.settings.AgentsScreen
import dev.huecoder.md3agent.data.files.LibraryLocation
import dev.huecoder.md3agent.data.files.LibraryRoot
import dev.huecoder.md3agent.feature.files.FolderScreen
import dev.huecoder.md3agent.feature.files.MediaScreen
import dev.huecoder.md3agent.feature.playground.PlaygroundScreen
import dev.huecoder.md3agent.feature.settings.AboutScreen
import dev.huecoder.md3agent.feature.settings.AppearanceScreen
import dev.huecoder.md3agent.feature.settings.ChatSettingsScreen
import dev.huecoder.md3agent.feature.settings.ModelsScreen
import dev.huecoder.md3agent.feature.settings.RemindersScreen
import dev.huecoder.md3agent.feature.settings.DeviceAccessScreen
import dev.huecoder.md3agent.feature.settings.PhoneUseSettingsScreen
import dev.huecoder.md3agent.feature.settings.MemorySettingsScreen
import dev.huecoder.md3agent.feature.settings.NetworkSettingsScreen
import dev.huecoder.md3agent.feature.settings.NotificationSettingsScreen
import dev.huecoder.md3agent.feature.settings.ProviderEditorScreen
import dev.huecoder.md3agent.feature.settings.SettingsDestinations
import dev.huecoder.md3agent.feature.settings.ToolSettingsScreen
import dev.huecoder.md3agent.feature.settings.McpServerEditorScreen
import dev.huecoder.md3agent.feature.settings.McpServersScreen
import dev.huecoder.md3agent.feature.settings.VoiceProviderEditorScreen
import dev.huecoder.md3agent.feature.settings.VoiceSettingsScreen
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.feature.home.ChatFilter
import dev.huecoder.md3agent.feature.home.HomeScreen

@Composable
fun AppNavHost(chatToOpen: String?, onChatOpened: () -> Unit, target: LaunchTarget?, onTargetHandled: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val onboarded by remember(container) { container.settings.settings.map { it.mode.onboarded }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null)
    val start = remember(onboarded != null) { onboarded?.let { if (it) HomeRoute else OnboardingRoute() } }
    if (start == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }
    AppNavGraph(start, chatToOpen, onChatOpened, target, onTargetHandled)
}

@Composable
private fun AppNavGraph(start: Any, chatToOpen: String?, onChatOpened: () -> Unit, target: LaunchTarget?, onTargetHandled: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backMotion = rememberBackMotion(navController)
    var homeRequest by remember { mutableStateOf<LaunchTarget?>(null) }
    LaunchedEffect(chatToOpen) {
        val chatId = chatToOpen ?: return@LaunchedEffect
        navController.navigate(ChatRoute(chatId)) { popUpTo(HomeRoute) }
        onChatOpened()
    }
    LaunchedEffect(target) {
        val requested = target ?: return@LaunchedEffect
        navController.popBackStack(HomeRoute, inclusive = false)
        homeRequest = requested
        onTargetHandled()
    }
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = Modifier.backStage(backMotion).clickHaptics(),
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.popEnter,
        popExitTransition = NavTransitions.popExit,
        predictivePopEnterTransition = NavTransitions.predictivePopEnter,
        predictivePopExitTransition = NavTransitions.predictivePopExit,
    ) {
        screen<HomeRoute>(backMotion) {
            MainScreen(
                request = homeRequest,
                onRequestHandled = { homeRequest = null },
                onOpenChat = { navController.navigate(ChatRoute(it)) },
                onOpenMessage = { chatId, messageId -> navController.navigate(ChatRoute(chatId, focusMessageId = messageId)) },
                onOpenFolder = { root -> navController.navigate(FolderRoute(root.name)) },
                onOpenMedia = { navController.navigate(MediaRoute) },
                onOpenCollection = { filter ->
                    when (filter) {
                        is ChatFilter.Folder -> navController.navigate(ChatCollectionRoute(CollectionKind.FOLDER, filter.id))
                        ChatFilter.Archive -> navController.navigate(ChatCollectionRoute(CollectionKind.ARCHIVE))
                        ChatFilter.Trash -> navController.navigate(ChatCollectionRoute(CollectionKind.TRASH))
                        ChatFilter.All -> Unit
                    }
                },
                settingsDestinations = SettingsDestinations(
                    models = { navController.navigate(ModelsRoute) },
                    appearance = { navController.navigate(AppearanceRoute) },
                    chat = { navController.navigate(ChatSettingsRoute) },
                    about = { navController.navigate(AboutRoute) },
                    agent = { navController.navigate(AgentsRoute) },
                    memory = { navController.navigate(MemorySettingsRoute) },
                    network = { navController.navigate(NetworkSettingsRoute) },
                    tools = { navController.navigate(ToolSettingsRoute) },
                    reminders = { navController.navigate(RemindersRoute) },
                    voice = { navController.navigate(VoiceSettingsRoute) },
                    access = { navController.navigate(DeviceAccessRoute) },
                    notifications = { navController.navigate(NotificationSettingsRoute) },
                    assistantInvoke = { navController.navigate(AssistantInvokeRoute) },
                    phoneUse = { navController.navigate(PhoneUseRoute) },
                    mcp = { navController.navigate(McpServersRoute) },
                    mode = { navController.navigate(ModeRoute) },
                    editAgent = { navController.navigate(AgentEditorRoute(it)) },
                ),
            )
        }
        screen<ChatCollectionRoute>(backMotion) { entry ->
            val route = entry.toRoute<ChatCollectionRoute>()
            HomeScreen(
                onOpenChat = { navController.navigate(ChatRoute(it)) },
                onOpenMessage = { chatId, messageId -> navController.navigate(ChatRoute(chatId, focusMessageId = messageId)) },
                onOpenSettings = navController::popBackStack,
                collection = when (route.kind) {
                    CollectionKind.FOLDER -> ChatFilter.Folder(route.folderId.orEmpty())
                    CollectionKind.ARCHIVE -> ChatFilter.Archive
                    CollectionKind.TRASH -> ChatFilter.Trash
                },
                onBack = navController::popBackStack,
            )
        }
        screen<ChatRoute>(backMotion) { entry ->
            val route = entry.toRoute<ChatRoute>()
            ChatScreen(
                chatId = route.chatId,
                focusMessageId = route.focusMessageId,
                onBack = navController::popBackStack,
                onOpenChat = { navController.navigate(ChatRoute(it)) },
            )
        }
        screen<ProviderEditorRoute>(backMotion) { entry ->
            ProviderEditorScreen(providerId = entry.toRoute<ProviderEditorRoute>().providerId, onDone = navController::popBackStack)
        }
        screen<AgentsRoute>(backMotion) {
            AgentsScreen(onBack = navController::popBackStack, onEditAgent = { navController.navigate(AgentEditorRoute(it)) })
        }
        screen<AgentEditorRoute>(backMotion) { entry ->
            AgentEditorScreen(agentId = entry.toRoute<AgentEditorRoute>().agentId, onDone = navController::popBackStack)
        }
        screen<ModeRoute>(backMotion) {
            ModeScreen(
                onBack = navController::popBackStack,
                onRetakeSurvey = { navController.navigate(OnboardingRoute(retake = true)) },
                onRetakeOnboarding = { navController.navigate(OnboardingRoute(replay = true)) },
            )
        }
        screen<OnboardingRoute>(backMotion) { entry ->
            val route = entry.toRoute<OnboardingRoute>()
            OnboardingScreen(
                retake = route.retake,
                onCustomProvider = { navController.navigate(ProviderEditorRoute()) },
                onFinished = { openChat ->
                    if (route.retake || route.replay) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                        if (openChat) scope.launch { if (container.settings.current().providers.isNotEmpty()) navController.navigate(ChatRoute()) }
                    }
                },
            )
        }
        screen<MemorySettingsRoute>(backMotion) { MemorySettingsScreen(onBack = navController::popBackStack) }
        screen<NetworkSettingsRoute>(backMotion) { NetworkSettingsScreen(onBack = navController::popBackStack) }
        screen<ToolSettingsRoute>(backMotion) { ToolSettingsScreen(onBack = navController::popBackStack) }
        screen<RemindersRoute>(backMotion) { RemindersScreen(onBack = navController::popBackStack) }
        screen<DeviceAccessRoute>(backMotion) { DeviceAccessScreen(onBack = navController::popBackStack) }
        screen<NotificationSettingsRoute>(backMotion) { NotificationSettingsScreen(onBack = navController::popBackStack) }
        screen<AssistantInvokeRoute>(backMotion) { AssistantInvokeSettingsScreen(onBack = navController::popBackStack) }
        screen<PhoneUseRoute>(backMotion) { PhoneUseSettingsScreen(onBack = navController::popBackStack) }
        screen<AppearanceRoute>(backMotion) { AppearanceScreen(onBack = navController::popBackStack) }
        screen<ChatSettingsRoute>(backMotion) { ChatSettingsScreen(onBack = navController::popBackStack) }
        screen<AboutRoute>(backMotion) {
            AboutScreen(
                onBack = navController::popBackStack,
                onSecret = { navController.navigate(PlaygroundRoute) },
            )
        }
        screen<PlaygroundRoute>(backMotion) { PlaygroundScreen() }
        screen<MediaRoute>(backMotion) { MediaScreen(onBack = navController::popBackStack) }
        screen<FolderRoute>(backMotion) { entry ->
            val route = entry.toRoute<FolderRoute>()
            FolderScreen(
                location = route.toLocation(),
                onOpen = { navController.navigate(it.toRoute()) },
                onBack = navController::popBackStack,
            )
        }
        screen<ModelsRoute>(backMotion) {
            ModelsScreen(
                onBack = navController::popBackStack,
                onEditProvider = { navController.navigate(ProviderEditorRoute(it)) },
            )
        }
        screen<VoiceSettingsRoute>(backMotion) {
            VoiceSettingsScreen(
                onBack = navController::popBackStack,
                onEditProvider = { navController.navigate(VoiceProviderEditorRoute(it)) },
            )
        }
        screen<McpServersRoute>(backMotion) {
            McpServersScreen(
                onBack = navController::popBackStack,
                onEditServer = { navController.navigate(McpServerEditorRoute(it)) },
            )
        }
        screen<McpServerEditorRoute>(backMotion) { entry ->
            McpServerEditorScreen(serverId = entry.toRoute<McpServerEditorRoute>().serverId, onDone = navController::popBackStack)
        }
        screen<VoiceProviderEditorRoute>(backMotion) { entry ->
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
    motion: BackMotion,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> BackScreen(entry, motion) { content(entry) } }
