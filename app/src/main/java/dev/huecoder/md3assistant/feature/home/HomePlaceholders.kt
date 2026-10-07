package dev.huecoder.md3assistant.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.graphics.shapes.Morph
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Forum
import com.composables.icons.materialsymbols.rounded.Key
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.ui.components.MorphShape

@Composable
fun EmptyChats(modifier: Modifier = Modifier) {
    Placeholder(
        icon = MaterialSymbols.Rounded.Forum,
        title = stringResource(R.string.home_empty_title),
        body = stringResource(R.string.home_empty_body),
        modifier = modifier,
    )
}

@Composable
fun SetupPrompt(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Placeholder(
        icon = MaterialSymbols.Rounded.Key,
        title = stringResource(R.string.home_setup_title),
        body = stringResource(R.string.home_setup_body),
        modifier = modifier,
    ) {
        Button(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.home_setup_open_settings)) }
    }
}

@Composable
private fun Placeholder(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = rememberDriftingShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(160.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun rememberDriftingShape(): MorphShape {
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Clover8Leaf) }
    val transition = rememberInfiniteTransition(label = "drift")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(DRIFT_MORPH_MS), RepeatMode.Reverse), label = "morph")
    val rotation by transition.animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(DRIFT_SPIN_MS, easing = LinearEasing)), label = "spin")
    return MorphShape(morph, progress, rotation)
}

private const val DRIFT_MORPH_MS = 2_400
private const val DRIFT_SPIN_MS = 20_000
private const val FULL_TURN = 360f
