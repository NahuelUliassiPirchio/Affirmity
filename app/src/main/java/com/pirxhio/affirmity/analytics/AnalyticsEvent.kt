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
}
