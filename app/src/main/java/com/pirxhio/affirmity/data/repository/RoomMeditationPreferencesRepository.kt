package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.TrackerPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Thin [MeditationPreferencesRepository] wrapper delegating 1:1 to the untouched
 * [TrackerPreferences] DataStore class.
 */
class RoomMeditationPreferencesRepository(
    private val trackerPreferences: TrackerPreferences,
) : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> =
        trackerPreferences.observeMeditationDurationSeconds()

    override suspend fun saveMeditationDurationSeconds(seconds: Int) =
        trackerPreferences.saveMeditationDurationSeconds(seconds)
}
