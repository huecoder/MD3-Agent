package dev.huecoder.md3agent.feature.main

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.updates.UpdateStatus

@Composable
fun UpdatePromptDialog(offer: UpdateStatus.Available, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_prompt_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_prompt_body, offer.version))
                TextButton(onClick = { open(offer.pageUrl) }) { Text(stringResource(R.string.update_prompt_changelog)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { open(offer.apkUrl ?: offer.pageUrl); onDismiss() }) { Text(stringResource(R.string.update_prompt_install)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_prompt_later)) } },
    )
}
