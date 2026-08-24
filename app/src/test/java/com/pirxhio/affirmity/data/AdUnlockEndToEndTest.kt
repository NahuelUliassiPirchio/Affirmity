package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockSource
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.access.resolveAccess
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
import com.pirxhio.affirmity.data.local.GroupSelectionPreferences
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
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import com.pirxhio.affirmity.data.repository.AffirmationRepository
import com.pirxhio.affirmity.data.repository.DailyCompletionRepository
import com.pirxhio.affirmity.data.repository.DailyMoodRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.LocalFreeEntitlementRepository
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import com.pirxhio.affirmity.ui.groups.groupAccessDecision
import com.pirxhio.affirmity.ui.groups.isToggleable
import com.pirxhio.affirmity.ui.meditation.catalog.findMeditationCatalogEntry
import com.pirxhio.affirmity.ui.meditation.catalog.meditationAccessDecision
import com.pirxhio.affirmity.ui.meditation.catalog.meditationContentKey
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

// ---------------------------------------------------------------------------------------------
// Minimal, always-emit fakes -- this file never signs in and never exercises the Local<->Remote
// swap (that is AffirmityAppStateSwapTest's job), so none of these need EventedFlow-style
// collect/cancel instrumentation. [FakeAdUnlockRepository] is the one exception: it is reused
// as-is from AffirmityAppStateSwapTest.kt (task E.4), not duplicated here.
// ---------------------------------------------------------------------------------------------

private class NoopAffirmationRepository : AffirmationRepository {
    override fun observeAll(): Flow<List<AffirmationEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: AffirmationEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
}

private class NoopDailyCompletionRepository : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private class NoopDailyMoodRepository : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private class NoopStreakHealerRepository : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private class NoopMeditationPreferencesRepository : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(null)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private class NoopNotificationSettingsRepository : NotificationSettingsRepository {
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

private class AlwaysSignedOutAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(provider: AuthProviderId, activityContext: android.content.Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

/** Never invoked in this file -- every scenario stays signed out, so [AffirmityAppState] never
 *  promotes past [DataSession.Local]. */
private class NeverCalledFirestoreMigrationSource : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean =
        error("AdUnlockEndToEndTest scenarios stay signed-out; migration must never run")
    override suspend fun commitChunk(writes: List<DocWrite>) =
        error("AdUnlockEndToEndTest scenarios stay signed-out; migration must never run")
}

/** Test double for [AdUnlockSource] (task E.4): always resolves to the fixed [outcome], recording
 *  every request so a test can additionally assert what was asked for. */
private class FakeAdUnlockSource(private val outcome: AdUnlockOutcome) : AdUnlockSource {
    val requested = mutableListOf<Pair<ContentKey, AdUnlockPolicy>>()
    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome {
        requested += key to policy
        return outcome
    }
}

/**
 * Builds a fully-wired, always-signed-out [AffirmityAppState] whose only interesting collaborator
 * is the ad-unlock seam (design §9): [adUnlockSource] and [adUnlocks] are the two parameters every
 * scenario in this file actually varies.
 */
private fun buildState(
    scope: CoroutineScope,
    adUnlockSource: AdUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Unavailable),
    adUnlocks: AdUnlockRepository = FakeAdUnlockRepository(),
    groupPreferences: GroupSelectionPreferences = FakeGroupSelectionPreferences(),
    knownGroupIds: Set<String> = setOf("personalizadas", "bienestar", "autocuidado", "fuerza_de_voluntad"),
): AffirmityAppState {
    val local = DataSession.Local(
        affirmations = NoopAffirmationRepository(),
        completions = NoopDailyCompletionRepository(),
        moods = NoopDailyMoodRepository(),
        healerUses = NoopStreakHealerRepository(),
        meditation = NoopMeditationPreferencesRepository(),
        notifications = NoopNotificationSettingsRepository(),
        adUnlocks = adUnlocks,
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
                affirmations = NoopAffirmationRepository(),
                completions = NoopDailyCompletionRepository(),
                moods = NoopDailyMoodRepository(),
                healerUses = NoopStreakHealerRepository(),
                meditation = NoopMeditationPreferencesRepository(),
                notifications = NoopNotificationSettingsRepository(),
                entitlements = LocalFreeEntitlementRepository(),
                adUnlocks = adUnlocks,
            )
        },
        migrator = FirestoreMigrator(NeverCalledFirestoreMigrationSource()),
        trackerPreferences = trackerPreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = AlwaysSignedOutAuthRepository(),
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        deviceTimeZoneId = { "UTC" },
        groupPreferences = groupPreferences,
        knownGroupIds = knownGroupIds,
        defaultThematicGroupIds = setOf("bienestar"),
        proOnlyGroupIds = setOf("autocuidado", "fuerza_de_voluntad"),
        adUnlockSource = adUnlockSource,
    )
}

