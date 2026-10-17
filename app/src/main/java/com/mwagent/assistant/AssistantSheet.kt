package com.mwagent.assistant

import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalView
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
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val flingThreshold = with(density) { FLING_THRESHOLD.toPx() }
    val nestedScroll = remember(draggable, settleSpec, flingThreshold) { SheetNestedScroll(draggable, settleSpec, flingThreshold) }
    var hasOpened by remember(draggable) { mutableStateOf(false) }

    LaunchedEffect(draggable) {
        snapshotFlow { draggable.settledValue }.first { it != SheetValue.Hidden }
        hasOpened = true
        snapshotFlow { draggable.settledValue }.first { it == SheetValue.Hidden }
        onDismissed()
    }

    val view = LocalView.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var frameTick by remember { mutableLongStateOf(0L) }
    val windowLocation = remember { IntArray(2) }
    LaunchedEffect(view, imeVisible) {
        do {
            withFrameNanos { frameTick = it }
        } while (imeVisible || windowLocation[1] != 0)
    }
    val systemPan = {
        frameTick
        view.getLocationInWindow(windowLocation)
        -windowLocation[1]
    }

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { translationY = systemPan().toFloat() }) {
        val windowHeight = constraints.maxHeight.toFloat()
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val fullHeight = constraints.maxHeight.toFloat()
            val halfHeight = minOf(windowHeight * HALF_HEIGHT_FRACTION, fullHeight * MAX_HALF_OF_AVAILABLE)

            LaunchedEffect(draggable, fullHeight, halfHeight) {
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
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    content()
                }
            }
        }
    }
}

private class SheetNestedScroll(
    private val state: AnchoredDraggableState<SheetValue>,
    private val settleSpec: AnimationSpec<Float>,
    private val flingThreshold: Float,
) : NestedScrollConnection {
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
        settle(available.y)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle(available.y)
        return available
    }

    private suspend fun settle(velocity: Float) {
        val offset = state.offset.takeUnless { it.isNaN() } ?: return
        val target = when {
            velocity <= -flingThreshold -> state.anchors.closestAnchor(offset, searchUpwards = false)
            velocity >= flingThreshold -> state.anchors.closestAnchor(offset, searchUpwards = true)
            else -> state.anchors.closestAnchor(offset)
        } ?: return
        state.animateTo(target, settleSpec)
    }
}

private const val HALF_HEIGHT_FRACTION = 0.45f
private const val MAX_HALF_OF_AVAILABLE = 0.7f
private const val SCRIM_ALPHA = 0.32f
private const val HANDLE_ALPHA = 0.4f
private val CORNER = 28.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val HANDLE_PADDING = 10.dp
private val FLING_THRESHOLD = 125.dp
