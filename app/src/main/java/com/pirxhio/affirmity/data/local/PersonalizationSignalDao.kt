package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PersonalizationSignalDao {

    @Insert
    suspend fun insert(entity: PersonalizationSignalEntity)

    @Query("SELECT * FROM personalization_signals ORDER BY occurredAtMillis")
    suspend fun getAll(): List<PersonalizationSignalEntity>

    /** Retention (design D7): age-based prune, run opportunistically every 50th insert. */
    @Query("DELETE FROM personalization_signals WHERE occurredAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    /** Retention (design D7): hard row cap, keeps only the [limit] newest rows. */
    @Query(
        "DELETE FROM personalization_signals WHERE id NOT IN " +
            "(SELECT id FROM personalization_signals ORDER BY occurredAtMillis DESC LIMIT :limit)",
    )
    suspend fun trimToNewest(limit: Int)
}
