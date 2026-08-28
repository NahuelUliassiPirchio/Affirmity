package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A [com.pirxhio.affirmity.access.AdUnlockPolicy.TIMED_REPEATABLE] grant, persisted for a
 * signed-out user (design D16). A SIBLING of [AdUnlockEntity], never a reuse of it: this table
 * permits overwrite (re-earning after expiry) and [AdUnlockEntity]'s table must never. Same
 * column shape as [AdUnlockEntity] deliberately, so the two stores stay easy to compare.
 */
@Entity(tableName = "timed_ad_unlock")
data class TimedAdUnlockEntity(
    @PrimaryKey val contentKey: String,
    val contentType: String,
    val contentId: String,
    val grantedAtMillis: Long,
    val expiresAtMillis: Long?,
)
