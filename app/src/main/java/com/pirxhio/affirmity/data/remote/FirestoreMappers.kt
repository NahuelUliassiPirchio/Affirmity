package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

/**
 * Pure entity <-> `Map<String, Any>` mappers for the `users/{uid}` Firestore schema. Mirrors each
 * Room entity 1:1 — no cached streak field is ever produced (design.md's "Per-User Collection
 * Schema" invariant). No Firebase/Android dependency — unit-tested directly.
 */

private const val FIELD_ID = "id"
private const val FIELD_TITLE = "title"
private const val FIELD_SUBTITLE = "subtitle"
private const val FIELD_BACKGROUND_TYPE = "backgroundType"
private const val FIELD_BACKGROUND_VALUE = "backgroundValue"
private const val FIELD_GROUP_ID = "groupId"

fun affirmationToMap(entity: AffirmationEntity): Map<String, Any> = mapOf(
    FIELD_ID to entity.id,
    FIELD_TITLE to entity.title,
    FIELD_SUBTITLE to entity.subtitle,
    FIELD_BACKGROUND_TYPE to entity.backgroundType,
    FIELD_BACKGROUND_VALUE to entity.backgroundValue,
    FIELD_GROUP_ID to entity.groupId,
)

fun affirmationFromMap(map: Map<String, Any?>): AffirmationEntity = AffirmationEntity(
    id = map[FIELD_ID] as String,
    title = map[FIELD_TITLE] as String,
    subtitle = map[FIELD_SUBTITLE] as String,
    backgroundType = map[FIELD_BACKGROUND_TYPE] as String,
    backgroundValue = map[FIELD_BACKGROUND_VALUE] as String,
    groupId = map[FIELD_GROUP_ID] as? String ?: PERSONALIZADAS_GROUP_ID,
)

private const val FIELD_EPOCH_DAY = "epochDay"
private const val FIELD_MEDITATION_DONE = "meditationDone"
private const val FIELD_AFFIRMATION_DONE = "affirmationDone"

fun dailyCompletionToMap(entity: DailyCompletionEntity): Map<String, Any> = mapOf(
    FIELD_EPOCH_DAY to entity.epochDay,
    FIELD_MEDITATION_DONE to entity.meditationDone,
    FIELD_AFFIRMATION_DONE to entity.affirmationDone,
)

fun dailyCompletionFromMap(map: Map<String, Any?>): DailyCompletionEntity = DailyCompletionEntity(
    epochDay = (map[FIELD_EPOCH_DAY] as Number).toLong(),
    meditationDone = map[FIELD_MEDITATION_DONE] as? Boolean ?: false,
    affirmationDone = map[FIELD_AFFIRMATION_DONE] as? Boolean ?: false,
)

private const val FIELD_MOOD_VALUE = "moodValue"
private const val FIELD_NOTE = "note"

fun dailyMoodToMap(entity: DailyMoodEntity): Map<String, Any> = buildMap {
    put(FIELD_EPOCH_DAY, entity.epochDay)
    put(FIELD_MOOD_VALUE, entity.moodValue)
    entity.note?.let { put(FIELD_NOTE, it) }
}

fun dailyMoodFromMap(map: Map<String, Any?>): DailyMoodEntity = DailyMoodEntity(
    epochDay = (map[FIELD_EPOCH_DAY] as Number).toLong(),
    moodValue = (map[FIELD_MOOD_VALUE] as Number).toInt(),
    note = map[FIELD_NOTE] as? String,
)

private const val FIELD_HEALED_EPOCH_DAY = "healedEpochDay"
private const val FIELD_ACTIVATED_AT_MILLIS = "activatedAtMillis"

fun streakHealerUseToMap(entity: StreakHealerUseEntity): Map<String, Any> = mapOf(
    FIELD_HEALED_EPOCH_DAY to entity.healedEpochDay,
    FIELD_ACTIVATED_AT_MILLIS to entity.activatedAtMillis,
)

fun streakHealerUseFromMap(map: Map<String, Any?>): StreakHealerUseEntity = StreakHealerUseEntity(
    healedEpochDay = (map[FIELD_HEALED_EPOCH_DAY] as Number).toLong(),
    activatedAtMillis = (map[FIELD_ACTIVATED_AT_MILLIS] as Number).toLong(),
)

private val DEFAULT_REMINDER_SEGMENTS = setOf(DaySegment.MANANA, DaySegment.TARDE) // mirrors NotificationPreferences' default.
private val DEFAULT_NIGHT_SEGMENTS = setOf(DaySegment.NOCHE) // mirrors NotificationPreferences' default.

fun enabledKey(channel: NotificationChannelSpec) = "${channel.prefsPrefix}_enabled"
fun segmentsKey(channel: NotificationChannelSpec) = "${channel.prefsPrefix}_segments"

private fun defaultSegments(channel: NotificationChannelSpec) = when (channel) {
    NotificationChannelSpec.REFLECTION, NotificationChannelSpec.MOOD -> DEFAULT_NIGHT_SEGMENTS
    else -> DEFAULT_REMINDER_SEGMENTS
}

/**
 * Produces a partial map keyed by [channel]'s prefix so both channels can share one
 * `settings/preferences` document without colliding (see design.md's single-shared-document note).
 */
fun channelSettingsToMap(channel: NotificationChannelSpec, settings: ChannelSettings): Map<String, Any> = mapOf(
    enabledKey(channel) to settings.enabled,
    segmentsKey(channel) to settings.segments.map { it.key },
)

fun channelSettingsFromMap(channel: NotificationChannelSpec, map: Map<String, Any?>): ChannelSettings = ChannelSettings(
    enabled = map[enabledKey(channel)] as? Boolean ?: false,
    segments = (map[segmentsKey(channel)] as? List<*>)
        ?.mapNotNull { key -> DaySegment.entries.find { it.key == key } }
        ?.toSet()
        ?: defaultSegments(channel),
)

private const val QUIET_HOURS_ENABLED_FIELD = "quietHours_enabled"
private const val QUIET_HOURS_START_FIELD = "quietHours_startMinute"
private const val QUIET_HOURS_END_FIELD = "quietHours_endMinute"
private const val DEFAULT_QUIET_HOURS_START = 1380 // 23:00 — mirrors NotificationPreferences' default.
private const val DEFAULT_QUIET_HOURS_END = 420 // 07:00

/** Bare (non-channel-prefixed) fields on the same `settings/preferences` document. */
fun quietHoursSettingsToMap(settings: QuietHoursSettings): Map<String, Any> = mapOf(
    QUIET_HOURS_ENABLED_FIELD to settings.enabled,
    QUIET_HOURS_START_FIELD to settings.startMinute,
    QUIET_HOURS_END_FIELD to settings.endMinute,
)

fun quietHoursSettingsFromMap(map: Map<String, Any?>): QuietHoursSettings = QuietHoursSettings(
    enabled = map[QUIET_HOURS_ENABLED_FIELD] as? Boolean ?: false,
    startMinute = (map[QUIET_HOURS_START_FIELD] as? Number)?.toInt() ?: DEFAULT_QUIET_HOURS_START,
    endMinute = (map[QUIET_HOURS_END_FIELD] as? Number)?.toInt() ?: DEFAULT_QUIET_HOURS_END,
)
