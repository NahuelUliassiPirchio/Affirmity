package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pirxhio.affirmity.data.MilestoneProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.streakMilestoneDataStore by preferencesDataStore(name = "streak_milestone_prefs")

/** Seam over the milestone/banner persistence so [com.pirxhio.affirmity.data.AffirmityAppState] tests stay in memory. */
interface StreakMilestoneStore {
    suspend fun read(): MilestoneProgress
    suspend fun save(progress: MilestoneProgress)
    fun observeBestReached(): Flow<Int>
    fun observeDismissedBannerDay(): Flow<Long?>
    suspend fun dismissBannerForDay(epochDay: Long)
}

/**
 * Local-only (device) streak milestone bookkeeping and the risk-banner dismissal day. Deliberately
 * not synced and not cleared on sign-out: milestones are celebrated once per device history.
 */
class StreakMilestonePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) : StreakMilestoneStore {

    constructor(context: Context) : this(context.streakMilestoneDataStore)

    override suspend fun read(): MilestoneProgress = dataStore.data.first().let {
        MilestoneProgress(it[HIGHEST_CELEBRATED], it[BEST_REACHED])
    }

    override suspend fun save(progress: MilestoneProgress) {
        dataStore.edit {
            progress.highestCelebrated?.let { v -> it[HIGHEST_CELEBRATED] = v }
            progress.bestReached?.let { v -> it[BEST_REACHED] = v }
        }
    }

    override fun observeBestReached(): Flow<Int> = dataStore.data.map { it[BEST_REACHED] ?: 0 }

    override fun observeDismissedBannerDay(): Flow<Long?> = dataStore.data.map { it[DISMISSED_BANNER_DAY] }

    override suspend fun dismissBannerForDay(epochDay: Long) {
        dataStore.edit { it[DISMISSED_BANNER_DAY] = epochDay }
    }

    private companion object {
        val HIGHEST_CELEBRATED = intPreferencesKey("highest_celebrated_milestone")
        val BEST_REACHED = intPreferencesKey("best_milestone_reached")
        val DISMISSED_BANNER_DAY = longPreferencesKey("day_risk_banner_dismissed_day")
    }
}

/** Volatile [StreakMilestoneStore] for tests and previews. */
class InMemoryStreakMilestoneStore(initial: MilestoneProgress = MilestoneProgress()) : StreakMilestoneStore {
    private val progress = kotlinx.coroutines.flow.MutableStateFlow(initial)
    private val dismissedDay = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    override suspend fun read(): MilestoneProgress = progress.value
    override suspend fun save(progress: MilestoneProgress) { this.progress.value = progress }
    override fun observeBestReached(): Flow<Int> = progress.map { it.bestReached ?: 0 }
    override fun observeDismissedBannerDay(): Flow<Long?> = dismissedDay
    override suspend fun dismissBannerForDay(epochDay: Long) { dismissedDay.value = epochDay }
}
