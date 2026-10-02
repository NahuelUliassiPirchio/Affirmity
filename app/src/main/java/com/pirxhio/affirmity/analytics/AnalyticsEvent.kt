package com.pirxhio.affirmity.analytics

import com.pirxhio.affirmity.access.AdUnlockPolicy

/** Wire name for each of the 19 events. `wireName` is the only place a `String` lives on the name
 *  side of the model (REQ-4.5, validated by [com.pirxhio.affirmity.analytics.AnalyticsEventName]). */
enum class AnalyticsEventName(val wireName: String) {
    MEDITATION_ENTRY_TAPPED("meditation_entry_tapped"),
    MEDITATION_STARTED("meditation_started"),
    MEDITATION_COMPLETED("meditation_completed"),
    MEDITATION_CANCELLED("meditation_cancelled"),
    FREE_TIMER_COMPLETED("free_timer_completed"),
    AD_UNLOCK_REQUESTED("ad_unlock_requested"),
    AD_UNLOCK_EARNED("ad_unlock_earned"),
    AD_UNLOCK_DISMISSED("ad_unlock_dismissed"),
    AD_UNLOCK_FAILED("ad_unlock_failed"),
    AD_UNLOCK_UNAVAILABLE("ad_unlock_unavailable"),
    AD_UNLOCK_TAP_IGNORED("ad_unlock_tap_ignored"),
    PAYWALL_SHOWN("paywall_shown"),
    PAYWALL_PLAN_SELECTED("paywall_plan_selected"),
    PAYWALL_DISMISSED("paywall_dismissed"),
    CONTENT_LOCKED_TAPPED("content_locked_tapped"),
    CUSTOM_AFFIRMATION_CREATE_BLOCKED("custom_affirmation_create_blocked"),
    CUSTOM_AFFIRMATION_CREATED("custom_affirmation_created"),
    CUSTOM_AFFIRMATION_DELETED("custom_affirmation_deleted"),
    DAILY_GOAL_REACHED("daily_goal_reached"),
    // Notifications V2 (design §9): client-side half of the "Client and Server Analytics Split"
    // requirement -- notification_planned/notification_suppressed/notification_delivered are
    // server-only (Cloud Logging, functions/src/index.ts), never emitted from this client enum.
    NOTIFICATION_OPENED("notification_opened"),
    NOTIFICATION_ACTION_CLICKED("notification_action_clicked"),
    NOTIFICATION_COMPLETED("notification_completed"),
    // Round-end interstitial: a "round" is the user having seen every affirmation in their feed.
    ROUND_COMPLETED("round_completed"),
    ROUND_INTERSTITIAL_SHOWN("round_interstitial_shown"),
    ROUND_INTERSTITIAL_FAILED("round_interstitial_failed"),
    ROUND_INTERSTITIAL_SKIPPED("round_interstitial_skipped"),
}

/** Wire name for every declared parameter across the 19 events (REQ-4.5). */
enum class AnalyticsParam(val wireName: String) {
    ENTRY_ID("entry_id"),
    ACCESS_DECISION("access_decision"),
    AD_POLICY("ad_policy"),
    ELAPSED_SECONDS("elapsed_seconds"),
    DURATION_SECONDS("duration_seconds"),
    CONTENT_KEY("content_key"),
    FAILURE_REASON("failure_reason"),
    SOURCE("source"),
    PLAN("plan"),
    CONTENT_TYPE("content_type"),
    CREATION_METHOD("creation_method"),
    GOAL("goal"),
    // Notifications V2 (design §9): carried by all three client-side notification_* events.
    NOTIFICATION_FAMILY("notification_family"),
    VARIANT_KEY("variant_key"),
    DESTINATION("destination"),
    LOCALE("locale"),
    // Activity a streak notification was about (`meditation`/`affirmations`); only on activity-specific alerts.
    ACTIVITY("activity"),
    // Declared per design §9's exact AnalyticsParam list. Not yet populated on any event this phase
    // -- no client-side streak/inactivity banding function exists (that logic lives server-side,
    // functions/src/streak.ts's streakBand / the planned meditationReturnBand), and the spec's
    // "Client and Server Analytics Split" requirement only mandates notification_family/variant_key/
    // locale/destination on the six events. Reserved for a future band-aware client event.
    STREAK_COUNT_BAND("streak_count_band"),
    INACTIVE_DAYS_BAND("inactive_days_band"),
    FEED_SIZE_BUCKET("feed_size_bucket"),
    SKIP_REASON("skip_reason"),
}

/** Bounded feed size at round completion (rounds only count from 10 affirmations up). */
enum class FeedSizeBucket { SIZE_10_24, SIZE_25_49, SIZE_50_99, SIZE_100_PLUS }

/** Why a completed round did not produce an interstitial. */
enum class RoundSkipReason { PREMIUM, NO_CONSENT, NOT_LOADED }

