package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.access.AdUnlockSource
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.analytics.AnalyticsLogger
import com.pirxhio.affirmity.analytics.NoOpAnalyticsLogger
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
import com.pirxhio.affirmity.data.local.OnboardingGuidePreferences
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.DocWrite
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrationSource
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.repository.AffirmationRepository
import com.pirxhio.affirmity.data.repository.DailyCompletionRepository
import com.pirxhio.affirmity.data.repository.DailyMoodRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.LocalFreeEntitlementRepository
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationCanceller
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

private class NoopAffirmationRepository3 : AffirmationRepository {
    override fun observeAll(): Flow<List<AffirmationEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: AffirmationEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

/** Stateful (unlike the various `Noop*` fakes elsewhere): [isStreakRequirementCompleteToday] and
 * [StreakHealerStats.evaluate] both need to see marks actually accumulate to be exercised
 * meaningfully. */
private class CancellationFakeDailyCompletionRepository : DailyCompletionRepository {
    private val rows = mutableMapOf<Long, DailyCompletionEntity>()

    fun seed(epochDay: Long, meditationDone: Boolean = false, affirmationDone: Boolean = false) {
        rows[epochDay] = DailyCompletionEntity(epochDay, meditationDone, affirmationDone)
    }

    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> =
        flowOf((from..to).mapNotNull { rows[it] })

    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> =
        (from..to).mapNotNull { rows[it] }

    override suspend fun markMeditation(epochDay: Long) {
        val existing = rows[epochDay] ?: DailyCompletionEntity(epochDay)
        rows[epochDay] = existing.copy(meditationDone = true)
    }

    override suspend fun markAffirmation(epochDay: Long) {
        val existing = rows[epochDay] ?: DailyCompletionEntity(epochDay)
        rows[epochDay] = existing.copy(affirmationDone = true)
    }
}

private class FakeDailyMoodRepository3 : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private class CancellationFakeStreakHealerRepository : StreakHealerRepository {
    private val uses = mutableListOf<StreakHealerUseEntity>()
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(uses.toList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> =
        uses.filter { it.healedEpochDay in from..to }
    override suspend fun recordUse(healedEpochDay: Long) {
        uses.add(StreakHealerUseEntity(healedEpochDay, System.currentTimeMillis()))
    }
}

private class NoopMeditationPreferencesRepository3 : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(null)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private class NoopNotificationSettingsRepository3 : NotificationSettingsRepository {
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

private class AlwaysSignedOutAuthRepository3 : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(provider: AuthProviderId, activityContext: android.content.Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

private class NeverCalledFirestoreMigrationSource3 : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean =
        error("This test stays signed-out; migration must never run")
    override suspend fun commitChunk(writes: List<DocWrite>) =
        error("This test stays signed-out; migration must never run")
}

private class FixedOutcomeAdUnlockSource3(private val outcome: AdUnlockOutcome) : AdUnlockSource {
    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome = outcome
}

/**
 * Notifications V2 task 4.7: [AffirmityAppState.recordMood], [AffirmityAppState.recordMeditationCompleted],
 * [AffirmityAppState.recordAffirmationViewed], and [AffirmityAppState.activateStreakHealer] must call
 * [NotificationCanceller.cancelFamily] for their respective family once the corresponding action
 * actually completes (design §6's "Cancel on completion for each family" scenario).
 */
class AffirmityAppStateNotificationCancellationTest {

    private fun buildState(
        completions: CancellationFakeDailyCompletionRepository,
        healerUses: CancellationFakeStreakHealerRepository,
        notificationCanceller: NotificationCanceller,
        scope: CoroutineScope,
    ): AffirmityAppState {
        val local = DataSession.Local(
            affirmations = NoopAffirmationRepository3(),
            completions = completions,
            moods = FakeDailyMoodRepository3(),
            healerUses = healerUses,
            meditation = NoopMeditationPreferencesRepository3(),
            notifications = NoopNotificationSettingsRepository3(),
            adUnlocks = FakeAdUnlockRepository(),
        )
        val trackerPreferences = mock(TrackerPreferences::class.java)
        whenever(trackerPreferences.observeAffirmationsViewedToday())
            .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
        val notificationDebugLog = mock(NotificationDebugLog::class.java)
        whenever(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
        val onboardingPreferences = mock(OnboardingPreferences::class.java)
        whenever(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))
        val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
        whenever(onboardingGuidePreferences.observeHasSeenGuide()).thenReturn(flowOf(true))

        return AffirmityAppState(
            scope = scope,
            local = local,
            remoteSessionFactory = { uid ->
                DataSession.Remote(
                    uid = uid,
                    affirmations = NoopAffirmationRepository3(),
                    completions = completions,
                    moods = FakeDailyMoodRepository3(),
                    healerUses = healerUses,
                    meditation = NoopMeditationPreferencesRepository3(),
                    notifications = NoopNotificationSettingsRepository3(),
                    entitlements = LocalFreeEntitlementRepository(),
                    adUnlocks = FakeAdUnlockRepository(),
                )
            },
            migrator = FirestoreMigrator(NeverCalledFirestoreMigrationSource3()),
            trackerPreferences = trackerPreferences,
            imageStore = mock(AffirmationImageStore::class.java),
            notificationDebugLog = notificationDebugLog,
            notifier = mock(Notifier::class.java),
            notificationCanceller = notificationCanceller,
            widgetUpdater = WidgetUpdater { },
            authRepository = AlwaysSignedOutAuthRepository3(),
            fcmTokenRepository = mock(FcmTokenRepository::class.java),
            onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
            onboardingPreferences = onboardingPreferences,
            onboardingGuidePreferences = onboardingGuidePreferences,
            deviceTimeZoneId = { "UTC" },
            knownGroupIds = setOf("personalizadas", "fuerza_de_voluntad"),
            adUnlockSource = FixedOutcomeAdUnlockSource3(AdUnlockOutcome.Unavailable),
            analytics = NoOpAnalyticsLogger,
        )
    }

