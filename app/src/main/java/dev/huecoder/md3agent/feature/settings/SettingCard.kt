package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.ui.components.ShapeBadge

@Composable
internal fun SettingCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryMaxLines: Int = Int.MAX_VALUE,
    summaryColor: Color? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    badgeContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    selected: Boolean = false,
    indentBelow: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainer, label = "setting-card")
    val summaryTint = summaryColor ?: if (selected) colors.onSecondaryContainer.copy(alpha = SELECTED_SUMMARY_ALPHA) else colors.onSurfaceVariant
    val belowContent: (@Composable () -> Unit)? = below?.let { block -> { Column(Modifier.fillMaxWidth().padding(top = BELOW_GAP), content = block) } }
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = CARD_PADDING, vertical = CARD_PADDING_VERTICAL)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP), verticalAlignment = Alignment.Top) {
                ShapeBadge(icon, size = BADGE_SIZE, containerColor = badgeContainer, contentColor = badgeContent)
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = BADGE_SIZE),
                        horizontalArrangement = Arrangement.spacedBy(TRAILING_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                        trailing?.invoke()
                    }
                    summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = summaryTint, maxLines = summaryMaxLines, overflow = TextOverflow.Ellipsis) }
                    if (indentBelow) belowContent?.invoke()
                }
            }
            if (!indentBelow) belowContent?.invoke()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = container, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = container, modifier = modifier.fillMaxWidth(), content = content)
    }
}

private val BADGE_SIZE = 32.dp
private val CARD_PADDING = 16.dp
private val CARD_PADDING_VERTICAL = 12.dp
private val CONTENT_GAP = 12.dp
private val TRAILING_GAP = 8.dp
private val BELOW_GAP = 8.dp
private const val SELECTED_SUMMARY_ALPHA = 0.8f
