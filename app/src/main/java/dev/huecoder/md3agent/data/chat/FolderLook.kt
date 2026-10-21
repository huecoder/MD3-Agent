package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.db.FolderEntity

data class FolderLook(val glyph: ChatGlyph?, val tint: ChatTint) {
    companion object {
        val Default = FolderLook(glyph = null, tint = ChatTint.Default)

        fun of(iconKey: String?, colorKey: String?) = FolderLook(ChatGlyph.of(iconKey), ChatTint.of(colorKey) ?: ChatTint.Default)
    }
}

val FolderEntity.look: FolderLook get() = FolderLook.of(iconKey, colorKey)
