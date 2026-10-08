package dev.huecoder.md3agent.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.voice.DictationState
import dev.huecoder.md3agent.voice.DictationViewModel
import java.util.Locale

@Composable
fun DictationStrip(state: DictationState, onDismissError: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.heightIn(min = StripHeight).padding(horizontal = 12.dp),
    ) {
        when (state) {
            is DictationState.Recording -> {
                RecordingDot()
                Text(elapsedLabel(state.elapsedMs), style = MaterialTheme.typography.labelLarge)
                Waveform(state.levels, Modifier.weight(1f).height(WaveformHeight))
            }
            DictationState.Transcribing -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.dictation_transcribing), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is DictationState.Failed -> Text(
                state.message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f).clickable(onClick = onDismissError).padding(vertical = 8.dp),
            )
            DictationState.Idle -> Unit
        }
    }
}

@Composable
private fun RecordingDot() {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = DOT_DIM_ALPHA,
        animationSpec = infiniteRepeatable(tween(DOT_PULSE_MS), RepeatMode.Reverse),
        label = "rec-alpha",
    )
    Box(Modifier.size(10.dp).alpha(pulse).background(MaterialTheme.colorScheme.error, CircleShape))
}

@Composable
private fun Waveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth()) {
        val slot = size.width / DictationViewModel.WAVEFORM_BARS
        val barWidth = slot * BAR_FILL
        val offset = DictationViewModel.WAVEFORM_BARS - levels.size
        levels.forEachIndexed { index, level ->
            val barHeight = size.height * (MIN_BAR + (1 - MIN_BAR) * level.coerceIn(0f, 1f))
            drawRoundRect(
                color = color,
                topLeft = Offset((offset + index) * slot + (slot - barWidth) / 2, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private fun elapsedLabel(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private val StripHeight = 56.dp
private val WaveformHeight = 28.dp
private const val BAR_FILL = 0.55f
private const val MIN_BAR = 0.12f
private const val DOT_DIM_ALPHA = 0.25f
private const val DOT_PULSE_MS = 700
private const val MS_PER_SECOND = 1_000
private const val SECONDS_PER_MINUTE = 60
