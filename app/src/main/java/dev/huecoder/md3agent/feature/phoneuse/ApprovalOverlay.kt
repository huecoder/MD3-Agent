package dev.huecoder.md3agent.feature.phoneuse

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.data.settings.ApprovalSurface
import dev.huecoder.md3agent.feature.chat.ToolCallUi
import dev.huecoder.md3agent.feature.chat.awaitingApprovalUi
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.ui.avatar.ProvideAgentLook
import dev.huecoder.md3agent.ui.haptics.ProvideHaptics
import dev.huecoder.md3agent.ui.theme.AssistantTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ApprovalOverlay(private val service: AccessibilityService, private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var window: OverlayWindow? = null

    fun start() {
        scope.launch {
            requests().distinctUntilChanged().collect { request ->
                window?.hide()
                window = request?.let(::show)
            }
        }
    }

    fun stop() {
        window?.hide()
        window = null
        scope.cancel()
    }

    private fun requests(): Flow<ApprovalRequest?> {
        val session = container.phoneControl.session
        val surface = container.settings.settings.map { it.notifications.approvals }.distinctUntilChanged()
        return combine(container.conversations.drafts, container.visibility.isVisible, surface, session.chatId, session.settings) { drafts, appVisible, chosen, phoneChat, phone ->
            if (appVisible || chosen != ApprovalSurface.OVERLAY) return@combine null
            drafts.entries.firstNotNullOfOrNull { (chatId, draft) ->
                val call = draft.toolCalls.firstOrNull { it.id == draft.awaitingCallId }
                val askedByPhoneOverlay = chatId == phoneChat && phone.overlay
                if (call == null || askedByPhoneOverlay) null else ApprovalRequest(chatId, call)
            }
        }
    }

    private fun show(request: ApprovalRequest): OverlayWindow =
        OverlayWindow(service, windowManager, modalParams()) { Content(request, request.call.awaitingApprovalUi()) }.also { it.show() }

    @Composable
    private fun Content(request: ApprovalRequest, call: ToolCallUi) {
        AssistantTheme {
            ProvideAgentLook {
                ProvideHaptics {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        PhoneModal(
                            ModalRequest.Approval(call),
                            ModalActions(
                                onAnswer = { _, _ -> },
                                onApproval = { shown, decision -> container.approvals.respond(request.chatId, shown.id, decision) },
                                onStopAgent = { container.conversations.cancel(request.chatId) },
                                onKeepWorking = {},
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun modalParams() = LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private data class ApprovalRequest(val chatId: String, val call: ToolCall)
}
