package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.mcp.McpTool
import dev.huecoder.md3agent.tools.files.WriteFileTool
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

    fun requiresUnlock(toolName: String): Boolean = toolName in SENSITIVE_TOOLS || McpTool.isMcpName(toolName)
}
