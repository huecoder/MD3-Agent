package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Bookmark
import com.composables.icons.materialsymbols.rounded.Neurology
import dev.huecoder.md3agent.R
import kotlinx.coroutines.delay

@Composable
fun MemoryScene(modifier: Modifier = Modifier) {
    val appear = remember { Animatable(0f) }
    val pin = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            appear.snapTo(0f)
            pin.snapTo(0f)
            delay(HOLD_MS)
            appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f))
            delay(HOLD_MS / 2)
            pin.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
            delay(HOLD_MS * 3)
            appear.animateTo(0f, tween(FADE_MS))
        }
    }

    SceneFrame(modifier) {
        Column(
            Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    val scale = 0.8f + 0.2f * appear.value
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MaterialSymbols.Rounded.Neurology, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.onboarding_scene_memory_text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    alpha = pin.value.coerceIn(0f, 1f)
                    val scale = 0.6f + 0.4f * pin.value
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                Icon(MaterialSymbols.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.onboarding_scene_memory_saved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private const val HOLD_MS = 700L
private const val FADE_MS = 350
