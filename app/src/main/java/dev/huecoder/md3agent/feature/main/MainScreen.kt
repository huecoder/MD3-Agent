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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.feature.home.HomeScreen
import dev.huecoder.md3agent.feature.settings.SettingsDestinations
import dev.huecoder.md3agent.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.sign

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
    val thresholdPx = with(LocalDensity.current) { COLLAPSE_DISTANCE.toPx() }
    val collapseOnScroll = remember(thresholdPx) { CollapseOnScroll(thresholdPx) { expanded = it } }

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
private const val TAB_FADE_MS = 180
