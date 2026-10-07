package dev.huecoder.md3assistant.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Open_in_new
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.feature.chat.ChatInput
import dev.huecoder.md3assistant.feature.chat.ChatViewModel
import dev.huecoder.md3assistant.feature.chat.MessageList
import dev.huecoder.md3assistant.feature.chat.appendDictated
import dev.huecoder.md3assistant.feature.chat.rememberDictationControls
import dev.huecoder.md3assistant.ui.components.AgentAvatar
import dev.huecoder.md3assistant.ui.components.appViewModel
import dev.huecoder.md3assistant.ui.text.UiText
import dev.huecoder.md3assistant.voice.DictationViewModel

private val SheetShape = RoundedCornerShape(36.dp)
private const val SCRIM_ALPHA = 0.32f
private const val MAX_CONVERSATION_HEIGHT_FRACTION = 0.55f

@Composable
fun AssistantOverlay(activation: Int, onDismiss: () -> Unit, onOpenInApp: (chatId: String?) -> Unit) {
    val viewModel = appViewModel(key = "overlay-$activation") { ChatViewModel(null, it.chats, it.conversations, it.attachments, it.settings, it.applicationScope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember(activation) { mutableStateOf("") }
    val dictationModel = appViewModel(key = "overlay-dictation-$activation") { DictationViewModel(it.newVoiceRecorder(), it.transcriptions, it.settings) }
    val dictation = rememberDictationControls(
        model = dictationModel,
        onTranscript = { input = appendDictated(input, it) },
        onPermissionMissing = { dictationModel.fail(UiText.Resource(R.string.overlay_microphone_denied)) },
    )
    val focusRequester = remember { FocusRequester() }
    val visibility = remember(activation) { MutableTransitionState(false).apply { targetState = true } }
    val maxConversationHeight = LocalConfiguration.current.screenHeightDp.dp * MAX_CONVERSATION_HEIGHT_FRACTION

    LaunchedEffect(activation) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding().imePadding().navigationBarsPadding(),
        ) {
            Surface(
                shape = SheetShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    OverlayHeader(
                        agentName = state.agentName,
                        isWorking = state.isStreaming,
                        canOpen = state.chatId != null,
                        onOpenInApp = { onOpenInApp(state.chatId) },
                    )
                    if (state.messages.isNotEmpty()) {
                        MessageList(
                            messages = state.messages,
                            showUsage = state.showTokenUsage,
                            onRegenerate = viewModel::regenerate,
                            onSend = viewModel::send,
                            modifier = Modifier.fillMaxWidth().heightIn(max = maxConversationHeight).weight(1f, fill = false),
                            bottomPadding = 8.dp,
                        )
                    }
                    ChatInput(
                        text = input,
                        onTextChange = { input = it },
                        isStreaming = state.isStreaming,
                        canSend = state.canSend(input),
                        onSend = {
                            viewModel.send(input)
                            input = ""
                        },
                        onStop = viewModel::stop,
                        dictation = dictation,
                        focusRequester = focusRequester,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayHeader(agentName: String, isWorking: Boolean, canOpen: Boolean, onOpenInApp: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
    ) {
        AgentAvatar(isWorking = isWorking, size = 40.dp)
        Text(agentName, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenInApp, enabled = canOpen, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = stringResource(R.string.overlay_open_in_app))
        }
    }
}
