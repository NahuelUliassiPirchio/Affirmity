package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
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
import com.pirxhio.affirmity.data.local.ThemeSelectionPreferences
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
import com.pirxhio.affirmity.data.repository.Entitlement
import com.pirxhio.affirmity.data.repository.EntitlementRepository
import com.pirxhio.affirmity.data.repository.LocalFreeEntitlementRepository
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import com.pirxhio.affirmity.ui.myaffirmations.isCustomAffirmationCreationLocked
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

/**
 * A never-completing Flow (mirrors a real Room `Flow`, which stays subscribed until cancelled)
 * that records collection start/cancellation into a shared, thread-safe [events] log so tests can
 * assert ordering across the swap (design.md's "The swap moment").
 */
private class EventedFlow<T>(
    private val label: String,
    private val events: MutableList<String>,
    private val values: List<T>,
) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        events += "$label:collect"
        try {
            values.forEach { collector.emit(it) }
            awaitCancellation()
        } finally {
            events += "$label:cancel"
        }
    }
}

private class FakeAffirmationRepository(
    private val flow: Flow<List<AffirmationEntity>>,
) : AffirmationRepository {
    val inserted = CopyOnWriteArrayList<AffirmationEntity>()
    var deleteAllCallCount = 0
        private set
    override fun observeAll(): Flow<List<AffirmationEntity>> = flow
    override suspend fun insert(entity: AffirmationEntity) {
        inserted += entity
    }
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() {
        deleteAllCallCount++
    }
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private class FakeDailyCompletionRepository(
    private val flow: Flow<List<DailyCompletionEntity>> = EventedFlow("completions", mutableListOf(), listOf(emptyList())),
) : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flow
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private class FakeDailyMoodRepository(
    private val flow: Flow<List<DailyMoodEntity>> = EventedFlow("moods", mutableListOf(), listOf(emptyList())),
) : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flow
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private class FakeStreakHealerRepository(
    private val flow: Flow<List<StreakHealerUseEntity>> = EventedFlow("healerUses", mutableListOf(), listOf(emptyList())),
) : StreakHealerRepository {
    val recorded = CopyOnWriteArrayList<Long>()
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flow
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) {
        recorded += healedEpochDay
    }
}

private class FakeMeditationPreferencesRepository(
    private val flow: Flow<Int?>,
) : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flow
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private class FakeNotificationSettingsRepository(
    private val flow: Flow<ChannelSettings>,
) : NotificationSettingsRepository {
    override fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> = flow
    override suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) = Unit
    override suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) = Unit
    override fun observeQuietHours(): Flow<QuietHoursSettings> =
        flowOf(QuietHoursSettings(enabled = false, startMinute = 1380, endMinute = 420))
    override suspend fun setQuietHoursEnabled(enabled: Boolean) = Unit
    override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) = Unit
    override suspend fun setTimeZone(zoneId: String) = Unit
}

/** Fake [ThemeSelectionPreferences] — the real class needs an Android [Context], so JVM tests
 * that construct [AffirmityAppState] directly must fake this narrow interface (design risk #4).
 * Deliberately NOT `private`: reused as-is by [com.pirxhio.affirmity.data.AdUnlockEndToEndTest]. */
class FakeThemeSelectionPreferences(
    initial: Set<String>? = null,
) : ThemeSelectionPreferences {
    private val flow = MutableStateFlow(initial)
    val saved = CopyOnWriteArrayList<Set<String>>()
    override fun observeSelectedThemeIds(): Flow<Set<String>?> = flow
    override suspend fun saveSelectedThemeIds(ids: Set<String>) {
        saved += ids
        flow.value = ids
    }
}

/** Fake [EntitlementRepository] that always reports Free — none of the swap-lifecycle tests in
 * this file exercise entitlement transitions (covered separately). */
private class FakeEntitlementRepository(
    private val flow: Flow<Entitlement> = flowOf(Entitlement.Free),
) : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flow
}

