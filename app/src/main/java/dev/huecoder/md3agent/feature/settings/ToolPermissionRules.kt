package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.data.settings.ToolPermission

internal fun matchesSearch(query: String, vararg fields: String): Boolean {
    val haystack = fields.joinToString(" ")
    return query.split(' ').filter { it.isNotBlank() }.all { haystack.contains(it, ignoreCase = true) }
}

internal fun modeBehind(permission: ToolPermission, remembered: ToolPermission?, default: ToolPermission): ToolPermission =
    listOfNotNull(permission, remembered, default).firstOrNull { it != ToolPermission.OFF } ?: ToolPermission.ASK
