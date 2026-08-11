package com.pirxhio.affirmity.data

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
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
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
    override fun observeAll(): Flow<List<AffirmationEntity>> = flow
    override suspend fun insert(entity: AffirmationEntity) {
        inserted += entity
    }
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
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

/** Fake [GroupSelectionPreferences] — the real class needs an Android [Context], so JVM tests
 * that construct [AffirmityAppState] directly must fake this narrow interface (design risk #4). */
private class FakeGroupSelectionPreferences(
    initial: Set<String>? = null,
) : GroupSelectionPreferences {
    private val flow = MutableStateFlow(initial)
    val saved = CopyOnWriteArrayList<Set<String>>()
    override fun observeSelectedGroupIds(): Flow<Set<String>?> = flow
    override suspend fun saveSelectedGroupIds(ids: Set<String>) {
        saved += ids
        flow.value = ids
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

private fun fakeLocal(events: MutableList<String>, id: String = "local-1"): DataSession.Local = DataSession.Local(
    affirmations = FakeAffirmationRepository(
        EventedFlow("local-affirmations", events, listOf(listOf(affirmation(id)))),
    ),
    completions = FakeDailyCompletionRepository(EventedFlow("local-completions", events, listOf(emptyList()))),
    moods = FakeDailyMoodRepository(EventedFlow("local-moods", events, listOf(emptyList()))),
    healerUses = FakeStreakHealerRepository(EventedFlow("local-healerUses", events, listOf(emptyList()))),
    meditation = FakeMeditationPreferencesRepository(EventedFlow("local-meditation", events, listOf(600))),
    notifications = FakeNotificationSettingsRepository(
        EventedFlow("local-notifications", events, listOf(ChannelSettings(enabled = false, segments = setOf(DaySegment.MANANA, DaySegment.TARDE)))),
    ),
)

private fun fakeRemote(uid: String, events: MutableList<String>, id: String = "remote-1"): DataSession.Remote = DataSession.Remote(
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
    groupPreferences: GroupSelectionPreferences = FakeGroupSelectionPreferences(),
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
    val onboardingPreferences = mock(OnboardingPreferences::class.java)
    whenever(onboardingPreferences.observeHasCompletedOnboarding())
        .thenReturn(EventedFlow("onboarding-completed", mutableListOf(), listOf(true)))

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
        deviceTimeZoneId = { "UTC" },
        groupPreferences = groupPreferences,
        knownGroupIds = setOf("personalizadas", "bienestar"),
        defaultThematicGroupIds = setOf("bienestar"),
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
        val local = fakeLocal(events)
        var remoteInstance: DataSession.Remote? = null
        val gate = CompletableDeferred<Unit>()
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-2", events).also { remoteInstance = it } },
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
    fun `a session swap preserves the committed group selection and filteredAffirmations reflects the new session`() = runBlocking {
        val events = mutableListOf<String>()
        val local = fakeLocal(events, id = "local-swap")
        val authRepository = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val groupPreferences = FakeGroupSelectionPreferences(initial = setOf("bienestar"))
        val state = buildState(
            local = local,
            remote = { fakeRemote("uid-5", events, id = "remote-swap") },
            migrator = FirestoreMigrator(ImmediateFirestoreMigrationSource()),
            authRepository = authRepository,
            scope = scope,
            groupPreferences = groupPreferences,
        )
        delay(50)
        assertEquals(setOf("bienestar", "personalizadas"), state.selectedGroupIds.value)
        assertTrue(state.filteredAffirmations.any { it.id == "local-swap" })

        authRepository.emit(AuthState.SignedIn(uid = "uid-5", displayName = null, email = null))
        delay(200)

        assertEquals(setOf("bienestar", "personalizadas"), state.selectedGroupIds.value)
        assertTrue(state.filteredAffirmations.any { it.id == "remote-swap" })
        assertTrue(state.filteredAffirmations.none { it.id == "local-swap" })

        scope.cancel()
    }
}
