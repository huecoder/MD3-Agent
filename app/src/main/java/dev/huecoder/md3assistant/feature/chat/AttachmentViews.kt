package dev.huecoder.md3assistant.feature.chat

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
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Description
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.attachments.Attachment

@Composable
fun AttachmentStrip(attachments: List<Attachment>, modifier: Modifier = Modifier, onRemove: ((Attachment) -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        val context = LocalContext.current
        attachments.forEach { attachment ->
            Box {
                if (attachment.isImage) ImageThumb(attachment) else FilePill(attachment, onClick = { context.openAttachment(attachment) })
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
private fun ImageThumb(attachment: Attachment) {
    AttachmentImage(attachment, Modifier.size(THUMB_SIZE).clip(MaterialTheme.shapes.medium))
}

@Composable
fun AttachmentImage(attachment: Attachment, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val store = (context.applicationContext as App).container.attachments
    AsyncImage(
        model = store.file(attachment),
        contentDescription = attachment.name,
        contentScale = contentScale,
        modifier = modifier.clickable { context.openAttachment(attachment) },
    )
}

fun Context.openAttachment(attachment: Attachment) {
    val file = (applicationContext as App).container.attachments.file(attachment)
    val uri = FileProvider.getUriForFile(this, "$packageName$FILE_PROVIDER_SUFFIX", file, attachment.name)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.chat_attachment_no_viewer, attachment.name), Toast.LENGTH_SHORT).show()
    }
}

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

private const val FILE_PROVIDER_SUFFIX = ".files"
private val THUMB_SIZE = 72.dp
private val REMOVE_BUTTON_SIZE = 22.dp
private val MAX_FILE_NAME_WIDTH = 180.dp
