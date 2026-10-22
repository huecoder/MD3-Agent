package dev.huecoder.md3agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.ui.avatar.ProvideAgentLook
import dev.huecoder.md3agent.ui.haptics.ProvideHaptics
import dev.huecoder.md3agent.ui.keyboard.KeyboardGuard
import dev.huecoder.md3agent.ui.locale.AppLocale
import dev.huecoder.md3agent.ui.navigation.AppNavHost
import dev.huecoder.md3agent.ui.navigation.LaunchTarget
import dev.huecoder.md3agent.ui.theme.AssistantTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingChatId = MutableStateFlow<String?>(null)
    private val pendingTarget = MutableStateFlow<LaunchTarget?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consume(intent)
        setContent {
            val chatToOpen by pendingChatId.collectAsStateWithLifecycle()
            val target by pendingTarget.collectAsStateWithLifecycle()
            AssistantTheme {
                ProvideAgentLook {
                    ProvideHaptics {
                        KeyboardGuard()
                        AppNavHost(
                            chatToOpen = chatToOpen,
                            onChatOpened = { pendingChatId.value = null },
                            target = target,
                            onTargetHandled = { pendingTarget.value = null },
                        )
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
        LaunchTarget.parse(intent?.getStringExtra(EXTRA_TARGET))?.let { pendingTarget.value = it }
        val container = (application as App).container
        intent?.data?.takeIf(container.mcpSignIn::isCallback)?.let { callback ->
            container.applicationScope.launch { container.mcpSignIn.complete(callback) }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "dev.huecoder.md3agent.extra.CHAT_ID"
        const val EXTRA_TARGET = "dev.huecoder.md3agent.extra.TARGET"

        fun targetIntent(context: Context, target: LaunchTarget): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TARGET, target.name)

        fun chatIntent(context: Context, chatId: String?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { chatId?.let { putExtra(EXTRA_CHAT_ID, it) } }
    }
}
