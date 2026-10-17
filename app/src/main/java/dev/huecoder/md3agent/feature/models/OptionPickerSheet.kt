package dev.huecoder.md3agent.feature.models

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Play_arrow
import com.composables.icons.materialsymbols.rounded.Stop
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.BadgePolygon
import dev.huecoder.md3agent.ui.components.SearchField
import dev.huecoder.md3agent.ui.components.bottomEdgeFade
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.voice.PreviewState
import dev.huecoder.md3agent.voice.VoicePreviewPlayer

@Immutable
data class PickerOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val previewUrl: String? = null,
) {
    fun matches(words: List<String>, requiredTags: List<String>): Boolean {
        val haystack = listOfNotNull(title, id, description).plus(tags).joinToString(" ")
        return requiredTags.all { it in tags } && words.all { haystack.contains(it, ignoreCase = true) }
    }
}

@Immutable
data class PickerPage(
    val title: String,
    val icon: ImageVector,
    val selectedId: String,
    val options: List<PickerOption>,
    @PluralsRes val countLabel: Int,
    val emptyHint: String,
    val onPick: (PickerOption) -> Unit,
    val moreOptions: List<PickerOption> = emptyList(),
    val loading: Boolean = false,
    val error: UiText? = null,
    val onRefresh: (() -> Unit)? = null,
)

