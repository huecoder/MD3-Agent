package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.ui.components.FoldableHeader
import dev.huecoder.md3agent.ui.components.SectionFolds
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
    query: String = "",
    sectionModifier: @Composable (ToolCategory) -> Modifier = { Modifier },
    extras: @Composable (ToolCategory) -> Unit = {},
) {
    val modes = rememberToolModes()
    val searching = query.isNotBlank()
    val sections = toolSections(tools, query)
    if (sections.isEmpty()) {
        Text(
            stringResource(R.string.tools_search_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_PADDING),
        )
        return
    }
    sections.forEach { section ->
        val open = searching || folds.isOpen(section.category.name)
        FoldableHeader(
            title = section.title,
            expanded = open,
            onToggle = { if (!searching) folds.toggle(section.category.name) },
            summary = stringResource(R.string.tools_category_enabled, section.all.count { permissionOf(it.tool) != ToolPermission.OFF }, section.all.size),
            modifier = sectionModifier(section.category),
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
            exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                section.shown.forEachIndexed { index, entry ->
                    ToolPermissionCard(
                        toolName = entry.tool.spec.name,
                        icon = entry.info.icon,
                        title = entry.title,
                        summary = entry.summary,
                        permission = permissionOf(entry.tool),
                        defaultPermission = entry.tool.defaultPermission,
                        modes = modes,
                        shape = ListItemDefaults.segmentedShapes(index, section.shown.size).shape,
                        onChange = { onChange(entry.tool, it) },
                    )
                }
                if (!searching) extras(section.category)
            }
        }
    }
}

private class ToolEntry(val tool: Tool, val info: ToolInfo, val title: String, val summary: String)

private class ToolSection(val category: ToolCategory, val title: String, val all: List<ToolEntry>, val shown: List<ToolEntry>)

@Composable
private fun toolSections(tools: List<Tool>, query: String): List<ToolSection> {
    val byCategory = tools
        .map { tool -> ToolCatalog.of(tool).let { ToolEntry(tool, it, it.title.asString(), it.summary.asString()) } }
        .groupBy { it.info.category }
    return ToolCategory.entries.mapNotNull { category ->
        val all = byCategory[category] ?: return@mapNotNull null
        val title = stringResource(category.title)
        val shown = all.filter { matchesSearch(query, title, it.title, it.summary, it.tool.spec.name) }
        ToolSection(category, title, all, shown).takeIf { shown.isNotEmpty() }
    }
}

private val EMPTY_PADDING = 48.dp
