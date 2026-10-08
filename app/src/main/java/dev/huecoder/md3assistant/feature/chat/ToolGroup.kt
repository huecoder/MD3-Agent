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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Block
import com.composables.icons.materialsymbols.rounded.Cancel
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Error
import com.composables.icons.materialsymbols.rounded.Handyman
import com.composables.icons.materialsymbols.rounded.Shield_question
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.tools.ReadScreenTool
import dev.huecoder.md3assistant.tools.files.ListFilesTool
import dev.huecoder.md3assistant.tools.files.ReadFileTool
import dev.huecoder.md3assistant.tools.files.SendFilesTool
import dev.huecoder.md3assistant.tools.files.WriteFileTool
import dev.huecoder.md3assistant.tools.optionalArray
import dev.huecoder.md3assistant.tools.optionalString
import dev.huecoder.md3assistant.tools.system.RunCommandTool
import dev.huecoder.md3assistant.tools.system.RunPythonTool
import dev.huecoder.md3assistant.tools.weather.WeatherTool
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.SearchResults
import dev.huecoder.md3assistant.tools.web.WebSearchTool
import dev.huecoder.md3assistant.ui.components.ExpandableBlock
import dev.huecoder.md3assistant.ui.components.ToolCatalog
import dev.huecoder.md3assistant.ui.text.UiText
import dev.huecoder.md3assistant.ui.text.asString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

@Composable
fun ToolGroup(calls: List<ToolCallUi>, modifier: Modifier = Modifier) {
    val current = calls.firstOrNull { it.status == ToolStatus.AWAITING_APPROVAL } ?: calls.firstOrNull { it.status == ToolStatus.RUNNING }
    val single = calls.singleOrNull()
    val title = when {
        current != null -> ToolLook.of(current).titleText()
        single != null -> ToolLook.of(single).titleText()
        else -> groupSummary(calls).asString()
    }
    ExpandableBlock(
        title = title,
        modifier = modifier,
        leading = {
            when {
                current != null -> StatusMark(current.status)
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
    ExpandableBlock(title = look.titleText(), leading = { StatusMark(call.status) }) { ToolDetails(call) }
}

@Composable
private fun ToolDetails(call: ToolCallUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val arguments = readableArguments(call.arguments)
        if (arguments.isNotEmpty()) Detail(label = stringResource(R.string.chat_tool_arguments), text = arguments)
        val viewedImages = call.attachments.filter { it.forModel && it.isImage }
        if (viewedImages.isNotEmpty()) AttachmentStrip(viewedImages)
        when {
            call.result != null -> Detail(label = stringResource(R.string.chat_tool_result), text = ToolLook.readableResult(call))
            call.status == ToolStatus.CANCELLED -> Detail(
                label = stringResource(R.string.chat_tool_result),
                text = stringResource(R.string.chat_tool_cancelled_result),
            )
            call.status == ToolStatus.DENIED -> Detail(
                label = stringResource(R.string.chat_tool_result),
                text = stringResource(R.string.chat_tool_denied_result),
            )
        }
    }
}

private fun groupSummary(calls: List<ToolCallUi>): UiText {
    val failed = calls.count { it.status == ToolStatus.ERROR }
    val base = UiText.Plural(R.plurals.chat_tool_group_actions, calls.size, listOf(calls.size))
    return if (failed == 0) base else UiText.Resource(R.string.chat_tool_group_with_failures, listOf(base, failed))
}

@Composable
private fun StepIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(StepIconSize))
}

@Composable
private fun StatusMark(status: ToolStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        ToolStatus.AWAITING_APPROVAL -> Icon(MaterialSymbols.Rounded.Shield_question, contentDescription = stringResource(R.string.chat_tool_status_awaiting), tint = scheme.tertiary, modifier = Modifier.size(StepIconSize))
        ToolStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(StepIconSize), strokeWidth = 2.dp)
        ToolStatus.DONE -> Icon(MaterialSymbols.Rounded.Check, contentDescription = stringResource(R.string.chat_tool_status_done), tint = scheme.primary, modifier = Modifier.size(StepIconSize))
        ToolStatus.ERROR -> Icon(MaterialSymbols.Rounded.Error, contentDescription = stringResource(R.string.chat_tool_status_error), tint = scheme.error, modifier = Modifier.size(StepIconSize))
        ToolStatus.DENIED -> Icon(MaterialSymbols.Rounded.Block, contentDescription = stringResource(R.string.chat_tool_status_denied), tint = scheme.onSurfaceVariant, modifier = Modifier.size(StepIconSize))
        ToolStatus.CANCELLED -> Icon(MaterialSymbols.Rounded.Cancel, contentDescription = stringResource(R.string.chat_tool_status_cancelled), tint = scheme.onSurfaceVariant, modifier = Modifier.size(StepIconSize))
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

