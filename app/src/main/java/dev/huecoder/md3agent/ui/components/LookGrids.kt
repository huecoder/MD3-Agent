package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.chat.ChatGlyph
import dev.huecoder.md3agent.data.chat.ChatTint

@Composable
fun GlyphGrid(selected: ChatGlyph?, modifier: Modifier = Modifier, onPick: (ChatGlyph) -> Unit) {
    OptionGrid(modifier) {
        ChatGlyph.entries.forEach { glyph ->
            Option(selected = glyph == selected, onClick = { onPick(glyph) }) {
                Icon(glyph.icon, contentDescription = glyph.key, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun TintGrid(selected: ChatTint, glyphIcon: ImageVector, modifier: Modifier = Modifier, onPick: (ChatTint) -> Unit) {
    OptionGrid(modifier) {
        ChatTint.entries.forEach { tint ->
            val colors = tint.colors()
            Option(selected = tint == selected, onClick = { onPick(tint) }, container = colors.container) {
                Icon(glyphIcon, contentDescription = tint.key, tint = colors.content, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun OptionGrid(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
fun Option(
    selected: Boolean,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(SELECTED_BORDER, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.size(OPTION_SIZE).semantics { this.selected = selected },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private val OPTION_SIZE = 48.dp
private val SELECTED_BORDER = 3.dp
