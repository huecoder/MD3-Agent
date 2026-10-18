package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.feature.onboarding.PageScaffold
import dev.huecoder.md3agent.feature.onboarding.SettingsHint
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.feature.onboarding.sims.ScreenScene

@Composable
fun AssistantPage() {
    val context = LocalContext.current
    var held by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { held = AssistantRole.isHeld(context) }

    PageScaffold(
        title = stringResource(R.string.onboarding_assistant_title),
        body = stringResource(R.string.onboarding_assistant_body),
    ) {
        ScreenScene(Modifier.reveal(2))
        if (held) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.reveal(3)) {
                Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.onboarding_assistant_ready), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.assistant_invoke_system_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.reveal(4),
            )
        } else {
            Button(onClick = { AssistantRole.openSystemPicker(context) }, modifier = Modifier.fillMaxWidth().reveal(3)) {
                Text(stringResource(R.string.onboarding_assistant_choose))
            }
        }
        SettingsHint(modifier = Modifier.reveal(5))
    }
}
