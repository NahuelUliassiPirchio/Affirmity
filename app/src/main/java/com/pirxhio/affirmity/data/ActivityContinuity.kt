package com.pirxhio.affirmity.data

import kotlin.math.abs

/** In-process proof only. Restart begins a new observation; it never bridges an unseen day. */
class ActivityContinuity(private val owner: String, private val sourceId: String,
    startDay: Long, startMonotonicMillis: Long, private var zoneId: String,
    startWallMillis: Long = startMonotonicMillis,
) {
    private var lastDay = startDay
    private var lastMonotonic = startMonotonicMillis
    private var lastWall = startWallMillis
    private var firstEligibleDay = startDay + 1

    fun observe(day: Long, monotonicMillis: Long, zone: String, wallMillis: Long = monotonicMillis): List<ActivityCoverageInterval> {
        val elapsed = monotonicMillis - lastMonotonic
        val interrupted = elapsed !in 0..90_000 || abs((wallMillis - lastWall) - elapsed) > 5_000 ||
            zone != zoneId || day < lastDay || day > lastDay + 1
        val result = when {
            interrupted -> {
                firstEligibleDay = day + 1
                listOf(ActivityCoverageInterval(owner, sourceId, minOf(lastDay, day), maxOf(lastDay, day), 1, true))
            }
            day > lastDay && lastDay >= firstEligibleDay ->
                listOf(ActivityCoverageInterval(owner, sourceId, lastDay, lastDay, 1, false))
            else -> emptyList()
        }
        lastDay = day
        lastMonotonic = monotonicMillis
        lastWall = wallMillis
        zoneId = zone
        return result
    }
}
