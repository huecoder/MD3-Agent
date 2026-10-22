package dev.huecoder.md3agent.feature.files

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import dev.huecoder.md3agent.ui.text.resolve

@Composable
internal fun MediaErrorToasts(viewModel: MediaViewModel) {
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
}

@Composable
internal fun MediaDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.clickHaptics(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_delete_title, count)) },
        text = { Text(stringResource(R.string.files_media_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.files_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
    )
}
