package dev.huecoder.md3agent.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Swipe_left
import com.composables.icons.materialsymbols.rounded.Swipe_right
import com.composables.icons.materialsymbols.rounded.Translate
import com.composables.icons.materialsymbols.rounded.Tune
import com.composables.icons.materialsymbols.rounded.Vibration
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.InterfaceSettings
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.locale.AppLanguage
import dev.huecoder.md3agent.ui.locale.AppLocale
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val swipes = swipeOptions()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val languages = remember { AppLanguage.entries.map { DropdownOption(it, it.nativeName) } }

    SettingsScaffold(stringResource(R.string.settings_appearance), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding)) {
            SectionHeader(stringResource(R.string.appearance_section_language), topPadding = 8.dp)
            CardGroup {
                card { shape ->
                    MenuCard(
                        icon = MaterialSymbols.Rounded.Translate,
                        title = stringResource(R.string.appearance_language),
                        options = languages,
                        selected = AppLocale.current(context),
                        onSelect = { language -> activity?.let { AppLocale.apply(it, language) } },
                        shape = shape,
                    )
                }
            }

            SectionHeader(stringResource(R.string.appearance_section_chat_list))
            CardGroup {
                card { shape ->
                    MenuCard(
                        icon = MaterialSymbols.Rounded.Swipe_right,
                        title = stringResource(R.string.appearance_swipe_right),
                        options = swipes,
                        selected = current.ui.swipeRight,
                        onSelect = { action -> viewModel.updateInterface { it.copy(swipeRight = action) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    MenuCard(
                        icon = MaterialSymbols.Rounded.Swipe_left,
                        title = stringResource(R.string.appearance_swipe_left),
                        options = swipes,
                        selected = current.ui.swipeLeft,
                        onSelect = { action -> viewModel.updateInterface { it.copy(swipeLeft = action) } },
                        shape = shape,
                    )
                }
            }

            SectionHeader(stringResource(R.string.appearance_section_feedback))
            CardGroup {
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Vibration,
                        title = stringResource(R.string.appearance_haptics),
                        summary = stringResource(R.string.appearance_haptics_summary),
                        checked = current.ui.haptics,
                        onCheckedChange = { value -> viewModel.updateInterface { it.copy(haptics = value) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    SliderCard(
                        icon = MaterialSymbols.Rounded.Tune,
                        title = stringResource(R.string.appearance_haptic_strength),
                        value = current.ui.hapticStrength,
                        range = InterfaceSettings.MIN_HAPTIC_STRENGTH..1f,
                        steps = HAPTIC_STEPS,
                        enabled = current.ui.haptics,
                        valueLabel = { stringResource(R.string.appearance_haptic_strength_value, (it * PERCENT).roundToInt()) },
                        onDrag = { level -> haptics.preview(level) },
                        onCommit = { value -> viewModel.updateInterface { it.copy(hapticStrength = value) } },
                        shape = shape,
                    )
                }
            }
        }
    }
}

private const val PERCENT = 100
private const val HAPTIC_STEPS = 7
