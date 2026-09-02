package com.pirxhio.affirmity.notifications

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R
import java.util.concurrent.atomic.AtomicInteger

/** Android `NotificationChannel` importance tier a [NotificationChannelSpec] maps to. Three tiers,
 * not a boolean, because Healer needs to sit at DEFAULT normally and escalate conditionally
 * (see `AffirmityApplication`/`Notifier`), which a single time-sensitive flag can't express. */
enum class ChannelImportance {
    LOW,
    DEFAULT,
    HIGH,
}

/**
 * One notification channel: its Android [android.app.NotificationChannel] identity and the
 * DataStore/Firestore key prefix used by
 * [com.pirxhio.affirmity.data.local.NotificationPreferences] /
 * [com.pirxhio.affirmity.data.remote.FirestoreNotificationSettingsRepository]. Trigger computation
 * is server-driven (see `push-notifications` spec) — this type carries no WorkManager identity.
 */
enum class NotificationChannelSpec(
    val channelId: String,
    val notificationId: Int,
    val prefsPrefix: String,
    val importance: ChannelImportance = ChannelImportance.DEFAULT,
    @StringRes val channelNameRes: Int,
    @StringRes val channelDescriptionRes: Int,
    /** Wire-format `channel` token sent by the server (Notifications V2 design §7). Defaults to
     * [prefsPrefix] since they already coincide for every channel except [MEDITATION_RETURN]
     * (server token `meditation_return`, prefs key `meditationReturn`). */
    val wireChannelKey: String = prefsPrefix,
    /** Value assumed when `${prefsPrefix}_enabled` has never been written (DataStore or
     * Firestore). REMINDER/REFLECTION/MOOD are opt-in (`false`) -- unchanged pre-V2 behavior.
     * STREAK/HEALER/MEDITATION_RETURN are opt-out (`true`, design §10/D8): these three toggles
     * are new, existing users have never written the field, and a `false` default would silently
     * read as "disabled" both in the Settings UI and (mirrored server-side via the equivalent
     * `!== false` read in `functions/src/index.ts`) in actual delivery. */
    val defaultEnabled: Boolean = false,
) {
    REMINDER(
        channelId = "affirmity_reminders",
        notificationId = 1001,
        prefsPrefix = "reminder",
        channelNameRes = R.string.notification_channel_reminder_name,
        channelDescriptionRes = R.string.notification_channel_reminder_description,
    ),
    REFLECTION(
        channelId = "affirmity_reflection_prompts",
        notificationId = 1002,
        prefsPrefix = "reflection",
        channelNameRes = R.string.notification_channel_reflection_name,
        channelDescriptionRes = R.string.notification_channel_reflection_description,
    ),
    MOOD(
        channelId = "affirmity_mood_checkin_v2",
        notificationId = 1005,
        prefsPrefix = "mood",
        importance = ChannelImportance.DEFAULT,
        channelNameRes = R.string.notification_channel_mood_name,
        channelDescriptionRes = R.string.notification_channel_mood_description,
    ),
    STREAK(
        channelId = "affirmity_streak_alerts",
        notificationId = 1003,
        prefsPrefix = "streak",
        importance = ChannelImportance.HIGH,
        channelNameRes = R.string.notification_channel_streak_name,
        channelDescriptionRes = R.string.notification_channel_streak_description,
        defaultEnabled = true,
    ),
    HEALER(
        channelId = "affirmity_healer_alerts",
        notificationId = 1004,
        prefsPrefix = "healer",
        channelNameRes = R.string.notification_channel_healer_name,
        channelDescriptionRes = R.string.notification_channel_healer_description,
        defaultEnabled = true,
    ),
    MEDITATION_RETURN(
        channelId = "affirmity_meditation_return",
        notificationId = 1006,
        prefsPrefix = "meditationReturn",
        importance = ChannelImportance.LOW,
        channelNameRes = R.string.notification_channel_meditation_return_name,
        channelDescriptionRes = R.string.notification_channel_meditation_return_description,
        wireChannelKey = "meditation_return",
        defaultEnabled = true,
    ),
}

/** Reflection prompts can legitimately arrive more than once per day; the remaining channels are
 * single-instance nudges and keep replacing their own previous delivery. */
fun NotificationChannelSpec.notificationIdForDelivery(@Suppress("UNUSED_PARAMETER") deliveryTimeMillis: Long): Int =
    if (this == NotificationChannelSpec.REFLECTION) {
        notificationId * DELIVERY_ID_NAMESPACE_SIZE +
            reflectionDeliverySequence.getAndUpdate { current ->
                if (current == DELIVERY_ID_NAMESPACE_SIZE - 1) 0 else current + 1
            }
    } else {
        notificationId
    }

/**
 * Active notification IDs (from [android.app.NotificationManager.getActiveNotifications]) that
 * must be cancelled before posting a new notification for [channel] — the cancel-then-post
 * mechanism that keeps at most one visible notification per channel while leaving the rotating
 * delivery-ID pool (collision safety, #1224/#1237) completely untouched. Pure and JVM-testable.
 *
 * Non-rotating channels always post under their own fixed [NotificationChannelSpec.notificationId]
 * (`id / DELIVERY_ID_NAMESPACE_SIZE == 0`), which never equals a real channel id, so this
 * naturally no-ops for them — they already replace via that shared fixed ID.
 */
internal fun idsToCancelBeforePost(channel: NotificationChannelSpec, activeIds: List<Int>): List<Int> =
    activeIds.filter { it / DELIVERY_ID_NAMESPACE_SIZE == channel.notificationId }

internal const val DELIVERY_ID_NAMESPACE_SIZE = 1_000_000
private val reflectionDeliverySequence = AtomicInteger(
    Math.floorMod(System.nanoTime().hashCode(), DELIVERY_ID_NAMESPACE_SIZE),
)
