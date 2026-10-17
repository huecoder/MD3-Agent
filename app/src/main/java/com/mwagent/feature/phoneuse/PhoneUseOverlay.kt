package com.mwagent.feature.phoneuse

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.graphics.Point
import android.view.Gravity
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mwagent.AppContainer
import com.mwagent.MainActivity
import com.mwagent.data.settings.AppSettings
import com.mwagent.data.settings.PanelSpot
import com.mwagent.data.settings.PhonePanelState
import com.mwagent.data.settings.PhoneUseSettings
import com.mwagent.data.settings.TouchGuard
import com.mwagent.feature.chat.ChatItem
import com.mwagent.feature.chat.ChatUiState
import com.mwagent.feature.chat.ChatViewModel
import com.mwagent.feature.chat.ReplyPart
import com.mwagent.feature.chat.ToolCallUi
import com.mwagent.feature.chat.ToolStatus
import com.mwagent.system.control.CursorState
import com.mwagent.system.control.TouchArea
import com.mwagent.system.control.realDisplaySize
import com.mwagent.ui.avatar.AgentLook
import com.mwagent.ui.avatar.LocalAgentLook
import com.mwagent.ui.haptics.Haptics
import com.mwagent.ui.haptics.LocalHaptics
import com.mwagent.ui.theme.AssistantTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PhoneUseOverlay(private val service: AccessibilityService, private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val session = container.phoneControl.session
    private var shell: Shell? = null

    fun start() {
        scope.launch {
            combine(session.chatId, session.settings, container.visibility.isVisible) { chat, settings, appVisible ->
                chat?.takeIf { settings.overlay && !appVisible }
            }.distinctUntilChanged().collect { chat ->
                if (chat == null) returnToChat()
                unmount()
                chat?.let { mount(it, container.settings.phonePanel.first()) }
            }
        }
        scope.launch { session.settings.collect { settings -> shell?.apply(settings) } }
        scope.launch { session.gestures.collect { areas -> shell?.passTouches(areas) } }
        scope.launch { session.isOverlayHidden.collect { hidden -> shell?.hide(hidden) } }
        scope.launch {
            session.cursor.map { if (it.pressed) it.pressToken else 0 }.distinctUntilChanged().filter { it > 0 }.collect {
                val settings = session.settings.value
                container.phoneSounds.play(settings.tapSound, settings.soundVolume)
            }
        }
    }

    fun onDisplayChanged() {
        shell?.resize(windowManager.realDisplaySize())
    }

    fun stop() {
        unmount()
        scope.cancel()
    }

    private fun mount(chatId: String, panel: PhonePanelState) {
        shell = Shell(chatId, panel).also { it.show() }
        session.setOnScreen(true)
    }

    private fun returnToChat() {
        val finished = shell?.chatId?.takeIf { session.chatId.value == null } ?: return
        service.startActivity(MainActivity.chatIntent(service, finished))
    }

    private fun unmount() {
        session.setOnScreen(false)
        shell?.close()
        shell = null
    }

    private inner class Shell(val chatId: String, initialPanel: PhonePanelState) {
        private val shellScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val store = ViewModelStore()
        private val viewModel = ViewModelProvider(
            store,
            viewModelFactory {
                initializer {
                    ChatViewModel(
                        chatId, null, container.chats, container.conversations, container.attachments, container.approvals,
                        container.prompts, container.settings, container.modelCatalog, container.applicationScope, container::newSpeechSynthesizer,
                    )
                }
            },
        )[ChatViewModel::class.java]
        private val interruptAsked = MutableStateFlow(false)
        private val density = service.resources.displayMetrics.density
        private val bubblePx = (BUBBLE_SIZE.value * density).roundToInt()
        private val edgeMarginPx = (EDGE_MARGIN_DP * density).roundToInt()
        private val dipMarginPx = (DIP_MARGIN_DP * density).roundToInt()
        private val touchSlopPx = ViewConfiguration.get(service).scaledTouchSlop
        private var screen: Point = windowManager.realDisplaySize()
        private var settings: PhoneUseSettings = session.settings.value
        private val panelState: StateFlow<PhonePanelState> = container.settings.phonePanel
            .stateIn(shellScope, SharingStarted.Eagerly, initialPanel)
        private var frame by mutableStateOf(PanelFrame.of(screen.x, screen.y, settings))
        private var spot: PanelSpot = restingSpot()
        private var bubble: BubbleSpot = restingBubble()
        private var snapAnimator: ValueAnimator? = null

        private val appSettings: StateFlow<AppSettings?> = container.settings.settings.stateIn(shellScope, SharingStarted.Eagerly, null)
        private val agentLook: StateFlow<AgentLook> = combine(appSettings.filterNotNull(), viewModel.state.map { it.agentId }.distinctUntilChanged()) { all, agentId ->
            AgentLook.of(all.agent(agentId))
        }.distinctUntilChanged().stateIn(shellScope, SharingStarted.Eagerly, AgentLook())
        private val haptics: StateFlow<Haptics> = appSettings.filterNotNull().map { it.ui }.distinctUntilChanged()
            .map { ui -> if (ui.haptics) Haptics(container.hapticEngine, ui.hapticStrength) else Haptics.Silent }
            .stateIn(shellScope, SharingStarted.Eagerly, Haptics.Silent)
        private val modal: StateFlow<ModalRequest?> = combine(viewModel.pendingPrompt, viewModel.state, interruptAsked) { prompt, state, asked ->
            when {
                prompt != null -> ModalRequest.Prompt(prompt)
                state.awaitingApproval() != null -> ModalRequest.Approval(state.awaitingApproval()!!)
                asked -> ModalRequest.Interrupt
                else -> null
            }
        }.stateIn(shellScope, SharingStarted.Eagerly, null)

        private val acknowledgements = CursorAcknowledgements(session::arrived, session::pressSettled)
        private val panelActions = PanelActions(
            onStop = viewModel::stop,
            onSend = viewModel::send,
            onToggleCollapsed = { updatePanel { it.copy(collapsed = !it.collapsed) } },
            onMinimize = { updatePanel { it.copy(minimized = true) } },
            onDrag = ::movePanel,
            onDragEnd = ::savePanelPosition,
            onInputFocus = ::setPanelFocusable,
        )
        private val bubbleActions = BubbleActions(
            onOpen = { updatePanel { it.copy(minimized = false) } },
            onDrag = ::moveBubble,
            onDragEnd = ::dropBubble,
        )

        private val cursorWindow = OverlayWindow(service, windowManager, cursorParams()) { Themed { CursorContent() } }
        private val shieldWindow = OverlayWindow(service, windowManager, shieldParams()) { Themed { ShieldContent() } }
        private val panelWindow = OverlayWindow(service, windowManager, panelParams()) { Themed { PanelContent() } }
        private val modalWindow = OverlayWindow(service, windowManager, modalParams()) { Themed { ModalContent() } }

        private val dipped: StateFlow<Boolean> = session.cursor.map(::coversPanel).distinctUntilChanged()
            .stateIn(shellScope, SharingStarted.Eagerly, false)

        init {
            shellScope.launch {
                modal.collect { request ->
                    if (request != null) modalWindow.show() else modalWindow.hide()
                }
            }
            shellScope.launch {
                panelState.map { it.minimized }.distinctUntilChanged().collect { minimized ->
                    if (minimized) setPanelFocusable(false)
                    panelWindow.update { applyPanel(this) }
                }
            }
        }

        fun show() {
            cursorWindow.show()
            if (settings.touchGuard == TouchGuard.BLOCK) shieldWindow.show()
            panelWindow.show()
        }

        fun close() {
            snapAnimator?.cancel()
            shellScope.cancel()
            modalWindow.hide()
            panelWindow.hide()
            shieldWindow.hide()
            cursorWindow.hide()
            store.clear()
        }

        fun apply(next: PhoneUseSettings) {
            settings = next
            refreshFrame()
            cursorWindow.update { applyCursor(this) }
            panelWindow.update { applyPanel(this) }
            val blocking = next.touchGuard == TouchGuard.BLOCK
            if (blocking != shieldWindow.isShown) {
                panelWindow.hide()
                if (blocking) shieldWindow.show() else shieldWindow.hide()
                panelWindow.show()
            }
        }

        fun resize(size: Point) {
            screen = size
            refreshFrame()
            cursorWindow.update { width = size.x; height = size.y }
            shieldWindow.update { width = size.x; height = size.y }
            panelWindow.update { applyPanel(this) }
        }

        fun passTouches(areas: List<TouchArea>) {
            val passing = areas.isNotEmpty()
            shieldWindow.setTouchable(!passing)
            val bounds = panelWindow.bounds()
            val gestureOverPanel = passing && (bounds == null || areas.any { it.inflated(touchSlopPx).intersects(bounds) })
            panelWindow.setTouchable(!gestureOverPanel)
        }

        fun hide(hidden: Boolean) {
            listOf(cursorWindow, shieldWindow, panelWindow, modalWindow).forEach { it.setVisible(!hidden) }
        }

        private fun coversPanel(cursor: CursorState): Boolean {
            val x = cursor.x?.roundToInt() ?: return false
            val y = cursor.y?.roundToInt() ?: return false
            if (panelState.value.minimized) return false
            val bounds = panelWindow.bounds() ?: return false
            return TouchArea(x, y, x, y).inflated(dipMarginPx).intersects(bounds)
        }

        private fun cursorParams() = fullScreenParams(LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE).also(::applyCursor)

        private fun shieldParams() = fullScreenParams(LayoutParams.FLAG_NOT_FOCUSABLE)

        private fun fullScreenParams(flags: Int) = LayoutParams(
            screen.x, screen.y, LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags or LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        private fun applyCursor(params: LayoutParams) {
            params.flags = if (settings.keepAwake) params.flags or LayoutParams.FLAG_KEEP_SCREEN_ON else params.flags and LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            params.screenBrightness = if (settings.dimScreen) DIM_BRIGHTNESS else LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        private fun panelParams() = LayoutParams(
            0, LayoutParams.WRAP_CONTENT, LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            softInputMode = LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }.also(::applyPanel)

        private fun refreshFrame() {
            frame = PanelFrame.of(screen.x, screen.y, settings)
            spot = restingSpot()
            bubble = restingBubble()
        }

        private fun restingSpot(): PanelSpot = frame.restingSpot(screen.x, panelState.value.spot(frame.isLandscape))

        private fun restingBubble(): BubbleSpot {
            val panel = panelState.value
            return PanelBubble.spot(screen.x, screen.y, bubblePx, edgeMarginPx, panel.bubbleAtEnd, panel.bubbleHeightShare)
        }

        private fun applyPanel(params: LayoutParams) {
            if (panelState.value.minimized) {
                params.gravity = Gravity.TOP or Gravity.START
                params.width = bubblePx
                params.height = bubblePx
                params.x = bubble.x
                params.y = bubble.y
            } else {
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.width = frame.widthPx
                params.height = LayoutParams.WRAP_CONTENT
                params.x = spot.x
                params.y = spot.y + PANEL_BOTTOM_MARGIN_PX
            }
        }

        private fun modalParams() = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { softInputMode = LayoutParams.SOFT_INPUT_ADJUST_RESIZE or LayoutParams.SOFT_INPUT_STATE_UNCHANGED }

        @Composable
        private fun Themed(content: @Composable () -> Unit) {
            val look by agentLook.collectAsStateWithLifecycle()
            val feedback by haptics.collectAsStateWithLifecycle()
            AssistantTheme {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalAgentLook provides look,
                    LocalHaptics provides feedback,
                ) { content() }
            }
        }

        @Composable
        private fun CursorContent() {
            val cursor by session.cursor.collectAsStateWithLifecycle()
            val current by session.settings.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize()) {
                if (current.dimScreen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = DIM_SCRIM_ALPHA)))
                CursorLayer(cursor, current, acknowledgements)
            }
        }

        @Composable
        private fun ShieldContent() {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!session.touchesPassing) interruptAsked.value = true
                    }
                },
            )
        }

        @Composable
        private fun PanelContent() {
            val panel by panelState.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            if (panel.minimized) {
                PhoneBubble(isWorking = state.isStreaming, actions = bubbleActions)
            } else {
                val current by session.settings.collectAsStateWithLifecycle()
                val cursor by session.cursor.collectAsStateWithLifecycle()
                val isDipped by dipped.collectAsStateWithLifecycle()
                val maxHeight = with(LocalDensity.current) { frame.logMaxHeightPx.toDp() }
                PhonePanel(
                    state = state,
                    settings = current,
                    collapsed = panel.collapsed,
                    dipped = isDipped,
                    status = cursor.caption,
                    plateMaxHeight = maxHeight.coerceAtLeast(MIN_PLATE_HEIGHT),
                    actions = panelActions,
                )
            }
        }

        @Composable
        private fun ModalContent() {
            val request by modal.collectAsStateWithLifecycle()
            request?.let { shown ->
                PhoneModal(
                    shown,
                    ModalActions(
                        onAnswer = { prompt, answer -> viewModel.answerPrompt(prompt, answer) },
                        onApproval = viewModel::respondToApproval,
                        onStopAgent = {
                            interruptAsked.value = false
                            viewModel.stop()
                        },
                        onKeepWorking = { interruptAsked.value = false },
                    ),
                )
            }
        }

        private fun setPanelFocusable(focusable: Boolean) {
            panelWindow.update {
                flags = if (focusable) flags and LayoutParams.FLAG_NOT_FOCUSABLE.inv() else flags or LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }

        private fun movePanel(delta: Offset) {
            val reach = frame.horizontalReach(screen.x)
            spot = PanelSpot(
                x = (spot.x + delta.x).roundToInt().coerceIn(-reach, reach),
                y = (spot.y - delta.y).roundToInt().coerceIn(0, screen.y - PANEL_MIN_VISIBLE_PX),
            )
            panelWindow.update { applyPanel(this) }
        }

        private fun savePanelPosition() {
            val landscape = frame.isLandscape
            val resting = spot
            updatePanel { it.withSpot(landscape, resting) }
        }

        private fun moveBubble(delta: Offset) {
            snapAnimator?.cancel()
            bubble = PanelBubble.clamped(BubbleSpot(bubble.x + delta.x.roundToInt(), bubble.y + delta.y.roundToInt()), screen.x, screen.y, bubblePx)
            panelWindow.update { applyPanel(this) }
        }

        private fun dropBubble() {
            val atEnd = PanelBubble.staysAtEnd(bubble.x, bubblePx, screen.x)
            val rest = PanelBubble.spot(screen.x, screen.y, bubblePx, edgeMarginPx, atEnd, PanelBubble.heightShare(bubble.y, screen.y))
            val from = bubble
            snapAnimator?.cancel()
            snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SNAP_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val progress = it.animatedFraction
                    bubble = BubbleSpot(from.x + ((rest.x - from.x) * progress).roundToInt(), from.y + ((rest.y - from.y) * progress).roundToInt())
                    panelWindow.update { applyPanel(this) }
                }
                start()
            }
            updatePanel { it.copy(bubbleAtEnd = atEnd, bubbleHeightShare = PanelBubble.heightShare(rest.y, screen.y)) }
        }

        private fun updatePanel(change: (PhonePanelState) -> PhonePanelState) {
            container.applicationScope.launch { container.settings.updatePhonePanel(change) }
        }
    }

    private companion object {
        const val DIM_BRIGHTNESS = 0.01f
        const val DIM_SCRIM_ALPHA = 0.55f
        const val PANEL_BOTTOM_MARGIN_PX = 24
        const val PANEL_MIN_VISIBLE_PX = 160
        const val EDGE_MARGIN_DP = 12
        const val DIP_MARGIN_DP = 32
        const val SNAP_MS = 220L
        val MIN_PLATE_HEIGHT = 96.dp
    }
}

private fun ChatUiState.awaitingApproval(): ToolCallUi? =
    (messages.lastOrNull() as? ChatItem.Reply)?.parts
        ?.filterIsInstance<ReplyPart.Work>()?.flatMap { it.calls }
        ?.firstOrNull { it.status == ToolStatus.AWAITING_APPROVAL }
