package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.SwipeAction
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.OptionDropdown

@Composable
fun SwipeActionDropdown(@StringRes label: Int, selected: SwipeAction, onSelect: (SwipeAction) -> Unit) {
    OptionDropdown(
        label = stringResource(label),
        options = SwipeAction.entries.map { DropdownOption(it, stringResource(it.label)) },
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

@get:StringRes
private val SwipeAction.label: Int
    get() = when (this) {
        SwipeAction.NONE -> R.string.swipe_action_none
        SwipeAction.TRASH -> R.string.swipe_action_trash
        SwipeAction.ARCHIVE -> R.string.swipe_action_archive
        SwipeAction.MOVE -> R.string.swipe_action_move
    }
