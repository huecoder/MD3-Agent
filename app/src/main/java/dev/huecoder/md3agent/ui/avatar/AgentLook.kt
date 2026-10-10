package dev.huecoder.md3agent.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AgentShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AgentLook(
    val shape: AgentShape = AgentShape.CIRCLE,
    val tint: ChatTint = ChatTint.THEME,
    val workingShapes: List<AgentShape> = AgentProfile.DEFAULT_WORKING_SHAPES,
) {
    companion object {
        fun of(agent: AgentProfile) = AgentLook(agent.shape, agent.tint, agent.workingShapes)
    }
}

val LocalAgentLook = compositionLocalOf { AgentLook() }

@Composable
fun ProvideAgentLook(agentId: String? = null, content: @Composable () -> Unit) {
    val settings = (LocalContext.current.applicationContext as App).container.settings
    val looks = remember(settings, agentId) { settings.settings.map { AgentLook.of(it.agent(agentId)) }.distinctUntilChanged() }
    val look by looks.collectAsStateWithLifecycle(AgentLook())
    CompositionLocalProvider(LocalAgentLook provides look, content = content)
}
