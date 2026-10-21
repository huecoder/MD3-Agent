package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Folder
import dev.huecoder.md3agent.data.chat.FolderLook

val FolderLook.icon: ImageVector get() = glyph?.icon ?: MaterialSymbols.Rounded.Folder

@Composable
fun FolderBadge(look: FolderLook, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = look.tint.colors()
    Surface(shape = CircleShape, color = colors.container, contentColor = colors.content, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(look.icon, contentDescription = null, modifier = Modifier.size(size / 2)) }
    }
}
