package com.mwagent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mwagent.R
import com.mwagent.data.settings.ApiFormat
import com.mwagent.data.settings.HeaderEntry
import com.mwagent.data.settings.ProviderProfile
import com.mwagent.data.settings.SettingsRepository
import com.mwagent.data.models.ModelCatalog
import com.mwagent.ui.text.UiText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ProviderForm(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val format: ApiFormat = ApiFormat.OPENAI,
) {
    fun withFormat(next: ApiFormat) = copy(format = next, baseUrl = next.adoptBaseUrl(baseUrl))

    val baseUrlError: UiText?
        get() = if (baseUrl.isBlank() || baseUrl.trim().toHttpUrlOrNull() != null) null else UiText.Resource(R.string.provider_base_url_invalid)

    val canSave: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() && baseUrlError == null

    fun toProfile() = ProviderProfile(
        id = id,
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        extraHeaders = headers.filter { it.name.isNotBlank() },
        format = format,
    )
}

class ProviderEditorViewModel(
    private val providerId: String?,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
) : ViewModel() {
    private val draft = FormDraft<ProviderForm>(viewModelScope)
    val form: StateFlow<ProviderForm?> = draft.value

    val isDirty: StateFlow<Boolean> = draft.isDirty

    init {
        viewModelScope.launch {
            val existing = settings.current().provider(providerId)
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
                    format = existing.format,
                )
            })
        }
    }

    fun edit(transform: (ProviderForm) -> ProviderForm) = draft.edit(transform)

    fun save(onSaved: () -> Unit) {
        val current = draft.current?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            settings.upsertProvider(current.toProfile())
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
