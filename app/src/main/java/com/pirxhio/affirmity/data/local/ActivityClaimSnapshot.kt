package com.pirxhio.affirmity.data.local

/** All retained source facts, captured after the claim/write barrier in one Room transaction. */
data class ActivityClaimSnapshot(
    val owner: ActivityOwnerEntity,
    val dailyFacts: List<ActivityDailyFactEntity>,
    val events: List<ActivityEventEntity>,
    val coverage: List<ActivityCoverageEntity>,
)
