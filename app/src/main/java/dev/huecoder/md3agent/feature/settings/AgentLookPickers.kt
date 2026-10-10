package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.AgentShapes
import dev.huecoder.md3agent.ui.avatar.polygon
import dev.huecoder.md3agent.ui.components.boldColors
import dev.huecoder.md3agent.ui.haptics.rememberHaptics

@Composable
fun AgentShapePicker(selected: (AgentShape) -> Boolean, tint: ChatTint, onToggle: (AgentShape) -> Unit) {
    val haptics = rememberHaptics()
    val body = tint.boldColors().container
    OptionGrid {
        AgentShapes.faces.forEach { shape ->
            Option(selected = selected(shape), description = shape.name, onClick = { haptics.tick(); onToggle(shape) }) {
                Surface(shape = shape.polygon.toShape(), color = body, modifier = Modifier.size(SHAPE_SIZE), content = {})
            }
        }
    }
}

@Composable
fun AgentTintPicker(selected: ChatTint, onSelect: (ChatTint) -> Unit) {
    val haptics = rememberHaptics()
    OptionGrid {
        ChatTint.entries.forEach { tint ->
            val colors = tint.boldColors()
            Option(selected = tint == selected, description = tint.key, onClick = { haptics.tick(); onSelect(tint) }, container = colors.container) {}
        }
    }
}

@Composable
private fun OptionGrid(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun Option(
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = if (selected) BorderStroke(SELECTED_BORDER, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.size(OPTION_SIZE).semantics {
            this.selected = selected
            contentDescription = description
        },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private val OPTION_SIZE = 48.dp
private val SHAPE_SIZE = 28.dp
private val SELECTED_BORDER = 3.dp
