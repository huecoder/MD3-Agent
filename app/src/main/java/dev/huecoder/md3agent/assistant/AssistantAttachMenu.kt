package dev.huecoder.md3agent.assistant

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Photo_camera
import com.composables.icons.materialsymbols.rounded.Photo_library
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.ViewableFile
import java.io.File

internal enum class AttachMenu { CLOSED, SOURCES, APP_FILES }

@Composable
internal fun AttachMenuLayer(
    menu: AttachMenu,
    onDismiss: () -> Unit,
    onSource: (AttachSource) -> Unit,
    onAppFiles: () -> Unit,
    onPickedFromApp: (List<Uri>) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        MenuScrim(visible = menu != AttachMenu.CLOSED, onDismiss = onDismiss)
        MenuCard(visible = menu == AttachMenu.SOURCES, modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceTile(MaterialSymbols.Rounded.Photo_library, R.string.assistant_attach_photos, { onSource(AttachSource.PHOTOS) }, Modifier.weight(1f))
                SourceTile(MaterialSymbols.Rounded.Photo_camera, R.string.assistant_attach_camera, { onSource(AttachSource.CAMERA) }, Modifier.weight(1f))
                SourceTile(MaterialSymbols.Rounded.Attach_file, R.string.assistant_attach_files, { onSource(AttachSource.FILES) }, Modifier.weight(1f))
                SourceTile(MaterialSymbols.Rounded.Folder, R.string.assistant_attach_app_files, onAppFiles, Modifier.weight(1f))
            }
        }
        MenuCard(visible = menu == AttachMenu.APP_FILES, modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(APP_FILES_HEIGHT)) {
            AppFilesPicker(onPicked = onPickedFromApp)
        }
    }
}

@Composable
private fun MenuScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(SCRIM_MS)), exit = fadeOut(tween(SCRIM_MS))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = MENU_SCRIM_ALPHA))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
}

@Composable
private fun MenuCard(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(CARD_MS)) { it } + fadeIn(tween(CARD_MS)),
        exit = slideOutVertically(tween(CARD_MS)) { it } + fadeOut(tween(CARD_MS)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = CARD_CORNER, topEnd = CARD_CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            content()
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, label: Int, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AppFilesPicker(onPicked: (List<Uri>) -> Unit) {
    val library = (LocalContext.current.applicationContext as App).container.library
    val images by produceState<List<ViewableFile>?>(null) { value = library.media() }
    val chosen = remember { mutableStateListOf<File>() }
    Column(Modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.assistant_app_files_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
        val loaded = images
        when {
            loaded == null -> Unit
            loaded.isEmpty() -> Text(stringResource(R.string.assistant_app_files_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(GRID_CELL),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                verticalArrangement = Arrangement.spacedBy(GRID_GAP),
                modifier = Modifier.weight(1f),
            ) {
                items(loaded, key = { it.file.path }) { image ->
                    val selected = image.file in chosen
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { if (selected) chosen.remove(image.file) else chosen.add(image.file) },
                    ) {
                        AsyncImage(model = image.file, contentDescription = image.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (selected) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TINT)), contentAlignment = Alignment.Center) {
                                Icon(MaterialSymbols.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { onPicked(chosen.map(Uri::fromFile)) },
            enabled = chosen.isNotEmpty(),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(stringResource(R.string.assistant_app_files_attach, chosen.size))
        }
    }
}

private const val SCRIM_MS = 180
private const val CARD_MS = 260
private const val MENU_SCRIM_ALPHA = 0.4f
private const val SELECTED_TINT = 0.55f
private const val APP_FILES_HEIGHT = 0.7f
private val CARD_CORNER = 28.dp
private val GRID_CELL = 96.dp
private val GRID_GAP = 6.dp
