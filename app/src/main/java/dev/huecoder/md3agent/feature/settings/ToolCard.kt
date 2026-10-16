package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.ui.components.CheckSwitch
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.theme.LayoutMotion

@Stable
internal class ToolModeMemory {
    private val remembered = mutableStateMapOf<String, ToolPermission>()

    fun modeOf(toolName: String, permission: ToolPermission, default: ToolPermission): ToolPermission =
        modeBehind(permission, remembered[toolName], default)

    fun remember(toolName: String, mode: ToolPermission) {
        remembered[toolName] = mode
    }
}

@Composable
internal fun rememberToolModes(): ToolModeMemory = remember { ToolModeMemory() }

@Composable
internal fun ToolPermissionCard(
    toolName: String,
    icon: ImageVector,
    title: String,
    summary: String?,
    permission: ToolPermission,
    defaultPermission: ToolPermission,
    modes: ToolModeMemory,
    shape: Shape,
    onChange: (ToolPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = modes.modeOf(toolName, permission, defaultPermission)
    ToolCard(
        icon = icon,
        title = title,
        summary = summary,
        enabled = permission != ToolPermission.OFF,
        mode = mode,
        shape = shape,
        onMode = {
            modes.remember(toolName, it)
            onChange(it)
        },
        onEnabled = { on ->
            if (!on) modes.remember(toolName, mode)
            onChange(if (on) mode else ToolPermission.OFF)
        },
        modifier = modifier,
    )
}

@Composable
internal fun ToolCard(
    icon: ImageVector,
    title: String,
    summary: String?,
    enabled: Boolean,
    mode: ToolPermission,
    shape: Shape,
    onMode: (ToolPermission) -> Unit,
    onEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val dim by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, LayoutMotion.settle(), label = "tool-dim")
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        ToolCardLayout(
            modifier = Modifier.padding(horizontal = ROW_PADDING, vertical = ROW_PADDING_VERTICAL),
            icon = { ShapeBadge(icon, size = BADGE_SIZE, modifier = Modifier.alpha(dim)) },
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(dim),
                )
            },
            summary = summary?.takeIf { it.isNotBlank() }?.let { text ->
                {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(dim),
                    )
                }
            },
            mode = { ModePill(mode = mode, enabled = enabled, onSelect = onMode) },
            toggle = {
                CheckSwitch(
                    checked = enabled,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onEnabled(it)
                    },
                    modifier = Modifier.semantics { contentDescription = title },
                )
            },
        )
    }
}

private val BADGE_SIZE = 32.dp
private val ROW_PADDING = 16.dp
private val ROW_PADDING_VERTICAL = 12.dp
private const val DISABLED_ALPHA = 0.38f
private const val TITLE_MAX_LINES = 2
