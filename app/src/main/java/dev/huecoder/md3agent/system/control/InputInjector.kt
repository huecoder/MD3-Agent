package dev.huecoder.md3agent.system.control

import dev.huecoder.md3agent.system.shell.Shell

class InputInjector(private val shell: suspend () -> Shell?) {
    suspend fun tap(x: Int, y: Int): Boolean = run("input tap $x $y")

    suspend fun longPress(x: Int, y: Int, durationMs: Long): Boolean = run("input swipe $x $y $x $y $durationMs")

    suspend fun swipe(from: ScreenPoint, to: ScreenPoint, durationMs: Long): Boolean =
        run("input swipe ${from.x} ${from.y} ${to.x} ${to.y} $durationMs")

    suspend fun key(code: Int): Boolean = run("input keyevent $code")

    private suspend fun run(command: String): Boolean {
        val active = shell() ?: return false
        return runCatching { active.run(command, TIMEOUT_MS).exitCode == 0 }.getOrDefault(false)
    }

    companion object {
        const val KEYCODE_ENTER = 66
        private const val TIMEOUT_MS = 5_000L
    }
}
