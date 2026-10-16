package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.ui.haptics.rememberHaptics

@Composable
internal fun ModePill(mode: ToolPermission, enabled: Boolean, onSelect: (ToolPermission) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = if (enabled) colors.surfaceContainerHighest else colors.onSurface.copy(alpha = TRACK_DISABLED_ALPHA)
    Row(modifier.height(PILL_HEIGHT).clip(CircleShape).background(track).selectableGroup()) {
        MODES.forEach { choice ->
            ModeOption(
                label = stringResource(choice.label),
                selected = choice.value == mode,
                enabled = enabled,
                onClick = { onSelect(choice.value) },
            )
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val container by animateColorAsState(
        when {
            !selected -> Color.Transparent
            enabled -> colors.secondaryContainer
            else -> colors.onSurface.copy(alpha = THUMB_DISABLED_ALPHA)
        },
        label = "mode-option",
    )
    val content = when {
        !enabled -> colors.onSurface.copy(alpha = CONTENT_DISABLED_ALPHA)
        selected -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = OPTION_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

private val MODES = listOf(
    Choice(ToolPermission.ASK, R.string.tools_permission_ask),
    Choice(ToolPermission.AUTO, R.string.tools_permission_auto),
)

private val PILL_HEIGHT = 32.dp
private val OPTION_PADDING = 12.dp
private const val TRACK_DISABLED_ALPHA = 0.06f
private const val THUMB_DISABLED_ALPHA = 0.12f
private const val CONTENT_DISABLED_ALPHA = 0.38f
