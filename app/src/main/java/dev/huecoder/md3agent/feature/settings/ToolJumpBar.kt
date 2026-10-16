package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.ui.components.ToolCategory
import dev.huecoder.md3agent.ui.components.ToolGroup
import dev.huecoder.md3agent.ui.components.group
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlin.math.roundToInt

@Stable
internal class SectionAnchors {
    private var viewport: LayoutCoordinates? = null
    private val anchors = mutableStateMapOf<ToolCategory, LayoutCoordinates>()

    val groups: List<ToolGroup> get() = anchors.keys.map { it.group }.distinct().sorted()

    fun viewport(): Modifier = Modifier.onGloballyPositioned { viewport = it }

    @Composable
    fun anchor(category: ToolCategory): Modifier {
        DisposableEffect(category) { onDispose { anchors.remove(category) } }
        return Modifier.onPlaced { anchors[category] = it }
    }

    suspend fun scrollTo(group: ToolGroup, scroll: ScrollState) {
        val first = firstCategory(group) ?: return
        val top = topOf(first) ?: return
        scroll.animateScrollTo((scroll.value + top).roundToInt().coerceAtLeast(0))
    }

    fun selected(scroll: ScrollState, threshold: Float): ToolGroup? {
        val tops = groups.mapNotNull { group -> firstCategory(group)?.let(::topOf)?.let { group to it } }
        return spiedGroup(tops, atBottom = scroll.maxValue > 0 && scroll.value >= scroll.maxValue, threshold)
    }

    private fun firstCategory(group: ToolGroup): ToolCategory? = anchors.keys.filter { it.group == group }.minOrNull()

    private fun topOf(category: ToolCategory): Float? {
        val frame = viewport?.takeIf { it.isAttached } ?: return null
        val anchor = anchors[category]?.takeIf { it.isAttached } ?: return null
        return frame.localPositionOf(anchor, Offset.Zero).y
    }
}

internal fun spiedGroup(tops: List<Pair<ToolGroup, Float>>, atBottom: Boolean, threshold: Float): ToolGroup? {
    if (tops.isEmpty()) return null
    if (atBottom) return tops.last().first
    return tops.lastOrNull { it.second <= threshold }?.first ?: tops.first().first
}

@Composable
internal fun ToolJumpBar(available: List<ToolGroup>, selected: ToolGroup?, onSelect: (ToolGroup) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolGroup.entries.forEach { group ->
            FilterChip(
                selected = group == selected,
                enabled = group in available,
                onClick = {
                    haptics.tick()
                    onSelect(group)
                },
                label = { Text(stringResource(group.title)) },
            )
        }
    }
}
