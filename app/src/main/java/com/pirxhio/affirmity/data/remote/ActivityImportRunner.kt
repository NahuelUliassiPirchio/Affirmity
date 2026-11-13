package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.ActivityClaimSnapshot
import com.pirxhio.affirmity.data.local.ActivityOwnerEntity
import com.pirxhio.affirmity.data.local.AffirmityDatabase

/** Local reads and writes the import loop needs, kept apart from Room so the loop runs on the JVM. */
interface ActivityImportStore {
    suspend fun pendingOwners(uid: String): List<ActivityOwnerEntity>
    suspend fun claimedCount(uid: String): Int
    suspend fun snapshot(owner: String, uid: String): ActivityClaimSnapshot
    suspend fun markImported(owner: String, uid: String, atMillis: Long)
}

/** What one import pass did; logged so cold-start cost is observable on a device. */
data class ActivityImportStats(
    val claimed: Int = 0,
    val skippedImported: Int = 0,
    val skippedEmpty: Int = 0,
    val networkChecked: Int = 0,
)

class ActivityImportRunner(
    private val importer: ActivityHistoryImporter,
    private val store: ActivityImportStore,
    private val nowMillis: () -> Long,
    private val report: (ActivityImportStats) -> Unit,
) {
    /**
     * Imports every claimed generation of [uid] that is not yet known to be on the server. Only pending
     * rows are read; sealed rows stay forever because they are the write barrier. Progress is
     * persisted per owner right after it succeeds, so a failure (offline, cancelled) never discards the
     * owners already done. Owners already marked cost nothing; empty ones are marked without a write: the
     * server marker only prevents re-importing a generation's data on this device, so an empty generation
     * has nothing for it to protect. Claimed generations are write-barriered, so "empty" is permanent.
     */
    suspend fun run(uid: String, currentUid: () -> String?) {
        var stats = ActivityImportStats()
        try {
            val owners = store.pendingOwners(uid)
            val claimed = store.claimedCount(uid)
            stats = stats.copy(claimed = claimed, skippedImported = claimed - owners.size)
            for (owner in owners) {
                val snapshot = store.snapshot(owner.owner, uid)
                if (snapshot.dailyFacts.isEmpty() && snapshot.events.isEmpty() && snapshot.coverage.isEmpty()) {
                    store.markImported(owner.owner, uid, nowMillis())
                    stats = stats.copy(skippedEmpty = stats.skippedEmpty + 1)
                    continue
                }
                stats = stats.copy(networkChecked = stats.networkChecked + 1)
                importer.import(snapshot, uid, currentUid)
                store.markImported(owner.owner, uid, nowMillis())
            }
        } finally {
            report(stats)
        }
    }
}

class RoomActivityImportStore(private val database: AffirmityDatabase) : ActivityImportStore {
    override suspend fun pendingOwners(uid: String) = database.activityDao().pendingImportOwners(uid)
    override suspend fun claimedCount(uid: String) = database.activityDao().countClaimed(uid)
    override suspend fun snapshot(owner: String, uid: String) = database.claimActivitySnapshot(owner, uid)
    override suspend fun markImported(owner: String, uid: String, atMillis: Long) =
        database.activityDao().markImported(owner, uid, atMillis)
}
