package dev.huecoder.md3agent.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.History
import com.composables.icons.materialsymbols.rounded.North_west
import com.composables.icons.materialsymbols.rounded.Search
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.appViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatSearch(onOpenChat: (String) -> Unit, onOpenMessage: (chatId: String, messageId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = appViewModel(key = "chat-search") { ChatSearchViewModel(it.chats, it.searchHistory) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val text = rememberTextFieldState()
    val bar = rememberSearchBarState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(viewModel::setQuery) }
    val close: () -> Unit = {
        scope.launch { bar.animateToCollapsed() }
        text.clearText()
    }
    val open = { action: () -> Unit ->
        viewModel.remember(text.text.toString())
        close()
        action()
    }
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = bar,
            onSearch = viewModel::remember,
            placeholder = { Text(stringResource(R.string.home_search_hint)) },
            leadingIcon = { SearchLeadingIcon(bar, onBack = close) },
            trailingIcon = if (text.text.isNotEmpty()) {
                {
                    IconButton(onClick = { text.clearText() }) {
                        Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.home_search_clear))
                    }
                }
            } else {
                null
            },
        )
    }

    SearchBar(state = bar, inputField = inputField, modifier = modifier.fillMaxWidth())
    ExpandedFullScreenSearchBar(state = bar, inputField = inputField) {
        when {
            text.text.isBlank() -> RecentSearches(recent, onPick = { text.setTextAndPlaceCursorAtEnd(it) }, onForget = viewModel::forget)
            results.isEmpty && results.query.isNotEmpty() -> Text(
                stringResource(R.string.home_search_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> SearchResultsList(
                results = results,
                onChat = { chatId -> open { onOpenChat(chatId) } },
                onMessage = { hit -> open { onOpenMessage(hit.chatId, hit.messageId) } },
            )
        }
    }
}

@Composable
private fun SearchLeadingIcon(bar: SearchBarState, onBack: () -> Unit) {
    if (bar.currentValue == SearchBarValue.Expanded) {
        IconButton(onClick = onBack) { Icon(MaterialSymbols.Rounded.Arrow_back, contentDescription = stringResource(R.string.common_back)) }
    } else {
        Icon(MaterialSymbols.Rounded.Search, contentDescription = null)
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onForget: (String) -> Unit) {
    LazyColumn(Modifier.imePadding()) {
        items(recent, key = { it }) { query ->
            ListItem(
                onClick = { onPick(query) },
                onLongClick = { onForget(query) },
                leadingContent = { Icon(MaterialSymbols.Rounded.History, contentDescription = null) },
                trailingContent = { Icon(MaterialSymbols.Rounded.North_west, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.animateItem(),
            ) { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun SearchResultsList(results: SearchResults, onChat: (String) -> Unit, onMessage: (MessageResult) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = Modifier.imePadding(),
    ) {
        if (results.chats.isNotEmpty()) {
            item(key = "chats-header") { SectionHeader(stringResource(R.string.home_search_section_chats), topPadding = 8.dp) }
            items(results.chats, key = { "chat-${it.chatId}" }) { chat ->
                ListItem(
                    onClick = { onChat(chat.chatId) },
                    leadingContent = { ChatBadge(chat.look, size = 40.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.animateItem(),
                ) { Text(chat.title.highlighted(chat.match, highlight), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (results.messages.isNotEmpty()) {
            item(key = "messages-header") { SectionHeader(stringResource(R.string.home_search_section_messages)) }
            items(results.messages, key = { "message-${it.messageId}" }) { hit ->
                ListItem(
                    onClick = { onMessage(hit) },
                    overlineContent = {
                        Text(
                            stringResource(if (hit.fromUser) R.string.home_search_from_you else R.string.home_search_from_agent, hit.chatTitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = { Text(RelativeTime.format(hit.createdAt, locale)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.animateItem(),
                ) { Text(hit.snippet.text.highlighted(hit.snippet.match, highlight), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun String.highlighted(match: IntRange?, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(this@highlighted)
    if (match != null && match.first >= 0 && match.last < length) addStyle(style, match.first, match.last + 1)
}
