package dev.huecoder.md3agent.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Model_training
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.models.OptionPickerSheet
import dev.huecoder.md3agent.feature.models.PickerPage
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.llm.models.ModelRoles

@Composable
internal fun EmbeddingModelSheet(
    selectedId: String,
    models: Loaded<ModelInfo>,
    onPick: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val labels = remember(resources) { OptionLabels(resources) }
    val split = remember(models.items) { ModelRoles.embedding(models.items) }
    val page = PickerPage(
        title = stringResource(R.string.memory_model),
        icon = MaterialSymbols.Rounded.Model_training,
        selectedId = selectedId,
        options = remember(split, labels) { split.suggested.map(labels::model) },
        moreOptions = remember(split, labels) { split.others.map(labels::model) },
        countLabel = R.plurals.model_picker_count,
        emptyHint = stringResource(R.string.picker_models_empty),
        loading = models.loading,
        error = models.error,
        onRefresh = onRefresh,
        onPick = { option -> onPick(option.id) },
    )
    OptionPickerSheet(tabs = listOf(page), initialTab = 0, onDismiss = onDismiss)
}
