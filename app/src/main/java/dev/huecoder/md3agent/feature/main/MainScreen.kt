package dev.huecoder.md3agent.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import dev.huecoder.md3agent.feature.home.ChatFilter
import dev.huecoder.md3agent.feature.home.FolderEditorSheet
import dev.huecoder.md3agent.feature.home.HomeViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.data.files.LibraryRoot
import dev.huecoder.md3agent.feature.files.FilesScreen
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.feature.home.HomeScreen
import dev.huecoder.md3agent.feature.settings.SettingsDestinations
import dev.huecoder.md3agent.feature.settings.SettingsScreen
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.bottomEdgeFade
import dev.huecoder.md3agent.ui.navigation.LaunchTarget
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun MainScreen(
    request: LaunchTarget?,
    onRequestHandled: () -> Unit,
    onOpenChat: (chatId: String?) -> Unit,
    onOpenMessage: (chatId: String, messageId: String) -> Unit,
    onOpenFolder: (LibraryRoot) -> Unit,
    onOpenMedia: () -> Unit,
    onOpenCollection: (ChatFilter) -> Unit,
    settingsDestinations: SettingsDestinations,
) {
    val container = (LocalContext.current.applicationContext as App).container
    val hasProvider by remember(container) { container.settings.settings.map { it.providers.isNotEmpty() } }
        .collectAsStateWithLifecycle(initialValue = true)
    val pager = rememberPagerState { MainTab.entries.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val tab = MainTab.entries[pager.targetPage]
    val open = { target: MainTab -> scope.launch { pager.animateScrollToPage(target.ordinal) } }
    val home = appViewModel { HomeViewModel(it.chats, it.settings, it.conversations) }
    val homeState by home.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var creatingFolder by remember { mutableStateOf(false) }
    val reselects = remember { MutableSharedFlow<MainTab>(extraBufferCapacity = 1) }
    val openFromDrawer = { filter: ChatFilter ->
        scope.launch { drawerState.close() }
        onOpenCollection(filter)
    }
    val updatePrompt = appViewModel { UpdatePromptViewModel(it.updates, it.settings) }
    val updateOffer by updatePrompt.offer.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val thresholdPx = with(LocalDensity.current) { COLLAPSE_DISTANCE.toPx() }
    val collapseOnScroll = remember(thresholdPx) { CollapseOnScroll(thresholdPx) { expanded = it } }

    LaunchedEffect(request) {
        when (request ?: return@LaunchedEffect) {
            LaunchTarget.NEW_CHAT -> if (hasProvider) onOpenChat(null) else open(MainTab.SETTINGS)
            LaunchTarget.FILES -> open(MainTab.FILES)
            LaunchTarget.SETTINGS -> open(MainTab.SETTINGS)
        }
        onRequestHandled()
    }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect {
            haptics.tick()
            expanded = true
        }
    }

    BackHandler(enabled = tab != MainTab.CHATS) { open(MainTab.CHATS) }

    updateOffer?.let { UpdatePromptDialog(it, updatePrompt::dismiss) }

    CompositionLocalProvider(LocalTabReselects provides reselects) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ChatsDrawer(
                    folders = homeState.folders,
                    onFolder = { openFromDrawer(ChatFilter.Folder(it.id)) },
                    onNewFolder = {
                        scope.launch { drawerState.close() }
                        creatingFolder = true
                    },
                    onArchive = { openFromDrawer(ChatFilter.Archive) },
                    onTrash = { openFromDrawer(ChatFilter.Trash) },
                )
            },
        ) {
            Box(Modifier.fillMaxSize().nestedScroll(collapseOnScroll)) {
                HorizontalPager(state = pager, key = { MainTab.entries[it] }, modifier = Modifier.fillMaxSize().bottomEdgeFade()) { page ->
                    when (MainTab.entries[page]) {
                        MainTab.CHATS -> HomeScreen(
                            onOpenChat = onOpenChat,
                            onOpenMessage = onOpenMessage,
                            onOpenSettings = { open(MainTab.SETTINGS) },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                        )
                        MainTab.FILES -> FilesScreen(onOpenFolder = onOpenFolder, onOpenMedia = onOpenMedia)
                        MainTab.SETTINGS -> SettingsScreen(destinations = settingsDestinations)
                    }
                }
                MainNavBar(
                    selected = tab,
                    expanded = expanded,
                    onSelect = { target -> if (target == tab) reselects.tryEmit(target) else open(target) },
                    onNewChat = {
                        haptics.tap()
                        if (hasProvider) onOpenChat(null) else open(MainTab.SETTINGS)
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }

    if (creatingFolder) {
        FolderEditorSheet(
            folder = null,
            onSave = { name, look -> home.createFolder(name, look); creatingFolder = false },
            onDismiss = { creatingFolder = false },
        )
    }
}

private class CollapseOnScroll(
    private val thresholdPx: Float,
    private val onExpandedChange: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var travelled = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val delta = consumed.y
        if (delta == 0f) return Offset.Zero
        if (delta.sign != travelled.sign) travelled = 0f
        travelled += delta
        if (abs(travelled) >= thresholdPx) {
            onExpandedChange(travelled > 0f)
            travelled = 0f
        }
        return Offset.Zero
    }
}

private val COLLAPSE_DISTANCE = 48.dp
