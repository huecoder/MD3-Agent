package com.mwagent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.mwagent.R
import com.mwagent.ui.haptics.rememberHaptics
import com.mwagent.ui.theme.LayoutMotion

@Stable
class SectionFolds(initiallyOpen: Set<String>) {
    private var open by mutableStateOf(initiallyOpen)

    fun isOpen(key: String) = key in open

    fun toggle(key: String) {
        open = if (key in open) open - key else open + key
    }

    companion object {
        val Saver = listSaver<SectionFolds, String>(save = { it.open.toList() }, restore = { SectionFolds(it.toSet()) })
    }
}

@Composable
fun rememberSectionFolds(vararg initiallyOpen: String): SectionFolds =
    rememberSaveable(saver = SectionFolds.Saver) { SectionFolds(initiallyOpen.toSet()) }

@Composable
fun FoldableHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val haptics = rememberHaptics()
    val turn by animateFloatAsState(if (expanded) HALF_TURN else 0f, LayoutMotion.settle(), label = "fold-chevron")
    val state = stringResource(if (expanded) R.string.common_expanded else R.string.common_collapsed)
    Surface(
        onClick = {
            haptics.tick()
            onToggle()
        },
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { stateDescription = state },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(min = HEADER_HEIGHT).padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                MaterialSymbols.Rounded.Expand_more,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        }
    }
}

private const val HALF_TURN = 180f
private val HEADER_HEIGHT = 40.dp
