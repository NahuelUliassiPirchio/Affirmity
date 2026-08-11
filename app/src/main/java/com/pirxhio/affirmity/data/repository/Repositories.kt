package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.flow.Flow

/**
 * Store-agnostic contract for affirmation persistence. Implementations exist for Room
 * (signed-out users, `RoomAffirmationRepository`) and Firestore (signed-in users,
 * `FirestoreAffirmationRepository`) — see `data-sync` spec for the single-writer cutover rule.
 */
interface AffirmationRepository {
    fun observeAll(): Flow<List<AffirmationEntity>>
    suspend fun insert(entity: AffirmationEntity)
    suspend fun deleteById(id: String)
    suspend fun deleteAll()
}

/** Store-agnostic contract for the daily habit-completion tracker (streak source of truth). */
interface DailyCompletionRepository {
    fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>>
    suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity>
    suspend fun markMeditation(epochDay: Long)
    suspend fun markAffirmation(epochDay: Long)
}

/** Store-agnostic contract for the daily mood check-in (1-5 scale + optional note). */
interface DailyMoodRepository {
    fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>>
    suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity>
    suspend fun upsert(epochDay: Long, moodValue: Int, note: String?)
}

/**
 * Store-agnostic contract for the streak-healer activation event log — an append-only log keyed
 * by [StreakHealerUseEntity.healedEpochDay], never a mutable held/consumed flag (design.md's
 * "Persist an event log, not mutable healer state" decision).
 */
interface StreakHealerRepository {
    fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>>
    suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity>
    suspend fun recordUse(healedEpochDay: Long)
}

/** Store-agnostic contract for the meditation-duration preference. */
interface MeditationPreferencesRepository {
    fun observeMeditationDurationSeconds(): Flow<Int?>
    suspend fun saveMeditationDurationSeconds(seconds: Int)
}

/** Store-agnostic contract for both notification-channel preferences. */
interface NotificationSettingsRepository {
    fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings>
    suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean)
    suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>)

    /**
     * Persists the device's IANA timezone id so the server planner can compute this user's
     * local-day trigger instants. No-op for the Room-backed (signed-out) implementation, since
     * signed-out users have no server-driven scheduling to feed.
     */
    suspend fun setTimeZone(zoneId: String)
}
