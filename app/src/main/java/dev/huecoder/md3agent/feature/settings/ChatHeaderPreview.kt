package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ChatHeaderStyle
import dev.huecoder.md3agent.data.settings.ChatSettings
import dev.huecoder.md3agent.feature.chat.ChatMenuActions
import dev.huecoder.md3agent.feature.chat.ChatUiState
import dev.huecoder.md3agent.feature.chat.ClassicChatBar
import dev.huecoder.md3agent.feature.chat.CompactChatHeader

@Composable
internal fun ChatHeaderPreview(chat: ChatSettings, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.chat_header_preview_title)
    val sample = ChatUiState(
        chatId = SAMPLE_CHAT_ID,
        modelName = stringResource(R.string.chat_header_preview_model),
        contextTokens = SAMPLE_TOKENS,
        showContext = chat.showContext,
        header = chat.header,
        contextDisplay = chat.contextDisplay,
    )
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.background,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
    ) {
        Box {
            FakeMessages(Modifier.padding(top = MESSAGES_TOP, start = 16.dp, end = 16.dp))
            when (chat.header) {
                ChatHeaderStyle.COMPACT -> CompactChatHeader(
                    title = title,
                    state = sample,
                    topInset = 0.dp,
                    onBack = {},
                    actions = ChatMenuActions.None,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                ChatHeaderStyle.CLASSIC -> ClassicChatBar(title, sample, onBack = {}, actions = ChatMenuActions.None, windowInsets = WindowInsets(0))
            }
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
        }
    }
}

@Composable
private fun FakeMessages(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FAKE_WIDTHS.forEach { fraction ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(fraction).height(FAKE_HEIGHT),
                content = {},
            )
        }
    }
}

private const val SAMPLE_CHAT_ID = "preview"
private const val SAMPLE_TOKENS = 24_400
private val FAKE_WIDTHS = listOf(0.8f, 0.55f, 0.7f)
private val PREVIEW_HEIGHT = 176.dp
private val MESSAGES_TOP = 54.dp
private val FAKE_HEIGHT = 40.dp
