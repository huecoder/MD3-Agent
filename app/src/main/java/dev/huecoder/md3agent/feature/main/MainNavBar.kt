package dev.huecoder.md3agent.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Edit_square
import com.composables.icons.materialsymbols.rounded.Folder_open
import com.composables.icons.materialsymbols.rounded.Forum
import com.composables.icons.materialsymbols.rounded.Settings
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.theme.LayoutMotion

enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    CHATS(R.string.home_title, MaterialSymbols.Rounded.Forum),
    FILES(R.string.files_title, MaterialSymbols.Rounded.Folder_open),
    SETTINGS(R.string.settings_title, MaterialSymbols.Rounded.Settings),
}

val MainNavBarClearance = 112.dp

@Composable
fun MainNavBar(
    selected: MainTab,
    expanded: Boolean,
    onSelect: (MainTab) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onNewChat) {
                Icon(MaterialSymbols.Rounded.Edit_square, contentDescription = stringResource(R.string.home_new_chat))
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
    ) {
        MainTab.entries.forEach { tab -> TabButton(tab, checked = tab == selected, onClick = { onSelect(tab) }) }
    }
}

@Composable
private fun TabButton(tab: MainTab, checked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = ToggleButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = colors.onPrimaryContainer,
            checkedContainerColor = colors.primary,
            checkedContentColor = colors.onPrimary,
        ),
    ) {
        Icon(tab.icon, contentDescription = stringResource(tab.label), modifier = Modifier.size(ButtonDefaults.IconSize))
        AnimatedVisibility(
            visible = checked,
            enter = fadeIn() + expandHorizontally(LayoutMotion.settle()),
            exit = fadeOut() + shrinkHorizontally(LayoutMotion.settle()),
        ) {
            Text(stringResource(tab.label), maxLines = 1, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}
