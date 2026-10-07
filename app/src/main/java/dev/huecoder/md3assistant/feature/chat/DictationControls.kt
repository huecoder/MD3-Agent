package dev.huecoder.md3assistant.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3assistant.voice.DictationState
import dev.huecoder.md3assistant.voice.DictationViewModel

class DictationControls(
    val state: DictationState,
    val onStart: () -> Unit,
    val onFinish: () -> Unit,
    val onCancel: () -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
fun rememberDictationControls(
    model: DictationViewModel,
    onTranscript: (String) -> Unit,
    onPermissionMissing: () -> Unit,
): DictationControls {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val deliver by rememberUpdatedState(onTranscript)
    LaunchedEffect(model) { model.transcripts.collect { deliver(it) } }
    return DictationControls(
        state = state,
        onStart = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) model.start() else onPermissionMissing()
        },
        onFinish = model::finish,
        onCancel = model::cancel,
        onDismissError = model::dismissError,
    )
}

fun appendDictated(current: String, dictated: String): String =
    if (current.isBlank()) dictated else current.trimEnd() + " " + dictated
