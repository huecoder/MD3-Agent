package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Notifications_active
import com.mwagent.R
import com.mwagent.data.db.ReminderEntity
import com.mwagent.data.reminders.ReminderRepository
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class RemindersViewModel(private val reminders: ReminderRepository) : ViewModel() {
    val upcoming: StateFlow<List<ReminderEntity>?> = reminders.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val canNotify: Boolean get() = reminders.canNotify

    fun cancel(id: String) {
        viewModelScope.launch { reminders.cancel(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { RemindersViewModel(it.reminders) }
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_reminders), onBack) { padding ->
        val list = upcoming ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { Hint(stringResource(R.string.reminders_hint)) }
            if (!viewModel.canNotify) item { Hint(stringResource(R.string.reminders_notifications_off), isWarning = true) }
            if (list.isEmpty()) item { Hint(stringResource(R.string.reminders_empty)) }
            itemsIndexed(list, key = { _, reminder -> reminder.id }) { index, reminder ->
                ReminderRow(reminder, index, list.size, onCancel = { viewModel.cancel(reminder.id) })
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: ReminderEntity, index: Int, count: Int, onCancel: () -> Unit) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = groupedListColors(),
        leadingContent = { ShapeBadge(MaterialSymbols.Rounded.Notifications_active) },
        supportingContent = { Text(formatTrigger(reminder.triggerAt)) },
        trailingContent = {
            IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(R.string.reminders_cancel))
            }
        },
    ) { Text(reminder.text, maxLines = 3, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Hint(text: String, isWarning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isWarning) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (isWarning) colors.onErrorContainer else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

private val TRIGGER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatTrigger(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TRIGGER_FORMAT)
