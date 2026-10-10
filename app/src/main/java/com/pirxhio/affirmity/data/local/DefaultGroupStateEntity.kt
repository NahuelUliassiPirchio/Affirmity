package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted state of a code-defined default group (DG-3). A missing row means disabled and never
 * used; rows are only written when the user enables a group, never seeded.
 */
@Entity(tableName = "default_group_state")
data class DefaultGroupStateEntity(
    @PrimaryKey val id: String,
    val enabled: Boolean,
    val lastUsedAtMillis: Long,
)
