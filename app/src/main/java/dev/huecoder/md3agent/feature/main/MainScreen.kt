package dev.huecoder.md3agent.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.feature.home.HomeScreen
import dev.huecoder.md3agent.feature.settings.SettingsDestinations
import dev.huecoder.md3agent.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.map

@Composable
fun MainScreen(
    onOpenChat: (chatId: String?) -> Unit,
    settingsDestinations: SettingsDestinations,
) {
    val container = (LocalContext.current.applicationContext as App).container
    val hasProvider by remember(container) { container.settings.settings.map { it.providers.isNotEmpty() } }
        .collectAsStateWithLifecycle(initialValue = true)
    var tab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val collapseOnScroll = remember { CollapseOnScroll { expanded = it } }

    BackHandler(enabled = tab != MainTab.CHATS) { tab = MainTab.CHATS }

    Box(Modifier.fillMaxSize().nestedScroll(collapseOnScroll)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(TAB_FADE_MS)) togetherWith fadeOut(tween(TAB_FADE_MS)) },
            label = "mainTab",
        ) { current ->
            when (current) {
                MainTab.CHATS -> HomeScreen(onOpenChat = onOpenChat, onOpenSettings = { tab = MainTab.SETTINGS })
                MainTab.SETTINGS -> SettingsScreen(destinations = settingsDestinations)
            }
        }
        MainNavBar(
            selected = tab,
            expanded = expanded,
            onSelect = { selected -> tab = selected; expanded = true },
            onNewChat = { if (hasProvider) onOpenChat(null) else tab = MainTab.SETTINGS },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

private class CollapseOnScroll(private val onExpandedChange: (Boolean) -> Unit) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        when {
            available.y < -SCROLL_THRESHOLD -> onExpandedChange(false)
            available.y > SCROLL_THRESHOLD -> onExpandedChange(true)
        }
        return Offset.Zero
    }
}

private const val SCROLL_THRESHOLD = 4f
private const val TAB_FADE_MS = 180
