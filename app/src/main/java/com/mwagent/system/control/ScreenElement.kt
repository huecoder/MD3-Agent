package com.mwagent.system.control

data class ScreenElement(
    val number: Int,
    val role: String,
    val label: String,
    val bounds: ScreenBox,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checked: Boolean?,
    val focused: Boolean,
    val viewId: String?,
    val className: String,
    val partial: Boolean = false,
    val scrollMoves: String = "",
) {
    val labelParts: List<String> get() = label.split(LABEL_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val LABEL_SEPARATOR = " · "
    }
}
