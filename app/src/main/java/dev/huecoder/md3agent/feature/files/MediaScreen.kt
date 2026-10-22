package dev.huecoder.md3agent.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Select_all
import com.composables.icons.materialsymbols.rounded.Share
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.feature.chat.lightbox.LocalLightbox
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.files.shareFiles

@Composable
fun MediaScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(key = "files-media") { MediaViewModel(it.library) }
    val media by viewModel.media.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    MediaErrorToasts(viewModel)
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
        MediaDeleteDialog(
            count = selection.size,
            onConfirm = { viewModel.deleteSelected(); confirmingDelete = false },
            onDismiss = { confirmingDelete = false },
        )
    }
}
