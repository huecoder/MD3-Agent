package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_upward
import com.composables.icons.materialsymbols.rounded.Attach_file
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.AgentAvatar
import kotlinx.coroutines.delay

@Composable
fun AskScene(modifier: Modifier = Modifier, height: Dp = CHAT_HEIGHT, onCycle: () -> Unit = {}) {
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
            onCycle()
        }
    }

    SceneFrame(modifier, height = height) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = fade.value }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (asked > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { UserBubble(question.take(asked)) }
                }
                if (answered > 0 || asked == question.length) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgentAvatar(isWorking = answered in 1 until answer.length, size = AVATAR_SIZE)
                        Text(answer.take(answered).ifEmpty { "…" }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Composer()
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Surface(
        shape = UserBubbleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.widthIn(max = MAX_BUBBLE_WIDTH),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun Composer() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(percent = 50), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) {
                Icon(MaterialSymbols.Rounded.Attach_file, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.chat_input_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            Box(Modifier.size(ACTION_SIZE).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                Icon(MaterialSymbols.Rounded.Arrow_upward, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
    }
}

private val UserBubbleShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp)
private val MAX_BUBBLE_WIDTH = 320.dp
private val AVATAR_SIZE = 28.dp
private val ACTION_SIZE = 48.dp
private val CHAT_HEIGHT = 300.dp
private const val PAUSE_BEFORE_MS = 700L
private const val PAUSE_THINKING_MS = 450L
private const val PAUSE_AFTER_MS = 2_400L
private const val QUESTION_CHAR_MS = 45L
private const val ANSWER_CHAR_MS = 24L
