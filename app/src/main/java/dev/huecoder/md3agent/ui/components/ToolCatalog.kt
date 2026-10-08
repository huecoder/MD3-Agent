package dev.huecoder.md3agent.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Manage_search
import com.composables.icons.materialsymbols.rounded.Bookmark_remove
import com.composables.icons.materialsymbols.rounded.Edit_note
import com.composables.icons.materialsymbols.rounded.Bookmark_add
import com.composables.icons.materialsymbols.rounded.Bar_chart
import com.composables.icons.materialsymbols.rounded.Build
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Description
import com.composables.icons.materialsymbols.rounded.Edit_document
import com.composables.icons.materialsymbols.rounded.Folder_open
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Perm_media
import com.composables.icons.materialsymbols.rounded.Language
import com.composables.icons.materialsymbols.rounded.Partly_cloudy_day
import com.composables.icons.materialsymbols.rounded.Quiz
import com.composables.icons.materialsymbols.rounded.Screenshot_monitor
import com.composables.icons.materialsymbols.rounded.Smart_button
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Travel_explore
import com.composables.icons.materialsymbols.rounded.Alarm_add
import com.composables.icons.materialsymbols.rounded.Event_upcoming
import com.composables.icons.materialsymbols.rounded.Notification_add
import com.composables.icons.materialsymbols.rounded.Notifications_off
import com.composables.icons.materialsymbols.rounded.Timer
import com.composables.icons.materialsymbols.rounded.Account_tree
import com.composables.icons.materialsymbols.rounded.Forum
import com.composables.icons.materialsymbols.rounded.Article
import com.composables.icons.materialsymbols.rounded.File_save
import com.composables.icons.materialsymbols.rounded.Phone_android
import com.composables.icons.materialsymbols.rounded.Touch_app
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.mcp.McpTool
import dev.huecoder.md3agent.tools.AskUserTool
import dev.huecoder.md3agent.tools.ReadScreenTool
import dev.huecoder.md3agent.tools.ShowChartTool
import dev.huecoder.md3agent.tools.ShowDiagramTool
import dev.huecoder.md3agent.tools.ShowUiTool
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.chats.ExportChatTool
import dev.huecoder.md3agent.tools.chats.ListChatsTool
import dev.huecoder.md3agent.tools.chats.ReadChatTool
import dev.huecoder.md3agent.tools.files.ListFilesTool
import dev.huecoder.md3agent.tools.files.ReadFileTool
import dev.huecoder.md3agent.tools.files.SendFilesTool
import dev.huecoder.md3agent.tools.files.WriteFileTool
import dev.huecoder.md3agent.tools.memory.MemoryDeleteTool
import dev.huecoder.md3agent.tools.memory.MemorySaveTool
import dev.huecoder.md3agent.tools.memory.MemorySearchTool
import dev.huecoder.md3agent.tools.memory.MemoryUpdateTool
import dev.huecoder.md3agent.tools.reminders.CancelReminderTool
import dev.huecoder.md3agent.tools.reminders.ListRemindersTool
import dev.huecoder.md3agent.tools.reminders.SetAlarmTool
import dev.huecoder.md3agent.tools.reminders.SetReminderTool
import dev.huecoder.md3agent.tools.reminders.SetTimerTool
import dev.huecoder.md3agent.tools.phone.PhoneActionTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenTool
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool
import dev.huecoder.md3agent.tools.weather.WeatherTool
import dev.huecoder.md3agent.tools.web.ReadWebpageTool
import dev.huecoder.md3agent.tools.web.WebSearchTool
import dev.huecoder.md3agent.ui.text.UiText

enum class ToolCategory(@StringRes val title: Int) {
    ASSISTANT(R.string.tools_category_assistant),
    MEMORY(R.string.tools_category_memory),
    CHATS(R.string.tools_category_chats),
    WEB(R.string.tools_category_web),
    SCREEN(R.string.tools_category_screen),
    PHONE(R.string.tools_category_phone),
    FILES(R.string.tools_category_files),
    TIME(R.string.tools_category_time),
    SYSTEM(R.string.tools_category_system),
    MCP(R.string.tools_category_mcp),
    OTHER(R.string.tools_category_other),
}

data class ToolInfo(val icon: ImageVector, val title: UiText, val summary: UiText, val category: ToolCategory)

