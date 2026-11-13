package com.pirxhio.affirmity.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory stand-in for the activity tables, shared by the owner DAO and the daily-fact DAO. */
class FakeActivityStore {
    val owners = linkedMapOf<String, ActivityOwnerEntity>()
    val events = linkedMapOf<Pair<String, String>, ActivityEventEntity>()
    val coverages = mutableListOf<ActivityCoverageEntity>()
    val facts = mutableListOf<ActivityDailyFactEntity>()

    val dao: ActivityDao = object : ActivityDao() {
        override suspend fun owner(owner: String) = owners[owner]
        override suspend fun claimedOwners(uid: String) = owners.values.filter { it.claimedUid == uid }.sortedBy { it.owner }
        override suspend fun pendingImportOwners(uid: String) =
            owners.values.filter { it.claimedUid == uid && it.importedAtMillis == null }.sortedBy { it.owner }
        override suspend fun countClaimed(uid: String) = owners.values.count { it.claimedUid == uid }
        override suspend fun activeAnonymous() = owners.values.firstOrNull { it.active && it.claimedUid == null }
        override suspend fun insertOwner(owner: ActivityOwnerEntity) {
            check(owner.owner !in owners)
            owners[owner.owner] = owner
        }
        override suspend fun assignOwner(owner: String, uid: String) {
            owners[owner] = owners.getValue(owner).copy(claimedUid = uid, active = false)
        }
        override suspend fun markImported(owner: String, uid: String, atMillis: Long) {
            val row = owners[owner] ?: return
            if (row.claimedUid == uid && row.importedAtMillis == null) owners[owner] = row.copy(importedAtMillis = atMillis)
        }
        override suspend fun insertEvent(event: ActivityEventEntity): Long {
            val key = event.owner to event.eventId
            if (key in events) return -1L
            events[key] = event
            return 1L
        }
        override suspend fun get(owner: String, eventId: String) = events[owner to eventId]
        override suspend fun getRange(owner: String, from: Long, to: Long) =
            events.values.filter { it.owner == owner && it.epochDay in from..to }
        override fun observeRange(owner: String, from: Long, to: Long): Flow<List<ActivityEventEntity>> = flowOf(emptyList())
        override suspend fun pending(owner: String, limit: Int): List<ActivityEventEntity> = error("Not used")
        override suspend fun acknowledge(owner: String, eventId: String) = Unit
        override suspend fun markFailed(owner: String, eventId: String) = Unit
        override suspend fun rearmFailed(owner: String) = Unit
        override suspend fun rearmFailedCoverage(owner: String) = Unit
        override suspend fun insertCoverage(coverage: ActivityCoverageEntity): Long {
            if (coverage in coverages) return -1L
            coverages.add(coverage)
            return 1L
        }
        override suspend fun coverageRange(owner: String, from: Long, to: Long) =
            coverages.filter { it.owner == owner && it.fromEpochDay <= to && it.toEpochDay >= from }
        override fun observeCoverage(owner: String, from: Long, to: Long): Flow<List<ActivityCoverageEntity>> = flowOf(emptyList())
        override suspend fun pendingCoverage(owner: String, limit: Int): List<ActivityCoverageEntity> = error("Not used")
        override suspend fun markCoverageUploaded(owner: String, sourceId: String, schema: Int, from: Long, to: Long, uncertain: Boolean) = Unit
        override suspend fun markCoverageFailedRow(owner: String, sourceId: String, schema: Int, from: Long, to: Long, uncertain: Boolean) = Unit
    }

    val dailyFacts: ActivityDailyFactDao = object : ActivityDailyFactDao() {
        override suspend fun getRange(owner: String, from: Long, to: Long) =
            facts.filter { it.owner == owner && it.epochDay in from..to }
        override fun observeRange(owner: String, from: Long, to: Long): Flow<List<ActivityDailyFactEntity>> = flowOf(facts)
        override suspend fun getAll(owner: String) = facts.filter { it.owner == owner }.sortedBy { it.epochDay }
        override suspend fun ownerState(owner: String) = owners[owner]
        override suspend fun firstAdopter() = owners.values.firstOrNull { it.legacyAdoptionComplete && it.legacyAdopterOwner != null }?.legacyAdopterOwner
        override suspend fun completeAdoption(owner: String, adopter: String) {
            owners[owner] = owners.getValue(owner).copy(legacyAdoptionComplete = true, legacyAdopterOwner = adopter)
        }
        override suspend fun legacyMoods() = emptyList<DailyMoodEntity>()
        override suspend fun legacyCompletions() = emptyList<DailyCompletionEntity>()
        override suspend fun legacyHealers() = emptyList<StreakHealerUseEntity>()
        override suspend fun write(fact: ActivityDailyFactEntity) {
            facts.removeAll { it.owner == fact.owner && it.epochDay == fact.epochDay }
            facts += fact
        }
    }

    val lifecycle = ActivityOwnerLifecycle(dao, dailyFacts, object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    })
}
