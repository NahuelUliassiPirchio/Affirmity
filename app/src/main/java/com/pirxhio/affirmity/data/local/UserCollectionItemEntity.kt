package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Membership of an affirmation in a user collection. `affirmationId` has no FK on purpose: ids
 * live in two stores (owned affirmations and catalog `cat_` rows). Items are removed with their
 * collection through the FK cascade.
 */
@Entity(
    tableName = "user_collection_items",
    primaryKeys = ["userCollectionId", "affirmationId"],
    foreignKeys = [
        ForeignKey(
            entity = UserCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["userCollectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("affirmationId")],
)
data class UserCollectionItemEntity(
    val userCollectionId: String,
    val affirmationId: String,
    val addedAtMillis: Long,
)
