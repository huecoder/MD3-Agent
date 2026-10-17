package com.mwagent.feature.chat

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import com.mwagent.feature.models.ModelPickerSheet
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwagent.R
import com.mwagent.data.settings.TypingStyle
import com.mwagent.conversation.ApprovalDecision
import com.mwagent.feature.chat.lightbox.LightboxHost
import com.mwagent.ui.components.AgentAvatar
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.text.UiText
import com.mwagent.ui.text.asString
import com.mwagent.ui.text.resolve
import com.mwagent.voice.DictationState
import com.mwagent.voice.DictationViewModel
import com.mwagent.ui.avatar.AvatarHandoff
import com.mwagent.ui.avatar.KnockoutStage
import com.mwagent.ui.avatar.ProvideAgentLook
import com.mwagent.ui.avatar.TypingAttention
import com.mwagent.ui.avatar.knockoutArea
import com.mwagent.ui.avatar.knockoutObstacle
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(chatId: String?, onBack: () -> Unit, focusMessageId: String? = null) {
    val viewModel = appViewModel(key = "chat-$chatId") { ChatViewModel(chatId, focusMessageId, it.chats, it.conversations, it.attachments, it.approvals, it.prompts, it.settings, it.modelCatalog, it.applicationScope, it::newSpeechSynthesizer) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingId.collectAsStateWithLifecycle()
    val prompt by viewModel.pendingPrompt.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf(viewModel.draft()) }
    val updateInput = { text: String ->
        input = text
        viewModel.saveDraft(text)
    }
    var pickingModel by rememberSaveable { mutableStateOf(false) }
    val typingAttention = remember { TypingAttention() }
    val knockout = remember { KnockoutStage() }
    val handoff = remember { AvatarHandoff() }
    val sendEntrance = remember { SendEntrance() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { knockout.abort() }
    val hasMessages = state.messages.isNotEmpty()
    val density = LocalDensity.current
    var composerHeight by remember { mutableStateOf(ComposerClearance) }
    LaunchedEffect(hasMessages) {
        if (chatId == null && hasMessages && !knockout.isPlaying) handoff.play()
    }
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
            updateInput(appendDictated(input, it))
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
        ModelPickerSheet(
            model = state.selection,
            agentId = state.agentId,
            onSelectModel = viewModel::selectModel,
            onSelectAgent = viewModel::selectAgent,
            onDismiss = { pickingModel = false },
        )
    }

    ProvideAgentLook(state.agentId) {
        LightboxHost {
            Scaffold(
                topBar = {
                    ChatTopBar(
                        title = state.title.asString(),
                        state = state,
                        onBack = onBack,
                        onPickModel = { pickingModel = true },
                        onRename = viewModel::rename,
                        onDelete = { viewModel.delete(); onBack() },
                        onLookChange = viewModel::setLook,
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHost) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding().knockoutArea(knockout)) {
                    val isEmpty = state.messages.isEmpty()
                    if (!isEmpty) {
                        MessageList(
                            messages = state.messages,
                            showUsage = state.showTokenUsage,
                            typing = state.typing,
                            speakingId = speakingId,
                            onRegenerate = viewModel::regenerate,
                            onToggleSpeech = viewModel::toggleSpeech,
                            onSend = viewModel::send,
                            onApproval = viewModel::respondToApproval,
                            bottomPadding = composerHeight + COMPOSER_GAP,
                            scrollButtonPadding = composerHeight,
                            focusItemId = state.focusItemId,
                            onFocusShown = viewModel::focusShown,
                            avatarHandoff = handoff,
                            sendEntrance = sendEntrance,
                        )
                    }
                    if (isEmpty || handoff.isActive) {
                        EmptyConversation(
                            Modifier.align(Alignment.Center),
                            interactive = isEmpty,
                            typing = typingAttention,
                            listeningLevel = dictation.listeningLevel(),
                            knockout = knockout,
                            handoff = handoff,
                        )
                    }
                    val composerModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { composerHeight = with(density) { it.height.toDp() } }
                        .knockoutObstacle(knockout, COMPOSER_OBSTACLE)
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                    val asked = prompt
                    if (asked != null) {
                        PromptPanel(asked, onAnswer = { viewModel.answerPrompt(asked, it) }, modifier = composerModifier)
                    } else {
                        ChatInput(
                            text = input,
                            onTextChange = updateInput,
                            isStreaming = state.isStreaming,
                            canSend = state.canSend(input),
                            onSend = {
                                sendEntrance.arm()
                                viewModel.send(input)
                                updateInput("")
                            },
                            onStop = viewModel::stop,
                            attachments = state.pendingAttachments,
                            isImporting = state.isImporting,
                            onAttach = { pickFiles.launch(PICKABLE_MIME_TYPES) },
                            onRemoveAttachment = viewModel::removeAttachment,
                            onPasteImages = viewModel::attach,
                            dictation = dictation,
                            modifier = composerModifier,
                            onLetterTyped = typingAttention::onLetter,
                        )
                    }
                }
            }
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
    scrollButtonPadding: Dp = bottomPadding,
    focusItemId: String? = null,
    onFocusShown: () -> Unit = {},
    avatarHandoff: AvatarHandoff? = null,
    sendEntrance: SendEntrance? = null,
) {
    val handoffTargetId = if (avatarHandoff?.isActive == true) messages.firstOrNull { it is ChatItem.Reply }?.id else null
    val newestId = messages.lastOrNull()?.id
    val newestUserId = messages.lastOrNull { it is ChatItem.User }?.id
    val listState = rememberLazyListState()
    val anchor = rememberScrollAnchor(listState)
    val atBottom by remember(anchor) { derivedStateOf { anchor.isAtBottom } }
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    var flashing by remember { mutableStateOf<String?>(null) }
    val flashColor = MaterialTheme.colorScheme.primaryContainer
    LaunchedEffect(focusItemId) {
        val target = focusItemId ?: return@LaunchedEffect
        val index = messages.asReversed().indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            flashing = target
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FOCUS_FLASH_MS))
            flashing = null
        }
        onFocusShown()
    }
    Box(modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
        ) {
            items(messages.asReversed(), key = { it.id }) { item ->
                val keepingPlace = remember(anchor, item.id) { anchor.keepingPlace(item.id) }
                val placed = Modifier
                    .animateItem(placementSpec = null)
                    .then(anchor.shifted)
                    .then(if (item.id == newestId) keepingPlace else Modifier)
                    .then(
                        if (item.id == flashing) {
                            Modifier.drawBehind {
                                drawRoundRect(flashColor.copy(alpha = flash.value), cornerRadius = CornerRadius(FOCUS_CORNER.toPx()))
                            }
                        } else {
                            Modifier
                        },
                    )
                when (item) {
                    is ChatItem.User -> UserMessage(item, placed.sentEntrance(sendEntrance, item.id == newestUserId, item.id))
                    is ChatItem.Reply -> AssistantReply(
                        item,
                        showUsage = showUsage,
                        typing = typing,
                        isSpeaking = item.id == speakingId,
                        onRegenerate = { onRegenerate(item) },
                        onToggleSpeech = { onToggleSpeech(item) },
                        onSend = onSend,
                        onApproval = onApproval,
                        modifier = placed,
                        avatarHandoff = avatarHandoff.takeIf { item.id == handoffTargetId },
                    )
                }
            }
        }
        ScrollToBottomButton(
            visible = !atBottom,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = scrollButtonPadding + SCROLL_BUTTON_MARGIN),
        )
    }
}

