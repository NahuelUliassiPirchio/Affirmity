package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per local calendar day (see `DayClock.epochDay`), the day's mood check-in. */
@Entity(tableName = "daily_mood")
data class DailyMoodEntity(
    @PrimaryKey val epochDay: Long,
    val moodValue: Int,
    val note: String? = null,
)
