package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Face
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Shield_person
import com.composables.icons.materialsymbols.rounded.Tune
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.feature.mode.titleRes
import dev.huecoder.md3agent.feature.onboarding.OnboardingMotion
import dev.huecoder.md3agent.feature.onboarding.OnboardingState
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.ui.components.AgentAvatar

@Composable
fun DonePage(state: OnboardingState, departing: Boolean) {
    val look = state.agentPick?.let(state.cluster::getOrNull) ?: state.currentLook
    val context = LocalContext.current
    val assistantHeld = remember { AssistantRole.isHeld(context) }
    val lift by animateFloatAsState(if (departing) 1f else 0f, tween(OnboardingMotion.DEPART_MILLIS, easing = FastOutSlowInEasing), label = "depart")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = OnboardingMotion.ContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .reveal(0)
                .graphicsLayer {
                    translationY = -lift * DEPART_RISE.dp.toPx()
                    val scale = 1f - lift * DEPART_SHRINK
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - lift
                },
        ) {
            AgentAvatar(isWorking = false, size = AVATAR_SIZE, look = look, interactive = true)
        }
        Text(stringResource(R.string.onboarding_done_title), style = MaterialTheme.typography.headlineLargeEmphasized, textAlign = TextAlign.Center, modifier = Modifier.reveal(1))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().reveal(2)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Summary(stringResource(R.string.onboarding_done_ai, state.providerName ?: stringResource(R.string.onboarding_done_ai_none)))
                state.preset?.let { Summary(stringResource(R.string.onboarding_done_mode, stringResource(it.titleRes))) }
                if (state.needs.assistant) {
                    Summary(stringResource(if (assistantHeld) R.string.onboarding_done_assistant_yes else R.string.onboarding_done_assistant_later))
                }
            }
        }
        Column(Modifier.fillMaxWidth().reveal(3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.onboarding_done_where), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Where(MaterialSymbols.Rounded.Hub, R.string.onboarding_where_models, R.string.settings_models)
            Where(MaterialSymbols.Rounded.Tune, R.string.onboarding_where_mode, R.string.settings_mode)
            Where(MaterialSymbols.Rounded.Face, R.string.onboarding_where_agent, R.string.settings_agent)
            Where(MaterialSymbols.Rounded.Shield_person, R.string.onboarding_where_access, R.string.settings_access)
        }
    }
}

@Composable
private fun Summary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Where(icon: ImageVector, @StringRes what: Int, @StringRes section: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column {
            Text(stringResource(what), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.onboarding_where_path, stringResource(R.string.settings_title), stringResource(section)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AVATAR_SIZE = 132.dp
private const val DEPART_RISE = 320f
private const val DEPART_SHRINK = 0.55f
