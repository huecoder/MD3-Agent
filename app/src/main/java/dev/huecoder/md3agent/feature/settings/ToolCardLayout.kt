package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max

private enum class Slot { ICON, TITLE, SUMMARY, MODE, TOGGLE }

@Composable
internal fun ToolCardLayout(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    mode: @Composable () -> Unit,
    toggle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(Slot.ICON)) { icon() }
            Box(Modifier.layoutId(Slot.TITLE)) { title() }
            summary?.let { Box(Modifier.layoutId(Slot.SUMMARY)) { it() } }
            Box(Modifier.layoutId(Slot.MODE)) { mode() }
            Box(Modifier.layoutId(Slot.TOGGLE)) { toggle() }
        },
        modifier = modifier,
        measurePolicy = ToolCardPolicy,
    )
}

private object ToolCardPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val gap = GAP.roundToPx()
        val summaryGap = SUMMARY_GAP.roundToPx()
        val modeGap = MODE_GAP.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        fun measured(slot: Slot): Placeable? = measurables.firstOrNull { it.layoutId == slot }?.measure(loose)

        val icon = checkNotNull(measured(Slot.ICON))
        val mode = checkNotNull(measured(Slot.MODE))
        val toggle = checkNotNull(measured(Slot.TOGGLE))
        val width = constraints.maxWidth
        val inlineTitleWidth = width - icon.width - mode.width - toggle.width - 3 * gap
        val inline = inlineTitleWidth >= MIN_TITLE_WIDTH.roundToPx()
        val titleWidth = if (inline) inlineTitleWidth else width - icon.width - toggle.width - 2 * gap
        val title = checkNotNull(measurables.firstOrNull { it.layoutId == Slot.TITLE }).measure(Constraints(maxWidth = titleWidth.coerceAtLeast(0)))
        val textStart = icon.width + gap
        val summary = measurables.firstOrNull { it.layoutId == Slot.SUMMARY }
            ?.measure(Constraints(maxWidth = (width - textStart).coerceAtLeast(0)))

        val headerHeight = listOf(icon.height, title.height, toggle.height, if (inline) mode.height else 0).max()
        val summaryTop = headerHeight + if (summary != null) summaryGap else 0
        val summaryBottom = summaryTop + (summary?.height ?: 0)
        val height = if (inline) summaryBottom else summaryBottom + modeGap + mode.height

        return layout(width, height) {
            icon.placeRelative(0, centered(headerHeight, icon.height))
            title.placeRelative(textStart, centered(headerHeight, title.height))
            toggle.placeRelative(width - toggle.width, centered(headerHeight, toggle.height))
            summary?.placeRelative(textStart, summaryTop)
            if (inline) {
                mode.placeRelative(width - toggle.width - gap - mode.width, centered(headerHeight, mode.height))
            } else {
                mode.placeRelative(textStart, summaryBottom + modeGap)
            }
        }
    }

    private fun centered(space: Int, size: Int) = max(0, (space - size) / 2)
}

private val GAP = 12.dp
private val SUMMARY_GAP = 4.dp
private val MODE_GAP = 8.dp
private val MIN_TITLE_WIDTH = 120.dp
