package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityKind
import java.util.Locale

@Entity(tableName = "activity_event", primaryKeys = ["owner", "eventId"], indices = [Index(value = ["owner", "epochDay"])])
data class ActivityEventEntity(
    val owner: String,
    val eventId: String,
    val epochDay: Long,
    val kind: String,
    val occurredAtMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
    val zoneId: String,
    val meditationId: String?,
    val nameSnapshot: String?,
    val activeSeconds: Long,
    val sourceId: String,
    val uploadState: String,
)

@Entity(tableName = "activity_owner", primaryKeys = ["owner"])
data class ActivityOwnerEntity(
    val owner: String,
    val sourceId: String,
    val claimedUid: String?,
    val active: Boolean,
    @ColumnInfo(defaultValue = "0") val legacyAdoptionComplete: Boolean = false,
    val legacyAdopterOwner: String? = null,
)

@Entity(tableName = "activity_coverage", primaryKeys = ["owner", "sourceId", "recordingSchema", "fromEpochDay", "toEpochDay", "uncertain"])
data class ActivityCoverageEntity(
    val owner: String,
    val sourceId: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val recordingSchema: Int,
    val uncertain: Boolean,
    val uploadState: String,
)

@Entity(tableName = "activity_daily_fact", primaryKeys = ["owner", "epochDay"])
data class ActivityDailyFactEntity(
    val owner: String,
    val epochDay: Long,
    val moodValue: Int? = null,
    val note: String? = null,
    @ColumnInfo(defaultValue = "0") val completionPresent: Boolean = false,
    @ColumnInfo(defaultValue = "0") val meditationDone: Boolean = false,
    @ColumnInfo(defaultValue = "0") val affirmationDone: Boolean = false,
    val healedAtMillis: Long? = null,
)

/** Copies retained facts exactly; never manufactures viewed counts or completed sessions. */
fun adoptDailyFacts(
    owner: String,
    moods: List<DailyMoodEntity>,
    completions: List<DailyCompletionEntity>,
    healers: List<StreakHealerUseEntity>,
): List<ActivityDailyFactEntity> {
    val days = mutableMapOf<Long, ActivityDailyFactEntity>()
    fun day(epochDay: Long) = days[epochDay] ?: ActivityDailyFactEntity(owner, epochDay)
    moods.forEach { days[it.epochDay] = day(it.epochDay).copy(moodValue = it.moodValue, note = it.note) }
    completions.forEach { days[it.epochDay] = day(it.epochDay).copy(
        completionPresent = true, meditationDone = it.meditationDone, affirmationDone = it.affirmationDone,
    ) }
    healers.forEach { days[it.healedEpochDay] = day(it.healedEpochDay).copy(healedAtMillis = it.activatedAtMillis) }
    return days.values.sortedBy { it.epochDay }
}

sealed interface DailyFactChange {
    data class Mood(val value: Int, val note: String?) : DailyFactChange
    data object Meditation : DailyFactChange
    data object Affirmation : DailyFactChange
    data class Healer(val activatedAtMillis: Long) : DailyFactChange
}

fun applyDailyFactChange(fact: ActivityDailyFactEntity, change: DailyFactChange): ActivityDailyFactEntity = when (change) {
    is DailyFactChange.Mood -> {
        require(change.value in 1..5)
        fact.copy(moodValue = change.value, note = change.note)
    }
    DailyFactChange.Meditation -> fact.copy(completionPresent = true, meditationDone = true)
    DailyFactChange.Affirmation -> fact.copy(completionPresent = true, affirmationDone = true)
    is DailyFactChange.Healer -> {
        require(change.activatedAtMillis >= 0)
        fact.copy(healedAtMillis = fact.healedAtMillis ?: change.activatedAtMillis)
    }
}

fun ActivityEvent.stored(uploadState: String) = ActivityEventEntity(owner, eventId, epochDay,
    kind.name.lowercase(Locale.ROOT), occurredAtMillis, startMillis, endMillis, zoneId,
    meditationId, nameSnapshot, activeSeconds, sourceId, uploadState)

fun ActivityEventEntity.domain() = ActivityEvent(owner, eventId, epochDay,
    ActivityKind.valueOf(kind.uppercase(Locale.ROOT)), occurredAtMillis, startMillis, endMillis,
    zoneId, meditationId, nameSnapshot, activeSeconds, sourceId)
