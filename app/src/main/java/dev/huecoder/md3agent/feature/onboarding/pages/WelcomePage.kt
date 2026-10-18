package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.OnboardingMotion
import dev.huecoder.md3agent.feature.onboarding.rememberDeliberateFling
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.locale.AppLanguage
import dev.huecoder.md3agent.ui.locale.LocalizedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun WelcomePage(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    val languages = AppLanguage.entries
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = language.ordinal) { languages.size }
    val current by rememberUpdatedState(language)
    val nudge = with(LocalDensity.current) { OnboardingMotion.NUDGE.toPx() }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val chosen = languages[page]
            if (chosen != current) {
                haptics.tick()
                onLanguage(chosen)
            }
        }
    }
    LaunchedEffect(language) { if (pager.settledPage != language.ordinal) pager.animateScrollToPage(language.ordinal, animationSpec = OnboardingMotion.SnapSpring) }
    LaunchedEffect(Unit) {
        delay(NUDGE_DELAY_MS)
        if (pager.isScrollInProgress || pager.settledPage != language.ordinal) return@LaunchedEffect
        val toward = if (language.ordinal < languages.lastIndex) 1f else -1f
        pager.animateScrollBy(toward * nudge, OnboardingMotion.SnapSpring)
        pager.animateScrollBy(-toward * nudge, OnboardingMotion.SnapSpring)
    }

    val nextLabel = stringResource(R.string.onboarding_language_next)
    val previousLabel = stringResource(R.string.onboarding_language_previous)
    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { scope.launch { pager.animateScrollToPage(language.next(1).ordinal) }; true },
                    CustomAccessibilityAction(previousLabel) { scope.launch { pager.animateScrollToPage(language.next(-1).ordinal) }; true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pager,
            flingBehavior = rememberDeliberateFling(pager),
            modifier = Modifier.fillMaxSize().reveal(0),
        ) { index ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LocalizedContent(languages[index]) { Greeting() }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).reveal(2),
        ) {
            languages.forEach { lang ->
                Text(
                    lang.nativeName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        val position = pager.currentPage + pager.currentPageOffsetFraction
                        alpha = DIM + (1f - DIM) * (1f - abs(lang.ordinal - position)).coerceIn(0f, 1f)
                    },
                )
            }
        }
    }
}

@Composable
private fun Greeting() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = OnboardingMotion.ContentPadding),
    ) {
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displayLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val NUDGE_DELAY_MS = 1_400L
private const val DIM = 0.4f
