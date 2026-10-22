package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import dev.huecoder.md3agent.R

data class SettingsItem(val page: SettingsPage, @StringRes val title: Int, @StringRes val section: Int? = null)

val SETTINGS_ITEMS: List<SettingsItem> = listOf(
    SettingsItem(SettingsPage.MODELS, R.string.settings_add_provider, R.string.settings_section_providers),

    SettingsItem(SettingsPage.MODE, R.string.mode_phone_control, R.string.mode_switches),
    SettingsItem(SettingsPage.MODE, R.string.mode_shell_and_files, R.string.mode_switches),
    SettingsItem(SettingsPage.MODE, R.string.mode_mcp, R.string.mode_switches),
    SettingsItem(SettingsPage.MODE, R.string.mode_multiple_agents, R.string.mode_switches),
    SettingsItem(SettingsPage.MODE, R.string.mode_retake),
    SettingsItem(SettingsPage.MODE, R.string.mode_onboarding),

    SettingsItem(SettingsPage.MEMORY, R.string.memory_section_search),

    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_header_style, R.string.chat_settings_section_header),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_context_display, R.string.chat_settings_section_header),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_show_tokens, R.string.chat_settings_section_replies),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_show_context, R.string.chat_settings_section_replies),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_auto_title, R.string.chat_settings_section_replies),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_auto_retry, R.string.chat_settings_section_replies),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_attach_menu, R.string.chat_settings_section_input),
    SettingsItem(SettingsPage.CHAT, R.string.chat_settings_section_typing),

    SettingsItem(SettingsPage.APPEARANCE, R.string.appearance_language, R.string.appearance_section_language),
    SettingsItem(SettingsPage.APPEARANCE, R.string.appearance_swipe_right, R.string.appearance_section_chat_list),
    SettingsItem(SettingsPage.APPEARANCE, R.string.appearance_swipe_left, R.string.appearance_section_chat_list),
    SettingsItem(SettingsPage.APPEARANCE, R.string.appearance_haptics, R.string.appearance_section_feedback),
    SettingsItem(SettingsPage.APPEARANCE, R.string.appearance_haptic_strength, R.string.appearance_section_feedback),

    SettingsItem(SettingsPage.VOICE, R.string.voice_settings_add_provider, R.string.voice_settings_section_providers),
    SettingsItem(SettingsPage.VOICE, R.string.voice_settings_transcription_provider, R.string.dictation_settings_section),
    SettingsItem(SettingsPage.VOICE, R.string.dictation_settings_language, R.string.dictation_settings_section),
    SettingsItem(SettingsPage.VOICE, R.string.voice_settings_invoke, R.string.dictation_settings_section),
    SettingsItem(SettingsPage.VOICE, R.string.voice_settings_speak, R.string.voice_settings_section_output),
    SettingsItem(SettingsPage.VOICE, R.string.voice_settings_engine, R.string.voice_settings_section_output),
    SettingsItem(SettingsPage.VOICE, R.string.voice_settings_speech_provider, R.string.voice_settings_section_output),

    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_overlay),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_start_glow),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_help_sound),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_guard, R.string.phone_use_section_screen),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_keep_awake, R.string.phone_use_section_screen),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_dim, R.string.phone_use_section_screen),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_cursor_size, R.string.phone_use_section_cursor),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_cursor_shape, R.string.phone_use_section_cursor),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_agent_placement, R.string.phone_use_section_cursor),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_caption, R.string.phone_use_section_cursor),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_trail, R.string.phone_use_section_cursor),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_panel_opacity, R.string.phone_use_section_panel),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_panel_width, R.string.phone_use_section_panel),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_panel_height, R.string.phone_use_section_panel),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_section_sound),
    SettingsItem(SettingsPage.PHONE_USE, R.string.phone_use_python, R.string.phone_use_section_python),

    SettingsItem(SettingsPage.ACCESS, R.string.permission_assistant, R.string.access_section_permissions),
    SettingsItem(SettingsPage.ACCESS, R.string.permission_microphone, R.string.access_section_permissions),
    SettingsItem(SettingsPage.ACCESS, R.string.permission_notifications, R.string.access_section_permissions),
    SettingsItem(SettingsPage.ACCESS, R.string.permission_background_work, R.string.access_section_permissions),
    SettingsItem(SettingsPage.ACCESS, R.string.permission_phone_control, R.string.access_section_permissions),
    SettingsItem(SettingsPage.ACCESS, R.string.access_section_shell),
    SettingsItem(SettingsPage.ACCESS, R.string.access_section_files),

    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_layout, R.string.assistant_invoke_section_look),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_orb, R.string.assistant_invoke_section_look),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_glow, R.string.assistant_invoke_section_glow),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_grid, R.string.assistant_invoke_section_glow),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_thickness, R.string.assistant_invoke_section_glow),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_duration, R.string.assistant_invoke_section_glow),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_lock_title, R.string.assistant_invoke_section_lock),
    SettingsItem(SettingsPage.ASSISTANT_INVOKE, R.string.assistant_invoke_system_title, R.string.assistant_invoke_section_system),

    SettingsItem(SettingsPage.NOTIFICATIONS, R.string.notifications_live, R.string.notifications_section_live),
    SettingsItem(SettingsPage.NOTIFICATIONS, R.string.notifications_chip, R.string.notifications_section_live),
    SettingsItem(SettingsPage.NOTIFICATIONS, R.string.notifications_approvals, R.string.notifications_section_approvals),
    SettingsItem(SettingsPage.NOTIFICATIONS, R.string.notifications_done, R.string.notifications_section_results),
    SettingsItem(SettingsPage.NOTIFICATIONS, R.string.notifications_lock, R.string.notifications_section_lock),

    SettingsItem(SettingsPage.NETWORK, R.string.network_doh_title),
    SettingsItem(SettingsPage.NETWORK, R.string.network_doh_server, R.string.network_doh_title),

    SettingsItem(SettingsPage.ABOUT, R.string.about_check_updates),
    SettingsItem(SettingsPage.ABOUT, R.string.about_source),
    SettingsItem(SettingsPage.ABOUT, R.string.about_license),
)
