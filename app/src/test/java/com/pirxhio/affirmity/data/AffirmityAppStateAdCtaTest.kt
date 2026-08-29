package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockSource
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
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
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

private class NoopAffirmationRepositoryForAdCta : AffirmationRepository {
    override fun observeAll(): Flow<List<AffirmationEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: AffirmationEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private class NoopDailyCompletionRepositoryForAdCta : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private class NoopDailyMoodRepositoryForAdCta : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private class NoopStreakHealerRepositoryForAdCta : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private class NoopMeditationPreferencesRepositoryForAdCta : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(null)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private class NoopNotificationSettingsRepositoryForAdCta : NotificationSettingsRepository {
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

private class AlwaysSignedOutAuthRepositoryForAdCta : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(provider: AuthProviderId, activityContext: android.content.Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

private class NeverCalledFirestoreMigrationSourceForAdCta : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = error("must never run -- stays signed out")
    override suspend fun commitChunk(writes: List<DocWrite>) = error("must never run -- stays signed out")
}

/** Configurable-outcome [AdUnlockSource] that can be driven by an external gate, so a test can
 *  hold [AffirmityAppState.requestAdUnlock] "in flight" indefinitely to assert
 *  [AffirmityAppState.adRequestInFlight]'s state while a request is pending. */
private class GatedAdUnlockSource(
    private val gate: CompletableDeferred<AdUnlockOutcome>,
) : AdUnlockSource {
    val requested = mutableListOf<Pair<ContentKey, AdUnlockPolicy>>()
    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome {
        requested += key to policy
        return gate.await()
    }
}

private fun buildAdCtaTestState(
    scope: CoroutineScope,
    adUnlockSource: AdUnlockSource,
): AffirmityAppState {
    val local = DataSession.Local(
        affirmations = NoopAffirmationRepositoryForAdCta(),
        completions = NoopDailyCompletionRepositoryForAdCta(),
        moods = NoopDailyMoodRepositoryForAdCta(),
        healerUses = NoopStreakHealerRepositoryForAdCta(),
        meditation = NoopMeditationPreferencesRepositoryForAdCta(),
        notifications = NoopNotificationSettingsRepositoryForAdCta(),
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
                affirmations = NoopAffirmationRepositoryForAdCta(),
                completions = NoopDailyCompletionRepositoryForAdCta(),
                moods = NoopDailyMoodRepositoryForAdCta(),
                healerUses = NoopStreakHealerRepositoryForAdCta(),
                meditation = NoopMeditationPreferencesRepositoryForAdCta(),
                notifications = NoopNotificationSettingsRepositoryForAdCta(),
                entitlements = LocalFreeEntitlementRepository(),
                adUnlocks = FakeAdUnlockRepository(),
            )
        },
        migrator = FirestoreMigrator(NeverCalledFirestoreMigrationSourceForAdCta()),
        trackerPreferences = trackerPreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = AlwaysSignedOutAuthRepositoryForAdCta(),
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        deviceTimeZoneId = { "UTC" },
        groupPreferences = FakeGroupSelectionPreferences(),
        knownGroupIds = setOf("personalizadas", "fuerza_de_voluntad"),
        defaultThematicGroupIds = emptySet(),
        proOnlyGroupIds = setOf("fuerza_de_voluntad"),
        adUnlockSource = adUnlockSource,
    )
}

/** [AffirmityAppState.adRequestInFlight] / [AffirmityAppState.adRequestNotice] (REQ-4.8/6.1,
 *  design D7): in-flight set/clear, one-shot notice consumed once, single-flight enforced across
 *  BOTH a meditation key and a group key -- the shared, app-wide slot. Uses a real (non-Unconfined)
 *  dispatcher so [requestAdUnlock]'s launched coroutine genuinely suspends, unlike
 *  [AdUnlockEndToEndTest] which deliberately runs everything inline. */
class AffirmityAppStateAdCtaTest {

