package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DaySegment
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

    override suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) =
        notificationPreferences.setSegments(channel, segments)

    /**
     * No-op: signed-out users have no server-driven scheduling to feed, so there is nothing to
     * persist the device timezone into locally.
     */
    override suspend fun setTimeZone(zoneId: String) = Unit
}
