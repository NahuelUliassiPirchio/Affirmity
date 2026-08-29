package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** READ + seed only (design.md). No insert/update/delete of a single row -- the only write path
 * is [replaceAll], called exclusively by `CatalogSeeder`. */
@Dao
interface CatalogAffirmationDao {

    @Query("SELECT * FROM catalog_affirmations ORDER BY groupId ASC, sortOrder ASC")
    fun observeAll(): Flow<List<CatalogAffirmationEntity>>

    /** Feed query. Empty [groupIds] returns empty -- never "all", which would leak locked groups. */
    @Query("SELECT * FROM catalog_affirmations WHERE groupId IN (:groupIds) ORDER BY groupId ASC, sortOrder ASC")
    fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>>

    /** Favorites cross-space resolution (design D10) -- ids may reference either space. */
    @Query("SELECT * FROM catalog_affirmations WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity>

    @Query("SELECT COUNT(*) FROM catalog_affirmations")
    suspend fun count(): Int

    /** Seed path only (design D13). `@Transaction` makes replace-then-insert atomic. */
    @Transaction
    suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
        deleteAll()
        insertAll(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CatalogAffirmationEntity>)

    @Query("DELETE FROM catalog_affirmations")
    suspend fun deleteAll()
}
