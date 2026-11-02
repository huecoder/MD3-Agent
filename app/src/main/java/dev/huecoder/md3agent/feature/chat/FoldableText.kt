package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Expand_more
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.animateReveal
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlin.math.roundToInt

@Composable
fun FoldableText(text: String, stateKey: String, fadeColor: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyLarge
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toPx() }
    val foldFrom = lineHeight * FOLD_FROM_LINES
    val foldTo = lineHeight * FOLD_TO_LINES
    val fadeHeight = lineHeight * FADE_LINES
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    var foldable by rememberSaveable(stateKey, text) { mutableStateOf(false) }
    val reveal = animateReveal(expanded)
    Column(modifier) {
        Box(
            Modifier
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val hidden = 1f - reveal.value
                    if (foldable && hidden > 0f) {
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, fadeColor), startY = size.height - fadeHeight, endY = size.height), alpha = hidden)
                    }
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = if (placeable.height > foldFrom) lerp(foldTo, placeable.height.toFloat(), reveal.value).roundToInt() else placeable.height
                    layout(placeable.width, height) { placeable.place(0, 0) }
                },
        ) {
            SelectionContainer { Text(text, style = style, onTextLayout = { foldable = it.size.height > foldFrom }) }
        }
        if (foldable) FoldToggle(expanded, reveal) { expanded = !expanded }
    }
}

@Composable
private fun FoldToggle(expanded: Boolean, reveal: State<Float>, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .padding(top = TOGGLE_TOP_GAP)
            .clip(MaterialTheme.shapes.small)
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(vertical = TOGGLE_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(if (expanded) R.string.chat_message_show_less else R.string.chat_message_show_more), style = MaterialTheme.typography.labelLarge)
        Icon(
            MaterialSymbols.Rounded.Expand_more,
            contentDescription = null,
            modifier = Modifier.size(TOGGLE_ICON_SIZE).graphicsLayer { rotationZ = HALF_TURN * reveal.value },
        )
    }
}

private const val FOLD_FROM_LINES = 13
private const val FOLD_TO_LINES = 10
private const val FADE_LINES = 2
private const val HALF_TURN = 180f
private val TOGGLE_TOP_GAP = 4.dp
private val TOGGLE_VERTICAL_PADDING = 4.dp
private val TOGGLE_ICON_SIZE = 18.dp
