package dev.huecoder.md3assistant.prompt

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
    fun render(agent: AgentProfile, accessNotes: List<String> = emptyList()): String {
        val values = variables(agent)
        val prompt = PLACEHOLDER.replace(agent.systemPromptTemplate) { match ->
            values[match.groupValues[1]]?.invoke() ?: match.value
        }
        if (accessNotes.isEmpty()) return prompt
        return prompt + "\n\n" + ACCESS_HEADER + accessNotes.joinToString("") { "\n- $it" }
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
        const val ACCESS_HEADER = "Device access available to you right now:"
        val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")
        val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
