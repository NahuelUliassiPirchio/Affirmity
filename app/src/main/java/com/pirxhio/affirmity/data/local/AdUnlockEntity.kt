package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A durable (i.e. [com.pirxhio.affirmity.access.AdUnlockPolicy.ONE_TIME_TRIAL]) ad unlock,
 * persisted for a signed-out user (design §4a). [contentKey] mirrors
 * [com.pirxhio.affirmity.access.ContentKey.storageKey]; [contentType]/[contentId] are stored
 * alongside it (not re-derived) so a row can be mapped back to a
 * [com.pirxhio.affirmity.access.AdUnlockRecord] without re-parsing the primary key.
 * [expiresAtMillis] is `null` for a permanent grant.
 */
@Entity(tableName = "ad_unlock")
data class AdUnlockEntity(
    @PrimaryKey val contentKey: String,
    val contentType: String,
    val contentId: String,
    val grantedAtMillis: Long,
    val expiresAtMillis: Long?,
)
