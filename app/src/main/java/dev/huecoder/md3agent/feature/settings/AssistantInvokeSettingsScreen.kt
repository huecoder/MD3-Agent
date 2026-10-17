package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Auto_awesome
import com.composables.icons.materialsymbols.rounded.Face
import com.composables.icons.materialsymbols.rounded.Line_weight
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Shield_question
import com.composables.icons.materialsymbols.rounded.Timer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.data.settings.AssistantGlow
import dev.huecoder.md3agent.data.settings.AssistantLayout
import dev.huecoder.md3agent.data.settings.AssistantSettings
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import kotlin.math.roundToInt

@Composable
fun AssistantInvokeSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val update: ((AssistantSettings) -> AssistantSettings) -> Unit = { change -> viewModel.updateAssistant(change) }

    SettingsScaffold(stringResource(R.string.settings_assistant_invoke), onBack) { padding ->
        val current = settings?.assistant ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding)) {
            InvokePreview(current, Modifier.padding(top = 8.dp))

            SectionHeader(stringResource(R.string.assistant_invoke_section_look))
            CardGroup {
                card("layout") { shape ->
                    SettingCard(
                        icon = MaterialSymbols.Rounded.Auto_awesome,
                        title = stringResource(R.string.assistant_invoke_layout),
                        summary = stringResource(if (current.layout == AssistantLayout.COMPACT) R.string.assistant_invoke_layout_compact_hint else R.string.assistant_invoke_layout_panel_hint),
                        shape = shape,
                        indentBelow = false,
                        below = { ChoicePill(LAYOUT_CHOICES, current.layout, { value -> update { it.copy(layout = value) } }, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
                    )
                }
                if (current.layout == AssistantLayout.COMPACT) {
                    card("orb") { shape ->
                        SwitchCard(
                            icon = MaterialSymbols.Rounded.Face,
                            title = stringResource(R.string.assistant_invoke_orb),
                            summary = stringResource(R.string.assistant_invoke_orb_hint),
                            checked = current.showOrb,
                            onCheckedChange = { value -> update { it.copy(showOrb = value) } },
                            shape = shape,
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.assistant_invoke_section_glow))
            CardGroup {
                card("glow") { shape ->
                    SettingCard(
                        icon = MaterialSymbols.Rounded.Palette,
                        title = stringResource(R.string.assistant_invoke_glow),
                        summary = stringResource(R.string.assistant_invoke_glow_hint),
                        shape = shape,
                        indentBelow = false,
                        below = { ChoicePill(GLOW_CHOICES, current.glow, { value -> update { it.copy(glow = value) } }, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
                    )
                }
                if (current.glow != AssistantGlow.OFF) {
                    card("grid") { shape ->
                        SwitchCard(
                            icon = MaterialSymbols.Rounded.Auto_awesome,
                            title = stringResource(R.string.assistant_invoke_grid),
                            summary = stringResource(R.string.assistant_invoke_grid_hint),
                            checked = current.glowGrid,
                            onCheckedChange = { value -> update { it.copy(glowGrid = value) } },
                            shape = shape,
                        )
                    }
                    card("thickness") { shape ->
                        SliderCard(
                            icon = MaterialSymbols.Rounded.Line_weight,
                            title = stringResource(R.string.assistant_invoke_thickness),
                            value = current.glowThickness,
                            range = AssistantSettings.MIN_GLOW_THICKNESS..AssistantSettings.MAX_GLOW_THICKNESS,
                            valueLabel = { stringResource(R.string.assistant_invoke_percent, (it * PERCENT).roundToInt()) },
                            onCommit = { value -> update { it.copy(glowThickness = value) } },
                            shape = shape,
                        )
                    }
                    card("duration") { shape ->
                        SliderCard(
                            icon = MaterialSymbols.Rounded.Timer,
                            title = stringResource(R.string.assistant_invoke_duration),
                            value = current.glowSeconds,
                            range = AssistantSettings.MIN_GLOW_SECONDS..AssistantSettings.MAX_GLOW_SECONDS,
                            valueLabel = { stringResource(R.string.assistant_invoke_seconds, it) },
                            onCommit = { value -> update { it.copy(glowSeconds = value.roundedToTenth()) } },
                            shape = shape,
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.assistant_invoke_section_system))
            CardGroup {
                card("system") { shape ->
                    SettingCard(
                        icon = MaterialSymbols.Rounded.Shield_question,
                        title = stringResource(R.string.assistant_invoke_system_title),
                        summary = stringResource(R.string.assistant_invoke_system_hint),
                        shape = shape,
                        indentBelow = false,
                        below = {
                            FilledTonalButton(onClick = { AssistantRole.openSystemPicker(context) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.access_action_open_settings))
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun Float.roundedToTenth(): Float = (this * TENTHS_PER_UNIT).roundToInt() / TENTHS_PER_UNIT

private const val PERCENT = 100
private const val TENTHS_PER_UNIT = 10f

private val LAYOUT_CHOICES = listOf(
    Choice(AssistantLayout.PANEL, R.string.assistant_invoke_layout_panel),
    Choice(AssistantLayout.COMPACT, R.string.assistant_invoke_layout_compact),
)

private val GLOW_CHOICES = listOf(
    Choice(AssistantGlow.OFF, R.string.assistant_invoke_glow_off),
    Choice(AssistantGlow.ACCENT, R.string.assistant_invoke_glow_accent),
    Choice(AssistantGlow.RAINBOW, R.string.assistant_invoke_glow_rainbow),
)
