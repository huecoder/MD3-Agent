package com.mwagent.feature.files

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.mwagent.R
import com.mwagent.data.files.ViewableFile
import com.mwagent.feature.home.dragToSelect

@Composable
internal fun MediaGrid(
    images: List<ViewableFile>,
    selection: Set<String>,
    topPadding: Dp,
    onOpen: (index: Int) -> Unit,
    onToggle: (ViewableFile) -> Unit,
    onSelectRange: (anchor: String, current: String) -> Unit,
    onRangeEnd: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val paths = images.map { it.file.path }.toSet()
    val isImage by rememberUpdatedState<(Any) -> Boolean> { key -> key in paths }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(CELL_SIZE),
        modifier = Modifier
            .fillMaxSize()
            .dragToSelect(gridState, scope, isSelectable = { isImage(it) }, onRange = onSelectRange, onEnd = onRangeEnd),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        itemsIndexed(images, key = { _, image -> image.file.path }) { index, image ->
            MediaTile(
                image = image,
                selected = image.file.path in selection,
                selecting = selection.isNotEmpty(),
                onClick = { if (selection.isEmpty()) onOpen(index) else onToggle(image) },
                onLongClick = { onToggle(image) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    image: ViewableFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inset by animateDpAsState(if (selected) SELECTED_INSET else 0.dp, label = "tile-inset")
    Box(
        modifier
            .aspectRatio(1f)
            .semantics { this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.home_select)),
    ) {
        AsyncImage(
            model = image.file,
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(inset).clip(MaterialTheme.shapes.medium),
        )
        if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(MARK_MARGIN))
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(MARK_SIZE)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Black.copy(alpha = MARK_SCRIM_ALPHA))
            .then(if (selected) Modifier else Modifier.border(MARK_BORDER, Color.White, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(MaterialSymbols.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(MARK_ICON))
    }
}

private val CELL_SIZE = 108.dp
private val GAP = 4.dp
private val SELECTED_INSET = 10.dp
private val MARK_SIZE = 24.dp
private val MARK_ICON = 16.dp
private val MARK_MARGIN = 10.dp
private val MARK_BORDER = 2.dp
private const val MARK_SCRIM_ALPHA = 0.3f