internal fun readableArguments(arguments: JsonObject): String = arguments.entries.joinToString("\n") { (key, value) ->
    "$key: ${(value as? JsonPrimitive)?.content ?: value.toString()}"
}

@Composable
private fun ToolLook.titleText(): String = title.asString().trim()

private data class ToolLook(val icon: ImageVector, val title: UiText) {
    companion object {
        private const val MAX_RESULT_CHARS = 3_000
        private const val COMMAND_PREVIEW_CHARS = 48
        private val json = Json { ignoreUnknownKeys = true }

        fun of(call: ToolCallUi): ToolLook {
            val running = call.status == ToolStatus.RUNNING || call.status == ToolStatus.AWAITING_APPROVAL
            val args = call.arguments
            val title = when (call.name) {
                WebSearchTool.NAME -> UiText.Resource(
                    if (running) R.string.chat_tool_searching else R.string.chat_tool_search,
                    listOf(quoted(args.optionalString("query"))),
                )
                ReadWebpageTool.NAME -> UiText.Resource(
                    if (running) R.string.chat_tool_reading_page else R.string.chat_tool_page,
                    listOf(args.optionalString("url")?.let(::hostOf).orEmpty()),
                )
                WeatherTool.NAME -> UiText.Resource(R.string.chat_tool_weather, listOf(args.optionalString("location").orEmpty()))
                ReadScreenTool.NAME -> UiText.Resource(if (running) R.string.chat_tool_reading_screen else R.string.chat_tool_screen)
                RunCommandTool.NAME -> UiText.Resource(
                    if (running) R.string.chat_tool_running_command else R.string.chat_tool_command,
                    listOf(args.optionalString("command")?.lineSequence()?.first()?.take(COMMAND_PREVIEW_CHARS).orEmpty()),
                )
                RunPythonTool.NAME -> UiText.Resource(if (running) R.string.chat_tool_running_python else R.string.chat_tool_python)
                ListFilesTool.NAME -> UiText.Resource(R.string.chat_tool_list_files, listOf(fileName(args.optionalString("path"))))
                ReadFileTool.NAME -> UiText.Resource(
                    if (running) R.string.chat_tool_reading_file else R.string.chat_tool_file,
                    listOf(fileName(args.optionalString("path"))),
                )
                WriteFileTool.NAME -> UiText.Resource(
                    if (running) R.string.chat_tool_writing_file else R.string.chat_tool_wrote_file,
                    listOf(fileName(args.optionalString("path"))),
                )
                SendFilesTool.NAME -> UiText.Plural(R.plurals.chat_tool_sent_files, args.optionalArray("paths").size, listOf(args.optionalArray("paths").size))
                else -> ToolCatalog.of(call.name).title
            }
            return ToolLook(ToolCatalog.of(call.name).icon, title)
        }

        fun readableResult(call: ToolCallUi): String {
            val raw = call.result.orEmpty()
            val search = if (call.name == WebSearchTool.NAME) runCatching { json.decodeFromString(SearchResults.serializer(), raw) }.getOrNull() else null
            val text = search?.results?.joinToString("\n") { "• ${it.title}\n  ${it.url}" } ?: raw
            return if (text.length > MAX_RESULT_CHARS) text.take(MAX_RESULT_CHARS) + "…" else text
        }

        private fun quoted(value: String?): UiText = value?.let { UiText.Resource(R.string.chat_tool_quoted, listOf(it)) } ?: UiText.Raw("")

        private fun fileName(path: String?): String = path?.trimEnd('/')?.substringAfterLast('/')?.ifEmpty { path }.orEmpty()

        private fun hostOf(url: String) = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}
