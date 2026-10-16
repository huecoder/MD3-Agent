package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun AlbumGrid(count: Int, modifier: Modifier = Modifier, tile: @Composable (index: Int, modifier: Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GALLERY_GAP)) {
        (0 until count).chunked(ALBUM_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GALLERY_GAP)) {
                row.forEach { index ->
                    tile(
                        index,
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (row.size == 1) WIDE_TILE_RATIO else SQUARE_TILE_RATIO)
                            .clip(MaterialTheme.shapes.large),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SliderRow(count: Int, modifier: Modifier = Modifier, item: @Composable (index: Int, modifier: Modifier) -> Unit) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { count },
        preferredItemWidth = SLIDER_ITEM_WIDTH,
        itemSpacing = GALLERY_GAP,
        modifier = modifier.fillMaxWidth().height(SLIDER_HEIGHT),
    ) { index ->
        item(index, Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge))
    }
}

private const val ALBUM_COLUMNS = 2
private const val SQUARE_TILE_RATIO = 1f
private const val WIDE_TILE_RATIO = 2f
internal val GALLERY_GAP = 4.dp
private val SLIDER_ITEM_WIDTH = 200.dp
private val SLIDER_HEIGHT = 240.dp
