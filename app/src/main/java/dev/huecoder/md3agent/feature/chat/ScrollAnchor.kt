package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel

internal class HeightWatch {
    private var id: String? = null
    private var height = 0

    fun change(id: String, height: Int): Int {
        val change = if (id == this.id) height - this.height else 0
        this.id = id
        this.height = height
        return change
    }
}

@Stable
class ScrollAnchor internal constructor(private val listState: LazyListState, private val bottomTolerance: Int) {
    private val newest = HeightWatch()
    private var pending = 0
    private var shift by mutableIntStateOf(0)
    private val grown = Channel<Unit>(Channel.CONFLATED)

    val isAtBottom: Boolean
        get() = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= bottomTolerance

    val shifted: Modifier = Modifier.offset { IntOffset(0, shift) }

    fun keepingPlace(id: String): Modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        holdPlace(newest.change(id, placeable.height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    suspend fun settle() {
        while (true) {
            grown.receive()
            val moved = pending
            pending = 0
            shift = 0
            listState.dispatchRawDelta(moved.toFloat())
        }
    }

    private fun holdPlace(change: Int) {
        if (change == 0 || Snapshot.withoutReadObservation { isAtBottom }) return
        pending += change
        shift = pending
        grown.trySend(Unit)
    }
}

@Composable
fun rememberScrollAnchor(listState: LazyListState): ScrollAnchor {
    val tolerance = with(LocalDensity.current) { BOTTOM_TOLERANCE.roundToPx() }
    val anchor = remember(listState, tolerance) { ScrollAnchor(listState, tolerance) }
    LaunchedEffect(anchor) { anchor.settle() }
    return anchor
}

private val BOTTOM_TOLERANCE = 8.dp
