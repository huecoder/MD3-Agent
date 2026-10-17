package dev.huecoder.md3agent.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenContextStoreTest {
    private val labels = mapOf("org.telegram.messenger" to "Telegram")
    private val store = ScreenContextStore(clock = { 1_000L })

    @Test
    fun `nothing is said before the screen was read`() {
        assertNull(store.openedOverNote(labels::get))
    }

    @Test
    fun `the note names the app and lists the elements`() {
        store.recordElements("org.telegram.messenger", "App: org.telegram.messenger, screen 1080x2400\n[1] button \"Search\" (tap) @10,20")
        val note = store.openedOverNote(labels::get)!!
        assertTrue(note.contains("Telegram (org.telegram.messenger)"))
        assertTrue(note.contains("[1] button \"Search\""))
    }

    @Test
    fun `an unknown app is named by its package`() {
        store.recordElements("com.example.app", "App: com.example.app")
        assertTrue(store.openedOverNote(labels::get)!!.contains("com.example.app"))
    }

    @Test
    fun `clearing forgets the previous screen`() {
        store.recordElements("org.telegram.messenger", "App")
        store.clear()
        assertNull(store.latest.value)
        assertNull(store.openedOverNote(labels::get))
    }

    @Test
    fun `a missing structure does not wipe what accessibility found`() {
        store.recordElements("org.telegram.messenger", "App")
        store.recordStructure(null)
        assertEquals("org.telegram.messenger", store.latest.value?.packageName)
    }
}
