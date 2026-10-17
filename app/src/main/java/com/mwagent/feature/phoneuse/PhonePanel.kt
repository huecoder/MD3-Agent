package com.mwagent.feature.phoneuse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.graphicsLayer
import com.composables.icons.materialsymbols.rounded.Close_fullscreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.mwagent.R
import com.mwagent.data.settings.PhoneUseSettings
import com.mwagent.feature.chat.ChatInput
import com.mwagent.feature.chat.ChatItem
import com.mwagent.feature.chat.ChatUiState
import com.mwagent.feature.chat.ReplyPart
import com.mwagent.feature.chat.ReplyText
import com.mwagent.feature.chat.UserMessage
import com.mwagent.feature.chat.WorkBlock
import com.mwagent.ui.components.AgentAvatar
import com.mwagent.ui.text.asString
import kotlinx.coroutines.delay

class PanelActions(
    val onStop: () -> Unit,
    val onSend: (String) -> Unit,
    val onToggleCollapsed: () -> Unit,
    val onMinimize: () -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onInputFocus: (Boolean) -> Unit,
)

class BubbleActions(
    val onOpen: () -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
)

@Composable
fun PhonePanel(
    state: ChatUiState,
    settings: PhoneUseSettings,
    collapsed: Boolean,
    dipped: Boolean,
    status: String?,
    plateMaxHeight: Dp,
    actions: PanelActions,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val opacity = settings.panelOpacity
    val dip by animateFloatAsState(if (dipped) DIPPED_ALPHA else 1f, label = "panel-dip")

    LaunchedEffect(inputFocused) {
        actions.onInputFocus(inputFocused)
        if (inputFocused) {
            delay(KEYBOARD_DELAY_MS)
            keyboard?.show()
        }
    }

    Column(modifier.graphicsLayer { alpha = dip }, verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = opacity),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                PanelHeader(state, collapsed, status, actions)
                AnimatedVisibility(
                    visible = !collapsed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    PlateLog(state, Modifier.heightIn(max = plateMaxHeight))
                }
            }
        }
        ChatInput(
            text = input,
            onTextChange = { input = it },
            isStreaming = state.isStreaming,
            steerable = true,
            canSend = state.canSend(input),
            onSend = {
                actions.onSend(input)
                input = ""
                focusManager.clearFocus()
            },
            onStop = actions.onStop,
            modifier = Modifier.alpha(opacity.coerceAtLeast(INPUT_MIN_OPACITY)).onFocusChanged { inputFocused = it.hasFocus },
        )
    }
}

@Composable
private fun PanelHeader(state: ChatUiState, collapsed: Boolean, status: String?, actions: PanelActions) {
    val chevron by animateFloatAsState(if (collapsed) 0f else HALF_TURN, label = "panel-chevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .windowDrag(actions.onDrag, actions.onDragEnd),
        ) {
            AgentAvatar(isWorking = state.isStreaming, size = HEADER_AVATAR)
            Text(
                status ?: stringResource(R.string.phone_use_working),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = actions.onMinimize, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.Close_fullscreen, contentDescription = stringResource(R.string.phone_use_minimize))
        }
        IconButton(onClick = actions.onToggleCollapsed, shapes = IconButtonDefaults.shapes()) {
            Icon(
                MaterialSymbols.Rounded.Expand_more,
                contentDescription = stringResource(if (collapsed) R.string.phone_use_expand else R.string.phone_use_collapse),
                modifier = Modifier.rotate(chevron),
            )
        }
    }
}

@Composable
private fun PlateLog(state: ChatUiState, modifier: Modifier) {
    LazyColumn(
        reverseLayout = true,
        modifier = modifier,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        items(state.messages.asReversed(), key = { it.id }) { item ->
            when (item) {
                is ChatItem.User -> UserMessage(item)
                is ChatItem.Reply -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.parts.forEach { part ->
                        when (part) {
                            is ReplyPart.Text -> ReplyText(part.content, part.isStreaming, state.typing, isStopped = part.isStopped)
                            is ReplyPart.Work -> WorkBlock(part)
                            is ReplyPart.Error -> Text(part.text.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            is ReplyPart.Media, is ReplyPart.Chart, is ReplyPart.Diagram -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneBubble(isWorking: Boolean, actions: BubbleActions, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = BUBBLE_ELEVATION,
        modifier = modifier
            .size(BUBBLE_SIZE)
            .windowDrag(actions.onDrag, actions.onDragEnd, onTap = actions.onOpen),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isWorking) {
                CircularProgressIndicator(
                    strokeWidth = BUBBLE_RING_STROKE,
                    modifier = Modifier.fillMaxSize().padding(BUBBLE_RING_INSET),
                )
            }
            AgentAvatar(isWorking = isWorking, size = BUBBLE_AVATAR)
        }
    }
}

val BUBBLE_SIZE = 56.dp
private val BUBBLE_AVATAR = 34.dp
private val BUBBLE_ELEVATION = 6.dp
private val BUBBLE_RING_STROKE = 2.5.dp
private val BUBBLE_RING_INSET = 3.dp
private const val DIPPED_ALPHA = 0.25f
private val PANEL_GAP = 8.dp
private val HEADER_AVATAR = 26.dp
private const val HALF_TURN = 180f
private const val INPUT_MIN_OPACITY = 0.6f
private const val KEYBOARD_DELAY_MS = 90L
