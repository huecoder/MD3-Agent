package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class Loaded<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: UiText? = null,
    val key: String? = null,
)

class CatalogLoader<T>(
    private val scope: CoroutineScope,
    @StringRes private val failureMessage: Int,
    private val fetch: suspend (ProviderProfile) -> List<T>,
) {
    private val _state = MutableStateFlow(Loaded<T>())
    val state: StateFlow<Loaded<T>> = _state.asStateFlow()
    private var job: Job? = null

    fun load(provider: ProviderProfile, force: Boolean = false) {
        val key = fingerprint(provider)
        val current = _state.value
        if (!force && current.key == key && (current.loading || current.error == null)) return
        job?.cancel()
        _state.value = Loaded(items = current.items.takeIf { current.key == key }.orEmpty(), loading = true, key = key)
        job = scope.launch {
            _state.value = try {
                Loaded(items = fetch(provider), key = key)
            } catch (e: ProviderException) {
                Loaded(error = e.message?.let(UiText::Raw) ?: UiText.Resource(failureMessage), key = key)
            }
        }
    }

    private fun fingerprint(provider: ProviderProfile) =
        listOf(provider.format, provider.baseUrl.trim(), provider.apiKey.trim(), provider.extraHeaders).joinToString("|")
}
