package dev.huecoder.md3agent.feature.onboarding

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.snap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.pages.AbilitiesPage
import dev.huecoder.md3agent.feature.onboarding.pages.AboutPage
import dev.huecoder.md3agent.feature.onboarding.pages.AgentsPage
import dev.huecoder.md3agent.feature.onboarding.pages.AssistantPage
import dev.huecoder.md3agent.feature.onboarding.pages.PermissionsPage
import dev.huecoder.md3agent.feature.onboarding.pages.FirstAgentPage
import dev.huecoder.md3agent.feature.onboarding.pages.DonePage
import dev.huecoder.md3agent.feature.onboarding.pages.ExperiencePage
import dev.huecoder.md3agent.feature.onboarding.pages.GoalsPage
import dev.huecoder.md3agent.feature.onboarding.pages.ModePage
import dev.huecoder.md3agent.feature.onboarding.pages.PlacesPage
import dev.huecoder.md3agent.feature.onboarding.pages.ProviderPage
import dev.huecoder.md3agent.feature.onboarding.pages.WelcomePage
import dev.huecoder.md3agent.feature.onboarding.scene.BackdropCanvas
import dev.huecoder.md3agent.feature.onboarding.scene.BackdropScene
import dev.huecoder.md3agent.feature.onboarding.scene.ScenePlan
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.locale.AppLocale
import dev.huecoder.md3agent.ui.locale.LocalizedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(retake: Boolean, onCustomProvider: () -> Unit, onFinished: (openChat: Boolean) -> Unit) {
    val viewModel = appViewModel(key = "onboarding-$retake") {
        OnboardingViewModel(it.appContext, it.settings, it.mode, PermissionProbe(it.appContext, it.phoneControl, it.deviceFiles, it.shellAccess), it.modelCatalog, retake)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val activity = LocalActivity.current
    LocalizedContent(current.language) {
        OnboardingContent(
            state = current,
            viewModel = viewModel,
            retake = retake,
            onCustomProvider = onCustomProvider,
            onFinished = { openChat ->
                onFinished(openChat)
                if (!retake) activity?.let { AppLocale.apply(it, current.language) }
            },
        )
    }
}

@Composable
private fun OnboardingContent(state: OnboardingState, viewModel: OnboardingViewModel, retake: Boolean, onCustomProvider: () -> Unit, onFinished: (openChat: Boolean) -> Unit) {
    val steps = state.steps
    val pager = rememberPagerState { steps.size }
    val scene = remember { BackdropScene() }
    val poses = remember(steps) { steps.mapIndexed { index, step -> ScenePlan.pose(index, step.calm) } }
    val currentSteps by rememberUpdatedState(steps)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val animations = remember { ValueAnimator.areAnimatorsEnabled() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val page = pager.currentPage.coerceIn(steps.indices)
    val step = steps[page]
    var departing by remember { mutableStateOf(false) }

    LaunchedEffect(pager) { snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { scene.position = it } }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { currentSteps.getOrNull(it)?.let(viewModel::markShown) } }
    LaunchedEffect(animations, scene, scene.stage) {
        if (!animations || scene.stage == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { scene.run() }
    }

    fun go(target: Int) {
        if (target !in steps.indices || target == pager.currentPage) return
        haptics.tick()
        scope.launch { pager.animateScrollToPage(target, animationSpec = if (animations) OnboardingMotion.PageSpring else snap()) }
    }

    fun finish(skipped: Boolean) {
        scope.launch {
            if (!skipped && step == OnboardingStep.DONE) {
                departing = true
                delay(OnboardingMotion.DEPART_MILLIS.toLong())
            }
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BackdropCanvas(scene, poses, Modifier.fillMaxSize())
        DeliberateTouch {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (step != OnboardingStep.DONE) SkipButton(onSkip = ::skip)
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = step != OnboardingStep.WELCOME,
                    flingBehavior = rememberDeliberateFling(pager),
                    key = { steps[it].name },
                    modifier = Modifier.weight(1f),
                ) { index ->
                    val offset = remember(pager, index) { { pager.offsetOf(index) } }
                    CompositionLocalProvider(LocalPageOffset provides offset) {
                        StepPage(steps[index], state, viewModel, departing, onCustomProvider, onLater = { go(page + 1) })
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
}

@Composable
private fun StepPage(step: OnboardingStep, state: OnboardingState, viewModel: OnboardingViewModel, departing: Boolean, onCustomProvider: () -> Unit, onLater: () -> Unit) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomePage(state.language, viewModel::chooseLanguage)
        OnboardingStep.ABOUT -> AboutPage()
        OnboardingStep.GOALS -> GoalsPage(state.answers) { goal -> viewModel.answer { it.toggle(goal) } }
        OnboardingStep.EXPERIENCE -> ExperiencePage(state.answers) { item -> viewModel.answer { it.toggle(item) } }
        OnboardingStep.PLACES -> PlacesPage(state.answers) { place -> viewModel.answer { it.toggle(place) } }
        OnboardingStep.MODE -> ModePage(state, viewModel::choosePreset)
        OnboardingStep.AGENTS -> AgentsPage(state)
        OnboardingStep.ABILITIES -> AbilitiesPage()
        OnboardingStep.DONE -> DonePage(state, departing)
        OnboardingStep.ASSISTANT -> AssistantPage()
        OnboardingStep.PERMISSIONS -> PermissionsPage(state, viewModel::refreshPermissions)
        OnboardingStep.PROVIDER -> ProviderPage(state, viewModel::connectProvider, viewModel::selectModel, onCustomProvider, onLater)
        OnboardingStep.FIRST_AGENT -> FirstAgentPage(state, viewModel::pickShape, viewModel::pickTint, viewModel::renameAgent)
    }
}

private fun PagerState.offsetOf(page: Int): Float = page - currentPage - currentPageOffsetFraction
