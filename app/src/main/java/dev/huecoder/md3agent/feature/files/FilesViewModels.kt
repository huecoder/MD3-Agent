package dev.huecoder.md3agent.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.EntryKind
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
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()
    private val _errors = MutableStateFlow<UiText?>(null)
    val errors: StateFlow<UiText?> = _errors.asStateFlow()
    private val range = RangeSelect()

    val selectedFiles: List<ViewableFile> get() = _media.value.orEmpty().filter { it.file.path in _selection.value }

    fun refresh() {
        viewModelScope.launch {
            val images = library.media()
            _media.value = images
            _selection.update { selected -> selected.intersect(images.map { it.file.path }.toSet()) }
        }
    }

    fun toggle(image: ViewableFile) {
        val path = image.file.path
        _selection.update { if (path in it) it - path else it + path }
    }

    fun selectAll() {
        _selection.value = _media.value.orEmpty().map { it.file.path }.toSet()
    }

    fun selectRange(anchor: String, current: String) {
        val ordered = _media.value.orEmpty().map { it.file.path }
        _selection.update { range.extend(it, ordered, anchor, current) }
    }

    fun endRange() = range.end()

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun deleteSelected() {
        val doomed = selectedFiles
        clearSelection()
        viewModelScope.launch {
            try {
                library.deleteMedia(doomed)
            } catch (e: IOException) {
                _errors.value = UiText.Resource(R.string.files_error_delete)
            }
            refresh()
        }
    }

    fun consumeError() {
        _errors.value = null
    }
}

data class FolderUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val selection: Set<String> = emptySet(),
    val isLoaded: Boolean = false,
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
    val selectedEntries: List<LibraryEntry> get() = entries.filter { it.key in selection }
    val selectableKeys: List<String> get() = entries.filter { it.kind != EntryKind.CHAT_FILES }.map { it.key }
    val canDelete: Boolean get() = isSelecting && selectedEntries.none { it.isReadOnly }
    val canRename: Boolean get() = selectedEntries.singleOrNull()?.isReadOnly == false
    val canShare: Boolean get() = isSelecting && selectedEntries.all { it.kind == EntryKind.FILE && it.file != null }
    val sharedFiles: List<ViewableFile> get() = selectedEntries.mapNotNull { it.file }
}

class FolderViewModel(private val library: FileLibrary, val location: LibraryLocation) : ViewModel() {
    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()
    private val _errors = MutableStateFlow<UiText?>(null)
    val errors: StateFlow<UiText?> = _errors.asStateFlow()
    private val range = RangeSelect()

    fun refresh() {
        viewModelScope.launch {
            val entries = library.list(location)
            _state.update { current -> current.copy(entries = entries, selection = current.selection.intersect(entries.map { it.key }.toSet()), isLoaded = true) }
        }
    }

    fun toggle(entry: LibraryEntry) {
        if (entry.kind == EntryKind.CHAT_FILES) return
        _state.update { it.copy(selection = if (entry.key in it.selection) it.selection - entry.key else it.selection + entry.key) }
    }

    fun selectAll() = _state.update { it.copy(selection = it.selectableKeys.toSet()) }

    fun selectRange(anchor: String, current: String) =
        _state.update { it.copy(selection = range.extend(it.selection, it.selectableKeys, anchor, current)) }

    fun endRange() = range.end()

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
