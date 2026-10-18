package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.OnboardingMotion
import dev.huecoder.md3agent.feature.onboarding.SettingsHint
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.feature.onboarding.sims.AskScene
import dev.huecoder.md3agent.feature.onboarding.sims.CodeScene
import dev.huecoder.md3agent.feature.onboarding.sims.MemoryScene
import dev.huecoder.md3agent.feature.onboarding.sims.PhoneScene
import dev.huecoder.md3agent.feature.onboarding.sims.ScreenScene

private enum class Ability(@StringRes val title: Int, @StringRes val caption: Int) {
    ASK(R.string.onboarding_ability_ask, R.string.onboarding_ability_ask_caption),
    SCREEN(R.string.onboarding_ability_screen, R.string.onboarding_ability_screen_caption),
    PHONE(R.string.onboarding_ability_phone, R.string.onboarding_ability_phone_caption),
    MEMORY(R.string.onboarding_ability_memory, R.string.onboarding_ability_memory_caption),
    CODE(R.string.onboarding_ability_code, R.string.onboarding_ability_code_caption),
}

@Composable
fun AbilitiesPage() {
    val list = rememberLazyListState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_abilities_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            modifier = Modifier.padding(horizontal = OnboardingMotion.ContentPadding).reveal(0),
        )
        Text(
            stringResource(R.string.onboarding_abilities_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = OnboardingMotion.ContentPadding).reveal(1),
        )
        @OptIn(ExperimentalFoundationApi::class)
        LazyRow(
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            contentPadding = PaddingValues(horizontal = OnboardingMotion.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.reveal(2),
        ) {
            items(Ability.entries) { ability -> AbilityCard(ability) }
        }
        SettingsHint(modifier = Modifier.padding(horizontal = OnboardingMotion.ContentPadding).reveal(3))
    }
}

@Composable
private fun AbilityCard(ability: Ability) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(CARD_WIDTH)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (ability) {
                Ability.ASK -> AskScene()
                Ability.SCREEN -> ScreenScene()
                Ability.PHONE -> PhoneScene()
                Ability.MEMORY -> MemoryScene()
                Ability.CODE -> CodeScene()
            }
            Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(ability.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(ability.caption), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val CARD_WIDTH = 280.dp
