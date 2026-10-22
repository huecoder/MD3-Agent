package dev.huecoder.md3agent.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Select_all
import com.composables.icons.materialsymbols.rounded.Share
import com.composables.icons.materialsymbols.rounded.Smart_toy
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.LibraryRoot
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.feature.chat.lightbox.LocalLightbox
import dev.huecoder.md3agent.feature.main.MainNavBarClearance
import dev.huecoder.md3agent.feature.main.MainTab
import dev.huecoder.md3agent.feature.main.ScrollToTopOnReselect
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.groupedListColors
import dev.huecoder.md3agent.ui.files.shareFiles

@Composable
fun FilesScreen(onOpenFolder: (LibraryRoot) -> Unit, onOpenMedia: () -> Unit) {
    val viewModel = appViewModel(key = "files-media") { MediaViewModel(it.library) }
    val media by viewModel.media.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    val context = LocalContext.current
    var confirmingDelete by remember { mutableStateOf(false) }
    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    MediaErrorToasts(viewModel)
    BackHandler(enabled = selecting, onBack = viewModel::clearSelection)

    LightboxHost {
        Scaffold(
            modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = {
                        Text(if (selecting) pluralStringResource(R.plurals.home_selected, selection.size, selection.size) else stringResource(R.string.files_title))
                    },
                    navigationIcon = {
                        if (selecting) BarAction(MaterialSymbols.Rounded.Close, R.string.home_selection_close, viewModel::clearSelection)
                    },
                    actions = {
                        if (selecting) {
                            BarAction(MaterialSymbols.Rounded.Select_all, R.string.home_select_all, viewModel::selectAll)
                            BarAction(MaterialSymbols.Rounded.Share, R.string.files_share) { context.shareFiles(viewModel.selectedFiles) }
                            BarAction(MaterialSymbols.Rounded.Delete, R.string.files_delete) { confirmingDelete = true }
                        }
                    },
                    scrollBehavior = topBarScroll,
                )
            },
        ) { padding ->
            val listState = rememberLazyListState()
            ScrollToTopOnReselect(MainTab.FILES, listState)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = MainNavBarClearance),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "media-header") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(stringResource(R.string.files_media), Modifier.weight(1f), topPadding = 8.dp)
                        if (!media.isNullOrEmpty()) TextButton(onClick = onOpenMedia) { Text(stringResource(R.string.files_media_all)) }
                    }
                }
                item(key = "media-strip") { MediaStrip(media, selection, viewModel::toggle) }
                item(key = "folders-header") { SectionHeader(stringResource(R.string.files_folders)) }
                item(key = "mine") {
                    RootRow(MaterialSymbols.Rounded.Folder, R.string.files_root_mine, R.string.files_root_mine_summary, index = 0) { onOpenFolder(LibraryRoot.MINE) }
                }
                item(key = "agent") {
                    RootRow(MaterialSymbols.Rounded.Smart_toy, R.string.files_root_agent, R.string.files_root_agent_summary, index = 1) { onOpenFolder(LibraryRoot.AGENT) }
                }
            }
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

@Composable
private fun MediaStrip(media: List<ViewableFile>?, selection: Set<String>, onToggle: (ViewableFile) -> Unit) {
    val lightbox = LocalLightbox.current
    val recent = media.orEmpty().take(STRIP_SIZE)
    if (media != null && recent.isEmpty()) {
        Text(
            stringResource(R.string.files_media_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(recent, key = { _, image -> image.file.path }) { index, image ->
            MediaTile(
                image = image,
                selected = image.file.path in selection,
                selecting = selection.isNotEmpty(),
                onClick = { if (selection.isEmpty()) lightbox?.show(media.orEmpty(), index) else onToggle(image) },
                onLongClick = { onToggle(image) },
                modifier = Modifier.size(THUMB_SIZE),
            )
        }
    }
}

@Composable
private fun RootRow(icon: ImageVector, title: Int, summary: Int, index: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = ROOT_COUNT),
        colors = groupedListColors(),
        leadingContent = { ShapeBadge(icon) },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title), style = MaterialTheme.typography.titleMediumEmphasized) }
}

private const val STRIP_SIZE = 15
private const val ROOT_COUNT = 2
private val THUMB_SIZE = 96.dp
