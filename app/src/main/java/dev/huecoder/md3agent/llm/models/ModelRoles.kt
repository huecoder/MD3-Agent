package dev.huecoder.md3agent.llm.models

import dev.huecoder.md3agent.data.settings.ApiFormat

data class ModelSplit(val suggested: List<ModelInfo>, val others: List<ModelInfo> = emptyList())

object ModelRoles {
    private val ELEVENLABS_RECOGNITION = listOf("scribe_v2", "scribe_v2_turbo", "scribe_v2_medical").map(::ModelInfo)
    private val RECOGNITION_HINTS = listOf("whisper", "transcribe", "stt")
    private val GEMINI_NON_SPEECH_INPUT = listOf("tts", "embed", "image", "imagen", "veo", "aqa", "robotics")
    private val SPEECH_HINTS = listOf("tts", "speech")
    private val EMBEDDING_HINTS = listOf("embed")
    private const val GENERATE_CONTENT = "generateContent"
    private const val EMBED_CONTENT = "embedContent"

    fun recognition(format: ApiFormat, models: List<ModelInfo>): ModelSplit = when (format) {
        ApiFormat.ELEVENLABS -> ModelSplit(ELEVENLABS_RECOGNITION)
        ApiFormat.GEMINI -> models.split { model ->
            GENERATE_CONTENT in model.methods() && GEMINI_NON_SPEECH_INPUT.none { model.id.contains(it, ignoreCase = true) }
        }
        else -> models.split { model -> RECOGNITION_HINTS.any { model.id.contains(it, ignoreCase = true) } }
    }

    fun speech(format: ApiFormat, models: List<ModelInfo>): ModelSplit = when (format) {
        ApiFormat.ELEVENLABS -> models.split { it.details?.canSpeak != false }
        else -> models.split { model -> SPEECH_HINTS.any { model.id.contains(it, ignoreCase = true) } }
    }

    fun embedding(models: List<ModelInfo>): ModelSplit = models.split { model ->
        EMBED_CONTENT in model.methods() || EMBEDDING_HINTS.any { model.id.contains(it, ignoreCase = true) }
    }

    private fun ModelInfo.methods() = details?.methods.orEmpty()

    private fun List<ModelInfo>.split(isSuggested: (ModelInfo) -> Boolean): ModelSplit {
        val (suggested, others) = partition(isSuggested)
        return if (suggested.isEmpty()) ModelSplit(this) else ModelSplit(suggested, others)
    }
}
