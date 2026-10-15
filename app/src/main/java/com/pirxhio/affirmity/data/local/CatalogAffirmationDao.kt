package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Max ids per `IN (...)` query; well under SQLite's 999-variable cap at minSdk 24. */
const val MAX_BOUND_VARIABLES = 226

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

    /** Carousel projection: ids only, in feed order. Never loads text. */
    @Query(
        "SELECT id, groupId, collectionId FROM catalog_affirmations " +
            "WHERE groupId = :groupId ORDER BY sortOrder ASC, id ASC",
    )
    suspend fun getRefsByGroupId(groupId: String): List<CatalogAffirmationRef>

    @Query("SELECT id, groupId, collectionId FROM catalog_affirmations WHERE collectionId IN (:ids)")
    suspend fun getRefsByCollectionIdsChunk(ids: List<String>): List<CatalogAffirmationRef>

    /**
     * Chunked so a long id list never exceeds SQLite's 999 bound-variable cap on minSdk 24
     * (226 catalog collections today; the chunk size leaves ample headroom).
     */
    suspend fun getRefsByCollectionIds(ids: Collection<String>): List<CatalogAffirmationRef> =
        ids.distinct().chunked(MAX_BOUND_VARIABLES).flatMap { getRefsByCollectionIdsChunk(it) }

    /** Whole-catalog projection for the free fallback (~2k rows of three short columns). */
    @Query("SELECT id, groupId, collectionId FROM catalog_affirmations ORDER BY groupId ASC, sortOrder ASC")
    suspend fun getAllRefs(): List<CatalogAffirmationRef>

    /** Single full row, used to load text only for the frame being shown. */
    @Query("SELECT * FROM catalog_affirmations WHERE id = :id")
    suspend fun getById(id: String): CatalogAffirmationEntity?

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