@Composable
fun EmptyConversation(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 96.dp,
    style: TextStyle = MaterialTheme.typography.displaySmallEmphasized,
    interactive: Boolean = false,
    typing: TypingAttention? = null,
    listeningLevel: Float? = null,
    knockout: KnockoutStage? = null,
    handoff: AvatarHandoff? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AgentAvatar(
            isWorking = false,
            size = avatarSize,
            modifier = handoff?.avatarModifier() ?: Modifier,
            interactive = interactive,
            typing = typing,
            listeningLevel = listeningLevel,
            knockout = knockout,
        )
        Text(
            stringResource(R.string.chat_empty_greeting),
            style = style,
            modifier = Modifier
                .knockoutObstacle(knockout, GREETING_OBSTACLE)
                .graphicsLayer { alpha = handoff?.greetingAlpha ?: 1f },
        )
    }
}

private fun DictationControls.listeningLevel(): Float? =
    (state as? DictationState.Recording)?.let { it.levels.lastOrNull() ?: 0f }

private const val FOCUS_FLASH_MS = 1_600
private val FOCUS_CORNER = 20.dp
private val SCROLL_BUTTON_MARGIN = 12.dp
private val COMPOSER_GAP = 8.dp

private const val GREETING_OBSTACLE = "greeting"
private const val COMPOSER_OBSTACLE = "composer"

private val PICKABLE_MIME_TYPES = arrayOf("*/*")

private val ComposerClearance = 104.dp