@Composable
fun OptionPickerSheet(tabs: List<PickerPage>, initialTab: Int, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var requestedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val selectedTab = requestedTab.coerceIn(tabs.indices)
    val tab = tabs[selectedTab]
    val previews = (LocalContext.current.applicationContext as App).container.voicePreviews
    DisposableEffect(previews) { onDispose { previews.stop() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.clickHaptics(),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            if (tabs.size > 1) {
                PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { index, item ->
                        Tab(selected = index == selectedTab, onClick = { requestedTab = index }, text = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            } else {
                Text(tab.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp))
            }
            key(selectedTab) {
                TabContent(tab, previews, onPick = { option ->
                    tab.onPick(option)
                    onDismiss()
                })
            }
        }
    }
}

@Composable
private fun ColumnScope.TabContent(tab: PickerPage, previews: VoicePreviewPlayer, onPick: (PickerOption) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var activeTags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    val preview by previews.state.collectAsStateWithLifecycle()
    val pool = if (showMore) tab.options + tab.moreOptions else tab.options
    val tags = remember(pool) { frequentTags(pool) }
    val words = remember(query) { query.split(' ').filter { it.isNotBlank() } }
    val rows = remember(pool, words, activeTags) { pool.filter { it.matches(words, activeTags) } }
    val typed = query.trim()
    val customId = typed.takeIf { text -> text.isNotEmpty() && (tab.options + tab.moreOptions).none { it.id == text } && (rows.isEmpty() || text.looksLikeId()) }
    val listState = rememberLazyListState()
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow

    LaunchedEffect(rows.isNotEmpty()) {
        val index = rows.indexOfFirst { it.id == tab.selectedId }
        if (index > 0) listState.scrollToItem(index)
    }

    PickerHeader(pluralStringResource(tab.countLabel, pool.size, pool.size), tab.loading, tab.onRefresh)
    SearchField(query, { query = it }, stringResource(R.string.picker_search), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    if (tab.moreOptions.isNotEmpty() || tags.isNotEmpty()) {
        ChipRow {
            if (tab.moreOptions.isNotEmpty()) {
                FilterChip(selected = showMore, onClick = { showMore = !showMore }, label = { Text(stringResource(R.string.picker_show_all)) })
            }
            tags.forEach { tag ->
                FilterChip(
                    selected = tag in activeTags,
                    onClick = { activeTags = if (tag in activeTags) activeTags - tag else activeTags + tag },
                    label = { Text(tag) },
                )
            }
        }
    }
    val failure = tab.error ?: UiText.Resource(R.string.picker_preview_failed).takeIf { preview is PreviewState.Failed }
    failure?.let {
        Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
    if (rows.isEmpty() && customId == null) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            if (!tab.loading) {
                Text(
                    if (pool.isEmpty()) tab.emptyHint else stringResource(R.string.picker_no_match),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).bottomEdgeFade(color = sheetColor, underNavigationBar = false),
        contentPadding = PickerListPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        customId?.let { id ->
            item(key = CUSTOM_KEY) { CustomRow(id, onClick = { onPick(PickerOption(id = id, title = id)) }) }
        }
        itemsIndexed(rows, key = { _, option -> option.id }) { index, option ->
            OptionRow(
                option = option,
                icon = tab.icon,
                selected = option.id == tab.selectedId,
                index = index,
                count = rows.size,
                preview = preview,
                onPreview = previews::toggle,
                onClick = { onPick(option) },
            )
        }
    }
}

@Composable
private fun CustomRow(id: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = ListItemDefaults.segmentedColors(containerColor = colors.tertiaryContainer),
        leadingContent = { OptionBadge(MaterialSymbols.Rounded.Edit, null, colors.tertiary, colors.onTertiary, PreviewState.Idle, {}) },
        supportingContent = { Text(stringResource(R.string.picker_custom_hint), color = colors.onTertiaryContainer) },
        modifier = Modifier.padding(bottom = 8.dp),
    ) { Text(stringResource(R.string.picker_use_custom, id), style = MaterialTheme.typography.titleMediumEmphasized, color = colors.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun OptionRow(
    option: PickerOption,
    icon: ImageVector,
    selected: Boolean,
    index: Int,
    count: Int,
    preview: PreviewState,
    onPreview: (String) -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        selected = selected,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer, selectedContainerColor = colors.secondaryContainer),
        leadingContent = {
            OptionBadge(
                icon = icon,
                previewUrl = option.previewUrl,
                container = if (selected) colors.secondary else colors.surfaceContainerHighest,
                content = if (selected) colors.onSecondary else colors.onSurface,
                preview = preview,
                onPreview = onPreview,
            )
        },
        supportingContent = { OptionDetails(option, selected) },
        trailingContent = if (selected) {
            { Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = null, tint = colors.primary) }
        } else {
            null
        },
    ) { Text(option.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun OptionDetails(option: PickerOption, selected: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        option.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = DESCRIPTION_LINES, overflow = TextOverflow.Ellipsis) }
        if (option.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) { option.tags.forEach { TagLabel(it, selected) } }
        }
    }
}

@Composable
private fun TagLabel(text: String, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (selected) colors.onSecondaryContainer.copy(alpha = SELECTED_TAG_ALPHA) else colors.surfaceContainerHighest) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun OptionBadge(
    icon: ImageVector,
    previewUrl: String?,
    container: Color,
    content: Color,
    preview: PreviewState,
    onPreview: (String) -> Unit,
) {
    val shape = BadgePolygon.toShape()
    val active = previewUrl != null && preview.url == previewUrl && (preview is PreviewState.Loading || preview is PreviewState.Playing)
    val glyph: @Composable () -> Unit = {
        Box(Modifier.size(BADGE_SIZE), contentAlignment = Alignment.Center) {
            when {
                active && preview is PreviewState.Loading -> LoadingIndicator(Modifier.size(BADGE_GLYPH), color = LocalContentColor.current)
                active -> Icon(MaterialSymbols.Rounded.Stop, contentDescription = stringResource(R.string.picker_preview_stop), modifier = Modifier.size(BADGE_GLYPH))
                previewUrl != null -> Icon(MaterialSymbols.Rounded.Play_arrow, contentDescription = stringResource(R.string.picker_preview_play), modifier = Modifier.size(BADGE_GLYPH))
                else -> Icon(icon, contentDescription = null, modifier = Modifier.size(BADGE_GLYPH))
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    val tint = if (active) colors.primary else container
    val ink = if (active) colors.onPrimary else content
    if (previewUrl != null) {
        Surface(onClick = { onPreview(previewUrl) }, shape = shape, color = tint, contentColor = ink, content = glyph)
    } else {
        Surface(shape = shape, color = tint, contentColor = ink, content = glyph)
    }
}

private fun String.looksLikeId() = none { it.isWhitespace() } && any { !it.isLetter() }

private fun frequentTags(options: List<PickerOption>): List<String> {
    if (options.size < MIN_OPTIONS_FOR_TAGS) return emptyList()
    return options.flatMap { it.tags.distinct() }
        .groupingBy { it }
        .eachCount()
        .filterValues { count -> count >= MIN_TAG_USES && count < options.size }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_TAG_CHIPS)
        .map { it.key }
}

private const val SHEET_HEIGHT_FRACTION = 0.92f
private const val MIN_OPTIONS_FOR_TAGS = 6
private const val MIN_TAG_USES = 2
private const val MAX_TAG_CHIPS = 16
private const val TITLE_LINES = 2
private const val DESCRIPTION_LINES = 3
private const val SELECTED_TAG_ALPHA = 0.1f
private const val CUSTOM_KEY = "custom-option"
private val BADGE_SIZE = 40.dp
private val BADGE_GLYPH = 22.dp
