package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.auth.AuthProviderId
import com.pirxhio.affirmity.auth.AuthRepository
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.local.DailyViewCount
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.FeedSources
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.OnboardingGuidePreferences
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.data.local.ThemeSelectionPreferences
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.DocWrite
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrationSource
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
import com.pirxhio.affirmity.data.repository.CatalogOverrideRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

private const val UNIVERSE_ID = "body_energy_wellbeing"
private const val THEME_ID = "body_energy_wellbeing.body_acceptance"

/** Covers the resolved implementation-risk item from `sdd/feed-randomize-order/tasks` T4 (
 * `isDraftThemeSelectionValid` must read the DRAFT feed sources, not the committed ones) and T5's
 * draft feed-sources state machine: pending toggles, commit-time persistence, seed generation
 * timing, and reset -- mirroring `AffirmityAppStateCatalogTest`'s Mockito helper pattern. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AffirmityAppStateFeedSourcesDraftTest {

    // --- T4: isDraftThemeSelectionValid reads the DRAFT, not the committed sources ------------

    @Test
    fun `validity reflects a draft toggle flipped off even though committed sources are still on`() = runTest {
        val state = buildState(
            backgroundScope,
            committedFeedSources = FeedSources(includeFavorites = true, includeOwn = false),
        )
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(includeFavorites = false, includeOwn = false))
        state.removeThemesInUniverse(UNIVERSE_ID) // ensure draftThemeIds is empty for this check

        assertFalse(state.isDraftThemeSelectionValid)
    }

    @Test
    fun `validity reflects a draft toggle flipped on even though committed sources are both off`() = runTest {
        val state = buildState(
            backgroundScope,
            committedFeedSources = FeedSources(includeFavorites = false, includeOwn = false),
        )
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(includeFavorites = false, includeOwn = true))
        state.removeThemesInUniverse(UNIVERSE_ID)

        assertTrue(state.isDraftThemeSelectionValid)
    }

    // --- T5: draft toggling never touches the committed/visible feed --------------------------

    @Test
    fun `toggling the draft does not change the committed feedSources`() = runTest {
        val state = buildState(backgroundScope, committedFeedSources = FeedSources(includeFavorites = false))
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(includeFavorites = true))

        assertFalse(state.feedSources.value.includeFavorites)
        assertTrue(state.draftFeedSources.value.includeFavorites)
    }

    // --- T5: commit applies the draft and persists it ------------------------------------------

    @Test
    fun `applyThemeSelection commits the draft feed sources and persists them`() = runTest {
        val trackerPreferences = mock(TrackerPreferences::class.java)
        val state = buildState(
            backgroundScope,
            committedFeedSources = FeedSources(includeFavorites = false, includeOwn = true),
            trackerPreferences = trackerPreferences,
        )
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(includeFavorites = true, includeOwn = true))
        state.applyThemeSelection()
        runCurrent()
        advanceUntilIdle()

        assertTrue(state.feedSources.value.includeFavorites)
        verify(trackerPreferences, times(1)).saveFeedSources(state.feedSources.value)
    }

    @Test
    fun `commit with randomizeOrder on calls the injected seed source and stores its result`() = runTest {
        val state = buildState(backgroundScope, feedSeedSource = { 42L })
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(randomizeOrder = true))
        state.applyThemeSelection()
        runCurrent()
        advanceUntilIdle()

        assertEquals(42L, state.feedSources.value.orderSeed)
    }

    @Test
    fun `commit with randomizeOrder off keeps the previous committed seed unchanged`() = runTest {
        val state = buildState(
            backgroundScope,
            committedFeedSources = FeedSources(randomizeOrder = false, orderSeed = 7L),
            feedSeedSource = { error("must not be called when randomizeOrder is off") },
        )
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(randomizeOrder = false, orderSeed = 7L, includeOwn = false))
        state.applyThemeSelection()
        runCurrent()
        advanceUntilIdle()

        assertEquals(7L, state.feedSources.value.orderSeed)
    }

    @Test
    fun `commit with randomizeOrder already on before and after still generates a new seed`() = runTest {
        var callCount = 0
        val state = buildState(
            backgroundScope,
            committedFeedSources = FeedSources(randomizeOrder = true, orderSeed = 1L),
            feedSeedSource = { callCount++; 2L },
        )
        runCurrent()
        advanceUntilIdle()

        // Toggle stayed on, but an unrelated theme change still triggers a commit.
        state.setDraftFeedSources(FeedSources(randomizeOrder = true, orderSeed = 1L))
        state.applyThemeSelection()
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, callCount)
        assertEquals(2L, state.feedSources.value.orderSeed)
    }

    // --- T5: reset discards pending draft edits --------------------------------------------------

    @Test
    fun `resetThemeDraftToCommitted discards pending draftFeedSources changes`() = runTest {
        val state = buildState(backgroundScope, committedFeedSources = FeedSources(includeFavorites = false))
        runCurrent()
        advanceUntilIdle()

        state.setDraftFeedSources(FeedSources(includeFavorites = true))
        state.resetThemeDraftToCommitted()

        assertFalse(state.draftFeedSources.value.includeFavorites)
    }

    // --- T5: cold start seeds the draft without generating a new seed -------------------------

    @Test
    fun `cold start seeds draftFeedSources from the persisted value without calling feedSeedSource`() = runTest {
        val state = buildState(
            backgroundScope,
            committedFeedSources = FeedSources(randomizeOrder = true, orderSeed = 99L),
            feedSeedSource = { error("must not be called on cold start, only on commit") },
        )
        runCurrent()
        advanceUntilIdle()

        assertEquals(99L, state.feedSources.value.orderSeed)
        assertEquals(99L, state.draftFeedSources.value.orderSeed)
        assertTrue(state.draftFeedSources.value.randomizeOrder)
    }

    @Test
    fun `isFeedDraftDirty is false with no pending changes and true once the draft diverges`() = runTest {
        val state = buildState(backgroundScope, committedFeedSources = FeedSources(randomizeOrder = false))
        runCurrent()
        advanceUntilIdle()

        assertFalse(state.isFeedDraftDirty)

        state.setDraftFeedSources(FeedSources(randomizeOrder = true))

        assertTrue(state.isFeedDraftDirty)
    }
}

private fun buildState(
    scope: CoroutineScope,
    committedFeedSources: FeedSources = FeedSources(),
    trackerPreferences: TrackerPreferences = mock(TrackerPreferences::class.java),
    feedSeedSource: () -> Long = { 0L },
): AffirmityAppState {
    whenever(trackerPreferences.observeAffirmationsViewedToday())
        .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
    whenever(trackerPreferences.observeHiddenAffirmationIds()).thenReturn(flowOf(emptySet()))
    whenever(trackerPreferences.observeFeedSources()).thenReturn(flowOf(committedFeedSources))
    val notificationDebugLog = mock(NotificationDebugLog::class.java)
    whenever(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
    val onboardingPreferences = mock(OnboardingPreferences::class.java)
    whenever(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))
    val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
    whenever(onboardingGuidePreferences.observeHasSeenGuide()).thenReturn(flowOf(true))
    val local = DataSession.Local(
        affirmations = EmptyAffirmationRepositoryDraft,
        completions = EmptyCompletionsRepositoryDraft,
        moods = EmptyMoodsRepositoryDraft,
        healerUses = EmptyHealerRepositoryDraft,
        meditation = EmptyMeditationRepositoryDraft,
        notifications = EmptyNotificationsRepositoryDraft,
        entitlements = FakeEntitlementRepositoryDraft(AccessTier.PRO),
        adUnlocks = com.pirxhio.affirmity.data.FakeAdUnlockRepository(),
        catalogOverrides = EmptyCatalogOverrideRepositoryDraft,
    )

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { error("Remote session is not used by these tests") },
        migrator = FirestoreMigrator(NoOpMigrationSourceDraft),
        trackerPreferences = trackerPreferences,
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = SignedOutAuthRepositoryDraft,
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
        themePreferences = FixedThemeSelectionPreferencesDraft(setOf(THEME_ID)),
        knownThemeIds = setOf(THEME_ID),
        defaultThematicThemeIds = emptySet(),
        favorites = EmptyFavoritesRepositoryDraft,
        catalog = EmptyCatalogAffirmationRepositoryDraft,
        catalogSeeder = null,
        useRemoteSession = false,
        feedSeedSource = feedSeedSource,
    )
}

private object EmptyAffirmationRepositoryDraft : com.pirxhio.affirmity.data.repository.AffirmationRepository {
    override fun observeAll(): Flow<List<com.pirxhio.affirmity.data.local.AffirmationEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: com.pirxhio.affirmity.data.local.AffirmationEntity) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private object EmptyCompletionsRepositoryDraft : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
    override suspend fun earliestEpochDay(): Long? = null
}

private object EmptyMoodsRepositoryDraft : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private object EmptyHealerRepositoryDraft : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private object EmptyMeditationRepositoryDraft : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(600)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private object EmptyNotificationsRepositoryDraft : NotificationSettingsRepository {
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

private object EmptyCatalogOverrideRepositoryDraft : CatalogOverrideRepository {
    override fun observeAll(): Flow<Map<String, Map<String, String>>> = flowOf(emptyMap())
    override suspend fun setOverrides(catalogAffirmationId: String, overrides: Map<String, String>) = Unit
}

private object EmptyCatalogAffirmationRepositoryDraft : CatalogAffirmationRepository {
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<com.pirxhio.affirmity.data.local.CatalogAffirmationEntity>> =
        flowOf(emptyList())
    override suspend fun getByIds(ids: List<String>): List<com.pirxhio.affirmity.data.local.CatalogAffirmationEntity> = emptyList()
}

private object EmptyFavoritesRepositoryDraft : FavoriteAffirmationRepository {
    override fun observeFavoriteIds(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun isFavorite(id: String): Boolean = false
    override suspend fun add(id: String, favoritedAtMillis: Long) = Unit
    override suspend fun remove(id: String) = Unit
    override suspend fun clear() = Unit
}

private class FakeEntitlementRepositoryDraft(private val tier: AccessTier) : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flowOf(Entitlement(tier))
}

private class FixedThemeSelectionPreferencesDraft(private val ids: Set<String>) : ThemeSelectionPreferences {
    override fun observeSelectedThemeIds(): Flow<Set<String>?> = flowOf(ids)
    override suspend fun saveSelectedThemeIds(ids: Set<String>) = Unit
}

private object SignedOutAuthRepositoryDraft : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(
        provider: AuthProviderId,
        activityContext: android.content.Context,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun signOut() = Unit
}

private object NoOpMigrationSourceDraft : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = true
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}
