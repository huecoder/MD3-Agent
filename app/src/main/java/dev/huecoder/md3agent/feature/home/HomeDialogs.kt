package dev.huecoder.md3agent.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check_box
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Drive_file_move
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Folder_off
import com.composables.icons.materialsymbols.rounded.Create_new_folder
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.db.FolderEntity

@Composable
fun ChatActionsSheet(
    title: String,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetAction(MaterialSymbols.Rounded.Edit, stringResource(R.string.home_rename), onRename)
            SheetAction(MaterialSymbols.Rounded.Drive_file_move, stringResource(R.string.home_move), onMove)
            SheetAction(MaterialSymbols.Rounded.Check_box, stringResource(R.string.home_select), onSelect)
            SheetAction(MaterialSymbols.Rounded.Delete, stringResource(R.string.home_delete_chat), onDelete, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun FolderActionsSheet(folder: FolderEntity, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetAction(MaterialSymbols.Rounded.Edit, stringResource(R.string.home_folder_rename), onRename)
            SheetAction(MaterialSymbols.Rounded.Delete, stringResource(R.string.home_folder_delete), onDelete, MaterialTheme.colorScheme.error)
            Text(
                stringResource(R.string.home_folder_delete_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(icon, contentDescription = null, tint = tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) { Text(label, color = tint.takeOrElse { MaterialTheme.colorScheme.onSurface }) }
}

@Composable
fun MoveToFolderSheet(
    folders: List<FolderEntity>,
    onMove: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.home_move), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetAction(MaterialSymbols.Rounded.Folder_off, stringResource(R.string.home_move_none), { onMove(null) })
            folders.forEach { folder -> SheetAction(MaterialSymbols.Rounded.Folder, folder.name, { onMove(folder.id) }) }
            SheetAction(MaterialSymbols.Rounded.Create_new_folder, stringResource(R.string.home_folder_new), onNewFolder, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val submit = { if (value.text.isNotBlank()) onConfirm(value.text.trim()) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = value.text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
    )
}

@Composable
fun DeleteChatsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.home_delete_chats_title, count, count)) },
        text = { Text(stringResource(R.string.home_delete_chat_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_delete_chat), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
    )
}
