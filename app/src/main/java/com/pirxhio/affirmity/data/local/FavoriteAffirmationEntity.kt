package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_affirmations")
data class FavoriteAffirmationEntity(
    @PrimaryKey val affirmationId: String,
    val favoritedAtMillis: Long,
)
