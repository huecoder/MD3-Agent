package com.mwagent.feature.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Archive
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Delete_forever
import com.composables.icons.materialsymbols.rounded.Drive_file_move
import com.composables.icons.materialsymbols.rounded.Restore_from_trash
import com.composables.icons.materialsymbols.rounded.Unarchive
import com.mwagent.R
import com.mwagent.data.settings.SwipeAction
import com.mwagent.ui.haptics.rememberHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class RowAction(val icon: ImageVector, @StringRes val label: Int, val tone: Tone) {
    TRASH(MaterialSymbols.Rounded.Delete, R.string.home_action_trash, Tone.DANGER),
    ARCHIVE(MaterialSymbols.Rounded.Archive, R.string.home_action_archive, Tone.CALM),
    UNARCHIVE(MaterialSymbols.Rounded.Unarchive, R.string.home_action_unarchive, Tone.CALM),
    MOVE(MaterialSymbols.Rounded.Drive_file_move, R.string.home_move, Tone.NEUTRAL),
    RESTORE(MaterialSymbols.Rounded.Restore_from_trash, R.string.home_action_restore, Tone.CALM),
    DELETE_FOREVER(MaterialSymbols.Rounded.Delete_forever, R.string.home_action_delete_forever, Tone.DANGER),
    ;

    enum class Tone { DANGER, CALM, NEUTRAL }

    val keepsRow: Boolean get() = this == MOVE

    companion object {
        fun resolve(setting: SwipeAction, filter: ChatFilter, isRight: Boolean): RowAction? = when {
            filter == ChatFilter.Trash -> if (isRight) RESTORE else DELETE_FOREVER
            else -> when (setting) {
                SwipeAction.NONE -> null
                SwipeAction.TRASH -> TRASH
                SwipeAction.MOVE -> MOVE
                SwipeAction.ARCHIVE -> if (filter == ChatFilter.Archive) UNARCHIVE else ARCHIVE
            }
        }
    }
}

@Composable
fun SwipeableRow(
    right: RowAction?,
    left: RowAction?,
    shape: Shape,
    onAction: (RowAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) state.snapTo(SwipeToDismissBoxValue.Settled)
        snapshotFlow { state.targetValue }.distinctUntilChanged().drop(1).collect { haptics.tick() }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = right != null,
        enableDismissFromEndToStart = left != null,
        onDismiss = { value ->
            val action = if (value == SwipeToDismissBoxValue.StartToEnd) right else left
            action?.let {
                haptics.press()
                onAction(it)
            }
            if (action == null || action.keepsRow) scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            val isRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = (if (isRight) right else left) ?: return@SwipeToDismissBox
            val armed = state.targetValue != SwipeToDismissBoxValue.Settled
            val iconScale by animateFloatAsState(if (armed) ARMED_SCALE else 1f, POP_SPEC, label = "swipeIcon")
            val (container, content) = action.tone.colors(armed)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, if (isRight) Alignment.Start else Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().clip(shape).background(container).padding(horizontal = 24.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = content, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge, color = content)
            }
        },
    ) { content() }
}

@Composable
private fun RowAction.Tone.colors(armed: Boolean): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        RowAction.Tone.DANGER -> if (armed) scheme.error to scheme.onError else scheme.errorContainer to scheme.onErrorContainer
        RowAction.Tone.CALM -> if (armed) scheme.tertiary to scheme.onTertiary else scheme.tertiaryContainer to scheme.onTertiaryContainer
        RowAction.Tone.NEUTRAL -> if (armed) scheme.secondary to scheme.onSecondary else scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}

private const val ARMED_SCALE = 1.25f
private val POP_SPEC = spring<Float>(dampingRatio = 0.4f, stiffness = 700f)
