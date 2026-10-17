package dev.huecoder.md3agent.assistant

import android.app.assist.AssistStructure
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScreenSnapshot(
    val packageName: String?,
    val text: String,
    val screenshot: Bitmap?,
    val capturedAt: Long,
    val elements: String? = null,
)

class ScreenContextStore(private val clock: () -> Long = System::currentTimeMillis) {
    private val _latest = MutableStateFlow<ScreenSnapshot?>(null)
    val latest: StateFlow<ScreenSnapshot?> = _latest.asStateFlow()

    fun recordStructure(structure: AssistStructure?) {
        if (structure == null) return
        val text = ScreenTextExtractor.extract(structure)
        val packageName = structure.activityComponent?.packageName
        update { it.copy(packageName = packageName ?: it.packageName, text = text, capturedAt = clock()) }
    }

    fun recordScreenshot(screenshot: Bitmap?) {
        if (screenshot == null) return
        update { it.copy(screenshot = screenshot, capturedAt = clock()) }
    }

    fun recordElements(packageName: String?, elements: String) {
        update { it.copy(packageName = it.packageName ?: packageName, elements = elements) }
    }

    fun clear() {
        _latest.value = null
    }

    fun openedOverNote(appLabel: (packageName: String) -> String?): String? {
        val snapshot = _latest.value ?: return null
        val packageName = snapshot.packageName ?: return null
        val app = appLabel(packageName)?.let { "$it ($packageName)" } ?: packageName
        return buildString {
            append("The user opened you over the app $app, so that app is on the screen right now.")
            when {
                snapshot.elements != null -> append("\nScreen elements at that moment (numbers go stale as soon as the screen changes):\n").append(snapshot.elements)
                snapshot.text.isNotBlank() -> append("\nText on the screen at that moment:\n").append(snapshot.text.take(TEXT_NOTE_CHARS))
            }
        }
    }

    private fun update(change: (ScreenSnapshot) -> ScreenSnapshot) {
        _latest.update { change(it ?: ScreenSnapshot(null, "", null, clock())) }
    }

    private companion object {
        const val TEXT_NOTE_CHARS = 3_000
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
