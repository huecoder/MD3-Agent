package dev.huecoder.md3agent.feature.phoneuse

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Touch_app
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.PendingPrompt
import dev.huecoder.md3agent.conversation.PromptAnswer
import dev.huecoder.md3agent.feature.chat.ApprovalCard
import dev.huecoder.md3agent.feature.chat.PromptCard
import dev.huecoder.md3agent.feature.chat.PromptPanel
import dev.huecoder.md3agent.feature.chat.ToolCallUi

sealed interface ModalRequest {
    data class Prompt(val pending: PendingPrompt) : ModalRequest
    data class Approval(val call: ToolCallUi) : ModalRequest
    data object Interrupt : ModalRequest
}

class ModalActions(
    val onAnswer: (PendingPrompt, PromptAnswer) -> Unit,
    val onApproval: (ToolCallUi, ApprovalDecision) -> Unit,
    val onStopAgent: () -> Unit,
    val onKeepWorking: () -> Unit,
)

@Composable
fun PhoneModal(request: ModalRequest, actions: ModalActions) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(MODAL_MARGIN),
    ) {
        val card = Modifier.widthIn(max = MODAL_MAX_WIDTH)
        when (request) {
            is ModalRequest.Prompt -> WithStopAgent(actions, card) {
                PromptPanel(request.pending, onAnswer = { actions.onAnswer(request.pending, it) }, modifier = Modifier.fillMaxWidth())
            }
            is ModalRequest.Approval -> WithStopAgent(actions, card) {
                ApprovalCard(request.call, onDecision = { actions.onApproval(request.call, it) }, modifier = Modifier.fillMaxWidth())
            }
            ModalRequest.Interrupt -> InterruptCard(actions, card)
        }
    }
}

@Composable
private fun WithStopAgent(actions: ModalActions, modifier: Modifier, card: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
        card()
        FilledTonalButton(
            onClick = actions.onStopAgent,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { Text(stringResource(R.string.phone_use_interrupt_confirm)) }
    }
}

@Composable
private fun InterruptCard(actions: ModalActions, modifier: Modifier) {
    PromptCard(
        icon = MaterialSymbols.Rounded.Touch_app,
        header = stringResource(R.string.phone_use_working),
        title = stringResource(R.string.phone_use_interrupt_title),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.phone_use_interrupt_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = actions.onKeepWorking, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.phone_use_interrupt_keep)) }
            Button(
                onClick = actions.onStopAgent,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.phone_use_interrupt_confirm)) }
        }
    }
}

private const val SCRIM_ALPHA = 0.55f
private val MODAL_MARGIN = 16.dp
private val MODAL_MAX_WIDTH = 440.dp
