package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private const val MAX_SUGGESTIONS = 50

@Composable
fun ModelPicker(selected: String, models: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(selected, models) {
        val query = selected.trim()
        val matching = if (query.isEmpty() || query in models) models else models.filter { it.contains(query, ignoreCase = true) }
        matching.take(MAX_SUGGESTIONS)
    }

    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {
                onSelect(it)
                expanded = true
            },
            label = { Text("ID модели") },
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            trailingIcon = { if (models.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
            suggestions.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                )
            }
        }
    }
}