    private val meditationKey = ContentKey(ContentType.MEDITATION, "enfoque")
    private val groupKey = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad")

    @Test
    fun `adRequestInFlight is set while a request is pending and cleared once it resolves`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val gate = CompletableDeferred<AdUnlockOutcome>()
        val state = buildAdCtaTestState(scope, GatedAdUnlockSource(gate))
        delay(50)

        assertNull(state.adRequestInFlight.value)
        state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
        delay(50)
        assertEquals(meditationKey, state.adRequestInFlight.value)

        gate.complete(AdUnlockOutcome.Earned)
        delay(50)
        assertNull("in-flight slot must clear once the request resolves", state.adRequestInFlight.value)

        scope.cancel()
    }

    @Test
    fun `outcome notice is populated once per request and consumable exactly once`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val gate = CompletableDeferred<AdUnlockOutcome>()
        val state = buildAdCtaTestState(scope, GatedAdUnlockSource(gate))
        delay(50)

        state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
        delay(50)
        gate.complete(AdUnlockOutcome.Dismissed)
        delay(50)

        assertEquals(AdRequestNotice.DISMISSED, state.adRequestNotice.value)
        state.acknowledgeAdRequestNotice()
        assertNull("notice must be consumable exactly once", state.adRequestNotice.value)

        scope.cancel()
    }

    @Test
    fun `Earned outcome produces an EARNED notice`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val gate = CompletableDeferred<AdUnlockOutcome>()
        val state = buildAdCtaTestState(scope, GatedAdUnlockSource(gate))
        delay(50)

        state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
        delay(50)
        gate.complete(AdUnlockOutcome.Earned)
        delay(50)

        assertEquals(AdRequestNotice.EARNED, state.adRequestNotice.value)
        scope.cancel()
    }

    @Test
    fun `Failed and Unavailable outcomes both produce an UNAVAILABLE notice`() = runBlocking {
        listOf(AdUnlockOutcome.Failed("no fill"), AdUnlockOutcome.Unavailable).forEach { outcome ->
            val scope = CoroutineScope(Dispatchers.Default)
            val gate = CompletableDeferred<AdUnlockOutcome>()
            val state = buildAdCtaTestState(scope, GatedAdUnlockSource(gate))
            delay(50)

            state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
            delay(50)
            gate.complete(outcome)
            delay(50)

            assertEquals(AdRequestNotice.UNAVAILABLE, state.adRequestNotice.value)
            scope.cancel()
        }
    }

    @Test
    fun `single-flight is enforced across a meditation key and a group key -- one shared slot`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val gate = CompletableDeferred<AdUnlockOutcome>()
        val source = GatedAdUnlockSource(gate)
        val state = buildAdCtaTestState(scope, source)
        delay(50)

        state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
        delay(50)
        assertEquals(meditationKey, state.adRequestInFlight.value)

        // A tap on the OTHER surface's key while the meditation request is in flight must be
        // ignored outright -- never reach the source, never change the in-flight key.
        state.requestAdUnlock(groupKey, AdUnlockPolicy.PER_USE)
        delay(50)
        assertEquals(
            "the group tap must be ignored while the meditation request is in flight",
            meditationKey,
            state.adRequestInFlight.value,
        )
        assertEquals(listOf(meditationKey to AdUnlockPolicy.PER_USE), source.requested)
        assertTrue(source.requested.none { it.first == groupKey })

        gate.complete(AdUnlockOutcome.Earned)
        scope.cancel()
    }

    @Test
    fun `a second tap on the same key while in flight is ignored (no duplicate request)`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val gate = CompletableDeferred<AdUnlockOutcome>()
        val source = GatedAdUnlockSource(gate)
        val state = buildAdCtaTestState(scope, source)
        delay(50)

        state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
        delay(50)
        state.requestAdUnlock(meditationKey, AdUnlockPolicy.PER_USE)
        delay(50)

        assertEquals(1, source.requested.size)
        gate.complete(AdUnlockOutcome.Earned)
        scope.cancel()
    }
}