/** Fake [AdUnlockRepository] mirroring the real repositories' create-if-absent semantics
 * (design §8/§10 EC-1): a second [grantDurableUnlock] call for an already-present key is a
 * silent no-op, never an overwrite. [granted] records every call that actually inserted a row,
 * so tests can assert reconciliation-replay idempotency.
 *
 * Deliberately NOT `private`: reused as-is by [com.pirxhio.affirmity.data.AdUnlockEndToEndTest]
 * (task E.4) rather than duplicated. */
class FakeAdUnlockRepository(
    initial: List<AdUnlockRecord> = emptyList(),
    initialTimed: List<AdUnlockRecord> = emptyList(),
) : AdUnlockRepository {
    private val state = MutableStateFlow(initial)
    private val timedState = MutableStateFlow(initialTimed)
    val granted = CopyOnWriteArrayList<AdUnlockRecord>()
    val timedGranted = CopyOnWriteArrayList<AdUnlockRecord>()
    override fun observeDurableUnlocks(): Flow<List<AdUnlockRecord>> = state
    override suspend fun getDurableUnlocks(): List<AdUnlockRecord> = state.value
    override suspend fun grantDurableUnlock(record: AdUnlockRecord) {
        if (state.value.none { it.key == record.key }) {
            state.value = state.value + record
            granted += record
        }
    }

    override fun observeTimedUnlocks(): Flow<List<AdUnlockRecord>> = timedState

    /** UPSERT, unlike [grantDurableUnlock] -- mirrors the real repositories' TIMED_REPEATABLE
     *  semantics (design D16). */
    override suspend fun grantTimedUnlock(record: AdUnlockRecord) {
        timedState.value = timedState.value.filterNot { it.key == record.key } + record
        timedGranted += record
    }
}

private class FakeAuthRepository(
    initial: AuthState = AuthState.SignedOut,
) : AuthRepository {
    private val flow = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = flow
    fun emit(state: AuthState) { flow.value = state }
    override suspend fun signIn(provider: AuthProviderId, activityContext: android.content.Context): Result<Unit> =
        Result.success(Unit)
    override suspend fun signOut() = Unit
}

/** A [FirestoreMigrationSource] whose [markerExists] suspends on [gate] — lets a test hold the
 * session in [DataSession.Migrating] until it chooses to release it. */
private class GatedFirestoreMigrationSource(private val gate: CompletableDeferred<Unit>) : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean {
        gate.await()
        return true
    }
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}

private class FailingFirestoreMigrationSource(private val error: Throwable) : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = throw error
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}

private class ImmediateFirestoreMigrationSource : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = true
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}

private fun fakeLocal(
    events: MutableList<String>,
    id: String = "local-1",
    adUnlocks: AdUnlockRepository = FakeAdUnlockRepository(),
    seededAffirmations: List<AffirmationEntity> = listOf(affirmation(id)),
    entitlements: EntitlementRepository = LocalFreeEntitlementRepository(),
): DataSession.Local = DataSession.Local(
    affirmations = FakeAffirmationRepository(
        EventedFlow("local-affirmations", events, listOf(seededAffirmations)),
    ),
    completions = FakeDailyCompletionRepository(EventedFlow("local-completions", events, listOf(emptyList()))),
    moods = FakeDailyMoodRepository(EventedFlow("local-moods", events, listOf(emptyList()))),
    healerUses = FakeStreakHealerRepository(EventedFlow("local-healerUses", events, listOf(emptyList()))),
    entitlements = entitlements,
    meditation = FakeMeditationPreferencesRepository(EventedFlow("local-meditation", events, listOf(600))),
    notifications = FakeNotificationSettingsRepository(
        EventedFlow("local-notifications", events, listOf(ChannelSettings(enabled = false, segments = setOf(DaySegment.MANANA, DaySegment.TARDE)))),
    ),
    adUnlocks = adUnlocks,
)

