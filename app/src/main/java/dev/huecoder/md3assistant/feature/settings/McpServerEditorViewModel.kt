package dev.huecoder.md3assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.settings.HeaderEntry
import dev.huecoder.md3assistant.data.settings.McpServer
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import dev.huecoder.md3assistant.mcp.McpToolbox
import dev.huecoder.md3assistant.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class McpServerForm(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val url: String = "",
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val enabled: Boolean = true,
) {
    val urlError: UiText?
        get() = if (url.isBlank() || url.trim().toHttpUrlOrNull() != null) null else UiText.Resource(R.string.mcp_url_invalid)

    val canSave: Boolean
        get() = name.isNotBlank() && url.isNotBlank() && urlError == null

    fun toServer() = McpServer(
        id = id,
        name = name.trim(),
        url = url.trim(),
        apiKey = apiKey.trim(),
        headers = headers.filter { it.name.isNotBlank() },
        enabled = enabled,
    )
}

sealed interface McpProbeState {
    data object Idle : McpProbeState

    data object Running : McpProbeState

    data class Found(val toolNames: List<String>) : McpProbeState

    data class Failed(val message: String) : McpProbeState
}

class McpServerEditorViewModel(
    private val serverId: String?,
    private val settings: SettingsRepository,
    private val toolbox: McpToolbox,
) : ViewModel() {
    private val _form = MutableStateFlow<McpServerForm?>(null)
    private val _probe = MutableStateFlow<McpProbeState>(McpProbeState.Idle)
    private var probeJob: Job? = null

    val form: StateFlow<McpServerForm?> = _form.asStateFlow()

    val probe: StateFlow<McpProbeState> = _probe.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = settings.current().tools.mcpServers.firstOrNull { it.id == serverId }
            _form.value = existing?.let {
                McpServerForm(it.id, isNew = false, name = it.name, url = it.url, apiKey = it.apiKey, headers = it.headers, enabled = it.enabled)
            } ?: McpServerForm(id = settings.newProviderId(), isNew = true)
        }
    }

    fun edit(transform: (McpServerForm) -> McpServerForm) {
        _form.update { it?.let(transform) }
        probeJob?.cancel()
        _probe.value = McpProbeState.Idle
    }

    fun test() {
        val server = _form.value?.takeIf { it.canSave }?.toServer() ?: return
        probeJob?.cancel()
        _probe.value = McpProbeState.Running
        probeJob = viewModelScope.launch {
            _probe.value = try {
                McpProbeState.Found(toolbox.probe(server).map { it.title ?: it.name })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                McpProbeState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val server = _form.value?.takeIf { it.canSave }?.toServer() ?: return
        viewModelScope.launch {
            settings.updateTools { tools ->
                val exists = tools.mcpServers.any { it.id == server.id }
                tools.copy(mcpServers = if (exists) tools.mcpServers.map { if (it.id == server.id) server else it } else tools.mcpServers + server)
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = serverId ?: return onDeleted()
        viewModelScope.launch {
            settings.updateTools { tools -> tools.copy(mcpServers = tools.mcpServers.filterNot { it.id == id }) }
            onDeleted()
        }
    }
}
