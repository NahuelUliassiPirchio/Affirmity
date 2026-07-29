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

/** Which day the user last viewed affirmations, and how many they'd viewed by then. */
data class DailyViewCount(val epochDay: Long, val count: Int)

/**
 * Non-streak tracker preferences. Streak/weekly derivation lives exclusively in
 * `daily_completion` (via [com.pirxhio.affirmity.data.local.DailyCompletionDao]) — this class
 * intentionally does NOT cache streak state, to avoid the dual-source-of-truth drift that caused
 * the old contiguous-streak-derivation bug.
 */
class TrackerPreferences(private val context: Context) {

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

    /** Null means no duration has been picked yet — the screen falls back to its own default. */
    fun observeMeditationDurationSeconds(): Flow<Int?> =
        context.trackerDataStore.data.map { it[MEDITATION_DURATION_SECONDS] }

    suspend fun saveMeditationDurationSeconds(seconds: Int) {
        context.trackerDataStore.edit { it[MEDITATION_DURATION_SECONDS] = seconds }
    }

    private companion object {
        val AFFIRMATIONS_VIEWED_EPOCH_DAY = longPreferencesKey("affirmations_viewed_epoch_day")
        val AFFIRMATIONS_VIEWED_COUNT = intPreferencesKey("affirmations_viewed_count")
        val MEDITATION_DURATION_SECONDS = intPreferencesKey("meditation_duration_seconds")
    }
}
