package dev.huecoder.md3agent.assistant

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Photo_camera
import com.composables.icons.materialsymbols.rounded.Photo_library
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.FileLibrary
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.ui.components.ShapeBadge
import kotlin.math.roundToInt

internal enum class AttachPage { SOURCES, APP_FILES }

@Stable
internal class AttachMenuState {
    var isOpen by mutableStateOf(false)
        private set
    var page by mutableStateOf(AttachPage.SOURCES)
        private set
    val files = AppFilesState()

    fun open() {
        files.reset()
        page = AttachPage.SOURCES
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    fun openAppFiles() {
        page = AttachPage.APP_FILES
    }

    fun back(): Boolean {
        if (!isOpen) return false
        when {
            page == AttachPage.APP_FILES && files.goUp() -> Unit
            page == AttachPage.APP_FILES -> page = AttachPage.SOURCES
            else -> close()
        }
        return true
    }
}

@Composable
internal fun AttachMenuLayer(menu: AttachMenuState, library: FileLibrary, onSource: (AttachSource) -> Unit, onPickFiles: (List<ViewableFile>) -> Unit) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(Modifier.fillMaxSize()) {
        MenuScrim(visible = menu.isOpen, onDismiss = menu::close)
        AnimatedVisibility(
            visible = menu.isOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spatial) { it } + fadeIn(effects),
            exit = slideOutVertically(spatial) { it } + fadeOut(effects),
        ) {
            AttachPanel(menu, library, onSource, onPickFiles)
        }
    }
}

@Composable
private fun MenuScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
}

@Composable
private fun AttachPanel(menu: AttachMenuState, library: FileLibrary, onSource: (AttachSource) -> Unit, onPickFiles: (List<ViewableFile>) -> Unit) {
    val density = LocalDensity.current
    val dismissDistance = with(density) { DISMISS_DISTANCE.toPx() }
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val pageSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val pageFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var dragged by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(topStart = PANEL_CORNER, topEnd = PANEL_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragged.roundToInt()) }
            .draggable(
                state = rememberDraggableState { delta -> dragged = (dragged + delta).coerceAtLeast(0f) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragged > dismissDistance || velocity > DISMISS_VELOCITY) {
                        menu.close()
                    } else {
                        animate(dragged, 0f, animationSpec = settle) { value, _ -> dragged = value }
                    }
                },
            ),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally))
            AnimatedContent(
                targetState = menu.page,
                transitionSpec = { (fadeIn(pageFade) togetherWith fadeOut(pageFade)).using(SizeTransform(clip = true) { _, _ -> pageSize }) },
                label = "attach-page",
            ) { page ->
                when (page) {
                    AttachPage.SOURCES -> SourcesPage(onSource, onAppFiles = menu::openAppFiles)
                    AttachPage.APP_FILES -> AppFilesPage(menu.files, library, onBack = { menu.back() }, onAttach = onPickFiles)
                }
            }
        }
    }
}

@Composable
private fun SourcesPage(onSource: (AttachSource) -> Unit, onAppFiles: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.assistant_attach_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceTile(MaterialSymbols.Rounded.Photo_library, R.string.assistant_attach_photos, { onSource(AttachSource.PHOTOS) }, Modifier.weight(1f))
            SourceTile(MaterialSymbols.Rounded.Photo_camera, R.string.assistant_attach_camera, { onSource(AttachSource.CAMERA) }, Modifier.weight(1f))
            SourceTile(MaterialSymbols.Rounded.Attach_file, R.string.assistant_attach_files, { onSource(AttachSource.FILES) }, Modifier.weight(1f))
        }
        AppFilesEntry(onAppFiles)
    }
}

@Composable
private fun SourceTile(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShapeBadge(icon)
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun AppFilesEntry(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShapeBadge(MaterialSymbols.Rounded.Folder, containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.assistant_attach_app_files), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.assistant_attach_app_files_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Icon(MaterialSymbols.Rounded.Chevron_right, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

private const val SCRIM_ALPHA = 0.4f
private const val DISMISS_VELOCITY = 1_200f
private val DISMISS_DISTANCE = 96.dp
private val PANEL_CORNER = 28.dp
