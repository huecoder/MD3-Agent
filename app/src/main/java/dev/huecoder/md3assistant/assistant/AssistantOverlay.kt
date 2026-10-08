package dev.huecoder.md3assistant.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Open_in_new
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.settings.InvokeInput
import dev.huecoder.md3assistant.feature.chat.ChatInput
import dev.huecoder.md3assistant.feature.chat.ChatViewModel
import dev.huecoder.md3assistant.feature.chat.EmptyConversation
import dev.huecoder.md3assistant.feature.chat.MessageList
import dev.huecoder.md3assistant.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3assistant.feature.chat.appendDictated
import dev.huecoder.md3assistant.feature.chat.rememberDictationControls
import dev.huecoder.md3assistant.ui.components.appViewModel
import dev.huecoder.md3assistant.ui.text.UiText
import dev.huecoder.md3assistant.ui.text.resolve
import dev.huecoder.md3assistant.voice.DictationViewModel
import kotlinx.coroutines.flow.first

@Composable
fun AssistantOverlay(activation: Int, onDismiss: () -> Unit, onOpenInApp: (chatId: String?) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val viewModel = appViewModel(key = "overlay-$activation") {
        ChatViewModel(null, it.chats, it.conversations, it.attachments, it.approvals, it.settings, it.applicationScope, it::newSpeechSynthesizer)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingId.collectAsStateWithLifecycle()
    var input by remember(activation) { mutableStateOf("") }
    val dictationModel = appViewModel(key = "overlay-dictation-$activation") { DictationViewModel(it.newVoiceRecorder(), it.transcriptions, it.settings) }
    val dictation = rememberDictationControls(
        model = dictationModel,
        onTranscript = {
            input = appendDictated(input, it)
            viewModel.noteDictation()
        },
        onPermissionMissing = { dictationModel.fail(UiText.Resource(R.string.overlay_microphone_denied)) },
    )
    val focusRequester = remember { FocusRequester() }
    val sheet = rememberAssistantSheetState(activation)

    LaunchedEffect(activation) {
        snapshotFlow { sheet.value }.first { it != SheetValue.Hidden }
        val wantsVoice = container.settings.current().voice.invokeInput == InvokeInput.VOICE
        val canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (wantsVoice && canRecord) dictation.onStart() else focusRequester.requestFocus()
    }
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    AssistantSheet(state = sheet, onDismissed = onDismiss) {
        OverlayHeader(
            agentName = state.agentName,
            canOpen = state.chatId != null,
            onOpenInApp = { onOpenInApp(state.chatId) },
        )
        Conversation {
            if (state.messages.isEmpty()) {
                EmptyConversation(
                    Modifier.align(Alignment.Center),
                    avatarSize = EMPTY_AVATAR_SIZE,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            } else {
                LightboxHost {
                    MessageList(
                        messages = state.messages,
                        showUsage = state.showTokenUsage,
                        speakingId = speakingId,
                        onRegenerate = viewModel::regenerate,
                        onToggleSpeech = viewModel::toggleSpeech,
                        onSend = viewModel::send,
                        onApproval = viewModel::respondToApproval,
                        bottomPadding = 8.dp,
                    )
                }
            }
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

@Composable
private fun ColumnScope.Conversation(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().weight(1f), content = content)
}

@Composable
private fun OverlayHeader(agentName: String, canOpen: Boolean, onOpenInApp: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
    ) {
        Text(agentName, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenInApp, enabled = canOpen, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = stringResource(R.string.overlay_open_in_app))
        }
    }
}

private val EMPTY_AVATAR_SIZE = 64.dp
