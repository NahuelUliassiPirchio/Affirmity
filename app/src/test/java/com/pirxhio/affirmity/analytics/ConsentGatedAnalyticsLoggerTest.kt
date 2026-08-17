package com.pirxhio.affirmity.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-6.3.4 (consent gating). UNKNOWN and DENIED must both fully suppress delegation; GRANTED
 *  must delegate every attempted event. */
class ConsentGatedAnalyticsLoggerTest {

    private val allNineteenSample: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.MeditationEntryTapped(
            AnalyticsId.of(com.pirxhio.affirmity.access.ContentKey(com.pirxhio.affirmity.access.ContentType.MEDITATION, "sample")),
            AccessDecisionValue.UNLOCKED,
            com.pirxhio.affirmity.access.AdUnlockPolicy.NONE,
        ),
        AnalyticsEvent.MeditationStarted(sampleId(), AccessDecisionValue.UNLOCKED),
        AnalyticsEvent.MeditationCompleted(sampleId(), AccessDecisionValue.UNLOCKED, 60L),
        AnalyticsEvent.MeditationCancelled(sampleId(), 10L),
        AnalyticsEvent.FreeTimerCompleted(120L),
        AnalyticsEvent.AdUnlockRequested(sampleId(), com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE),
        AnalyticsEvent.AdUnlockEarned(sampleId(), com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE),
        AnalyticsEvent.AdUnlockDismissed(sampleId(), com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE),
        AnalyticsEvent.AdUnlockFailed(sampleId(), com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE, AdFailureReason.NO_FILL),
        AnalyticsEvent.AdUnlockUnavailable(sampleId(), com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE),
        AnalyticsEvent.AdUnlockTapIgnored(sampleId()),
        AnalyticsEvent.PaywallShown(PaywallSource.SETTINGS, null),
        AnalyticsEvent.PaywallPlanSelected(PaywallPlan.MONTHLY, PaywallSource.SETTINGS),
        AnalyticsEvent.PaywallDismissed(PaywallSource.SETTINGS),
        AnalyticsEvent.ContentLockedTapped(sampleId(), AnalyticsContentType.MEDITATION, AccessDecisionValue.LOCKED_NEEDS_PRO),
        AnalyticsEvent.CustomAffirmationCreateBlocked(AccessDecisionValue.LOCKED_NEEDS_PRO),
        AnalyticsEvent.CustomAffirmationCreated(CreationMethod.COLOR),
        AnalyticsEvent.CustomAffirmationDeleted,
        AnalyticsEvent.DailyGoalReached(DailyGoal.AFFIRMATION),
    )

    private fun sampleId() = AnalyticsId.of(
        com.pirxhio.affirmity.access.ContentKey(com.pirxhio.affirmity.access.ContentType.MEDITATION, "sample"),
    )

    @Test
    fun `all 19 sample events are covered by this test`() {
        assertEquals(19, allNineteenSample.size)
    }

    @Test
    fun `UNKNOWN consent blocks every event`() {
        val fake = FakeAnalyticsLogger()
        val gated = ConsentGatedAnalyticsLogger(fake) { AnalyticsConsentState.UNKNOWN }

        allNineteenSample.forEach(gated::log)

        assertTrue(fake.recorded.isEmpty())
    }

    @Test
    fun `DENIED consent blocks every event`() {
        val fake = FakeAnalyticsLogger()
        val gated = ConsentGatedAnalyticsLogger(fake) { AnalyticsConsentState.DENIED }

        allNineteenSample.forEach(gated::log)

        assertTrue(fake.recorded.isEmpty())
    }

    @Test
    fun `GRANTED consent delegates every event`() {
        val fake = FakeAnalyticsLogger()
        val gated = ConsentGatedAnalyticsLogger(fake) { AnalyticsConsentState.GRANTED }

        allNineteenSample.forEach(gated::log)

        assertEquals(allNineteenSample, fake.recorded)
    }
}
