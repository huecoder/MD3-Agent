package dev.huecoder.md3agent.feature.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_drop_down
import dev.huecoder.md3agent.feature.models.ModelPickerSheet
import dev.huecoder.md3agent.feature.models.label
import dev.huecoder.md3agent.ui.components.VendorGlyph
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.ui.components.AgentAvatar
import dev.huecoder.md3agent.ui.components.BackButton
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.text.resolve
import dev.huecoder.md3agent.voice.DictationViewModel

@Composable
fun ChatScreen(chatId: String?, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "chat-$chatId") { ChatViewModel(chatId, it.chats, it.conversations, it.attachments, it.approvals, it.prompts, it.settings, it.modelCatalog, it.applicationScope, it::newSpeechSynthesizer) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingId.collectAsStateWithLifecycle()
    val prompt by viewModel.pendingPrompt.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var pickingModel by rememberSaveable { mutableStateOf(false) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.attach(uris)
    }
    val dictationModel = appViewModel(key = "dictation-$chatId") { DictationViewModel(it.newVoiceRecorder(), it.transcriptions, it.settings) }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictationModel.start() else dictationModel.fail(UiText.Resource(R.string.dictation_error_microphone_denied))
    }
    val dictation = rememberDictationControls(
        model = dictationModel,
        onTranscript = {
            input = appendDictated(input, it)
            viewModel.noteDictation()
        },
        onPermissionMissing = { askMicrophone.launch(Manifest.permission.RECORD_AUDIO) },
    )

    LaunchedEffect(error) {
        error?.let {
            snackbarHost.showSnackbar(it.resolve(context))
            viewModel.consumeError()
        }
    }

    if (pickingModel) {
        ModelPickerSheet(current = state.selection, onSelect = viewModel::selectModel, onDismiss = { pickingModel = false })
    }

    LightboxHost {
        Scaffold(
            topBar = { ChatTopBar(title = state.title.asString(), state = state, onBack = onBack, onPickModel = { pickingModel = true }) },
            snackbarHost = { SnackbarHost(snackbarHost) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
                if (state.messages.isEmpty()) {
                    EmptyConversation(Modifier.align(Alignment.Center))
                } else {
                    MessageList(
                        messages = state.messages,
                        showUsage = state.showTokenUsage,
                        typing = state.typing,
                        speakingId = speakingId,
                        onRegenerate = viewModel::regenerate,
                        onToggleSpeech = viewModel::toggleSpeech,
                        onSend = viewModel::send,
                        onApproval = viewModel::respondToApproval,
                    )
                }
                val composerModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                val asked = prompt
                if (asked != null) {
                    PromptPanel(asked, onAnswer = { viewModel.answerPrompt(asked, it) }, modifier = composerModifier)
                } else {
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
                        attachments = state.pendingAttachments,
                        isImporting = state.isImporting,
                        onAttach = { pickFiles.launch(PICKABLE_MIME_TYPES) },
                        onRemoveAttachment = viewModel::removeAttachment,
                        dictation = dictation,
                        modifier = composerModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(title: String, state: ChatUiState, onBack: () -> Unit, onPickModel: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { ModelChip(state, onPickModel) },
        navigationIcon = { BackButton(onBack) },
    )
}

@Composable
private fun ModelChip(state: ChatUiState, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            VendorGlyph(state.modelFamily, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOfNotNull(
                    state.modelName ?: stringResource(R.string.chat_change_model),
                    state.selection?.reasoningEffort?.let { stringResource(it.label) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MODEL_CHIP_MAX_WIDTH),
            )
            Icon(
                MaterialSymbols.Rounded.Arrow_drop_down,
                contentDescription = stringResource(R.string.chat_change_model),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun MessageList(
    messages: List<ChatItem>,
    showUsage: Boolean,
    typing: TypingStyle,
    speakingId: String?,
    onRegenerate: (ChatItem.Reply) -> Unit,
    onToggleSpeech: (ChatItem.Reply) -> Unit,
    onSend: (String) -> Unit,
    onApproval: (ToolCallUi, ApprovalDecision) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    bottomPadding: Dp = ComposerClearance,
) {
    LazyColumn(
        reverseLayout = true,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
    ) {
        items(messages.asReversed(), key = { it.id }) { item ->
            when (item) {
                is ChatItem.User -> UserMessage(item, Modifier.animateItem(placementSpec = null))
                is ChatItem.Reply -> AssistantReply(
                    item,
                    showUsage = showUsage,
                    typing = typing,
                    isSpeaking = item.id == speakingId,
                    onRegenerate = { onRegenerate(item) },
                    onToggleSpeech = { onToggleSpeech(item) },
                    onSend = onSend,
                    onApproval = onApproval,
                    modifier = Modifier.animateItem(placementSpec = null),
                )
            }
        }
    }
}

@Composable
fun EmptyConversation(modifier: Modifier = Modifier, avatarSize: Dp = 96.dp, style: TextStyle = MaterialTheme.typography.displaySmallEmphasized) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AgentAvatar(isWorking = false, size = avatarSize)
        Text(stringResource(R.string.chat_empty_greeting), style = style)
    }
}

private val PICKABLE_MIME_TYPES = arrayOf("*/*")

private val ComposerClearance = 104.dp
private val MODEL_CHIP_MAX_WIDTH = 240.dp
