package dev.huecoder.md3agent.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavBackStackEntryInfo
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHistory
import androidx.navigationevent.NavigationEventTransitionState
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal enum class BackRole { NONE, CLOSING, ENTERING, GONE }

private enum class BackPhase { IDLE, DRAGGING, COMMITTING, CANCELLING }

private class BackSignal(val gesture: NavigationEvent?, val handledByNavHost: Boolean, val stack: List<NavBackStackEntry>)

@Stable
internal class BackMotion(
    private val pxPerDp: () -> Float,
    private val displayCornerRadius: () -> Float,
    private val easings: BackEasings,
) {
    var size by mutableStateOf(IntSize.Zero)
    var frame by mutableStateOf(BackFrame.Rest)
        private set
    private var closingId by mutableStateOf<String?>(null)
    private var enteringId by mutableStateOf<String?>(null)
    private var retiredIds by mutableStateOf(emptyList<String>())
    private var phase = BackPhase.IDLE
    private var geometry: BackGeometry? = null
    private var animation: Job? = null
    private var entriesAtStart = 0
    private var progress = 0f
    private var touchY = 0f

    fun roleOf(id: String): BackRole = when {
        id == closingId -> BackRole.CLOSING
        id in retiredIds -> BackRole.GONE
        id == enteringId -> BackRole.ENTERING
        else -> BackRole.NONE
    }

    suspend fun track(dispatcher: NavigationEventDispatcher, backStack: StateFlow<List<NavBackStackEntry>>) = coroutineScope {
        val scope = this
        var known = backStack.value
        combine(dispatcher.transitionState, dispatcher.history, backStack) { transition, history, stack ->
            BackSignal(transition.backEvent(), history.isHandledByNavHost(), stack)
        }.collect { signal ->
            react(scope, signal, known)
            known = signal.stack
        }
    }

    private fun react(scope: CoroutineScope, signal: BackSignal, known: List<NavBackStackEntry>) {
        val stack = signal.stack
        val drag = signal.gesture?.takeIf { signal.handledByNavHost && stack.size >= MIN_ENTRIES }
        when {
            drag != null -> drag(drag, stack)
            phase == BackPhase.DRAGGING -> release(scope, stack)
            else -> followStack(scope, known, stack)
        }
    }

    private fun drag(event: NavigationEvent, stack: List<NavBackStackEntry>) {
        if (phase != BackPhase.DRAGGING && !begin(event, stack)) return
        progress = event.progress
        touchY = event.touchY
        frame = checkNotNull(geometry).gesture(progress, touchY)
    }

    private fun begin(event: NavigationEvent, stack: List<NavBackStackEntry>): Boolean {
        finishRunning()
        if (size == IntSize.Zero) return false
        geometry = open(stack.last().id, stack[stack.lastIndex - 1].id, event.touchY, event.startsOnLeft())
        entriesAtStart = stack.size
        phase = BackPhase.DRAGGING
        return true
    }

    private fun release(scope: CoroutineScope, stack: List<NavBackStackEntry>) {
        val current = geometry ?: return reset()
        if (stack.size < entriesAtStart) commit(scope, current, frame) else cancel(scope, current)
    }

    private fun followStack(scope: CoroutineScope, known: List<NavBackStackEntry>, stack: List<NavBackStackEntry>) {
        val leaving = known.lastOrNull() ?: return
        val revealed = stack.lastOrNull() ?: return
        when {
            leaving == revealed -> Unit
            revealed !in known -> finishRunning()
            leaving !in stack -> pop(scope, leaving, revealed)
        }
    }

    private fun pop(scope: CoroutineScope, closing: NavBackStackEntry, entering: NavBackStackEntry) {
        finishRunning()
        if (size == IntSize.Zero) return
        val centerY = size.height / 2f
        val opened = open(closing.id, entering.id, centerY, pressedToRight = true)
        commit(scope, opened, opened.gesture(0f, centerY))
    }

    private fun open(closing: String, entering: String, startTouchY: Float, pressedToRight: Boolean): BackGeometry {
        closingId = closing
        enteringId = entering
        return BackGeometry(size.width.toFloat(), size.height.toFloat(), startTouchY, pressedToRight, displayCornerRadius(), pxPerDp(), easings)
    }

    private fun commit(scope: CoroutineScope, geometry: BackGeometry, from: BackFrame) {
        phase = BackPhase.COMMITTING
        retire(closingId)
        animation = scope.launch {
            animate(0f, 1f, animationSpec = tween(BACK_COMMIT_MS, easing = LinearEasing)) { time, _ -> frame = geometry.commit(from, time) }
            finishCommit()
        }
    }

    private fun cancel(scope: CoroutineScope, geometry: BackGeometry) {
        val from = progress
        if (from <= 0f) return reset()
        phase = BackPhase.CANCELLING
        val duration = max(CANCEL_MIN_MS, (from * CANCEL_FULL_MS).roundToInt())
        animation = scope.launch {
            animate(from, 0f, animationSpec = tween(duration, easing = CancelEasing)) { value, _ ->
                frame = geometry.gesture(value, touchY).copy(scrim = value / from)
            }
            reset()
        }
    }

    private fun finishRunning() {
        animation?.cancel()
        animation = null
        when (phase) {
            BackPhase.COMMITTING -> finishCommit()
            BackPhase.CANCELLING -> reset()
            BackPhase.IDLE, BackPhase.DRAGGING -> Unit
        }
    }

    private fun finishCommit() {
        phase = BackPhase.IDLE
        enteringId = null
        geometry = null
        frame = frame.copy(scrim = 0f, closingAlpha = 0f)
    }

    private fun reset() {
        phase = BackPhase.IDLE
        closingId = null
        enteringId = null
        geometry = null
        frame = BackFrame.Rest
    }

    private fun retire(id: String?) {
        if (id != null) retiredIds = (retiredIds + id).takeLast(MAX_RETIRED)
    }

    private fun NavigationEvent.startsOnLeft() =
        swipeEdge == NavigationEvent.EDGE_LEFT || (swipeEdge == NavigationEvent.EDGE_NONE && touchX < size.width / 2f)
}

private fun NavigationEventTransitionState.backEvent(): NavigationEvent? =
    (this as? NavigationEventTransitionState.InProgress)
        ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
        ?.latestEvent

private fun NavigationEventHistory.isHandledByNavHost() = mergedHistory.getOrNull(currentIndex) is NavBackStackEntryInfo

internal const val BACK_COMMIT_MS = 375
private const val MIN_ENTRIES = 2
private const val MAX_RETIRED = 8
private const val CANCEL_MIN_MS = 120
private const val CANCEL_FULL_MS = 220
private val CancelEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
