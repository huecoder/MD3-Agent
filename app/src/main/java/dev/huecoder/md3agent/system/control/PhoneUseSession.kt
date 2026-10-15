package dev.huecoder.md3agent.system.control

import dev.huecoder.md3agent.data.settings.PhoneUseSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

data class CursorState(
    val x: Float? = null,
    val y: Float? = null,
    val caption: String? = null,
    val captionToken: Int = 0,
    val travelMs: Int? = null,
    val aimToken: Int = 0,
    val pressed: Boolean = false,
    val pressToken: Int = 0,
    val waiting: Boolean = false,
)

data class TouchArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun intersects(other: TouchArea): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

    fun inflated(by: Int): TouchArea = TouchArea(left - by, top - by, right + by, bottom + by)

    companion object {
        fun around(fromX: Int, fromY: Int, toX: Int, toY: Int) = TouchArea(min(fromX, toX), min(fromY, toY), max(fromX, toX), max(fromY, toY))
    }
}

private data class Acknowledged(val arrived: Int = 0, val pressed: Int = 0)

class PhoneUseSession {
    private val activeChat = MutableStateFlow<String?>(null)
    private val cursorState = MutableStateFlow(CursorState())
    private val acknowledged = MutableStateFlow(Acknowledged())
    private val gestureAreas = MutableStateFlow<List<TouchArea>>(emptyList())
    private val hiding = MutableStateFlow(0)
    private val onScreen = MutableStateFlow(false)

    val settings = MutableStateFlow(PhoneUseSettings())
    val chatId: StateFlow<String?> = activeChat.asStateFlow()
    val cursor: StateFlow<CursorState> = cursorState.asStateFlow()
    val gestures: StateFlow<List<TouchArea>> = gestureAreas.asStateFlow()
    val isPassingTouches = gestureAreas.map { it.isNotEmpty() }
    val touchesPassing: Boolean get() = gestureAreas.value.isNotEmpty()
    val isOverlayHidden = hiding.map { it > 0 }

    fun begin(chatId: String) {
        if (activeChat.value == chatId) return
        cursorState.update { CursorState(captionToken = it.captionToken, aimToken = it.aimToken, pressToken = it.pressToken) }
        activeChat.value = chatId
    }

    fun end() {
        activeChat.value = null
    }

    fun setOnScreen(shown: Boolean) {
        onScreen.value = shown
    }

    fun arrived(aimToken: Int) {
        acknowledged.update { it.copy(arrived = max(it.arrived, aimToken)) }
    }

    fun pressSettled(pressToken: Int) {
        acknowledged.update { it.copy(pressed = max(it.pressed, pressToken)) }
    }

    suspend fun aim(x: Int, y: Int, caption: String?) {
        val token = cursorState.updateAndGet {
            it.copy(x = x.toFloat(), y = y.toFloat(), travelMs = null, waiting = false, aimToken = it.aimToken + 1).withCaption(caption)
        }.aimToken
        if (onScreen.value) withTimeoutOrNull(ARRIVAL_TIMEOUT_MS) { acknowledged.first { it.arrived >= token } }
    }

    suspend fun <T> click(x: Int, y: Int, caption: String?, action: suspend () -> T): T {
        aim(x, y, caption)
        return pressing(TouchArea(x, y, x, y), action)
    }

    suspend fun <T> doubleClick(x: Int, y: Int, caption: String?, action: suspend () -> T): T {
        aim(x, y, caption)
        return pressing(TouchArea(x, y, x, y)) {
            coroutineScope {
                val second = launch {
                    delay(PhoneGestures.TAP_MS)
                    cursorState.update { it.copy(pressed = false) }
                    delay(PhoneGestures.DOUBLE_TAP_GAP_MS)
                    cursorState.update { it.copy(pressed = true, pressToken = it.pressToken + 1) }
                }
                try {
                    action()
                } finally {
                    second.cancel()
                }
            }
        }
    }

    suspend fun <T> dragging(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long, caption: String?, action: suspend () -> T): T {
        aim(fromX, fromY, caption)
        return pressing(TouchArea.around(fromX, fromY, toX, toY)) {
            cursorState.update { it.copy(x = toX.toFloat(), y = toY.toFloat(), travelMs = durationMs.toInt(), aimToken = it.aimToken + 1) }
            action()
        }
    }

    fun say(caption: String) {
        cursorState.update { it.withCaption(caption) }
    }

    fun waiting(isWaiting: Boolean) {
        cursorState.update { it.copy(waiting = isWaiting) }
    }

    suspend fun <T> passingTouches(area: TouchArea, block: suspend () -> T): T {
        gestureAreas.update { it + area }
        try {
            return block()
        } finally {
            gestureAreas.update { areas -> areas.toMutableList().also { it.remove(area) } }
        }
    }

    suspend fun <T> withOverlayHidden(block: suspend () -> T): T {
        if (!onScreen.value) return block()
        hiding.update { it + 1 }
        try {
            delay(HIDE_SETTLE_MS)
            return block()
        } finally {
            hiding.update { it - 1 }
        }
    }

    private suspend fun <T> pressing(area: TouchArea, action: suspend () -> T): T = passingTouches(area) {
        try {
            pressDown()
            action()
        } finally {
            cursorState.update { it.copy(pressed = false) }
        }
    }

    private suspend fun pressDown() {
        val token = cursorState.updateAndGet { it.copy(pressed = true, pressToken = it.pressToken + 1) }.pressToken
        if (onScreen.value) withTimeoutOrNull(PRESS_TIMEOUT_MS) { acknowledged.first { it.pressed >= token } }
    }

    private fun CursorState.withCaption(caption: String?) =
        if (caption == null) this else copy(caption = caption, captionToken = captionToken + 1)

    private companion object {
        const val ARRIVAL_TIMEOUT_MS = 900L
        const val PRESS_TIMEOUT_MS = 400L
        const val HIDE_SETTLE_MS = 140L
    }
}
