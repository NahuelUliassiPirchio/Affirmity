package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.ActivityDailyFactDao
import com.pirxhio.affirmity.data.local.ActivityDailyFactEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import kotlinx.coroutines.flow.map

private fun List<ActivityDailyFactEntity>.moods() = mapNotNull { fact ->
    fact.moodValue?.let { DailyMoodEntity(fact.epochDay, it, fact.note) }
}
private fun List<ActivityDailyFactEntity>.completions() = filter { it.completionPresent }.map {
    DailyCompletionEntity(it.epochDay, it.meditationDone, it.affirmationDone)
}
private fun List<ActivityDailyFactEntity>.healers() = mapNotNull { fact ->
    fact.healedAtMillis?.let { StreakHealerUseEntity(fact.epochDay, it) }
}

/** Each adapter binds one generation permanently; a later suspension never resolves a new owner. */
class ScopedRoomDailyMoodRepository(private val dao: ActivityDailyFactDao, private val owner: String) : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long) = dao.observeRange(owner, from, to).map { it.moods() }
    override suspend fun getRange(from: Long, to: Long) = dao.getRange(owner, from, to).moods()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = dao.upsertMood(owner, epochDay, moodValue, note)
}

class ScopedRoomDailyCompletionRepository(private val dao: ActivityDailyFactDao, private val owner: String) : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long) = dao.observeRange(owner, from, to).map { it.completions() }
    override suspend fun getRange(from: Long, to: Long) = dao.getRange(owner, from, to).completions()
    override suspend fun markMeditation(epochDay: Long) = dao.markMeditation(owner, epochDay)
    override suspend fun markAffirmation(epochDay: Long) = dao.markAffirmation(owner, epochDay)
    override suspend fun earliestEpochDay() = dao.getAll(owner).filter { it.completionPresent }.minOfOrNull { it.epochDay }
}

class ScopedRoomStreakHealerRepository(
    private val dao: ActivityDailyFactDao,
    private val owner: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long) = dao.observeRange(owner, from, to).map { it.healers() }
    override suspend fun getRange(from: Long, to: Long) = dao.getRange(owner, from, to).healers()
    override suspend fun recordUse(healedEpochDay: Long) = dao.recordHealer(owner, healedEpochDay, nowMillis())
}
