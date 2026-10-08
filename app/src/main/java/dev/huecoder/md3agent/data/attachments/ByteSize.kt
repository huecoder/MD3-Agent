package dev.huecoder.md3agent.data.attachments

import java.util.Locale

object ByteSize {
    private val units = listOf("B", "KB", "MB", "GB")
    private const val STEP = 1024.0

    fun describe(bytes: Long): String {
        var value = bytes.toDouble()
        var unit = 0
        while (value >= STEP && unit < units.lastIndex) {
            value /= STEP
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
