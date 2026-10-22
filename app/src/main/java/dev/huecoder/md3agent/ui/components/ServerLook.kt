package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Hub
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatGlyph
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.data.settings.McpServer
import dev.huecoder.md3agent.ui.haptics.clickHaptics

private val AUTO_TINTS = ChatTint.entries.filter { it != ChatTint.THEME && it != ChatTint.GRAY }

val McpServer.glyph: ChatGlyph? get() = ChatGlyph.of(iconKey)

val McpServer.tint: ChatTint get() = ChatTint.of(colorKey) ?: autoTint(id)

val McpServer.icon: ImageVector get() = glyph?.icon ?: MaterialSymbols.Rounded.Hub

fun autoTint(seed: String): ChatTint = AUTO_TINTS[Math.floorMod(seed.hashCode(), AUTO_TINTS.size)]

@Composable
fun McpServerBadge(server: McpServer, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = server.tint.colors()
    ShapeBadge(server.icon, modifier, size, containerColor = colors.container, contentColor = colors.content)
}

@Composable
fun LookSheet(glyph: ChatGlyph?, tint: ChatTint, fallback: ImageVector, onGlyph: (ChatGlyph) -> Unit, onTint: (ChatTint) -> Unit, onDismiss: () -> Unit) {
    val colors = tint.colors()
    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.clickHaptics()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                ShapeBadge(glyph?.icon ?: fallback, size = 72.dp, containerColor = colors.container, contentColor = colors.content)
            }
            SectionHeader(stringResource(R.string.chat_look_icon))
            GlyphGrid(glyph, onPick = onGlyph)
            SectionHeader(stringResource(R.string.chat_look_color))
            TintGrid(tint, glyph?.icon ?: fallback, Modifier.padding(bottom = 24.dp), onTint)
        }
    }
}
