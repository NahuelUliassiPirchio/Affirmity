package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.streakRescueDataStore by preferencesDataStore(name = "streak_rescue_prefs")

/**
 * The epoch day on which the user last dismissed the streak-rescue dialog, so "once per day after
 * dismissal" survives process death. `null` means never dismissed. Device-local on purpose: it is
 * a nag-suppression flag, not account data.
 */
class StreakRescuePreferences(private val context: Context) {

    fun observeDismissedEpochDay(): Flow<Long?> =
        context.streakRescueDataStore.data.map { it[DISMISSED_EPOCH_DAY] }

    suspend fun saveDismissedEpochDay(epochDay: Long) {
        context.streakRescueDataStore.edit { it[DISMISSED_EPOCH_DAY] = epochDay }
    }

    private companion object {
        val DISMISSED_EPOCH_DAY = longPreferencesKey("dismissed_epoch_day")
    }
}
