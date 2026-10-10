package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DefaultGroupStateDao {
    @Query("SELECT * FROM default_group_state")
    fun observeAll(): Flow<List<DefaultGroupStateEntity>>

    @Query("SELECT * FROM default_group_state")
    suspend fun getAll(): List<DefaultGroupStateEntity>

    @Upsert
    suspend fun upsert(entity: DefaultGroupStateEntity)

    /** Portable upsert (Room's `@Upsert`; SQLite UPSERT needs API 30, minSdk is 24). */
    suspend fun enable(id: String, nowMillis: Long) {
        upsert(DefaultGroupStateEntity(id = id, enabled = true, lastUsedAtMillis = nowMillis))
    }

    /** No row means already disabled, so this is a no-op then. */
    @Query("UPDATE default_group_state SET enabled = 0 WHERE id = :id")
    suspend fun disable(id: String)
}
