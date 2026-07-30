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
        val batch = firestore.batch()
        writes.forEach { write ->
            batch.set(firestore.document(write.path), write.fields, SetOptions.merge())
        }
        batch.commit().await()
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
