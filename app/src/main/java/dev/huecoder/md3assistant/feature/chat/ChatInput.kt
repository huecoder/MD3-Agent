package dev.huecoder.md3assistant.feature.chat

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_upward
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.roundedfilled.Stop
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.voice.DictationState

private val ComposerShape = RoundedCornerShape(32.dp)
private const val MAX_VISIBLE_LINES = 6

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    attachments: List<Attachment> = emptyList(),
    isImporting: Boolean = false,
    onAttach: (() -> Unit)? = null,
    onRemoveAttachment: (Attachment) -> Unit = {},
    dictation: DictationControls? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val dictationState = dictation?.state ?: DictationState.Idle
    val isDictating = dictationState != DictationState.Idle
    val action = when {
        isStreaming -> ComposerAction.STOP
        dictationState is DictationState.Recording -> ComposerAction.FINISH_DICTATION
        canSend || dictation == null || dictationState == DictationState.Transcribing -> ComposerAction.SEND
        else -> ComposerAction.DICTATE
    }

    Surface(
        shape = ComposerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            if (attachments.isNotEmpty() || isImporting) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp)) {
                    AttachmentStrip(attachments, onRemove = onRemoveAttachment, modifier = Modifier.weight(1f, fill = false))
                    if (isImporting) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val leading: (() -> Unit)? = if (isDictating) dictation?.onCancel else onAttach
                leading?.let { onClick ->
                    IconButton(
                        onClick = onClick,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(bottom = ATTACH_BUTTON_BOTTOM_INSET),
                    ) {
                        if (isDictating) {
                            Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.dictation_cancel))
                        } else {
                            Icon(MaterialSymbols.Rounded.Attach_file, contentDescription = stringResource(R.string.chat_attach_file))
                        }
                    }
                }
                if (isDictating && dictation != null) {
                    DictationStrip(dictationState, onDismissError = dictation.onDismissError, modifier = Modifier.weight(1f))
                } else {
                    MessageField(text, onTextChange, focusRequester, Modifier.weight(1f))
                }
                ActionButton(
                    action = action,
                    enabled = when (action) {
                        ComposerAction.SEND -> canSend && !isDictating
                        else -> true
                    },
                    onClick = {
                        when (action) {
                            ComposerAction.STOP -> onStop()
                            ComposerAction.SEND -> onSend()
                            ComposerAction.DICTATE -> dictation?.onStart?.invoke()
                            ComposerAction.FINISH_DICTATION -> dictation?.onFinish?.invoke()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageField(text: String, onTextChange: (String) -> Unit, focusRequester: FocusRequester, modifier: Modifier) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.chat_input_placeholder), style = MaterialTheme.typography.bodyLarge) },
        textStyle = MaterialTheme.typography.bodyLarge,
        maxLines = MAX_VISIBLE_LINES,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.heightIn(min = 56.dp).focusRequester(focusRequester),
    )
}

private val ATTACH_BUTTON_BOTTOM_INSET = 4.dp

private enum class ComposerAction(val icon: ImageVector, @StringRes val description: Int) {
    SEND(MaterialSymbols.Rounded.Arrow_upward, R.string.chat_send),
    STOP(MaterialSymbols.RoundedFilled.Stop, R.string.chat_stop),
    DICTATE(MaterialSymbols.Rounded.Mic, R.string.dictation_start),
    FINISH_DICTATION(MaterialSymbols.Rounded.Check, R.string.dictation_finish),
}

@Composable
private fun ActionButton(action: ComposerAction, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            disabledContainerColor = scheme.surfaceContainerHighest,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Uniform)),
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = { scaleIn() togetherWith scaleOut() },
            label = "composer-action-icon",
        ) { shown ->
            Icon(shown.icon, contentDescription = stringResource(shown.description))
        }
    }
}
