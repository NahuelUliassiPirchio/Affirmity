package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per explicit healer activation, keyed by the local calendar day it healed
 * ([healedEpochDay], see `DayClock.epochDay`) — an append-only log, never a mutable
 * consumed/unconsumed flag (design.md's "Persist an event log" decision).
 * [activatedAtMillis] is audit-only and never read by [com.pirxhio.affirmity.data.StreakHealerStats].
 */
@Entity(tableName = "streak_healer_use")
data class StreakHealerUseEntity(
    @PrimaryKey val healedEpochDay: Long,
    val activatedAtMillis: Long,
)
