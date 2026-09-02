package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.access.AdUnlockSource
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsId
import com.pirxhio.affirmity.analytics.FakeAnalyticsLogger
import com.pirxhio.affirmity.analytics.NotificationDestinationValue
import com.pirxhio.affirmity.analytics.NotificationFamilyValue
import com.pirxhio.affirmity.analytics.NotificationLocaleValue
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

private class NoopAffirmationRepository4 : AffirmationRepository {
    override fun observeAll(): Flow<List<AffirmationEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: AffirmationEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private class CompletionAnalyticsFakeDailyCompletionRepository : DailyCompletionRepository {
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

private class FakeDailyMoodRepository4 : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private class CompletionAnalyticsFakeStreakHealerRepository : StreakHealerRepository {
    private val uses = mutableListOf<StreakHealerUseEntity>()
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(uses.toList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> =
        uses.filter { it.healedEpochDay in from..to }
    override suspend fun recordUse(healedEpochDay: Long) {
        uses.add(StreakHealerUseEntity(healedEpochDay, System.currentTimeMillis()))
    }
}

private class NoopMeditationPreferencesRepository4 : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(null)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private class NoopNotificationSettingsRepository4 : NotificationSettingsRepository {
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

private class AlwaysSignedOutAuthRepository4 : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(provider: AuthProviderId, activityContext: android.content.Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

private class NeverCalledFirestoreMigrationSource4 : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean =
        error("This test stays signed-out; migration must never run")
    override suspend fun commitChunk(writes: List<DocWrite>) =
        error("This test stays signed-out; migration must never run")
}

private class FixedOutcomeAdUnlockSource4(private val outcome: AdUnlockOutcome) : AdUnlockSource {
    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome = outcome
}

/**
 * Notifications V2 Phase 6 (design §9): [AffirmityAppState.setActiveNotificationAttribution] /
 * [AffirmityAppState.completeNotificationAttribution] fire `notification_completed` at exactly the
 * same 4 in-class completion sites [AffirmityAppStateNotificationCancellationTest] already covers
 * for [NotificationCanceller.cancelFamily] (mood, streak, meditation return, healer) -- attribution
 * is intent-scoped (no new persistence store): it only fires when the family completing matches the
 * family most recently attributed via [AffirmityAppState.setActiveNotificationAttribution], and is
 * consumed (cleared) on first match.
 */
class AffirmityAppStateNotificationCompletedAnalyticsTest {

    private fun buildState(
        completions: CompletionAnalyticsFakeDailyCompletionRepository,
        healerUses: CompletionAnalyticsFakeStreakHealerRepository,
        analytics: FakeAnalyticsLogger,
        scope: CoroutineScope,
    ): AffirmityAppState {
        val local = DataSession.Local(
            affirmations = NoopAffirmationRepository4(),
            completions = completions,
            moods = FakeDailyMoodRepository4(),
            healerUses = healerUses,
            meditation = NoopMeditationPreferencesRepository4(),
            notifications = NoopNotificationSettingsRepository4(),
            adUnlocks = FakeAdUnlockRepository(),
        )
        val trackerPreferences = mock(TrackerPreferences::class.java)
        org.mockito.Mockito.`when`(trackerPreferences.observeAffirmationsViewedToday())
            .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
        val notificationDebugLog = mock(NotificationDebugLog::class.java)
        org.mockito.Mockito.`when`(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
        val onboardingPreferences = mock(OnboardingPreferences::class.java)
        org.mockito.Mockito.`when`(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))
        val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
        org.mockito.Mockito.`when`(onboardingGuidePreferences.observeHasSeenGuide()).thenReturn(flowOf(true))

        return AffirmityAppState(
            scope = scope,
            local = local,
            remoteSessionFactory = { uid ->
                DataSession.Remote(
                    uid = uid,
                    affirmations = NoopAffirmationRepository4(),
                    completions = completions,
                    moods = FakeDailyMoodRepository4(),
                    healerUses = healerUses,
                    meditation = NoopMeditationPreferencesRepository4(),
                    notifications = NoopNotificationSettingsRepository4(),
                    entitlements = LocalFreeEntitlementRepository(),
                    adUnlocks = FakeAdUnlockRepository(),
                )
            },
            migrator = FirestoreMigrator(NeverCalledFirestoreMigrationSource4()),
            trackerPreferences = trackerPreferences,
            imageStore = mock(AffirmationImageStore::class.java),
            notificationDebugLog = notificationDebugLog,
            notifier = mock(Notifier::class.java),
            notificationCanceller = mock(NotificationCanceller::class.java),
            widgetUpdater = WidgetUpdater { },
            authRepository = AlwaysSignedOutAuthRepository4(),
            fcmTokenRepository = mock(FcmTokenRepository::class.java),
            onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
            onboardingPreferences = onboardingPreferences,
            onboardingGuidePreferences = onboardingGuidePreferences,
            deviceTimeZoneId = { "UTC" },
            knownGroupIds = setOf("personalizadas", "fuerza_de_voluntad"),
            adUnlockSource = FixedOutcomeAdUnlockSource4(AdUnlockOutcome.Unavailable),
            analytics = analytics,
        )
    }

    @Test
    fun `mood save fires notification_completed when attributed to mood`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildState(
            CompletionAnalyticsFakeDailyCompletionRepository(),
            CompletionAnalyticsFakeStreakHealerRepository(),
            analytics,
            scope,
        )
        delay(50)
        state.setActiveNotificationAttribution("mood", "mood_afternoon_a", "mood_checkin", "es")

        state.recordMood(DayClock.epochDay(), moodValue = 3, note = null)
        delay(20)

        assertEquals(
            listOf(
                AnalyticsEvent.NotificationCompleted(
                    NotificationFamilyValue.MOOD,
                    AnalyticsId.ofNotificationVariant("mood_afternoon_a"),
                    NotificationDestinationValue.MOOD_CHECKIN,
                    NotificationLocaleValue.ES,
                ),
            ),
            analytics.recorded,
        )
        scope.cancel()
    }

    @Test
    fun `mood save does NOT fire notification_completed when attribution is a different family`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildState(
            CompletionAnalyticsFakeDailyCompletionRepository(),
            CompletionAnalyticsFakeStreakHealerRepository(),
            analytics,
            scope,
        )
        delay(50)
        state.setActiveNotificationAttribution("streak", "streak_1_3_a", "streak_action", "es")

        state.recordMood(DayClock.epochDay(), moodValue = 3, note = null)
        delay(20)

        assertTrue(analytics.recorded.none { it is AnalyticsEvent.NotificationCompleted })
        scope.cancel()
    }

    @Test
    fun `mood save with no active attribution never fires notification_completed`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildState(
            CompletionAnalyticsFakeDailyCompletionRepository(),
            CompletionAnalyticsFakeStreakHealerRepository(),
            analytics,
            scope,
        )
        delay(50)

        state.recordMood(DayClock.epochDay(), moodValue = 3, note = null)
        delay(20)

        assertTrue(analytics.recorded.none { it is AnalyticsEvent.NotificationCompleted })
        scope.cancel()
    }

    @Test
    fun `recordMeditationCompleted fires notification_completed for meditation_return`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildState(
            CompletionAnalyticsFakeDailyCompletionRepository(),
            CompletionAnalyticsFakeStreakHealerRepository(),
            analytics,
            scope,
        )
        delay(50)
        state.setActiveNotificationAttribution("meditation_return", "inactive_3_4_a", "short_meditation", "en")

        state.recordMeditationCompleted(System.currentTimeMillis())
        delay(20)

        assertEquals(
            listOf(
                AnalyticsEvent.NotificationCompleted(
                    NotificationFamilyValue.MEDITATION_RETURN,
                    AnalyticsId.ofNotificationVariant("inactive_3_4_a"),
                    NotificationDestinationValue.SHORT_MEDITATION,
                    NotificationLocaleValue.EN,
                ),
            ),
            // recordMeditationCompleted also emits its own DailyGoalReached(MEDITATION) --
            // unrelated to this notification-attribution seam, filtered out here.
            analytics.recorded.filterIsInstance<AnalyticsEvent.NotificationCompleted>(),
        )
        scope.cancel()
    }

