package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Auto_awesome
import com.composables.icons.materialsymbols.rounded.Autorenew
import com.composables.icons.materialsymbols.rounded.Data_usage
import com.composables.icons.materialsymbols.rounded.Numbers
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AttachMenuStyle
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val attachMenus = attachMenuOptions()

    SettingsScaffold(stringResource(R.string.settings_chat), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding)) {
            SectionHeader(stringResource(R.string.chat_settings_section_replies), topPadding = 8.dp)
            CardGroup {
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Numbers,
                        title = stringResource(R.string.chat_settings_show_tokens),
                        summary = stringResource(R.string.chat_settings_show_tokens_summary),
                        checked = current.chat.showTokenUsage,
                        onCheckedChange = { value -> viewModel.updateChat { it.copy(showTokenUsage = value) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Data_usage,
                        title = stringResource(R.string.chat_settings_show_context),
                        summary = stringResource(R.string.chat_settings_show_context_summary),
                        checked = current.chat.showContext,
                        onCheckedChange = { value -> viewModel.updateChat { it.copy(showContext = value) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Auto_awesome,
                        title = stringResource(R.string.chat_settings_auto_title),
                        summary = stringResource(R.string.chat_settings_auto_title_summary),
                        checked = current.chat.autoTitle,
                        onCheckedChange = { value -> viewModel.updateChat { it.copy(autoTitle = value) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Autorenew,
                        title = stringResource(R.string.chat_settings_auto_retry),
                        summary = stringResource(R.string.chat_settings_auto_retry_summary),
                        checked = current.chat.autoRetry,
                        onCheckedChange = { value -> viewModel.updateChat { it.copy(autoRetry = value) } },
                        shape = shape,
                    )
                }
            }

            SectionHeader(stringResource(R.string.chat_settings_section_input))
            CardGroup {
                card { shape ->
                    MenuCard(
                        icon = MaterialSymbols.Rounded.Attach_file,
                        title = stringResource(R.string.chat_settings_attach_menu),
                        options = attachMenus,
                        selected = current.chat.attachMenu,
                        onSelect = { style -> viewModel.updateChat { it.copy(attachMenu = style) } },
                        shape = shape,
                    )
                }
            }

            SectionHeader(stringResource(R.string.chat_settings_section_typing))
            CardGroup { typingCards(current.chat.typing) { style -> viewModel.updateChat { it.copy(typing = style) } } }
        }
    }
}

@Composable
private fun attachMenuOptions(): List<DropdownOption<AttachMenuStyle>> =
    AttachMenuStyle.entries.map { DropdownOption(it, stringResource(it.label)) }

@get:StringRes
private val AttachMenuStyle.label: Int
    get() = when (this) {
        AttachMenuStyle.COMPACT -> R.string.chat_attach_menu_compact
        AttachMenuStyle.PANEL -> R.string.chat_attach_menu_panel
    }
