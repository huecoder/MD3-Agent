package dev.huecoder.md3agent.assistant

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Open_in_new
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.glow.EdgeGlow
import dev.huecoder.md3agent.data.settings.AssistantGlow
import dev.huecoder.md3agent.data.settings.AssistantLayout
import dev.huecoder.md3agent.data.settings.InvokeInput
import dev.huecoder.md3agent.feature.chat.ChatInput
import dev.huecoder.md3agent.feature.chat.ChatViewModel
import dev.huecoder.md3agent.feature.chat.MessageList
import dev.huecoder.md3agent.feature.chat.PromptPanel
import dev.huecoder.md3agent.feature.chat.SendEntrance
import dev.huecoder.md3agent.feature.chat.lightbox.InlineLightbox
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxController
import dev.huecoder.md3agent.feature.chat.lightbox.LocalLightbox
import dev.huecoder.md3agent.feature.chat.appendDictated
import dev.huecoder.md3agent.feature.chat.rememberDictationControls
import dev.huecoder.md3agent.system.control.InstalledApps
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
    val menu = remember(activation) { AttachMenuState() }
    val lightbox = remember(activation) { LightboxController() }
    var closing by remember(activation) { mutableStateOf(false) }
    var glowDone by remember(activation) { mutableStateOf(look.glow == AssistantGlow.OFF) }
    var composerHeight by remember { mutableIntStateOf(with(density) { DEFAULT_COMPOSER_HEIGHT.roundToPx() }) }

    val dismiss: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch { sheet.hide() }
    }
    val currentDismiss by rememberUpdatedState(dismiss)
    val openMenu = {
        keyboard?.hide()
        focusManager.clearFocus()
        menu.open()
    }

    DisposableEffect(menu, lightbox) {
        onBackHandler {
            val handled = if (lightbox.isOpen) {
                lightbox.dismiss()
                true
            } else {
                menu.back()
            }
            if (!handled) currentDismiss()
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
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.value }.first { it != SheetValue.Hidden }
        snapshotFlow { sheet.target }.first { it == SheetValue.Hidden }
        closing = true
    }
    LaunchedEffect(viewModel) {
        container.assistant.picked.collect { picked ->
            viewModel.attach(picked.uris).invokeOnCompletion { picked.markImported() }
        }
    }
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }
    val expandSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val expandFromCompact: () -> Unit = {
        if (compact && sheet.target == SheetValue.Compact) scope.launch { sheet.settleAt(SheetValue.Half, expandSpec) }
    }

    val composerDp = with(density) { composerHeight.toDp() }
    val navigationDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val compactHeight = if (compact) composerDp + navigationDp + if (look.showOrb) ORB_ZONE else BARE_ZONE else null
    CompositionLocalProvider(LocalLightbox provides lightbox) {
        Box(Modifier.fillMaxSize()) {
            AssistantSheet(
                state = sheet,
                compactContentHeight = compactHeight,
                onDismissRequest = dismiss,
                onDismissed = {
                    scope.launch {
                        snapshotFlow { glowDone }.first { it }
                        onDismiss()
                    }
                },
            ) { chrome ->
                OverlayHeader(
                    agentName = state.agentName,
                    canOpen = state.chatId != null,
                    onOpenInApp = { onOpenInApp(state.chatId) },
                    modifier = Modifier.collapsing(chrome),
                )
                Conversation {
                    if (state.messages.isEmpty()) {
                        AssistantIntro(chrome, restsAtBottom = compact && look.showOrb, modifier = Modifier.fillMaxSize())
                    } else {
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
                            modifier = if (compact) Modifier.fillMaxSize().graphicsLayer { alpha = chrome() } else Modifier.fillMaxSize(),
                            bottomPadding = 8.dp,
                            sendEntrance = sendEntrance,
                        )
                    }
                }
                val composerModifier = Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { composerHeight = it.height }
                    .composerMargins(chrome)
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
                            expandFromCompact()
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
                menu = menu,
                library = container.library,
                onSource = { source ->
                    menu.close()
                    onPickAttachment(source)
                },
                onPickFiles = { files ->
                    menu.close()
                    viewModel.attachFiles(files)
                },
            )
            InlineLightbox(lightbox, onLeaveApp = dismiss)
            EdgeGlow(
                glow = look.glow,
                grid = look.glowGrid,
                thickness = look.glowThickness,
                durationSeconds = look.glowSeconds,
                play = true,
                closing = closing,
                onFinished = { glowDone = true },
            )
        }
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

private fun Modifier.composerMargins(chrome: () -> Float): Modifier = layout { measurable, constraints ->
    val card = chrome()
    val side = lerp(COMPACT_SIDE_MARGIN, CARD_SIDE_MARGIN, card).roundToPx()
    val top = CARD_VERTICAL_MARGIN.roundToPx()
    val bottom = lerp(COMPACT_BOTTOM_MARGIN, CARD_VERTICAL_MARGIN, card).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = -2 * side, vertical = -(top + bottom)))
    layout(placeable.width + 2 * side, placeable.height + top + bottom) { placeable.place(side, top) }
}

private fun AppContainer.appLabel(packageName: String): String? =
    InstalledApps.list(appContext.packageManager).firstOrNull { it.packageName == packageName }?.label

private val DEFAULT_COMPOSER_HEIGHT = 64.dp
private val ORB_ZONE: Dp = 92.dp
private val COMPACT_SIDE_MARGIN = 20.dp
private val COMPACT_BOTTOM_MARGIN = 20.dp
private val CARD_SIDE_MARGIN = 8.dp
private val CARD_VERTICAL_MARGIN = 4.dp
private val BARE_ZONE: Dp = 8.dp
