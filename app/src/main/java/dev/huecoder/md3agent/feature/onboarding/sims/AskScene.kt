package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.AgentAvatar
import kotlinx.coroutines.delay

@Composable
fun AskScene(modifier: Modifier = Modifier) {
    val question = stringResource(R.string.onboarding_scene_ask_question)
    val answer = stringResource(R.string.onboarding_scene_ask_answer)
    var asked by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(question, answer) {
        while (true) {
            asked = 0
            answered = 0
            fade.snapTo(1f)
            delay(PAUSE_BEFORE_MS)
            typeInto(question, QUESTION_CHAR_MS) { asked = it }
            delay(PAUSE_THINKING_MS)
            typeInto(answer, ANSWER_CHAR_MS) { answered = it }
            delay(PAUSE_AFTER_MS)
            fade.animateTo(0f)
        }
    }

    SceneFrame(modifier) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).graphicsLayer { alpha = fade.value },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (asked > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Bubble(question.take(asked), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (answered > 0 || asked == question.length) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    AgentAvatar(isWorking = answered in 1 until answer.length, size = 32.dp)
                    Bubble(answer.take(answered).ifEmpty { "…" }, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = container, contentColor = content, modifier = Modifier.widthIn(max = 230.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

private const val PAUSE_BEFORE_MS = 700L
private const val PAUSE_THINKING_MS = 450L
private const val PAUSE_AFTER_MS = 2_400L
private const val QUESTION_CHAR_MS = 45L
private const val ANSWER_CHAR_MS = 24L
