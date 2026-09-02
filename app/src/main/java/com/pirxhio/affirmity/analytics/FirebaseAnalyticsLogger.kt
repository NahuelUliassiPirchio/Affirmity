package com.pirxhio.affirmity.analytics

/**
 * Pure mapper: [AnalyticsEvent] -> `(wireName, params)`, then delegates to [sink] (design D6).
 * The exhaustive `when` over all 19 subtypes makes a missing 20th-event mapping a compile error,
 * mirroring `requestAdUnlock`'s own exhaustive `when (outcome)` (design §1/D3). Never imports
 * `android.os.Bundle` or any Firebase SDK class -- [sink] is the only SDK boundary.
 */
class FirebaseAnalyticsLogger(private val sink: FirebaseAnalyticsSink) : AnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        sink.logEvent(event.name.wireName, paramsFor(event))
    }

    private fun paramsFor(event: AnalyticsEvent): List<AnalyticsParamValue> = when (event) {
        is AnalyticsEvent.MeditationEntryTapped -> listOf(
            id(AnalyticsParam.ENTRY_ID, event.entryId),
            text(AnalyticsParam.ACCESS_DECISION, event.access.wireName),
            text(AnalyticsParam.AD_POLICY, event.adPolicy.name),
        )
        is AnalyticsEvent.MeditationStarted -> listOf(
            id(AnalyticsParam.ENTRY_ID, event.entryId),
            text(AnalyticsParam.ACCESS_DECISION, event.access.wireName),
        )
        is AnalyticsEvent.MeditationCompleted -> listOf(
            id(AnalyticsParam.ENTRY_ID, event.entryId),
            text(AnalyticsParam.ACCESS_DECISION, event.access.wireName),
            number(AnalyticsParam.ELAPSED_SECONDS, event.elapsedSeconds),
        )
        is AnalyticsEvent.MeditationCancelled -> listOf(
            id(AnalyticsParam.ENTRY_ID, event.entryId),
            number(AnalyticsParam.ELAPSED_SECONDS, event.elapsedSeconds),
        )
        is AnalyticsEvent.FreeTimerCompleted -> listOf(
            number(AnalyticsParam.DURATION_SECONDS, event.durationSeconds),
        )
        is AnalyticsEvent.AdUnlockRequested -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
            text(AnalyticsParam.AD_POLICY, event.adPolicy.name),
        )
        is AnalyticsEvent.AdUnlockEarned -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
            text(AnalyticsParam.AD_POLICY, event.adPolicy.name),
        )
        is AnalyticsEvent.AdUnlockDismissed -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
            text(AnalyticsParam.AD_POLICY, event.adPolicy.name),
        )
        is AnalyticsEvent.AdUnlockFailed -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
            text(AnalyticsParam.AD_POLICY, event.adPolicy.name),
            text(AnalyticsParam.FAILURE_REASON, event.failureReason.name),
        )
        is AnalyticsEvent.AdUnlockUnavailable -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
            text(AnalyticsParam.AD_POLICY, event.adPolicy.name),
        )
        is AnalyticsEvent.AdUnlockTapIgnored -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
        )
        is AnalyticsEvent.PaywallShown -> listOfNotNull(
            text(AnalyticsParam.SOURCE, event.source.name),
            event.access?.let { text(AnalyticsParam.ACCESS_DECISION, it.wireName) },
        )
        is AnalyticsEvent.PaywallPlanSelected -> listOf(
            text(AnalyticsParam.PLAN, event.plan.name),
            text(AnalyticsParam.SOURCE, event.source.name),
        )
        is AnalyticsEvent.PaywallDismissed -> listOf(
            text(AnalyticsParam.SOURCE, event.source.name),
        )
        is AnalyticsEvent.ContentLockedTapped -> listOf(
            id(AnalyticsParam.CONTENT_KEY, event.contentKey),
            text(AnalyticsParam.CONTENT_TYPE, event.contentType.name),
            text(AnalyticsParam.ACCESS_DECISION, event.access.wireName),
        )
        is AnalyticsEvent.CustomAffirmationCreateBlocked -> listOf(
            text(AnalyticsParam.ACCESS_DECISION, event.access.wireName),
        )
        is AnalyticsEvent.CustomAffirmationCreated -> listOf(
            text(AnalyticsParam.CREATION_METHOD, event.method.name),
        )
        AnalyticsEvent.CustomAffirmationDeleted -> emptyList()
        is AnalyticsEvent.DailyGoalReached -> listOf(
            text(AnalyticsParam.GOAL, event.goal.name),
        )
        is AnalyticsEvent.NotificationOpened -> notificationParams(
            event.family, event.variantKey, event.destination, event.locale,
        )
        is AnalyticsEvent.NotificationActionClicked -> notificationParams(
            event.family, event.variantKey, event.destination, event.locale,
        )
        is AnalyticsEvent.NotificationCompleted -> notificationParams(
            event.family, event.variantKey, event.destination, event.locale,
        )
    }

    /** Shared param mapping for the three notification_* events (design §9): all four declared
     *  params are identical in shape across [AnalyticsEvent.NotificationOpened]/
     *  [AnalyticsEvent.NotificationActionClicked]/[AnalyticsEvent.NotificationCompleted]. */
    private fun notificationParams(
        family: NotificationFamilyValue,
        variantKey: AnalyticsId?,
        destination: NotificationDestinationValue,
        locale: NotificationLocaleValue,
    ): List<AnalyticsParamValue> = listOfNotNull(
        text(AnalyticsParam.NOTIFICATION_FAMILY, family.name),
        variantKey?.let { id(AnalyticsParam.VARIANT_KEY, it) },
        text(AnalyticsParam.DESTINATION, destination.name),
        text(AnalyticsParam.LOCALE, locale.name),
    )

    private fun id(param: AnalyticsParam, id: AnalyticsId) = AnalyticsParamValue.Text(param.wireName, id.value)
    private fun text(param: AnalyticsParam, value: String) =
        AnalyticsParamValue.Text(param.wireName, value.lowercase())
    private fun number(param: AnalyticsParam, value: Long) = AnalyticsParamValue.Number(param.wireName, value)
}
