package dev.huecoder.md3agent.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySettingsTest {
    @Test
    fun `gemini lists models with its default address once a key is set`() {
        val settings = MemorySettings(embeddingKind = EmbeddingKind.GEMINI, embeddingApiKey = "key")

        assertTrue(settings.canListModels)
        assertEquals(ApiFormat.GEMINI, settings.asEndpointProfile().format)
        assertEquals(MemorySettings.GEMINI_BASE_URL, settings.asEndpointProfile().baseUrl)
    }

    @Test
    fun `listing models needs a key and an address but not a model`() {
        val withoutKey = MemorySettings(embeddingKind = EmbeddingKind.OPENAI, embeddingBaseUrl = "https://api.openai.com/v1")
        val withKey = withoutKey.copy(embeddingApiKey = "key")

        assertFalse(withoutKey.canListModels)
        assertTrue(withKey.canListModels)
        assertFalse(withKey.embeddingsReady)
        assertTrue(withKey.copy(embeddingModel = "text-embedding-3-small").embeddingsReady)
    }

    @Test
    fun `an embeddings address pasted in full still points at the models list`() {
        val settings = MemorySettings(
            embeddingKind = EmbeddingKind.OPENAI,
            embeddingBaseUrl = " https://example.com/v1/embeddings/ ",
            embeddingApiKey = "key",
        )

        assertEquals(ApiFormat.OPENAI, settings.asEndpointProfile().format)
        assertEquals("https://example.com/v1", settings.asEndpointProfile().baseUrl)
    }

    @Test
    fun `turned off embeddings never list models`() {
        assertFalse(MemorySettings(embeddingApiKey = "key", embeddingBaseUrl = "https://example.com/v1").canListModels)
    }
}
