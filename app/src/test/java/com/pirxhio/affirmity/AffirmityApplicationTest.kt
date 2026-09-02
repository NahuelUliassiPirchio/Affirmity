package com.pirxhio.affirmity

import android.app.NotificationManager
import com.pirxhio.affirmity.notifications.ChannelImportance
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Testability note (Reliability review finding): [AffirmityApplication.createNotificationChannels]/
 * [AffirmityApplication.deleteRetiredNotificationChannels] call `NotificationManagerCompat.from(this)`
 * directly and read string resources via `getString(...)`, both of which need a real or
 * Robolectric-shadowed `Context`/`NotificationManager` to exercise end-to-end. This repo has
 * **no Robolectric dependency** and **no `app/src/androidTest` convention that touches
 * `NotificationManager`** (confirmed: no existing test anywhere references either), so adding
 * either would be new test infra, not a fix-batch-scoped change.
 *
 * What IS tested here, at the pure/data level the existing infra already supports (mirrors this
 * codebase's established convention of extracting Android-free pure functions out of
 * Android-coupled classes and unit-testing those, e.g. `NotificationPolicyTest`):
 * - (a)/(c): [com.pirxhio.affirmity.platformImportance] is the *exact* expression
 *   `createNotificationChannels` evaluates per channel -- proving it maps `MOOD`'s importance to
 *   `IMPORTANCE_DEFAULT` and `MEDITATION_RETURN`'s to `IMPORTANCE_LOW` pins the real production
 *   mapping, not just the [NotificationChannelSpec] data declaration.
 * - (b): [AffirmityApplication.RETIRED_CHANNEL_IDS] (the literal argument
 *   `deleteRetiredNotificationChannels` iterates and calls `deleteNotificationChannel` on) still
 *   contains `affirmity_mood_checkin`, AND no live [NotificationChannelSpec] entry uses that
 *   channel id -- i.e. even if a future change re-adds a channel with this id,
 *   `createNotificationChannels` would immediately recreate what `deleteRetiredNotificationChannels`
 *   just deleted, so this second assertion is the regression pin that actually matters.
 *
 * If real end-to-end coverage of the migration (verifying the real `NotificationManager` API calls
 * fire in the right order) is wanted later, it requires adding Robolectric -- flagged explicitly
 * rather than skipped silently.
 */
class AffirmityApplicationTest {

    @Test
    fun `affirmity_mood_checkin_v2 is created with DEFAULT importance`() {
        assertEquals("affirmity_mood_checkin_v2", NotificationChannelSpec.MOOD.channelId)
        assertEquals(ChannelImportance.DEFAULT, NotificationChannelSpec.MOOD.importance)
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            platformImportance(NotificationChannelSpec.MOOD.importance),
        )
    }

    @Test
    fun `affirmity_meditation_return is created with LOW importance`() {
        assertEquals("affirmity_meditation_return", NotificationChannelSpec.MEDITATION_RETURN.channelId)
        assertEquals(ChannelImportance.LOW, NotificationChannelSpec.MEDITATION_RETURN.importance)
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            platformImportance(NotificationChannelSpec.MEDITATION_RETURN.importance),
        )
    }

    @Test
    fun `the legacy affirmity_mood_checkin channel id is retired and never recreated`() {
        assertEquals(listOf("affirmity_mood_checkin"), AffirmityApplication.RETIRED_CHANNEL_IDS)
        assertFalse(
            NotificationChannelSpec.entries.any { it.channelId in AffirmityApplication.RETIRED_CHANNEL_IDS },
        )
    }
}
