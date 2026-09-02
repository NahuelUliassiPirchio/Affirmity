package com.pirxhio.affirmity.notifications

import android.app.NotificationManager
import com.pirxhio.affirmity.AppDestinations
import com.pirxhio.affirmity.data.local.NotificationLogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {


    @Test
    fun `only same-moment mood and streak channels are time sensitive`() {
        assertTrue(NotificationChannelSpec.MOOD.isTimeSensitive)
        assertTrue(NotificationChannelSpec.STREAK.isTimeSensitive)
        assertFalse(NotificationChannelSpec.REMINDER.isTimeSensitive)
        assertFalse(NotificationChannelSpec.REFLECTION.isTimeSensitive)
        assertFalse(NotificationChannelSpec.HEALER.isTimeSensitive)
    }

    @Test
    fun `healer and streak taps open progress while mood opens mood`() {
        assertEquals(AppDestinations.PROGRESO, notificationStartDestination(NotificationChannelSpec.HEALER))
        assertEquals(AppDestinations.PROGRESO, notificationStartDestination(NotificationChannelSpec.STREAK))
        assertEquals(AppDestinations.ANIMO, notificationStartDestination(NotificationChannelSpec.MOOD))
        assertEquals(null, notificationStartDestination(NotificationChannelSpec.REMINDER))
    }

    @Test
    fun `mood quick actions expose the central three values`() {
        assertEquals(listOf(2, 3, 4), MOOD_QUICK_ACTION_VALUES)
    }

    @Test
    fun `mood notification body opens the full picker while other bodies do not`() {
        assertTrue(notificationOpensMoodPicker(NotificationChannelSpec.MOOD))
        assertFalse(notificationOpensMoodPicker(NotificationChannelSpec.REFLECTION))
    }

    @Test
    fun `reflection deliveries get distinct notification ids while other channels stay fixed`() {
        val first = NotificationChannelSpec.REFLECTION.notificationIdForDelivery(1_000L)
        val second = NotificationChannelSpec.REFLECTION.notificationIdForDelivery(2_000L)

        assertNotEquals(first, second)
        assertNotEquals(NotificationChannelSpec.REMINDER.notificationId, first)
        assertEquals(
            NotificationChannelSpec.MOOD.notificationId,
            NotificationChannelSpec.MOOD.notificationIdForDelivery(2_000L),
        )
    }

    @Test
    fun `multiple reflection deliveries created at the same instant still get distinct ids`() {
        val first = NotificationChannelSpec.REFLECTION.notificationIdForDelivery(42_000L)
        val second = NotificationChannelSpec.REFLECTION.notificationIdForDelivery(42_000L)

        assertNotEquals(first, second)
    }

    @Test
    fun `channel blocked requires API 26 and importance none`() {
        assertFalse(isNotificationChannelBlocked(25, NotificationManager.IMPORTANCE_NONE))
        assertTrue(isNotificationChannelBlocked(26, NotificationManager.IMPORTANCE_NONE))
        assertFalse(isNotificationChannelBlocked(26, NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(isNotificationChannelBlocked(26, null))
    }

    @Test
    fun `channel blocking and global permission produce distinct debug events`() {
        assertEquals(
            NotificationLogEvent.NOTIFY_SKIPPED_CHANNEL_BLOCKED,
            notificationSkipEvent(true, 26, NotificationManager.IMPORTANCE_NONE),
        )
        assertEquals(
            NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION,
            notificationSkipEvent(false, 26, NotificationManager.IMPORTANCE_NONE),
        )
        assertEquals(null, notificationSkipEvent(true, 26, NotificationManager.IMPORTANCE_DEFAULT))
    }
}
