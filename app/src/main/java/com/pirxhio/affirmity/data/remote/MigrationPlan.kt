package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

/** One idempotent document write: a full Firestore doc path and the fields to `set(..., merge)`. */
data class DocWrite(val path: String, val fields: Map<String, Any>)

/**
 * A one-time snapshot of a signed-in user's local state, taken once at the start of migration
 * (design.md's "The swap moment" step 4).
 */
data class MigrationSnapshot(
    val uid: String,
    val affirmations: List<AffirmationEntity>,
    val completions: List<DailyCompletionEntity>,
    val moods: List<DailyMoodEntity>,
    val healerUses: List<StreakHealerUseEntity> = emptyList(),
    val meditationDurationSeconds: Int?,
    val notificationSettings: Map<NotificationChannelSpec, ChannelSettings>,
    val quietHours: QuietHoursSettings,
    val migratedAt: Long,
)

/**
 * Pure `snapshot -> chunks of writes` planner. Deterministic doc IDs make retries idempotent
 * (spec's "Migration is idempotent on retry" scenario); the `meta/migrated` marker is always the
 * last op of the last chunk (design.md's "Batch size" decision). No Firebase/Android dependency —
 * unit-tested directly.
 */
object MigrationPlan {
    private const val MAX_OPS_PER_CHUNK = 450

    fun build(snapshot: MigrationSnapshot): List<List<DocWrite>> {
        val writes = mutableListOf<DocWrite>()
        snapshot.affirmations.forEach { entity ->
            writes += DocWrite(FirestorePaths.affirmationDoc(snapshot.uid, entity.id), affirmationToMap(entity))
        }
        snapshot.completions.forEach { entity ->
            writes += DocWrite(FirestorePaths.dailyCompletionDoc(snapshot.uid, entity.epochDay), dailyCompletionToMap(entity))
        }
        snapshot.moods.forEach { entity ->
            writes += DocWrite(FirestorePaths.dailyMoodDoc(snapshot.uid, entity.epochDay), dailyMoodToMap(entity))
        }
        snapshot.healerUses.forEach { entity ->
            writes += DocWrite(
                FirestorePaths.streakHealerUseDoc(snapshot.uid, entity.healedEpochDay),
                streakHealerUseToMap(entity),
            )
        }
        writes += DocWrite(FirestorePaths.settingsPreferencesDoc(snapshot.uid), preferencesMap(snapshot))

        val marker = DocWrite(FirestorePaths.migratedMarkerDoc(snapshot.uid), markerMap(snapshot.migratedAt))
        return chunkWithMarkerLast(writes, marker, MAX_OPS_PER_CHUNK)
    }

    private fun preferencesMap(snapshot: MigrationSnapshot): Map<String, Any> {
        val fields = mutableMapOf<String, Any>()
        snapshot.meditationDurationSeconds?.let { fields[FIELD_MEDITATION_DURATION_SECONDS] = it }
        snapshot.notificationSettings.forEach { (channel, settings) ->
            fields += channelSettingsToMap(channel, settings)
        }
        fields += quietHoursSettingsToMap(snapshot.quietHours)
        return fields
    }

    private fun markerMap(migratedAt: Long): Map<String, Any> = mapOf(
        "migratedAt" to migratedAt,
        "schemaVersion" to 1,
        "source" to "room",
    )

    /** Splits [writes] into chunks of at most [chunkSize], appending [marker] as the final op. */
    private fun chunkWithMarkerLast(
        writes: List<DocWrite>,
        marker: DocWrite,
        chunkSize: Int,
    ): List<List<DocWrite>> {
        val chunks = writes.chunked(chunkSize).toMutableList()
        val lastChunk = chunks.lastOrNull()
        if (lastChunk == null || lastChunk.size >= chunkSize) {
            chunks += listOf(marker)
        } else {
            chunks[chunks.lastIndex] = lastChunk + marker
        }
        return chunks
    }

    private const val FIELD_MEDITATION_DURATION_SECONDS = "meditationDurationSeconds"
}
