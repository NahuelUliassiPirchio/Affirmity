package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    /** Whether the short chime played on a guided meditation's phase transitions (the `SOUND`
     * channel in [com.pirxhio.affirmity.ui.meditation.GuidedMeditationAudioExecutor], e.g.
     * [com.pirxhio.affirmity.meditation.breathing.BreathingAudio.RETENTION_START]) is audible.
     * Defaults to on. Device-local by design, same rationale as [MEDITATION_DURATION_SECONDS]'s
     * sibling knobs -- a mute preference is not account data. */
    fun observeMeditationCueSoundEnabled(): Flow<Boolean> =
        context.trackerDataStore.data.map { it[MEDITATION_CUE_SOUND_ENABLED] ?: true }

    suspend fun saveMeditationCueSoundEnabled(enabled: Boolean) {
        context.trackerDataStore.edit { it[MEDITATION_CUE_SOUND_ENABLED] = enabled }
    }

    /** Catalog affirmation ids the user has hidden from their rotation (pre-launch audit item #1).
     * Device-local, same rationale as [MEDITATION_CUE_SOUND_ENABLED] -- a "don't show me this one"
     * preference is not account data. Defaults to empty. */
    fun observeHiddenAffirmationIds(): Flow<Set<String>> =
        context.trackerDataStore.data.map { it[HIDDEN_AFFIRMATION_IDS] ?: emptySet() }

    suspend fun hideAffirmation(id: String) {
        context.trackerDataStore.edit { prefs ->
            prefs[HIDDEN_AFFIRMATION_IDS] = (prefs[HIDDEN_AFFIRMATION_IDS] ?: emptySet()) + id
        }
    }

    suspend fun unhideAffirmation(id: String) {
        context.trackerDataStore.edit { prefs ->
            prefs[HIDDEN_AFFIRMATION_IDS] = (prefs[HIDDEN_AFFIRMATION_IDS] ?: emptySet()) - id
        }
    }
    private companion object {
        val AFFIRMATIONS_VIEWED_EPOCH_DAY = longPreferencesKey("affirmations_viewed_epoch_day")
        val AFFIRMATIONS_VIEWED_COUNT = intPreferencesKey("affirmations_viewed_count")
        val MEDITATION_DURATION_SECONDS = intPreferencesKey("meditation_duration_seconds")
        val MEDITATION_CUE_SOUND_ENABLED = booleanPreferencesKey("meditation_cue_sound_enabled")
        val HIDDEN_AFFIRMATION_IDS = stringSetPreferencesKey("hidden_affirmation_ids")
    }
}
