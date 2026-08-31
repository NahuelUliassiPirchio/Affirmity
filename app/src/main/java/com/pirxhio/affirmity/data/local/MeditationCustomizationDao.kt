package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Per-meditation customization CRUD. Mirrors [CatalogOverrideDao]. */
@Dao
interface MeditationCustomizationDao {

    @Query("SELECT * FROM meditation_customizations WHERE meditationId = :meditationId")
    suspend fun getById(meditationId: String): MeditationCustomizationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MeditationCustomizationEntity)
}
