package dev.huecoder.md3assistant.assistant

import android.app.assist.AssistStructure
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenSnapshot(
    val packageName: String?,
    val text: String,
    val screenshot: Bitmap?,
    val capturedAt: Long,
)

class ScreenContextStore(private val clock: () -> Long = System::currentTimeMillis) {
    private val _latest = MutableStateFlow<ScreenSnapshot?>(null)
    val latest: StateFlow<ScreenSnapshot?> = _latest.asStateFlow()

    fun recordStructure(structure: AssistStructure?) {
        val text = structure?.let(ScreenTextExtractor::extract).orEmpty()
        val packageName = structure?.activityComponent?.packageName
        _latest.value = ScreenSnapshot(packageName, text, _latest.value?.screenshot, clock())
    }

    fun recordScreenshot(screenshot: Bitmap?) {
        val previous = _latest.value
        _latest.value = ScreenSnapshot(previous?.packageName, previous?.text.orEmpty(), screenshot, clock())
    }

    fun clear() {
        _latest.value = null
    }
}

object ScreenTextExtractor {
    private const val MAX_CHARS = 12_000

    fun extract(structure: AssistStructure): String {
        val lines = LinkedHashSet<String>()
        for (index in 0 until structure.windowNodeCount) {
            collect(structure.getWindowNodeAt(index).rootViewNode, lines)
        }
        return lines.joinToString("\n").take(MAX_CHARS)
    }

    private fun collect(node: AssistStructure.ViewNode, lines: MutableSet<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(lines::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(lines::add)
        for (child in 0 until node.childCount) collect(node.getChildAt(child), lines)
    }
}
