package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mwagent.R
import com.mwagent.data.settings.InterfaceSettings
import com.mwagent.ui.haptics.rememberHaptics
import kotlin.math.roundToInt

@Composable
fun HapticStrengthSlider(strength: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val haptics = rememberHaptics()
    var dragged by remember(strength) { mutableFloatStateOf(strength) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(stringResource(R.string.appearance_haptic_strength), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.appearance_haptic_strength_value, (dragged * PERCENT).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragged,
            enabled = enabled,
            onValueChange = { value ->
                if (value != dragged) haptics.preview(value)
                dragged = value
            },
            onValueChangeFinished = { onChange(dragged) },
            valueRange = InterfaceSettings.MIN_HAPTIC_STRENGTH..1f,
            steps = STEPS,
        )
    }
}

private const val PERCENT = 100
private const val STEPS = 7
