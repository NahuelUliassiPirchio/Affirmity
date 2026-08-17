package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockSource
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsEventName
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.AnalyticsLogger
import com.pirxhio.affirmity.analytics.FakeAnalyticsLogger
import com.pirxhio.affirmity.auth.AuthProviderId
import com.pirxhio.affirmity.auth.AuthRepository
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DailyViewCount
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.DocWrite
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrationSource
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import com.pirxhio.affirmity.data.repository.AffirmationRepository
import com.pirxhio.affirmity.data.repository.DailyCompletionRepository
import com.pirxhio.affirmity.data.repository.DailyMoodRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.LocalFreeEntitlementRepository
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

private class NoopAffirmationRepository2 : AffirmationRepository {
    override fun observeAll(): Flow<List<AffirmationEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: AffirmationEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
}

private class NoopDailyCompletionRepository2 : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private class NoopDailyMoodRepository2 : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private class NoopStreakHealerRepository2 : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private class NoopMeditationPreferencesRepository2 : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(null)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private class NoopNotificationSettingsRepository2 : NotificationSettingsRepository {
    override fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> =
        flowOf(ChannelSettings(enabled = false, segments = emptySet()))
    override suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) = Unit
    override suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) = Unit
    override fun observeQuietHours(): Flow<QuietHoursSettings> =
        flowOf(QuietHoursSettings(enabled = false, startMinute = 1380, endMinute = 420))
    override suspend fun setQuietHoursEnabled(enabled: Boolean) = Unit
    override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) = Unit
    override suspend fun setTimeZone(zoneId: String) = Unit
}

private class AlwaysSignedOutAuthRepository2 : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(provider: AuthProviderId, activityContext: android.content.Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

private class NeverCalledFirestoreMigrationSource2 : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean =
        error("This test stays signed-out; migration must never run")
    override suspend fun commitChunk(writes: List<DocWrite>) =
        error("This test stays signed-out; migration must never run")
}

private class FixedOutcomeAdUnlockSource(private val outcome: AdUnlockOutcome) : AdUnlockSource {
    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome = outcome
}

