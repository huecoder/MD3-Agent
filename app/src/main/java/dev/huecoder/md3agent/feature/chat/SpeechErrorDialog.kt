package dev.huecoder.md3agent.feature.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.copyText
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import kotlinx.coroutines.launch

@Composable
fun SpeechErrorDialog(message: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        modifier = Modifier.clickHaptics(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_speak_failed_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        dismissButton = { TextButton(onClick = { scope.launch { clipboard.copyText(message) } }) { Text(stringResource(R.string.chat_copy)) } },
    )
}
