package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_drop_down
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.models.ModelFamily

@Composable
fun ModelChip(
    family: ModelFamily?,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ModelChipSize = ModelChipSize.Compact,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.gap),
            modifier = Modifier.heightIn(min = size.height).padding(start = size.startPadding, end = size.endPadding),
        ) {
            VendorGlyph(family, size = size.glyph, tint = tint)
            Text(
                label ?: stringResource(R.string.chat_change_model),
                style = size.textStyle(),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = size.maxTextWidth),
            )
            Icon(
                MaterialSymbols.Rounded.Arrow_drop_down,
                contentDescription = stringResource(R.string.chat_change_model),
                tint = tint,
                modifier = Modifier.size(size.arrow),
            )
        }
    }
}

enum class ModelChipSize(
    val height: Dp,
    val glyph: Dp,
    val arrow: Dp,
    val gap: Dp,
    val startPadding: Dp,
    val endPadding: Dp,
    val maxTextWidth: Dp,
) {
    Compact(height = 24.dp, glyph = 12.dp, arrow = 16.dp, gap = 4.dp, startPadding = 8.dp, endPadding = 2.dp, maxTextWidth = 220.dp),
    Regular(height = 36.dp, glyph = 16.dp, arrow = 20.dp, gap = 6.dp, startPadding = 12.dp, endPadding = 6.dp, maxTextWidth = 150.dp);

    @Composable
    fun textStyle(): TextStyle = when (this) {
        Compact -> MaterialTheme.typography.labelMedium
        Regular -> MaterialTheme.typography.labelLarge
    }
}
