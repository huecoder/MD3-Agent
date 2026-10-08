package dev.huecoder.md3assistant.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.huecoder.md3assistant.data.attachments.Attachment

@Composable
fun MediaGallery(attachments: List<Attachment>, modifier: Modifier = Modifier) {
    val (images, files) = attachments.partition { it.isImage }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GAP)) {
        when {
            images.size == 1 -> SingleImage(images.single())
            images.size in 2..MAX_ALBUM_SIZE -> Album(images)
            images.size > MAX_ALBUM_SIZE -> Slider(images)
        }
        if (files.isNotEmpty()) {
            val context = LocalContext.current
            files.forEach { file -> FilePill(file, onClick = { context.openAttachment(file) }) }
        }
    }
}

@Composable
private fun SingleImage(image: Attachment) {
    AttachmentImage(
        image,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .widthIn(max = MEDIA_MAX_WIDTH)
            .fillMaxWidth()
            .heightIn(max = SINGLE_MAX_HEIGHT)
            .clip(MaterialTheme.shapes.extraLarge),
    )
}

@Composable
private fun Album(images: List<Attachment>) {
    Column(Modifier.widthIn(max = MEDIA_MAX_WIDTH), verticalArrangement = Arrangement.spacedBy(GAP)) {
        images.chunked(ALBUM_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { image ->
                    AttachmentImage(
                        image,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(if (row.size == 1) WIDE_TILE_RATIO else SQUARE_TILE_RATIO)
                            .clip(MaterialTheme.shapes.large),
                    )
                }
            }
        }
    }
}

@Composable
private fun Slider(images: List<Attachment>) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { images.size },
        preferredItemWidth = SLIDER_ITEM_WIDTH,
        itemSpacing = GAP,
        modifier = Modifier.fillMaxWidth().height(SLIDER_HEIGHT),
    ) { index ->
        AttachmentImage(images[index], modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge))
    }
}

private const val MAX_ALBUM_SIZE = 4
private const val ALBUM_COLUMNS = 2
private const val SQUARE_TILE_RATIO = 1f
private const val WIDE_TILE_RATIO = 2f
private val GAP = 4.dp
private val MEDIA_MAX_WIDTH = 360.dp
private val SINGLE_MAX_HEIGHT = 420.dp
private val SLIDER_ITEM_WIDTH = 200.dp
private val SLIDER_HEIGHT = 240.dp
