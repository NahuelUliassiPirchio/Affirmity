package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Room-backed (real SQL and transactions) coverage of the claim barrier and the import bookkeeping. */
class ActivityOwnerSealingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: AffirmityDatabase? = null
    private fun open(): AffirmityDatabase = Room.databaseBuilder(context, AffirmityDatabase::class.java, DB)
        .build().also { database = it }

    @After fun close() {
        database?.close()
        context.deleteDatabase(DB)
    }

    private fun candidate(name: String) = ActivityOwnerEntity("local:$name", "device", null, true)

    private fun event(owner: String, id: String) = ActivityEventEntity(owner, id, 20_000, "view", 1, 1, 1,
        "UTC", null, null, 0, "device", "LOCAL")

    @Test fun claimingAnEmptyGenerationStillSealsItAgainstLateWrites() = runBlocking {
        val db = open()
        val owner = db.initializeAnonymousActivityOwner(candidate("empty"))
        val snapshot = db.claimActivitySnapshot(owner.owner, "A")
        assertEquals("A", snapshot.owner.claimedUid)
        assertEquals(false, db.activityDao().owner(owner.owner)?.active)
        try { db.activityDao().record(event(owner.owner, "late")); fail("Late event must be rejected") }
        catch (_: IllegalArgumentException) { }
        try { db.activityDailyFactDao().upsertMood(owner.owner, 20_000, 2, "late"); fail("Late fact must be rejected") }
        catch (_: AnonymousGenerationClaimedException) { }
        assertEquals(emptyList<ActivityEventEntity>(), db.activityDao().getRange(owner.owner, Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test fun aLateWriteFromA_neverReachesB() = runBlocking {
        val db = open()
        val g = db.initializeAnonymousActivityOwner(candidate("g"))
        db.claimActivitySnapshot(g.owner, "A")
        runCatching { db.activityDao().record(event(g.owner, "late")) }
        runCatching { db.activityDailyFactDao().upsertMood(g.owner, 20_000, 2, "A's note") }
        // A signs out; B signs in on a fresh generation.
        val fresh = db.initializeAnonymousActivityOwner(candidate("fresh"))
        val snapshot = db.claimActivitySnapshot(fresh.owner, "B")
        assertTrue(snapshot.events.isEmpty() && snapshot.dailyFacts.isEmpty())
        assertEquals(listOf(fresh.owner), db.activityDao().claimedOwners("B").map { it.owner })
        assertEquals("A", db.activityDao().owner(g.owner)?.claimedUid)
    }

    @Test fun markImportedRecordsOnceAndOnlyForTheClaimingUid() = runBlocking {
        val db = open()
        val dao = db.activityDao()
        val owner = db.initializeAnonymousActivityOwner(candidate("one"))
        dao.markImported(owner.owner, "A", 10L)
        assertNull("an unclaimed generation is never marked", dao.owner(owner.owner)?.importedAtMillis)
        db.claimActivitySnapshot(owner.owner, "A")
        dao.markImported(owner.owner, "B", 20L)
        assertNull("a foreign uid cannot mark it", dao.owner(owner.owner)?.importedAtMillis)
        dao.markImported(owner.owner, "A", 30L)
        dao.markImported(owner.owner, "A", 40L)
        assertEquals(30L, dao.owner(owner.owner)?.importedAtMillis)
    }

    @Test fun pendingOwnersReturnOnlyUnimportedRowsClaimedByTheUid() = runBlocking {
        val db = open()
        val dao = db.activityDao()
        val names = listOf("a", "b", "c")
        for (name in names) {
            val owner = db.initializeAnonymousActivityOwner(candidate(name))
            db.claimActivitySnapshot(owner.owner, "A")
        }
        val other = db.initializeAnonymousActivityOwner(candidate("other"))
        db.claimActivitySnapshot(other.owner, "B")
        val active = db.initializeAnonymousActivityOwner(candidate("active"))
        dao.markImported("local:b", "A", 5L)
        assertEquals(listOf("local:a", "local:c"), dao.pendingImportOwners("A").map { it.owner })
        assertEquals(3, dao.countClaimed("A"))
        assertEquals(listOf("local:other"), dao.pendingImportOwners("B").map { it.owner })
        assertTrue(active.owner !in dao.pendingImportOwners("A").map { it.owner })
        assertEquals(emptyList<ActivityOwnerEntity>(), dao.pendingImportOwners("nobody"))
    }

    private companion object {
        const val DB = "activity-owner-sealing-test"
    }
}
