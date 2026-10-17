package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Key
import com.composables.icons.materialsymbols.rounded.Manage_search
import com.composables.icons.materialsymbols.rounded.Model_training
import com.composables.icons.materialsymbols.rounded.Network_intelligence
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.EmbeddingKind
import dev.huecoder.md3agent.data.settings.MemorySettings
import dev.huecoder.md3agent.ui.components.FormField

internal fun CardGroupScope.embeddingCards(
    settings: MemorySettings,
    probe: EmbeddingProbe,
    onKind: (EmbeddingKind) -> Unit,
    onEdit: ((MemorySettings) -> MemorySettings) -> Unit,
    onPickModel: () -> Unit,
    onProbe: () -> Unit,
) {
    val kind = settings.embeddingKind
    card("mode") { shape ->
        SettingCard(
            icon = MaterialSymbols.Rounded.Manage_search,
            title = stringResource(R.string.memory_search_mode),
            summary = stringResource(kind.hint),
            shape = shape,
            indentBelow = false,
            below = { ChoicePill(KIND_CHOICES, kind, onKind, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
        )
    }
    if (kind == EmbeddingKind.OFF) {
        card("gemini-tip") { shape -> GeminiTipCard(onUse = { onKind(EmbeddingKind.GEMINI) }, shape = shape) }
        return
    }
    card("fields") { shape -> EmbeddingFieldsCard(settings, onEdit, shape) }
    if (kind == EmbeddingKind.GEMINI) card("get-key") { shape -> GetKeyCard(shape) }
    card("model") { shape ->
        PickerCard(
            icon = MaterialSymbols.Rounded.Model_training,
            title = stringResource(R.string.memory_model),
            value = settings.model.ifBlank { stringResource(R.string.memory_model_not_set) },
            detail = stringResource(R.string.memory_model_default).takeIf { kind == EmbeddingKind.GEMINI && settings.embeddingModel.isBlank() },
            shape = shape,
            onClick = onPickModel,
        )
    }
    card("probe") { shape -> EmbeddingProbeCard(probe, enabled = settings.embeddingsReady, onProbe = onProbe, shape = shape) }
}

@Composable
private fun EmbeddingFieldsCard(settings: MemorySettings, onEdit: ((MemorySettings) -> MemorySettings) -> Unit, shape: Shape) {
    FieldsCard(shape) {
        if (settings.embeddingKind == EmbeddingKind.OPENAI) {
            FormField(
                value = settings.embeddingBaseUrl,
                onValueChange = { value -> onEdit { it.copy(embeddingBaseUrl = value) } },
                label = stringResource(R.string.memory_base_url),
                placeholder = stringResource(R.string.memory_base_url_placeholder_openai),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SecretField(
            value = settings.embeddingApiKey,
            onValueChange = { value -> onEdit { it.copy(embeddingApiKey = value) } },
            label = stringResource(R.string.memory_api_key),
        )
    }
}

@Composable
private fun GeminiTipCard(onUse: () -> Unit, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    SettingCard(
        icon = MaterialSymbols.Rounded.Network_intelligence,
        title = stringResource(R.string.memory_gemini_tip_title),
        summary = stringResource(R.string.memory_gemini_tip_body),
        shape = shape,
        badgeContainer = colors.tertiaryContainer,
        badgeContent = colors.onTertiaryContainer,
        indentBelow = false,
        below = { FilledTonalButton(onClick = onUse, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.memory_gemini_tip_use)) } },
    )
}

@Composable
private fun GetKeyCard(shape: Shape) {
    val uriHandler = LocalUriHandler.current
    LinkCard(
        icon = MaterialSymbols.Rounded.Key,
        title = stringResource(R.string.memory_get_key),
        summary = MemorySettings.GEMINI_KEY_URL.toUri().host.orEmpty(),
        onClick = { uriHandler.openUri(MemorySettings.GEMINI_KEY_URL) },
        shape = shape,
    )
}

@Composable
private fun EmbeddingProbeCard(probe: EmbeddingProbe, enabled: Boolean, onProbe: () -> Unit, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    TestCard(
        title = stringResource(R.string.memory_probe),
        summary = when (probe) {
            is EmbeddingProbe.Ready -> stringResource(R.string.memory_probe_ok, probe.dimensions)
            is EmbeddingProbe.Failed -> probe.message
            EmbeddingProbe.Idle, EmbeddingProbe.Running -> stringResource(R.string.memory_probe_hint)
        },
        summaryColor = when (probe) {
            is EmbeddingProbe.Ready -> colors.primary
            is EmbeddingProbe.Failed -> colors.error
            else -> null
        },
        running = probe == EmbeddingProbe.Running,
        enabled = enabled,
        onTest = onProbe,
        shape = shape,
    )
}

private val EmbeddingKind.hint: Int
    get() = when (this) {
        EmbeddingKind.OFF -> R.string.memory_kind_off_hint
        EmbeddingKind.GEMINI -> R.string.memory_kind_gemini_hint
        EmbeddingKind.OPENAI -> R.string.memory_kind_openai_hint
    }

private val KIND_CHOICES = listOf(
    Choice(EmbeddingKind.OFF, R.string.memory_kind_off),
    Choice(EmbeddingKind.GEMINI, R.string.memory_kind_gemini),
    Choice(EmbeddingKind.OPENAI, R.string.memory_kind_openai),
)
