package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.flow.Flow

/**
 * Store-agnostic contract for affirmation persistence. Implementations exist for Room
 * (signed-out users, `RoomAffirmationRepository`) and Firestore (signed-in users,
 * `FirestoreAffirmationRepository`) — see `data-sync` spec for the single-writer cutover rule.
 */
interface AffirmationRepository {
    fun observeAll(): Flow<List<AffirmationEntity>>
    suspend fun insert(entity: AffirmationEntity)
    suspend fun deleteById(id: String)
    suspend fun deleteAll()
}

/** Store-agnostic contract for the daily habit-completion tracker (streak source of truth). */
interface DailyCompletionRepository {
    fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>>
    suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity>
    suspend fun markMeditation(epochDay: Long)
    suspend fun markAffirmation(epochDay: Long)
}

/** Store-agnostic contract for the daily mood check-in (1-5 scale + optional note). */
interface DailyMoodRepository {
    fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>>
    suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity>
    suspend fun upsert(epochDay: Long, moodValue: Int, note: String?)
}

/**
 * Store-agnostic contract for the streak-healer activation event log — an append-only log keyed
 * by [StreakHealerUseEntity.healedEpochDay], never a mutable held/consumed flag (design.md's
 * "Persist an event log, not mutable healer state" decision).
 */
interface StreakHealerRepository {
    fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>>
    suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity>
    suspend fun recordUse(healedEpochDay: Long)
}

/** Store-agnostic contract for the meditation-duration preference. */
interface MeditationPreferencesRepository {
    fun observeMeditationDurationSeconds(): Flow<Int?>
    suspend fun saveMeditationDurationSeconds(seconds: Int)
}

/** Store-agnostic contract for both notification-channel preferences. */
interface NotificationSettingsRepository {
    fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings>
    suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean)
    suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>)

    /** Global "mute everything" window, independent of any channel. */
    fun observeQuietHours(): Flow<QuietHoursSettings>
    suspend fun setQuietHoursEnabled(enabled: Boolean)
    suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int)

    /**
     * Persists the device's IANA timezone id so the server planner can compute this user's
     * local-day trigger instants. No-op for the Room-backed (signed-out) implementation, since
     * signed-out users have no server-driven scheduling to feed.
     */
    suspend fun setTimeZone(zoneId: String)
}

/** Client-facing entitlement snapshot. [expiryTimeMillis] is `null` for a Free tier or a
 * non-expiring grant. */
data class Entitlement(
    val tier: AccessTier,
    val expiryTimeMillis: Long? = null,
    val productId: String? = null,
) {
    companion object {
        val Free = Entitlement(tier = AccessTier.FREE)
    }
}

/**
 * Store-agnostic, **read-only by design** contract for the user's Play Billing entitlement
 * (design.md D5) — no write methods exist so the client can never grant itself Pro; the doc is
 * server-write-only (`firestore.rules`: `write: if false`).
 */
interface EntitlementRepository {
    fun observe(): Flow<Entitlement>
}

/**
 * Store-agnostic contract for per-item ad-unlock grants. **Durable grants only** —
 * [com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE] is process-scoped by product definition
 * (design §0) and never reaches this interface.
 *
 * Unlike [EntitlementRepository] this IS client-writable: the client is what observes a rewarded-ad
 * completion, so there is no server signal to write from. Deliberate, recorded weakening (design
 * §8). Writes are **create-if-absent**: a granted trial can never be re-granted, back-dated, or
 * erased.
 */
interface AdUnlockRepository {
    fun observeDurableUnlocks(): Flow<List<AdUnlockRecord>>
    suspend fun getDurableUnlocks(): List<AdUnlockRecord>

    /** Idempotent. A second call for an existing [AdUnlockRecord.key] is a silent no-op, not an
     *  overwrite. */
    suspend fun grantDurableUnlock(record: AdUnlockRecord)
}
