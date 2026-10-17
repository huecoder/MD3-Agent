package com.mwagent.tools.location

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.location.DeviceLocation
import com.mwagent.system.location.LocationFix
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.objectSchema
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import kotlin.math.roundToInt

class LocationTool(private val location: DeviceLocation) : Tool {

    override val spec = ToolSpec(
        name = NAME,
        description = "The phone's current position: coordinates, accuracy and the nearest street address when one can be found. " +
            "Use it when the user asks where they are or for something near them.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override val defaultPermission: ToolPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!location.hasPermission()) {
            return ToolOutcome.error("Location permission is not granted. Ask the user to allow it in Settings > Device access.")
        }
        if (!location.isEnabled()) {
            return ToolOutcome.error("Location services are turned off on the phone. Ask the user to turn them on.")
        }
        val fix = location.current()
            ?: return ToolOutcome.error("The phone could not get a position. Ask the user to try again outside or with location on.")
        val place = location.placeName(fix)
        return ToolOutcome(LocationReport.format(fix, place))
    }

    companion object {
        const val NAME = "get_location"
    }
}

internal object LocationReport {
    fun format(fix: LocationFix, place: String?): String = buildString {
        appendLine("Place: ${place ?: "address not found"}")
        append("Coordinates: ")
        append(String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude))
        val details = listOfNotNull(
            fix.accuracyMeters?.let { "accuracy ±${it.roundToInt()} m" },
            fix.provider,
            age(fix.ageMillis),
        )
        append(" (").append(details.joinToString(", ")).append(')')
    }

    private fun age(millis: Long): String = when {
        millis < MINUTE_MS -> "fresh"
        millis < HOUR_MS -> "${millis / MINUTE_MS} min old"
        else -> "${millis / HOUR_MS} h old"
    }

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
}
