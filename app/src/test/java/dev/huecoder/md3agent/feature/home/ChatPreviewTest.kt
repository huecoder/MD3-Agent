package dev.huecoder.md3agent.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPreviewTest {
    @Test
    fun stripsInlineEmphasis() {
        assertEquals("Честно в этом чате у меня нет доступа", ChatPreview.of("Честно в этом чате у меня **нет доступа**"))
    }

    @Test
    fun stripsHeadingsListsAndQuotes() {
        assertEquals("План шаг один шаг два цитата", ChatPreview.of("## План\n- шаг один\n1. шаг два\n> цитата"))
    }

    @Test
    fun keepsLinkTextAndDropsCodeBlocks() {
        assertEquals("см. доку дальше", ChatPreview.of("см. [доку](https://x.y)\n```kotlin\nval a = 1\n```\nдальше"))
    }

    @Test
    fun keepsSnakeCaseIdentifiers() {
        assertEquals("вызови snake_case_name", ChatPreview.of("вызови `snake_case_name`"))
    }
}