/** Bounded mapping of the wire `family` token (design §7's `V2FcmData.family`) carried by every
 *  notification-related client analytics event (design §9). `UNKNOWN` is the safe fallback for a
 *  missing/legacy/malformed value -- never a raw `String` crosses the [AnalyticsEvent] boundary.
 *
 *  The server's `family` field is actually the internal wire *channel* token
 *  (functions/src/copyCatalog.ts's `NotificationFamily`: 'reminder'/'reflection'/'mood'/'streak'/
 *  'healer'/'meditation_return' -- see index.ts's `const family: NotificationFamily = channel`),
 *  not the analytics-facing display name. `reminder` and `reflection` diverge from their display
 *  name (`AFFIRMATION`/`COMPASS`), so this MUST be an explicit map, not a by-name match -- a prior
 *  ignoreCase-name-match version silently mapped both to UNKNOWN. */
enum class NotificationFamilyValue {
    AFFIRMATION, MOOD, COMPASS, STREAK, HEALER, MEDITATION_RETURN, UNKNOWN;

    companion object {
        fun fromWire(raw: String?): NotificationFamilyValue = when (raw) {
            "reminder" -> AFFIRMATION
            "mood" -> MOOD
            "reflection" -> COMPASS
            "streak" -> STREAK
            "healer" -> HEALER
            "meditation_return" -> MEDITATION_RETURN
            else -> UNKNOWN
        }
    }
}

/** Bounded mapping of the wire `destination` token (design §7). `UNKNOWN` covers a missing/legacy
 *  value the same way [NotificationFamilyValue.UNKNOWN] does. */
enum class NotificationDestinationValue {
    AFFIRMATIONS_FEED, MOOD_CHECKIN, COMPASS_QUESTION, STREAK_ACTION, HEALER_FLOW, SHORT_MEDITATION, UNKNOWN;

