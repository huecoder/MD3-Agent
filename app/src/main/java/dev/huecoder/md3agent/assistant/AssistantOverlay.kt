package dev.huecoder.md3agent.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Open_in_new
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.glow.EdgeGlow
import dev.huecoder.md3agent.data.settings.AssistantLayout
import dev.huecoder.md3agent.data.settings.InvokeInput
import dev.huecoder.md3agent.feature.chat.ChatInput
import dev.huecoder.md3agent.feature.chat.ChatViewModel
import dev.huecoder.md3agent.feature.chat.EmptyConversation
import dev.huecoder.md3agent.feature.chat.MessageList
import dev.huecoder.md3agent.feature.chat.PromptPanel
import dev.huecoder.md3agent.feature.chat.SendEntrance
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.feature.chat.appendDictated
import dev.huecoder.md3agent.feature.chat.rememberDictationControls
import dev.huecoder.md3agent.system.control.InstalledApps
import dev.huecoder.md3agent.ui.components.AgentAvatar
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.resolve
import dev.huecoder.md3agent.voice.DictationViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AssistantOverlay(
    activation: Int,
    onDismiss: () -> Unit,
    onOpenInApp: (chatId: String?) -> Unit,
    onPickAttachment: (AttachSource) -> Unit,
    onBackHandler: ((() -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val configured by container.assistantSettings.collectAsStateWithLifecycle()
    val look = configured ?: return
    val compact = look.layout == AssistantLayout.COMPACT
    val viewModel = appViewModel(key = "overlay-$activation") { app ->
        ChatViewModel(
            null, null, app.chats, app.conversations, app.attachments, app.approvals, app.prompts, app.settings, app.modelCatalog,
            app.applicationScope, app::newSpeechSynthesizer, screenNote = { app.screenContext.openedOverNote(app::appLabel) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingId.collectAsStateWithLifecycle()
    val prompt by viewModel.pendingPrompt.collectAsStateWithLifecycle()
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
    val sendEntrance = remember(activation) { SendEntrance() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var attachMenu by remember { mutableStateOf(AttachMenu.CLOSED) }
    var composerHeight by remember { mutableIntStateOf(with(density) { DEFAULT_COMPOSER_HEIGHT.roundToPx() }) }

    val dismiss: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch { sheet.hide() }
    }
    val closeMenu = { attachMenu = AttachMenu.CLOSED }
    val openMenu = {
        keyboard?.hide()
        focusManager.clearFocus()
        attachMenu = AttachMenu.SOURCES
    }

    DisposableEffect(attachMenu) {
        onBackHandler {
            if (attachMenu != AttachMenu.CLOSED) closeMenu() else dismiss()
            true
        }
        onDispose { onBackHandler(null) }
    }
    LaunchedEffect(activation) {
        snapshotFlow { windowInfo.isWindowFocused && sheet.isPlaced }.first { it }
        val wantsVoice = container.settings.current().voice.invokeInput == InvokeInput.VOICE
        val canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (wantsVoice && canRecord) dictation.onStart() else runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(Unit) {
        container.assistant.attachments.collect(viewModel::attach)
    }
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }
    val hasMessages = state.messages.isNotEmpty()
    LaunchedEffect(compact, hasMessages) {
        if (compact && hasMessages && sheet.target == SheetValue.Compact) sheet.settleAt(SheetValue.Half)
    }

    val composerDp = with(density) { composerHeight.toDp() }
    val navigationDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val compactHeight = if (compact) composerDp + navigationDp + if (look.showOrb) ORB_ZONE else BARE_ZONE else null
    val composerModifier = Modifier.onSizeChanged { composerHeight = it.height }.padding(horizontal = 8.dp, vertical = 4.dp)

    Box(Modifier.fillMaxSize()) {
        AssistantSheet(
            state = sheet,
            compactContentHeight = compactHeight,
            onDismissRequest = dismiss,
            onDismissed = onDismiss,
        ) { chrome ->
            OverlayHeader(
                agentName = state.agentName,
                canOpen = state.chatId != null,
                onOpenInApp = { onOpenInApp(state.chatId) },
                modifier = Modifier.collapsing(chrome),
            )
            Conversation {
                if (state.messages.isEmpty()) {
                    when {
                        !compact -> EmptyConversation(
                            Modifier.align(Alignment.Center),
                            avatarSize = EMPTY_AVATAR_SIZE,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                        )
                        look.showOrb -> AgentAvatar(
                            isWorking = false,
                            size = ORB_SIZE,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = ORB_GAP).graphicsLayer { alpha = 1f - chrome() },
                        )
                    }
                } else {
                    LightboxHost {
                        MessageList(
                            messages = state.messages,
                            showUsage = state.showTokenUsage,
                            typing = state.typing,
                            speakingId = speakingId,
                            onRegenerate = viewModel::regenerate,
                            onToggleSpeech = viewModel::toggleSpeech,
                            onSend = viewModel::send,
                            onApproval = viewModel::respondToApproval,
                            modifier = if (compact) Modifier.fillMaxSize().graphicsLayer { alpha = chrome() } else Modifier.fillMaxSize(),
                            bottomPadding = 8.dp,
                            sendEntrance = sendEntrance,
                        )
                    }
                }
            }
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
                        sendEntrance.arm()
                        viewModel.send(input)
                        input = ""
                    },
                    onStop = viewModel::stop,
                    attachments = state.pendingAttachments,
                    isImporting = state.isImporting,
                    onAttach = openMenu,
                    attachIcon = MaterialSymbols.Rounded.Add,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onPasteImages = viewModel::attach,
                    dictation = dictation,
                    focusRequester = focusRequester,
                    modifier = composerModifier,
                )
            }
        }
        AttachMenuLayer(
            menu = attachMenu,
            onDismiss = closeMenu,
            onSource = { source ->
                closeMenu()
                onPickAttachment(source)
            },
            onAppFiles = { attachMenu = AttachMenu.APP_FILES },
            onPickedFromApp = { uris: List<Uri> ->
                closeMenu()
                viewModel.attach(uris)
            },
        )
        EdgeGlow(
            glow = look.glow,
            grid = look.glowGrid,
            thickness = look.glowThickness,
            durationSeconds = look.glowSeconds,
            play = true,
        )
    }
}

@Composable
private fun ColumnScope.Conversation(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().weight(1f), content = content)
}

@Composable
private fun OverlayHeader(agentName: String, canOpen: Boolean, onOpenInApp: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
    ) {
        Text(agentName, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenInApp, enabled = canOpen, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = stringResource(R.string.overlay_open_in_app))
        }
    }
}

private fun AppContainer.appLabel(packageName: String): String? =
    InstalledApps.list(appContext.packageManager).firstOrNull { it.packageName == packageName }?.label

private val EMPTY_AVATAR_SIZE = 64.dp
private val DEFAULT_COMPOSER_HEIGHT = 64.dp
private val ORB_SIZE = 56.dp
private val ORB_GAP = 12.dp
private val ORB_ZONE: Dp = 92.dp
private val BARE_ZONE: Dp = 8.dp
