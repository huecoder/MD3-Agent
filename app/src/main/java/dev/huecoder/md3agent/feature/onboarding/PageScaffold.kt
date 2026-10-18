package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Settings
import dev.huecoder.md3agent.R

@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    hint: String? = null,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OnboardingMotion.ContentPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, if (centered) Alignment.CenterVertically else Alignment.Top),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLargeEmphasized, textAlign = align, modifier = Modifier.reveal(0))
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align, modifier = Modifier.reveal(1))
        }
        content()
        hint?.let { SettingsHint(it, Modifier.reveal(HINT_ORDER)) }
    }
}

@Composable
fun SettingsHint(text: String = stringResource(R.string.onboarding_changeable), modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(MaterialSymbols.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

const val HINT_ORDER = 6
