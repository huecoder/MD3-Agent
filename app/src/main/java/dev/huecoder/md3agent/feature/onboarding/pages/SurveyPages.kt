package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Auto_fix_high
import com.composables.icons.materialsymbols.rounded.Block
import com.composables.icons.materialsymbols.rounded.Chat
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Key
import com.composables.icons.materialsymbols.rounded.Install_mobile
import com.composables.icons.materialsymbols.rounded.Layers
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Notifications_active
import com.composables.icons.materialsymbols.rounded.Screenshot_monitor
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Touch_app
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ModePreset
import dev.huecoder.md3agent.feature.mode.icon
import dev.huecoder.md3agent.feature.mode.summaryRes
import dev.huecoder.md3agent.feature.mode.titleRes
import dev.huecoder.md3agent.feature.onboarding.ChoiceCard
import dev.huecoder.md3agent.feature.onboarding.Experience
import dev.huecoder.md3agent.feature.onboarding.Goal
import dev.huecoder.md3agent.feature.onboarding.OnboardingState
import dev.huecoder.md3agent.feature.onboarding.PageScaffold
import dev.huecoder.md3agent.feature.onboarding.Place
import dev.huecoder.md3agent.feature.onboarding.SurveyAnswers
import dev.huecoder.md3agent.feature.onboarding.reveal

@Composable
fun GoalsPage(answers: SurveyAnswers, onToggle: (Goal) -> Unit) {
    PageScaffold(
        title = stringResource(R.string.onboarding_goals_title),
        body = stringResource(R.string.onboarding_pick_several),
        hint = stringResource(R.string.onboarding_survey_hint),
    ) {
        Goal.entries.forEachIndexed { index, goal ->
            ChoiceCard(
                icon = goal.icon,
                label = stringResource(goal.label),
                selected = goal in answers.goals,
                onClick = { onToggle(goal) },
                modifier = Modifier.reveal(minOf(2 + index, LAST_CARD_ORDER)),
            )
        }
    }
}

@Composable
fun ExperiencePage(answers: SurveyAnswers, onToggle: (Experience) -> Unit) {
    PageScaffold(
        title = stringResource(R.string.onboarding_experience_title),
        body = stringResource(R.string.onboarding_experience_body),
        hint = stringResource(R.string.onboarding_survey_hint),
    ) {
        Experience.entries.forEachIndexed { index, item ->
            ChoiceCard(
                icon = item.icon,
                label = stringResource(item.label),
                selected = item in answers.experience,
                onClick = { onToggle(item) },
                modifier = Modifier.reveal(minOf(2 + index, LAST_CARD_ORDER)),
            )
        }
    }
}

@Composable
fun PlacesPage(answers: SurveyAnswers, onToggle: (Place) -> Unit) {
    PageScaffold(
        title = stringResource(R.string.onboarding_places_title),
        body = stringResource(R.string.onboarding_pick_several),
        hint = stringResource(R.string.onboarding_survey_hint),
    ) {
        Place.entries.forEachIndexed { index, place ->
            ChoiceCard(
                icon = place.icon,
                label = stringResource(place.label),
                selected = place in answers.places,
                onClick = { onToggle(place) },
                modifier = Modifier.reveal(minOf(2 + index, LAST_CARD_ORDER)),
            )
        }
    }
}

@Composable
fun ModePage(state: OnboardingState, onChoose: (ModePreset) -> Unit) {
    val recommended = state.recommended
    PageScaffold(
        title = stringResource(R.string.onboarding_mode_title),
        body = recommended?.let { stringResource(R.string.onboarding_mode_body_recommended, stringResource(it.titleRes)) }
            ?: stringResource(R.string.onboarding_mode_body_none),
        hint = stringResource(R.string.onboarding_mode_hint),
    ) {
        ModePreset.entries.forEachIndexed { index, preset ->
            ChoiceCard(
                icon = preset.icon,
                label = stringResource(preset.titleRes),
                summary = stringResource(preset.summaryRes),
                tag = stringResource(R.string.onboarding_mode_recommended).takeIf { preset == recommended },
                selected = preset == state.preset,
                dimmed = state.preset != null,
                onClick = { onChoose(preset) },
                modifier = Modifier.reveal(minOf(2 + index, LAST_CARD_ORDER)),
            )
        }
    }
}

private val Goal.icon: ImageVector
    get() = when (this) {
        Goal.ASK -> MaterialSymbols.Rounded.Chat
        Goal.SCREEN -> MaterialSymbols.Rounded.Screenshot_monitor
        Goal.REMINDERS -> MaterialSymbols.Rounded.Notifications_active
        Goal.VOICE_CONTROL -> MaterialSymbols.Rounded.Touch_app
        Goal.FILES_CODE -> MaterialSymbols.Rounded.Code
        Goal.AUTOMATE -> MaterialSymbols.Rounded.Auto_fix_high
    }

@get:StringRes
private val Goal.label: Int
    get() = when (this) {
        Goal.ASK -> R.string.onboarding_goal_ask
        Goal.SCREEN -> R.string.onboarding_goal_screen
        Goal.REMINDERS -> R.string.onboarding_goal_reminders
        Goal.VOICE_CONTROL -> R.string.onboarding_goal_voice_control
        Goal.FILES_CODE -> R.string.onboarding_goal_files_code
        Goal.AUTOMATE -> R.string.onboarding_goal_automate
    }

private val Experience.icon: ImageVector
    get() = when (this) {
        Experience.API_KEY -> MaterialSymbols.Rounded.Key
        Experience.TERMINAL -> MaterialSymbols.Rounded.Terminal
        Experience.SIDELOAD -> MaterialSymbols.Rounded.Install_mobile
        Experience.NONE -> MaterialSymbols.Rounded.Block
    }

@get:StringRes
private val Experience.label: Int
    get() = when (this) {
        Experience.API_KEY -> R.string.onboarding_experience_api_key
        Experience.TERMINAL -> R.string.onboarding_experience_terminal
        Experience.SIDELOAD -> R.string.onboarding_experience_sideload
        Experience.NONE -> R.string.onboarding_experience_none
    }

private val Place.icon: ImageVector
    get() = when (this) {
        Place.CHAT -> MaterialSymbols.Rounded.Chat
        Place.OVERLAY -> MaterialSymbols.Rounded.Layers
        Place.VOICE -> MaterialSymbols.Rounded.Mic
    }

@get:StringRes
private val Place.label: Int
    get() = when (this) {
        Place.CHAT -> R.string.onboarding_place_chat
        Place.OVERLAY -> R.string.onboarding_place_overlay
        Place.VOICE -> R.string.onboarding_place_voice
    }

private const val LAST_CARD_ORDER = 5
