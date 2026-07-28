package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "affirmations")
data class AffirmationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val backgroundType: String,
    val backgroundValue: String,
)
