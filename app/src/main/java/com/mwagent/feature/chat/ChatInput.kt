package com.mwagent.feature.chat

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_upward
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.roundedfilled.Stop
import com.mwagent.R
import com.mwagent.data.attachments.Attachment
import com.mwagent.ui.haptics.rememberHaptics
import com.mwagent.voice.DictationState

private val InlineShape = RoundedCornerShape(percent = 50)
private val StackedShape = RoundedCornerShape(28.dp)
private const val MAX_VISIBLE_LINES = 8
private const val SENT_FADE_MS = 160

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
    onPasteImages: ((List<Uri>) -> Unit)? = null,
    dictation: DictationControls? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onLetterTyped: (Offset) -> Unit = {},
    steerable: Boolean = false,
) {
    val haptics = rememberHaptics()
    val dictationState = dictation?.state ?: DictationState.Idle
    val isDictating = dictationState != DictationState.Idle
    val steering = steerable && isStreaming && canSend && !isDictating
    val action = when {
        isStreaming && !steering -> ComposerAction.STOP
        dictationState is DictationState.Recording -> ComposerAction.FINISH_DICTATION
        canSend || dictation == null || dictationState == DictationState.Transcribing -> ComposerAction.SEND
        else -> ComposerAction.DICTATE
    }
    var wrappedAt by remember { mutableStateOf<Int?>(null) }
    val settledAt = ComposerStacking.settle(wrappedAt, text)
    if (settledAt != wrappedAt) wrappedAt = settledAt
    val stacked = ComposerStacking.isStacked(settledAt, text) && !isDictating
    var sentText by remember { mutableStateOf<String?>(null) }
    val leading: (() -> Unit)? = if (isDictating) dictation?.onCancel else onAttach

    val leadingButton = @Composable { leading?.let { LeadingButton(isDictating, it) } }
    val actionButton = @Composable {
        if (steering) {
            StopButton(onClick = { haptics.confirm(); onStop() })
            Spacer(Modifier.width(SIDE_BUTTON_GAP))
        }
        ActionButton(
            action = action,
            enabled = action != ComposerAction.SEND || (canSend && !isDictating),
            onClick = {
                haptics.confirm()
                when (action) {
                    ComposerAction.STOP -> onStop()
                    ComposerAction.SEND -> {
                        sentText = text.ifBlank { null }
                        onSend()
                    }
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
            Box(fieldModifier) {
                MessageField(
                    text = text,
                    onTextChange = onTextChange,
                    focusRequester = focusRequester,
                    onLineCount = { lines, laidOut -> wrappedAt = ComposerStacking.wrappedAfter(wrappedAt, laidOut, lines) },
                    onLetterTyped = onLetterTyped,
                    onPasteImages = onPasteImages,
                    modifier = Modifier.fillMaxWidth(),
                )
                sentText?.let { SentTextFade(it, onFaded = { sentText = null }) }
            }
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
private fun BoxScope.SentTextFade(text: String, onFaded: () -> Unit) {
    val alpha = remember(text) { Animatable(1f) }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(text) {
        alpha.animateTo(0f, tween(SENT_FADE_MS))
        onFaded()
    }
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = FIELD_VERTICAL_INSET),
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(ACTION_SIZE)) {
        Icon(MaterialSymbols.RoundedFilled.Stop, contentDescription = stringResource(R.string.chat_stop))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageField(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onLineCount: (lineCount: Int, laidOutText: String) -> Unit,
    onLetterTyped: (Offset) -> Unit,
    onPasteImages: ((List<Uri>) -> Unit)?,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    val caret = remember { CaretTracker() }
    val field = rememberTextFieldState(text)
    val latestText by rememberUpdatedState(text)
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    val reportEdits = remember {
        InputTransformation {
            val edited = asCharSequence().toString()
            if (!originalText.contentEquals(edited)) {
                caret.onEdit(originalText.toString(), edited)
                latestOnTextChange(edited)
            }
        }
    }
    LaunchedEffect(text) {
        if (field.text.toString() != latestText) field.setTextAndPlaceCursorAtEnd(latestText)
    }
    BasicTextField(
        state = field,
        inputTransformation = reportEdits,
        textStyle = style,
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_VISIBLE_LINES),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        onTextLayout = { layout ->
            layout()?.let { result ->
                onLineCount(result.lineCount, result.layoutInput.text.text)
                caret.onLayout(result)?.let(onLetterTyped)
            }
        },
        modifier = modifier
            .heightIn(min = FIELD_MIN_HEIGHT)
            .focusRequester(focusRequester)
            .then(if (onPasteImages == null) Modifier else Modifier.contentReceiver(pastedImages(onPasteImages))),
        decorator = { inner ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(vertical = FIELD_VERTICAL_INSET)) {
                if (field.text.isEmpty()) Text(stringResource(R.string.chat_input_placeholder), style = style, color = colors.onSurfaceVariant)
                Box(Modifier.onGloballyPositioned { caret.textCoordinates = it }) { inner() }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun pastedImages(onImages: (List<Uri>) -> Unit) = ReceiveContentListener { content ->
    if (!content.hasMediaType(MediaType.Image)) return@ReceiveContentListener content
    val uris = mutableListOf<Uri>()
    val rest = content.consume { item -> item.uri?.let(uris::add) == true }
    if (uris.isNotEmpty()) onImages(uris)
    rest
}

private class CaretTracker {
    var textCoordinates: LayoutCoordinates? = null
    private var pendingIndex: Int? = null

    fun onEdit(before: String, after: String) {
        val prefix = before.commonPrefixWith(after).length
        val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
        pendingIndex = after.length - suffix
    }

    fun onLayout(layout: TextLayoutResult): Offset? {
        val index = pendingIndex ?: return null
        pendingIndex = null
        val coordinates = textCoordinates?.takeIf { it.isAttached } ?: return null
        val cursor = layout.getCursorRect(index.coerceIn(0, layout.layoutInput.text.length))
        return coordinates.localToRoot(cursor.center)
    }
}

private val COMPOSER_INSET = 6.dp
private val FIELD_SIDE_INSET = 12.dp
private val FIELD_VERTICAL_INSET = 12.dp
private val FIELD_MIN_HEIGHT = 48.dp
private val ACTION_SIZE = 48.dp
private val SIDE_BUTTON_GAP = 6.dp

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
