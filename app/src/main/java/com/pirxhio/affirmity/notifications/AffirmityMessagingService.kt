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

    private val handler = FcmMessageHandler { channel ->
        when (channel) {
            NotificationChannelSpec.REMINDER ->
                getString(R.string.notification_reminder_title) to
                    getString(R.string.notification_reminder_fallback_body)

            NotificationChannelSpec.REFLECTION ->
                getString(R.string.notification_reflection_title) to
                    ReflectionPromptProvider.randomPrompt(applicationContext)

            NotificationChannelSpec.STREAK ->
                getString(R.string.notification_streak_title) to
                    getString(R.string.notification_streak_fallback_body)

            NotificationChannelSpec.HEALER ->
                getString(R.string.notification_healer_title) to
                    getString(R.string.notification_healer_fallback_body)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val action = handler.resolve(message.data)
        scope.launch {
            val poster = Notifier(applicationContext, NotificationDebugLog(applicationContext))
            action.applyTo(poster) {
                WeeklyTrackerWidget().updateAll(applicationContext)
            }
        }
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            FcmTokenRepository(FirebaseFirestore.getInstance()).registerToken(uid, token)
        }
    }
}
