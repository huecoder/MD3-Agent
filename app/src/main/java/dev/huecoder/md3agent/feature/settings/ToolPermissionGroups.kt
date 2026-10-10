package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.ui.components.FoldableHeader
import dev.huecoder.md3agent.ui.components.SectionFolds
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.components.ToolCategory
import dev.huecoder.md3agent.ui.components.ToolInfo
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.theme.LayoutMotion

@Composable
fun ToolPermissionGroups(
    tools: List<Tool>,
    permissionOf: (Tool) -> ToolPermission,
    onChange: (Tool, ToolPermission) -> Unit,
    folds: SectionFolds,
    extras: @Composable (ToolCategory) -> Unit = {},
) {
    val grouped = tools.groupBy { ToolCatalog.of(it).category }
    ToolCategory.entries.forEach { category ->
        val categoryTools = grouped[category].orEmpty()
        if (categoryTools.isEmpty()) return@forEach
        val enabled = categoryTools.count { permissionOf(it) != ToolPermission.OFF }
        FoldableHeader(
            title = stringResource(category.title),
            expanded = folds.isOpen(category.name),
            onToggle = { folds.toggle(category.name) },
            summary = stringResource(R.string.tools_category_enabled, enabled, categoryTools.size),
        )
        AnimatedVisibility(
            visible = folds.isOpen(category.name),
            enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
            exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                categoryTools.forEachIndexed { index, tool ->
                    ToolPermissionRow(
                        info = ToolCatalog.of(tool),
                        permission = permissionOf(tool),
                        shape = ListItemDefaults.segmentedShapes(index, categoryTools.size).shape,
                        onChange = { onChange(tool, it) },
                    )
                }
                extras(category)
            }
        }
    }
}

@Composable
private fun ToolPermissionRow(info: ToolInfo, permission: ToolPermission, shape: Shape, onChange: (ToolPermission) -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShapeBadge(info.icon, size = BADGE_SIZE)
                Column(Modifier.weight(1f)) {
                    Text(info.title.asString(), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(info.summary.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SegmentedChoices(PERMISSION_CHOICES, permission, onChange)
        }
    }
}

private val PERMISSION_CHOICES = listOf(
    Choice(ToolPermission.OFF, R.string.tools_permission_off),
    Choice(ToolPermission.ASK, R.string.tools_permission_ask),
    Choice(ToolPermission.AUTO, R.string.tools_permission_auto),
)

private val BADGE_SIZE = 40.dp
