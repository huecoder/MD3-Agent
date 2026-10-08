package dev.huecoder.md3agent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.data.db.MemoryEntity
import dev.huecoder.md3agent.data.db.MemoryTier
import dev.huecoder.md3agent.data.memory.MemoryStore
import dev.huecoder.md3agent.data.settings.EmbeddingKind
import dev.huecoder.md3agent.data.settings.MemorySettings
import dev.huecoder.md3agent.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoryLists(
    val instant: List<MemoryEntity> = emptyList(),
    val longTerm: List<MemoryEntity> = emptyList(),
)

data class MemoryDraft(
    val id: String? = null,
    val tier: MemoryTier = MemoryTier.LONG_TERM,
    val title: String = "",
    val content: String = "",
) {
    val canSubmit: Boolean get() = title.isNotBlank() && content.isNotBlank()
}

sealed interface EmbeddingProbe {
    data object Idle : EmbeddingProbe
    data object Running : EmbeddingProbe
    data class Ready(val dimensions: Int) : EmbeddingProbe
    data class Failed(val message: String) : EmbeddingProbe
}

class MemoryViewModel(
    private val settings: SettingsRepository,
    private val memories: MemoryStore,
) : ViewModel() {
    private val draft = FormDraft<MemorySettings>(viewModelScope)
    private val _probe = MutableStateFlow<EmbeddingProbe>(EmbeddingProbe.Idle)
    private var probeJob: Job? = null

    val embedding: StateFlow<MemorySettings?> = draft.value
    val isDirty: StateFlow<Boolean> = draft.isDirty
    val probe: StateFlow<EmbeddingProbe> = _probe.asStateFlow()

    val lists: StateFlow<MemoryLists> = memories.all
        .map { all ->
            MemoryLists(
                instant = all.filter { it.tier == MemoryTier.INSTANT }.sortedBy { it.createdAt },
                longTerm = all.filter { it.tier == MemoryTier.LONG_TERM },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MemoryLists())

    init {
        viewModelScope.launch { draft.load(settings.current().tools.memory) }
    }

    fun edit(transform: (MemorySettings) -> MemorySettings) {
        draft.edit(transform)
        probeJob?.cancel()
        _probe.value = EmbeddingProbe.Idle
    }

    fun selectKind(kind: EmbeddingKind) = edit { it.copy(embeddingKind = kind) }

    fun probeEmbeddings() {
        val current = draft.current?.trimmed()?.takeIf { it.embeddingsReady } ?: return
        probeJob?.cancel()
        _probe.value = EmbeddingProbe.Running
        probeJob = viewModelScope.launch {
            _probe.value = try {
                EmbeddingProbe.Ready(memories.probeEmbeddings(current))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EmbeddingProbe.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = draft.current?.trimmed() ?: return
        viewModelScope.launch {
            settings.updateTools { it.copy(memory = current) }
            draft.markSaved()
            onSaved()
        }
    }

    fun submit(memory: MemoryDraft, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = runCatching {
                if (memory.id == null) {
                    memories.save(memory.tier, memory.title, memory.content)
                } else {
                    memories.update(memory.id, memory.title, memory.content, memory.tier)
                }
            }.exceptionOrNull()
            if (error is CancellationException) throw error
            onResult(error?.let { it.message ?: it.javaClass.simpleName })
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { memories.delete(id) }
    }

    private fun MemorySettings.trimmed() = copy(
        embeddingBaseUrl = embeddingBaseUrl.trim(),
        embeddingApiKey = embeddingApiKey.trim(),
        embeddingModel = embeddingModel.trim(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
