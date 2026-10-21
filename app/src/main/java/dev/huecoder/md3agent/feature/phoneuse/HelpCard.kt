package dev.huecoder.md3agent.feature.phoneuse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.conversation.PendingPrompt
import dev.huecoder.md3agent.conversation.PromptAnswer
import dev.huecoder.md3agent.conversation.UserQuestion
import dev.huecoder.md3agent.ui.haptics.clickHaptics

class HelpAsk(val pending: PendingPrompt, val question: UserQuestion) {
    val doneLabel: String get() = question.options.first().label
    val cannotLabel: String get() = question.options.last().label

    fun answer(label: String) = PromptAnswer.Chosen(listOf(label), null)

    companion object {
        fun of(pending: PendingPrompt?): HelpAsk? {
            val question = (pending?.prompt as? UserQuestion)?.takeIf { it.handoff && it.options.size >= 2 } ?: return null
            return HelpAsk(pending, question)
        }
    }
}

@Composable
fun HelpCard(ask: HelpAsk, onAnswer: (PromptAnswer) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth().clickHaptics(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ask.question.header?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            Text(ask.question.question, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { onAnswer(ask.answer(ask.cannotLabel)) }, shapes = ButtonDefaults.shapes()) { Text(ask.cannotLabel) }
                Button(onClick = { onAnswer(ask.answer(ask.doneLabel)) }, shapes = ButtonDefaults.shapes()) { Text(ask.doneLabel) }
            }
        }
    }
}
