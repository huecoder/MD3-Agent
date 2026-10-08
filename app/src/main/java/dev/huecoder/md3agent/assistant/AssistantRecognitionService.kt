package dev.huecoder.md3agent.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class AssistantRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }

    override fun onStopListening(listener: Callback?) = Unit

    override fun onCancel(listener: Callback?) = Unit
}
