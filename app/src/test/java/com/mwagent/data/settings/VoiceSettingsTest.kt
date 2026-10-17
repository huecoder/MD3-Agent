package com.mwagent.data.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSettingsTest {
    private val openAi = VoiceProvider(id = "openai", name = "OpenAI", baseUrl = "https://a", speechVoice = "alloy")
    private val eleven = VoiceProvider(id = "eleven", name = "ElevenLabs", baseUrl = "https://b", speechVoice = "rachel-id", format = ApiFormat.ELEVENLABS)

    private fun settings(output: SpeechOutput, speechProviderId: String? = "openai") =
        VoiceSettings(providers = listOf(openAi, eleven), activeProviderId = "openai", speechProviderId = speechProviderId, speechOutput = output)

    @Test
    fun `an agent without a voice follows the speech engine from settings`() {
        assertEquals(openAi, settings(SpeechOutput.PROVIDER).speechProviderFor(AgentVoice()))
        assertNull(settings(SpeechOutput.SYSTEM).speechProviderFor(AgentVoice()))
    }

    @Test
    fun `an agent that names a provider uses it even while the engine is the system one`() {
        assertEquals(eleven, settings(SpeechOutput.SYSTEM).speechProviderFor(AgentVoice(providerId = "eleven")))
    }

    @Test
    fun `an agent voice replaces only the voice of the provider`() {
        val provider = settings(SpeechOutput.PROVIDER).speechProviderFor(AgentVoice(providerId = "eleven", voiceId = "adam-id", voiceName = "Adam"))
        assertEquals(eleven.copy(speechVoice = "adam-id", speechVoiceName = "Adam"), provider)
    }

    @Test
    fun `a voice without a provider rides on the default speech provider`() {
        val provider = settings(SpeechOutput.PROVIDER).speechProviderFor(AgentVoice(voiceId = "nova"))
        assertEquals(openAi.copy(speechVoice = "nova"), provider)
    }

    @Test
    fun `a deleted provider falls back to the default speech engine`() {
        assertEquals(openAi, settings(SpeechOutput.PROVIDER).speechProviderFor(AgentVoice(providerId = "gone")))
        assertNull(settings(SpeechOutput.SYSTEM).speechProviderFor(AgentVoice(providerId = "gone", voiceId = "nova")))
    }

    @Test
    fun `agents saved before voices existed decode with the default voice`() {
        val agent = Json { ignoreUnknownKeys = true }.decodeFromString(AgentProfile.serializer(), """{"id":"main","name":"Assistant"}""")
        assertEquals(AgentVoice(), agent.voice)
    }
}
