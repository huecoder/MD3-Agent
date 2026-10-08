package dev.huecoder.md3agent.data.chat

enum class ChatGlyph {
    CHAT, CODE, BUG, TERMINAL, IDEA, SCIENCE, MATH, BOOK, SCHOOL, WRITING, DOCUMENT, TRANSLATE, MAIL, CALENDAR, WORK,
    MONEY, SHOPPING, FOOD, COOKING, HEALTH, MEDICINE, FITNESS, MIND, TRAVEL, CAR, WORLD, HOME, PETS, NATURE, MUSIC,
    MOVIE, GAME, PHOTO, ART, PARTY, LOVE, ROCKET, SECURITY, REPAIR, SEARCH, ROBOT;

    val key: String get() = name.lowercase()

    companion object {
        val Default = CHAT

        fun of(key: String?): ChatGlyph? = entries.firstOrNull { it.key == key }
    }
}

enum class ChatTint {
    THEME, RED, ORANGE, AMBER, GREEN, TEAL, CYAN, BLUE, INDIGO, PURPLE, PINK, GRAY;

    val key: String get() = name.lowercase()

    companion object {
        val Default = THEME

        fun of(key: String?): ChatTint? = entries.firstOrNull { it.key == key }
    }
}

enum class ChatShape {
    COOKIE, CLOVER, FLOWER, SUNNY, GEM, PUFFY, BURST, HEART, ARCH, PILL, CIRCLE, SQUARE, PENTAGON, GHOST, BUN, FAN;

    val key: String get() = name.lowercase()

    companion object {
        val Default = COOKIE

        fun of(key: String?): ChatShape? = entries.firstOrNull { it.key == key }
    }
}

data class ChatLook(val glyph: ChatGlyph, val tint: ChatTint, val shape: ChatShape) {
    companion object {
        val Default = ChatLook(ChatGlyph.Default, ChatTint.Default, ChatShape.Default)

        fun of(iconKey: String?, colorKey: String?, shapeKey: String?) = ChatLook(
            glyph = ChatGlyph.of(iconKey) ?: ChatGlyph.Default,
            tint = ChatTint.of(colorKey) ?: ChatTint.Default,
            shape = ChatShape.of(shapeKey) ?: ChatShape.Default,
        )
    }
}
