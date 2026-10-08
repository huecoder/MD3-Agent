package dev.huecoder.md3agent.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.theme.heroTitle

@Composable
fun HomeScreen(
    onOpenChat: (chatId: String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.chats, it.settings, it.conversations) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_title), style = MaterialTheme.typography.heroTitle) },
                subtitle = state.activeModel?.let { model -> { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                scrollBehavior = topBarScroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                !state.isLoaded -> Unit
                !state.hasProvider -> SetupPrompt(onOpenSettings, Modifier.padding(padding))
                state.groups.isEmpty() -> EmptyChats(Modifier.padding(padding))
                else -> ChatList(
                    groups = state.groups,
                    activity = state.activity,
                    contentPadding = padding,
                    onOpenChat = { onOpenChat(it) },
                    onDeleteRequest = { pendingDeletion = it },
                )
            }
        }
    }

    pendingDeletion?.let { chatId ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.home_delete_chat_title)) },
            text = { Text(stringResource(R.string.home_delete_chat_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(chatId); pendingDeletion = null }) { Text(stringResource(R.string.home_delete_chat)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
        )
    }
}
