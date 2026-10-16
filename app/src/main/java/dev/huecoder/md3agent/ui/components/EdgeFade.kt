package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.bottomEdgeFade(
    color: Color = MaterialTheme.colorScheme.background,
    height: Dp = EDGE_FADE_HEIGHT,
    underNavigationBar: Boolean = true,
): Modifier {
    val inset = if (underNavigationBar) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    return drawWithContent {
        drawContent()
        val band = (height + inset).toPx().coerceAtMost(size.height)
        val ramp = (height.toPx() / band).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(0f to color.copy(alpha = 0f), ramp to color, 1f to color),
                startY = size.height - band,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - band),
            size = Size(size.width, band),
        )
    }
}

private val EDGE_FADE_HEIGHT = 28.dp
