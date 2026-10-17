package com.mwagent.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mwagent.R
import com.mwagent.ui.avatar.AgentLook
import com.mwagent.ui.components.AgentAvatar

@Composable
fun DefaultAgentButton(look: AgentLook, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TOUCH_TARGET)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.home_agent_picker), role = Role.Button, onClick = onClick),
    ) {
        AgentAvatar(isWorking = false, size = AVATAR_SIZE, look = look, watchingBelow = true)
    }
}

private val TOUCH_TARGET = 48.dp
private val AVATAR_SIZE = 40.dp
