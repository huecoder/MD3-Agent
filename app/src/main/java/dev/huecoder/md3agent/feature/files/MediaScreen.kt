package dev.huecoder.md3agent.feature.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.feature.chat.lightbox.LocalLightbox
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun MediaScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(key = "files-media") { MediaViewModel(it.library) }
    val media by viewModel.media.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LightboxHost {
        SettingsScaffold(stringResource(R.string.files_media), onBack) { padding ->
            val lightbox = LocalLightbox.current
            val images = media.orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CELL_SIZE),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                itemsIndexed(images, key = { _, image -> image.file.path }) { index, image ->
                    AsyncImage(
                        model = image.file,
                        contentDescription = image.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { lightbox?.show(images, index) }
                            .animateItem(),
                    )
                }
            }
        }
    }
}

private val CELL_SIZE = 108.dp
private val GAP = 4.dp
