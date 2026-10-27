package com.pirxhio.affirmity.data

/** Owner is captured at the interaction, never resolved again after a suspension. */
data class ActivityEvent(
    val owner: String,
    val eventId: String,
    val epochDay: Long,
    val kind: ActivityKind,
    val occurredAtMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
    val zoneId: String,
    val meditationId: String?,
    val nameSnapshot: String?,
    val activeSeconds: Long,
    val sourceId: String,
) {
    init {
        require(owner.isNotBlank() && eventId.isNotBlank() && sourceId.isNotBlank())
        require(zoneId.isNotBlank() && activeSeconds >= 0)
        require(startMillis <= endMillis)
        require(kind != ActivityKind.VIEW || (activeSeconds == 0L && meditationId == null && nameSnapshot == null))
        require(kind == ActivityKind.VIEW || !nameSnapshot.isNullOrBlank())
    }
}

enum class ActivityKind { VIEW, GUIDED, FREE }

/** FULL_SOURCE_DAY is valid only for an uninterrupted anonymous installation day, not an account. */
enum class ActivityCoverage { UNKNOWN, FULL_SOURCE_DAY }

data class ActivityCoverageInterval(
    val owner: String,
    val sourceId: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val recordingSchema: Int,
    val uncertain: Boolean,
) {
    init {
        require(owner.isNotBlank() && sourceId.isNotBlank())
        require(fromEpochDay <= toEpochDay)
        require(recordingSchema > 0)
    }
}

/** Absence on an account is never proof of zero, even after a successful server read. */
fun resolveActivityCoverage(owner: String, epochDay: Long, intervals: List<ActivityCoverageInterval>): ActivityCoverage {
    if (!owner.startsWith("local:")) return ActivityCoverage.UNKNOWN
    val matching = intervals.filter { it.owner == owner && epochDay in it.fromEpochDay..it.toEpochDay }
    if (matching.any { it.uncertain || it.recordingSchema != 1 }) return ActivityCoverage.UNKNOWN
    return if (matching.isEmpty()) ActivityCoverage.UNKNOWN else ActivityCoverage.FULL_SOURCE_DAY
}

data class ActivityDay(
    val epochDay: Long,
    val viewedCount: Int,
    val sessions: List<ActivityEvent>,
    val coverage: ActivityCoverage,
) {
    val activeSeconds: Long get() = sessions.sumOf { it.activeSeconds }
    val confirmedEmpty: Boolean get() = coverage == ActivityCoverage.FULL_SOURCE_DAY && viewedCount == 0 && sessions.isEmpty()
}

/** Retried identical identities contribute once; conflicting identities never silently win. */
fun aggregateActivityDay(epochDay: Long, events: List<ActivityEvent>, coverage: ActivityCoverage): ActivityDay {
    require(events.map { it.owner }.distinct().size <= 1) { "Mixed activity owners" }
    val unique = events.groupBy { it.eventId }.map { (_, copies) ->
        require(copies.distinct().size == 1) { "Conflicting activity identity" }
        copies.first()
    }.filter { it.epochDay == epochDay }.sortedWith(compareBy({ it.occurredAtMillis }, { it.eventId }))
    return ActivityDay(epochDay, unique.count { it.kind == ActivityKind.VIEW },
        unique.filter { it.kind != ActivityKind.VIEW }, coverage)
}

sealed interface ActivityRangeState {
    data object Loading : ActivityRangeState
    data class Ready(val days: Map<Long, ActivityDay>, val pendingCount: Int) : ActivityRangeState
    data class Error(val cause: Throwable) : ActivityRangeState
}

enum class ActivitySaveState { LOCAL, PENDING, UPLOADED, FAILED }

fun mergeActivityEvents(owner: String, remote: List<ActivityEvent>, pending: List<ActivityEvent>): List<ActivityEvent> {
    val events = remote + pending
    require(events.all { it.owner == owner }) { "Foreign activity owner" }
    return events.groupBy { it.eventId }.map { (_, copies) ->
        require(copies.distinct().size == 1) { "Conflicting activity identity" }
        copies.first()
    }.sortedWith(compareBy({ it.occurredAtMillis }, { it.eventId }))
}
