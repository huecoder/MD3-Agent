package com.mwagent.data.updates

class AppVersion private constructor(private val parts: List<Int>, val label: String) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val length = maxOf(parts.size, other.parts.size)
        for (index in 0 until length) {
            val difference = parts.getOrElse(index) { 0 } - other.parts.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?) = other is AppVersion && compareTo(other) == 0

    override fun hashCode() = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString() = label

    companion object {
        private val NUMERIC = Regex("""^v?(\d+(?:\.\d+)*)""")

        fun parse(raw: String): AppVersion? {
            val match = NUMERIC.find(raw.trim()) ?: return null
            val numbers = match.groupValues[1]
            return AppVersion(numbers.split('.').map(String::toInt), numbers)
        }
    }
}
