package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trackerDataStore by preferencesDataStore(name = "tracker_prefs")

/** [streakDays] consecutive days ending on [lastCompletedEpochDay] (a device-local day count, see [epochDay]). */
data class StreakSnapshot(val streakDays: Int, val lastCompletedEpochDay: Long)

/** Which day the user last viewed affirmations, and how many they'd viewed by then. */
data class DailyViewCount(val epochDay: Long, val count: Int)

class TrackerPreferences(private val context: Context) {

    fun observeAffirmationsStreak(): Flow<StreakSnapshot> =
        observeStreak(AFFIRMATIONS_STREAK_DAYS, AFFIRMATIONS_LAST_EPOCH_DAY)

    fun observeMeditationStreak(): Flow<StreakSnapshot> =
        observeStreak(MEDITATION_STREAK_DAYS, MEDITATION_LAST_EPOCH_DAY)

    fun observeAffirmationsViewedToday(): Flow<DailyViewCount> =
        context.trackerDataStore.data.map {
            DailyViewCount(
                epochDay = it[AFFIRMATIONS_VIEWED_EPOCH_DAY] ?: -1L,
                count = it[AFFIRMATIONS_VIEWED_COUNT] ?: 0,
            )
        }

    suspend fun saveAffirmationsViewedToday(viewed: DailyViewCount) {
        context.trackerDataStore.edit {
            it[AFFIRMATIONS_VIEWED_EPOCH_DAY] = viewed.epochDay
            it[AFFIRMATIONS_VIEWED_COUNT] = viewed.count
        }
    }

    suspend fun saveAffirmationsStreak(snapshot: StreakSnapshot) =
        saveStreak(AFFIRMATIONS_STREAK_DAYS, AFFIRMATIONS_LAST_EPOCH_DAY, snapshot)

    suspend fun saveMeditationStreak(snapshot: StreakSnapshot) =
        saveStreak(MEDITATION_STREAK_DAYS, MEDITATION_LAST_EPOCH_DAY, snapshot)

    /** Null means no duration has been picked yet — the screen falls back to its own default. */
    fun observeMeditationDurationSeconds(): Flow<Int?> =
        context.trackerDataStore.data.map { it[MEDITATION_DURATION_SECONDS] }

    suspend fun saveMeditationDurationSeconds(seconds: Int) {
        context.trackerDataStore.edit { it[MEDITATION_DURATION_SECONDS] = seconds }
    }

    private fun observeStreak(
        streakKey: Preferences.Key<Int>,
        epochDayKey: Preferences.Key<Long>,
    ): Flow<StreakSnapshot> =
        context.trackerDataStore.data.map {
            StreakSnapshot(
                streakDays = it[streakKey] ?: 0,
                lastCompletedEpochDay = it[epochDayKey] ?: -1L,
            )
        }

    private suspend fun saveStreak(
        streakKey: Preferences.Key<Int>,
        epochDayKey: Preferences.Key<Long>,
        snapshot: StreakSnapshot,
    ) {
        context.trackerDataStore.edit {
            it[streakKey] = snapshot.streakDays
            it[epochDayKey] = snapshot.lastCompletedEpochDay
        }
    }

    private companion object {
        val AFFIRMATIONS_STREAK_DAYS = intPreferencesKey("affirmations_streak_days")
        val AFFIRMATIONS_LAST_EPOCH_DAY = longPreferencesKey("affirmations_last_epoch_day")
        val MEDITATION_STREAK_DAYS = intPreferencesKey("meditation_streak_days")
        val MEDITATION_LAST_EPOCH_DAY = longPreferencesKey("meditation_last_epoch_day")
        val AFFIRMATIONS_VIEWED_EPOCH_DAY = longPreferencesKey("affirmations_viewed_epoch_day")
        val AFFIRMATIONS_VIEWED_COUNT = intPreferencesKey("affirmations_viewed_count")
        val MEDITATION_DURATION_SECONDS = intPreferencesKey("meditation_duration_seconds")
    }
}
