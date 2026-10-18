package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.PageScaffold
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.feature.onboarding.sims.AskScene

@Composable
fun AboutPage() {
    PageScaffold(
        title = stringResource(R.string.onboarding_about_title),
        body = stringResource(R.string.onboarding_about_body),
    ) {
        AskScene(Modifier.reveal(2))
    }
}
