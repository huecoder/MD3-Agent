package dev.huecoder.md3agent.feature.onboarding

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.pages.AbilitiesPage
import dev.huecoder.md3agent.feature.onboarding.pages.AboutPage
import dev.huecoder.md3agent.feature.onboarding.pages.AgentsPage
import dev.huecoder.md3agent.feature.onboarding.pages.FirstAgentPage
import dev.huecoder.md3agent.feature.onboarding.pages.ExperiencePage
import dev.huecoder.md3agent.feature.onboarding.pages.GoalsPage
import dev.huecoder.md3agent.feature.onboarding.pages.ModePage
import dev.huecoder.md3agent.feature.onboarding.pages.PlacesPage
import dev.huecoder.md3agent.feature.onboarding.pages.WelcomePage
import dev.huecoder.md3agent.feature.onboarding.scene.BackdropCanvas
import dev.huecoder.md3agent.feature.onboarding.scene.BackdropScene
import dev.huecoder.md3agent.feature.onboarding.scene.SceneMotion
import dev.huecoder.md3agent.feature.onboarding.scene.ScenePlan
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.locale.AppLocale
import dev.huecoder.md3agent.ui.locale.LocalizedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun OnboardingScreen(retake: Boolean, onFinished: (openChat: Boolean) -> Unit) {
    val viewModel = appViewModel(key = "onboarding-$retake") {
        OnboardingViewModel(it.appContext, it.settings, it.mode, PermissionProbe(it.appContext, it.phoneControl, it.deviceFiles, it.shellAccess), retake)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val activity = LocalActivity.current
    LocalizedContent(current.language) {
        OnboardingContent(
            state = current,
            viewModel = viewModel,
            retake = retake,
            onFinished = { openChat ->
                onFinished(openChat)
                if (!retake) activity?.let { AppLocale.apply(it, current.language) }
            },
        )
    }
}

@Composable
private fun OnboardingContent(state: OnboardingState, viewModel: OnboardingViewModel, retake: Boolean, onFinished: (openChat: Boolean) -> Unit) {
    val steps = state.steps
    val pager = rememberPagerState { steps.size }
    val scene = remember { BackdropScene() }
    val poses = remember(steps) { steps.mapIndexed { index, step -> ScenePlan.pose(index, step.calm) } }
    val currentPoses by rememberUpdatedState(poses)
    val currentSteps by rememberUpdatedState(steps)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val animations = remember { ValueAnimator.areAnimatorsEnabled() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val page = pager.currentPage.coerceIn(steps.indices)
    val step = steps[page]

    LaunchedEffect(pager) { snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { scene.position = it } }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { currentSteps.getOrNull(it)?.let(viewModel::markShown) } }
    LaunchedEffect(animations, scene, pager) {
        if (!animations) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { currentSteps.getOrNull(pager.settledPage) == OnboardingStep.WELCOME && !pager.isScrollInProgress }.collectLatest { onWelcome ->
                while (onWelcome) {
                    delay(SceneMotion.IDLE_STRIKE_MILLIS)
                    scene.strike(currentPoses) {}
                }
            }
        }
    }

    fun go(target: Int) {
        if (target !in steps.indices || target == pager.currentPage) return
        haptics.tick()
        scope.launch {
            if (animations) launch { scene.strike(currentPoses) { power -> haptics.impact(power) } }
            pager.animateScrollToPage(target, animationSpec = OnboardingMotion.PageSpring)
        }
    }

    fun finish(skipped: Boolean) {
        scope.launch {
            viewModel.complete(skipped)
            onFinished(!skipped && !retake)
        }
    }

    fun skip() {
        val provider = steps.indexOf(OnboardingStep.PROVIDER)
        if (!retake && provider > page) go(provider) else finish(skipped = true)
    }

    BackHandler(enabled = page > 0) { go(page - 1) }

    val pillLabel = stringResource(
        when {
            step == OnboardingStep.WELCOME -> R.string.onboarding_start
            step == OnboardingStep.DONE -> R.string.onboarding_done_button
            page == steps.lastIndex -> R.string.onboarding_finish
            else -> R.string.onboarding_next
        },
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BackdropCanvas(scene, poses, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (step != OnboardingStep.DONE) SkipButton(onSkip = ::skip)
            HorizontalPager(
                state = pager,
                userScrollEnabled = step != OnboardingStep.WELCOME,
                key = { steps[it].name },
                modifier = Modifier.weight(1f),
            ) { index ->
                val arrival = remember(pager, index) { { arrivalOf(pager.offsetOf(index)) } }
                CompositionLocalProvider(LocalPageArrival provides arrival) {
                    Box(Modifier.fillMaxSize().pageDepth(pager, index)) {
                        StepPage(steps[index], state, viewModel)
                    }
                }
            }
            OnboardingBar(
                progress = { ((scene.position + 1f) / steps.size).coerceIn(0f, 1f) },
                leading = when {
                    page > 0 -> BarLeading.BACK
                    retake -> BarLeading.NONE
                    else -> BarLeading.LANGUAGE
                },
                language = state.language,
                pillLabel = pillLabel,
                onBack = { go(page - 1) },
                onLanguage = viewModel::chooseLanguage,
                onPill = { if (page == steps.lastIndex) finish(skipped = false) else go(page + 1) },
            )
        }
    }
}

@Composable
private fun StepPage(step: OnboardingStep, state: OnboardingState, viewModel: OnboardingViewModel) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomePage(state.language, viewModel::chooseLanguage)
        OnboardingStep.ABOUT -> AboutPage()
        OnboardingStep.GOALS -> GoalsPage(state.answers) { goal -> viewModel.answer { it.toggle(goal) } }
        OnboardingStep.EXPERIENCE -> ExperiencePage(state.answers) { item -> viewModel.answer { it.toggle(item) } }
        OnboardingStep.PLACES -> PlacesPage(state.answers) { place -> viewModel.answer { it.toggle(place) } }
        OnboardingStep.MODE -> ModePage(state, viewModel::choosePreset)
        OnboardingStep.AGENTS -> AgentsPage(state)
        OnboardingStep.ABILITIES -> AbilitiesPage()
        OnboardingStep.FIRST_AGENT -> FirstAgentPage(state, viewModel::pickAgent, viewModel::renameAgent)
        else -> Unit
    }
}

private fun PagerState.offsetOf(page: Int): Float = page - currentPage - currentPageOffsetFraction

private fun Modifier.pageDepth(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = pager.offsetOf(page)
    val distance = abs(offset).coerceAtMost(1f)
    translationX = -offset * size.width * OnboardingMotion.PARALLAX
    alpha = 1f - distance
    val scale = 1f - OnboardingMotion.PAGE_SHRINK * distance
    scaleX = scale
    scaleY = scale
}