private fun fakeRemote(
    uid: String,
    events: MutableList<String>,
    id: String = "remote-1",
    adUnlocks: AdUnlockRepository = FakeAdUnlockRepository(),
    entitlements: EntitlementRepository = FakeEntitlementRepository(),
): DataSession.Remote = DataSession.Remote(
    uid = uid,
    affirmations = FakeAffirmationRepository(
        EventedFlow("remote-affirmations", events, listOf(listOf(affirmation(id)))),
    ),
    completions = FakeDailyCompletionRepository(EventedFlow("remote-completions", events, listOf(emptyList()))),
    moods = FakeDailyMoodRepository(EventedFlow("remote-moods", events, listOf(emptyList()))),
    healerUses = FakeStreakHealerRepository(EventedFlow("remote-healerUses", events, listOf(emptyList()))),
    meditation = FakeMeditationPreferencesRepository(EventedFlow("remote-meditation", events, listOf(600))),
    notifications = FakeNotificationSettingsRepository(
        EventedFlow("remote-notifications", events, listOf(ChannelSettings(enabled = false, segments = setOf(DaySegment.MANANA, DaySegment.TARDE)))),
    ),
    entitlements = entitlements,
    adUnlocks = adUnlocks,
)

private fun affirmation(id: String) = AffirmationEntity(
    id = id,
    title = "t-$id",
    subtitle = "s-$id",
    backgroundType = "color",
    backgroundValue = "#000000",
)

/** Builds a fully-wired [AffirmityAppState] with every non-[DataSession] collaborator mocked out —
 * the swap logic under test lives entirely in [AffirmityAppState]'s `session`/`ready()` wiring. */
private fun buildState(
    local: DataSession.Local,
    remote: () -> DataSession.Remote,
    migrator: FirestoreMigrator,
    authRepository: AuthRepository,
    scope: CoroutineScope,
    themePreferences: ThemeSelectionPreferences = FakeThemeSelectionPreferences(),
    knownThemeIds: Set<String> = setOf("u1.t1"),
    defaultThematicThemeIds: Set<String> = setOf("u1.t1"),
    proOnlyThemeIds: Set<String> = emptySet(),
    onboardingPreferencesOverride: OnboardingPreferences? = null,
    onboardingGuidePreferencesOverride: OnboardingGuidePreferences? = null,
): AffirmityAppState {
    val trackerPreferences = mock(TrackerPreferences::class.java)
    whenever(trackerPreferences.observeAffirmationsViewedToday())
        .thenReturn(EventedFlow("tracker-viewed", mutableListOf(), listOf(DailyViewCount(epochDay = -1L, count = 0))))
    val notificationDebugLog = mock(NotificationDebugLog::class.java)
    whenever(notificationDebugLog.entries)
        .thenReturn(EventedFlow("debug-log", mutableListOf(), listOf(emptyList())))
    val notifier = mock(Notifier::class.java)
    val imageStore = mock(AffirmationImageStore::class.java)
    val fcmTokenRepository = mock(FcmTokenRepository::class.java)
    val onboardingPreferences = onboardingPreferencesOverride ?: mock(OnboardingPreferences::class.java).also {
        whenever(it.observeHasCompletedOnboarding())
            .thenReturn(EventedFlow("onboarding-completed", mutableListOf(), listOf(true)))
    }
    val onboardingGuidePreferences = onboardingGuidePreferencesOverride ?: mock(OnboardingGuidePreferences::class.java).also {
        whenever(it.observeHasSeenGuide())
            .thenReturn(EventedFlow("onboarding-guide-seen", mutableListOf(), listOf(true)))
    }

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { remote() },
        migrator = migrator,
        trackerPreferences = trackerPreferences,
        imageStore = imageStore,
        notificationDebugLog = notificationDebugLog,
        notifier = notifier,
        widgetUpdater = WidgetUpdater { },
        authRepository = authRepository,
        fcmTokenRepository = fcmTokenRepository,
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        deviceTimeZoneId = { "UTC" },
        themePreferences = themePreferences,
        knownThemeIds = knownThemeIds,
        defaultThematicThemeIds = defaultThematicThemeIds,
        proOnlyThemeIds = proOnlyThemeIds,
    )
}

