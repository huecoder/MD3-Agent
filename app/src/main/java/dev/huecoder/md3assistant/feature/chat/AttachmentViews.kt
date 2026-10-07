package dev.huecoder.md3assistant.feature.chat

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
        attachments.forEach { attachment ->
            Box {
                if (attachment.isImage) ImageThumb(attachment) else FilePill(attachment)
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
    val store = (LocalContext.current.applicationContext as App).container.attachments
    AsyncImage(
        model = store.file(attachment),
        contentDescription = attachment.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(THUMB_SIZE).clip(MaterialTheme.shapes.medium),
    )
}

@Composable
private fun FilePill(attachment: Attachment) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
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
