package dev.huecoder.md3agent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.llm.ModelCatalogClient
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.llm.VoiceListClient
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val speechVoiceName: String = "",
    val format: ApiFormat = ApiFormat.OPENAI,
) {
    fun withFormat(next: ApiFormat): VoiceProviderForm {
        val voice = speechVoice.adopt(VoiceProvider.defaultSpeechVoice(format), VoiceProvider.defaultSpeechVoice(next))
        return copy(
            format = next,
            baseUrl = next.adoptBaseUrl(baseUrl),
            transcriptionModel = transcriptionModel.adopt(VoiceProvider.defaultTranscriptionModel(format), VoiceProvider.defaultTranscriptionModel(next)),
            speechModel = speechModel.adopt(VoiceProvider.defaultSpeechModel(format), VoiceProvider.defaultSpeechModel(next)),
            speechVoice = voice,
            speechVoiceName = speechVoiceName.takeIf { voice == speechVoice }.orEmpty(),
        )
    }

    private fun String.adopt(oldDefault: String, newDefault: String) = if (isBlank() || this == oldDefault) newDefault else this

    val baseUrlError: UiText?
        get() = if (baseUrl.isBlank() || baseUrl.trim().toHttpUrlOrNull() != null) null else UiText.Resource(R.string.provider_base_url_invalid)

    val canSave: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() && baseUrlError == null && transcriptionModel.isNotBlank()

    fun copyEndpoint(source: ProviderProfile) = (if (source.format.hasVoice) withFormat(source.format) else this).copy(
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
        speechModel = speechModel.trim().ifBlank { VoiceProvider.defaultSpeechModel(format) },
        speechVoice = speechVoice.trim().ifBlank { VoiceProvider.defaultSpeechVoice(format) },
        speechVoiceName = speechVoiceName.trim(),
        format = format,
    )
}

class VoiceProviderEditorViewModel(
    private val providerId: String?,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalogClient,
    private val voiceList: VoiceListClient,
) : ViewModel() {
    private val draft = FormDraft<VoiceProviderForm>(viewModelScope)
    val form: StateFlow<VoiceProviderForm?> = draft.value

    val isDirty: StateFlow<Boolean> = draft.isDirty

    private val _chatProviders = MutableStateFlow<List<ProviderProfile>>(emptyList())
    val chatProviders: StateFlow<List<ProviderProfile>> = _chatProviders.asStateFlow()

    private val modelLoader = CatalogLoader(viewModelScope, R.string.provider_models_fetch_failed) { catalog.listModelInfo(it, withDetails = true) }
    private val voiceLoader = CatalogLoader(viewModelScope, R.string.provider_voices_fetch_failed) { voiceList.list(it) }

    val models: StateFlow<Loaded<ModelInfo>> = modelLoader.state
    val voices: StateFlow<Loaded<SpeechVoice>> = voiceLoader.state

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
                    speechVoiceName = it.speechVoiceName,
                    format = it.format,
                )
            } ?: VoiceProviderForm(id = settings.newProviderId(), isNew = true))
        }
    }

    fun edit(transform: (VoiceProviderForm) -> VoiceProviderForm) = draft.edit(transform)

    fun loadCatalog(force: Boolean = false) {
        val current = draft.current?.takeIf { it.baseUrl.isNotBlank() && it.baseUrlError == null } ?: return
        val endpoint = current.toProvider().asEndpointProfile()
        modelLoader.load(endpoint, force)
        voiceLoader.load(endpoint, force)
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
