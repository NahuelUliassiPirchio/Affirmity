package com.pirxhio.affirmity.analytics

import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

/** REQ-6.2/D6: one mapper case per subtype, asserting exact `(wireName, params)`. The exhaustive
 *  `when` in [FirebaseAnalyticsLogger] makes a missing case a compile error, so this file's job is
 *  proving the VALUES are right, not proving coverage is complete. */
class FirebaseAnalyticsLoggerTest {

    private fun id(id: String, type: ContentType = ContentType.MEDITATION) = AnalyticsId.of(ContentKey(type, id))

    private fun log(event: AnalyticsEvent): FakeFirebaseAnalyticsSink.Logged {
        val sink = FakeFirebaseAnalyticsSink()
        FirebaseAnalyticsLogger(sink).log(event)
        return sink.logged.single()
    }

    @Test
    fun `meditation_entry_tapped maps entry_id, access_decision, ad_policy`() {
        val logged = log(AnalyticsEvent.MeditationEntryTapped(id("calma"), AccessDecisionValue.UNLOCKED, AdUnlockPolicy.NONE))
        assertEquals("meditation_entry_tapped", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("entry_id", "meditation_calma"),
                AnalyticsParamValue.Text("access_decision", "unlocked"),
                AnalyticsParamValue.Text("ad_policy", "none"),
            ),
            logged.params,
        )
    }

    @Test
    fun `meditation_started maps entry_id, access_decision`() {
        val logged = log(AnalyticsEvent.MeditationStarted(id("calma"), AccessDecisionValue.LOCKED_AD_UNLOCKABLE))
        assertEquals("meditation_started", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("entry_id", "meditation_calma"),
                AnalyticsParamValue.Text("access_decision", "locked_ad_unlockable"),
            ),
            logged.params,
        )
    }

    @Test
    fun `meditation_completed maps entry_id, access_decision, elapsed_seconds`() {
        val logged = log(AnalyticsEvent.MeditationCompleted(id("calma"), AccessDecisionValue.UNLOCKED, 300L))
        assertEquals("meditation_completed", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("entry_id", "meditation_calma"),
                AnalyticsParamValue.Text("access_decision", "unlocked"),
                AnalyticsParamValue.Number("elapsed_seconds", 300L),
            ),
            logged.params,
        )
    }

    @Test
    fun `meditation_cancelled maps entry_id, elapsed_seconds`() {
        val logged = log(AnalyticsEvent.MeditationCancelled(id("calma"), 12L))
        assertEquals("meditation_cancelled", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("entry_id", "meditation_calma"),
                AnalyticsParamValue.Number("elapsed_seconds", 12L),
            ),
            logged.params,
        )
    }

    @Test
    fun `free_timer_completed maps duration_seconds`() {
        val logged = log(AnalyticsEvent.FreeTimerCompleted(900L))
        assertEquals("free_timer_completed", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Number("duration_seconds", 900L)), logged.params)
    }

    @Test
    fun `ad_unlock_requested maps content_key, ad_policy`() {
        val logged = log(AnalyticsEvent.AdUnlockRequested(id("fuerza_de_voluntad", ContentType.AFFIRMATION_GROUP), AdUnlockPolicy.PER_USE))
        assertEquals("ad_unlock_requested", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("content_key", "affirmationGroup_fuerza_de_voluntad"),
                AnalyticsParamValue.Text("ad_policy", "per_use"),
            ),
            logged.params,
        )
    }

    @Test
    fun `ad_unlock_earned maps content_key, ad_policy`() {
        val logged = log(AnalyticsEvent.AdUnlockEarned(id("calma"), AdUnlockPolicy.ONE_TIME_TRIAL))
        assertEquals("ad_unlock_earned", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("content_key", "meditation_calma"),
                AnalyticsParamValue.Text("ad_policy", "one_time_trial"),
            ),
            logged.params,
        )
    }

    @Test
    fun `ad_unlock_dismissed maps content_key, ad_policy`() {
        val logged = log(AnalyticsEvent.AdUnlockDismissed(id("calma"), AdUnlockPolicy.PER_USE))
        assertEquals("ad_unlock_dismissed", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("content_key", "meditation_calma"),
                AnalyticsParamValue.Text("ad_policy", "per_use"),
            ),
            logged.params,
        )
    }

    @Test
    fun `ad_unlock_failed maps content_key, ad_policy, failure_reason`() {
        val logged = log(AnalyticsEvent.AdUnlockFailed(id("calma"), AdUnlockPolicy.PER_USE, AdFailureReason.NO_FILL))
        assertEquals("ad_unlock_failed", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("content_key", "meditation_calma"),
                AnalyticsParamValue.Text("ad_policy", "per_use"),
                AnalyticsParamValue.Text("failure_reason", "no_fill"),
            ),
            logged.params,
        )
    }

    @Test
    fun `ad_unlock_unavailable maps content_key, ad_policy`() {
        val logged = log(AnalyticsEvent.AdUnlockUnavailable(id("calma"), AdUnlockPolicy.PER_USE))
        assertEquals("ad_unlock_unavailable", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("content_key", "meditation_calma"),
                AnalyticsParamValue.Text("ad_policy", "per_use"),
            ),
            logged.params,
        )
    }

    @Test
    fun `ad_unlock_tap_ignored maps content_key only`() {
        val logged = log(AnalyticsEvent.AdUnlockTapIgnored(id("calma")))
        assertEquals("ad_unlock_tap_ignored", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Text("content_key", "meditation_calma")), logged.params)
    }

    @Test
    fun `paywall_shown maps source and omits null access_decision`() {
        val logged = log(AnalyticsEvent.PaywallShown(PaywallSource.SETTINGS, access = null))
        assertEquals("paywall_shown", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Text("source", "settings")), logged.params)
    }

    @Test
    fun `paywall_shown includes access_decision when present`() {
        val logged = log(AnalyticsEvent.PaywallShown(PaywallSource.MEDITATION_CATALOG, AccessDecisionValue.LOCKED_NEEDS_PRO))
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("source", "meditation_catalog"),
                AnalyticsParamValue.Text("access_decision", "locked_needs_pro"),
            ),
            logged.params,
        )
    }

    @Test
    fun `paywall_plan_selected maps plan, source`() {
        val logged = log(AnalyticsEvent.PaywallPlanSelected(PaywallPlan.ANNUAL, PaywallSource.GROUP_SELECTOR))
        assertEquals("paywall_plan_selected", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("plan", "annual"),
                AnalyticsParamValue.Text("source", "group_selector"),
            ),
            logged.params,
        )
    }

    @Test
    fun `paywall_dismissed maps source`() {
        val logged = log(AnalyticsEvent.PaywallDismissed(PaywallSource.OTHER))
        assertEquals("paywall_dismissed", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Text("source", "other")), logged.params)
    }

    @Test
    fun `content_locked_tapped maps content_key, content_type, access_decision`() {
        val logged = log(
            AnalyticsEvent.ContentLockedTapped(
                id("fuerza_de_voluntad", ContentType.AFFIRMATION_GROUP),
                AnalyticsContentType.AFFIRMATION_GROUP,
                AccessDecisionValue.LOCKED_NEEDS_PRO,
            ),
        )
        assertEquals("content_locked_tapped", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("content_key", "affirmationGroup_fuerza_de_voluntad"),
                AnalyticsParamValue.Text("content_type", "affirmation_group"),
                AnalyticsParamValue.Text("access_decision", "locked_needs_pro"),
            ),
            logged.params,
        )
    }

    @Test
    fun `custom_affirmation_create_blocked maps access_decision`() {
        val logged = log(AnalyticsEvent.CustomAffirmationCreateBlocked(AccessDecisionValue.LOCKED_NEEDS_PRO))
        assertEquals("custom_affirmation_create_blocked", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Text("access_decision", "locked_needs_pro")), logged.params)
    }

    @Test
    fun `custom_affirmation_created maps creation_method`() {
        val logged = log(AnalyticsEvent.CustomAffirmationCreated(CreationMethod.GALLERY))
        assertEquals("custom_affirmation_created", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Text("creation_method", "gallery")), logged.params)
    }

    @Test
    fun `custom_affirmation_deleted carries zero parameters`() {
        val logged = log(AnalyticsEvent.CustomAffirmationDeleted)
        assertEquals("custom_affirmation_deleted", logged.name)
        assertEquals(emptyList<AnalyticsParamValue>(), logged.params)
    }

    @Test
    fun `daily_goal_reached maps goal`() {
        val logged = log(AnalyticsEvent.DailyGoalReached(DailyGoal.MEDITATION))
        assertEquals("daily_goal_reached", logged.name)
        assertEquals(listOf(AnalyticsParamValue.Text("goal", "meditation")), logged.params)
    }

    @Test
    fun `notification_opened maps family, variant_key, destination, locale`() {
        val logged = log(
            AnalyticsEvent.NotificationOpened(
                NotificationFamilyValue.STREAK,
                AnalyticsId.ofNotificationVariant("streak_risk_14plus_a"),
                NotificationDestinationValue.STREAK_ACTION,
                NotificationLocaleValue.ES,
            ),
        )
        assertEquals("notification_opened", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("notification_family", "streak"),
                AnalyticsParamValue.Text("variant_key", "streak_risk_14plus_a"),
                AnalyticsParamValue.Text("destination", "streak_action"),
                AnalyticsParamValue.Text("locale", "es"),
            ),
            logged.params,
        )
    }

    @Test
    fun `notification_opened omits null variant_key`() {
        val logged = log(
            AnalyticsEvent.NotificationOpened(
                NotificationFamilyValue.MEDITATION_RETURN,
                variantKey = null,
                NotificationDestinationValue.SHORT_MEDITATION,
                NotificationLocaleValue.EN,
            ),
        )
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("notification_family", "meditation_return"),
                AnalyticsParamValue.Text("destination", "short_meditation"),
                AnalyticsParamValue.Text("locale", "en"),
            ),
            logged.params,
        )
    }

    @Test
    fun `notification_action_clicked maps family, variant_key, destination, locale`() {
        val logged = log(
            AnalyticsEvent.NotificationActionClicked(
                NotificationFamilyValue.HEALER,
                AnalyticsId.ofNotificationVariant("healer_window_a"),
                NotificationDestinationValue.HEALER_FLOW,
                NotificationLocaleValue.ES,
            ),
        )
        assertEquals("notification_action_clicked", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("notification_family", "healer"),
                AnalyticsParamValue.Text("variant_key", "healer_window_a"),
                AnalyticsParamValue.Text("destination", "healer_flow"),
                AnalyticsParamValue.Text("locale", "es"),
            ),
            logged.params,
        )
    }

    @Test
    fun `notification_completed maps family, variant_key, destination, locale`() {
        val logged = log(
            AnalyticsEvent.NotificationCompleted(
                NotificationFamilyValue.COMPASS,
                AnalyticsId.ofNotificationVariant("gratitude_today"),
                NotificationDestinationValue.COMPASS_QUESTION,
                NotificationLocaleValue.EN,
            ),
        )
        assertEquals("notification_completed", logged.name)
        assertEquals(
            listOf(
                AnalyticsParamValue.Text("notification_family", "compass"),
                AnalyticsParamValue.Text("variant_key", "gratitude_today"),
                AnalyticsParamValue.Text("destination", "compass_question"),
                AnalyticsParamValue.Text("locale", "en"),
            ),
            logged.params,
        )
    }
}
