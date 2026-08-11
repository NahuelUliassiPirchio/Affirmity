package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")

/** Per-channel enabled flag and the fixed day-part segments it may fire within. */
data class ChannelSettings(
    val enabled: Boolean,
    val segments: Set<DaySegment>,
)

/** Global "mute everything" window (minutes since local midnight), independent of any channel.
 * [endMinute] may be less than [startMinute] to express a range that wraps past midnight
 * (e.g. 23:00-07:00). */
data class QuietHoursSettings(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
)

class NotificationPreferences(private val context: Context) {

    fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> =
        context.notificationDataStore.data.map {
            ChannelSettings(
                enabled = it[enabledKey(channel)] ?: false,
                segments = (it[segmentsKey(channel)] ?: defaultSegmentKeys(channel))
                    .mapNotNull { key -> DaySegment.entries.find { segment -> segment.key == key } }
                    .toSet(),
            )
        }

    fun isEnabled(channel: NotificationChannelSpec): Flow<Boolean> =
        observe(channel).map { it.enabled }

    suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) {
        context.notificationDataStore.edit { it[enabledKey(channel)] = enabled }
    }

    suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) {
        context.notificationDataStore.edit {
            it[segmentsKey(channel)] = segments.map { segment -> segment.key }.toSet()
        }
    }

    fun observeQuietHours(): Flow<QuietHoursSettings> =
        context.notificationDataStore.data.map {
            QuietHoursSettings(
                enabled = it[QUIET_HOURS_ENABLED_KEY] ?: false,
                startMinute = it[QUIET_HOURS_START_KEY] ?: DEFAULT_QUIET_HOURS_START,
                endMinute = it[QUIET_HOURS_END_KEY] ?: DEFAULT_QUIET_HOURS_END,
            )
        }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[QUIET_HOURS_ENABLED_KEY] = enabled }
    }

    suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) {
        context.notificationDataStore.edit {
            it[QUIET_HOURS_START_KEY] = startMinute
            it[QUIET_HOURS_END_KEY] = endMinute
        }
    }

    private fun enabledKey(channel: NotificationChannelSpec) =
        booleanPreferencesKey("${channel.prefsPrefix}_enabled")

    private fun segmentsKey(channel: NotificationChannelSpec) =
        stringSetPreferencesKey("${channel.prefsPrefix}_segments")

    private fun defaultSegmentKeys(channel: NotificationChannelSpec): Set<String> {
        val defaults = when (channel) {
            NotificationChannelSpec.REFLECTION, NotificationChannelSpec.MOOD -> DEFAULT_NIGHT_SEGMENTS
            else -> DEFAULT_REMINDER_SEGMENTS
        }
        return defaults.map { it.key }.toSet()
    }

    private companion object {
        val DEFAULT_REMINDER_SEGMENTS = setOf(DaySegment.MANANA, DaySegment.TARDE)
        val DEFAULT_NIGHT_SEGMENTS = setOf(DaySegment.NOCHE)

        val QUIET_HOURS_ENABLED_KEY = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START_KEY = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_HOURS_END_KEY = intPreferencesKey("quiet_hours_end_minute")
        const val DEFAULT_QUIET_HOURS_START = 1380 // 23:00
        const val DEFAULT_QUIET_HOURS_END = 420 // 07:00
    }
}
