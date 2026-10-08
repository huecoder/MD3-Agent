package dev.huecoder.md3agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import dev.huecoder.md3agent.R

@Immutable
data class SaveAction(
    val isDirty: Boolean,
    val canSave: Boolean,
    val onSave: () -> Unit,
) {
    val isEnabled: Boolean get() = isDirty && canSave
}

@Composable
fun StickySaveBar(action: SaveAction, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val glow by animateColorAsState(
        targetValue = if (action.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = GLOW_ALPHA) else Color.Transparent,
        label = "saveGlow",
    )
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface, surface)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, glow)))
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Button(
            onClick = action.onSave,
            enabled = action.isEnabled,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(MaterialSymbols.Rounded.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(
                stringResource(if (action.isDirty) R.string.settings_save else R.string.settings_saved),
                Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
    }
}

@Composable
fun UnsavedChangesDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = {
            Text(stringResource(if (canSave) R.string.unsaved_body else R.string.unsaved_body_invalid))
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.unsaved_discard)) }
                if (canSave) TextButton(onClick = onSave) { Text(stringResource(R.string.settings_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.unsaved_keep_editing)) } },
    )
}

private const val GLOW_ALPHA = 0.08f
