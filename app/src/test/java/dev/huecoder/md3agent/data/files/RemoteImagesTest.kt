package dev.huecoder.md3agent.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteImagesTest {
    private val directory = File("/cache/remote-images")
    private val mimeTypeOf: (String) -> String = { name ->
        when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    private fun viewable(url: String, description: String = "") = RemoteImages.viewable(directory, url, description, mimeTypeOf)

    @Test
    fun `a web picture keeps its file name from the address`() {
        val file = viewable("https://store.example.com/img/pixel-10-pro%20obsidian.png?width=800#top", "Obsidian")

        assertEquals("pixel-10-pro obsidian.png", file.name)
        assertEquals("image/png", file.mimeType)
        assertEquals("https://store.example.com/img/pixel-10-pro%20obsidian.png?width=800#top", file.url)
        assertEquals(directory, file.file.parentFile)
        assertTrue(file.file.name.endsWith(".png"))
    }

    @Test
    fun `an address without a picture extension is named after the description`() {
        val file = viewable("https://cdn.example.com/render?id=42", "Porcelain / back view")

        assertEquals("Porcelain _ back view.jpg", file.name)
        assertEquals("image/jpeg", file.mimeType)
    }

    @Test
    fun `an address and description with nothing usable fall back to image`() {
        assertEquals("image.jpg", viewable("https://cdn.example.com/", "***").name)
    }

    @Test
    fun `the same address always maps to the same cache file`() {
        assertEquals(viewable("https://a.example/x.png").file, viewable("https://a.example/x.png").file)
        assertFalse(viewable("https://a.example/x.png").file == viewable("https://a.example/y.png").file)
    }

    @Test
    fun `local files are shown in place`() {
        val absolute = viewable("/storage/emulated/0/Pictures/a.webp")
        val uri = viewable("file:///storage/emulated/0/Pictures/b%20c.png")

        assertEquals(File("/storage/emulated/0/Pictures/a.webp"), absolute.file)
        assertNull(absolute.url)
        assertEquals(File("/storage/emulated/0/Pictures/b c.png"), uri.file)
        assertEquals("b c.png", uri.name)
    }

    @Test
    fun `only web and local pictures can be opened elsewhere`() {
        assertTrue(viewable("https://a.example/x.png").canExport)
        assertTrue(viewable("/data/x.png").canExport)
        assertFalse(viewable("data:image/png;base64,AAAA").canExport)
        assertFalse(viewable("content://media/external/images/1").canExport)
    }

    @Test
    fun `the server content type corrects the name and mime`() {
        val guessed = viewable("https://cdn.example.com/render?id=42", "Obsidian")

        val fixed = RemoteImages.corrected(guessed, "image/png; charset=binary")

        assertEquals("Obsidian.png", fixed.name)
        assertEquals("image/png", fixed.mimeType)
        assertEquals(guessed, RemoteImages.corrected(guessed, "image/jpeg"))
        assertEquals(guessed, RemoteImages.corrected(guessed, "text/html"))
        assertEquals(guessed, RemoteImages.corrected(guessed, null))
    }
}
