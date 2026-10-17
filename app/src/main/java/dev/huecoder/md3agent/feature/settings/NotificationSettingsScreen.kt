package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Shield_question
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApprovalSurface
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_notifications), onBack) { padding ->
        val current = settings?.notifications ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding)) {
            SectionHeader(stringResource(R.string.notifications_section_approvals), topPadding = 8.dp)
            CardGroup {
                card { shape ->
                    SettingCard(
                        icon = MaterialSymbols.Rounded.Shield_question,
                        title = stringResource(R.string.notifications_approvals),
                        summary = stringResource(current.approvals.hint),
                        shape = shape,
                        indentBelow = false,
                        below = {
                            ChoicePill(
                                APPROVAL_CHOICES,
                                current.approvals,
                                { surface -> viewModel.updateNotifications { it.copy(approvals = surface) } },
                                Modifier.fillMaxWidth(),
                                fillWidth = true,
                                height = CHOICE_HEIGHT,
                            )
                        },
                    )
                }
            }
        }
    }
}

private val APPROVAL_CHOICES = listOf(
    Choice(ApprovalSurface.NOTIFICATION, R.string.notifications_approvals_notification),
    Choice(ApprovalSurface.OVERLAY, R.string.notifications_approvals_overlay),
)

@get:StringRes
private val ApprovalSurface.hint: Int
    get() = when (this) {
        ApprovalSurface.NOTIFICATION -> R.string.notifications_approvals_notification_hint
        ApprovalSurface.OVERLAY -> R.string.notifications_approvals_overlay_hint
    }
