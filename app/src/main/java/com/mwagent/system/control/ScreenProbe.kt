package com.mwagent.system.control

data class ScreenProbe(val packageName: String?, val fingerprint: Long, val labels: List<String>) {
    fun shows(text: String): Boolean = labels.any { it.contains(text, ignoreCase = true) }
}
