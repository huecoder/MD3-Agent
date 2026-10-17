package com.mwagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwagent.ui.avatar.ProvideAgentLook
import com.mwagent.ui.haptics.ProvideHaptics
import com.mwagent.ui.navigation.AppNavHost
import com.mwagent.ui.theme.AssistantTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingChatId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consume(intent)
        setContent {
            val chatToOpen by pendingChatId.collectAsStateWithLifecycle()
            AssistantTheme {
                ProvideAgentLook {
                    ProvideHaptics {
                        AppNavHost(chatToOpen = chatToOpen, onChatOpened = { pendingChatId.value = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        intent?.getStringExtra(EXTRA_CHAT_ID)?.let { pendingChatId.value = it }
        val container = (application as App).container
        intent?.data?.takeIf(container.mcpSignIn::isCallback)?.let { callback ->
            container.applicationScope.launch { container.mcpSignIn.complete(callback) }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "com.mwagent.extra.CHAT_ID"

        fun chatIntent(context: Context, chatId: String?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { chatId?.let { putExtra(EXTRA_CHAT_ID, it) } }
    }
}
