package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.local.AdUnlockDao
import com.pirxhio.affirmity.data.local.AdUnlockEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thin [AdUnlockRepository] wrapper delegating 1:1 to [AdUnlockDao], mapping
 * [AdUnlockEntity] <-> [AdUnlockRecord]. A row whose [AdUnlockEntity.contentType] no longer
 * parses to a known [ContentType] (design §4a) is DROPPED on read via [mapNotNull], never
 * crashed on — the same "tolerate unknown enum values from storage" posture as
 * [com.pirxhio.affirmity.access.ContentKey.parse]. */
class RoomAdUnlockRepository(private val dao: AdUnlockDao) : AdUnlockRepository {

    override fun observeDurableUnlocks(): Flow<List<AdUnlockRecord>> =
        dao.observeAll().map { entities -> entities.mapNotNull(::toRecord) }

    override suspend fun getDurableUnlocks(): List<AdUnlockRecord> =
        dao.getAll().mapNotNull(::toRecord)

    override suspend fun grantDurableUnlock(record: AdUnlockRecord) =
        dao.insertIfAbsent(record.toEntity())

    private fun toRecord(entity: AdUnlockEntity): AdUnlockRecord? {
        val type = ContentType.fromWireName(entity.contentType) ?: return null
        return AdUnlockRecord(
            key = ContentKey(type, entity.contentId),
            grantedAtMillis = entity.grantedAtMillis,
            expiresAtMillis = entity.expiresAtMillis,
        )
    }

    private fun AdUnlockRecord.toEntity(): AdUnlockEntity = AdUnlockEntity(
        contentKey = key.storageKey,
        contentType = key.type.wireName,
        contentId = key.id,
        grantedAtMillis = grantedAtMillis,
        expiresAtMillis = expiresAtMillis,
    )
}
