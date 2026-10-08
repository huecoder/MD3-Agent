package dev.huecoder.md3assistant.tools

import dev.huecoder.md3assistant.data.settings.ToolPermission
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolCall
import dev.huecoder.md3assistant.llm.ToolSpec
import dev.huecoder.md3assistant.net.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException

class ToolRegistry(private val tools: List<Tool>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val byName = tools.associateBy { it.spec.name }

    init {
        require(byName.size == tools.size) { "Tool names must be unique" }
    }

    val all: List<Tool> get() = tools

    fun specs(settings: ToolSettings): List<ToolSpec> = tools.filter { permission(it, settings) != ToolPermission.OFF }.map { it.spec }

    fun permission(tool: Tool, settings: ToolSettings): ToolPermission = settings.permission(tool.spec.name, tool.defaultPermission)

    fun permission(toolName: String, settings: ToolSettings): ToolPermission =
        byName[toolName]?.let { permission(it, settings) } ?: ToolPermission.OFF

    fun endsTurn(call: ToolCall): Boolean = byName[call.name]?.endsTurn == true

    suspend fun run(call: ToolCall, settings: ToolSettings): ToolOutcome {
        val tool = byName[call.name]?.takeIf { permission(it, settings) != ToolPermission.OFF }
            ?: return ToolOutcome.error("Tool \"${call.name}\" is not available")
        val arguments = parseArguments(call.arguments)
            ?: return ToolOutcome.error("Arguments are not a JSON object: ${call.arguments.take(ARGUMENT_PREVIEW_CHARS)}")
        return try {
            withTimeout(tool.timeoutMs) { tool.execute(arguments, settings) }
        } catch (e: TimeoutCancellationException) {
            ToolOutcome.error("Tool did not respond within ${tool.timeoutMs / MS_PER_SECOND} s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolArgumentException) {
            ToolOutcome.error(e.message.orEmpty())
        } catch (e: HttpStatusException) {
            ToolOutcome.error("Service responded with an error: ${e.message}")
        } catch (e: IOException) {
            ToolOutcome.error("Network error: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: SerializationException) {
            ToolOutcome.error("Service returned an unexpected response: ${e.message}")
        } catch (e: IllegalStateException) {
            ToolOutcome.error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun parseArguments(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject }.getOrNull()

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val ARGUMENT_PREVIEW_CHARS = 200
    }
}
