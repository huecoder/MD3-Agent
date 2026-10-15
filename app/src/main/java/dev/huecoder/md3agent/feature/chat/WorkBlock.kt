package dev.huecoder.md3agent.feature.chat

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
import com.composables.icons.materialsymbols.rounded.Neurology
import com.composables.icons.materialsymbols.rounded.Shield_question
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.tools.ReadScreenTool
import dev.huecoder.md3agent.tools.files.ListFilesTool
import dev.huecoder.md3agent.tools.files.ReadFileTool
import dev.huecoder.md3agent.tools.files.SendFilesTool
import dev.huecoder.md3agent.tools.files.WriteFileTool
import dev.huecoder.md3agent.tools.optionalArray
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.ShowChartTool
import dev.huecoder.md3agent.tools.ShowDiagramTool
import dev.huecoder.md3agent.tools.phone.PhoneActionTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenTool
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool
import dev.huecoder.md3agent.tools.weather.WeatherTool
import dev.huecoder.md3agent.tools.web.ReadWebpageTool
import dev.huecoder.md3agent.tools.web.SearchResults
import dev.huecoder.md3agent.tools.web.WebSearchTool
import dev.huecoder.md3agent.ui.components.ExpandableBlock
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

@Composable
fun WorkBlock(work: ReplyPart.Work, modifier: Modifier = Modifier) {
    val steps = work.steps
    val calls = work.calls
    val current = calls.firstOrNull { it.status == ToolStatus.AWAITING_APPROVAL } ?: calls.firstOrNull { it.status == ToolStatus.RUNNING }
    val single = steps.singleOrNull()
    val isThinking = (steps.last() as? WorkStep.Thought)?.isStreaming == true
    val title = when {
        current != null -> ToolLook.of(current).titleText()
        isThinking -> stringResource(R.string.chat_reasoning_streaming)
        single is WorkStep.Call -> ToolLook.of(single.call).titleText()
        else -> workSummary(work).asString()
    }
    ExpandableBlock(
        title = title,
        modifier = modifier,
        leading = {
            when {
                current != null -> StatusMark(current.status)
                isThinking -> StepIcon(MaterialSymbols.Rounded.Neurology)
                calls.any { it.status == ToolStatus.ERROR } -> StatusMark(ToolStatus.ERROR)
                single is WorkStep.Call -> StepIcon(ToolLook.of(single.call).icon)
                steps.any { it is WorkStep.Thought } -> StepIcon(MaterialSymbols.Rounded.Neurology)
                else -> StepIcon(MaterialSymbols.Rounded.Handyman)
            }
        },
    ) {
        if (single != null) {
            StepDetails(single)
        } else {
            Column { steps.forEachIndexed { index, step -> key(index) { StepRow(step) } } }
        }
    }
}

@Composable
private fun StepRow(step: WorkStep) {
    when (step) {
        is WorkStep.Thought -> ReasoningBlock(step.text, step.isStreaming)
        is WorkStep.Call -> ToolStep(step.call)
    }
}

@Composable
private fun StepDetails(step: WorkStep) {
    when (step) {
        is WorkStep.Thought -> ReasoningText(step.text)
        is WorkStep.Call -> ToolDetails(step.call)
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

private fun workSummary(work: ReplyPart.Work): UiText {
    val calls = work.calls
    if (calls.isEmpty()) return UiText.Resource(R.string.chat_reasoning)
    val actions = UiText.Plural(R.plurals.chat_tool_group_actions, calls.size, listOf(calls.size))
    val base = if (work.steps.any { it is WorkStep.Thought }) UiText.Resource(R.string.chat_work_with_reasoning, listOf(actions)) else actions
    val failed = calls.count { it.status == ToolStatus.ERROR }
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
                PhoneScreenTool.NAME -> UiText.Resource(if (running) R.string.chat_tool_looking_phone else R.string.chat_tool_looked_phone)
                PhoneActionTool.NAME -> UiText.Resource(R.string.chat_tool_phone_action, listOf(phoneStep(args)))
                RunCommandTool.NAME -> UiText.Resource(
                    if (running) R.string.chat_tool_running_command else R.string.chat_tool_command,
                    listOf(args.optionalString("command")?.lineSequence()?.first()?.take(COMMAND_PREVIEW_CHARS).orEmpty()),
                )
                ShowChartTool.NAME -> UiText.Resource(R.string.chat_tool_chart, listOf(args.optionalString("title").orEmpty()))
                ShowDiagramTool.NAME -> UiText.Resource(R.string.chat_tool_diagram, listOf(args.optionalString("title").orEmpty()))
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

        private fun phoneStep(args: JsonObject): String = listOfNotNull(
            args.optionalString("action")?.replace('_', ' '),
            args.optionalString("app") ?: args.optionalString("key") ?: args.optionalString("direction"),
            args.optionalInt("element")?.let { "#$it" },
            args.optionalString("text")?.let { "\"${it.take(COMMAND_PREVIEW_CHARS)}\"" },
        ).joinToString(" ")

        private fun quoted(value: String?): UiText = value?.let { UiText.Resource(R.string.chat_tool_quoted, listOf(it)) } ?: UiText.Raw("")

        private fun fileName(path: String?): String = path?.trimEnd('/')?.substringAfterLast('/')?.ifEmpty { path }.orEmpty()

        private fun hostOf(url: String) = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}
