package dev.huecoder.md3agent.feature.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.models.ModelFamily
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.llm.models.ModelTraits
import dev.huecoder.md3agent.llm.models.ModelVendor
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CapabilityFilter { VISION, REASONING, IMAGES }

data class ModelRow(val info: ModelInfo, val family: ModelFamily?, val traits: ModelTraits)

data class ModelPickerState(
    val providers: List<ProviderProfile> = emptyList(),
    val providerId: String? = null,
    val rows: List<ModelRow> = emptyList(),
    val vendors: List<ModelVendor> = emptyList(),
    val totalCount: Int = 0,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ModelPickerViewModel(
    initialProviderId: String?,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
) : ViewModel() {
    private val providerId = MutableStateFlow(initialProviderId)
    private val query = MutableStateFlow("")
    private val capabilities = MutableStateFlow(emptySet<CapabilityFilter>())
    private val vendor = MutableStateFlow<ModelVendor?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<UiText?>(null)

    val queryText: StateFlow<String> = query.asStateFlow()
    val activeCapabilities: StateFlow<Set<CapabilityFilter>> = capabilities.asStateFlow()
    val activeVendor: StateFlow<ModelVendor?> = vendor.asStateFlow()

    val agents: StateFlow<List<AgentProfile>> = settings.settings
        .map { it.agents.agents }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val providers = settings.settings.map { it.providers }

    private val provider = combine(providers, providerId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }

    private val allRows = provider.filterNotNull().flatMapLatest { profile ->
        catalog.observe(profile.id).map { cached ->
            cached?.models.orEmpty().map { ModelRow(it, it.family, it.traits) }
        }
    }.flowOn(Dispatchers.Default)

    private val filters = combine(query, capabilities, vendor) { text, caps, chosenVendor -> Filters(text.trim(), caps, chosenVendor) }

    private val listing = combine(allRows, filters) { rows, active -> rows to active.apply(rows) }.flowOn(Dispatchers.Default)

    private val status = combine(refreshing, error) { isRefreshing, failure -> isRefreshing to failure }

    val state: StateFlow<ModelPickerState> = combine(providers, provider, listing, status) { list, current, (rows, filtered), (isRefreshing, failure) ->
        ModelPickerState(
            providers = list,
            providerId = current?.id,
            rows = filtered,
            vendors = rows.mapNotNull { it.family?.vendor }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
            totalCount = rows.size,
            isRefreshing = isRefreshing,
            error = failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ModelPickerState())

    init {
        viewModelScope.launch {
            provider.filterNotNull().map { it.id }.collect { id ->
                settings.current().provider(id)?.let { catalog.refreshIfStale(it) }
            }
        }
    }

    fun selectProvider(id: String) {
        providerId.value = id
        vendor.value = null
    }

    fun search(text: String) {
        query.value = text
    }

    fun toggle(filter: CapabilityFilter) = capabilities.update { if (filter in it) it - filter else it + filter }

    fun toggle(selected: ModelVendor) = vendor.update { if (it == selected) null else selected }

    fun refresh() {
        val id = state.value.providerId ?: return
        viewModelScope.launch {
            val profile = settings.current().provider(id) ?: return@launch
            refreshing.value = true
            error.value = null
            try {
                catalog.refresh(profile)
            } catch (e: ProviderException) {
                error.value = e.message?.let(UiText::Raw)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun traitsOf(selection: ModelSelection): ModelTraits = catalog.info(selection.providerId, selection.modelId).traits

    fun selection(row: ModelRow, current: ModelSelection?): ModelSelection {
        val providerKey = state.value.providerId ?: current?.providerId.orEmpty()
        val keptEffort = current?.reasoningEffort?.takeIf { it in row.traits.reasoningEfforts }
        return ModelSelection(providerKey, row.info.id, keptEffort)
    }

    private data class Filters(val text: String, val capabilities: Set<CapabilityFilter>, val vendor: ModelVendor?) {
        fun apply(rows: List<ModelRow>): List<ModelRow> = rows.filter { row ->
            (vendor == null || row.family?.vendor == vendor) &&
                capabilities.all { row.traits.matches(it) } &&
                (text.isEmpty() || row.matches(text))
        }

        private fun ModelRow.matches(text: String): Boolean {
            val words = text.split(' ').filter { it.isNotBlank() }
            val haystack = listOfNotNull(info.id, info.name, family?.displayName, family?.vendor?.displayName).joinToString(" ")
            return words.all { haystack.contains(it, ignoreCase = true) }
        }

        private fun ModelTraits.matches(filter: CapabilityFilter) = when (filter) {
            CapabilityFilter.VISION -> vision
            CapabilityFilter.REASONING -> reasoning
            CapabilityFilter.IMAGES -> generatesImages
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
