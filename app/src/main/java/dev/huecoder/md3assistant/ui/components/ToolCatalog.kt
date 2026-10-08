package dev.huecoder.md3assistant.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Build
import com.composables.icons.materialsymbols.rounded.Description
import com.composables.icons.materialsymbols.rounded.Edit_document
import com.composables.icons.materialsymbols.rounded.Folder_open
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Perm_media
import com.composables.icons.materialsymbols.rounded.Language
import com.composables.icons.materialsymbols.rounded.Partly_cloudy_day
import com.composables.icons.materialsymbols.rounded.Screenshot_monitor
import com.composables.icons.materialsymbols.rounded.Smart_button
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Travel_explore
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.mcp.McpTool
import dev.huecoder.md3assistant.tools.ReadScreenTool
import dev.huecoder.md3assistant.tools.ShowUiTool
import dev.huecoder.md3assistant.tools.Tool
import dev.huecoder.md3assistant.tools.files.ListFilesTool
import dev.huecoder.md3assistant.tools.files.ReadFileTool
import dev.huecoder.md3assistant.tools.files.SendFilesTool
import dev.huecoder.md3assistant.tools.files.WriteFileTool
import dev.huecoder.md3assistant.tools.system.RunCommandTool
import dev.huecoder.md3assistant.tools.weather.WeatherTool
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.WebSearchTool
import dev.huecoder.md3assistant.ui.text.UiText

enum class ToolCategory(@StringRes val title: Int) {
    WEB(R.string.tools_category_web),
    SCREEN(R.string.tools_category_screen),
    FILES(R.string.tools_category_files),
    SYSTEM(R.string.tools_category_system),
    MCP(R.string.tools_category_mcp),
    OTHER(R.string.tools_category_other),
}

data class ToolInfo(val icon: ImageVector, val title: UiText, val summary: UiText, val category: ToolCategory)

object ToolCatalog {
    private val known = mapOf(
        WebSearchTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Travel_explore, R.string.tools_web_search_title, R.string.tools_web_search_summary),
        ReadWebpageTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Language, R.string.tools_read_webpage_title, R.string.tools_read_webpage_summary),
        WeatherTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Partly_cloudy_day, R.string.tools_weather_title, R.string.tools_weather_summary),
        ReadScreenTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Screenshot_monitor, R.string.tools_read_screen_title, R.string.tools_read_screen_summary),
        ShowUiTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Smart_button, R.string.tools_show_ui_title, R.string.tools_show_ui_summary),
        RunCommandTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Terminal, R.string.tools_run_command_title, R.string.tools_run_command_summary),
        ListFilesTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Folder_open, R.string.tools_list_files_title, R.string.tools_list_files_summary),
        ReadFileTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Description, R.string.tools_read_file_title, R.string.tools_read_file_summary),
        WriteFileTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Edit_document, R.string.tools_write_file_title, R.string.tools_write_file_summary),
        SendFilesTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Perm_media, R.string.tools_send_files_title, R.string.tools_send_files_summary),
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
