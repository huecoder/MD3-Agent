package dev.huecoder.md3assistant.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Quiz
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.conversation.PendingQuestion
import dev.huecoder.md3assistant.conversation.QuestionAnswer
import dev.huecoder.md3assistant.conversation.QuestionOption

@Composable
fun QuestionPanel(pending: PendingQuestion, onAnswer: (QuestionAnswer) -> Unit, modifier: Modifier = Modifier) {
    val question = pending.question
    var chosen by rememberSaveable(pending.id) { mutableStateOf(emptyList<String>()) }
    var custom by rememberSaveable(pending.id) { mutableStateOf("") }
    val canSend = chosen.isNotEmpty() || custom.isNotBlank()

    fun toggle(option: QuestionOption) {
        chosen = when {
            question.multiSelect && option.label in chosen -> chosen - option.label
            question.multiSelect -> chosen + option.label
            else -> listOf(option.label)
        }
        if (!question.multiSelect) custom = ""
    }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MaterialSymbols.Rounded.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    question.header ?: stringResource(R.string.question_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(question.question, style = MaterialTheme.typography.titleMediumEmphasized)
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.heightIn(max = OPTIONS_MAX_HEIGHT).verticalScroll(rememberScrollState()),
            ) {
                question.options.forEachIndexed { index, option ->
                    val isChosen = option.label in chosen
                    SegmentedListItem(
                        onClick = { toggle(option) },
                        selected = isChosen,
                        shapes = ListItemDefaults.segmentedShapes(index, question.options.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        leadingContent = {
                            if (question.multiSelect) Checkbox(checked = isChosen, onCheckedChange = null) else RadioButton(selected = isChosen, onClick = null)
                        },
                        supportingContent = option.description?.let { { Text(it) } },
                    ) { Text(option.label, style = MaterialTheme.typography.titleMedium) }
                }
            }
            if (question.allowCustom) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        if (!question.multiSelect && it.isNotBlank()) chosen = emptyList()
                    },
                    placeholder = { Text(stringResource(R.string.question_custom)) },
                    shape = MaterialTheme.shapes.large,
                    maxLines = CUSTOM_MAX_LINES,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onAnswer(QuestionAnswer.Skipped) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.question_skip))
                }
                Button(
                    onClick = { onAnswer(QuestionAnswer.Answered(chosen, custom.trim().ifEmpty { null })) },
                    enabled = canSend,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.question_send)) }
            }
        }
    }
}

private val OPTIONS_MAX_HEIGHT = 320.dp
private const val CUSTOM_MAX_LINES = 4
