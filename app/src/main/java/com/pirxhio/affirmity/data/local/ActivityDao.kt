package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Rows loaded per upload page, so a long offline backlog never sits in memory at once. */
const val ACTIVITY_PENDING_PAGE_SIZE = 100

@Dao
abstract class ActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEvent(event: ActivityEventEntity): Long

    @Query("SELECT * FROM activity_event WHERE owner = :owner AND eventId = :eventId")
    abstract suspend fun get(owner: String, eventId: String): ActivityEventEntity?

    @Transaction
    open suspend fun record(event: ActivityEventEntity): ActivityEventEntity {
        require(event.activeSeconds >= 0 && event.endMillis >= event.startMillis)
        get(event.owner, event.eventId)?.let { existing ->
            require(existing.copy(uploadState = event.uploadState) == event) { "Conflicting activity identity" }
            return existing
        }
        requireWritableOwner(event.owner)
        if (insertEvent(event) != -1L) return event
        val existing = requireNotNull(get(event.owner, event.eventId))
        require(existing.copy(uploadState = event.uploadState) == event) { "Conflicting activity identity" }
        return existing
    }

    private suspend fun requireWritableOwner(owner: String) {
        if (!owner.startsWith("local:")) return
        val state = requireNotNull(owner(owner)) { "Unknown anonymous generation" }
        require(state.active && state.claimedUid == null) { "Anonymous generation already claimed" }
    }

    @Query("SELECT * FROM activity_event WHERE owner = :owner AND epochDay BETWEEN :from AND :to ORDER BY occurredAtMillis, eventId")
    abstract suspend fun getRange(owner: String, from: Long, to: Long): List<ActivityEventEntity>

    @Query("SELECT * FROM activity_event WHERE owner = :owner AND epochDay BETWEEN :from AND :to ORDER BY occurredAtMillis, eventId")
    abstract fun observeRange(owner: String, from: Long, to: Long): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_event WHERE owner = :owner AND uploadState = 'PENDING' ORDER BY occurredAtMillis, eventId LIMIT :limit")
    abstract suspend fun pending(owner: String, limit: Int): List<ActivityEventEntity>

    @Query("UPDATE activity_event SET uploadState = 'UPLOADED' WHERE owner = :owner AND eventId = :eventId")
    abstract suspend fun acknowledge(owner: String, eventId: String)

    /** Terminal rejection: kept for recovery, excluded from [pending] and from the sync-pending count. */
    @Query("UPDATE activity_event SET uploadState = 'FAILED' WHERE owner = :owner AND eventId = :eventId AND uploadState = 'PENDING'")
    abstract suspend fun markFailed(owner: String, eventId: String)

    /** Bounded recovery: FAILED rows get one fresh attempt per retry session. */
    @Query("UPDATE activity_event SET uploadState = 'PENDING' WHERE owner = :owner AND uploadState = 'FAILED'")
    abstract suspend fun rearmFailed(owner: String)

    @Query("UPDATE activity_coverage SET uploadState = 'PENDING' WHERE owner = :owner AND uploadState = 'FAILED'")
    abstract suspend fun rearmFailedCoverage(owner: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCoverage(coverage: ActivityCoverageEntity): Long

    @Transaction
    open suspend fun recordCoverage(coverage: ActivityCoverageEntity): Long {
        require(coverage.fromEpochDay <= coverage.toEpochDay && coverage.recordingSchema > 0)
        val existing = coverageRange(coverage.owner, coverage.fromEpochDay, coverage.toEpochDay)
            .firstOrNull { it.copy(uploadState = coverage.uploadState) == coverage }
        if (existing != null) return -1L
        requireWritableOwner(coverage.owner)
        return insertCoverage(coverage)
    }

    @Query("SELECT * FROM activity_coverage WHERE owner = :owner AND fromEpochDay <= :to AND toEpochDay >= :from")
    abstract suspend fun coverageRange(owner: String, from: Long, to: Long): List<ActivityCoverageEntity>

    @Query("SELECT * FROM activity_coverage WHERE owner = :owner AND fromEpochDay <= :to AND toEpochDay >= :from")
    abstract fun observeCoverage(owner: String, from: Long, to: Long): Flow<List<ActivityCoverageEntity>>

    @Query("SELECT * FROM activity_coverage WHERE owner = :owner AND uploadState = 'PENDING' ORDER BY fromEpochDay, toEpochDay, sourceId, recordingSchema, uncertain LIMIT :limit")
    abstract suspend fun pendingCoverage(owner: String, limit: Int): List<ActivityCoverageEntity>

    @Query("UPDATE activity_coverage SET uploadState = 'UPLOADED' WHERE owner = :owner AND sourceId = :sourceId AND recordingSchema = :schema AND fromEpochDay = :from AND toEpochDay = :to AND uncertain = :uncertain")
    protected abstract suspend fun markCoverageUploaded(owner: String, sourceId: String, schema: Int, from: Long, to: Long, uncertain: Boolean)

    suspend fun acknowledgeCoverage(coverage: ActivityCoverageEntity) = markCoverageUploaded(
        coverage.owner, coverage.sourceId, coverage.recordingSchema, coverage.fromEpochDay, coverage.toEpochDay, coverage.uncertain,
    )

    @Query("UPDATE activity_coverage SET uploadState = 'FAILED' WHERE owner = :owner AND sourceId = :sourceId AND recordingSchema = :schema AND fromEpochDay = :from AND toEpochDay = :to AND uncertain = :uncertain AND uploadState = 'PENDING'")
    protected abstract suspend fun markCoverageFailedRow(owner: String, sourceId: String, schema: Int, from: Long, to: Long, uncertain: Boolean)

    suspend fun markCoverageFailed(coverage: ActivityCoverageEntity) = markCoverageFailedRow(
        coverage.owner, coverage.sourceId, coverage.recordingSchema, coverage.fromEpochDay, coverage.toEpochDay, coverage.uncertain,
    )

    @Query("SELECT * FROM activity_owner WHERE owner = :owner")
    abstract suspend fun owner(owner: String): ActivityOwnerEntity?

    @Query("SELECT * FROM activity_owner WHERE claimedUid = :uid ORDER BY owner")
    abstract suspend fun claimedOwners(uid: String): List<ActivityOwnerEntity>

    @Query("SELECT * FROM activity_owner WHERE active = 1 AND claimedUid IS NULL LIMIT 1")
    protected abstract suspend fun activeAnonymous(): ActivityOwnerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOwner(owner: ActivityOwnerEntity)

    @Query("UPDATE activity_owner SET claimedUid = :uid, active = 0 WHERE owner = :owner")
    protected abstract suspend fun assignOwner(owner: String, uid: String)

    /** Reuse the active generation across restarts; only a claimed generation is rotated. */
    @Transaction
    open suspend fun anonymousOwner(candidate: ActivityOwnerEntity): ActivityOwnerEntity {
        require(candidate.owner.startsWith("local:") && candidate.claimedUid == null && candidate.active)
        require(candidate.owner.length > "local:".length && candidate.sourceId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        return activeAnonymous() ?: candidate.also { insertOwner(it) }
    }

    /** Capture before importing. Retained originals can never be reassigned from A to B. */
    @Transaction
    open suspend fun claimAnonymous(owner: String, uid: String): ActivityOwnerEntity {
        require(owner.startsWith("local:") && uid.isNotBlank())
        val existing = requireNotNull(owner(owner))
        require(existing.claimedUid == null || existing.claimedUid == uid) { "Anonymous generation already claimed" }
        assignOwner(owner, uid)
        return existing.copy(claimedUid = uid, active = false)
    }
}
