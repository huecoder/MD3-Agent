package dev.huecoder.md3agent.feature.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.components.AgentAvatar

@Composable
internal fun AgentList(agents: List<AgentProfile>, selectedId: String?, onPick: (AgentProfile) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier = modifier, contentPadding = PickerListPadding, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        itemsIndexed(agents, key = { _, agent -> agent.id }) { index, agent ->
            val isSelected = agent.id == selectedId
            SegmentedListItem(
                onClick = { onPick(agent) },
                selected = isSelected,
                shapes = ListItemDefaults.segmentedShapes(index, agents.size),
                colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer, selectedContainerColor = colors.secondaryContainer),
                leadingContent = { AgentAvatar(isWorking = false, size = AVATAR_SIZE, look = AgentLook.of(agent)) },
                trailingContent = if (isSelected) {
                    { Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = null, tint = colors.primary) }
                } else {
                    null
                },
            ) {
                Text(agent.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val AVATAR_SIZE = 40.dp
