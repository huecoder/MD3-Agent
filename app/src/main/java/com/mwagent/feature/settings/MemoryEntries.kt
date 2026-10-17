package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Bolt
import com.composables.icons.materialsymbols.rounded.Neurology
import com.mwagent.R
import com.mwagent.data.db.MemoryEntity
import com.mwagent.data.db.MemoryTier
import com.mwagent.data.memory.MemoryStore
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.groupedListColors
import com.mwagent.ui.haptics.clickHaptics

internal fun LazyListScope.memorySection(
    tier: MemoryTier,
    hint: @Composable () -> String,
    memories: List<MemoryEntity>,
    onOpen: (MemoryDraft) -> Unit,
) {
    val count = memories.size + 1
    item(key = "header-$tier") {
        Column {
            SectionHeader(stringResource(tier.title))
            Text(
                hint(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }
    }
    itemsIndexed(memories, key = { _, memory -> memory.id }) { index, memory ->
        SegmentedListItem(
            onClick = { onOpen(MemoryDraft(memory.id, memory.tier, memory.title, memory.content)) },
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = groupedListColors(),
            leadingContent = { ShapeBadge(tier.icon) },
            supportingContent = { Text(memory.content, maxLines = PREVIEW_LINES, overflow = TextOverflow.Ellipsis) },
        ) { Text(memory.title, style = MaterialTheme.typography.titleMediumEmphasized) }
    }
    item(key = "add-$tier") {
        SegmentedListItem(
            onClick = { onOpen(MemoryDraft(tier = tier)) },
            shapes = ListItemDefaults.segmentedShapes(count - 1, count),
            colors = groupedListColors(),
            leadingContent = {
                ShapeBadge(
                    MaterialSymbols.Rounded.Add,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        ) { Text(stringResource(R.string.memory_add), style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}

@Composable
internal fun MemoryEditorDialog(
    initial: MemoryDraft,
    onSubmit: (MemoryDraft, (String?) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var error by remember(initial) { mutableStateOf<String?>(null) }
    val contentLimit = if (draft.tier == MemoryTier.INSTANT) MemoryStore.MAX_INSTANT_CONTENT else MemoryStore.MAX_CONTENT

    AlertDialog(
        modifier = Modifier.clickHaptics(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == null) R.string.memory_add else R.string.memory_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedChoices(TIER_CHOICES, draft.tier, { draft = draft.copy(tier = it) })
                FormField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it.take(MemoryStore.MAX_TITLE)); error = null },
                    label = stringResource(R.string.memory_title),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormField(
                    value = draft.content,
                    onValueChange = { draft = draft.copy(content = it); error = null },
                    label = stringResource(R.string.memory_content),
                    singleLine = false,
                    isError = draft.content.length > contentLimit || error != null,
                    supportingText = error ?: "${draft.content.length} / $contentLimit",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(draft) { failure -> if (failure == null) onDismiss() else error = failure } },
                enabled = draft.canSubmit && draft.content.length <= contentLimit,
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) }
            }
        },
    )
}

private val MemoryTier.title: Int
    get() = when (this) {
        MemoryTier.INSTANT -> R.string.memory_section_instant
        MemoryTier.LONG_TERM -> R.string.memory_section_long_term
    }

private val MemoryTier.icon
    get() = when (this) {
        MemoryTier.INSTANT -> MaterialSymbols.Rounded.Bolt
        MemoryTier.LONG_TERM -> MaterialSymbols.Rounded.Neurology
    }

private val TIER_CHOICES = listOf(
    Choice(MemoryTier.INSTANT, R.string.memory_tier_instant),
    Choice(MemoryTier.LONG_TERM, R.string.memory_tier_long_term),
)

private const val PREVIEW_LINES = 2
