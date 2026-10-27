package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Narrow seam over Firestore marker-check and batch-commit so [FirestoreMigrator]'s branching
 * logic is unit-testable on the JVM without a real Firebase/Android dependency (design.md's
 * Testing Strategy — mirrors [com.pirxhio.affirmity.auth.FirebaseAuthSource]'s convention).
 */
interface FirestoreMigrationSource {
    suspend fun markerExists(uid: String): Boolean
    suspend fun commitChunk(writes: List<DocWrite>)
}

/** Real [FirestoreMigrationSource] — thin Firebase SDK glue, untested by design. */
class RealFirestoreMigrationSource(private val firestore: FirebaseFirestore) : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean =
        firestore.document(FirestorePaths.migratedMarkerDoc(uid)).get().await().exists()

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
 * `ensureMigrated` is a no-op when `users/{uid}/meta/migrated` already exists (spec's
 * "Already-migrated user skips migration" scenario); otherwise it builds a [MigrationPlan] from
 * the snapshot and commits every chunk in order, marker last (spec's "First sign-in copies
 * existing data" + "Migration is idempotent on retry" scenarios).
 */
class FirestoreMigrator(private val source: FirestoreMigrationSource) {

    constructor(firestore: FirebaseFirestore) : this(RealFirestoreMigrationSource(firestore))

    suspend fun ensureMigrated(snapshot: MigrationSnapshot) {
        if (source.markerExists(snapshot.uid)) return
        MigrationPlan.build(snapshot).forEach { chunk -> source.commitChunk(chunk) }
    }
}
