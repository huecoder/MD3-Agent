package dev.huecoder.md3agent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ProviderForm(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val models: List<String> = emptyList(),
    val defaultModel: String = "",
    val makeActive: Boolean = true,
    val format: ApiFormat = ApiFormat.OPENAI,
) {
    fun withFormat(next: ApiFormat) = copy(format = next, baseUrl = next.adoptBaseUrl(baseUrl))

    val baseUrlError: UiText?
        get() = if (baseUrl.isBlank() || baseUrl.trim().toHttpUrlOrNull() != null) null else UiText.Resource(R.string.provider_base_url_invalid)

    val canSave: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() && baseUrlError == null && defaultModel.isNotBlank()

    fun toProfile() = ProviderProfile(
        id = id,
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        extraHeaders = headers.filter { it.name.isNotBlank() },
        models = models,
        defaultModel = defaultModel.trim(),
        format = format,
    )
}

sealed interface ModelFetchState {
    data object Idle : ModelFetchState
    data object Loading : ModelFetchState
    data class Failed(val message: UiText) : ModelFetchState
}

class ProviderEditorViewModel(
    private val providerId: String?,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
) : ViewModel() {
    private val draft = FormDraft<ProviderForm>(viewModelScope)
    val form: StateFlow<ProviderForm?> = draft.value

    val isDirty: StateFlow<Boolean> = draft.isDirty

    private val _modelFetch = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
    val modelFetch: StateFlow<ModelFetchState> = _modelFetch.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.current()
            val existing = current.provider(providerId)
            draft.load(if (existing == null) {
                ProviderForm(id = settings.newProviderId(), isNew = true)
            } else {
                ProviderForm(
                    id = existing.id,
                    isNew = false,
                    name = existing.name,
                    baseUrl = existing.baseUrl,
                    apiKey = existing.apiKey,
                    headers = existing.extraHeaders,
                    models = existing.models,
                    defaultModel = existing.defaultModel,
                    makeActive = current.activeSelection?.providerId == existing.id,
                    format = existing.format,
                )
            })
        }
    }

    fun edit(transform: (ProviderForm) -> ProviderForm) = draft.edit(transform)

    fun fetchModels() {
        val current = draft.current ?: return
        _modelFetch.value = ModelFetchState.Loading
        viewModelScope.launch {
            _modelFetch.value = try {
                val models = catalog.refresh(current.toProfile()).map { it.id }
                edit { form -> form.copy(models = models, defaultModel = form.defaultModel.ifBlank { models.firstOrNull().orEmpty() }) }
                ModelFetchState.Idle
            } catch (e: ProviderException) {
                ModelFetchState.Failed(e.message?.let(UiText::Raw) ?: UiText.Resource(R.string.provider_models_fetch_failed))
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = draft.current?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            val profile = current.toProfile()
            settings.upsertProvider(profile)
            if (current.makeActive) settings.setActiveSelection(ModelSelection(profile.id, profile.defaultModel))
            draft.markSaved()
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = providerId ?: return onDeleted()
        viewModelScope.launch {
            settings.deleteProvider(id)
            catalog.forget(id)
            onDeleted()
        }
    }
}
