package dev.huecoder.md3agent.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.FileLibrary
import dev.huecoder.md3agent.data.files.InvalidFileNameException
import dev.huecoder.md3agent.data.files.LibraryEntry
import dev.huecoder.md3agent.data.files.LibraryLocation
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class MediaViewModel(private val library: FileLibrary) : ViewModel() {
    private val _media = MutableStateFlow<List<ViewableFile>?>(null)
    val media: StateFlow<List<ViewableFile>?> = _media.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _media.value = library.media() }
    }
}

data class FolderUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val selection: Set<String> = emptySet(),
    val isLoaded: Boolean = false,
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
    val selectedEntries: List<LibraryEntry> get() = entries.filter { it.name in selection }
}

class FolderViewModel(private val library: FileLibrary, val location: LibraryLocation) : ViewModel() {
    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()
    private val _errors = MutableStateFlow<UiText?>(null)
    val errors: StateFlow<UiText?> = _errors.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val entries = library.list(location)
            _state.update { current -> current.copy(entries = entries, selection = current.selection.intersect(entries.map { it.name }.toSet()), isLoaded = true) }
        }
    }

    fun toggle(entry: LibraryEntry) {
        if (entry.isReadOnly) return
        _state.update { it.copy(selection = if (entry.name in it.selection) it.selection - entry.name else it.selection + entry.name) }
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun upload(uris: List<Uri>) = change(R.string.files_error_upload) { uris.forEach { library.upload(it, location) } }

    fun createFolder(name: String) = change(R.string.files_error_folder) { library.createFolder(location, name) }

    fun rename(entry: LibraryEntry, name: String) = change(R.string.files_error_rename) { library.rename(location, entry, name) }

    fun deleteSelected() {
        val doomed = _state.value.selectedEntries
        clearSelection()
        change(R.string.files_error_delete) { library.delete(location, doomed) }
    }

    fun consumeError() {
        _errors.value = null
    }

    private fun change(failure: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: InvalidFileNameException) {
                _errors.value = UiText.Resource(R.string.files_error_name)
            } catch (e: IOException) {
                _errors.value = UiText.Resource(failure)
            }
            refresh()
        }
    }
}
