package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Arrow_drop_down
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.settings.ContextDisplay
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.ui.components.VendorGlyph
import dev.huecoder.md3agent.ui.haptics.clickHaptics

@Composable
internal fun compactHeaderHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + COMPACT_HEADER_HEIGHT

@Composable
internal fun CompactChatHeader(
    title: String,
    state: ChatUiState,
    topInset: Dp,
    onBack: () -> Unit,
    actions: ChatMenuActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .headerFade(MaterialTheme.colorScheme.background)
            .padding(top = topInset)
            .height(COMPACT_HEADER_HEIGHT)
            .padding(horizontal = SIDE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SIDE_GAP),
    ) {
        HeaderCircle(onClick = onBack) {
            Icon(MaterialSymbols.Rounded.Arrow_back, contentDescription = stringResource(R.string.common_back))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (state.isStarted) Modifier.renameOnLongPress(actions.rename) else Modifier,
            )
            HeaderSubtitle(state, onPickModel = actions.pickModel, onOpenContext = actions.openContext)
        }
        if (state.isStarted) HeaderAvatar(state, actions) else Spacer(Modifier.size(HEADER_BUTTON))
    }
}

@Composable
private fun HeaderSubtitle(state: ChatUiState, onPickModel: () -> Unit, onOpenContext: () -> Unit) {
    val meter = state.context.takeIf { state.isStarted && state.contextDisplay != ContextDisplay.MENU }
    val colors = MaterialTheme.colorScheme
    val model = state.modelName?.let(ModelDisplayName::short) ?: stringResource(R.string.chat_change_model)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.isStarted) {
            Text(
                model,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(CircleShape)
                    .clickable(onClick = onPickModel)
                    .padding(horizontal = TAP_PADDING_H, vertical = TAP_PADDING_V),
            )
        } else {
            Surface(
                onClick = onPickModel,
                shape = CircleShape,
                color = colors.surfaceContainerHigh.copy(alpha = CIRCLE_ALPHA),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Row(
                    Modifier.padding(start = MODEL_START_PADDING, end = MODEL_END_PADDING, top = TAP_PADDING_V, bottom = TAP_PADDING_V),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RING_GAP),
                ) {
                    VendorGlyph(state.modelFamily, size = MODEL_GLYPH, tint = colors.onSurfaceVariant)
                    Text(
                        model,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        MaterialSymbols.Rounded.Arrow_drop_down,
                        contentDescription = stringResource(R.string.chat_change_model),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(MODEL_ARROW),
                    )
                }
            }
        }
        if (meter != null) {
            val description = meter.description()
            Text(SUBTITLE_SEPARATOR, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenContext)
                    .semantics { contentDescription = description }
                    .padding(horizontal = TAP_PADDING_H, vertical = TAP_PADDING_V),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RING_GAP),
            ) {
                ContextRing(meter.fraction, SUBTITLE_RING)
                if (state.contextDisplay == ContextDisplay.NUMBERS) {
                    Text(meter.tokenLabel(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HeaderAvatar(state: ChatUiState, actions: ChatMenuActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(HEADER_BUTTON)
                .clip(CircleShape)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.chat_more_options),
                    onLongClickLabel = stringResource(R.string.chat_look_title),
                    onLongClick = actions.look,
                    onClick = { open = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ChatBadge(state.look, size = AVATAR_SIZE)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.clickHaptics()) {
            ChatMenuItems(state, actions) { open = false }
        }
    }
}

@Composable
internal fun Modifier.renameOnLongPress(rename: () -> Unit): Modifier = this
    .clip(MaterialTheme.shapes.small)
    .combinedClickable(onLongClickLabel = stringResource(R.string.home_rename), onLongClick = rename, onClick = {})

@Composable
private fun HeaderCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CIRCLE_ALPHA),
        modifier = Modifier.size(HEADER_BUTTON),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private fun Modifier.headerFade(color: Color): Modifier = drawBehind {
    val total = size.height + FADE_HEIGHT.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = TOP_ALPHA),
                size.height / total to color.copy(alpha = BAND_ALPHA),
                1f to color.copy(alpha = 0f),
            ),
            startY = 0f,
            endY = total,
        ),
        size = Size(size.width, total),
    )
}

private val COMPACT_HEADER_HEIGHT = 60.dp
private val HEADER_BUTTON = 44.dp
private val AVATAR_SIZE = 40.dp
private val SIDE_PADDING = 8.dp
private val SIDE_GAP = 4.dp
private val TAP_PADDING_H = 6.dp
private val TAP_PADDING_V = 3.dp
private val RING_GAP = 5.dp
private val MODEL_START_PADDING = 8.dp
private val MODEL_END_PADDING = 2.dp
private val MODEL_GLYPH = 12.dp
private val MODEL_ARROW = 18.dp
private val SUBTITLE_RING = 12.dp
private val FADE_HEIGHT = 28.dp
private const val SUBTITLE_SEPARATOR = "·"
private const val CIRCLE_ALPHA = 0.8f
private const val TOP_ALPHA = 0.97f
private const val BAND_ALPHA = 0.9f
