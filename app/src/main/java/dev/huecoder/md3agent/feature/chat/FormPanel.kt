package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Edit_note
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.FormField
import dev.huecoder.md3agent.conversation.FormFieldKind
import dev.huecoder.md3agent.conversation.FormRequest
import dev.huecoder.md3agent.conversation.PromptAnswer
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.OptionDropdown

@Composable
internal fun FormPanel(promptId: String, form: FormRequest, onAnswer: (PromptAnswer) -> Unit, modifier: Modifier) {
    var values by rememberSaveable(promptId) { mutableStateOf(form.fields.associate { it.key to it.default.orEmpty() }) }
    val isComplete = form.fields.all { field -> isValid(field, values[field.key].orEmpty()) }

    PromptCard(MaterialSymbols.Rounded.Edit_note, stringResource(R.string.form_header, form.source), form.message, modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = FIELDS_MAX_HEIGHT).verticalScroll(rememberScrollState()),
        ) {
            form.fields.forEach { field ->
                FieldInput(field, values[field.key].orEmpty()) { values = values + (field.key to it) }
            }
        }
        PromptActions(
            canSend = isComplete,
            onSkip = { onAnswer(PromptAnswer.Skipped) },
            onSend = { onAnswer(PromptAnswer.Submitted(values.filterValues { it.isNotEmpty() })) },
        )
    }
}

@Composable
private fun FieldInput(field: FormField, value: String, onChange: (String) -> Unit) {
    val label = if (field.required) field.title + REQUIRED_MARK else field.title
    when (val kind = field.kind) {
        FormFieldKind.Toggle -> LabeledSwitch(
            title = label,
            summary = field.description,
            checked = value.toBooleanStrictOrNull() ?: false,
            onCheckedChange = { onChange(it.toString()) },
        )
        is FormFieldKind.Choice -> OptionDropdown(
            label = label,
            options = kind.values.zip(kind.labels) { option, title -> DropdownOption(option, title) },
            selected = value.ifEmpty { null },
            onSelect = onChange,
            modifier = Modifier.fillMaxWidth(),
        )
        is FormFieldKind.Number, FormFieldKind.Text -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            supportingText = field.description?.let { { Text(it) } },
            isError = value.isNotEmpty() && !isValid(field, value),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = if (kind is FormFieldKind.Number) KeyboardType.Decimal else KeyboardType.Text),
            visualTransformation = if (field.looksSecret()) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun isValid(field: FormField, value: String): Boolean {
    if (value.isEmpty()) return !field.required
    val kind = field.kind as? FormFieldKind.Number ?: return true
    val number = (if (kind.integer) value.toLongOrNull()?.toDouble() else value.toDoubleOrNull()) ?: return false
    return (kind.minimum == null || number >= kind.minimum) && (kind.maximum == null || number <= kind.maximum)
}

private fun FormField.looksSecret(): Boolean = SECRET_HINTS.any { key.contains(it, ignoreCase = true) || title.contains(it, ignoreCase = true) }

private val SECRET_HINTS = listOf("key", "token", "secret", "password")
private val FIELDS_MAX_HEIGHT = 360.dp
private const val REQUIRED_MARK = " *"
