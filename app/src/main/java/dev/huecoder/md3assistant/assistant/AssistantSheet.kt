package dev.huecoder.md3assistant.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SheetValue { Hidden, Half, Full }

class AssistantSheetState internal constructor(internal val draggable: AnchoredDraggableState<SheetValue>) {
    val value: SheetValue get() = draggable.settledValue

    suspend fun expand() = draggable.animateTo(SheetValue.Full)

    suspend fun hide() = draggable.animateTo(SheetValue.Hidden)
}

@Composable
fun rememberAssistantSheetState(key: Any?): AssistantSheetState =
    remember(key) { AssistantSheetState(AnchoredDraggableState(SheetValue.Hidden)) }

@Composable
fun AssistantSheet(state: AssistantSheetState, onDismissed: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val draggable = state.draggable
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(draggable, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
    val nestedScroll = remember(draggable) { SheetNestedScroll(draggable) }
    var hasOpened by remember(draggable) { mutableStateOf(false) }

    LaunchedEffect(draggable) {
        snapshotFlow { draggable.settledValue }.first { it != SheetValue.Hidden }
        hasOpened = true
        snapshotFlow { draggable.settledValue }.first { it == SheetValue.Hidden }
        onDismissed()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowHeight = constraints.maxHeight.toFloat()
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val fullHeight = constraints.maxHeight.toFloat()
            val halfHeight = (windowHeight * HALF_HEIGHT_FRACTION).coerceAtMost(fullHeight)

            LaunchedEffect(fullHeight, halfHeight) {
                val anchors = DraggableAnchors {
                    SheetValue.Full at 0f
                    SheetValue.Half at fullHeight - halfHeight
                    SheetValue.Hidden at fullHeight
                }
                draggable.updateAnchors(anchors, if (hasOpened) draggable.targetValue else SheetValue.Hidden)
                if (!hasOpened) draggable.animateTo(SheetValue.Half)
            }

            fun offset() = draggable.offset.takeUnless { it.isNaN() } ?: fullHeight

            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black, alpha = SCRIM_ALPHA * (1f - offset() / fullHeight).coerceIn(0f, 1f)) }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { scope.launch { state.hide() } },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height = (fullHeight - offset()).roundToInt().coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        val halfOffset = fullHeight - halfHeight
                        val towardsFull = if (halfOffset <= 0f) 1f else (1f - offset() / halfOffset).coerceIn(0f, 1f)
                        shape = RoundedCornerShape(topStart = lerp(CORNER.toPx(), 0f, towardsFull), topEnd = lerp(CORNER.toPx(), 0f, towardsFull))
                        clip = true
                    }
                    .background(surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .nestedScroll(nestedScroll)
                    .anchoredDraggable(draggable, Orientation.Vertical, flingBehavior = flingBehavior)
                    .layout { measurable, constraints ->
                        val inset = (statusBarTop - offset()).roundToInt().coerceIn(0, statusBarTop)
                        val placeable = measurable.measure(constraints.copy(maxHeight = (constraints.maxHeight - inset).coerceAtLeast(0), minHeight = 0))
                        layout(placeable.width, constraints.maxHeight) { placeable.place(0, inset) }
                    }
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = HANDLE_PADDING)
                        .size(HANDLE_WIDTH, HANDLE_HEIGHT)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA)),
                )
                content()
            }
        }
    }
}

private class SheetNestedScroll(private val state: AnchoredDraggableState<SheetValue>) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        return Offset(0f, state.dispatchRawDelta(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        return Offset(0f, state.dispatchRawDelta(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val isExpanding = available.y < 0f && state.offset > state.anchors.minPosition()
        if (!isExpanding) return Velocity.Zero
        state.settle(available.y)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        state.settle(available.y)
        return available
    }
}

private const val HALF_HEIGHT_FRACTION = 0.6f
private const val SCRIM_ALPHA = 0.32f
private const val HANDLE_ALPHA = 0.4f
private val CORNER = 28.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val HANDLE_PADDING = 10.dp
