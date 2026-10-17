package dev.huecoder.md3agent.feature.settings

import android.content.res.Resources
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.models.PickerOption
import dev.huecoder.md3agent.feature.models.formatContext
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.llm.models.ModelInfo
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

internal class OptionLabels(private val resources: Resources) {
    private val locale: Locale get() = resources.configuration.locales[0]

    fun model(info: ModelInfo): PickerOption {
        val details = info.details
        val tags = buildList {
            info.contextLength?.let { add(resources.getString(R.string.model_context, formatContext(it))) }
            details?.outputTokenLimit?.let { add(resources.getString(R.string.picker_output_limit, formatContext(it))) }
            details?.languages?.takeIf { it.isNotEmpty() }?.let { add(resources.getQuantityString(R.plurals.picker_languages, it.size, it.size)) }
            details?.maxInputCharacters?.let { add(resources.getString(R.string.picker_max_characters, NumberFormat.getIntegerInstance(locale).format(it))) }
            details?.costFactor?.let { add(resources.getString(R.string.picker_cost_factor, BigDecimal.valueOf(it).stripTrailingZeros().toPlainString())) }
            details?.owner?.let(::add)
            details?.createdAt?.let { add(DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(it * MILLIS_PER_SECOND))) }
        }
        return PickerOption(
            id = info.id,
            title = info.displayName,
            subtitle = info.id.takeIf { it != info.displayName },
            description = details?.description,
            tags = tags,
        )
    }

    fun voice(voice: SpeechVoice) = PickerOption(
        id = voice.id,
        title = voice.name,
        subtitle = voice.id.takeIf { it != voice.name },
        description = voice.description,
        tags = voice.traits,
        previewUrl = voice.previewUrl,
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
