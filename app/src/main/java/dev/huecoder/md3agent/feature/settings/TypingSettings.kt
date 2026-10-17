package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Speed
import com.composables.icons.materialsymbols.rounded.Text_fields
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.TypingMode
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.feature.chat.ReplyText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal fun CardGroupScope.typingCards(style: TypingStyle, onChange: (TypingStyle) -> Unit) {
    card("typing-style") { shape ->
        SettingCard(
            icon = MaterialSymbols.Rounded.Text_fields,
            title = stringResource(R.string.typing_style),
            summary = stringResource(style.mode.hint),
            shape = shape,
            indentBelow = false,
            below = { ChoicePill(TYPING_CHOICES, style.mode, { onChange(style.copy(mode = it)) }, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
        )
    }
    if (style.isAnimated) {
        card("typing-speed") { shape ->
            SliderCard(
                icon = MaterialSymbols.Rounded.Speed,
                title = stringResource(R.string.typing_speed),
                summary = stringResource(R.string.typing_speed_hint),
                value = style.charsPerSecond.toFloat(),
                range = TypingStyle.MIN_CHARS_PER_SECOND.toFloat()..TypingStyle.MAX_CHARS_PER_SECOND.toFloat(),
                valueLabel = { stringResource(R.string.typing_speed_value, it.roundToInt()) },
                onCommit = { onChange(style.copy(charsPerSecond = it.roundToInt())) },
                shape = shape,
            )
        }
    }
    card("typing-preview") { shape -> TypingPreview(style, shape) }
}

@Composable
private fun TypingPreview(style: TypingStyle, shape: Shape) {
    val sample = stringResource(R.string.typing_preview_sample)
    var run by remember { mutableIntStateOf(0) }
    LaunchedEffect(style, run) {
        delay(sample.length * MILLIS_PER_SECOND / style.charsPerSecond + PREVIEW_PAUSE_MS)
        run++
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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
