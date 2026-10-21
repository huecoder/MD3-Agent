package dev.huecoder.md3agent.feature.onboarding.pages

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Content_paste
import com.composables.icons.materialsymbols.rounded.Key
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.feature.models.ModelPickerSheet
import dev.huecoder.md3agent.feature.onboarding.ChoiceCard
import dev.huecoder.md3agent.feature.onboarding.ConnectResult
import dev.huecoder.md3agent.feature.onboarding.LATE_ORDER
import dev.huecoder.md3agent.feature.onboarding.OnboardingState
import dev.huecoder.md3agent.feature.onboarding.PageScaffold
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.feature.settings.SecretField
import kotlinx.coroutines.launch

private enum class KeyBranch { HAS, NONE }

private sealed interface ConnectStatus {
    data object Idle : ConnectStatus

    data object Checking : ConnectStatus

    data class Verified(val models: Int) : ConnectStatus

    data class Unverified(val reason: String) : ConnectStatus
}

private val SERVICES = listOf(ApiFormat.OPENAI, ApiFormat.ANTHROPIC, ApiFormat.GEMINI)

@Composable
fun ProviderPage(
    state: OnboardingState,
    onConnect: suspend (ApiFormat, String, String) -> ConnectResult,
    onPickModel: (ModelSelection) -> Unit,
    onCustom: () -> Unit,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var branch by rememberSaveable { mutableStateOf<KeyBranch?>(null) }
    var format by rememberSaveable { mutableStateOf(ApiFormat.GEMINI) }
    var key by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<ConnectStatus>(ConnectStatus.Idle) }
    val serviceName = stringResource(format.nameRes)

    LaunchedEffect(status) { if (status is ConnectStatus.Verified && state.model == null) picking = true }

    PageScaffold(
        title = stringResource(R.string.onboarding_provider_title),
        body = stringResource(R.string.onboarding_provider_body),
    ) {
        if (state.providerName != null) {
            Connected(state.providerName, status, state.model, onPick = { picking = true }, modifier = Modifier.reveal(2))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.reveal(2)) {
                ChoiceCard(
                    icon = MaterialSymbols.Rounded.Key,
                    label = stringResource(R.string.onboarding_provider_has_key),
                    selected = branch == KeyBranch.HAS,
                    onClick = { branch = KeyBranch.HAS },
                )
                ChoiceCard(
                    icon = MaterialSymbols.Rounded.Open_in_new,
                    label = stringResource(R.string.onboarding_provider_no_key),
                    selected = branch == KeyBranch.NONE,
                    onClick = { branch = KeyBranch.NONE },
                )
            }
            branch?.let {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SERVICES.forEach { service ->
                            FilterChip(selected = format == service, onClick = { format = service }, label = { Text(stringResource(service.nameRes)) })
                        }
                    }
                    if (it == KeyBranch.HAS) {
                        SecretField(value = key, onValueChange = { value -> key = value }, label = stringResource(R.string.onboarding_provider_key_label))
                        Text(stringResource(R.string.onboarding_provider_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { scope.launch { clipboard.pastedText()?.let { pasted -> key = pasted } } }) {
                                Icon(MaterialSymbols.Rounded.Content_paste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.onboarding_provider_paste), Modifier.padding(start = 8.dp))
                            }
                            TextButton(onClick = onCustom) { Text(stringResource(R.string.onboarding_provider_custom)) }
                        }
                        Button(
                            enabled = key.isNotBlank() && status != ConnectStatus.Checking,
                            onClick = {
                                status = ConnectStatus.Checking
                                scope.launch {
                                    status = when (val result = onConnect(format, serviceName, key)) {
                                        is ConnectResult.Verified -> ConnectStatus.Verified(result.models)
                                        is ConnectResult.Unverified -> ConnectStatus.Unverified(result.reason)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(if (status == ConnectStatus.Checking) R.string.onboarding_provider_checking else R.string.onboarding_provider_connect)) }
                        (status as? ConnectStatus.Unverified)?.let {
                            Text(stringResource(R.string.onboarding_provider_unverified, it.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        KeySteps(detailed = state.needs.keyHelpInDetail)
                        FilledTonalButton(
                            onClick = { context.openUrl(format.keyPageUrl) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_provider_open_keys, serviceName)) }
                        TextButton(onClick = { branch = KeyBranch.HAS }) { Text(stringResource(R.string.onboarding_provider_got_it)) }
                    }
                }
            }
        }
        if (state.providerName == null) {
            TextButton(onClick = onLater, modifier = Modifier.reveal(LATE_ORDER)) { Text(stringResource(R.string.onboarding_provider_later)) }
        }
    }

    if (picking) {
        ModelPickerSheet(model = state.model, agentId = null, onSelectModel = onPickModel, onSelectAgent = {}, onDismiss = { picking = false })
    }
}

@Composable
private fun Connected(name: String, status: ConnectStatus, model: ModelSelection?, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.onboarding_provider_connected, name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            when (status) {
                is ConnectStatus.Verified -> Text(stringResource(R.string.onboarding_provider_verified, status.models), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                is ConnectStatus.Unverified -> Text(stringResource(R.string.onboarding_provider_unverified, status.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                else -> Unit
            }
            FilledTonalButton(onClick = onPick) { Text(model?.modelId ?: stringResource(R.string.onboarding_provider_pick_model)) }
        }
    }
}

@Composable
private fun KeySteps(detailed: Boolean) {
    if (!detailed) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.onboarding_provider_step_1, R.string.onboarding_provider_step_2, R.string.onboarding_provider_step_3).forEachIndexed { index, text ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private suspend fun androidx.compose.ui.platform.Clipboard.pastedText(): String? =
    getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

private fun android.content.Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

@get:StringRes
private val ApiFormat.nameRes: Int
    get() = when (this) {
        ApiFormat.OPENAI -> R.string.api_format_openai
        ApiFormat.ANTHROPIC -> R.string.api_format_anthropic
        ApiFormat.GEMINI -> R.string.api_format_gemini
        ApiFormat.ELEVENLABS -> R.string.api_format_elevenlabs
    }

private val ApiFormat.keyPageUrl: String
    get() = when (this) {
        ApiFormat.OPENAI -> "https://platform.openai.com/api-keys"
        ApiFormat.ANTHROPIC -> "https://platform.claude.com/settings/keys"
        ApiFormat.GEMINI -> "https://aistudio.google.com/apikey"
        ApiFormat.ELEVENLABS -> "https://elevenlabs.io/app/settings/api-keys"
    }
