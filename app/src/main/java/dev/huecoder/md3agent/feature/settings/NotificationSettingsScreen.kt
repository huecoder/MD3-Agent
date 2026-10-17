package dev.huecoder.md3agent.feature.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Lock
import com.composables.icons.materialsymbols.rounded.Notification_settings
import com.composables.icons.materialsymbols.rounded.Shield_question
import com.composables.icons.materialsymbols.rounded.Task_alt
import com.composables.icons.materialsymbols.rounded.Timer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApprovalSurface
import dev.huecoder.md3agent.data.settings.ChipContent
import dev.huecoder.md3agent.data.settings.LockScreenDetail
import dev.huecoder.md3agent.data.settings.NotificationSettings
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var liveBlocked by remember { mutableStateOf(context.liveUpdatesBlocked()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { liveBlocked = context.liveUpdatesBlocked() }
    val update: ((NotificationSettings) -> NotificationSettings) -> Unit = { change -> viewModel.updateNotifications(change) }

    SettingsScaffold(stringResource(R.string.settings_notifications), onBack) { padding ->
        val current = settings?.notifications ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding)) {
            SectionHeader(stringResource(R.string.notifications_section_live), topPadding = 8.dp)
            CardGroup {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
                    card { shape ->
                        SettingCard(
                            icon = MaterialSymbols.Rounded.Timer,
                            title = stringResource(R.string.notifications_live),
                            summary = stringResource(R.string.notifications_live_unavailable),
                            shape = shape,
                        )
                    }
                } else {
                    liveCards(current, liveBlocked, update, context)
                }
            }

            SectionHeader(stringResource(R.string.notifications_section_approvals))
            CardGroup {
                card { shape ->
                    ChoiceSettingCard(
                        icon = MaterialSymbols.Rounded.Shield_question,
                        title = R.string.notifications_approvals,
                        hint = current.approvals.hint,
                        choices = APPROVAL_CHOICES,
                        selected = current.approvals,
                        onSelect = { surface -> update { it.copy(approvals = surface) } },
                        shape = shape,
                    )
                }
            }

            SectionHeader(stringResource(R.string.notifications_section_results))
            CardGroup {
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Task_alt,
                        title = stringResource(R.string.notifications_done),
                        summary = stringResource(R.string.notifications_done_summary),
                        checked = current.taskDone,
                        onCheckedChange = { value -> update { it.copy(taskDone = value) } },
                        shape = shape,
                    )
                }
            }

            SectionHeader(stringResource(R.string.notifications_section_lock))
            CardGroup {
                card { shape ->
                    ChoiceSettingCard(
                        icon = MaterialSymbols.Rounded.Lock,
                        title = R.string.notifications_lock,
                        hint = current.lockScreen.hint,
                        choices = LOCK_CHOICES,
                        selected = current.lockScreen,
                        onSelect = { detail -> update { it.copy(lockScreen = detail) } },
                        shape = shape,
                    )
                }
            }
        }
    }
}

private fun CardGroupScope.liveCards(
    current: NotificationSettings,
    blocked: Boolean,
    update: ((NotificationSettings) -> NotificationSettings) -> Unit,
    context: Context,
) {
    card("live") { shape ->
        SwitchCard(
            icon = MaterialSymbols.Rounded.Timer,
            title = stringResource(R.string.notifications_live),
            summary = stringResource(R.string.notifications_live_summary),
            checked = current.liveStatus,
            onCheckedChange = { value -> update { it.copy(liveStatus = value) } },
            shape = shape,
        )
    }
    if (blocked && current.liveStatus) {
        card("blocked") { shape ->
            SettingCard(
                icon = MaterialSymbols.Rounded.Notification_settings,
                title = stringResource(R.string.notifications_live_blocked),
                shape = shape,
                indentBelow = false,
                below = {
                    FilledTonalButton(onClick = { context.openNotificationSettings() }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.access_action_open_settings))
                    }
                },
            )
        }
    }
    if (current.liveStatus) {
        card("chip") { shape ->
            SettingCard(
                icon = MaterialSymbols.Rounded.Timer,
                title = stringResource(R.string.notifications_chip),
                summary = stringResource(R.string.notifications_chip_hint),
                shape = shape,
                indentBelow = false,
                below = {
                    ChoicePill(
                        CHIP_CHOICES,
                        current.chip,
                        { content -> update { it.copy(chip = content) } },
                        Modifier.fillMaxWidth(),
                        fillWidth = true,
                        height = CHOICE_HEIGHT,
                    )
                    ChipPreview(current.chip, Modifier.padding(top = 12.dp))
                },
            )
        }
    }
}

@Composable
private fun <T> ChoiceSettingCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes hint: Int,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    shape: Shape,
) {
    SettingCard(
        icon = icon,
        title = stringResource(title),
        summary = stringResource(hint),
        shape = shape,
        indentBelow = false,
        below = { ChoicePill(choices, selected, onSelect, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
    )
}

private fun Context.liveUpdatesBlocked(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

private fun Context.openNotificationSettings() =
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

private val APPROVAL_CHOICES = listOf(
    Choice(ApprovalSurface.NOTIFICATION, R.string.notifications_approvals_notification),
    Choice(ApprovalSurface.OVERLAY, R.string.notifications_approvals_overlay),
)

private val CHIP_CHOICES = listOf(
    Choice(ChipContent.TIME, R.string.notifications_chip_time),
    Choice(ChipContent.TOKENS, R.string.notifications_chip_tokens),
    Choice(ChipContent.TIME_AND_TOKENS, R.string.notifications_chip_both),
)

private val LOCK_CHOICES = listOf(
    Choice(LockScreenDetail.EVERYTHING, R.string.notifications_lock_everything),
    Choice(LockScreenDetail.STATUS_ONLY, R.string.notifications_lock_status),
    Choice(LockScreenDetail.NOTHING, R.string.notifications_lock_nothing),
)

@get:StringRes
private val ApprovalSurface.hint: Int
    get() = when (this) {
        ApprovalSurface.NOTIFICATION -> R.string.notifications_approvals_notification_hint
        ApprovalSurface.OVERLAY -> R.string.notifications_approvals_overlay_hint
    }

@get:StringRes
private val LockScreenDetail.hint: Int
    get() = when (this) {
        LockScreenDetail.EVERYTHING -> R.string.notifications_lock_everything_hint
        LockScreenDetail.STATUS_ONLY -> R.string.notifications_lock_status_hint
        LockScreenDetail.NOTHING -> R.string.notifications_lock_nothing_hint
    }