/**
 * The seam's end-to-end acceptance test (design §9, spec §11): every scenario runs with ZERO ad
 * SDK in the build, proving the whole path -- [com.pirxhio.affirmity.access.AdUnlockSource] ->
 * [AffirmityAppState.requestAdUnlock] -> [AffirmityAppState.adUnlockState] ->
 * [groupAccessDecision] -- is correct using only [FakeAdUnlockSource] and [FakeAdUnlockRepository].
 */
class AdUnlockEndToEndTest {

    private val fuerzaDeVoluntad = defaultAffirmationGroups().first { it.id == "fuerza_de_voluntad" }
    private val fuerzaKey = ContentKey(ContentType.AFFIRMATION_GROUP, fuerzaDeVoluntad.id)

    // 1. FREE tier, no grant -> LockedAdUnlockable(PER_USE), not toggleable.
    @Test
    fun `FREE tier with no grants -- fuerza_de_voluntad is locked but ad-unlockable`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(scope)
        delay(50)

        val decision = groupAccessDecision(
            fuerzaDeVoluntad, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), decision)
        assertFalse(isToggleable(fuerzaDeVoluntad, decision))

        scope.cancel()
    }

    // 2. requestAdUnlock via a FakeAdUnlockSource(Earned) -> UnlockedByAd(PER_USE), toggleable.
    @Test
    fun `requestAdUnlock with an Earned outcome unlocks fuerza_de_voluntad for PER_USE`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned)
        val state = buildState(scope, adUnlockSource = adUnlockSource)
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)

        val decision = groupAccessDecision(
            fuerzaDeVoluntad, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE), decision)
        assertTrue(isToggleable(fuerzaDeVoluntad, decision))
        assertEquals(listOf(fuerzaKey to AdUnlockPolicy.PER_USE), adUnlockSource.requested)

        scope.cancel()
    }

    // 3. toggleGroup + applyGroupSelection() commits the group correctly with the ad-unlocked item.
    @Test
    fun `toggling and committing the ad-unlocked group succeeds`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned)
        val groupPreferences = FakeGroupSelectionPreferences(initial = setOf("bienestar"))
        val state = buildState(scope, adUnlockSource = adUnlockSource, groupPreferences = groupPreferences)
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)
        val decision = groupAccessDecision(
            fuerzaDeVoluntad, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        state.toggleGroup(fuerzaDeVoluntad.id, isToggleable(fuerzaDeVoluntad, decision))
        val committed = state.applyGroupSelection()

        assertTrue("commit must succeed", committed)
        assertEquals(
            setOf("personalizadas", "bienestar", fuerzaDeVoluntad.id),
            state.selectedGroupIds.value,
        )

        scope.cancel()
    }

    // 4. Committing a selection WITHOUT the ad-unlocked group drops the grant via
    //    retainSelectionScopedUnlocks; decision reverts to LockedAdUnlockable(PER_USE).
    @Test
    fun `committing a selection without the ad-unlocked group drops the grant and re-locks it`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned)
        val groupPreferences = FakeGroupSelectionPreferences(initial = setOf("bienestar"))
        val state = buildState(scope, adUnlockSource = adUnlockSource, groupPreferences = groupPreferences)
        delay(50)

        state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)
        // Committing here without ever toggling fuerza_de_voluntad into the draft: the committed
        // set stays {personalizadas, bienestar}, so the grant is out-of-selection the moment it
        // commits.
        val committed = state.applyGroupSelection()
        assertTrue(committed)

        val decision = groupAccessDecision(
            fuerzaDeVoluntad, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), decision)

        scope.cancel()
    }

    // 5. Dismissed / Failed / Unavailable outcomes -> no grant recorded, decision unchanged.
    @Test
    fun `Dismissed, Failed and Unavailable outcomes never grant an unlock`() = runBlocking {
        listOf(
            AdUnlockOutcome.Dismissed,
            AdUnlockOutcome.Failed("no fill"),
            AdUnlockOutcome.Unavailable,
        ).forEach { outcome ->
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val adUnlockSource = FakeAdUnlockSource(outcome)
            val state = buildState(scope, adUnlockSource = adUnlockSource)
            delay(50)

            state.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
            delay(50)

            val decision = groupAccessDecision(
                fuerzaDeVoluntad, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
            )
            assertEquals(
                "outcome $outcome must never grant an unlock",
                AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE),
                decision,
            )
            scope.cancel()
        }
    }

    // 6. ONE_TIME_TRIAL over a FakeAdUnlockRepository: grant, then a FRESH AffirmityAppState over
    //    the SAME fake repository (simulated restart) is still UnlockedByAd(ONE_TIME_TRIAL) --
    //    durable. Contrast: a PER_USE grant over a fresh state reverts to locked -- session-scoped.
    @Test
    fun `ONE_TIME_TRIAL grants durably survive a process restart, unlike PER_USE`() = runBlocking {
        val sharedAdUnlocks = FakeAdUnlockRepository()
        val trialKey = ContentKey(ContentType.MEDITATION, "calma")
        val trialContent = ContentAccess.ProOrAdTrial

        val scope1 = CoroutineScope(Dispatchers.Unconfined)
        val state1 = buildState(scope1, adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned), adUnlocks = sharedAdUnlocks)
        delay(50)
        state1.requestAdUnlock(trialKey, AdUnlockPolicy.ONE_TIME_TRIAL)
        delay(50)
        val decisionBeforeRestart = resolveAccess(
            trialKey, trialContent, state1.entitlementTier.value, state1.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL), decisionBeforeRestart)
        scope1.cancel()

        // Simulated process restart: a brand new AffirmityAppState instance over the SAME fake
        // repository -- nothing about the previous process's in-memory state survives except what
        // was durably persisted.
        val scope2 = CoroutineScope(Dispatchers.Unconfined)
        val state2 = buildState(scope2, adUnlocks = sharedAdUnlocks)
        delay(50)
        val decisionAfterRestart = resolveAccess(
            trialKey, trialContent, state2.entitlementTier.value, state2.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(
            "a durable ONE_TIME_TRIAL grant must survive a process restart",
            AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL),
            decisionAfterRestart,
        )
        scope2.cancel()

        // Contrast: PER_USE over a fresh state -- session-scoped, dies with the process.
        val scope3 = CoroutineScope(Dispatchers.Unconfined)
        val state3 = buildState(scope3, adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned))
        delay(50)
        state3.requestAdUnlock(fuerzaKey, AdUnlockPolicy.PER_USE)
        delay(50)
        val decisionScope3 = groupAccessDecision(
            fuerzaDeVoluntad, state3.entitlementTier.value, state3.adUnlockState, System.currentTimeMillis(),
        )
        assertTrue("the PER_USE grant must be live in the process that earned it", decisionScope3.isUnlocked)
        scope3.cancel()

        val scope4 = CoroutineScope(Dispatchers.Unconfined)
        val state4 = buildState(scope4) // fresh process: no session-scoped grant survives
        delay(50)
        val decisionScope4 = groupAccessDecision(
            fuerzaDeVoluntad, state4.entitlementTier.value, state4.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(
            "a PER_USE grant must NOT survive a process restart -- it truly dies with the process",
            AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE),
            decisionScope4,
        )
        scope4.cancel()
    }

    // 7. enfoque (ProOrAdPerUse) full round trip: FREE -> LockedAdUnlockable(PER_USE) ->
    //    requestAdUnlock earns -> UnlockedByAd(PER_USE) -> consumeMeditationPlaybackUnlock(Completed)
    //    -> back to LockedAdUnlockable(PER_USE). First end-to-end proof of the playback-scoped
    //    binding this whole spec exists to make true (REQ-5.5, acceptance criterion 12).
    @Test
    fun `enfoque PER_USE round trip -- earned then consumed on session Completed re-locks it`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned)
        val state = buildState(scope, adUnlockSource = adUnlockSource)
        delay(50)
        val enfoque = findMeditationCatalogEntry("enfoque")!!

        val beforeEarn = meditationAccessDecision(
            enfoque, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), beforeEarn)

        state.requestAdUnlock(meditationContentKey(enfoque.id), AdUnlockPolicy.PER_USE)
        delay(50)
        val afterEarn = meditationAccessDecision(
            enfoque, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE), afterEarn)

        state.consumeMeditationPlaybackUnlock(enfoque.id, SessionEndReason.Completed)
        val afterConsume = meditationAccessDecision(
            enfoque, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(
            "a spent PER_USE session grant must re-lock the entry",
            AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE),
            afterConsume,
        )

        scope.cancel()
    }

    // 8. dormir (ProOrAdTrial) asymmetry: FREE -> LockedAdUnlockable(ONE_TIME_TRIAL) -> earn ->
    //    durable record persisted via the fake repo -> UnlockedByAd(ONE_TIME_TRIAL) -> session ends
    //    (consumeMeditationPlaybackUnlock(Completed)) -> STILL UnlockedByAd(ONE_TIME_TRIAL), because
    //    consumePlaybackScopedUnlock only ever touches sessionAdUnlocks (in-memory), never the
    //    durable grant -- a durable trial is spent once, ever, not once per session (EC-3,
    //    acceptance criterion 13). This is easy to get backwards; it MUST explicitly assert the
    //    grant SURVIVES a completed playback session.
    @Test
    fun `dormir ONE_TIME_TRIAL survives a completed playback session -- durable, not playback-scoped`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val adUnlockSource = FakeAdUnlockSource(AdUnlockOutcome.Earned)
        val sharedAdUnlocks = FakeAdUnlockRepository()
        val state = buildState(scope, adUnlockSource = adUnlockSource, adUnlocks = sharedAdUnlocks)
        delay(50)
        val dormir = findMeditationCatalogEntry("dormir")!!

        val beforeEarn = meditationAccessDecision(
            dormir, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.ONE_TIME_TRIAL), beforeEarn)

        state.requestAdUnlock(meditationContentKey(dormir.id), AdUnlockPolicy.ONE_TIME_TRIAL)
        delay(50)
        val afterEarn = meditationAccessDecision(
            dormir, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL), afterEarn)

        state.consumeMeditationPlaybackUnlock(dormir.id, SessionEndReason.Completed)
        val afterConsume = meditationAccessDecision(
            dormir, state.entitlementTier.value, state.adUnlockState, System.currentTimeMillis(),
        )
        assertEquals(
            "a durable ONE_TIME_TRIAL grant must survive a completed playback session -- it is " +
                "spent once, ever, not once per session",
            AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL),
            afterConsume,
        )

        scope.cancel()
    }
}
