package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.mcp.McpTool
import dev.huecoder.md3agent.tools.chats.ExportChatTool
import dev.huecoder.md3agent.tools.chats.ListChatsTool
import dev.huecoder.md3agent.tools.chats.ReadChatTool
import dev.huecoder.md3agent.tools.chats.SearchMessagesTool
import dev.huecoder.md3agent.tools.files.ListFilesTool
import dev.huecoder.md3agent.tools.files.ReadFileTool
import dev.huecoder.md3agent.tools.files.SendFilesTool
import dev.huecoder.md3agent.tools.files.WriteFileTool
import dev.huecoder.md3agent.tools.memory.MemoryDeleteTool
import dev.huecoder.md3agent.tools.memory.MemorySaveTool
import dev.huecoder.md3agent.tools.memory.MemorySearchTool
import dev.huecoder.md3agent.tools.memory.MemoryUpdateTool
import dev.huecoder.md3agent.tools.phone.PhoneActionTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenshotTool
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool

object ApprovalRisk {
    private val SENSITIVE_TOOLS = setOf(
        RunCommandTool.NAME,
        RunPythonTool.NAME,
        WriteFileTool.NAME,
        PhoneScreenTool.NAME,
        PhoneScreenshotTool.NAME,
        PhoneActionTool.NAME,
    )

    private val PRIVATE_TOOLS = setOf(
        ListFilesTool.NAME,
        ReadFileTool.NAME,
        SendFilesTool.NAME,
        MemorySaveTool.NAME,
        MemoryUpdateTool.NAME,
        MemoryDeleteTool.NAME,
        MemorySearchTool.NAME,
        ListChatsTool.NAME,
        ReadChatTool.NAME,
        SearchMessagesTool.NAME,
        ExportChatTool.NAME,
    )

    fun requiresUnlock(toolName: String): Boolean = toolName in SENSITIVE_TOOLS || McpTool.isMcpName(toolName)

    fun requiresConfirmationWhenLocked(toolName: String): Boolean = requiresUnlock(toolName) || toolName in PRIVATE_TOOLS
}
