package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AvatarRestShape
import dev.huecoder.md3agent.data.settings.AvatarStyle
import dev.huecoder.md3agent.ui.avatar.BotAvatar
import dev.huecoder.md3agent.ui.components.ClassicAvatar
import kotlinx.coroutines.delay

@Composable
fun AvatarPicker(style: AvatarStyle, restShape: AvatarRestShape, onStyle: (AvatarStyle) -> Unit, onRestShape: (AvatarRestShape) -> Unit) {
    var working by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PREVIEW_PHASE_MS)
            working = !working
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AvatarCard(R.string.agent_avatar_classic, selected = style == AvatarStyle.CLASSIC, onClick = { onStyle(AvatarStyle.CLASSIC) }, modifier = Modifier.weight(1f)) {
                ClassicAvatar(isWorking = working, size = PREVIEW_SIZE)
            }
            AvatarCard(R.string.agent_avatar_bot, selected = style == AvatarStyle.BOT, onClick = { onStyle(AvatarStyle.BOT) }, modifier = Modifier.weight(1f)) {
                BotAvatar(isWorking = working, restShape = restShape, size = PREVIEW_SIZE)
            }
        }
        if (style == AvatarStyle.BOT) {
            Text(stringResource(R.string.agent_avatar_rest_shape), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                AvatarRestShape.entries.forEachIndexed { index, shape ->
                    ToggleButton(
                        checked = shape == restShape,
                        onCheckedChange = { onRestShape(shape) },
                        shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(shape.label)) }
                }
            }
        }
    }
}

@Composable
private fun AvatarCard(@StringRes title: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Box(contentAlignment = Alignment.Center) { preview() }
            Text(stringResource(title), style = MaterialTheme.typography.titleSmallEmphasized)
        }
    }
}

@get:StringRes
private val AvatarRestShape.label: Int
    get() = when (this) {
        AvatarRestShape.CIRCLE -> R.string.agent_avatar_rest_circle
        AvatarRestShape.HEXAGON -> R.string.agent_avatar_rest_hexagon
    }

private const val PREVIEW_PHASE_MS = 3_500L
private val PREVIEW_SIZE = 88.dp
