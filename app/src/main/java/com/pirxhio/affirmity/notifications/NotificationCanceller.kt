package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Cancel-on-completion (Notifications V2 spec's "Lifecycle — Replace-Not-Stack and
 * Cancel-on-Completion" requirement / design §6): once the action a notification was nudging the
 * user toward is actually completed in-app, its notification should disappear immediately rather
 * than linger until TTL/midnight.
 *
 * This is intentionally the mirror image of [Notifier]'s cancel-then-post: it cancels every active
 * notification currently sitting in [channel]'s delivery-ID namespace, via the same
 * [idsToCancelBeforePost] namespace math, without posting a replacement.
 *
 * Wiring status (task 4.7): all five named completion sites are now wired. Four in
 * [com.pirxhio.affirmity.data.AffirmityAppState] --
 * [com.pirxhio.affirmity.data.AffirmityAppState.recordMood] (MOOD, today only),
 * [com.pirxhio.affirmity.data.AffirmityAppState.recordMeditationCompleted] (MEDITATION_RETURN
 * unconditionally, plus STREAK once the compound "today's requirement complete" condition holds),
 * [com.pirxhio.affirmity.data.AffirmityAppState.recordAffirmationViewed] (the other half of that
 * same STREAK condition), and
 * [com.pirxhio.affirmity.data.AffirmityAppState.activateStreakHealer] (HEALER, only on an actual
 * activation). The fifth -- Compass ("reflection") answer -- is wired in
 * [com.pirxhio.affirmity.compass.CompassAnswerRepository.submitAnswer]: closed via a
 * scope-expansion decision (mid-Phase-5 apply) that added the missing pieces this site actually
 * needed -- a new `answerCompassQuestion` Cloud Function (the only legitimate writer of
 * `compassAnswers/{localDay}`, since `firestore.rules` still denies all client writes there by
 * design) and a minimal in-app Compass answer screen, neither of which existed before.
 */
class NotificationCanceller(private val context: Context) {

    /** Cancels every currently-visible notification for [channel]. */
    fun cancelFamily(channel: NotificationChannelSpec) {
        val notificationManager = NotificationManagerCompat.from(context)
        // Resilience fix (mirrors Notifier.notify's cancel-then-post): getActiveNotifications() is
        // OEM-flaky and must never stop the direct fixed-id cancel below from running.
        val activeIds = activeNotificationIdsOrEmpty(TAG) { notificationManager.activeNotifications.map { it.id } }
        idsToCancelBeforePost(channel, activeIds).forEach { idToCancel ->
            notificationManager.cancel(idToCancel)
        }
        // Non-rotating channels (everything except REFLECTION) always post under their own fixed
        // notificationId, so cancel that directly too — idsToCancelBeforePost only matches the
        // rotating-ID namespace and structurally can't see a fixed id (see its own kdoc).
        notificationManager.cancel(channel.notificationId)
    }

    private companion object {
        const val TAG = "NotificationCanceller"
    }
}
