package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity

data class ActivityCalendarDay(
    val activity: ActivityDay,
    val mood: DailyMoodEntity?,
    val completion: DailyCompletionEntity?,
    val healed: Boolean,
)

sealed interface ActivityMonthState {
    data object Loading : ActivityMonthState
    data class Ready(val days: Map<Long, ActivityCalendarDay>, val pendingCount: Int) : ActivityMonthState
    data class Error(val cause: Throwable) : ActivityMonthState
}

fun joinActivityMonth(range: ActivityRangeState, moods: List<DailyMoodEntity>,
    completions: List<DailyCompletionEntity>, healers: List<StreakHealerUseEntity>): ActivityMonthState = when (range) {
    ActivityRangeState.Loading -> ActivityMonthState.Loading
    is ActivityRangeState.Error -> ActivityMonthState.Error(range.cause)
    is ActivityRangeState.Ready -> {
        val moodByDay = moods.associateBy { it.epochDay }
        val completionByDay = completions.associateBy { it.epochDay }
        val healedDays = healers.map { it.healedEpochDay }.toSet()
        ActivityMonthState.Ready(range.days.mapValues { (day, activity) ->
            ActivityCalendarDay(activity, moodByDay[day], completionByDay[day], day in healedDays)
        }, range.pendingCount)
    }
}
