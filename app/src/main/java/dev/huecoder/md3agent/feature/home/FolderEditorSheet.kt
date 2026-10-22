package dev.huecoder.md3agent.feature.home

import dev.huecoder.md3agent.ui.components.AppBottomSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.FolderLook
import dev.huecoder.md3agent.data.chat.look
import dev.huecoder.md3agent.data.db.FolderEntity
import dev.huecoder.md3agent.ui.components.FolderBadge
import dev.huecoder.md3agent.ui.components.GlyphGrid
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.TintGrid
import dev.huecoder.md3agent.ui.components.icon
import dev.huecoder.md3agent.ui.haptics.clickHaptics

@Composable
fun FolderEditorSheet(folder: FolderEntity?, onSave: (name: String, look: FolderLook) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(folder?.name.orEmpty()) }
    var look by remember { mutableStateOf(folder?.look ?: FolderLook.Default) }
    val save = { if (name.isNotBlank()) onSave(name.trim(), look) }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.clickHaptics(),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            Text(
                stringResource(if (folder == null) R.string.home_folder_new else R.string.home_folder_edit),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { FolderBadge(look, size = PREVIEW_SIZE) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.home_folder_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(stringResource(R.string.chat_look_icon))
            GlyphGrid(look.glyph) { look = look.copy(glyph = it) }
            SectionHeader(stringResource(R.string.chat_look_color))
            TintGrid(look.tint, look.icon) { look = look.copy(tint = it) }
            Button(
                onClick = save,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(vertical = 16.dp),
            ) { Text(stringResource(if (folder == null) R.string.home_folder_create else R.string.settings_save)) }
        }
    }
}

private val PREVIEW_SIZE = 72.dp
