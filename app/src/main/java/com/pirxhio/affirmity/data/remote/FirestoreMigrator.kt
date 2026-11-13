package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.local.MigrationConfirmationPreferences
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Narrow seam over Firestore marker-check and batch-commit so [FirestoreMigrator]'s branching
 * logic is unit-testable on the JVM without a real Firebase/Android dependency (design.md's
 * Testing Strategy — mirrors [com.pirxhio.affirmity.auth.FirebaseAuthSource]'s convention).
 */
interface FirestoreMigrationSource {
    suspend fun markerExists(uid: String): Boolean

    /**
     * Like [markerExists] but says whether a present marker was confirmed by the server. The default
     * treats every present marker as server-backed; only the real source can tell cache from server.
     */
    suspend fun readMarker(uid: String): MarkerRead =
        if (markerExists(uid)) MarkerRead.PRESENT_FROM_SERVER else MarkerRead.ABSENT
    suspend fun commitChunk(writes: List<DocWrite>)
}

enum class MarkerRead {
    ABSENT,
    /** Read from the server, with no pending local writes: safe to remember. */
    PRESENT_FROM_SERVER,
    /** Served from the offline cache (or a pending write): the server may have deleted it since. */
    PRESENT_FROM_CACHE,
}

/** Only a marker the server itself vouched for (not cached, no pending local write) is safe to remember. */
fun classifyMarker(exists: Boolean, isFromCache: Boolean, hasPendingWrites: Boolean): MarkerRead = when {
    !exists -> MarkerRead.ABSENT
    isFromCache || hasPendingWrites -> MarkerRead.PRESENT_FROM_CACHE
    else -> MarkerRead.PRESENT_FROM_SERVER
}

/** Real [FirestoreMigrationSource] — thin Firebase SDK glue, untested by design. */
class RealFirestoreMigrationSource(private val firestore: FirebaseFirestore) : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = readMarker(uid) != MarkerRead.ABSENT

    override suspend fun readMarker(uid: String): MarkerRead {
        val snapshot = firestore.document(FirestorePaths.migratedMarkerDoc(uid)).get().await()
        return classifyMarker(snapshot.exists(), snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())
    }

    override suspend fun commitChunk(writes: List<DocWrite>) {
        // Firestore requires all reads before any writes. The transaction closes retry-vs-edit races.
        firestore.runTransaction { transaction ->
            val destinations = writes.associateWith { write ->
                if (write.policy == MigrationWritePolicy.MERGE) null
                else transaction.get(firestore.document(write.path)).let { if (it.exists()) it.data else null }
            }
            writes.forEach { write ->
                resolveMigrationWrite(write, destinations[write])?.let { fields ->
                    transaction.set(firestore.document(write.path), fields, SetOptions.merge())
                }
            }
        }.await()
    }
}

/**
 * Device-local memory of which uids are known to have `users/{uid}/meta/migrated` on the server.
 * The marker is permanent once written, so a confirmed uid never needs the server check again.
 */
interface MigrationConfirmationStore {
    suspend fun isConfirmed(uid: String): Boolean
    suspend fun confirm(uid: String)

    /** Remembers nothing: every launch checks the server. */
    object None : MigrationConfirmationStore {
        override suspend fun isConfirmed(uid: String) = false
        override suspend fun confirm(uid: String) = Unit
    }
}

/** Persists confirmations in a dedicated DataStore that is excluded from Auto Backup. */
class DataStoreMigrationConfirmations(private val preferences: MigrationConfirmationPreferences) : MigrationConfirmationStore {
    override suspend fun isConfirmed(uid: String) = preferences.isConfirmed(uid)
    override suspend fun confirm(uid: String) = preferences.confirm(uid)
}

enum class MigrationOutcome {
    /** The local confirmation existed: no snapshot was built and Firestore was not touched. */
    SKIPPED_CONFIRMED_LOCALLY,
    /** The server marker already existed. */
    ALREADY_MIGRATED,
    /** The plan was committed, marker last. */
    MIGRATED,
}

class FirestoreMigrator(
    private val source: FirestoreMigrationSource,
    private val confirmations: MigrationConfirmationStore = MigrationConfirmationStore.None,
    private val report: (message: String, cause: Throwable) -> Unit = { _, _ -> },
) {

    constructor(
        firestore: FirebaseFirestore,
        confirmations: MigrationConfirmationStore,
        report: (message: String, cause: Throwable) -> Unit,
    ) : this(RealFirestoreMigrationSource(firestore), confirmations, report)

    /**
     * Skips everything (no snapshot, no Firestore call) when [uid] is already confirmed on this device.
     * Otherwise reads the server marker, migrating when it is absent. The confirmation is recorded only
     * once the marker is known to be on the server: observed from the server, or its write awaited.
     * A marker served from the offline cache still skips the migration this launch (as before) but is
     * not remembered, because the server may have deleted it since; a later online launch re-checks.
     * Skipping on a confirmation equals the marker check it replaces: with the marker present, legacy
     * local data created later was never re-migrated anyway.
     */
    suspend fun ensureMigrated(uid: String, snapshot: suspend () -> MigrationSnapshot): MigrationOutcome {
        // The confirmation is an optional cache: an unreadable one means "unconfirmed", never a blocker.
        val confirmed = try {
            confirmations.isConfirmed(uid)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            report("migration confirmation unreadable; checking the server", failure)
            false
        }
        if (confirmed) return MigrationOutcome.SKIPPED_CONFIRMED_LOCALLY
        val outcome = when (source.readMarker(uid)) {
            MarkerRead.PRESENT_FROM_CACHE -> return MigrationOutcome.ALREADY_MIGRATED
            MarkerRead.PRESENT_FROM_SERVER -> MigrationOutcome.ALREADY_MIGRATED
            MarkerRead.ABSENT -> {
                MigrationPlan.build(snapshot()).forEach { chunk -> source.commitChunk(chunk) }
                MigrationOutcome.MIGRATED
            }
        }
        // Best effort: failing to remember only costs the next launch one server check.
        try {
            confirmations.confirm(uid)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            report("migration confirmation not saved; the next launch re-checks", failure)
        }
        return outcome
    }
}
