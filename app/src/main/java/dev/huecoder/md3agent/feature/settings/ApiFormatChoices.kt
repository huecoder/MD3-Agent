package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat

internal fun apiFormatChoices(formats: List<ApiFormat>) = formats.map { Choice(it, it.label) }

private val ApiFormat.label: Int
    get() = when (this) {
        ApiFormat.OPENAI -> R.string.api_format_openai
        ApiFormat.ANTHROPIC -> R.string.api_format_anthropic
        ApiFormat.GEMINI -> R.string.api_format_gemini
        ApiFormat.ELEVENLABS -> R.string.api_format_elevenlabs
    }
