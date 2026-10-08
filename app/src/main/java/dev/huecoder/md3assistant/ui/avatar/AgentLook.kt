package dev.huecoder.md3assistant.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.data.settings.AvatarRestShape
import dev.huecoder.md3assistant.data.settings.AvatarStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AgentLook(val style: AvatarStyle = AvatarStyle.CLASSIC, val restShape: AvatarRestShape = AvatarRestShape.CIRCLE)

val LocalAgentLook = compositionLocalOf { AgentLook() }

@Composable
fun ProvideAgentLook(content: @Composable () -> Unit) {
    val settings = (LocalContext.current.applicationContext as App).container.settings
    val looks = remember(settings) { settings.settings.map { AgentLook(it.agent.avatar, it.agent.restShape) }.distinctUntilChanged() }
    val look by looks.collectAsStateWithLifecycle(AgentLook())
    CompositionLocalProvider(LocalAgentLook provides look, content = content)
}
