package dev.huecoder.md3agent.llm.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogParsingTest {
    @Test
    fun `families are detected from ids with and without vendor prefixes`() {
        val expected = mapOf(
            "anthropic/claude-sonnet-4.5" to ModelFamily.CLAUDE,
            "gpt-5-mini" to ModelFamily.GPT,
            "chatgpt-4o-latest" to ModelFamily.GPT,
            "o3-pro" to ModelFamily.GPT,
            "google/gemini-3-pro-preview" to ModelFamily.GEMINI,
            "google/gemma-3-27b-it" to ModelFamily.GEMMA,
            "nano-banana-pro" to ModelFamily.GEMINI,
            "meta-llama/llama-4-maverick" to ModelFamily.LLAMA,
            "meta/muse-spark-1.3" to ModelFamily.MUSE,
            "nvidia/llama-3.3-nemotron-super-49b" to ModelFamily.NEMOTRON,
            "deepseek/deepseek-r1-distill-qwen-32b" to ModelFamily.DEEPSEEK,
            "moonshotai/kimi-k2-thinking" to ModelFamily.KIMI,
            "z-ai/glm-4.6" to ModelFamily.GLM,
            "x-ai/grok-4" to ModelFamily.GROK,
            "qwen/qwen3-235b-a22b" to ModelFamily.QWEN,
            "perplexity/sonar-pro" to ModelFamily.SONAR,
            "minimax/minimax-m2" to ModelFamily.MINIMAX,
            "xiaomi/mimo-v2-flash" to ModelFamily.MIMO,
            "stepfun-ai/step3" to ModelFamily.STEP,
            "bytedance/seed-oss-36b" to ModelFamily.SEED,
            "mistralai/magistral-medium" to ModelFamily.MISTRAL,
        )
        expected.forEach { (id, family) -> assertEquals(id, family, ModelFamily.detect(id)) }
        assertEquals(null, ModelFamily.detect("some-random-model"))
    }

    @Test
    fun `heuristics spot reasoning vision and image output`() {
        val sonnet = ModelInfo("anthropic/claude-sonnet-4.5").traits
        assertTrue(sonnet.reasoning && sonnet.vision && !sonnet.generatesImages)
        assertEquals(ModelOutput.TEXT_AND_IMAGE, ModelInfo("google/gemini-2.5-flash-image").traits.output)
        assertEquals(ModelOutput.IMAGE, ModelInfo("black-forest-labs/flux-1.1-pro").traits.output)
        assertFalse(ModelInfo("gpt-4o-mini").traits.reasoning)
    }

    @Test
    fun `openrouter metadata wins over guesses`() {
        val body = """
            {"data":[{"id":"vendor/odd-name","name":"Vendor: Odd Name","context_length":200000,
              "architecture":{"input_modalities":["text","image"],"output_modalities":["image","text"]},
              "supported_parameters":["tools","reasoning","include_reasoning"]}]}
        """.trimIndent()
        val info = ModelInfoParser.parseList(Json.parseToJsonElement(body)).single()
        assertEquals("Odd Name", info.displayName)
        assertEquals(200000, info.contextLength)
        val traits = info.traits
        assertTrue(traits.vision && traits.reasoning)
        assertEquals(ModelOutput.TEXT_AND_IMAGE, traits.output)
        assertEquals(ReasoningControl.REASONING_OBJECT, traits.reasoningControl)
    }

    @Test
    fun `plain openai lists still parse`() {
        val infos = ModelInfoParser.parseList(Json.parseToJsonElement("""{"object":"list","data":[{"id":"b"},{"id":"a"}]}"""))
        assertEquals(listOf("a", "b"), infos.map { it.id })
        assertEquals(null, infos.first().reasoning)
    }

    @Test
    fun `gemini and anthropic model lists keep clean ids and display names`() {
        val gemini = ModelInfoParser.parseList(
            Json.parseToJsonElement(
                """{"models":[{"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","inputTokenLimit":1048576,"thinking":true}]}""",
            ),
        ).single()
        assertEquals("gemini-2.5-flash", gemini.id)
        assertEquals("Gemini 2.5 Flash", gemini.displayName)
        assertEquals(1_048_576, gemini.contextLength)
        assertEquals(true, gemini.reasoning)

        val claude = ModelInfoParser.parseList(
            Json.parseToJsonElement("""{"data":[{"type":"model","id":"claude-sonnet-4-5","display_name":"Claude Sonnet 4.5"}]}"""),
        ).single()
        assertEquals("claude-sonnet-4-5", claude.id)
        assertEquals("Claude Sonnet 4.5", claude.displayName)
    }

    @Test
    fun `elevenlabs models are listed by model_id`() {
        val body = """[{"model_id":"eleven_multilingual_v2","name":"Eleven Multilingual v2"},{"model_id":"eleven_flash_v2_5","name":"Eleven Flash v2.5"}]"""
        val ids = ModelInfoParser.parseList(Json.parseToJsonElement(body)).map { it.id }
        assertEquals(listOf("eleven_flash_v2_5", "eleven_multilingual_v2"), ids)
    }
}
