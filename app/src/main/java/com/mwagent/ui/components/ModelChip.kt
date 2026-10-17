package com.mwagent.ui.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_drop_down
import com.mwagent.R
import com.mwagent.llm.models.ModelFamily

@Composable
fun ModelChip(
    family: ModelFamily?,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
            modifier = Modifier.heightIn(min = HEIGHT).padding(start = START_PADDING, end = END_PADDING),
        ) {
            VendorGlyph(family, size = GLYPH, tint = tint)
            Text(
                label ?: stringResource(R.string.chat_change_model),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).widthIn(max = MAX_TEXT_WIDTH),
            )
            Icon(
                MaterialSymbols.Rounded.Arrow_drop_down,
                contentDescription = stringResource(R.string.chat_change_model),
                tint = tint,
                modifier = Modifier.size(ARROW),
            )
        }
    }
}

private val HEIGHT = 36.dp
private val GLYPH = 16.dp
private val ARROW = 20.dp
private val GAP = 6.dp
private val START_PADDING = 12.dp
private val END_PADDING = 6.dp
private val MAX_TEXT_WIDTH = 150.dp
