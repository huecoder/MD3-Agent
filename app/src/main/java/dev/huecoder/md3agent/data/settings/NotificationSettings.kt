package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class NotificationSettings(
    val approvals: ApprovalSurface = ApprovalSurface.NOTIFICATION,
    val liveStatus: Boolean = true,
    val chip: ChipContent = ChipContent.TIME_AND_TOKENS,
    val lockScreen: LockScreenDetail = LockScreenDetail.STATUS_ONLY,
    val taskDone: Boolean = true,
)

@Serializable
enum class ApprovalSurface { NOTIFICATION, OVERLAY }

@Serializable
enum class ChipContent { TIME, TOKENS, TIME_AND_TOKENS }

@Serializable
enum class LockScreenDetail { EVERYTHING, STATUS_ONLY, NOTHING }
