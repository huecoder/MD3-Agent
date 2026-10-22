package dev.huecoder.md3agent.feature.chat

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import dev.huecoder.md3agent.feature.models.ModelPickerSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.AttachMenuLayer
import dev.huecoder.md3agent.assistant.AttachMenuState
import dev.huecoder.md3agent.assistant.AttachPage
import dev.huecoder.md3agent.data.settings.AttachMenuStyle
import dev.huecoder.md3agent.data.settings.ChatHeaderStyle
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.ui.components.AgentAvatar
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.navigation.SlideBackHandler
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.text.resolve
import dev.huecoder.md3agent.voice.DictationState
import dev.huecoder.md3agent.voice.DictationViewModel
import dev.huecoder.md3agent.ui.avatar.AvatarHandoff
import dev.huecoder.md3agent.ui.avatar.KnockoutStage
import dev.huecoder.md3agent.ui.avatar.ProvideAgentLook
import dev.huecoder.md3agent.ui.avatar.TypingAttention
import dev.huecoder.md3agent.ui.avatar.knockoutArea
import dev.huecoder.md3agent.ui.avatar.knockoutObstacle
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(chatId: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit, focusMessageId: String? = null) {
    val viewModel = appViewModel(key = "chat-$chatId") { ChatViewModel(chatId, focusMessageId, it.chats, it.conversations, it.attachments, it.approvals, it.prompts, it.settings, it.modelCatalog, it.applicationScope, it::newSpeechSynthesizer) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val speechError by viewModel.speechError.collectAsStateWithLifecycle()
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
    var showingContext by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var stashedDraft by rememberSaveable { mutableStateOf("") }
    val attachMenu = remember { AttachMenuState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val library = (context.applicationContext as App).container.library
    val forkTitle = stringResource(R.string.chat_fork_title, state.title.asString())
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
    val pickAttachment = rememberAttachPicker(viewModel::attach)
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(editingId) {
        if (editingId != null) runCatching { composerFocus.requestFocus() }
    }
    val startEdit = { message: ChatItem.User ->
        if (editingId == null) stashedDraft = input
        editingId = message.id
        updateInput(message.content)
    }
    val finishEdit = {
        editingId = null
        updateInput(stashedDraft)
        stashedDraft = ""
    }
    val selectVersion = { messageId: String ->
        if (editingId != null) finishEdit()
        viewModel.selectVersion(messageId)
    }
    val openPanel = {
        keyboard?.hide()
        focusManager.clearFocus()
        attachMenu.open()
    }
    val compactAttachMenu: @Composable ColumnScope.(() -> Unit) -> Unit = { close ->
        CompactAttachItems(
            onSource = { source ->
                close()
                pickAttachment(source)
            },
            onAppFiles = {
                close()
                attachMenu.open(AttachPage.APP_FILES)
            },
        )
    }
    SlideBackHandler(enabled = attachMenu.isOpen, back = attachMenu.slide) {
        attachMenu.back()
        if (attachMenu.isOpen) attachMenu.slide.release()
    }
    BackHandler(enabled = editingId != null && !attachMenu.isOpen) { finishEdit() }
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

    speechError?.let { SpeechErrorDialog(it, onDismiss = viewModel::consumeSpeechError) }

    if (pickingModel) {
        ModelPickerSheet(
            model = state.selection,
            agentId = state.agentId,
            onSelectModel = viewModel::selectModel,
            onSelectAgent = viewModel::selectAgent,
            onDismiss = { pickingModel = false },
        )
    }

    val contextMeter = state.context
    if (showingContext && contextMeter != null) {
        ContextSheet(contextMeter, load = viewModel::contextBreakdown, onDismiss = { showingContext = false })
    }

    ProvideAgentLook(state.agentId) {
        LightboxHost {
            Box(Modifier.fillMaxSize()) {
                val compactHeader = state.header == ChatHeaderStyle.COMPACT
                val headerClearance = if (compactHeader) compactHeaderHeight() else 0.dp
                val topBar: @Composable (Modifier) -> Unit = { barModifier ->
                    ChatTopBar(
                        title = state.title.asString(),
                        state = state,
                        onBack = onBack,
                        onPickModel = { pickingModel = true },
                        onOpenContext = { showingContext = true },
                        onRename = viewModel::rename,
                        onDelete = { viewModel.delete(); onBack() },
                        onLookChange = viewModel::setLook,
                        modifier = barModifier,
                    )
                }
                Scaffold(
                    topBar = { if (!compactHeader) topBar(Modifier) },
                    snackbarHost = { SnackbarHost(snackbarHost) },
                    contentWindowInsets = if (compactHeader) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
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
                                onContinue = viewModel::continueReply,
                                onToggleSpeech = viewModel::toggleSpeech,
                                onSend = viewModel::send,
                                onApproval = viewModel::respondToApproval,
                                bottomPadding = composerHeight + COMPOSER_GAP,
                                topPadding = headerClearance,
                                scrollButtonPadding = composerHeight,
                                focusItemId = state.focusItemId,
                                onFocusShown = viewModel::focusShown,
                                avatarHandoff = handoff,
                                sendEntrance = sendEntrance,
                                onEdit = startEdit.takeUnless { state.isStreaming },
                                onSelectVersion = selectVersion.takeUnless { state.isStreaming },
                                onFork = { reply -> viewModel.fork(reply, forkTitle, onOpenChat) },
                            )
                        }
                        if (isEmpty || handoff.isActive) {
                            EmptyConversation(
                                Modifier.align(Alignment.Center).padding(top = headerClearance),
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
                            Column(composerModifier) {
                                if (editingId != null) EditBanner(onCancel = finishEdit)
                                ChatInput(
                                    text = input,
                                    onTextChange = updateInput,
                                    isStreaming = state.isStreaming,
                                    canSend = state.canSend(input),
                                    onSend = {
                                        val editing = editingId
                                        if (editing != null) {
                                            viewModel.edit(editing, input)
                                            finishEdit()
                                        } else {
                                            sendEntrance.arm()
                                            viewModel.send(input)
                                            updateInput("")
                                        }
                                    },
                                    onStop = viewModel::stop,
                                    attachments = state.pendingAttachments,
                                    isImporting = state.isImporting,
                                    onAttach = openPanel.takeIf { state.attachMenu == AttachMenuStyle.PANEL },
                                    attachIcon = MaterialSymbols.Rounded.Add,
                                    attachMenu = compactAttachMenu.takeIf { state.attachMenu == AttachMenuStyle.COMPACT },
                                    onRemoveAttachment = viewModel::removeAttachment,
                                    onPasteImages = viewModel::attach,
                                    dictation = dictation,
                                    focusRequester = composerFocus,
                                    onLetterTyped = typingAttention::onLetter,
                                )
                            }
                        }
                        if (compactHeader) topBar(Modifier.align(Alignment.TopCenter))
                    }
                }
                AttachMenuLayer(
                    menu = attachMenu,
                    library = library,
                    onSource = { source ->
                        attachMenu.close()
                        pickAttachment(source)
                    },
                    onPickFiles = { files ->
                        attachMenu.close()
                        viewModel.attachFiles(files)
                    },
                )
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
    onContinue: () -> Unit,
    onToggleSpeech: (ChatItem.Reply) -> Unit,
    onSend: (String) -> Unit,
    onApproval: (ToolCallUi, ApprovalDecision) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    bottomPadding: Dp = ComposerClearance,
    topPadding: Dp = 0.dp,
    scrollButtonPadding: Dp = bottomPadding,
    focusItemId: String? = null,
    onFocusShown: () -> Unit = {},
    avatarHandoff: AvatarHandoff? = null,
    sendEntrance: SendEntrance? = null,
    onEdit: ((ChatItem.User) -> Unit)? = null,
    onSelectVersion: ((String) -> Unit)? = null,
    onFork: ((ChatItem.Reply) -> Unit)? = null,
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
    var versionJump by remember { mutableStateOf<VersionJump?>(null) }
    val flashColor = MaterialTheme.colorScheme.primaryContainer
    LaunchedEffect(messages, versionJump) {
        val jump = versionJump ?: return@LaunchedEffect
        val index = messages.asReversed().indexOfFirst { it.id == jump.targetId }
        if (index >= 0) {
            listState.scrollToItem(index, -jump.offset)
            versionJump = null
        }
    }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp + topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
        ) {
            items(messages.asReversed(), key = { it.id }) { item ->
                val keepingPlace = remember(anchor, item.id) { anchor.keepingPlace(item.id) }
                val placed = Modifier
                    .animateItem(fadeOutSpec = null, placementSpec = null)
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
                    is ChatItem.User -> UserMessage(
                        item,
                        placed.sentEntrance(sendEntrance, item.id == newestUserId, item.id),
                        onEdit = onEdit,
                        onSelectVersion = onSelectVersion?.let { select ->
                            { target ->
                                versionJump = listState.versionJump(from = item.id, target = target)
                                select(target)
                            }
                        },
                    )
                    is ChatItem.Reply -> AssistantReply(
                        item,
                        showUsage = showUsage,
                        typing = typing,
                        isSpeaking = item.id == speakingId,
                        onRegenerate = { onRegenerate(item) },
                        onContinue = onContinue,
                        onToggleSpeech = { onToggleSpeech(item) },
                        onSend = onSend,
                        onApproval = onApproval,
                        modifier = placed,
                        avatarHandoff = avatarHandoff.takeIf { item.id == handoffTargetId },
                        onFork = onFork?.let { fork -> { fork(item) } },
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
            rememberGreeting(),
            style = style,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = GREETING_MARGIN)
                .knockoutObstacle(knockout, GREETING_OBSTACLE)
                .graphicsLayer { alpha = handoff?.greetingAlpha ?: 1f },
        )
    }
}

private class VersionJump(val targetId: String, val offset: Int)

private fun LazyListState.versionJump(from: String, target: String): VersionJump? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == from }?.let { VersionJump(target, it.offset) }

private fun DictationControls.listeningLevel(): Float? =
    (state as? DictationState.Recording)?.let { it.levels.lastOrNull() ?: 0f }

private const val FOCUS_FLASH_MS = 1_600
private val FOCUS_CORNER = 20.dp
private val SCROLL_BUTTON_MARGIN = 12.dp
private val COMPOSER_GAP = 8.dp

private const val GREETING_OBSTACLE = "greeting"
private val GREETING_MARGIN = 32.dp
private const val COMPOSER_OBSTACLE = "composer"

private val ComposerClearance = 104.dp
