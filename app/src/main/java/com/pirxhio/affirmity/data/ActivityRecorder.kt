package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.repository.ActivitySessionBinding
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ActivityRecorder(private val binding: ActivitySessionBinding) {
    suspend fun view(occurredAtMillis: Long, zone: TimeZone) {
        val identity = binding.identity.await()
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = occurredAtMillis }
        binding.repository.record(ActivityEvent(identity.owner, UUID.randomUUID().toString(), DayClock.epochDay(calendar),
            ActivityKind.VIEW, occurredAtMillis, occurredAtMillis, occurredAtMillis, zone.id, null, null, 0, identity.sourceId))
    }

    fun startSession(kind: ActivityKind, meditationId: String?, name: String, startMillis: Long) =
        MeditationActivityCapture(binding, UUID.randomUUID().toString(), kind, meditationId, name, startMillis)
}

/** Allocated at Start. Only a Completed callback invokes complete; retries reuse the first payload. */
class MeditationActivityCapture internal constructor(
    private val binding: ActivitySessionBinding,
    val eventId: String,
    private val kind: ActivityKind,
    private val meditationId: String?,
    private val name: String,
    private val startMillis: Long,
) {
    private val mutex = Mutex()
    private var completed: ActivityEvent? = null

    suspend fun complete(activeSeconds: Long, endMillis: Long, zone: TimeZone): ActivitySaveState = mutex.withLock {
        val event = completed ?: binding.identity.await().let { identity ->
            // A wall clock stepped backward mid-session leaves end < start; keep the completion by
            // deriving the start from the active duration instead of tripping ActivityEvent's check.
            val start = if (endMillis >= startMillis) startMillis
                else (endMillis - activeSeconds * 1_000).coerceIn(0L, endMillis)
            ActivityEvent(identity.owner, eventId,
                DayClock.attributedEpochDay(start, endMillis, Calendar.getInstance(zone)), kind,
                endMillis, start, endMillis, zone.id, meditationId, name, activeSeconds, identity.sourceId)
        }.also { completed = it }
        binding.repository.record(event)
    }
}
