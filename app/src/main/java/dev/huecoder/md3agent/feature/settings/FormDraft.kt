package dev.huecoder.md3agent.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class FormDraft<T : Any>(scope: CoroutineScope) {
    private val pristine = MutableStateFlow<T?>(null)
    private val draft = MutableStateFlow<T?>(null)

    val value: StateFlow<T?> = draft.asStateFlow()

    val isDirty: StateFlow<Boolean> = combine(draft, pristine) { current, saved -> current != saved }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val current: T? get() = draft.value

    fun load(form: T) {
        pristine.value = form
        draft.value = form
    }

    fun edit(transform: (T) -> T) = draft.update { it?.let(transform) }

    fun applyPersisted(transform: (T) -> T) {
        pristine.update { it?.let(transform) }
        draft.update { it?.let(transform) }
    }

    fun markSaved() {
        pristine.value = draft.value
    }
}
