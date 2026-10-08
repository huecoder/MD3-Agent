package dev.huecoder.md3agent.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.TranscriptionClient
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface DictationState {
    data object Idle : DictationState

    data class Recording(val elapsedMs: Long, val levels: List<Float>) : DictationState

    data object Transcribing : DictationState

    data class Failed(val message: UiText) : DictationState
}

class DictationViewModel(
    private val recorder: VoiceRecorder,
    private val transcriber: TranscriptionClient,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    private val _transcripts = Channel<String>(Channel.BUFFERED)
    private var job: Job? = null

    val state: StateFlow<DictationState> = _state.asStateFlow()

    val transcripts: Flow<String> = _transcripts.receiveAsFlow()

    fun start() {
        if (_state.value is DictationState.Recording || _state.value is DictationState.Transcribing) return
        try {
            recorder.start()
        } catch (e: IOException) {
            _state.value = DictationState.Failed(UiText.Resource(R.string.dictation_error_microphone_unavailable))
            return
        }
        job = viewModelScope.launch { trackRecording(startedAt = clock()) }
    }

    fun finish() {
        if (_state.value !is DictationState.Recording) return
        job?.cancel()
        val audio = try {
            recorder.stop()
        } catch (e: RecordingTooShortException) {
            _state.value = DictationState.Failed(UiText.Resource(R.string.dictation_error_too_short))
            return
        } catch (e: IOException) {
            _state.value = DictationState.Failed(UiText.Resource(R.string.dictation_error_recording_failed))
            return
        }
        _state.value = DictationState.Transcribing
        job = viewModelScope.launch {
            try {
                val text = transcribe(audio)
                if (text.isNotEmpty()) _transcripts.send(text)
                _state.value = if (text.isEmpty()) DictationState.Failed(UiText.Resource(R.string.dictation_error_no_speech)) else DictationState.Idle
            } catch (e: NoSpeechProviderException) {
                _state.value = DictationState.Failed(UiText.Resource(R.string.dictation_error_no_provider))
            } catch (e: IOException) {
                _state.value = DictationState.Failed(
                    UiText.Resource(R.string.dictation_error_transcription_failed_reason, listOf(e.message ?: e.javaClass.simpleName)),
                )
            } catch (e: ProviderException) {
                _state.value = DictationState.Failed(e.message?.let(UiText::Raw) ?: UiText.Resource(R.string.dictation_error_transcription_failed))
            } finally {
                audio.delete()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        recorder.cancel()
        _state.value = DictationState.Idle
    }

    fun fail(message: UiText) {
        _state.value = DictationState.Failed(message)
    }

    fun dismissError() {
        if (_state.value is DictationState.Failed) _state.value = DictationState.Idle
    }

    private suspend fun trackRecording(startedAt: Long) {
        var levels = emptyList<Float>()
        while (true) {
            val elapsed = clock() - startedAt
            levels = (levels + recorder.level()).takeLast(WAVEFORM_BARS)
            _state.value = DictationState.Recording(elapsed, levels)
            if (elapsed >= MAX_RECORDING_MS) return finish()
            delay(LEVEL_POLL_MS)
        }
    }

    private suspend fun transcribe(audio: File): String {
        val voice = settings.current().voice
        val provider = voice.transcriptionProvider ?: throw NoSpeechProviderException()
        return transcriber.transcribe(provider.asEndpointProfile(), provider.transcriptionModel, voice.language, audio, VoiceRecorder.MIME_TYPE)
    }

    override fun onCleared() {
        recorder.cancel()
    }

    companion object {
        const val WAVEFORM_BARS = 32
        private const val LEVEL_POLL_MS = 80L
        private const val MAX_RECORDING_MS = 5 * 60 * 1_000L
    }
}

class NoSpeechProviderException : IllegalStateException("No speech recognition provider configured")
