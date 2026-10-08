package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.TypingMode
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.feature.chat.ReplyText
import dev.huecoder.md3agent.ui.theme.LayoutMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TypingSettingsCard(style: TypingStyle, onChange: (TypingStyle) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedChoices(TYPING_CHOICES, style.mode, onSelect = { onChange(style.copy(mode = it)) })
            Text(
                stringResource(style.mode.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = style.isAnimated,
                enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
                exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
            ) {
                SpeedSlider(style.charsPerSecond, onChange = { onChange(style.copy(charsPerSecond = it)) })
            }
            TypingPreview(style)
        }
    }
}

@Composable
private fun SpeedSlider(charsPerSecond: Int, onChange: (Int) -> Unit) {
    var dragged by remember(charsPerSecond) { mutableFloatStateOf(charsPerSecond.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(stringResource(R.string.typing_speed), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.typing_speed_value, dragged.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged.roundToInt()) },
            valueRange = TypingStyle.MIN_CHARS_PER_SECOND.toFloat()..TypingStyle.MAX_CHARS_PER_SECOND.toFloat(),
        )
        Text(
            stringResource(R.string.typing_speed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TypingPreview(style: TypingStyle) {
    val sample = stringResource(R.string.typing_preview_sample)
    var run by remember { mutableIntStateOf(0) }
    LaunchedEffect(style, run) {
        delay(sample.length * MILLIS_PER_SECOND / style.charsPerSecond + PREVIEW_PAUSE_MS)
        run++
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).heightIn(min = PREVIEW_MIN_HEIGHT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.typing_preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            key(style, run) { ReplyText(sample, isStreaming = true, style = style) }
        }
    }
}

private val TypingMode.hint: Int
    get() = when (this) {
        TypingMode.FADE -> R.string.typing_mode_fade_hint
        TypingMode.TYPEWRITER -> R.string.typing_mode_typewriter_hint
        TypingMode.RAW -> R.string.typing_mode_raw_hint
    }

private val TYPING_CHOICES = listOf(
    Choice(TypingMode.FADE, R.string.typing_mode_fade),
    Choice(TypingMode.TYPEWRITER, R.string.typing_mode_typewriter),
    Choice(TypingMode.RAW, R.string.typing_mode_raw),
)

private const val MILLIS_PER_SECOND = 1_000L
private const val PREVIEW_PAUSE_MS = 1_500L
private val PREVIEW_MIN_HEIGHT = 96.dp
