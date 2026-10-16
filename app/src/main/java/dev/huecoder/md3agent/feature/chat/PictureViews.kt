package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Broken_image
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.RemoteImages
import dev.huecoder.md3agent.feature.chat.lightbox.LocalLightbox
import dev.huecoder.md3agent.ui.pictures.GroupLayout
import dev.huecoder.md3agent.ui.pictures.MarkdownBlock
import dev.huecoder.md3agent.ui.pictures.Picture
import dev.huecoder.md3agent.ui.pictures.PictureAlign
import dev.huecoder.md3agent.ui.pictures.PictureSizing

@Composable
fun PictureGroupView(block: MarkdownBlock.Pictures, modifier: Modifier = Modifier) {
    val options = block.options
    val open = rememberPictureOpener(block.pictures)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val alignment = options.align.toAlignment()
        when (GroupLayout.of(block.pictures.size, options.grouping)) {
            GroupLayout.SINGLE -> SinglePicture(block.pictures.single(), options.widthShare, maxWidth, open, Modifier.align(alignment))
            GroupLayout.ALBUM -> AlbumGrid(
                block.pictures.size,
                Modifier.align(alignment).width(PictureSizing.widthCap(maxWidth.value, options.widthShare, null).dp),
            ) { index, tile -> PictureTile(block.pictures[index], tile.openOnTap(open, index)) }
            GroupLayout.SLIDER -> SliderRow(
                block.pictures.size,
                Modifier.align(alignment).width((maxWidth.value * (options.widthShare ?: 1f)).dp),
            ) { index, item -> PictureTile(block.pictures[index], item.openOnTap(open, index)) }
        }
    }
}

@Composable
fun SinglePictureView(picture: Picture, modifier: Modifier = Modifier) {
    val open = rememberPictureOpener(listOf(picture))
    BoxWithConstraints(modifier.fillMaxWidth()) {
        SinglePicture(picture, picture.options.widthShare, maxWidth, open, Modifier.align(picture.options.align.toAlignment()))
    }
}

@Composable
private fun SinglePicture(picture: Picture, share: Float?, available: Dp, open: ((Int) -> Unit)?, modifier: Modifier) {
    val density = LocalDensity.current
    var loaded by remember(picture.url) { mutableStateOf<IntSize?>(null) }
    var failed by remember(picture.url) { mutableStateOf(false) }
    if (failed) {
        UnavailablePicture(picture, modifier.widthIn(max = PictureSizing.widthCap(available.value, share, null).dp).fillMaxWidth().height(UNAVAILABLE_HEIGHT))
        return
    }
    val naturalDp = loaded?.let { with(density) { it.width.toDp().value } }
    val ratio = loaded?.let { it.width.toFloat() / it.height } ?: PLACEHOLDER_RATIO
    Box(
        modifier
            .widthIn(max = PictureSizing.widthCap(available.value, share, naturalDp).dp)
            .heightIn(max = PictureSizing.MAX_HEIGHT_DP.dp)
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .openOnTap(open, 0),
    ) {
        PictureImage(picture, Modifier.fillMaxSize(), ContentScale.Fit) { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> loaded = IntSize(state.result.image.width, state.result.image.height)
                is AsyncImagePainter.State.Error -> failed = true
                else -> Unit
            }
        }
    }
}

@Composable
private fun PictureTile(picture: Picture, modifier: Modifier) {
    var failed by remember(picture.url) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (failed) {
            UnavailablePicture(picture, Modifier.fillMaxSize())
        } else {
            PictureImage(picture, Modifier.fillMaxSize(), ContentScale.Crop) { state -> if (state is AsyncImagePainter.State.Error) failed = true }
        }
    }
}

@Composable
private fun PictureImage(picture: Picture, modifier: Modifier, contentScale: ContentScale, onState: (AsyncImagePainter.State) -> Unit) {
    val context = LocalContext.current
    val request = remember(picture.url) {
        ImageRequest.Builder(context).data(picture.url).size(REQUEST_SIDE_PX).crossfade(true).build()
    }
    AsyncImage(
        model = request,
        contentDescription = picture.description.ifBlank { null },
        contentScale = contentScale,
        onState = onState,
        modifier = modifier,
    )
}

@Composable
private fun UnavailablePicture(picture: Picture, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(8.dp),
    ) {
        Icon(MaterialSymbols.Rounded.Broken_image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            picture.description.ifBlank { stringResource(R.string.picture_unavailable) },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberPictureOpener(pictures: List<Picture>): ((Int) -> Unit)? {
    val lightbox = LocalLightbox.current ?: return null
    val directory = RemoteImages.directory(LocalContext.current)
    return remember(pictures, lightbox, directory) {
        { index -> lightbox.show(pictures.map { RemoteImages.viewable(directory, it.url, it.description) }, index) }
    }
}

private fun Modifier.openOnTap(open: ((Int) -> Unit)?, index: Int): Modifier =
    if (open == null) this else clickable { open(index) }

private fun PictureAlign?.toAlignment(): Alignment = when (this) {
    PictureAlign.CENTER -> Alignment.TopCenter
    PictureAlign.END -> Alignment.TopEnd
    else -> Alignment.TopStart
}

private const val PLACEHOLDER_RATIO = 4f / 3f
private const val REQUEST_SIDE_PX = 1280
private val UNAVAILABLE_HEIGHT = 96.dp
