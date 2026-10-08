package dev.huecoder.md3agent.system.control

enum class SwipeDirection(val key: String) {
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun of(key: String?): SwipeDirection? = entries.firstOrNull { it.key == key }
    }
}

enum class GlobalKey(val key: String) {
    BACK("back"),
    HOME("home"),
    RECENTS("recents"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick_settings"),
    LOCK_SCREEN("lock_screen"),
    SCREENSHOT("screenshot"),
    ;

    companion object {
        fun of(key: String?): GlobalKey? = entries.firstOrNull { it.key == key }
    }
}

sealed interface PhoneAction {
    data class Tap(val element: Int) : PhoneAction
    data class LongPress(val element: Int) : PhoneAction
    data class TapAt(val x: Int, val y: Int) : PhoneAction
    data class Type(val element: Int?, val text: String, val submit: Boolean) : PhoneAction
    data class Scroll(val element: Int?, val direction: SwipeDirection) : PhoneAction
    data class Swipe(val direction: SwipeDirection) : PhoneAction
    data class Press(val key: GlobalKey) : PhoneAction
    data class OpenApp(val name: String) : PhoneAction
    data class Wait(val millis: Long) : PhoneAction
}

sealed interface ActionResult {
    data class Done(val note: String) : ActionResult
    data class Failed(val reason: String) : ActionResult
}