    @Test
    fun `recordMood cancels the Mood notification family when saving today`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val state = buildState(CancellationFakeDailyCompletionRepository(), CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.recordMood(DayClock.epochDay(), moodValue = 3, note = null)
        delay(20)

        verify(canceller).cancelFamily(NotificationChannelSpec.MOOD)
        scope.cancel()
    }

    @Test
    fun `recordMood does NOT cancel Mood when backfilling a past day`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val state = buildState(CancellationFakeDailyCompletionRepository(), CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.recordMood(DayClock.epochDay() - 3, moodValue = 3, note = null)
        delay(20)

        verify(canceller, never()).cancelFamily(NotificationChannelSpec.MOOD)
        scope.cancel()
    }

    @Test
    fun `recordMeditationCompleted always cancels Meditation Return`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val state = buildState(CancellationFakeDailyCompletionRepository(), CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.recordMeditationCompleted(System.currentTimeMillis())
        delay(20)

        verify(canceller).cancelFamily(NotificationChannelSpec.MEDITATION_RETURN)
        scope.cancel()
    }

    @Test
    fun `recordMeditationCompleted cancels Streak only once affirmation is also done today`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val completions = CancellationFakeDailyCompletionRepository()
        val today = DayClock.epochDay()
        completions.seed(today, affirmationDone = true)
        val state = buildState(completions, CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.recordMeditationCompleted(System.currentTimeMillis())
        delay(20)

        verify(canceller).cancelFamily(NotificationChannelSpec.STREAK)
        scope.cancel()
    }

    @Test
    fun `recordMeditationCompleted does NOT cancel Streak while affirmation is still incomplete`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val state = buildState(CancellationFakeDailyCompletionRepository(), CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.recordMeditationCompleted(System.currentTimeMillis())
        delay(20)

        verify(canceller, never()).cancelFamily(NotificationChannelSpec.STREAK)
        scope.cancel()
    }

    @Test
    fun `recordAffirmationViewed crossing the goal cancels Streak once meditation is also done today`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val completions = CancellationFakeDailyCompletionRepository()
        val today = DayClock.epochDay()
        completions.seed(today, meditationDone = true)
        val state = buildState(completions, CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        // AffirmityAppState's private AFFIRMATIONS_GOAL_PER_DAY == 5 (see
        // AffirmityAppStateAdFunnelAnalyticsTest, which relies on the same literal).
        repeat(5) {
            state.recordAffirmationViewed()
            delay(20)
        }

        verify(canceller).cancelFamily(NotificationChannelSpec.STREAK)
        scope.cancel()
    }

    @Test
    fun `activateStreakHealer cancels Healer only when an activation actually happens`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val completions = CancellationFakeDailyCompletionRepository()
        val today = DayClock.epochDay()
        // Two consecutive full days grant a held healer; the day right before today is a break
        // (zero activity) with an active day right before it -- see StreakHealerStats.evaluate.
        completions.seed(today - 3, meditationDone = true, affirmationDone = true)
        completions.seed(today - 2, meditationDone = true, affirmationDone = true)
        val state = buildState(completions, CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.activateStreakHealer()
        delay(20)

        verify(canceller).cancelFamily(NotificationChannelSpec.HEALER)
        scope.cancel()
    }

    @Test
    fun `activateStreakHealer is a silent no-op and never cancels Healer when not eligible`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val canceller = mock(NotificationCanceller::class.java)
        val state = buildState(CancellationFakeDailyCompletionRepository(), CancellationFakeStreakHealerRepository(), canceller, scope)
        delay(50)

        state.activateStreakHealer()
        delay(20)

        verify(canceller, never()).cancelFamily(NotificationChannelSpec.HEALER)
        scope.cancel()
    }
}
