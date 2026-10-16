package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    AlbumGrid(images.size, Modifier.widthIn(max = MEDIA_MAX_WIDTH)) { index, tile ->
        AttachmentImage(images[index], gallery = images, modifier = tile)
    }
}

@Composable
private fun Slider(images: List<Attachment>) {
    SliderRow(images.size) { index, item ->
        AttachmentImage(images[index], gallery = images, modifier = item)
    }
}

private val MEDIA_MAX_WIDTH = 360.dp
private val SINGLE_MAX_HEIGHT = 420.dp
