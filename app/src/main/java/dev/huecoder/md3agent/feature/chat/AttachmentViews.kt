package dev.huecoder.md3agent.feature.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Description
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.files.openFile
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.feature.chat.lightbox.LocalLightbox

@Composable
fun AttachmentStrip(attachments: List<Attachment>, modifier: Modifier = Modifier, onRemove: ((Attachment) -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        val context = LocalContext.current
        val images = attachments.filter { it.isImage }
        attachments.forEach { attachment ->
            Box {
                if (attachment.isImage) ImageThumb(attachment, images) else FilePill(attachment, onClick = { context.openAttachment(attachment) })
                onRemove?.let { remove ->
                    FilledTonalIconButton(
                        onClick = { remove(attachment) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(REMOVE_BUTTON_SIZE),
                    ) {
                        Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.chat_attachment_remove, attachment.name), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageThumb(attachment: Attachment, gallery: List<Attachment>) {
    AttachmentImage(attachment, Modifier.size(THUMB_SIZE).clip(MaterialTheme.shapes.medium), gallery = gallery)
}

@Composable
fun AttachmentImage(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    gallery: List<Attachment> = listOf(attachment),
) {
    val context = LocalContext.current
    val store = (context.applicationContext as App).container.attachments
    val lightbox = LocalLightbox.current
    AsyncImage(
        model = store.file(attachment),
        contentDescription = attachment.name,
        contentScale = contentScale,
        modifier = modifier.clickable {
            if (lightbox != null) lightbox.show(gallery.map(store::viewable), gallery.indexOf(attachment)) else context.openAttachment(attachment)
        },
    )
}

fun Context.openAttachment(attachment: Attachment) = openFile((applicationContext as App).container.attachments.viewable(attachment))

@Composable
fun FilePill(attachment: Attachment, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(MaterialSymbols.Rounded.Description, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.smallIconSize))
            Text(
                attachment.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MAX_FILE_NAME_WIDTH),
            )
        }
    }
}

private val THUMB_SIZE = 72.dp
private val REMOVE_BUTTON_SIZE = 22.dp
private val MAX_FILE_NAME_WIDTH = 180.dp
