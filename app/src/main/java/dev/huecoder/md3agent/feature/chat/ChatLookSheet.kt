package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatGlyph
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.chat.ChatShape
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.colors
import dev.huecoder.md3agent.ui.components.icon
import dev.huecoder.md3agent.ui.components.polygon
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.haptics.clickHaptics

@Composable
fun ChatLookSheet(look: ChatLook, onChange: (ChatLook) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val pick = { updated: ChatLook ->
        haptics.tick()
        onChange(updated)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.clickHaptics()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                ChatBadge(look, size = PREVIEW_SIZE)
            }
            SectionHeader(stringResource(R.string.chat_look_icon))
            OptionGrid {
                ChatGlyph.entries.forEach { glyph ->
                    Option(selected = glyph == look.glyph, onClick = { pick(look.copy(glyph = glyph)) }) {
                        Icon(glyph.icon, contentDescription = glyph.key, modifier = Modifier.size(24.dp))
                    }
                }
            }
            SectionHeader(stringResource(R.string.chat_look_color))
            OptionGrid {
                ChatTint.entries.forEach { tint ->
                    val colors = tint.colors()
                    Option(selected = tint == look.tint, onClick = { pick(look.copy(tint = tint)) }, container = colors.container) {
                        Icon(look.glyph.icon, contentDescription = tint.key, tint = colors.content, modifier = Modifier.size(20.dp))
                    }
                }
            }
            SectionHeader(stringResource(R.string.chat_look_shape))
            OptionGrid(Modifier.padding(bottom = 24.dp)) {
                ChatShape.entries.forEach { shape ->
                    Option(selected = shape == look.shape, onClick = { pick(look.copy(shape = shape)) }) {
                        Surface(
                            shape = shape.polygon.toShape(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            content = {},
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.chat_look_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun OptionGrid(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun Option(
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

private val PREVIEW_SIZE = 72.dp
private val OPTION_SIZE = 48.dp
private val SELECTED_BORDER = 3.dp
