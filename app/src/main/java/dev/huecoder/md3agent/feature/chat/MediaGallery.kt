package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.ui.pictures.GroupLayout

@Composable
fun MediaGallery(attachments: List<Attachment>, modifier: Modifier = Modifier) {
    val (images, files) = attachments.partition { it.isImage }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GALLERY_GAP)) {
        when {
            images.size == 1 -> SingleImage(images.single())
            images.size in 2..GroupLayout.MAX_ALBUM_SIZE -> Album(images)
            images.size > GroupLayout.MAX_ALBUM_SIZE -> Slider(images)
        }
        if (files.isNotEmpty()) {
            val context = LocalContext.current
            files.forEach { file -> FilePill(file, onClick = { context.openAttachment(file) }) }
        }
    }
}

@Composable
private fun SingleImage(image: Attachment) {
    Box(
        Modifier
            .widthIn(max = MEDIA_MAX_WIDTH)
            .fillMaxWidth()
            .heightIn(max = SINGLE_MAX_HEIGHT)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        AttachmentImage(image, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        image.caption?.let { CaptionOverlay(it, Modifier.align(Alignment.BottomStart)) }
    }
}

@Composable
private fun Album(images: List<Attachment>) {
    AlbumGrid(images.size, Modifier.widthIn(max = MEDIA_MAX_WIDTH)) { index, tile ->
        CaptionedTile(images[index], images, tile)
    }
}

@Composable
private fun Slider(images: List<Attachment>) {
    SliderRow(images.size) { index, item, visibility ->
        CaptionedTile(images[index], images, item, visibility)
    }
}

@Composable
private fun CaptionedTile(image: Attachment, gallery: List<Attachment>, modifier: Modifier, captionVisibility: () -> Float = { 1f }) {
    Box(modifier) {
        AttachmentImage(image, Modifier.fillMaxSize(), gallery = gallery)
        image.caption?.let { CaptionOverlay(it, Modifier.align(Alignment.BottomStart), captionVisibility) }
    }
}

private val MEDIA_MAX_WIDTH = 360.dp
private val SINGLE_MAX_HEIGHT = 420.dp
