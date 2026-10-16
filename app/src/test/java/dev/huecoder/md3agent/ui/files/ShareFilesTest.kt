package dev.huecoder.md3agent.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFilesTest {
    @Test
    fun `one kind of file is shared as that kind`() {
        assertEquals("image/png", commonMimeType(listOf("image/png", "image/png")))
        assertEquals("application/pdf", commonMimeType(listOf("application/pdf")))
    }

    @Test
    fun `different pictures are shared as any picture`() {
        assertEquals("image/*", commonMimeType(listOf("image/png", "image/jpeg", "image/webp")))
    }

    @Test
    fun `mixed files are shared as anything`() {
        assertEquals("*/*", commonMimeType(listOf("image/png", "text/plain")))
        assertEquals("*/*", commonMimeType(emptyList()))
    }
}
