package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.AgentCloud
import dev.huecoder.md3agent.feature.onboarding.OnboardingMotion
import dev.huecoder.md3agent.feature.onboarding.OnboardingState
import dev.huecoder.md3agent.feature.onboarding.SettingsHint
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.ui.components.FormField

@Composable
fun AgentsPage(state: OnboardingState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = OnboardingMotion.ContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_agents_title),
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.reveal(0),
        )
        Box(Modifier.weight(1f).fillMaxWidth().reveal(1), contentAlignment = Alignment.Center) {
            AgentCloud(state.cluster, interactive = true, modifier = Modifier.widthIn(max = CLOUD_MAX_WIDTH).fillMaxWidth().aspectRatio(1f))
        }
        Text(
            stringResource(R.string.onboarding_agents_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.reveal(2),
        )
    }
}

@Composable
fun FirstAgentPage(state: OnboardingState, onPick: (Int) -> Unit, onName: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = OnboardingMotion.ContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_first_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.reveal(0),
        )
        Text(
            stringResource(R.string.onboarding_first_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.reveal(1),
        )
        Box(Modifier.weight(1f).fillMaxWidth().reveal(2), contentAlignment = Alignment.Center) {
            AgentCloud(
                state.cluster,
                selected = state.agentPick,
                onSelect = onPick,
                modifier = Modifier.widthIn(max = CLOUD_MAX_WIDTH).fillMaxWidth().aspectRatio(1f),
            )
        }
        FormField(
            value = state.agentName,
            onValueChange = onName,
            label = stringResource(R.string.onboarding_first_name),
            modifier = Modifier.fillMaxWidth().reveal(3),
        )
        SettingsHint(modifier = Modifier.reveal(4))
    }
}

private val CLOUD_MAX_WIDTH = 360.dp
