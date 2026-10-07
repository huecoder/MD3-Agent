package dev.huecoder.md3assistant.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Build
import com.composables.icons.materialsymbols.rounded.Language
import com.composables.icons.materialsymbols.rounded.Partly_cloudy_day
import com.composables.icons.materialsymbols.rounded.Screenshot_monitor
import com.composables.icons.materialsymbols.rounded.Smart_button
import com.composables.icons.materialsymbols.rounded.Travel_explore
import dev.huecoder.md3assistant.tools.ReadScreenTool
import dev.huecoder.md3assistant.tools.ShowUiTool
import dev.huecoder.md3assistant.tools.weather.WeatherTool
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.WebSearchTool

data class ToolInfo(val icon: ImageVector, val title: String, val summary: String)

object ToolCatalog {
    private val known = mapOf(
        WebSearchTool.NAME to ToolInfo(MaterialSymbols.Rounded.Travel_explore, "Поиск в интернете", "Ищет и показывает источники пиллами"),
        ReadWebpageTool.NAME to ToolInfo(MaterialSymbols.Rounded.Language, "Чтение страниц", "Открывает ссылки и читает текст"),
        WeatherTool.NAME to ToolInfo(MaterialSymbols.Rounded.Partly_cloudy_day, "Погода", "Сейчас и прогноз до недели"),
        ReadScreenTool.NAME to ToolInfo(MaterialSymbols.Rounded.Screenshot_monitor, "Чтение экрана", "Текст приложения, поверх которого вызван ассистент"),
        ShowUiTool.NAME to ToolInfo(MaterialSymbols.Rounded.Smart_button, "Кнопки и варианты ответа", "Модель добавляет чипы и кнопки под ответом"),
    )

    fun of(toolName: String): ToolInfo = known[toolName] ?: ToolInfo(MaterialSymbols.Rounded.Build, toolName, "")
}
