package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class NotificationSettings(
    val approvals: ApprovalSurface = ApprovalSurface.NOTIFICATION,
)

@Serializable
enum class ApprovalSurface { NOTIFICATION, OVERLAY }
