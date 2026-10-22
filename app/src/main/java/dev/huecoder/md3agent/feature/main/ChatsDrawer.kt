package dev.huecoder.md3agent.feature.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Archive
import com.composables.icons.materialsymbols.rounded.Create_new_folder
import com.composables.icons.materialsymbols.rounded.Delete
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.look
import dev.huecoder.md3agent.data.db.FolderEntity
import dev.huecoder.md3agent.ui.components.FolderBadge
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.navigation.SlideBackHandler
import dev.huecoder.md3agent.ui.navigation.SlideEdge
import dev.huecoder.md3agent.ui.navigation.rememberSlideBack
import dev.huecoder.md3agent.ui.navigation.slideBack

@Composable
fun ChatsDrawer(
    drawerState: DrawerState,
    folders: List<FolderEntity>,
    onFolder: (FolderEntity) -> Unit,
    onNewFolder: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    val back = rememberSlideBack()
    SlideBackHandler(enabled = drawerState.targetValue == DrawerValue.Open, back = back) {
        drawerState.close()
        back.reset()
    }
    ModalDrawerSheet(modifier = Modifier.width(DRAWER_WIDTH).slideBack(back, SlideEdge.START)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            SectionHeader(stringResource(R.string.home_folders), topPadding = 24.dp)
            folders.forEach { folder ->
                NavigationDrawerItem(
                    label = { Text(folder.name, maxLines = 1) },
                    icon = { FolderBadge(folder.look, size = ICON_SIZE) },
                    selected = false,
                    onClick = { onFolder(folder) },
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.home_folder_new), color = MaterialTheme.colorScheme.primary) },
                icon = { Icon(MaterialSymbols.Rounded.Create_new_folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                selected = false,
                onClick = onNewFolder,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 28.dp))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.home_archive)) },
                icon = { Icon(MaterialSymbols.Rounded.Archive, contentDescription = null) },
                selected = false,
                onClick = onArchive,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.home_trash)) },
                icon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null) },
                selected = false,
                onClick = onTrash,
            )
        }
    }
}

private val ICON_SIZE = 28.dp
private val DRAWER_WIDTH = 300.dp
