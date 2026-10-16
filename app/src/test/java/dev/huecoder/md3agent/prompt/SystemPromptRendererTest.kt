package dev.huecoder.md3agent.prompt

import android.content.ContextWrapper
import dev.huecoder.md3agent.data.db.MemoryEntity
import dev.huecoder.md3agent.data.db.MemoryTier
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.ui.pictures.PictureOptions
import dev.huecoder.md3agent.ui.pictures.PictureSyntax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class SystemPromptRendererTest {
    private val renderer = SystemPromptRenderer(
        DeviceInfoProvider(ContextWrapper(null)),
        now = { ZonedDateTime.of(2026, 10, 15, 21, 30, 0, 0, ZoneId.of("UTC")) },
        locale = { Locale.forLanguageTag("ru") },
    )

    @Test
    fun `placeholders in the editable template are filled`() {
        val prompt = renderer.render(AgentProfile(name = "Mira", systemPromptTemplate = "You are {agent_name} on {date} in {locale}."))

        assertTrue(prompt.startsWith("You are Mira on 2026-10-15 in ru."))
    }

    @Test
    fun `the display notes come after the template however it was edited`() {
        val custom = renderer.render(AgentProfile(systemPromptTemplate = "Be brief."))
        val empty = renderer.render(AgentProfile(systemPromptTemplate = ""))

        listOf(custom, empty).forEach { prompt ->
            assertTrue(prompt.contains("How your replies are displayed:"))
            assertTrue(prompt.contains("![short description]"))
        }
        assertTrue(custom.indexOf("Be brief.") < custom.indexOf("How your replies are displayed:"))
    }

    @Test
    fun `memories and device access follow the display notes`() {
        val memory = MemoryEntity(id = "m1", tier = MemoryTier.INSTANT, title = "Name", content = "Alex", createdAt = 0L, updatedAt = 0L)

        val prompt = renderer.render(AgentProfile(systemPromptTemplate = "Hi."), accessNotes = listOf("Shell is available"), instantMemories = listOf(memory))

        val order = listOf("Hi.", "How your replies are displayed:", "Instant memory", "Device access available")
        assertEquals(order.map { prompt.indexOf(it) }.sorted(), order.map { prompt.indexOf(it) })
        assertTrue(order.all { prompt.contains(it) })
    }

    @Test
    fun `the picture example in the notes really parses`() {
        val picture = PictureSyntax.single("![description](url \"right 50%\")")

        assertNotNull(picture)
        assertEquals(PictureOptions.parse("right 50%"), picture!!.options)
    }
}
