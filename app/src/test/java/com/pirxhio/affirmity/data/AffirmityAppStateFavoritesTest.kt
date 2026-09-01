package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
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
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
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
import com.pirxhio.affirmity.data.repository.FavoriteAffirmationRepository
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

/**
 * `advanceUntilIdle()` alone does not reliably pick up a `backgroundScope.launch` issued
 * immediately before it in the same test-body tick; an intervening `runCurrent()` forces the
 * scheduler to register the new coroutine first. Every action call below is followed by
 * `runCurrent()` then `advanceUntilIdle()` for that reason.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AffirmityAppStateFavoritesTest {
    @Test
    fun `toggleFavorite adds an unfavorited id with a current timestamp`() = runTest {
        val favorites = RecordingFavoritesRepository()
        val state = buildState(backgroundScope, favorites)
        runCurrent()
        val before = System.currentTimeMillis()

        state.toggleFavorite("affirmation-1")
        runCurrent()
        advanceUntilIdle()

        val added = favorites.added.single()
        assertEquals("affirmation-1", added.first)
        assertTrue(added.second >= before)
        assertTrue(added.second <= System.currentTimeMillis())
    }

    @Test
    fun `toggleFavorite removes an already favorited id`() = runTest {
        val favorites = RecordingFavoritesRepository(initialIds = listOf("affirmation-1"))
        val state = buildState(backgroundScope, favorites)
        runCurrent()

        state.toggleFavorite("affirmation-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("affirmation-1"), favorites.removed)
        assertTrue(favorites.added.isEmpty())
    }

    @Test
    fun `duplicate removeFavorite callbacks remain remove-only and never re-add`() = runTest {
        val favorites = RecordingFavoritesRepository(initialIds = listOf("affirmation-1"))
        val state = buildState(backgroundScope, favorites)
        runCurrent()

        state.removeFavorite("affirmation-1")
        state.removeFavorite("affirmation-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("affirmation-1", "affirmation-1"), favorites.removed)
        assertTrue(favorites.added.isEmpty())
    }

    @Test
    fun `concurrent toggles serialize add then remove`() = runTest {
        val firstReadGate = CompletableDeferred<Unit>()
        val favorites = RecordingFavoritesRepository(firstReadGate = firstReadGate)
        val state = buildState(backgroundScope, favorites)
        runCurrent()

        state.toggleFavorite("affirmation-1")
        state.toggleFavorite("affirmation-1")
        runCurrent()

        assertEquals(1, favorites.isFavoriteCalls)
        firstReadGate.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "isFavorite:affirmation-1",
                "add:affirmation-1",
                "isFavorite:affirmation-1",
                "remove:affirmation-1",
            ),
            favorites.events,
        )
    }

    @Test
    fun `favoriteAffirmations follows repository recency order`() = runTest {
        val favorites = RecordingFavoritesRepository(initialIds = listOf("newest", "oldest"))
        val affirmations = RecordingAffirmationRepository(
            listOf(affirmationEntity("oldest"), affirmationEntity("newest")),
        )
        val state = buildState(backgroundScope, favorites, affirmations)
        runCurrent()

        assertEquals(listOf("newest", "oldest"), state.favoriteAffirmations.map { it.id })
    }

    @Test
    fun `favoriteAffirmations drops ids with no matching affirmation`() = runTest {
        val favorites = RecordingFavoritesRepository(initialIds = listOf("orphan", "kept"))
        val state = buildState(
            backgroundScope,
            favorites,
            RecordingAffirmationRepository(listOf(affirmationEntity("kept"))),
        )
        runCurrent()

        assertEquals(listOf("kept"), state.favoriteAffirmations.map { it.id })
    }

    @Test
    fun `an OWNED row is unconditionally in filteredAffirmations regardless of its groupId, and favoriteAffirmations is not filtered by theme selection either`() = runTest {
        // "Your feed" refactor scope decision #2: every OWNED row is now unconditionally included
        // in the feed -- `filteredAffirmations` no longer consults an OWNED row's groupId at all
        // (only CATALOG rows are theme-gated). This deliberately uses a non-personalizadas groupId
        // to prove that unconditional inclusion, which is the theme-level replacement for the old
        // group-level test's "not filtered by current group selection" premise.
        val favorites = RecordingFavoritesRepository(initialIds = listOf("outside"))
        val state = buildState(
            backgroundScope,
            favorites,
            RecordingAffirmationRepository(
                listOf(affirmationEntity("outside", groupId = "outside-selection")),
            ),
        )
        runCurrent()

        assertEquals(listOf("outside"), state.filteredAffirmations.map { it.id })
        assertEquals(listOf("outside"), state.favoriteAffirmations.map { it.id })
    }

    @Test
    fun `removeAffirmation deletes source before its favorite row`() = runTest {
        val calls = mutableListOf<String>()
        val favorites = RecordingFavoritesRepository(
            initialIds = listOf("affirmation-1"),
            sharedEvents = calls,
        )
        val affirmations = RecordingAffirmationRepository(
            initial = listOf(affirmationEntity("affirmation-1")),
            events = calls,
        )
        val state = buildState(backgroundScope, favorites, affirmations)
        runCurrent()

        state.removeAffirmation("affirmation-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf("affirmations.delete:affirmation-1", "remove:affirmation-1"),
            calls,
        )
    }

    @Test
    fun `replace import clears favorites after deleting all affirmations`() = runTest {
        val calls = mutableListOf<String>()
        val favorites = RecordingFavoritesRepository(sharedEvents = calls)
        val affirmations = RecordingAffirmationRepository(events = calls)
        val state = buildState(backgroundScope, favorites, affirmations)
        runCurrent()

        state.importAffirmationsFromJson(VALID_IMPORT_JSON, replaceExisting = true)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("affirmations.clear", "clear"), calls)
    }

    @Test
    fun `append import never clears favorites`() = runTest {
        val calls = mutableListOf<String>()
        val favorites = RecordingFavoritesRepository(sharedEvents = calls)
        val affirmations = RecordingAffirmationRepository(events = calls)
        val state = buildState(backgroundScope, favorites, affirmations)
        runCurrent()

        state.importAffirmationsFromJson(VALID_IMPORT_JSON, replaceExisting = false)
        runCurrent()
        advanceUntilIdle()

        assertTrue(calls.isEmpty())
    }

    private companion object {
        const val VALID_IMPORT_JSON =
            """[{"title":"Title","subtitle":"Subtitle","background":{"type":"color","value":"#000000"}}]"""
    }
}

private class RecordingFavoritesRepository(
    initialIds: List<String> = emptyList(),
    private val firstReadGate: CompletableDeferred<Unit>? = null,
    sharedEvents: MutableList<String> = mutableListOf(),
) : FavoriteAffirmationRepository {
    private val ids = MutableStateFlow(initialIds)
    val events = sharedEvents
    val added = mutableListOf<Pair<String, Long>>()
    val removed = mutableListOf<String>()
    var isFavoriteCalls: Int = 0

    override fun observeFavoriteIds(): Flow<List<String>> = ids

    override suspend fun isFavorite(id: String): Boolean {
        events += "isFavorite:$id"
        isFavoriteCalls += 1
        if (isFavoriteCalls == 1) firstReadGate?.await()
        return id in ids.value
    }

    override suspend fun add(id: String, favoritedAtMillis: Long) {
        events += "add:$id"
        added += id to favoritedAtMillis
        ids.value = listOf(id) + ids.value.filterNot { it == id }
    }

    override suspend fun remove(id: String) {
        events += "remove:$id"
        removed += id
        ids.value = ids.value.filterNot { it == id }
    }

    override suspend fun clear() {
        events += "clear"
        ids.value = emptyList()
    }
}

private fun buildState(
    scope: CoroutineScope,
    favorites: FavoriteAffirmationRepository,
    affirmations: RecordingAffirmationRepository = RecordingAffirmationRepository(),
): AffirmityAppState {
    val trackerPreferences = mock(TrackerPreferences::class.java)
    whenever(trackerPreferences.observeAffirmationsViewedToday())
        .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
    val notificationDebugLog = mock(NotificationDebugLog::class.java)
    whenever(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
    val onboardingPreferences = mock(OnboardingPreferences::class.java)
    whenever(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))
    val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
    whenever(onboardingGuidePreferences.observeHasSeenGuide()).thenReturn(flowOf(true))
    val local = DataSession.Local(
        affirmations = affirmations,
        completions = EmptyCompletionsRepository,
        moods = EmptyMoodsRepository,
        healerUses = EmptyHealerRepository,
        meditation = EmptyMeditationRepository,
        notifications = EmptyNotificationsRepository,
        entitlements = ProEntitlementRepository,
        adUnlocks = FakeAdUnlockRepository(),
    )

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { error("Remote session is not used by favorites tests") },
        migrator = FirestoreMigrator(NoOpMigrationSource),
        trackerPreferences = trackerPreferences,
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = SignedOutAuthRepository,
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        themePreferences = TestThemeSelectionPreferences,
        knownThemeIds = setOf("in-selection", "outside-selection"),
        defaultThematicThemeIds = emptySet(),
        favorites = favorites,
        useRemoteSession = false,
    )
}

private class RecordingAffirmationRepository(
    initial: List<AffirmationEntity> = emptyList(),
    private val events: MutableList<String> = mutableListOf(),
) : AffirmationRepository {
    private val entities = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<AffirmationEntity>> = entities
    override suspend fun insert(entity: AffirmationEntity) {
        entities.value = entities.value + entity
    }
    override suspend fun deleteById(id: String) {
        events += "affirmations.delete:$id"
        entities.value = entities.value.filterNot { it.id == id }
    }
    override suspend fun deleteAll() {
        events += "affirmations.clear"
        entities.value = emptyList()
    }
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private object EmptyCompletionsRepository : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private object EmptyMoodsRepository : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private object EmptyHealerRepository : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private object EmptyMeditationRepository : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(600)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private object EmptyNotificationsRepository : NotificationSettingsRepository {
    private val settings = ChannelSettings(enabled = false, segments = emptySet())
    override fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> = flowOf(settings)
    override suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) = Unit
    override suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) = Unit
    override fun observeQuietHours(): Flow<QuietHoursSettings> =
        flowOf(QuietHoursSettings(enabled = false, startMinute = 0, endMinute = 0))
    override suspend fun setQuietHoursEnabled(enabled: Boolean) = Unit
    override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) = Unit
    override suspend fun setTimeZone(zoneId: String) = Unit
}

private object ProEntitlementRepository : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flowOf(Entitlement(AccessTier.PRO))
}

private object SignedOutAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(
        provider: AuthProviderId,
        activityContext: android.content.Context,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun signOut() = Unit
}

private object TestThemeSelectionPreferences : ThemeSelectionPreferences {
    override fun observeSelectedThemeIds(): Flow<Set<String>?> =
        flowOf(setOf("in-selection"))
    override suspend fun saveSelectedThemeIds(ids: Set<String>) = Unit
}

private object NoOpMigrationSource : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = true
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}

private fun affirmationEntity(
    id: String,
    groupId: String = PERSONALIZADAS_GROUP_ID,
) = AffirmationEntity(
    id = id,
    title = "Title $id",
    subtitle = "Subtitle $id",
    backgroundType = "color",
    backgroundValue = "#000000",
    groupId = groupId,
)
