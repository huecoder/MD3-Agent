package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.settings.LockGuard

enum class LockRequirement { NONE, CONFIRM, UNLOCK }

object LockPolicy {
    fun requirement(mode: LockGuard, lockedAndSecure: Boolean, confirmed: Boolean, toolName: String): LockRequirement = when {
        mode == LockGuard.OFF || !lockedAndSecure || confirmed -> LockRequirement.NONE
        !ApprovalRisk.requiresConfirmationWhenLocked(toolName) -> LockRequirement.NONE
        mode == LockGuard.UNLOCK -> LockRequirement.UNLOCK
        else -> LockRequirement.CONFIRM
    }
}
