package com.pirxhio.affirmity.widget

import com.pirxhio.affirmity.data.local.ActivityDailyFactDao
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.data.repository.ScopedRoomDailyCompletionRepository
import com.pirxhio.affirmity.data.repository.ScopedRoomStreakHealerRepository

internal class AnonymousWidgetFacts(
    val hasAny: Boolean,
    val completions: List<DailyCompletionEntity>,
    val healerUses: List<StreakHealerUseEntity>,
)

/** Reads the same owner-scoped daily facts the app writes for an anonymous generation. */
internal suspend fun loadAnonymousWidgetFacts(
    daily: ActivityDailyFactDao,
    owner: String,
    completionsFrom: Long,
    healersFrom: Long,
    to: Long,
): AnonymousWidgetFacts {
    val completions = ScopedRoomDailyCompletionRepository(daily, owner)
    return AnonymousWidgetFacts(
        hasAny = completions.earliestEpochDay() != null,
        completions = completions.getRange(completionsFrom, to),
        healerUses = ScopedRoomStreakHealerRepository(daily, owner).getRange(healersFrom, to),
    )
}
