package dev.huecoder.md3assistant.prompt

import dev.huecoder.md3assistant.data.db.MemoryEntity
import dev.huecoder.md3assistant.data.settings.AgentProfile
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
        const val MEMORY_HEADER = "Instant memory, what you know about the user (ids in brackets):"
        const val ACCESS_HEADER = "Device access available to you right now:"
        val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")
        val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
