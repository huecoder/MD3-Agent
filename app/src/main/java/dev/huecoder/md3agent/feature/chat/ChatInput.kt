package dev.huecoder.md3agent.feature.chat

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
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
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.voice.DictationState

private val InlineShape = RoundedCornerShape(percent = 50)
private val StackedShape = RoundedCornerShape(28.dp)
private const val MAX_VISIBLE_LINES = 8
private const val UNSTACK_MARGIN_CHARS = 6

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
    val haptics = rememberHaptics()
    val dictationState = dictation?.state ?: DictationState.Idle
    val isDictating = dictationState != DictationState.Idle
    val action = when {
        isStreaming -> ComposerAction.STOP
        dictationState is DictationState.Recording -> ComposerAction.FINISH_DICTATION
        canSend || dictation == null || dictationState == DictationState.Transcribing -> ComposerAction.SEND
        else -> ComposerAction.DICTATE
    }
    var stackedAt by remember { mutableStateOf<Int?>(null) }
    val enteredStackAt = stackedAt
    if (enteredStackAt != null && '\n' !in text && text.length < enteredStackAt - UNSTACK_MARGIN_CHARS) stackedAt = null
    val stacked = stackedAt != null && !isDictating
    val leading: (() -> Unit)? = if (isDictating) dictation?.onCancel else onAttach

    val leadingButton = @Composable { leading?.let { LeadingButton(isDictating, it) } }
    val actionButton = @Composable {
        ActionButton(
            action = action,
            enabled = action != ComposerAction.SEND || (canSend && !isDictating),
            onClick = {
                haptics.confirm()
                when (action) {
                    ComposerAction.STOP -> onStop()
                    ComposerAction.SEND -> onSend()
                    ComposerAction.DICTATE -> dictation?.onStart?.invoke()
                    ComposerAction.FINISH_DICTATION -> dictation?.onFinish?.invoke()
                }
            },
        )
    }
    val field = @Composable { fieldModifier: Modifier ->
        if (isDictating && dictation != null) {
            DictationStrip(dictationState, onDismissError = dictation.onDismissError, modifier = fieldModifier)
        } else {
            MessageField(
                text = text,
                onTextChange = onTextChange,
                focusRequester = focusRequester,
                onLineCount = { lines -> if (lines > 1 && stackedAt == null) stackedAt = text.length },
                modifier = fieldModifier,
            )
        }
    }

    Surface(
        shape = if (stacked || attachments.isNotEmpty() || isImporting) StackedShape else InlineShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(COMPOSER_INSET).animateContentSize()) {
            if (attachments.isNotEmpty() || isImporting) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 4.dp)) {
                    AttachmentStrip(attachments, onRemove = onRemoveAttachment, modifier = Modifier.weight(1f, fill = false))
                    if (isImporting) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!stacked) leadingButton()
                field(Modifier.weight(1f).padding(start = if (stacked || leading == null) FIELD_SIDE_INSET else 0.dp, end = if (stacked) FIELD_SIDE_INSET else 4.dp))
                if (!stacked) actionButton()
            }
            if (stacked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingButton()
                    Spacer(Modifier.weight(1f))
                    actionButton()
                }
            }
        }
    }
}

@Composable
private fun LeadingButton(isDictating: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        if (isDictating) {
            Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.dictation_cancel))
        } else {
            Icon(MaterialSymbols.Rounded.Attach_file, contentDescription = stringResource(R.string.chat_attach_file))
        }
    }
}

@Composable
private fun MessageField(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onLineCount: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = style,
        maxLines = MAX_VISIBLE_LINES,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        onTextLayout = { onLineCount(it.lineCount) },
        modifier = modifier.heightIn(min = FIELD_MIN_HEIGHT).focusRequester(focusRequester),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(vertical = FIELD_VERTICAL_INSET)) {
                if (text.isEmpty()) Text(stringResource(R.string.chat_input_placeholder), style = style, color = colors.onSurfaceVariant)
                inner()
            }
        },
    )
}

private val COMPOSER_INSET = 6.dp
private val FIELD_SIDE_INSET = 12.dp
private val FIELD_VERTICAL_INSET = 12.dp
private val FIELD_MIN_HEIGHT = 48.dp
private val ACTION_SIZE = 48.dp

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
        modifier = Modifier.size(ACTION_SIZE),
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
