package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyMetaFormatTest {
    @Test
    fun `nothing is shown for cache when no token came from it`() {
        assertNull(ReplyMetaFormat.cached(TokenUsage(promptTokens = 2_300, completionTokens = 90)))
    }

    @Test
    fun `small cache counts are plain numbers`() {
        val shown = ReplyMetaFormat.cached(TokenUsage(promptTokens = 2_300, completionTokens = 90, cachedTokens = 640))

        assertEquals(UiText.Resource(R.string.chat_meta_cached, listOf(UiText.Raw("640"))), shown)
    }

    @Test
    fun `large cache counts are shortened to thousands`() {
        val shown = ReplyMetaFormat.cached(TokenUsage(promptTokens = 12_300, completionTokens = 90, cachedTokens = 10_500))

        assertEquals(
            UiText.Resource(R.string.chat_meta_cached, listOf(UiText.Resource(R.string.chat_meta_tokens_thousands, listOf(10.5)))),
            shown,
        )
    }

    @Test
    fun `cache from several rounds adds up`() {
        val total = TokenUsage(1_000, 50, 800) + TokenUsage(1_200, 60, 900)

        assertEquals(1_700, total.cachedTokens)
    }
}
