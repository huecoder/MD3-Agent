package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import dev.huecoder.md3agent.data.settings.CursorShape
import dev.huecoder.md3agent.feature.phoneuse.CursorGlyph
import dev.huecoder.md3agent.feature.phoneuse.CursorMotion
import dev.huecoder.md3agent.feature.phoneuse.drawCursor
import dev.huecoder.md3agent.feature.phoneuse.drawRipple
import dev.huecoder.md3agent.ui.avatar.LocalAgentLook
import dev.huecoder.md3agent.ui.components.boldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Play_arrow
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AgentPlacement
import dev.huecoder.md3agent.data.settings.PhoneUseSettings
import dev.huecoder.md3agent.data.settings.TapSound
import dev.huecoder.md3agent.data.settings.TouchGuard
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PhoneUseSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<PhoneUseSettings?>(null) }
    var saved by remember { mutableStateOf<PhoneUseSettings?>(null) }
    val update: ((PhoneUseSettings) -> PhoneUseSettings) -> Unit = { change -> draft = draft?.let(change) }

    LaunchedEffect(Unit) { container.settings.current().tools.phoneUse.let { saved = it; draft = it } }

    val save = draft?.let { current ->
        SaveAction(isDirty = current != saved, canSave = true) {
            scope.launch {
                container.settings.updateTools { it.copy(phoneUse = current) }
                saved = current
                onBack()
            }
        }
    }

    SettingsScaffold(stringResource(R.string.settings_phone_use), onBack, save = save) { padding ->
        val current = draft ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                LabeledSwitch(
                    title = stringResource(R.string.phone_use_overlay),
                    summary = stringResource(R.string.phone_use_overlay_summary),
                    checked = current.overlay,
                    onCheckedChange = { value -> update { it.copy(overlay = value) } },
                )
            }

            SectionHeader(stringResource(R.string.phone_use_section_screen), topPadding = 12.dp)
            Card {
                ChoiceRow(
                    title = stringResource(R.string.phone_use_guard),
                    choices = GUARD_CHOICES,
                    selected = current.touchGuard,
                    onSelect = { value -> update { it.copy(touchGuard = value) } },
                    hint = stringResource(if (current.touchGuard == TouchGuard.BLOCK) R.string.phone_use_guard_block_hint else R.string.phone_use_guard_observe_hint),
                )
                LabeledSwitch(
                    title = stringResource(R.string.phone_use_keep_awake),
                    summary = stringResource(R.string.phone_use_keep_awake_summary),
                    checked = current.keepAwake,
                    onCheckedChange = { value -> update { it.copy(keepAwake = value) } },
                )
                LabeledSwitch(
                    title = stringResource(R.string.phone_use_dim),
                    summary = stringResource(R.string.phone_use_dim_summary),
                    checked = current.dimScreen,
                    onCheckedChange = { value -> update { it.copy(dimScreen = value) } },
                )
            }

            SectionHeader(stringResource(R.string.phone_use_section_cursor), topPadding = 12.dp)
            Card {
                PercentSlider(
                    title = R.string.phone_use_cursor_size,
                    value = current.cursorSize,
                    range = PhoneUseSettings.MIN_CURSOR_SIZE..PhoneUseSettings.MAX_CURSOR_SIZE,
                    onChange = { value -> update { it.copy(cursorSize = value) } },
                )
                CursorShapePicker(
                    selected = current.cursorShape,
                    sizeScale = current.cursorSize,
                    sound = current.tapSound,
                    volume = current.soundVolume,
                    onSelect = { value -> update { it.copy(cursorShape = value) } },
                )
                ChoiceRow(
                    title = stringResource(R.string.phone_use_agent_placement),
                    choices = PLACEMENT_CHOICES,
                    selected = current.agentPlacement,
                    onSelect = { value -> update { it.copy(agentPlacement = value) } },
                )
                LabeledSwitch(
                    title = stringResource(R.string.phone_use_caption),
                    summary = stringResource(R.string.phone_use_caption_summary),
                    checked = current.cursorCaption,
                    onCheckedChange = { value -> update { it.copy(cursorCaption = value) } },
                )
                LabeledSwitch(
                    title = stringResource(R.string.phone_use_trail),
                    summary = stringResource(R.string.phone_use_trail_summary),
                    checked = current.cursorTrail,
                    onCheckedChange = { value -> update { it.copy(cursorTrail = value) } },
                )
            }

            SectionHeader(stringResource(R.string.phone_use_section_panel), topPadding = 12.dp)
            Card {
                PercentSlider(
                    title = R.string.phone_use_panel_opacity,
                    value = current.panelOpacity,
                    range = PhoneUseSettings.MIN_PANEL_OPACITY..1f,
                    onChange = { value -> update { it.copy(panelOpacity = value) } },
                )
                PercentSlider(
                    title = R.string.phone_use_panel_width,
                    value = current.panelWidth,
                    range = PhoneUseSettings.MIN_PANEL_WIDTH..1f,
                    onChange = { value -> update { it.copy(panelWidth = value) } },
                )
                PercentSlider(
                    title = R.string.phone_use_panel_height,
                    value = current.panelHeight,
                    range = PhoneUseSettings.MIN_PANEL_HEIGHT..PhoneUseSettings.MAX_PANEL_HEIGHT,
                    onChange = { value -> update { it.copy(panelHeight = value) } },
                )
            }

            SectionHeader(stringResource(R.string.phone_use_section_sound), topPadding = 12.dp)
            SoundPicker(
                selected = current.tapSound,
                volume = current.soundVolume,
                onSelect = { value -> update { it.copy(tapSound = value) } },
                onVolume = { value -> update { it.copy(soundVolume = value) } },
            )

            SectionHeader(stringResource(R.string.phone_use_section_python), topPadding = 12.dp)
            Card {
                LabeledSwitch(
                    title = stringResource(R.string.phone_use_python),
                    summary = stringResource(R.string.phone_use_python_summary),
                    checked = current.pythonControl,
                    onCheckedChange = { value -> update { it.copy(pythonControl = value) } },
                )
            }
        }
    }
}

