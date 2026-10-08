package dev.huecoder.md3agent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.llm.ModelCatalogClient
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class VoiceProviderForm(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val transcriptionModel: String = VoiceProvider.DEFAULT_TRANSCRIPTION_MODEL,
    val speechModel: String = VoiceProvider.DEFAULT_SPEECH_MODEL,
    val speechVoice: String = VoiceProvider.DEFAULT_SPEECH_VOICE,
    val models: List<String> = emptyList(),
) {
    val baseUrlError: UiText?
        get() = if (baseUrl.isBlank() || baseUrl.trim().toHttpUrlOrNull() != null) null else UiText.Resource(R.string.provider_base_url_invalid)

    val canSave: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() && baseUrlError == null && transcriptionModel.isNotBlank()

    fun copyEndpoint(source: ProviderProfile) = copy(
        name = name.ifBlank { source.name },
        baseUrl = source.baseUrl,
        apiKey = source.apiKey,
        headers = source.extraHeaders,
    )

    fun toProvider() = VoiceProvider(
        id = id,
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        extraHeaders = headers.filter { it.name.isNotBlank() },
        transcriptionModel = transcriptionModel.trim(),
        speechModel = speechModel.trim().ifBlank { VoiceProvider.DEFAULT_SPEECH_MODEL },
        speechVoice = speechVoice.trim().ifBlank { VoiceProvider.DEFAULT_SPEECH_VOICE },
    )
}

class VoiceProviderEditorViewModel(
    private val providerId: String?,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalogClient,
) : ViewModel() {
    private val draft = FormDraft<VoiceProviderForm>(viewModelScope)
    val form: StateFlow<VoiceProviderForm?> = draft.value

    val isDirty: StateFlow<Boolean> = draft.isDirty

    private val _chatProviders = MutableStateFlow<List<ProviderProfile>>(emptyList())
    val chatProviders: StateFlow<List<ProviderProfile>> = _chatProviders.asStateFlow()

    private val _modelFetch = MutableStateFlow<ModelFetchState>(ModelFetchState.Idle)
    val modelFetch: StateFlow<ModelFetchState> = _modelFetch.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.current()
            _chatProviders.value = current.providers
            val existing = current.voice.providers.firstOrNull { it.id == providerId }
            draft.load(existing?.let {
                VoiceProviderForm(
                    id = it.id,
                    isNew = false,
                    name = it.name,
                    baseUrl = it.baseUrl,
                    apiKey = it.apiKey,
                    headers = it.extraHeaders,
                    transcriptionModel = it.transcriptionModel,
                    speechModel = it.speechModel,
                    speechVoice = it.speechVoice,
                )
            } ?: VoiceProviderForm(id = settings.newProviderId(), isNew = true))
        }
    }

    fun edit(transform: (VoiceProviderForm) -> VoiceProviderForm) = draft.edit(transform)

    fun fetchModels() {
        val current = draft.current ?: return
        _modelFetch.value = ModelFetchState.Loading
        viewModelScope.launch {
            _modelFetch.value = try {
                val models = catalog.listModels(current.toProvider().asEndpointProfile())
                edit { it.copy(models = models) }
                ModelFetchState.Idle
            } catch (e: ProviderException) {
                ModelFetchState.Failed(e.message?.let(UiText::Raw) ?: UiText.Resource(R.string.provider_models_fetch_failed))
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = draft.current?.takeIf { it.canSave } ?: return
        val provider = current.toProvider()
        viewModelScope.launch {
            settings.updateVoice { voice ->
                val exists = voice.providers.any { it.id == provider.id }
                val providers = if (exists) voice.providers.map { if (it.id == provider.id) provider else it } else voice.providers + provider
                voice.copy(providers = providers)
            }
            draft.markSaved()
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = providerId ?: return onDeleted()
        viewModelScope.launch {
            settings.updateVoice { voice ->
                voice.copy(
                    providers = voice.providers.filterNot { it.id == id },
                    activeProviderId = voice.activeProviderId.takeUnless { it == id },
                    speechProviderId = voice.speechProviderId.takeUnless { it == id },
                )
            }
            onDeleted()
        }
    }
}
