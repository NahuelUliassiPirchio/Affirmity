package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ActivityJournalTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: AffirmityDatabase? = null
    private fun open(): AffirmityDatabase = Room.databaseBuilder(context, AffirmityDatabase::class.java, DB)
        .build().also { database = it }

    @After fun close() {
        database?.close()
        context.deleteDatabase(DB)
    }

    @Test fun eventAndPendingPayloadSurviveRestartWithoutDuplicating() = runBlocking {
        var db = open()
        val event = ActivityEventEntity("account:A", "event", 20_000, "guided", 1_000,
            0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
        db.activityDao().record(event)
        db.close()
        db = open()
        db.activityDao().record(event)
        assertEquals(listOf(event), db.activityDao().getRange("account:A", 20_000, 20_000))
        assertEquals(listOf(event), db.activityDao().pending("account:A", ACTIVITY_PENDING_PAGE_SIZE))
        assertEquals(emptyList<ActivityEventEntity>(), db.activityDao().getRange("account:B", 20_000, 20_000))
    }

    @Test fun pendingQueriesRespectLimitAndKeepOldestFirstOrder() = runBlocking {
        val dao = open().activityDao()
        val base = ActivityEventEntity("account:A", "e000", 20_000, "guided", 1_000,
            0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
        val events = (0 until 5).map { base.copy(eventId = "e00$it", occurredAtMillis = 1_000L + it) }
        events.reversed().forEach { dao.record(it) }
        assertEquals(events.take(3), dao.pending("account:A", 3))
        assertEquals(events, dao.pending("account:A", ACTIVITY_PENDING_PAGE_SIZE))
        dao.acknowledge("account:A", "e000")
        assertEquals(events.drop(1).take(2), dao.pending("account:A", 2))
        val coverage = (0 until 5).map {
            ActivityCoverageEntity("account:A", "device", 20_000L + it, 20_000L + it, 1, false, "PENDING")
        }
        coverage.reversed().forEach { dao.recordCoverage(it) }
        assertEquals(coverage.take(3), dao.pendingCoverage("account:A", 3))
        assertEquals(coverage, dao.pendingCoverage("account:A", ACTIVITY_PENDING_PAGE_SIZE))
    }

    @Test fun sameIdentityRejectsConflictingFactsAndDoesNotResetAcknowledgement() = runBlocking {
        val dao = open().activityDao()
        val event = ActivityEventEntity("account:A", "event", 20_000, "guided", 1_000,
            0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
        dao.record(event)
        dao.acknowledge(event.owner, event.eventId)
        assertEquals("UPLOADED", dao.record(event).uploadState)
        assertEquals(emptyList<ActivityEventEntity>(), dao.pending(event.owner, ACTIVITY_PENDING_PAGE_SIZE))
        try {
            dao.record(event.copy(activeSeconds = 180))
            fail("Conflicting event identity must fail")
        } catch (_: IllegalArgumentException) {
            assertEquals(120L, dao.get(event.owner, event.eventId)?.activeSeconds)
        }
    }

    @Test fun coveragePreservesSchemaIdentityAndUncertaintyAcrossRestart() = runBlocking {
        var db = open()
        db.activityDao().anonymousOwner(ActivityOwnerEntity("local:generation", "device", null, true))
        val full = ActivityCoverageEntity("local:generation", "device", 20_000, 20_001, 1, false, "PENDING")
        val unsupported = full.copy(recordingSchema = 2, uncertain = true)
        db.activityDao().recordCoverage(full)
        db.activityDao().recordCoverage(unsupported)
        db.close()
        db = open()
        assertEquals(setOf(full, unsupported), db.activityDao().coverageRange(full.owner, 20_000, 20_001).toSet())
    }

    @Test fun claimSnapshotReadsAllRetainedDaysAndRejectsLateWrites() = runBlocking {
        val db = open()
        val owner = db.initializeAnonymousActivityOwner(ActivityOwnerEntity("local:snapshot", "device", null, true))
        db.activityDailyFactDao().upsertMood(owner.owner, -10_000, 4, "retained")
        val event = ActivityEventEntity(owner.owner, "captured", 20_000, "view", 1, 1, 1,
            "UTC", null, null, 0, "device", "LOCAL")
        db.activityDao().record(event)
        val snapshot = db.claimActivitySnapshot(owner.owner, "A")
        assertEquals(-10_000L, snapshot.dailyFacts.single().epochDay)
        assertEquals(listOf(event), snapshot.events)
        assertEquals("A", snapshot.owner.claimedUid)
        assertEquals(listOf(owner.owner), db.activityDao().claimedOwners("A").map { it.owner })
        assertEquals(emptyList<ActivityOwnerEntity>(), db.activityDao().claimedOwners("B"))
        try { db.activityDao().record(event.copy(eventId = "late")); fail("Late write must fail") }
        catch (_: IllegalArgumentException) { assertEquals(listOf(event), db.claimActivitySnapshot(owner.owner, "A").events) }
    }

    @Test fun anonymousGenerationClaimSurvivesRestartAndCannotMoveToAnotherAccount() = runBlocking {
        var db = open()
        val first = ActivityOwnerEntity("local:first", "device", null, true)
        assertEquals(first, db.activityDao().anonymousOwner(first))
        assertEquals(first, db.activityDao().anonymousOwner(first.copy(owner = "local:unused")))
        db.activityDao().claimAnonymous(first.owner, "A")
        db.close()
        db = open()
        assertEquals("A", db.activityDao().owner(first.owner)?.claimedUid)
        assertEquals(false, db.activityDao().owner(first.owner)?.active)
        db.activityDao().claimAnonymous(first.owner, "A")
        try {
            db.activityDao().claimAnonymous(first.owner, "B")
            fail("An anonymous generation can only be claimed once")
        } catch (_: IllegalArgumentException) {
            assertEquals("A", db.activityDao().owner(first.owner)?.claimedUid)
        }
        val second = first.copy(owner = "local:second")
        assertEquals(second, db.activityDao().anonymousOwner(second))
        assertEquals("A", db.activityDao().owner(first.owner)?.claimedUid)
    }

    @Test fun coverageAcknowledgementSurvivesRetryAndNeverAcknowledgesAnotherOwner() = runBlocking {
        val dao = open().activityDao()
        val full = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        val foreign = full.copy(owner = "account:B")
        dao.recordCoverage(full)
        dao.recordCoverage(foreign)
        dao.acknowledgeCoverage(full)
        dao.recordCoverage(full)
        assertEquals(emptyList<ActivityCoverageEntity>(), dao.pendingCoverage(full.owner, ACTIVITY_PENDING_PAGE_SIZE))
        assertEquals(listOf(foreign), dao.pendingCoverage(foreign.owner, ACTIVITY_PENDING_PAGE_SIZE))
        assertEquals("UPLOADED", dao.coverageRange(full.owner, 20_000, 20_001).single().uploadState)
    }

    @Test fun failedRowsAreExcludedFromPendingButNeverDeleted() = runBlocking {
        val dao = open().activityDao()
        val event = ActivityEventEntity("account:A", "event", 20_000, "guided", 1_000,
            0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
        val full = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        dao.record(event)
        dao.recordCoverage(full)
        dao.markFailed(event.owner, event.eventId)
        dao.markCoverageFailed(full)
        assertEquals(emptyList<ActivityEventEntity>(), dao.pending(event.owner, ACTIVITY_PENDING_PAGE_SIZE))
        assertEquals(emptyList<ActivityCoverageEntity>(), dao.pendingCoverage(full.owner, ACTIVITY_PENDING_PAGE_SIZE))
        assertEquals("FAILED", dao.getRange(event.owner, 20_000, 20_000).single().uploadState)
        assertEquals("FAILED", dao.coverageRange(full.owner, 20_000, 20_001).single().uploadState)

        dao.rearmFailed(event.owner)
        dao.rearmFailedCoverage(full.owner)
        assertEquals(listOf(event), dao.pending(event.owner, ACTIVITY_PENDING_PAGE_SIZE))
        assertEquals(listOf(full), dao.pendingCoverage(full.owner, ACTIVITY_PENDING_PAGE_SIZE))

        dao.acknowledge(event.owner, event.eventId)
        dao.acknowledgeCoverage(full)
        dao.markFailed(event.owner, event.eventId)
        dao.markCoverageFailed(full)
        assertEquals("UPLOADED", dao.getRange(event.owner, 20_000, 20_000).single().uploadState)
        assertEquals("UPLOADED", dao.coverageRange(full.owner, 20_000, 20_001).single().uploadState)
    }

    private companion object { const val DB = "activity-journal-test" }
}
