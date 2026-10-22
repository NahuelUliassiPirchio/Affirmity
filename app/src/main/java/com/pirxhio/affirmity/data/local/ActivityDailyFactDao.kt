package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ActivityDailyFactDao {
    @Query("SELECT * FROM activity_daily_fact WHERE owner = :owner AND epochDay BETWEEN :from AND :to ORDER BY epochDay")
    abstract suspend fun getRange(owner: String, from: Long, to: Long): List<ActivityDailyFactEntity>

    @Query("SELECT * FROM activity_daily_fact WHERE owner = :owner AND epochDay BETWEEN :from AND :to ORDER BY epochDay")
    abstract fun observeRange(owner: String, from: Long, to: Long): Flow<List<ActivityDailyFactEntity>>

    @Query("SELECT * FROM activity_daily_fact WHERE owner = :owner ORDER BY epochDay")
    abstract suspend fun getAll(owner: String): List<ActivityDailyFactEntity>

    @Query("SELECT * FROM activity_owner WHERE owner = :owner")
    protected abstract suspend fun ownerState(owner: String): ActivityOwnerEntity?

    @Query("SELECT legacyAdopterOwner FROM activity_owner WHERE legacyAdoptionComplete = 1 AND legacyAdopterOwner IS NOT NULL LIMIT 1")
    protected abstract suspend fun firstAdopter(): String?

    @Query("UPDATE activity_owner SET legacyAdoptionComplete = 1, legacyAdopterOwner = :adopter WHERE owner = :owner")
    protected abstract suspend fun completeAdoption(owner: String, adopter: String)

    @Query("SELECT * FROM daily_mood ORDER BY epochDay")
    protected abstract suspend fun legacyMoods(): List<DailyMoodEntity>

    @Query("SELECT * FROM daily_completion ORDER BY epochDay")
    protected abstract suspend fun legacyCompletions(): List<DailyCompletionEntity>

    @Query("SELECT * FROM streak_healer_use ORDER BY healedEpochDay")
    protected abstract suspend fun legacyHealers(): List<StreakHealerUseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun write(fact: ActivityDailyFactEntity)

    @Transaction
    open suspend fun adoptLegacyOnce(owner: String) {
        val state = requireNotNull(ownerState(owner))
        if (state.legacyAdoptionComplete) return
        require(state.active && state.claimedUid == null) { "Anonymous generation already claimed" }
        val adopter = firstAdopter()
        if (adopter == null) {
            adoptDailyFacts(owner, legacyMoods(), legacyCompletions(), legacyHealers()).forEach { write(it) }
        }
        completeAdoption(owner, adopter ?: owner)
    }

    @Transaction
    open suspend fun upsertMood(owner: String, epochDay: Long, moodValue: Int, note: String?) {
        mutate(owner, epochDay, DailyFactChange.Mood(moodValue, note))
    }

    suspend fun markMeditation(owner: String, epochDay: Long) = mutate(owner, epochDay, DailyFactChange.Meditation)
    suspend fun markAffirmation(owner: String, epochDay: Long) = mutate(owner, epochDay, DailyFactChange.Affirmation)
    suspend fun recordHealer(owner: String, epochDay: Long, activatedAtMillis: Long) =
        mutate(owner, epochDay, DailyFactChange.Healer(activatedAtMillis))

    @Transaction
    open suspend fun mutate(owner: String, epochDay: Long, change: DailyFactChange) {
        val state = requireNotNull(ownerState(owner))
        require(state.active && state.claimedUid == null && state.legacyAdoptionComplete) { "Anonymous generation already claimed" }
        val existing = getRange(owner, epochDay, epochDay).singleOrNull() ?: ActivityDailyFactEntity(owner, epochDay)
        write(applyDailyFactChange(existing, change))
    }
}
