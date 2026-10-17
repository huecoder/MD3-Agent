package com.mwagent.prompt

import com.mwagent.data.db.MemoryEntity
import com.mwagent.data.settings.AgentProfile
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class SystemPromptRenderer(
    private val deviceInfo: DeviceInfoProvider,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
    private val locale: () -> Locale = Locale::getDefault,
) {
    fun render(agent: AgentProfile, accessNotes: List<String> = emptyList(), instantMemories: List<MemoryEntity> = emptyList()): String {
        val values = variables(agent)
        val prompt = PLACEHOLDER.replace(agent.systemPromptTemplate) { match ->
            values[match.groupValues[1]]?.invoke() ?: match.value
        }
        return buildString {
            append(prompt)
            append("\n\n").append(DISPLAY_NOTES)
            if (instantMemories.isNotEmpty()) {
                append("\n\n").append(MEMORY_HEADER)
                instantMemories.forEach { append("\n- [").append(it.id).append("] ").append(it.title).append(": ").append(it.content) }
            }
            if (accessNotes.isNotEmpty()) {
                append("\n\n").append(ACCESS_HEADER)
                accessNotes.forEach { append("\n- ").append(it) }
            }
        }
    }

    fun contextNote(): String {
        val time = now()
        return "${time.format(DateTimeFormatter.ISO_LOCAL_DATE)} ${time.format(TIME_FORMAT)}, battery ${deviceInfo.batteryDescription()}"
    }

    val supportedVariables: List<String> get() = variables(AgentProfile()).keys.toList()

    private fun variables(agent: AgentProfile): Map<String, () -> String> {
        val time = now()
        val locale = locale()
        return mapOf(
            "agent_name" to { agent.name },
            "date" to { time.format(DateTimeFormatter.ISO_LOCAL_DATE) },
            "time" to { time.format(TIME_FORMAT) },
            "weekday" to { time.dayOfWeek.getDisplayName(TextStyle.FULL, locale) },
            "timezone" to { "${time.zone.id}, UTC${time.offset.id.replace("Z", "+00:00")}" },
            "device" to deviceInfo::deviceName,
            "android" to deviceInfo::androidVersion,
            "battery" to deviceInfo::batteryDescription,
            "locale" to { locale.toLanguageTag() },
        )
    }

    private companion object {
        val DISPLAY_NOTES = listOf(
            "How your replies are displayed:",
            "- Replies are rendered as markdown. To show a picture, put it alone on a line as ![short description](https://direct-link-to-the-image-file). " +
                "Use only direct image links that a tool returned, never made-up ones. Pictures get rounded corners and open full screen when tapped.",
            "- Position and size go into the title: ![description](url \"right 50%\"). Position: left (default), center or right. " +
                "Width: small, medium, large, full or a percentage like 50%. A title with any other word is an ordinary title and its options are ignored.",
            "- Pictures on neighbouring lines with no blank line between them form one group: two to four show as an album grid, more as a side-scrolling slider. " +
                "Add album or slider to the first title to force one, and leave a blank line between pictures to keep them separate.",
        ).joinToString("\n")
        const val MEMORY_HEADER = "Instant memory, what you know about the user (ids in brackets):"
        const val ACCESS_HEADER = "Device access available to you right now:"
        val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")
        val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
