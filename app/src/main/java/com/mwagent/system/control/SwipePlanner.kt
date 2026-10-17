package com.mwagent.system.control

import kotlin.math.roundToInt

data class SwipePlan(val from: ScreenPoint, val to: ScreenPoint, val durationMs: Long)

object SwipePlanner {
    const val DEFAULT_REACH = 0.6f
    const val CONTAINER_REACH = 0.7f
    const val DEFAULT_MS = 320L
    const val MIN_SWIPE_PX = 40
    const val EDGE_MARGIN_PX = 24

    fun plan(spec: SwipeSpec, screenWidth: Int, screenHeight: Int): SwipePlan {
        val duration = spec.durationMs ?: DEFAULT_MS
        val from = spec.from
        val to = spec.to
        if (from != null && to != null) return SwipePlan(inside(from, screenWidth, screenHeight), inside(to, screenWidth, screenHeight), duration)
        val direction = requireNotNull(spec.direction) { "A swipe needs a direction or two points" }
        return along(direction, ScreenBox(0, 0, screenWidth, screenHeight), spec.distance ?: DEFAULT_REACH, duration, EDGE_MARGIN_PX)
    }

    fun inside(box: ScreenBox, finger: SwipeDirection, reach: Float = CONTAINER_REACH, durationMs: Long = DEFAULT_MS): SwipePlan =
        along(finger, box, reach, durationMs, margin = 0)

    private fun along(direction: SwipeDirection, area: ScreenBox, reach: Float, durationMs: Long, margin: Int): SwipePlan {
        val halfX = (area.width * reach / 2f)
        val halfY = (area.height * reach / 2f)
        val centerX = area.centerX
        val centerY = area.centerY
        val from = ScreenPoint((centerX - direction.dx * halfX).roundToInt(), (centerY - direction.dy * halfY).roundToInt())
        val to = ScreenPoint((centerX + direction.dx * halfX).roundToInt(), (centerY + direction.dy * halfY).roundToInt())
        return SwipePlan(pulled(from, area, margin), pulled(to, area, margin), durationMs)
    }

    private fun inside(point: ScreenPoint, width: Int, height: Int) =
        ScreenPoint(point.x.coerceIn(0, (width - 1).coerceAtLeast(0)), point.y.coerceIn(0, (height - 1).coerceAtLeast(0)))

    private fun pulled(point: ScreenPoint, area: ScreenBox, margin: Int) = ScreenPoint(
        point.x.coerceIn(area.left + margin, (area.right - margin - 1).coerceAtLeast(area.left + margin)),
        point.y.coerceIn(area.top + margin, (area.bottom - margin - 1).coerceAtLeast(area.top + margin)),
    )
}
