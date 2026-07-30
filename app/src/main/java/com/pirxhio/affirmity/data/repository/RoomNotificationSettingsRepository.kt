package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.NotificationPreferences
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.flow.Flow

/**
 * Thin [NotificationSettingsRepository] wrapper delegating 1:1 to the untouched
 * [NotificationPreferences] DataStore class.
 */
class RoomNotificationSettingsRepository(
    private val notificationPreferences: NotificationPreferences,
) : NotificationSettingsRepository {
    override fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> =
        notificationPreferences.observe(channel)

    override suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) =
        notificationPreferences.setEnabled(channel, enabled)

    override suspend fun setWindow(channel: NotificationChannelSpec, startMinute: Int, endMinute: Int) =
        notificationPreferences.setWindow(channel, startMinute, endMinute)
}
