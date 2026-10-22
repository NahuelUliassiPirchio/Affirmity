package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.pirxhio.affirmity.data.local.*
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

interface ActivityImportSource {
    suspend fun markerExists(uid: String, generation: String): Boolean
    suspend fun writeFacts(writes: List<DocWrite>)
    suspend fun uploadEvent(uid: String, event: ActivityEventEntity)
    suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity)
    suspend fun acknowledge(uid: String, generation: String)
}

class ActivityHistoryImporter(private val source: ActivityImportSource) {
    suspend fun import(snapshot: ActivityClaimSnapshot, uid: String, currentUid: () -> String?) {
        require(snapshot.owner.claimedUid == uid && !snapshot.owner.active)
        val owner = snapshot.owner.owner
        require(snapshot.dailyFacts.all { it.owner == owner } && snapshot.events.all { it.owner == owner } &&
            snapshot.coverage.all { it.owner == owner }) { "Foreign import source" }
        fun checkIdentity() { if (currentUid() != uid) throw CancellationException("Activity import identity changed") }
        checkIdentity()
        if (source.markerExists(uid, owner)) return
        val writes = snapshot.dailyFacts.flatMap { fact -> buildList {
            fact.moodValue?.let { add(DocWrite(FirestorePaths.dailyMoodDoc(uid, fact.epochDay),
                dailyMoodToMap(DailyMoodEntity(fact.epochDay, it, fact.note)), MigrationWritePolicy.CREATE_IF_ABSENT)) }
            if (fact.completionPresent) add(DocWrite(FirestorePaths.dailyCompletionDoc(uid, fact.epochDay),
                dailyCompletionToMap(DailyCompletionEntity(fact.epochDay, fact.meditationDone, fact.affirmationDone)),
                MigrationWritePolicy.OR_COMPLETION))
            fact.healedAtMillis?.let { add(DocWrite(FirestorePaths.streakHealerUseDoc(uid, fact.epochDay),
                streakHealerUseToMap(StreakHealerUseEntity(fact.epochDay, it)), MigrationWritePolicy.CREATE_IF_ABSENT)) }
        } }
        for (chunk in writes.chunked(200)) { checkIdentity(); source.writeFacts(chunk) }
        for (event in snapshot.events) { checkIdentity(); source.uploadEvent(uid, event.copy(owner = "account:$uid")) }
        for (coverage in snapshot.coverage) { checkIdentity(); source.uploadCoverage(uid, coverage.copy(owner = "account:$uid")) }
        checkIdentity()
        source.acknowledge(uid, owner)
    }
}

fun activityImportMarkerPath(uid: String, generation: String): String {
    require(uid.isNotBlank() && '/' !in uid && generation.startsWith("local:"))
    val hash = MessageDigest.getInstance("SHA-256").digest(generation.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    return "users/$uid/meta/activity-v1-$hash"
}

class FirebaseActivityImportSource(private val firestore: FirebaseFirestore) : ActivityImportSource {
    private val facts = RealFirestoreMigrationSource(firestore)
    private val journal = FirebaseActivityClient(firestore)
    override suspend fun markerExists(uid: String, generation: String) =
        firestore.document(activityImportMarkerPath(uid, generation)).get(Source.SERVER).await().exists()
    override suspend fun writeFacts(writes: List<DocWrite>) = facts.commitChunk(writes)
    override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = journal.uploadEvent(uid, event)
    override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = journal.uploadCoverage(uid, coverage)
    override suspend fun acknowledge(uid: String, generation: String) {
        firestore.document(activityImportMarkerPath(uid, generation))
            .set(mapOf("schemaVersion" to 1, "sourceGeneration" to generation)).await()
    }
}
