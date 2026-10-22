package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun CaptionOverlay(caption: String, modifier: Modifier = Modifier, visibility: () -> Float = FULLY_VISIBLE) {
    val scrim = MaterialTheme.colorScheme.scrim
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = visibility() }
            .background(Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(alpha = SCRIM_ALPHA))))
            .padding(start = CAPTION_HORIZONTAL_PADDING, end = CAPTION_HORIZONTAL_PADDING, top = CAPTION_TOP_PADDING, bottom = CAPTION_BOTTOM_PADDING),
    ) {
        Text(
            caption,
            style = MaterialTheme.typography.labelLarge,
            color = CAPTION_TEXT,
            maxLines = CAPTION_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val FULLY_VISIBLE: () -> Float = { 1f }
private val CAPTION_TEXT = Color.White
private const val SCRIM_ALPHA = 0.72f
private const val CAPTION_MAX_LINES = 3
private val CAPTION_HORIZONTAL_PADDING = 12.dp
private val CAPTION_TOP_PADDING = 28.dp
private val CAPTION_BOTTOM_PADDING = 10.dp
