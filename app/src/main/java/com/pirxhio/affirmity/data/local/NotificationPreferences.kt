package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")

/** Per-channel enabled flag and the minutes-since-midnight window it may fire within. */
data class ChannelSettings(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
)

class NotificationPreferences(private val context: Context) {

    fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> =
        context.notificationDataStore.data.map {
            ChannelSettings(
                enabled = it[enabledKey(channel)] ?: false,
                startMinute = it[startMinuteKey(channel)] ?: DEFAULT_START_MINUTE,
                endMinute = it[endMinuteKey(channel)] ?: DEFAULT_END_MINUTE,
            )
        }

    fun isEnabled(channel: NotificationChannelSpec): Flow<Boolean> =
        observe(channel).map { it.enabled }

    suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) {
        context.notificationDataStore.edit { it[enabledKey(channel)] = enabled }
    }

    suspend fun setWindow(channel: NotificationChannelSpec, startMinute: Int, endMinute: Int) {
        require(endMinute >= startMinute) { "endMinute ($endMinute) must be >= startMinute ($startMinute)" }
        context.notificationDataStore.edit {
            it[startMinuteKey(channel)] = startMinute
            it[endMinuteKey(channel)] = endMinute
        }
    }

    private fun enabledKey(channel: NotificationChannelSpec) =
        booleanPreferencesKey("${channel.prefsPrefix}_enabled")

    private fun startMinuteKey(channel: NotificationChannelSpec) =
        intPreferencesKey("${channel.prefsPrefix}_start_minute")

    private fun endMinuteKey(channel: NotificationChannelSpec) =
        intPreferencesKey("${channel.prefsPrefix}_end_minute")

    private companion object {
        const val DEFAULT_START_MINUTE = 540 // 09:00
        const val DEFAULT_END_MINUTE = 1260 // 21:00
    }
}