class AffirmityAppStateSwapTest {

    @Test
    fun `sign-in cancels the in-flight Room collector before subscribing to Firestore`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events)
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        buildState(
            local = local,
            remote = { fakeRemote("uid-1", events) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
        )
        delay(50)
        assertTrue(events.contains("local-affirmations:collect"))

        authRepository.emit(AuthState.SignedIn(uid = "uid-1", displayName = null, email = null))
        delay(200)

        val cancelIndex = events.indexOf("local-affirmations:cancel")
        val remoteCollectIndex = events.indexOf("remote-affirmations:collect")
        assertTrue("local Room collector must be cancelled ($events)", cancelIndex >= 0)
        assertTrue("Firestore fake must be subscribed after cancellation ($events)", remoteCollectIndex >= 0)
        assertTrue("cancellation must happen before the Firestore subscription", cancelIndex < remoteCollectIndex)

        scope.cancel()
    }

    @Test
    fun `writes issued while migrating land in the remote fake, never the Room fake`() = runBlocking {
        val events = mutableListOf<String>()
        // Entitlements during Migrating delegate to the LOCAL session (design §9's documented
        // residual risk), independent of Spec 4's creation guard under test here -- this test
        // exercises write ROUTING, not access control, so it stays PRO throughout to avoid the
        // guard interfering with its actual purpose.
        val local = fakeLocal(events, entitlements = FakeEntitlementRepository(flowOf(Entitlement(tier = AccessTier.PRO))))
        var remoteInstance: DataSession.Remote? = null
        val gate = CompletableDeferred<Unit>()
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(
            local = local,
            remote = {
                fakeRemote(
                    "uid-2",
                    events,
                    entitlements = FakeEntitlementRepository(flowOf(Entitlement(tier = AccessTier.PRO))),
                ).also { remoteInstance = it }
            },
            migrator = FirestoreMigrator(GatedFirestoreMigrationSource(gate)),
            authRepository = authRepository,
            scope = scope,
        )
        delay(50)

        authRepository.emit(AuthState.SignedIn(uid = "uid-2", displayName = null, email = null))
        delay(50)

        // Session is Migrating: the write is issued now but must suspend in ready().
        state.addAffirmationWithColor("t", "s", "#111111")
        delay(50)
        val localRepo = local.affirmations as FakeAffirmationRepository
        assertEquals("write must not land in Room while migrating", 0, localRepo.inserted.size)

        gate.complete(Unit)
        delay(200)

        val remoteRepo = remoteInstance?.affirmations as? FakeAffirmationRepository
        assertNotNull(remoteRepo)
        assertEquals(1, remoteRepo!!.inserted.size)
        assertEquals(0, localRepo.inserted.size)

        scope.cancel()
    }

    @Test
    fun `a migration failure keeps the session Local and sets syncError`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-only")
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-3", events, id = "remote-only") },
            migrator = FirestoreMigrator(FailingFirestoreMigrationSource(IllegalStateException("boom"))),
            authRepository = authRepository,
            scope = scope,
        )
        delay(50)
        assertNull(state.syncError.value)

        authRepository.emit(AuthState.SignedIn(uid = "uid-3", displayName = null, email = null))
        delay(200)

        assertNotNull("a failed migration must set syncError", state.syncError.value)
        assertTrue(
            "the session must stay Local — affirmations must still be the Room fake's",
            state.affirmations.any { it.id == "local-only" },
        )
        assertTrue(state.affirmations.none { it.id == "remote-only" })

        scope.cancel()
    }

    @Test
    fun `sign-out cancels the Firestore listener and resumes the stale Room snapshot`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-stale")
        val authRepository = FakeAuthRepository(initial = AuthState.SignedIn(uid = "uid-4", displayName = null, email = null))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        buildState(
            local = local,
            remote = { fakeRemote("uid-4", events, id = "remote-fresh") },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
        )
        delay(200)
        assertTrue(events.contains("remote-affirmations:collect"))

        authRepository.emit(AuthState.SignedOut)
        delay(200)

        val remoteCancelIndex = events.indexOf("remote-affirmations:cancel")
        val localRecollectIndex = events.lastIndexOf("local-affirmations:collect")
        assertTrue("Firestore listener must be cancelled on sign-out", remoteCancelIndex >= 0)
        assertTrue("Room must be resubscribed after sign-out", localRecollectIndex >= 0)

        scope.cancel()
    }

    @Test
    fun `a session swap preserves the committed theme selection and filteredAffirmations reflects the new session`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-swap")
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // Both fixture affirmations below default to groupId=personalizadas (OWNED), which is now
        // unconditionally included in `filteredAffirmations` regardless of theme selection (scope
        // decision #2) -- this test verifies session-swap correctness purely via that inclusion,
        // so the theme selection itself can be an arbitrary non-empty synthetic set.
        val themePreferences = FakeThemeSelectionPreferences(initial = setOf("u1.t1"))
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-5", events, id = "remote-swap") },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
            themePreferences = themePreferences,
        )
        delay(50)
        assertEquals(setOf("u1.t1"), state.selectedThemeIds.value)
        assertTrue(state.filteredAffirmations.any { it.id == "local-swap" })

        authRepository.emit(AuthState.SignedIn(uid = "uid-5", displayName = null, email = null))
        delay(200)

        assertEquals(setOf("u1.t1"), state.selectedThemeIds.value)
        assertTrue(state.filteredAffirmations.any { it.id == "remote-swap" })
        assertTrue(state.filteredAffirmations.none { it.id == "local-swap" })

        scope.cancel()
    }

    @Test
    fun `sign-in replays local durable ad-unlocks into the remote repository, create-if-absent`() = runBlocking {
        val events = mutableListOf<String>()
        val onlyLocalRecord = AdUnlockRecord(
            key = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad"),
            grantedAtMillis = 1_000L,
        )
        val alreadyOnRemoteKey = ContentKey(ContentType.MEDITATION, "calma")
        val remoteOriginal = AdUnlockRecord(key = alreadyOnRemoteKey, grantedAtMillis = 999L)
        val localAdUnlocks = FakeAdUnlockRepository(
            listOf(onlyLocalRecord, AdUnlockRecord(key = alreadyOnRemoteKey, grantedAtMillis = 5_000L)),
        )
        val remoteAdUnlocks = FakeAdUnlockRepository(listOf(remoteOriginal))
        val local = fakeLocal(events, adUnlocks = localAdUnlocks)
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        buildState(
            local = local,
            remote = { fakeRemote("uid-6", events, adUnlocks = remoteAdUnlocks) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
        )
        delay(50)

        authRepository.emit(AuthState.SignedIn(uid = "uid-6", displayName = null, email = null))
        delay(200)

        val remoteRecords = remoteAdUnlocks.getDurableUnlocks()
        assertTrue(
            "the local-only record must be replayed into the remote repository",
            remoteRecords.any { it.key == onlyLocalRecord.key && it.grantedAtMillis == onlyLocalRecord.grantedAtMillis },
        )
        val alreadyPresentRecord = remoteRecords.first { it.key == alreadyOnRemoteKey }
        assertEquals(
            "an already-present remote record must never be overwritten by the replay",
            999L,
            alreadyPresentRecord.grantedAtMillis,
        )
        assertEquals(
            "local-only record must be the only one that actually inserted (idempotent replay)",
            1,
            remoteAdUnlocks.granted.size,
        )

        scope.cancel()
    }

    @Test
    fun `a cold-start FREE-resolved emission deselects a stale Pro-only theme with no live transition`() = runBlocking {
        // EC-2(iv)/REQ-9.6 (design §10 Q4(iv), spec §9), theme-level equivalent: before this fix,
        // the deselect sweep only fired on a LIVE Pro->Free transition (entitlementFlowInitialized
        // == true). A signed-out cold start's FIRST entitlement emission never satisfies that
        // guard, so a Pro-only theme left over in a persisted selection -- from a lapse that
        // happened while the app was closed, or a dead PER_USE grant -- silently survived forever.
        // This is a DELIBERATE, documented behavior fix (not a regression): the sweep must now
        // also run on this very first, non-transition emission whenever the resolved tier is FREE.
        val events = mutableListOf<String>()
        val local = fakeLocal(events) // entitlements defaults to LocalFreeEntitlementRepository (always Free)
        val authRepository = FakeAuthRepository() // stays SignedOut -- no live transition ever occurs
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val themePreferences = FakeThemeSelectionPreferences(
            initial = setOf("u1.t1", "u2.t2"),
        )
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-cold-start", events) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
            themePreferences = themePreferences,
            knownThemeIds = setOf("u1.t1", "u2.t2"),
            defaultThematicThemeIds = setOf("u1.t1"),
            proOnlyThemeIds = setOf("u2.t2"),
        )
        delay(200)

        assertEquals(
            "the stale Pro-only theme must be deselected and the minimum-thematic invariant" +
                " restored from defaultThematicThemeIds, even though no live transition occurred",
            setOf("u1.t1"),
            state.selectedThemeIds.value,
        )
        assertTrue(
            "the sweep's result must actually be persisted back",
            themePreferences.saved.any { it == setOf("u1.t1") },
        )
        assertFalse(
            "no live Pro->Free transition occurred, so no lapse notice should fire",
            state.proLapseNotice.value,
        )

        scope.cancel()
    }

    // --- Spec 4 (Custom Affirmations Pro): creation-surface guards ----------------------------

    /** Builds a signed-in, PRO-tier [AffirmityAppState] -- the swap completes to [DataSession.Remote]
     *  with a PRO-resolving [FakeEntitlementRepository], so [AffirmityAppState.entitlementTier]
     *  settles to [com.pirxhio.affirmity.access.AccessTier.PRO] once the entitlement flow emits.
     *  [onRemote] captures the constructed [DataSession.Remote] so a test can assert against its
     *  fake repositories. */
    private fun buildProState(
        events: MutableList<String>,
        scope: CoroutineScope,
        local: DataSession.Local = fakeLocal(events, id = "local-pro"),
        onRemote: (DataSession.Remote) -> Unit = {},
    ): AffirmityAppState {
        val authRepository = FakeAuthRepository(
            initial = AuthState.SignedIn(uid = "uid-pro", displayName = null, email = null),
        )
        return buildState(
            local = local,
            remote = {
                fakeRemote(
                    "uid-pro",
                    events,
                    id = "remote-pro",
                    entitlements = FakeEntitlementRepository(flowOf(Entitlement(tier = AccessTier.PRO))),
                ).also(onRemote)
            },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
        )
    }

    @Test
    fun `a FREE user's 3 add functions are true no-ops -- zero repo inserts, zero image-store calls`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-free")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-free", events) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = FakeAuthRepository(), // stays SignedOut -- Local session, FREE by default
            scope = scope,
        )
        delay(50)
        assertEquals(AccessTier.FREE, state.entitlementTier.value)

        state.addAffirmationWithColor("t", "s", "#111111")
        state.addAffirmationWithImage("t", "s", "https://example.com/a.jpg")
        state.addAffirmationWithGalleryImage("t", "s", mock(android.net.Uri::class.java))
        delay(50)

        val localRepo = local.affirmations as FakeAffirmationRepository
        assertEquals("a FREE user's creation attempts must insert nothing", 0, localRepo.inserted.size)

        scope.cancel()
    }

    @Test
    fun `a PRO user's addAffirmationWithColor is unaffected by the guard`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-pro-color")
        var remoteInstance: DataSession.Remote? = null
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildProState(events, scope, local) { remoteInstance = it }
        delay(200)
        assertEquals(AccessTier.PRO, state.entitlementTier.value)

        state.addAffirmationWithColor("t", "s", "#222222")
        delay(50)

        val remoteRepo = remoteInstance?.affirmations as? FakeAffirmationRepository
        assertNotNull(remoteRepo)
        assertEquals(
            "a PRO user's creation must be unaffected by the guard",
            1,
            remoteRepo!!.inserted.size,
        )

        scope.cancel()
    }

    @Test
    fun `a FREE user's importAffirmationsFromJson with replaceExisting=true never wipes the table`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-import-free")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-import-free", events) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = FakeAuthRepository(),
            scope = scope,
        )
        delay(50)
        assertEquals(AccessTier.FREE, state.entitlementTier.value)

        val json = """[{"title":"t","subtitle":"s","backgroundType":"color","backgroundValue":"#000000"}]"""
        state.importAffirmationsFromJson(json, replaceExisting = true)
        delay(50)

        val localRepo = local.affirmations as FakeAffirmationRepository
        assertEquals(
            "a blocked import must never call deleteAll(), even with replaceExisting=true",
            0,
            localRepo.deleteAllCallCount,
        )
        assertEquals("a blocked import must insert nothing", 0, localRepo.inserted.size)
        assertNotNull(
            "a blocked import must report the decline honestly",
            state.importAffirmationsError.value,
        )

        scope.cancel()
    }

    @Test
    fun `grandfathering -- a pre-seeded FREE user is still LockedNeedsPro, count is never consulted`() = runBlocking {
        val events = mutableListOf<String>()
        val seeded = (1..12).map { affirmation("seed-$it") }
        val local = fakeLocal(events, seededAffirmations = seeded)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-seed", events) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = FakeAuthRepository(),
            scope = scope,
        )
        delay(50)
        assertEquals(12, state.affirmations.size)
        assertTrue(
            "a FREE user with 12 pre-existing affirmations must still be locked -- the count is never read",
            isCustomAffirmationCreationLocked(state.customAffirmationCreateDecision),
        )

        scope.cancel()
    }

    @Test
    fun `grandfathering -- a pre-seeded PRO user is Unlocked, count is never consulted`() = runBlocking {
        val events = mutableListOf<String>()
        val seeded = (1..12).map { affirmation("seed-pro-$it") }
        val local = fakeLocal(events, seededAffirmations = seeded)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildProState(events, scope, local)
        delay(200)
        assertEquals(AccessTier.PRO, state.entitlementTier.value)
        assertFalse(
            "a PRO user with 12 pre-existing affirmations must be unlocked",
            isCustomAffirmationCreationLocked(state.customAffirmationCreateDecision),
        )

        scope.cancel()
    }

    // --- Spec R7.1: completeOnboarding() arms the guide as one atomic addition ----------------

    @Test
    fun `completeOnboarding arms the guide preferences without altering existing completion behavior`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-guide")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val onboardingPreferences = mock(OnboardingPreferences::class.java)
        whenever(onboardingPreferences.observeHasCompletedOnboarding())
            .thenReturn(EventedFlow("onboarding-completed", mutableListOf(), listOf(false)))
        val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
        whenever(onboardingGuidePreferences.observeHasSeenGuide())
            .thenReturn(EventedFlow("onboarding-guide-seen", mutableListOf(), listOf(null)))
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-guide", events) },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = FakeAuthRepository(),
            scope = scope,
            onboardingPreferencesOverride = onboardingPreferences,
            onboardingGuidePreferencesOverride = onboardingGuidePreferences,
        )
        delay(50)

        state.completeOnboarding()
        delay(50)

        org.mockito.Mockito.verify(onboardingGuidePreferences).arm()
        org.mockito.Mockito.verify(onboardingPreferences).setCompleted()
        assertTrue("hasCompletedOnboarding must still flip synchronously", state.hasCompletedOnboarding.value == true)

        scope.cancel()
    }
}
