package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.ui.components.AppBottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString
import kotlin.math.roundToInt

@Composable
fun ContextSheet(meter: ContextMeter, load: suspend () -> ContextBreakdown?, onChangeLimit: (Int?) -> Unit, onDismiss: () -> Unit) {
    val breakdown by produceState<ContextBreakdown?>(null, meter.used) { value = load() ?: ContextBreakdown(emptyList()) }
    val slices = breakdown?.slices.orEmpty()
    val palette = chartPalette(slices.size)
    var editingLimit by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.clickHaptics()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.chat_context_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PieChart(slices.map { it.tokens.toDouble() }, palette, Modifier.size(PIE_SIZE))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ReplyMetaFormat.tokens(meter.used).asString(), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        UiText.Resource(R.string.chat_context_of, listOf(ReplyMetaFormat.tokens(meter.limit))).asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.heightIn(min = ROW_HEIGHT * RESERVED_ROWS)) {
                val total = breakdown?.tokens ?: 0
                slices.forEachIndexed { index, slice ->
                    SliceRow(
                        color = palette[index % palette.size],
                        label = stringResource(slice.part.label),
                        tokens = ReplyMetaFormat.tokens(slice.tokens).asString(),
                        percent = UiText.Resource(R.string.chat_context_percent, listOf(percentOf(slice.tokens, total))).asString(),
                    )
                }
            }
            if (slices.isNotEmpty()) {
                Text(stringResource(R.string.chat_context_estimate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { editingLimit = true }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.chat_context_limit_change))
            }
            Spacer(Modifier.height(BOTTOM_SPACE))
        }
    }
    if (editingLimit) {
        ContextLimitDialog(
            custom = meter.custom,
            onConfirm = {
                onChangeLimit(it)
                editingLimit = false
            },
            onDismiss = { editingLimit = false },
        )
    }
}

@Composable
private fun ContextLimitDialog(custom: Int?, onConfirm: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(custom?.toString().orEmpty()) }
    val parsed = ContextWindowInput.parse(text)
    val isInvalid = text.isNotBlank() && parsed == null
    AlertDialog(
        modifier = Modifier.clickHaptics(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_context_limit_title)) },
        text = {
            FormField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.chat_context_limit_label),
                placeholder = stringResource(R.string.chat_context_limit_placeholder),
                supportingText = if (isInvalid) stringResource(R.string.chat_context_limit_invalid) else null,
                isError = isInvalid,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(parsed) }, enabled = !isInvalid) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
    )
}

@Composable
private fun SliceRow(color: Color, label: String, tokens: String, percent: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
    ) {
        Surface(Modifier.size(SWATCH), shape = CircleShape, color = color) {}
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(tokens, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(percent, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = PERCENT_WIDTH))
    }
}

private fun percentOf(tokens: Int, total: Int): Int =
    if (total <= 0) 0 else (tokens * PERCENT_BASE / total).roundToInt().coerceAtLeast(1)

private const val PERCENT_BASE = 100f
private val PIE_SIZE = 200.dp
private val ROW_HEIGHT = 44.dp
private const val RESERVED_ROWS = 4
private val SWATCH = 12.dp
private val PERCENT_WIDTH = 48.dp
private val BOTTOM_SPACE = 8.dp
