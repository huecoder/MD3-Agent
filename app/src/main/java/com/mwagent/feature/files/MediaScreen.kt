package com.mwagent.feature.files

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Select_all
import com.composables.icons.materialsymbols.rounded.Share
import com.mwagent.R
import com.mwagent.feature.chat.lightbox.LightboxHost
import com.mwagent.feature.chat.lightbox.LocalLightbox
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.files.shareFiles
import com.mwagent.ui.haptics.clickHaptics
import com.mwagent.ui.text.resolve

@Composable
fun MediaScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(key = "files-media") { MediaViewModel(it.library) }
    val media by viewModel.media.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }
    BackHandler(enabled = selecting, onBack = viewModel::clearSelection)

    LightboxHost {
        SettingsScaffold(
            title = if (selecting) pluralStringResource(R.plurals.home_selected, selection.size, selection.size) else stringResource(R.string.files_media),
            onBack = if (selecting) viewModel::clearSelection else onBack,
            actions = {
                if (selecting) {
                    BarAction(MaterialSymbols.Rounded.Select_all, R.string.home_select_all, viewModel::selectAll)
                    BarAction(MaterialSymbols.Rounded.Share, R.string.files_share) { context.shareFiles(viewModel.selectedFiles) }
                    BarAction(MaterialSymbols.Rounded.Delete, R.string.files_delete) { confirmingDelete = true }
                }
            },
        ) { padding ->
            val lightbox = LocalLightbox.current
            val images = media.orEmpty()
            MediaGrid(
                images = images,
                selection = selection,
                topPadding = padding.calculateTopPadding(),
                onOpen = { index -> lightbox?.show(images, index) },
                onToggle = viewModel::toggle,
                onSelectRange = viewModel::selectRange,
                onRangeEnd = viewModel::endRange,
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            modifier = Modifier.clickHaptics(),
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.files_delete_title, selection.size)) },
            text = { Text(stringResource(R.string.files_media_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); confirmingDelete = false }) {
                    Text(stringResource(R.string.files_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
        )
    }
}