    @Test
    fun `recordMeditationCompleted crossing the streak requirement fires notification_completed for streak`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val completions = CompletionAnalyticsFakeDailyCompletionRepository()
        val today = DayClock.epochDay()
        completions.seed(today, affirmationDone = true)
        val state = buildState(completions, CompletionAnalyticsFakeStreakHealerRepository(), analytics, scope)
        delay(50)
        state.setActiveNotificationAttribution("streak", "streak_1_3_a", "streak_action", "es")

        state.recordMeditationCompleted(System.currentTimeMillis())
        delay(20)

        assertEquals(
            listOf(
                AnalyticsEvent.NotificationCompleted(
                    NotificationFamilyValue.STREAK,
                    AnalyticsId.ofNotificationVariant("streak_1_3_a"),
                    NotificationDestinationValue.STREAK_ACTION,
                    NotificationLocaleValue.ES,
                ),
            ),
            // recordMeditationCompleted also emits its own DailyGoalReached(MEDITATION) --
            // unrelated to this notification-attribution seam, filtered out here.
            analytics.recorded.filterIsInstance<AnalyticsEvent.NotificationCompleted>(),
        )
        scope.cancel()
    }

    @Test
    fun `activateStreakHealer fires notification_completed only on a real activation`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val completions = CompletionAnalyticsFakeDailyCompletionRepository()
        val today = DayClock.epochDay()
        completions.seed(today - 3, meditationDone = true, affirmationDone = true)
        completions.seed(today - 2, meditationDone = true, affirmationDone = true)
        val state = buildState(completions, CompletionAnalyticsFakeStreakHealerRepository(), analytics, scope)
        delay(50)
        state.setActiveNotificationAttribution("healer", "healer_window_a", "healer_flow", "es")

        state.activateStreakHealer()
        delay(20)

        assertEquals(
            listOf(
                AnalyticsEvent.NotificationCompleted(
                    NotificationFamilyValue.HEALER,
                    AnalyticsId.ofNotificationVariant("healer_window_a"),
                    NotificationDestinationValue.HEALER_FLOW,
                    NotificationLocaleValue.ES,
                ),
            ),
            analytics.recorded,
        )
        scope.cancel()
    }

    @Test
    fun `completeNotificationAttribution matches REFLECTION's wireChannelKey, not the old raw 'compass' literal`() = runBlocking {
        // Readability fix (MainActivity.kt's CompassAnswerHost.onAnswered): the server sets the
        // wire `family` extra to the channel's own wireChannelKey ("reflection" for REFLECTION,
        // functions/src/index.ts's "reflection family"), never the literal "compass" the call site
        // used to pass. This pins the fix and documents the bug it closed: the old literal would
        // never have matched a real Compass/Reflection notification's attribution.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildState(
            CompletionAnalyticsFakeDailyCompletionRepository(),
            CompletionAnalyticsFakeStreakHealerRepository(),
            analytics,
            scope,
        )
        delay(50)
        state.setActiveNotificationAttribution(
            NotificationChannelSpec.REFLECTION.wireChannelKey,
            "reflection_prompt_a",
            "compass_question",
            "es",
        )

        // The old literal "compass" would fail this match (attribution.family stays "reflection"
        // and is never cleared) -- assert that first, then that the real fix's key matches.
        state.completeNotificationAttribution("compass")
        assertTrue(analytics.recorded.none { it is AnalyticsEvent.NotificationCompleted })

        state.completeNotificationAttribution(NotificationChannelSpec.REFLECTION.wireChannelKey)
        assertEquals(1, analytics.recorded.count { it is AnalyticsEvent.NotificationCompleted })
        scope.cancel()
    }

    @Test
    fun `attribution is consumed on first match and does not fire twice`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val analytics = FakeAnalyticsLogger()
        val state = buildState(
            CompletionAnalyticsFakeDailyCompletionRepository(),
            CompletionAnalyticsFakeStreakHealerRepository(),
            analytics,
            scope,
        )
        delay(50)
        state.setActiveNotificationAttribution("mood", "mood_afternoon_a", "mood_checkin", "es")

        state.recordMood(DayClock.epochDay(), moodValue = 3, note = null)
        delay(20)
        state.recordMood(DayClock.epochDay(), moodValue = 4, note = null)
        delay(20)

        assertEquals(1, analytics.recorded.count { it is AnalyticsEvent.NotificationCompleted })
        scope.cancel()
    }
}
