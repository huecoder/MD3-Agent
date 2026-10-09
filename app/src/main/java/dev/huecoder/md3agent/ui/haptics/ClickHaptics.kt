package dev.huecoder.md3agent.ui.haptics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf

fun Modifier.clickHaptics(): Modifier = this then ClickHapticsElement

private data object ClickHapticsElement : ModifierNodeElement<ClickHapticsNode>() {
    override fun create() = ClickHapticsNode()

    override fun update(node: ClickHapticsNode) = Unit
}

private class ClickHapticsNode : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(SuspendingPointerInputModifierNode { watchClicks() })
    }

    private suspend fun PointerInputScope.watchClicks() = awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        val release = awaitRelease(down) ?: return@awaitEachGesture
        val quick = release.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
        if (!quick || !release.isConsumed) return@awaitEachGesture
        val haptics = currentValueOf(LocalHaptics)
        if (!haptics.playedRecently()) haptics.tap()
    }

    private suspend fun AwaitPointerEventScope.awaitRelease(down: PointerInputChange): PointerInputChange? {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: return null
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
            if (!change.pressed) return change
        }
    }
}
