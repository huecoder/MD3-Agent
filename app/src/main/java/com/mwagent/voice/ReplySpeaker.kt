package com.mwagent.voice

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ReplySpeaker(private val scope: CoroutineScope, private val synthesizerFactory: suspend () -> SpeechSynthesizer) {
    private val _speakingId = MutableStateFlow<String?>(null)
    private val _failures = Channel<String>(Channel.BUFFERED)
    private var session: Session? = null

    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    val failures: Flow<String> = _failures.receiveAsFlow()

    fun follow(replyId: String, streamed: String) = sessionFor(replyId).push { it.ready(streamed) }

    fun finish(replyId: String, full: String) = sessionFor(replyId).apply { push { it.rest(full) } }.close()

    fun speak(replyId: String, full: String) {
        stop()
        finish(replyId, full)
    }

    fun stop() {
        session?.cancel()
        session = null
        _speakingId.value = null
    }

    private fun sessionFor(replyId: String): Session {
        session?.takeIf { it.replyId == replyId }?.let { return it }
        stop()
        return Session(replyId).also {
            session = it
            _speakingId.value = replyId
        }
    }

    private inner class Session(val replyId: String) {
        private val chunker = SpeechChunker()
        private val texts = Channel<String>(Channel.UNLIMITED)
        private val prepared = Channel<PreparedSpeech>(PREFETCH) { (it as? PreparedSpeech.Audio)?.file?.delete() }
        private var synthesizer: SpeechSynthesizer? = null
        private val job: Job = scope.launch {
            try {
                val engine = synthesizerFactory().also { synthesizer = it }
                coroutineScope {
                    launch {
                        try {
                            for (text in texts) prepared.send(engine.prepare(text))
                        } finally {
                            prepared.close()
                        }
                    }
                    for (speech in prepared) engine.play(speech)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Speaking a reply failed", e)
                _failures.trySend(e.message ?: e.javaClass.simpleName)
            } finally {
                end()
            }
        }

        fun push(chunks: (SpeechChunker) -> List<String>) = chunks(chunker).forEach { texts.trySend(it) }

        fun close() = texts.close()

        fun cancel() {
            job.cancel()
            prepared.cancel()
            synthesizer?.stop()
            synthesizer?.release()
        }

        private fun end() {
            synthesizer?.release()
            if (session === this) {
                session = null
                _speakingId.value = null
            }
        }
    }

    private companion object {
        const val TAG = "ReplySpeaker"
        const val PREFETCH = 1
    }
}