object ToolCatalog {
    private val known = mapOf(
        AskUserTool.NAME to known(ToolCategory.ASSISTANT, MaterialSymbols.Rounded.Quiz, R.string.tools_ask_user_title, R.string.tools_ask_user_summary),
        MemorySaveTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Bookmark_add, R.string.tools_memory_save_title, R.string.tools_memory_save_summary),
        MemoryUpdateTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Edit_note, R.string.tools_memory_update_title, R.string.tools_memory_update_summary),
        MemoryDeleteTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Bookmark_remove, R.string.tools_memory_delete_title, R.string.tools_memory_delete_summary),
        MemorySearchTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Manage_search, R.string.tools_memory_search_title, R.string.tools_memory_search_summary),
        ListChatsTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.Forum, R.string.tools_list_chats_title, R.string.tools_list_chats_summary),
        ReadChatTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.Article, R.string.tools_read_chat_title, R.string.tools_read_chat_summary),
        ExportChatTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.File_save, R.string.tools_export_chat_title, R.string.tools_export_chat_summary),
        WebSearchTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Travel_explore, R.string.tools_web_search_title, R.string.tools_web_search_summary),
        ReadWebpageTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Language, R.string.tools_read_webpage_title, R.string.tools_read_webpage_summary),
        WeatherTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Partly_cloudy_day, R.string.tools_weather_title, R.string.tools_weather_summary),
        ReadScreenTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Screenshot_monitor, R.string.tools_read_screen_title, R.string.tools_read_screen_summary),
        ShowUiTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Smart_button, R.string.tools_show_ui_title, R.string.tools_show_ui_summary),
        ShowDiagramTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Account_tree, R.string.tools_show_diagram_title, R.string.tools_show_diagram_summary),
        ShowChartTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Bar_chart, R.string.tools_show_chart_title, R.string.tools_show_chart_summary),
        PhoneScreenTool.NAME to known(ToolCategory.PHONE, MaterialSymbols.Rounded.Phone_android, R.string.tools_phone_screen_title, R.string.tools_phone_screen_summary),
        PhoneActionTool.NAME to known(ToolCategory.PHONE, MaterialSymbols.Rounded.Touch_app, R.string.tools_phone_action_title, R.string.tools_phone_action_summary),
        RunCommandTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Terminal, R.string.tools_run_command_title, R.string.tools_run_command_summary),
        RunPythonTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Code, R.string.tools_run_python_title, R.string.tools_run_python_summary),
        ListFilesTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Folder_open, R.string.tools_list_files_title, R.string.tools_list_files_summary),
        ReadFileTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Description, R.string.tools_read_file_title, R.string.tools_read_file_summary),
        WriteFileTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Edit_document, R.string.tools_write_file_title, R.string.tools_write_file_summary),
        SendFilesTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Perm_media, R.string.tools_send_files_title, R.string.tools_send_files_summary),
        SetReminderTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Notification_add, R.string.tools_set_reminder_title, R.string.tools_set_reminder_summary),
        ListRemindersTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Event_upcoming, R.string.tools_list_reminders_title, R.string.tools_list_reminders_summary),
        CancelReminderTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Notifications_off, R.string.tools_cancel_reminder_title, R.string.tools_cancel_reminder_summary),
        SetAlarmTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Alarm_add, R.string.tools_set_alarm_title, R.string.tools_set_alarm_summary),
        SetTimerTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Timer, R.string.tools_set_timer_title, R.string.tools_set_timer_summary),
    )

    fun of(toolName: String): ToolInfo = known[toolName] ?: when {
        McpTool.isMcpName(toolName) -> ToolInfo(MaterialSymbols.Rounded.Hub, UiText.Raw(McpTool.shortName(toolName)), UiText.Raw(""), ToolCategory.MCP)
        else -> ToolInfo(MaterialSymbols.Rounded.Build, UiText.Raw(toolName), UiText.Raw(""), ToolCategory.OTHER)
    }

    fun of(tool: Tool): ToolInfo = when (tool) {
        is McpTool -> ToolInfo(
            MaterialSymbols.Rounded.Hub,
            UiText.Raw("${tool.serverName} · ${tool.displayName}"),
            UiText.Raw(tool.remote.description.lineSequence().firstOrNull().orEmpty().take(MAX_SUMMARY_CHARS)),
            ToolCategory.MCP,
        )
        else -> of(tool.spec.name)
    }

    private fun known(category: ToolCategory, icon: ImageVector, @StringRes title: Int, @StringRes summary: Int): ToolInfo =
        ToolInfo(icon, UiText.Resource(title), UiText.Resource(summary), category)

    private const val MAX_SUMMARY_CHARS = 160
}
