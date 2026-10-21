package dev.huecoder.md3agent.feature.mode

import dev.huecoder.md3agent.data.settings.ModeSettings
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.tools.phone.PhoneActionTool
import dev.huecoder.md3agent.tools.phone.PhoneHandoffTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenshotTool
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool

private val PHONE_CONTROL_TOOLS = setOf(PhoneScreenTool.NAME, PhoneScreenshotTool.NAME, PhoneActionTool.NAME, PhoneHandoffTool.NAME)
private val SHELL_AND_FILES_TOOLS = setOf(RunCommandTool.NAME, RunPythonTool.NAME)

fun ToolSettings.followingMode(previous: ModeSettings, current: ModeSettings): ToolSettings =
    following(PHONE_CONTROL_TOOLS, previous.phoneControl, current.phoneControl)
        .following(SHELL_AND_FILES_TOOLS, previous.shellAndFiles, current.shellAndFiles)

private fun ToolSettings.following(tools: Set<String>, wasOn: Boolean, isOn: Boolean): ToolSettings = when {
    wasOn == isOn -> this
    isOn -> copy(permissions = permissions.filterNot { (name, permission) -> name in tools && permission == ToolPermission.OFF })
    else -> copy(permissions = permissions + tools.filter { it !in permissions }.associateWith { ToolPermission.OFF })
}
