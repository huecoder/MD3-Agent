package dev.huecoder.md3agent.ui.navigation

enum class LaunchTarget {
    NEW_CHAT,
    FILES,
    SETTINGS;

    companion object {
        fun parse(name: String?): LaunchTarget? = entries.firstOrNull { it.name == name }
    }
}
