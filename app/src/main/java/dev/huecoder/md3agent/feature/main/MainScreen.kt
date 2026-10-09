package dev.huecoder.md3agent.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun MainScreen(
    onOpenChat: (chatId: String?) -> Unit,
    onOpenMessage: (chatId: String, messageId: String) -> Unit,
    onOpenFolder: (LibraryRoot) -> Unit,
    onOpenMedia: () -> Unit,
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
    var expanded by rememberSaveable { mutableStateOf(true) }
    val thresholdPx = with(LocalDensity.current) { COLLAPSE_DISTANCE.toPx() }
    val collapseOnScroll = remember(thresholdPx) { CollapseOnScroll(thresholdPx) { expanded = it } }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect {
            haptics.tick()
            expanded = true
        }
    }

    BackHandler(enabled = tab != MainTab.CHATS) { open(MainTab.CHATS) }

    Box(Modifier.fillMaxSize().nestedScroll(collapseOnScroll)) {
        HorizontalPager(state = pager, key = { MainTab.entries[it] }, modifier = Modifier.fillMaxSize()) { page ->
            when (MainTab.entries[page]) {
                MainTab.CHATS -> HomeScreen(onOpenChat = onOpenChat, onOpenMessage = onOpenMessage, onOpenSettings = { open(MainTab.SETTINGS) })
                MainTab.FILES -> FilesScreen(onOpenFolder = onOpenFolder, onOpenMedia = onOpenMedia)
                MainTab.SETTINGS -> SettingsScreen(destinations = settingsDestinations)
            }
        }
        MainNavBar(
            selected = tab,
            expanded = expanded,
            onSelect = { open(it) },
            onNewChat = {
                haptics.tap()
                if (hasProvider) onOpenChat(null) else open(MainTab.SETTINGS)
            },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp),
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
