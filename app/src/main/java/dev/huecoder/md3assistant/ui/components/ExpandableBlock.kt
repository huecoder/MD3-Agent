package dev.huecoder.md3assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Expand_more
import dev.huecoder.md3assistant.ui.theme.LayoutMotion

@Composable
fun ExpandableBlock(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevron",
    )
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = HEADER_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                MaterialSymbols.Rounded.Expand_more,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = tint,
                modifier = Modifier.size(CHEVRON_SIZE).rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
            exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
        ) {
            QuoteRail(Modifier.padding(top = 4.dp, bottom = 4.dp)) { content() }
        }
    }
}

@Composable
fun QuoteRail(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant, content: @Composable () -> Unit) {
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .padding(start = RAIL_INSET)
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
        Box(Modifier.padding(start = RAIL_GAP).weight(1f)) { content() }
    }
}

private const val HALF_TURN = 180f
private val HEADER_VERTICAL_PADDING = 6.dp
private val CHEVRON_SIZE = 18.dp
private val RAIL_INSET = 7.dp
private val RAIL_WIDTH = 2.dp
private val RAIL_GAP = 14.dp
