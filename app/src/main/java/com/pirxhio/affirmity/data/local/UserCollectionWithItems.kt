package com.pirxhio.affirmity.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class UserCollectionWithItems(
    @Embedded val collection: UserCollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "userCollectionId",
        entity = UserCollectionItemEntity::class,
        projection = ["affirmationId"],
    )
    val affirmationIds: List<String>,
)
