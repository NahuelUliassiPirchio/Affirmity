package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Chip order: most recently enabled first, then newest created, then id for a stable tiebreak. */
private const val CHIP_ORDER = "ORDER BY lastUsedAtMillis DESC, createdAtMillis DESC, id ASC"

@Dao
interface UserCollectionDao {
    @Transaction
    @Query("SELECT * FROM user_collections $CHIP_ORDER")
    fun observeAll(): Flow<List<UserCollectionWithItems>>

    @Transaction
    @Query("SELECT * FROM user_collections $CHIP_ORDER")
    suspend fun getAll(): List<UserCollectionWithItems>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(entity: UserCollectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(entity: UserCollectionItemEntity)

    /** Atomic: when the item insert fails (e.g. FK violation) the collection insert rolls back. */
    @Transaction
    suspend fun createWithItem(collection: UserCollectionEntity, item: UserCollectionItemEntity?) {
        insertCollection(collection)
        if (item != null) insertItem(item)
    }

    @Query("UPDATE user_collections SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM user_collections WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "DELETE FROM user_collection_items " +
            "WHERE userCollectionId = :userCollectionId AND affirmationId = :affirmationId",
    )
    suspend fun deleteItem(userCollectionId: String, affirmationId: String)

    @Query("UPDATE user_collections SET enabled = 1, lastUsedAtMillis = :nowMillis WHERE id = :id")
    suspend fun enable(id: String, nowMillis: Long)

    @Query("UPDATE user_collections SET enabled = 0 WHERE id = :id")
    suspend fun disable(id: String)

    /** Callers must keep [affirmationIds] under SQLite's 999 bound-variable cap (minSdk 24). */
    @Query("DELETE FROM user_collection_items WHERE affirmationId IN (:affirmationIds)")
    suspend fun deleteItemsForAffirmations(affirmationIds: Collection<String>)
}
