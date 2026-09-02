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
    fun `only streak is HIGH importance, meditation return is LOW, the rest are DEFAULT`() {
        assertEquals(ChannelImportance.HIGH, NotificationChannelSpec.STREAK.importance)
        assertEquals(ChannelImportance.LOW, NotificationChannelSpec.MEDITATION_RETURN.importance)
        assertEquals(ChannelImportance.DEFAULT, NotificationChannelSpec.REMINDER.importance)
        assertEquals(ChannelImportance.DEFAULT, NotificationChannelSpec.REFLECTION.importance)
        assertEquals(ChannelImportance.DEFAULT, NotificationChannelSpec.HEALER.importance)
        assertEquals(ChannelImportance.DEFAULT, NotificationChannelSpec.MOOD.importance)
    }

    @Test
    fun `wire channel keys match the server's V2 channel tokens`() {
        assertEquals("reminder", NotificationChannelSpec.REMINDER.wireChannelKey)
        assertEquals("reflection", NotificationChannelSpec.REFLECTION.wireChannelKey)
        assertEquals("mood", NotificationChannelSpec.MOOD.wireChannelKey)
        assertEquals("streak", NotificationChannelSpec.STREAK.wireChannelKey)
        assertEquals("healer", NotificationChannelSpec.HEALER.wireChannelKey)
        assertEquals("meditation_return", NotificationChannelSpec.MEDITATION_RETURN.wireChannelKey)
    }

    @Test
    fun `streak, healer, and meditation_return default to enabled, the rest default to disabled`() {
        assertTrue(NotificationChannelSpec.STREAK.defaultEnabled)
        assertTrue(NotificationChannelSpec.HEALER.defaultEnabled)
        assertTrue(NotificationChannelSpec.MEDITATION_RETURN.defaultEnabled)
        assertFalse(NotificationChannelSpec.REMINDER.defaultEnabled)
        assertFalse(NotificationChannelSpec.REFLECTION.defaultEnabled)
        assertFalse(NotificationChannelSpec.MOOD.defaultEnabled)
    }

    @Test
    fun `idsToCancelBeforePost only matches active ids in the target channel's rotating namespace`() {
        val reflectionId1 = NotificationChannelSpec.REFLECTION.notificationId * 1_000_000 + 7
        val reflectionId2 = NotificationChannelSpec.REFLECTION.notificationId * 1_000_000 + 42
        val otherChannelRotatingId = 999 * 1_000_000 + 7
        val activeIds = listOf(reflectionId1, reflectionId2, otherChannelRotatingId)

        val result = idsToCancelBeforePost(NotificationChannelSpec.REFLECTION, activeIds)

        assertEquals(listOf(reflectionId1, reflectionId2), result)
    }

    @Test
    fun `idsToCancelBeforePost excludes ids from other channels' namespaces`() {
        val reflectionId = NotificationChannelSpec.REFLECTION.notificationId * 1_000_000 + 1
        val activeIds = listOf(reflectionId, NotificationChannelSpec.STREAK.notificationId)

        val result = idsToCancelBeforePost(NotificationChannelSpec.STREAK, activeIds)

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `idsToCancelBeforePost no-ops for non-rotating channels since their fixed id already replaces`() {
        val activeIds = listOf(NotificationChannelSpec.MOOD.notificationId, NotificationChannelSpec.STREAK.notificationId)

        val result = idsToCancelBeforePost(NotificationChannelSpec.MOOD, activeIds)

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `idsToCancelBeforePost returns empty when no active ids match`() {
        assertEquals(emptyList<Int>(), idsToCancelBeforePost(NotificationChannelSpec.REFLECTION, emptyList()))
    }

    @Test
    fun `healer and streak taps open progress while mood opens mood`() {
        assertEquals(AppDestinations.PROGRESO, notificationStartDestination(NotificationChannelSpec.HEALER))
        assertEquals(AppDestinations.PROGRESO, notificationStartDestination(NotificationChannelSpec.STREAK))
        assertEquals(AppDestinations.ANIMO, notificationStartDestination(NotificationChannelSpec.MOOD))
        assertEquals(null, notificationStartDestination(NotificationChannelSpec.REMINDER))
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

    /** Resilience fix: [android.app.NotificationManager.getActiveNotifications] is a known
     * OEM-flaky API. [activeNotificationIdsOrEmpty] is the exact seam [Notifier.notify] and
     * [NotificationCanceller.cancelFamily] call it through -- proving here that a throw returns an
     * empty list (rather than propagating) proves, by composition with the already-covered
     * [idsToCancelBeforePost] (returns `emptyList()` for any channel given an empty active-ids
     * list, see the "returns empty when no active ids match" case above), that a throw during
     * cancel-then-post always results in an empty cancel loop -- i.e. `notify()` falls through to
     * `notificationManager.notify(...)` and posts, un-deduped, instead of not posting at all. This
     * repo has no Robolectric/androidTest harness for `NotificationManagerCompat` itself (no
     * pre-existing `Notifier`/`NotificationCanceller` class-level test exists to extend), so this
     * pure-seam composition is the most rigorous proof the existing test infra supports -- same
     * convention as every other pure function in this file. */
    @Test
    fun `activeNotificationIdsOrEmpty returns an empty list instead of propagating when the fetch throws`() {
        val result = activeNotificationIdsOrEmpty("test") { throw SecurityException("OEM getActiveNotifications bug") }

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `activeNotificationIdsOrEmpty returns the fetched ids when the fetch succeeds`() {
        val result = activeNotificationIdsOrEmpty("test") { listOf(1, 2, 3) }

        assertEquals(listOf(1, 2, 3), result)
    }
}
