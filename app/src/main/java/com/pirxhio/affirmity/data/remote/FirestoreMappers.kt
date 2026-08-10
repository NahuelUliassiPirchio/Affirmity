package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
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

fun affirmationToMap(entity: AffirmationEntity): Map<String, Any> = mapOf(
    FIELD_ID to entity.id,
    FIELD_TITLE to entity.title,
    FIELD_SUBTITLE to entity.subtitle,
    FIELD_BACKGROUND_TYPE to entity.backgroundType,
    FIELD_BACKGROUND_VALUE to entity.backgroundValue,
)

fun affirmationFromMap(map: Map<String, Any?>): AffirmationEntity = AffirmationEntity(
    id = map[FIELD_ID] as String,
    title = map[FIELD_TITLE] as String,
    subtitle = map[FIELD_SUBTITLE] as String,
    backgroundType = map[FIELD_BACKGROUND_TYPE] as String,
    backgroundValue = map[FIELD_BACKGROUND_VALUE] as String,
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

private const val DEFAULT_START_MINUTE = 540 // 09:00 — mirrors NotificationPreferences' default.
private const val DEFAULT_END_MINUTE = 1260 // 21:00
private const val REFLECTION_DEFAULT_START_MINUTE = 1200 // 20:00 — mirrors NotificationPreferences' default.
private const val REFLECTION_DEFAULT_END_MINUTE = 1380 // 23:00

private fun enabledKey(channel: NotificationChannelSpec) = "${channel.prefsPrefix}_enabled"
private fun startMinuteKey(channel: NotificationChannelSpec) = "${channel.prefsPrefix}_startMinute"
private fun endMinuteKey(channel: NotificationChannelSpec) = "${channel.prefsPrefix}_endMinute"

private fun defaultStartMinute(channel: NotificationChannelSpec) =
    if (channel == NotificationChannelSpec.REFLECTION) REFLECTION_DEFAULT_START_MINUTE else DEFAULT_START_MINUTE

private fun defaultEndMinute(channel: NotificationChannelSpec) =
    if (channel == NotificationChannelSpec.REFLECTION) REFLECTION_DEFAULT_END_MINUTE else DEFAULT_END_MINUTE

/**
 * Produces a partial map keyed by [channel]'s prefix so both channels can share one
 * `settings/preferences` document without colliding (see design.md's single-shared-document note).
 */
fun channelSettingsToMap(channel: NotificationChannelSpec, settings: ChannelSettings): Map<String, Any> = mapOf(
    enabledKey(channel) to settings.enabled,
    startMinuteKey(channel) to settings.startMinute,
    endMinuteKey(channel) to settings.endMinute,
)

fun channelSettingsFromMap(channel: NotificationChannelSpec, map: Map<String, Any?>): ChannelSettings = ChannelSettings(
    enabled = map[enabledKey(channel)] as? Boolean ?: false,
    startMinute = (map[startMinuteKey(channel)] as? Number)?.toInt() ?: defaultStartMinute(channel),
    endMinute = (map[endMinuteKey(channel)] as? Number)?.toInt() ?: defaultEndMinute(channel),
)
