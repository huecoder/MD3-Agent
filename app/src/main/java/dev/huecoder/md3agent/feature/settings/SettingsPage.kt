package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Auto_awesome
import com.composables.icons.materialsymbols.rounded.Chat
import com.composables.icons.materialsymbols.rounded.Dns
import com.composables.icons.materialsymbols.rounded.Extension
import com.composables.icons.materialsymbols.rounded.Face
import com.composables.icons.materialsymbols.rounded.Handyman
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Neurology
import com.composables.icons.materialsymbols.rounded.Notification_settings
import com.composables.icons.materialsymbols.rounded.Notifications_active
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Shield_person
import com.composables.icons.materialsymbols.rounded.Touch_app
import com.composables.icons.materialsymbols.rounded.Tune
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ModeSettings

enum class SettingsSection(@StringRes val title: Int) {
    ASSISTANT(R.string.settings_section_assistant),
    CHAT(R.string.settings_section_chat),
    CAPABILITIES(R.string.settings_section_capabilities),
    SYSTEM(R.string.settings_section_system),
    APP(R.string.settings_section_app),
}

enum class SettingsPage(
    val icon: ImageVector,
    @StringRes val title: Int,
    val section: SettingsSection,
    private val isAvailable: (ModeSettings) -> Boolean = { true },
) {
    MODELS(MaterialSymbols.Rounded.Hub, R.string.settings_models, SettingsSection.ASSISTANT),
    MODE(MaterialSymbols.Rounded.Tune, R.string.settings_mode, SettingsSection.ASSISTANT),
    AGENT(MaterialSymbols.Rounded.Face, R.string.settings_agent, SettingsSection.ASSISTANT),
    MEMORY(MaterialSymbols.Rounded.Neurology, R.string.settings_memory, SettingsSection.ASSISTANT),
    CHAT(MaterialSymbols.Rounded.Chat, R.string.settings_chat, SettingsSection.CHAT),
    APPEARANCE(MaterialSymbols.Rounded.Palette, R.string.settings_appearance, SettingsSection.CHAT),
    VOICE(MaterialSymbols.Rounded.Mic, R.string.settings_voice, SettingsSection.CHAT),
    TOOLS(MaterialSymbols.Rounded.Handyman, R.string.settings_tools, SettingsSection.CAPABILITIES),
    MCP(MaterialSymbols.Rounded.Extension, R.string.settings_mcp, SettingsSection.CAPABILITIES, { it.mcp }),
    PHONE_USE(MaterialSymbols.Rounded.Touch_app, R.string.settings_phone_use, SettingsSection.CAPABILITIES, { it.phoneControl }),
    ACCESS(MaterialSymbols.Rounded.Shield_person, R.string.settings_access, SettingsSection.CAPABILITIES, { it.shellAndFiles }),
    ASSISTANT_INVOKE(MaterialSymbols.Rounded.Auto_awesome, R.string.settings_assistant_invoke, SettingsSection.SYSTEM),
    NOTIFICATIONS(MaterialSymbols.Rounded.Notification_settings, R.string.settings_notifications, SettingsSection.SYSTEM),
    REMINDERS(MaterialSymbols.Rounded.Notifications_active, R.string.settings_reminders, SettingsSection.SYSTEM),
    NETWORK(MaterialSymbols.Rounded.Dns, R.string.settings_network, SettingsSection.APP),
    ABOUT(MaterialSymbols.Rounded.Info, R.string.settings_about, SettingsSection.APP),
    ;

    fun isAvailable(mode: ModeSettings): Boolean = isAvailable.invoke(mode)

    @StringRes
    fun titleFor(mode: ModeSettings): Int = if (this == AGENT && !mode.multipleAgents) R.string.settings_agent_single else title
}
