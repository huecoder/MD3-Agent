package dev.huecoder.md3agent.feature.onboarding

import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.avatar.AgentShapes
import kotlin.random.Random

object AgentCluster {
    const val SIZE = 5

    fun random(random: Random = Random): List<AgentLook> {
        val shapes = AgentShapes.faces.shuffled(random).take(SIZE)
        val tints = ChatTint.entries.shuffled(random).take(SIZE)
        return shapes.zip(tints) { shape, tint -> AgentLook(shape, tint) }
    }
}
