package com.mwagent.tools

import android.util.Log
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolCall
import com.mwagent.llm.ToolSpec
import com.mwagent.net.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException

interface ToolSource {
    fun tools(): List<Tool>

    suspend fun awaitReady()
}

class ToolRegistry(private val builtIn: List<Tool>, private val sources: List<ToolSource> = emptyList()) {
    private val json = Json { ignoreUnknownKeys = true }
    private val builtInByName = builtIn.associateBy { it.spec.name }

    init {
        require(builtInByName.size == builtIn.size) { "Tool names must be unique" }
    }

    val all: List<Tool> get() = builtIn + connected().filter { it.spec.name !in builtInByName }

    fun specs(settings: ToolSettings): List<ToolSpec> = all.filter { permission(it, settings) != ToolPermission.OFF }.map { it.spec }

    fun permission(tool: Tool, settings: ToolSettings): ToolPermission = settings.permission(tool.spec.name, tool.defaultPermission)

    fun permission(toolName: String, settings: ToolSettings): ToolPermission =
        find(toolName)?.let { permission(it, settings) } ?: ToolPermission.OFF

    fun promptNotes(settings: ToolSettings): List<String> =
        all.filter { permission(it, settings) != ToolPermission.OFF }.mapNotNull { it.promptNote(settings) }.distinct()

    fun endsTurn(call: ToolCall): Boolean = find(call.name)?.endsTurn == true

    suspend fun run(call: ToolCall, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val tool = find(call.name)?.takeIf { permission(it, settings) != ToolPermission.OFF }
            ?: return ToolOutcome.error("Tool \"${call.name}\" is not available")
        val arguments = parseArguments(call.arguments)
            ?: return ToolOutcome.error("Arguments are not a JSON object: ${call.arguments.take(ARGUMENT_PREVIEW_CHARS)}")
        return try {
            withTimeout(tool.timeoutMs) { tool.execute(arguments, settings, context) }
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
        } catch (e: Exception) {
            Log.w(TAG, "Tool ${call.name} failed", e)
            ToolOutcome.error("Tool failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun awaitReady() = sources.forEach { it.awaitReady() }

    private fun connected(): List<Tool> = sources.flatMap { it.tools() }

    fun find(name: String): Tool? = builtInByName[name] ?: connected().firstOrNull { it.spec.name == name }

    private fun parseArguments(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) as? JsonObject }.getOrNull()

    private companion object {
        const val TAG = "ToolRegistry"
        const val MS_PER_SECOND = 1_000L
        const val ARGUMENT_PREVIEW_CHARS = 200
    }
}
