package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import kotlinx.coroutines.delay

@Composable
fun PhoneScene(modifier: Modifier = Modifier) {
    val labels = listOf(
        stringResource(R.string.onboarding_scene_phone_wifi),
        stringResource(R.string.onboarding_scene_phone_dark),
        stringResource(R.string.onboarding_scene_phone_flashlight),
    )
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var enabled by remember { mutableStateOf(listOf(true, false, false)) }
    val cursor = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val press = remember { Animatable(0f) }
    val rowPx = with(density) { ROW_HEIGHT.toPx() }
    val padPx = with(density) { PADDING.toPx() }

    LaunchedEffect(size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val switchX = size.width - padPx - with(density) { SWITCH_WIDTH.toPx() / 2f }
        fun switchOf(row: Int) = Offset(switchX, padPx + rowPx * row + rowPx / 2f)
        while (true) {
            enabled = listOf(true, false, false)
            cursor.snapTo(Offset(size.width * START_X, size.height.toFloat()))
            delay(HOLD_MS)
            listOf(1, 2).forEach { row ->
                cursor.animateTo(switchOf(row), tween(MOVE_MS, easing = FastOutSlowInEasing))
                press.snapTo(1f)
                press.animateTo(0f, tween(PRESS_MS))
                enabled = enabled.toMutableList().also { it[row] = true }
                delay(HOLD_MS / 2)
            }
            delay(HOLD_MS * 2)
        }
    }

    SceneFrame(modifier.onSizeChanged { size = it }) {
        Column(Modifier.fillMaxSize().padding(PADDING)) {
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    MockSwitch(enabled[index])
                }
            }
        }
        val ring = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            val at = cursor.value
            val pulse = press.value
            drawCircle(ring.copy(alpha = 0.25f * pulse), radius = 12.dp.toPx() + 22.dp.toPx() * pulse, center = at)
            drawCircle(ring, radius = 9.dp.toPx(), center = at)
        }
    }
}

@Composable
private fun MockSwitch(on: Boolean) {
    val track by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "mock-track")
    val thumb by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline, label = "mock-thumb")
    Box(Modifier.size(SWITCH_WIDTH, SWITCH_HEIGHT).clip(CircleShape).background(track).padding(4.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(SWITCH_HEIGHT - 8.dp).clip(CircleShape).background(thumb))
    }
}

private val ROW_HEIGHT = 56.dp
private val PADDING = 16.dp
private val SWITCH_WIDTH = 52.dp
private val SWITCH_HEIGHT = 30.dp
private const val START_X = 0.7f
private const val HOLD_MS = 800L
private const val MOVE_MS = 520
private const val PRESS_MS = 320
