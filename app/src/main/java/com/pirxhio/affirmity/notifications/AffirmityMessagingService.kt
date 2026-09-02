package com.pirxhio.affirmity.notifications

import androidx.glance.appwidget.updateAll
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.widget.WeeklyTrackerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Client's sole notification receipt path (design.md's "Client testability" decision): resolves
 * every incoming data message via the pure [FcmMessageHandler] and applies the result — it never
 * computes or reschedules a trigger time on-device (spec's "Client Receives and Posts via
 * Notifier" requirement). Hand-written, no DI framework —
 * `FirebaseMessagingService` is system-instantiated with a no-arg constructor, so
 * dependencies are built lazily from `applicationContext`/Firebase singletons instead of injected.
 */
class AffirmityMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Notifications V2 (design §1/§7): the server always renders and sends title/body at send
    // time. These are the ONE honest static fallback per channel — used only when the payload
    // omits title/body (e.g. upstream catalog resolution failed) — never a per-variant pool.
    // The old per-day-of-week `ReflectionPromptProvider`/`reflection_prompts_*` variant pool was
    // removed entirely (task 5.3) now that the server is the sole source of Reflection copy.
    private val handler = FcmMessageHandler { channel ->
        when (channel) {
            NotificationChannelSpec.REMINDER ->
                getString(R.string.notification_reminder_title) to
                    getString(R.string.notification_reminder_fallback_body)

            NotificationChannelSpec.REFLECTION ->
                getString(R.string.notification_reflection_title) to
                    getString(R.string.notification_reflection_fallback_body)

            NotificationChannelSpec.MOOD ->
                getString(R.string.notification_mood_title) to
                    getString(R.string.notification_mood_fallback_body)

            NotificationChannelSpec.STREAK ->
                getString(R.string.notification_streak_title) to
                    getString(R.string.notification_streak_fallback_body)

            NotificationChannelSpec.HEALER ->
                getString(R.string.notification_healer_title) to
                    getString(R.string.notification_healer_fallback_body)

            NotificationChannelSpec.MEDITATION_RETURN ->
                getString(R.string.notification_meditation_return_title) to
                    getString(R.string.notification_meditation_return_fallback_body)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val action = handler.resolve(message.data)
        // Firebase invokes this callback on a background thread. Keep the suspendable debug-log /
        // Glance update work inside the callback lifetime so process death cannot drop a delivery.
        runBlocking {
            val poster = Notifier(applicationContext, NotificationDebugLog(applicationContext))
            action.applyTo(poster) {
                WeeklyTrackerWidget().updateAll(applicationContext)
            }
        }
    }

    override fun onNewToken(token: String) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val repository = FcmTokenRepository(FirebaseFirestore.getInstance())
            processFcmTokenOwnershipCoordinator.registerIfActive(
                uid = uid,
                token = token,
                activeUid = { auth.currentUser?.uid },
                register = repository::registerToken,
                delete = repository::deleteToken,
            )
        }
    }
}
