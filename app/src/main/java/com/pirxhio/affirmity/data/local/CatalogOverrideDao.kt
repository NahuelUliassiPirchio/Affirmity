package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Per-user catalog override CRUD (design.md "Persistence -- DAOs"). */
@Dao
interface CatalogOverrideDao {

    @Query("SELECT * FROM catalog_affirmation_overrides")
    fun observeAll(): Flow<List<CatalogOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogOverrideEntity)

    /** Whole-map replacement mirrors `AffirmationRepository.setOverrides`; an empty map DELETES
     *  the row rather than storing `{}`, so "no overrides" has exactly one representation. */
    @Query("DELETE FROM catalog_affirmation_overrides WHERE catalogAffirmationId = :id")
    suspend fun deleteById(id: String)
}