    companion object {
        fun fromWire(raw: String?): NotificationDestinationValue =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Bounded mapping of the wire `locale` token (design §7, `'es'|'en'`). `UNKNOWN` covers a
 *  missing/unsupported value -- the render-time locale fallback (design §9's copy-catalog capability)
 *  already defaults unsupported locales to `en`, but this analytics-side mapping stays honest about
 *  a value that didn't come through at all rather than silently reporting `EN`. */
enum class NotificationLocaleValue {
    ES, EN, UNKNOWN;

    companion object {
        fun fromWire(raw: String?): NotificationLocaleValue =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Bounded mapping of the wire `activity` token of an activity-specific streak alert. Unknown values
 *  map to null (the param is then omitted) instead of leaking a raw string into analytics. */
enum class NotificationActivityValue {
    MEDITATION, AFFIRMATIONS;

    companion object {
        fun fromWire(raw: String?): NotificationActivityValue? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** [com.pirxhio.affirmity.access.AccessDecision] provenance, mapped for the wire (D4). */
enum class AccessDecisionValue(val wireName: String) {
    UNLOCKED("unlocked"),
    UNLOCKED_BY_AD("unlocked_by_ad"),
    LOCKED_NEEDS_PRO("locked_needs_pro"),
    LOCKED_AD_UNLOCKABLE("locked_ad_unlockable"),
}

/** [com.pirxhio.affirmity.access.AdUnlockSource]'s `AdUnlockOutcome.Failed.reason` mapped to a
 *  bounded enum (D2.3) — the raw SDK string never crosses the [AnalyticsEvent] boundary. */
enum class AdFailureReason { NO_FILL, NETWORK, TIMEOUT, SHOW_FAILED, CONFIG, UNKNOWN }

/** Closed set of paywall trigger surfaces (D8). */
enum class PaywallSource { MEDITATION_CATALOG, GROUP_SELECTOR, MY_AFFIRMATIONS, SETTINGS, OTHER }

enum class PaywallPlan { MONTHLY, ANNUAL }

enum class CreationMethod { COLOR, IMAGE_URL, GALLERY }

enum class DailyGoal { MEDITATION, AFFIRMATION }

enum class AnalyticsContentType { MEDITATION, AFFIRMATION_GROUP }

/**
 * The closed, 19-member taxonomy (spec §5, PART 1). No subtype declares an unbounded `String`
 * property — [AnalyticsId], [Int], [Long], [Boolean] and enum types are the entire allowed set
 * (REQ-4.3, D2), enforced structurally by the no-PII-representable test.
 */
sealed interface AnalyticsEvent {
    val name: AnalyticsEventName

    data class MeditationEntryTapped(
        val entryId: AnalyticsId,
        val access: AccessDecisionValue,
        val adPolicy: AdUnlockPolicy,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.MEDITATION_ENTRY_TAPPED
    }

    data class MeditationStarted(
        val entryId: AnalyticsId,
        val access: AccessDecisionValue,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.MEDITATION_STARTED
    }

    data class MeditationCompleted(
        val entryId: AnalyticsId,
        val access: AccessDecisionValue,
        val elapsedSeconds: Long,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.MEDITATION_COMPLETED
    }

    data class MeditationCancelled(
        val entryId: AnalyticsId,
        val elapsedSeconds: Long,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.MEDITATION_CANCELLED
    }

    data class FreeTimerCompleted(
        val durationSeconds: Long,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.FREE_TIMER_COMPLETED
    }

    data class AdUnlockRequested(
        val contentKey: AnalyticsId,
        val adPolicy: AdUnlockPolicy,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.AD_UNLOCK_REQUESTED
    }

    data class AdUnlockEarned(
        val contentKey: AnalyticsId,
        val adPolicy: AdUnlockPolicy,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.AD_UNLOCK_EARNED
    }

    data class AdUnlockDismissed(
        val contentKey: AnalyticsId,
        val adPolicy: AdUnlockPolicy,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.AD_UNLOCK_DISMISSED
    }

    data class AdUnlockFailed(
        val contentKey: AnalyticsId,
        val adPolicy: AdUnlockPolicy,
        val failureReason: AdFailureReason,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.AD_UNLOCK_FAILED
    }

    data class AdUnlockUnavailable(
        val contentKey: AnalyticsId,
        val adPolicy: AdUnlockPolicy,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.AD_UNLOCK_UNAVAILABLE
    }

    data class AdUnlockTapIgnored(
        val contentKey: AnalyticsId,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.AD_UNLOCK_TAP_IGNORED
    }

    data class PaywallShown(
        val source: PaywallSource,
        val access: AccessDecisionValue?,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.PAYWALL_SHOWN
    }

    data class PaywallPlanSelected(
        val plan: PaywallPlan,
        val source: PaywallSource,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.PAYWALL_PLAN_SELECTED
    }

    data class PaywallDismissed(
        val source: PaywallSource,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.PAYWALL_DISMISSED
    }

    data class ContentLockedTapped(
        val contentKey: AnalyticsId,
        val contentType: AnalyticsContentType,
        val access: AccessDecisionValue,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.CONTENT_LOCKED_TAPPED
    }

    data class CustomAffirmationCreateBlocked(
        val access: AccessDecisionValue,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.CUSTOM_AFFIRMATION_CREATE_BLOCKED
    }

    data class CustomAffirmationCreated(
        val method: CreationMethod,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.CUSTOM_AFFIRMATION_CREATED
    }

    data object CustomAffirmationDeleted : AnalyticsEvent {
        override val name = AnalyticsEventName.CUSTOM_AFFIRMATION_DELETED
    }

    data class DailyGoalReached(
        val goal: DailyGoal,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.DAILY_GOAL_REACHED
    }

    /** Fired from [com.pirxhio.affirmity.MainActivity]'s `onCreate`/`onNewIntent` when a launch
     *  carries the notification extras Notifications V2's [com.pirxhio.affirmity.notifications.Notifier]
     *  now attaches to every posted notification's `PendingIntent` (design §9). */
    data class NotificationOpened(
        val family: NotificationFamilyValue,
        val variantKey: AnalyticsId?,
        val destination: NotificationDestinationValue,
        val locale: NotificationLocaleValue,
        /** Set only for an activity-specific streak alert; null for every other notification. */
        val activity: NotificationActivityValue? = null,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.NOTIFICATION_OPENED
    }

    /** Same call site as [NotificationOpened] (design §9), gated on a CTA action extra that no
     *  `NotificationCompat.Action` button currently sets (see design's File Changes table / the
     *  Phase 5b follow-up flag on CTA wiring) -- the emission path exists and is tested, but has no
     *  live producer until that follow-up lands. */
    data class NotificationActionClicked(
        val family: NotificationFamilyValue,
        val variantKey: AnalyticsId?,
        val destination: NotificationDestinationValue,
        val locale: NotificationLocaleValue,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.NOTIFICATION_ACTION_CLICKED
    }

    /** Fired at the family's completion site (design §9), attribution scoped to the launching
     *  intent's notification extras still held in-memory -- no new persistence store. See
     *  `AffirmityAppState.setActiveNotificationAttribution`/`completeNotificationAttribution` and
     *  `CompassAnswerHost`'s `onAnswered` (the one completion site outside `AffirmityAppState`). */
    data class NotificationCompleted(
        val family: NotificationFamilyValue,
        val variantKey: AnalyticsId?,
        val destination: NotificationDestinationValue,
        val locale: NotificationLocaleValue,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.NOTIFICATION_COMPLETED
    }

    data class RoundCompleted(
        val feedSize: FeedSizeBucket,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.ROUND_COMPLETED
    }

    data object RoundInterstitialShown : AnalyticsEvent {
        override val name = AnalyticsEventName.ROUND_INTERSTITIAL_SHOWN
    }

    data class RoundInterstitialFailed(
        val failureReason: AdFailureReason,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.ROUND_INTERSTITIAL_FAILED
    }

    data class RoundInterstitialSkipped(
        val reason: RoundSkipReason,
    ) : AnalyticsEvent {
        override val name = AnalyticsEventName.ROUND_INTERSTITIAL_SKIPPED
    }
}
