package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.ActivityCoverageInterval
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.ActivityEventEntity

private val eventFields = setOf("eventId", "kind", "epochDay", "occurredAtMillis", "startMillis", "endMillis",
    "zoneId", "meditationId", "nameSnapshot", "activeSeconds", "sourceId")
private val coverageFields = setOf("sourceId", "recordingSchema", "fromEpochDay", "toEpochDay", "uncertain")
private fun Map<String, Any?>.integer(key: String) = requireNotNull(this[key] as? Long) { "Invalid activity integer: $key" }
private fun Map<String, Any?>.text(key: String) = requireNotNull(this[key] as? String) { "Invalid activity text: $key" }

fun activityEventToMap(event: ActivityEventEntity): Map<String, Any?> = mapOf(
    "eventId" to event.eventId, "kind" to event.kind, "epochDay" to event.epochDay,
    "occurredAtMillis" to event.occurredAtMillis, "startMillis" to event.startMillis,
    "endMillis" to event.endMillis, "zoneId" to event.zoneId, "meditationId" to event.meditationId,
    "nameSnapshot" to event.nameSnapshot, "activeSeconds" to event.activeSeconds, "sourceId" to event.sourceId,
)

fun activityEventFromMap(owner: String, id: String, data: Map<String, Any?>): ActivityEventEntity {
    require(data.keys == eventFields && data["eventId"] == id) { "Invalid activity event identity/shape" }
    require(data["meditationId"] == null || data["meditationId"] is String)
    require(data["nameSnapshot"] == null || data["nameSnapshot"] is String)
    val event = ActivityEventEntity(owner, id, data.integer("epochDay"), data.text("kind"),
        data.integer("occurredAtMillis"), data.integer("startMillis"), data.integer("endMillis"),
        data.text("zoneId"), data["meditationId"] as? String, data["nameSnapshot"] as? String,
        data.integer("activeSeconds"), data.text("sourceId"), "UPLOADED")
    require(event.kind in setOf("view", "guided", "free") && event.activeSeconds >= 0)
    require(event.epochDay >= 0 && event.startMillis >= 0 && event.endMillis >= event.startMillis && event.occurredAtMillis >= 0)
    require(event.zoneId.isNotBlank() && event.zoneId.length <= 128 && event.sourceId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    require(if (event.kind == "view") event.activeSeconds == 0L && event.meditationId == null && event.nameSnapshot == null
        else !event.nameSnapshot.isNullOrBlank() && event.nameSnapshot.length <= 1024)
    return event
}

fun activityCoverageToMap(coverage: ActivityCoverageEntity): Map<String, Any?> = mapOf(
    "sourceId" to coverage.sourceId, "recordingSchema" to coverage.recordingSchema.toLong(),
    "fromEpochDay" to coverage.fromEpochDay, "toEpochDay" to coverage.toEpochDay, "uncertain" to coverage.uncertain,
)

fun activityCoverageFromMap(owner: String, id: String, data: Map<String, Any?>): ActivityCoverageEntity {
    require(data.keys == coverageFields) { "Invalid coverage shape" }
    val schema = data.integer("recordingSchema")
    require(schema in 1..Int.MAX_VALUE.toLong())
    val interval = ActivityCoverageInterval(owner, data.text("sourceId"), data.integer("fromEpochDay"),
        data.integer("toEpochDay"), schema.toInt(), requireNotNull(data["uncertain"] as? Boolean))
    require(coverageIdentity(interval) == id) { "Invalid coverage identity" }
    return ActivityCoverageEntity(owner, interval.sourceId, interval.fromEpochDay, interval.toEpochDay,
        interval.recordingSchema, interval.uncertain, "UPLOADED")
}
