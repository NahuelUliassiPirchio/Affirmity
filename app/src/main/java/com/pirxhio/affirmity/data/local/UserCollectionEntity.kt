package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-created collection. Not related to the catalog's `collectionId` grouping. */
@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val enabled: Boolean,
    val lastUsedAtMillis: Long,
)
