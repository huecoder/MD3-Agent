package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Arrow_forward
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Translate
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import dev.huecoder.md3agent.ui.locale.AppLanguage

enum class BarLeading { NONE, BACK, LANGUAGE }

@Composable
fun OnboardingBar(
    progress: () -> Float,
    leading: BarLeading,
    language: AppLanguage,
    pillLabel: String,
    onBack: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onPill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = OnboardingMotion.ContentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearWavyProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (leading) {
                BarLeading.BACK -> RoundButton(onBack) { Icon(MaterialSymbols.Rounded.Arrow_back, contentDescription = stringResource(R.string.common_back)) }
                BarLeading.LANGUAGE -> LanguageButton(language, onLanguage)
                BarLeading.NONE -> Unit
            }
            Button(onClick = onPill, shape = CircleShape, modifier = Modifier.weight(1f).height(OnboardingMotion.BarHeight)) {
                AnimatedContent(pillLabel, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pill-label") { label ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Icon(MaterialSymbols.Rounded.Arrow_forward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(OnboardingMotion.BarHeight)) { content() }
}

@Composable
private fun LanguageButton(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton({ open = true }) { Icon(MaterialSymbols.Rounded.Translate, contentDescription = stringResource(R.string.onboarding_language)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.clickHaptics()) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.nativeName) },
                    trailingIcon = if (option == language) {
                        { Icon(MaterialSymbols.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onLanguage(option)
                    },
                )
            }
        }
    }
}
