package dev.huecoder.md3agent.feature.onboarding.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.OnboardingMotion
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.locale.AppLanguage
import dev.huecoder.md3agent.ui.locale.LocalizedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun WelcomePage(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    val languages = AppLanguage.entries
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(language.ordinal.toFloat()) }
    var dragStart by remember { mutableStateOf(0f) }
    var touched by remember { mutableStateOf(false) }

    LaunchedEffect(language) { position.animateTo(language.ordinal.toFloat(), OnboardingMotion.SnapSpring) }
    LaunchedEffect(Unit) {
        delay(NUDGE_DELAY_MS)
        if (touched) return@LaunchedEffect
        val rest = position.value
        val toward = if (language.ordinal < languages.lastIndex) 1f else -1f
        position.animateTo(rest + toward * OnboardingMotion.NUDGE_SHARE * NUDGE_STEPS, OnboardingMotion.SnapSpring)
        position.animateTo(rest, OnboardingMotion.SnapSpring)
    }

    fun settle(target: Int) {
        val chosen = languages[target.coerceIn(0, languages.lastIndex)]
        if (chosen != language) {
            haptics.tick()
            onLanguage(chosen)
        }
        scope.launch { position.animateTo(chosen.ordinal.toFloat(), OnboardingMotion.SnapSpring) }
    }

    val nextLabel = stringResource(R.string.onboarding_language_next)
    val previousLabel = stringResource(R.string.onboarding_language_previous)
    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { settle(language.next(1).ordinal); true },
                    CustomAccessibilityAction(previousLabel) { settle(language.next(-1).ordinal); true },
                )
            }
            .pointerInput(language) { detectTapGestures { settle(language.next(1).ordinal) } }
            .pointerInput(language) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        touched = true
                        dragStart = position.value
                        scope.launch { position.stop() }
                    },
                    onDragEnd = {
                        val moved = position.value - dragStart
                        val step = if (abs(moved) > OnboardingMotion.SWIPE_COMMIT_SHARE) sign(moved).toInt() else 0
                        settle(dragStart.roundToInt() + step)
                    },
                    onDragCancel = { settle(dragStart.roundToInt()) },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        val pull = -delta / (size.width * OnboardingMotion.LANGUAGE_PULL)
                        scope.launch { position.snapTo((position.value + pull).coerceIn(-OVERSCROLL, languages.lastIndex + OVERSCROLL)) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().reveal(0)) {
            languages.forEach { lang ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = lang.ordinal - position.value
                            translationX = offset * size.width * LAYER_TRAVEL
                            alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                            val scale = 1f - LAYER_SHRINK * abs(offset).coerceAtMost(1f)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LocalizedContent(lang) { Greeting() }
                }
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
                    modifier = Modifier.graphicsLayer { alpha = DIM + (1f - DIM) * (1f - abs(lang.ordinal - position.value)).coerceIn(0f, 1f) },
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
private const val NUDGE_STEPS = 2f
private const val OVERSCROLL = 0.12f
private const val LAYER_TRAVEL = 0.55f
private const val LAYER_SHRINK = 0.05f
private const val DIM = 0.4f
