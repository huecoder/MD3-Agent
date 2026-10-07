package dev.huecoder.md3assistant.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Cancel
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Error
import com.composables.icons.materialsymbols.rounded.Handyman
import dev.huecoder.md3assistant.tools.ReadScreenTool
import dev.huecoder.md3assistant.tools.optionalString
import dev.huecoder.md3assistant.tools.weather.WeatherTool
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.SearchResults
import dev.huecoder.md3assistant.tools.web.WebSearchTool
import dev.huecoder.md3assistant.ui.components.ExpandableBlock
import dev.huecoder.md3assistant.ui.components.RussianPlural
import dev.huecoder.md3assistant.ui.components.ToolCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

@Composable
fun ToolGroup(calls: List<ToolCallUi>, modifier: Modifier = Modifier) {
    val current = calls.firstOrNull { it.status == ToolStatus.RUNNING }
    val single = calls.singleOrNull()
    val title = when {
        current != null -> ToolLook.of(current).title
        single != null -> ToolLook.of(single).title
        else -> groupSummary(calls)
    }
    ExpandableBlock(
        title = title,
        modifier = modifier,
        leading = {
            when {
                current != null -> StatusMark(ToolStatus.RUNNING)
                calls.any { it.status == ToolStatus.ERROR } -> StatusMark(ToolStatus.ERROR)
                else -> StepIcon(single?.let { ToolLook.of(it).icon } ?: MaterialSymbols.Rounded.Handyman)
            }
        },
    ) {
        if (single != null) {
            ToolDetails(single)
        } else {
            Column { calls.forEach { call -> key(call.id) { ToolStep(call) } } }
        }
    }
}

@Composable
private fun ToolStep(call: ToolCallUi) {
    val look = ToolLook.of(call)
    ExpandableBlock(title = look.title, leading = { StatusMark(call.status) }) { ToolDetails(call) }
}

@Composable
private fun ToolDetails(call: ToolCallUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val arguments = call.arguments.entries.joinToString("\n") { (key, value) ->
            "$key: ${(value as? JsonPrimitive)?.content ?: value.toString()}"
        }
        if (arguments.isNotEmpty()) Detail(label = "Аргументы", text = arguments)
        when {
            call.result != null -> Detail(label = "Результат", text = ToolLook.readableResult(call))
            call.status == ToolStatus.CANCELLED -> Detail(label = "Результат", text = "Не выполнен: ответ был остановлен")
        }
    }
}

private fun groupSummary(calls: List<ToolCallUi>): String {
    val failed = calls.count { it.status == ToolStatus.ERROR }
    val base = "${calls.size} ${RussianPlural.of(calls.size, "действие", "действия", "действий")}"
    return if (failed == 0) base else "$base · $failed с ошибкой"
}

@Composable
private fun StepIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(StepIconSize))
}

@Composable
private fun StatusMark(status: ToolStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        ToolStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(StepIconSize), strokeWidth = 2.dp)
        ToolStatus.DONE -> Icon(MaterialSymbols.Rounded.Check, contentDescription = "Готово", tint = scheme.primary, modifier = Modifier.size(StepIconSize))
        ToolStatus.ERROR -> Icon(MaterialSymbols.Rounded.Error, contentDescription = "Ошибка", tint = scheme.error, modifier = Modifier.size(StepIconSize))
        ToolStatus.CANCELLED -> Icon(MaterialSymbols.Rounded.Cancel, contentDescription = "Отменён", tint = scheme.onSurfaceVariant, modifier = Modifier.size(StepIconSize))
    }
}

@Composable
private fun Detail(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal val StepIconSize = 16.dp

private data class ToolLook(val icon: ImageVector, val title: String) {
    companion object {
        private const val MAX_RESULT_CHARS = 3_000
        private val json = Json { ignoreUnknownKeys = true }

        fun of(call: ToolCallUi): ToolLook {
            val running = call.status == ToolStatus.RUNNING
            val args = call.arguments
            val title = when (call.name) {
                WebSearchTool.NAME -> (if (running) "Ищу " else "Поиск ") + quoted(args.optionalString("query"))
                ReadWebpageTool.NAME -> (if (running) "Читаю " else "Страница ") + (args.optionalString("url")?.let(::hostOf) ?: "")
                WeatherTool.NAME -> "Погода " + (args.optionalString("location") ?: "")
                ReadScreenTool.NAME -> if (running) "Читаю экран" else "Экран"
                else -> call.name
            }
            return ToolLook(ToolCatalog.of(call.name).icon, title.trim())
        }

        fun readableResult(call: ToolCallUi): String {
            val raw = call.result.orEmpty()
            val search = if (call.name == WebSearchTool.NAME) runCatching { json.decodeFromString(SearchResults.serializer(), raw) }.getOrNull() else null
            val text = search?.results?.joinToString("\n") { "• ${it.title}\n  ${it.url}" } ?: raw
            return if (text.length > MAX_RESULT_CHARS) text.take(MAX_RESULT_CHARS) + "…" else text
        }

        private fun quoted(value: String?) = value?.let { "«$it»" }.orEmpty()

        private fun hostOf(url: String) = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}
