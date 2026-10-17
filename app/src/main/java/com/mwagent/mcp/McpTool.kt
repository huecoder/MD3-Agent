package com.mwagent.mcp

import android.util.Base64
import com.mwagent.data.attachments.Attachment
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.conversation.UserPrompts
import com.mwagent.tools.ContextualTool
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolOutcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class McpTool(
    val serverName: String,
    val remote: McpRemoteTool,
    private val client: McpClient,
    private val attachments: AttachmentStore,
    private val prompts: UserPrompts,
) : ContextualTool {
    override val spec = ToolSpec(
        name = toolName(serverName, remote.name),
        description = remote.description.ifBlank { remote.title ?: remote.name } + " (MCP server $serverName)",
        parameters = objectSchema(remote.inputSchema),
    )

    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = TIMEOUT_MS

    val displayName: String get() = remote.title ?: remote.name

    override fun promptNote(settings: ToolSettings): String? =
        client.instructions?.let { "Notes from the MCP server $serverName: ${it.take(MAX_INSTRUCTION_CHARS)}" }

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val result = client.callTool(remote.name, arguments) { method, params ->
            if (method != McpElicitation.METHOD) return@callTool null
            val form = McpElicitation.form(serverName, params)
            McpElicitation.result(form, prompts.ask(context.chatId, form))
        }
        val texts = result.content.filterIsInstance<McpContent.Text>().map { it.text }
        val images = result.content.filterIsInstance<McpContent.Image>().mapIndexedNotNull { index, image -> storeImage(index, image) }
        val text = texts.joinToString("\n\n").ifBlank { if (images.isEmpty()) "The tool returned nothing" else "The tool returned ${images.size} image(s), attached below" }
        return ToolOutcome(text.take(MAX_RESULT_CHARS), isError = result.isError, attachments = images)
    }

    private suspend fun storeImage(index: Int, image: McpContent.Image): Attachment? {
        val bytes = runCatching { Base64.decode(image.base64, Base64.DEFAULT) }.getOrNull() ?: return null
        val extension = image.mimeType.substringAfter('/', DEFAULT_EXTENSION).substringBefore('+')
        return attachments.importBytes("${remote.name}-${index + 1}.$extension", bytes, forModel = true)
    }

    companion object {
        private const val PREFIX = "mcp"
        private const val MAX_NAME_LENGTH = 64
        private const val TIMEOUT_MS = 120_000L
        private const val MAX_RESULT_CHARS = 60_000
        private const val MAX_INSTRUCTION_CHARS = 2_000
        private const val DEFAULT_EXTENSION = "png"
        private val UNSAFE_CHARS = Regex("[^a-zA-Z0-9_-]+")

        fun toolName(serverName: String, remoteName: String): String {
            val server = serverName.replace(UNSAFE_CHARS, "_").trim('_').lowercase().ifEmpty { "server" }
            val tool = remoteName.replace(UNSAFE_CHARS, "_")
            return "${PREFIX}_${server}_$tool".take(MAX_NAME_LENGTH)
        }

        fun isMcpName(name: String) = name.startsWith("${PREFIX}_")

        fun shortName(name: String) = name.removePrefix("${PREFIX}_")

        private fun objectSchema(schema: JsonObject): JsonObject =
            if (schema["type"] == null) JsonObject(schema + ("type" to JsonPrimitive("object"))) else schema
    }
}