@Composable
private fun CursorShapePicker(selected: CursorShape, sizeScale: Float, sound: TapSound, volume: Float, onSelect: (CursorShape) -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.phone_use_cursor_shape), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CursorShape.entries.forEach { shape ->
                CursorShapeCard(
                    shape = shape,
                    isSelected = shape == selected,
                    sizeScale = sizeScale,
                    pressScale = if (shape == selected) press.value else 1f,
                    ripple = if (shape == selected) ripple.value else 1f,
                    onClick = { onSelect(shape) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FilledTonalButton(
            onClick = {
                scope.launch {
                    ripple.snapTo(1f)
                    press.animateTo(CursorMotion.PRESSED_SCALE, tween(CursorMotion.PRESS_MS, easing = FastOutSlowInEasing))
                    container.phoneSounds.play(sound, volume)
                    launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(CursorMotion.RIPPLE_MS))
                    }
                    press.animateTo(1f, spring(dampingRatio = CursorMotion.RELEASE_DAMPING, stiffness = CursorMotion.RELEASE_STIFFNESS))
                }
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(stringResource(R.string.phone_use_test_click)) }
    }
}

@Composable
private fun CursorShapeCard(
    shape: CursorShape,
    isSelected: Boolean,
    sizeScale: Float,
    pressScale: Float,
    ripple: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = LocalAgentLook.current.tint.boldColors().container
    val density = LocalDensity.current
    val sizePx = with(density) { PREVIEW_BASE_SIZE.toPx() } * sizeScale
    val glyph = remember(shape, sizePx) { CursorGlyph.of(shape, sizePx) }
    val bounds = remember(glyph) { glyph.path.getBounds() }
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        border = if (isSelected) BorderStroke(SELECTED_BORDER, scheme.primary) else null,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
            Canvas(Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)) {
                val tip = Offset(center.x, center.y) - (bounds.center - glyph.tip)
                drawRipple(tip, ripple, tint, sizePx)
                drawCursor(tip, glyph, pressScale, tint)
            }
            Text(
                stringResource(shape.title),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val CursorShape.title: Int
    get() = when (this) {
        CursorShape.BIBATA_MODERN -> R.string.phone_use_shape_modern
        CursorShape.BIBATA_ORIGINAL -> R.string.phone_use_shape_original
        CursorShape.ROUNDED -> R.string.phone_use_shape_rounded
    }

private val PREVIEW_BASE_SIZE = 28.dp
private val PREVIEW_HEIGHT = 96.dp
private val SELECTED_BORDER = 2.dp

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun PercentSlider(@StringRes title: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val haptics = rememberHaptics()
    var dragged by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.phone_use_percent, (dragged * PERCENT).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragged,
            onValueChange = { next ->
                if ((next * PERCENT).roundToInt() != (dragged * PERCENT).roundToInt()) haptics.preview(next)
                dragged = next
            },
            onValueChangeFinished = { onChange(dragged) },
            valueRange = range,
        )
    }
}

@Composable
private fun SoundPicker(selected: TapSound, volume: Float, onSelect: (TapSound) -> Unit, onVolume: (Float) -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        TapSound.entries.forEachIndexed { index, sound ->
            SegmentedListItem(
                onClick = {
                    onSelect(sound)
                    container.phoneSounds.play(sound, volume)
                },
                selected = sound == selected,
                shapes = ListItemDefaults.segmentedShapes(index, TapSound.entries.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                leadingContent = { RadioButton(selected = sound == selected, onClick = null) },
                trailingContent = if (sound == TapSound.OFF) null else {
                    {
                        IconButton(onClick = { container.phoneSounds.play(sound, volume) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(MaterialSymbols.Rounded.Play_arrow, contentDescription = stringResource(R.string.phone_use_sound_preview))
                        }
                    }
                },
            ) { Text(stringResource(sound.title), style = MaterialTheme.typography.titleMedium) }
        }
        Card {
            PercentSlider(R.string.phone_use_sound_volume, volume, VOLUME_RANGE, onVolume)
        }
    }
}

private val TapSound.title: Int
    get() = when (this) {
        TapSound.OFF -> R.string.phone_use_sound_off
        TapSound.SOFT_A -> R.string.phone_use_sound_soft_a
        TapSound.SOFT_B -> R.string.phone_use_sound_soft_b
        TapSound.MOUSE -> R.string.phone_use_sound_mouse
    }

private val GUARD_CHOICES = listOf(
    Choice(TouchGuard.BLOCK, R.string.phone_use_guard_block),
    Choice(TouchGuard.OBSERVE, R.string.phone_use_guard_observe),
)

private val PLACEMENT_CHOICES = listOf(
    Choice(AgentPlacement.BESIDE_CURSOR, R.string.phone_use_placement_beside),
    Choice(AgentPlacement.ON_CURSOR, R.string.phone_use_placement_on),
    Choice(AgentPlacement.HIDDEN, R.string.phone_use_placement_hidden),
)

private val VOLUME_RANGE = 0.1f..1f
private const val PERCENT = 100
