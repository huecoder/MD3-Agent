package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Expand_more
import dev.huecoder.md3agent.ui.components.CheckSwitch
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.theme.LayoutMotion

@Composable
internal fun Modifier.settingsPage(padding: PaddingValues, scroll: ScrollState = rememberScrollState()): Modifier =
    fillMaxSize()
        .verticalScroll(scroll)
        .padding(
            start = PAGE_SIDE_PADDING,
            end = PAGE_SIDE_PADDING,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + PAGE_BOTTOM_PADDING,
        )

internal class CardGroupScope {
    val cards = mutableListOf<Pair<Any, @Composable (Shape) -> Unit>>()

    fun card(key: Any = cards.size, content: @Composable (Shape) -> Unit) {
        cards += key to content
    }
}

@Composable
internal fun CardGroup(modifier: Modifier = Modifier, content: CardGroupScope.() -> Unit) {
    val cards = CardGroupScope().apply(content).cards
    Column(modifier.fillMaxWidth().animateContentSize(LayoutMotion.settle()), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        cards.forEachIndexed { index, (cardKey, card) ->
            key(cardKey) { card(segmentShape(index, cards.size)) }
        }
    }
}

@Composable
internal fun segmentShape(index: Int, count: Int): Shape = ListItemDefaults.segmentedShapes(index, count).shape

@Composable
internal fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SwitchCard(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val haptics = rememberHaptics()
    SettingCard(
        icon = icon,
        title = title,
        modifier = modifier,
        summary = summary,
        shape = shape,
        onClick = {
            haptics.toggle(!checked)
            onCheckedChange(!checked)
        },
        trailing = { CheckSwitch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
internal fun <T> MenuCard(
    icon: ImageVector,
    title: String,
    options: List<DropdownOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        SettingCard(
            icon = icon,
            title = title,
            summary = options.firstOrNull { it.value == selected }?.label,
            shape = shape,
            onClick = { expanded = true },
            trailing = { Icon(MaterialSymbols.Rounded.Expand_more, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.clickHaptics()) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option.value == selected) {
                        { Icon(MaterialSymbols.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun PickerCard(
    icon: ImageVector,
    title: String,
    value: String,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    SettingCard(
        icon = icon,
        title = title,
        modifier = modifier,
        summary = value,
        summaryMaxLines = PICKER_VALUE_LINES,
        shape = shape,
        onClick = onClick,
        trailing = { Icon(MaterialSymbols.Rounded.Chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        below = detail?.let { text ->
            { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
internal fun FormCard(
    icon: ImageVector,
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingCard(
        icon = icon,
        title = title,
        modifier = modifier,
        summary = summary,
        shape = shape,
        indentBelow = false,
        below = { Column(Modifier.fillMaxWidth().padding(top = FIELDS_TOP_GAP), verticalArrangement = Arrangement.spacedBy(FIELD_GAP), content = content) },
    )
}

@Composable
internal fun FieldsCard(shape: Shape, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(FIELDS_PADDING), verticalArrangement = Arrangement.spacedBy(FIELD_GAP), content = content)
    }
}

internal fun CardGroupScope.foldCard(
    icon: ImageVector,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    card("$title-fold") { shape ->
        val haptics = rememberHaptics()
        val turn by animateFloatAsState(if (expanded) HALF_TURN else 0f, LayoutMotion.settle(), label = "fold-card")
        SettingCard(
            icon = icon,
            title = title,
            summary = summary,
            shape = shape,
            onClick = {
                haptics.tick()
                onToggle()
            },
            trailing = { Icon(MaterialSymbols.Rounded.Expand_more, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(turn)) },
        )
    }
    if (expanded) card("$title-fold-content") { shape -> FieldsCard(shape, content = content) }
}

@Composable
internal fun SliderCard(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: @Composable (Float) -> String,
    onCommit: (Float) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    onDrag: (Float) -> Unit = {},
) {
    var dragged by remember(value) { mutableFloatStateOf(value) }
    SettingCard(
        icon = icon,
        title = title,
        modifier = modifier,
        summary = summary,
        shape = shape,
        indentBelow = false,
        trailing = { Text(valueLabel(dragged), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        below = {
            Slider(
                value = dragged,
                onValueChange = {
                    if (it != dragged) onDrag(it)
                    dragged = it
                },
                onValueChangeFinished = { onCommit(dragged) },
                valueRange = range,
                steps = steps,
                enabled = enabled,
            )
        },
    )
}

private const val HALF_TURN = 180f
private val PAGE_SIDE_PADDING = 16.dp
private val PAGE_BOTTOM_PADDING = 32.dp
private const val PICKER_VALUE_LINES = 2
private val FIELDS_TOP_GAP = 4.dp
private val FIELDS_PADDING = 16.dp
private val FIELD_GAP = 12.dp
