package dev.huecoder.md3agent.feature.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Photo_camera
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.AttachSource

@Composable
fun ColumnScope.CompactAttachItems(onSource: (AttachSource) -> Unit, onAppFiles: () -> Unit) {
    AttachItem(MaterialSymbols.Rounded.Photo_camera, R.string.chat_attach_take_photo) { onSource(AttachSource.CAMERA) }
    AttachItem(MaterialSymbols.Rounded.Attach_file, R.string.chat_attach_from_files) { onSource(AttachSource.FILES) }
    AttachItem(MaterialSymbols.Rounded.Folder, R.string.chat_attach_from_app_files, onAppFiles)
}

@Composable
private fun AttachItem(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
