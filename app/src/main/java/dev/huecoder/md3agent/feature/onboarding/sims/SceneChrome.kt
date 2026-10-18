package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SceneFrame(modifier: Modifier = Modifier, height: Dp = SCENE_HEIGHT, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(SCENE_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

suspend fun typeInto(text: String, charMillis: Long, publish: (Int) -> Unit) {
    for (count in 1..text.length) {
        publish(count)
        delay(charMillis)
    }
}

val SCENE_HEIGHT = 220.dp
private val SCENE_CORNER = 24.dp
