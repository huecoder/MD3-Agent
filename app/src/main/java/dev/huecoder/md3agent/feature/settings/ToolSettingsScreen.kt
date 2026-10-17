package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.FetchBackend
import dev.huecoder.md3agent.data.settings.FetchSettings
import dev.huecoder.md3agent.data.settings.SearchBackend
import dev.huecoder.md3agent.data.settings.SearchSettings
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.ui.components.rememberSectionFolds
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SearchField
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.ToolCategory
import kotlinx.coroutines.launch

@Composable
fun ToolSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf<ToolSettings?>(null) }
    var saved by remember { mutableStateOf<ToolSettings?>(null) }

    LaunchedEffect(Unit) { container.settings.current().tools.let { saved = it.trimmed(); tools = it } }

    val save = tools?.let { current ->
        val draft = current.trimmed()
        SaveAction(isDirty = draft != saved, canSave = current.search.isComplete() && current.fetch.isComplete()) {
            scope.launch {
                container.settings.updateTools { persisted -> draft.copy(access = persisted.access, phoneUse = persisted.phoneUse) }
                saved = draft
                onBack()
            }
        }
    }

    val folds = rememberSectionFolds(*ToolCategory.entries.map { it.name }.toTypedArray())
    var query by rememberSaveable { mutableStateOf("") }
    val anchors = remember { SectionAnchors() }
    val scroll = rememberScrollState()
    val spyThreshold = with(LocalDensity.current) { SPY_OFFSET.toPx() }
    val currentGroup by remember { derivedStateOf { anchors.selected(scroll, spyThreshold) } }
    val mcpStates by container.mcp.states.collectAsStateWithLifecycle()
    val allTools = remember(mcpStates) { container.tools.all }

    SettingsScaffold(stringResource(R.string.settings_tools), onBack, save = save) { padding ->
        val current = tools ?: return@SettingsScaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, stringResource(R.string.tools_search_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ToolJumpBar(anchors.groups, currentGroup, onSelect = { group -> scope.launch { anchors.scrollTo(group, scroll) } })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(anchors.viewport())
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ToolPermissionGroups(
                    tools = allTools,
                    permissionOf = { container.tools.permission(it, current) },
                    onChange = { tool, permission -> tools = current.withPermission(tool.spec.name, permission) },
                    folds = folds,
                    query = query,
                    sectionModifier = { category -> anchors.anchor(category) },
                ) { category ->
                    if (category == ToolCategory.WEB) {
                        CardGroup(Modifier.padding(top = SERVICE_GROUP_GAP)) { searchServiceCards(current.search) { tools = current.copy(search = it) } }
                        CardGroup(Modifier.padding(top = SERVICE_GROUP_GAP)) { fetchServiceCards(current.fetch) { tools = current.copy(fetch = it) } }
                        CardGroup(Modifier.padding(top = SERVICE_GROUP_GAP)) { weatherServiceCards(current.weather) { tools = current.copy(weather = it) } }
                    }
                }
            }
        }
    }
}

private fun FetchSettings.isComplete(): Boolean = backend != FetchBackend.CUSTOM || customUrl.isNotBlank()

private fun SearchSettings.isComplete(): Boolean = when (backend) {
    SearchBackend.HTML_SCRAPE -> true
    SearchBackend.SEARXNG -> searxngUrl.isNotBlank()
    SearchBackend.BRAVE_API -> apiKey.isNotBlank()
}

private fun ToolSettings.trimmed() = copy(
    search = search.copy(searxngUrl = search.searxngUrl.trim(), apiKey = search.apiKey.trim()),
    weather = weather.copy(forecastUrl = weather.forecastUrl.trim(), geocodingUrl = weather.geocodingUrl.trim()),
    fetch = fetch.copy(jinaApiKey = fetch.jinaApiKey.trim(), customUrl = fetch.customUrl.trim(), customApiKey = fetch.customApiKey.trim()),
)

private val SPY_OFFSET = 96.dp
private val SERVICE_GROUP_GAP = 12.dp
