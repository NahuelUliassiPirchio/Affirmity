package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per local calendar day (see `DayClock.epochDay`), tracking both habits independently. */
@Entity(tableName = "daily_completion")
data class DailyCompletionEntity(
    @PrimaryKey val epochDay: Long,
    val meditationDone: Boolean = false,
    val affirmationDone: Boolean = false,
)
