package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.chat.ChatGlyph
import dev.huecoder.md3agent.data.chat.ChatShape
import dev.huecoder.md3agent.data.chat.ChatTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatLabelsTest {
    @Test
    fun `json wrapped in prose and fences still parses`() {
        val reply = "Sure!\n```json\n{\"title\": \"«Рецепт борща».\", \"icon\": \"Cooking\", \"color\": \"red\", \"shape\": \"flower\"}\n```"
        val (title, look) = ChatLabels.parse(reply)!!
        assertEquals("Рецепт борща", title)
        assertEquals(ChatGlyph.COOKING, look.glyph)
        assertEquals(ChatTint.RED, look.tint)
        assertEquals(ChatShape.FLOWER, look.shape)
    }

    @Test
    fun `unknown icon falls back to the default glyph`() {
        assertEquals(ChatGlyph.Default, ChatLabels.parse("{\"title\": \"Trip\", \"icon\": \"spaceship\"}")!!.second.glyph)
    }

    @Test
    fun `blank title or missing json is rejected`() {
        assertNull(ChatLabels.parse("{\"title\": \"  \"}"))
        assertNull(ChatLabels.parse("no json here"))
    }
}
