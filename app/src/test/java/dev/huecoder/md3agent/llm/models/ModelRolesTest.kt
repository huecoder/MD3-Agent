package dev.huecoder.md3agent.llm.models

import dev.huecoder.md3agent.data.settings.ApiFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRolesTest {
    private fun models(vararg ids: String) = ids.map(::ModelInfo)

    @Test
    fun `openai recognition suggests whisper and transcribe models`() {
        val split = ModelRoles.recognition(ApiFormat.OPENAI, models("gpt-4o", "whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-mini-tts"))

        assertEquals(listOf("whisper-1", "gpt-4o-mini-transcribe"), split.suggested.map { it.id })
        assertEquals(listOf("gpt-4o", "gpt-4o-mini-tts"), split.others.map { it.id })
    }

    @Test
    fun `a server with unfamiliar names shows everything as suggested`() {
        val split = ModelRoles.recognition(ApiFormat.OPENAI, models("local-asr-small", "local-asr-large"))

        assertEquals(listOf("local-asr-small", "local-asr-large"), split.suggested.map { it.id })
        assertEquals(emptyList<ModelInfo>(), split.others)
    }

    @Test
    fun `elevenlabs recognition is the fixed scribe list`() {
        val split = ModelRoles.recognition(ApiFormat.ELEVENLABS, models("eleven_multilingual_v2"))

        assertEquals(listOf("scribe_v2", "scribe_v2_turbo", "scribe_v2_medical"), split.suggested.map { it.id })
    }

    @Test
    fun `gemini recognition needs generate content and skips speech and image models`() {
        fun model(id: String, vararg methods: String) = ModelInfo(id, details = ModelDetails(methods = methods.toList()))
        val split = ModelRoles.recognition(
            ApiFormat.GEMINI,
            listOf(
                model("gemini-2.5-flash", "generateContent"),
                model("gemini-2.5-flash-preview-tts", "generateContent"),
                model("gemini-embedding-001", "embedContent"),
            ),
        )

        assertEquals(listOf("gemini-2.5-flash"), split.suggested.map { it.id })
    }

    @Test
    fun `elevenlabs speech hides voice conversion models`() {
        fun model(id: String, speaks: Boolean) = ModelInfo(id, details = ModelDetails(canSpeak = speaks))
        val split = ModelRoles.speech(ApiFormat.ELEVENLABS, listOf(model("eleven_v3", true), model("eleven_multilingual_sts_v2", false)))

        assertEquals(listOf("eleven_v3"), split.suggested.map { it.id })
        assertEquals(listOf("eleven_multilingual_sts_v2"), split.others.map { it.id })
    }

    @Test
    fun `embedding models are found by method or by name`() {
        val withMethod = ModelInfo("gemini-embedding-2", details = ModelDetails(methods = listOf("embedContent")))
        val split = ModelRoles.embedding(listOf(withMethod, ModelInfo("text-embedding-3-small"), ModelInfo("gpt-5")))

        assertEquals(listOf("gemini-embedding-2", "text-embedding-3-small"), split.suggested.map { it.id })
        assertEquals(listOf("gpt-5"), split.others.map { it.id })
    }
}
