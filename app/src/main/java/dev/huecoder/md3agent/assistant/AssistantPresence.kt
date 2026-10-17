package dev.huecoder.md3agent.assistant

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class AssistantPresence {
    private val showing = MutableStateFlow(false)
    private var close: (() -> Unit)? = null
    private var resume: (() -> Unit)? = null

    val isShowing: StateFlow<Boolean> = showing.asStateFlow()
    val attachments = MutableSharedFlow<List<Uri>>(extraBufferCapacity = ATTACHMENT_BUFFER)

    fun onShown(close: () -> Unit, resume: () -> Unit) {
        this.close = close
        this.resume = resume
        showing.value = true
    }

    fun onHidden() {
        close = null
        resume = null
        showing.value = false
    }

    suspend fun dismiss() {
        val closing = close ?: return
        closing()
        withTimeoutOrNull(DISMISS_TIMEOUT_MS) { showing.first { !it } }
    }

    fun deliver(uris: List<Uri>) {
        if (uris.isNotEmpty()) attachments.tryEmit(uris)
    }

    fun pickerClosed() {
        resume?.invoke()
    }

    private companion object {
        const val ATTACHMENT_BUFFER = 8
        const val DISMISS_TIMEOUT_MS = 1_500L
    }
}
