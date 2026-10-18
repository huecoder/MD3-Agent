package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.huecoder.md3agent.ui.components.MorphShape

@Composable
fun ChoiceCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    summary: String? = null,
    tag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val bloom by animateFloatAsState(if (selected) 1f else 0f, spring(BLOOM_DAMPING, BLOOM_STIFFNESS), label = "choice-bloom")
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh, label = "choice-container")
    val badge by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, label = "choice-badge")
    val glyph by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, label = "choice-glyph")
    val shade by animateFloatAsState(if (dimmed && !selected) DIMMED_ALPHA else 1f, label = "choice-shade")

    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        modifier = modifier.fillMaxWidth().graphicsLayer { alpha = shade },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = MorphShape(morph, bloom, bloom * BLOOM_TURN),
                color = badge,
                contentColor = glyph,
                modifier = Modifier.size(BADGE_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tag?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.primary) }
                Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onSecondaryContainer else colors.onSurface)
                summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
        }
    }
}

private val BADGE_SIZE = 44.dp
private val ICON_SIZE = 22.dp
private const val BLOOM_DAMPING = 0.55f
private const val BLOOM_STIFFNESS = 380f
private const val BLOOM_TURN = 40f
private const val DIMMED_ALPHA = 0.62f
