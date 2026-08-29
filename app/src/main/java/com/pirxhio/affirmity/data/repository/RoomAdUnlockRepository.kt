package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.local.AdUnlockDao
import com.pirxhio.affirmity.data.local.AdUnlockEntity
import com.pirxhio.affirmity.data.local.TimedAdUnlockDao
import com.pirxhio.affirmity.data.local.TimedAdUnlockEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thin [AdUnlockRepository] wrapper delegating 1:1 to [AdUnlockDao] (ONE_TIME_TRIAL) and
 * [TimedAdUnlockDao] (TIMED_REPEATABLE, design D16 -- a SEPARATE table, never a reuse of
 * `ad_unlock`), mapping each entity <-> [AdUnlockRecord]. A row whose `contentType` no longer
 * parses to a known [ContentType] is DROPPED on read via [mapNotNull], never crashed on -- the
 * same "tolerate unknown enum values from storage" posture as
 * [com.pirxhio.affirmity.access.ContentKey.parse]. */
class RoomAdUnlockRepository(
    private val dao: AdUnlockDao,
    private val timedDao: TimedAdUnlockDao,
) : AdUnlockRepository {

    override fun observeDurableUnlocks(): Flow<List<AdUnlockRecord>> =
        dao.observeAll().map { entities -> entities.mapNotNull(::toRecord) }

    override suspend fun getDurableUnlocks(): List<AdUnlockRecord> =
        dao.getAll().mapNotNull(::toRecord)

    override suspend fun grantDurableUnlock(record: AdUnlockRecord) =
        dao.insertIfAbsent(record.toEntity())

    override fun observeTimedUnlocks(): Flow<List<AdUnlockRecord>> =
        timedDao.observeAll().map { entities -> entities.mapNotNull(::toRecord) }

    override suspend fun grantTimedUnlock(record: AdUnlockRecord) =
        timedDao.upsert(record.toTimedEntity())

    private fun toRecord(entity: AdUnlockEntity): AdUnlockRecord? {
        val type = ContentType.fromWireName(entity.contentType) ?: return null
        return AdUnlockRecord(
            key = ContentKey(type, entity.contentId),
            grantedAtMillis = entity.grantedAtMillis,
            expiresAtMillis = entity.expiresAtMillis,
        )
    }

    private fun toRecord(entity: TimedAdUnlockEntity): AdUnlockRecord? {
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

    private fun AdUnlockRecord.toTimedEntity(): TimedAdUnlockEntity = TimedAdUnlockEntity(
        contentKey = key.storageKey,
        contentType = key.type.wireName,
        contentId = key.id,
        grantedAtMillis = grantedAtMillis,
        expiresAtMillis = expiresAtMillis,
    )
}