private fun buildAnalyticsState(
    scope: CoroutineScope,
    analytics: AnalyticsLogger,
    adUnlockSource: AdUnlockSource,
): AffirmityAppState {
    val local = DataSession.Local(
        affirmations = NoopAffirmationRepository2(),
        completions = NoopDailyCompletionRepository2(),
        moods = NoopDailyMoodRepository2(),
        healerUses = NoopStreakHealerRepository2(),
        meditation = NoopMeditationPreferencesRepository2(),
        notifications = NoopNotificationSettingsRepository2(),
        adUnlocks = FakeAdUnlockRepository(),
    )
    val trackerPreferences = mock(TrackerPreferences::class.java)
    whenever(trackerPreferences.observeAffirmationsViewedToday())
        .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
    val notificationDebugLog = mock(NotificationDebugLog::class.java)
    whenever(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
    val onboardingPreferences = mock(OnboardingPreferences::class.java)
    whenever(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { uid ->
            DataSession.Remote(
                uid = uid,
                affirmations = NoopAffirmationRepository2(),
                completions = NoopDailyCompletionRepository2(),
                moods = NoopDailyMoodRepository2(),
                healerUses = NoopStreakHealerRepository2(),
                meditation = NoopMeditationPreferencesRepository2(),
                notifications = NoopNotificationSettingsRepository2(),
                entitlements = LocalFreeEntitlementRepository(),
                adUnlocks = FakeAdUnlockRepository(),
            )
        },
        migrator = FirestoreMigrator(NeverCalledFirestoreMigrationSource2()),
        trackerPreferences = trackerPreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = AlwaysSignedOutAuthRepository2(),
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        onboardingPreferences = onboardingPreferences,
        deviceTimeZoneId = { "UTC" },
        knownGroupIds = setOf("personalizadas", "fuerza_de_voluntad"),
        adUnlockSource = adUnlockSource,
        analytics = analytics,
    )
}

/** REQ-6.2 (events 6-11) + REQ-6.3.1 (funnel-closure invariant). */
class AffirmityAppStateAdFunnelAnalyticsTest {

    private val fuerzaDeVoluntad = defaultAffirmationGroups().first { it.id == "fuerza_de_voluntad" }
    private val fuerzaKey = ContentKey(ContentType.AFFIRMATION_GROUP, fuerzaDeVoluntad.id)

    @Test
    fun `Earned outcome emits ad_unlock_requested then ad_unlock_earned`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Earned))
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)

        assertEquals(
            listOf(AnalyticsEvent.AdUnlockRequested(AnalyticsId.of(fuerzaKey), AdUnlockPolicy.PER_USE)),
            analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockRequested>(),
        )
        assertEquals(
            listOf(AnalyticsEvent.AdUnlockEarned(AnalyticsId.of(fuerzaKey), AdUnlockPolicy.PER_USE)),
            analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockEarned>(),
        )
        scope.cancel()
    }

    @Test
    fun `Dismissed outcome emits ad_unlock_dismissed`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Dismissed))
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)

        assertEquals(
            listOf(AnalyticsEvent.AdUnlockDismissed(AnalyticsId.of(fuerzaKey), AdUnlockPolicy.PER_USE)),
            analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockDismissed>(),
        )
        scope.cancel()
    }

    @Test
    fun `Failed outcome emits ad_unlock_failed with a mapped, bounded reason`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Failed("no fill")))
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)

        val failed = analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockFailed>()
        assertEquals(1, failed.size)
        assertEquals(com.pirxhio.affirmity.analytics.AdFailureReason.NO_FILL, failed.single().failureReason)
        scope.cancel()
    }

    @Test
    fun `Unavailable outcome emits ad_unlock_unavailable`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Unavailable))
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)

        assertEquals(
            listOf(AnalyticsEvent.AdUnlockUnavailable(AnalyticsId.of(fuerzaKey), AdUnlockPolicy.PER_USE)),
            analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockUnavailable>(),
        )
        scope.cancel()
    }

    @Test
    fun `a second tap while a request is in-flight emits ad_unlock_tap_ignored, never a second _requested`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        // Dispatchers.Default keeps the coroutine suspended so the in-flight guard is observable.
        val slowSource = object : AdUnlockSource {
            override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome {
                delay(200)
                return AdUnlockOutcome.Earned
            }
        }
        val state = buildAnalyticsState(CoroutineScope(Dispatchers.Default), analytics, slowSource)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(300)

        assertEquals(1, analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockRequested>().size)
        assertEquals(1, analytics.recorded.filterIsInstance<AnalyticsEvent.AdUnlockTapIgnored>().size)
        scope.cancel()
    }

    @Test
    fun `funnel closure -- every ad_unlock_requested is followed by exactly one terminal event`() = runBlocking {
        val terminalNames = setOf(
            AnalyticsEventName.AD_UNLOCK_EARNED,
            AnalyticsEventName.AD_UNLOCK_DISMISSED,
            AnalyticsEventName.AD_UNLOCK_FAILED,
            AnalyticsEventName.AD_UNLOCK_UNAVAILABLE,
        )
        val outcomes = listOf(
            AdUnlockOutcome.Earned,
            AdUnlockOutcome.Dismissed,
            AdUnlockOutcome.Failed("network"),
            AdUnlockOutcome.Unavailable,
        )
        val analytics = FakeAnalyticsLogger()
        outcomes.forEach { outcome ->
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(outcome))
            delay(20)
            state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
            delay(20)
            scope.cancel()
        }

        val names = analytics.recorded.map { it.name }
        var i = 0
        var pairs = 0
        while (i < names.size) {
            if (names[i] == AnalyticsEventName.AD_UNLOCK_REQUESTED) {
                assertTrue("no terminal event followed a _requested at index $i", i + 1 < names.size)
                assertTrue(
                    "expected exactly one terminal event after _requested, got ${names[i + 1]}",
                    names[i + 1] in terminalNames,
                )
                pairs++
                i += 2
            } else {
                i++
            }
        }
        assertEquals(4, pairs)
    }

    // --- D9: daily_goal_reached fires on the exact crossing, not on every subsequent call ---------

    @Test
    fun `daily_goal_reached AFFIRMATION fires exactly once, on the exact threshold crossing`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Unavailable))
        delay(50)

        // AFFIRMATIONS_GOAL_PER_DAY == 5: views 1-4 must not emit, view 5 must, view 6 must not re-emit.
        repeat(6) {
            state.recordAffirmationViewed()
            delay(20)
        }

        assertEquals(1, analytics.recorded.filterIsInstance<AnalyticsEvent.DailyGoalReached>().size)
        assertEquals(
            com.pirxhio.affirmity.analytics.DailyGoal.AFFIRMATION,
            analytics.recorded.filterIsInstance<AnalyticsEvent.DailyGoalReached>().single().goal,
        )
        scope.cancel()
    }

    @Test
    fun `daily_goal_reached MEDITATION fires once per process per day, guarded across repeat completions`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Unavailable))
        delay(50)

        state.recordMeditationCompleted()
        delay(20)
        state.recordMeditationCompleted()
        delay(20)

        assertEquals(1, analytics.recorded.filterIsInstance<AnalyticsEvent.DailyGoalReached>().size)
        assertEquals(
            com.pirxhio.affirmity.analytics.DailyGoal.MEDITATION,
            analytics.recorded.filterIsInstance<AnalyticsEvent.DailyGoalReached>().single().goal,
        )
        scope.cancel()
    }

    // --- Event 18 (REQ-5.5): custom_affirmation_deleted carries zero parameters -------------------

    @Test
    fun `removeAffirmation emits custom_affirmation_deleted with zero parameters`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(scope, analytics, FixedOutcomeAdUnlockSource(AdUnlockOutcome.Unavailable))
        delay(50)

        state.removeAffirmation("some-id")
        delay(20)

        assertEquals(listOf(AnalyticsEvent.CustomAffirmationDeleted), analytics.recorded)
        scope.cancel()
    }
}
