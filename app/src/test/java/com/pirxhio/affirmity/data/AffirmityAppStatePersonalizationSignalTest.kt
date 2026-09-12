package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.analytics.AnalyticsConsentState
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.ConsentGatedAnalyticsLogger
import com.pirxhio.affirmity.analytics.FakeAnalyticsLogger
import com.pirxhio.affirmity.auth.AuthProviderId
import com.pirxhio.affirmity.auth.AuthRepository
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
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
import com.pirxhio.affirmity.data.local.FeedSources
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.DocWrite
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrationSource
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.repository.AffirmationRepository
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
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
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignalRecorder
import com.pirxhio.affirmity.personalization.signal.SignalType
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

/** Real ids from the committed taxonomy (`ui/groups/CatalogTaxonomy.kt`), mirroring
 *  `AffirmityAppStateCatalogTest`'s convention of exercising the actual generated data instead of
 *  an invented fixture, so `catalogCollectionsById()[collectionId]?.themeId` resolves for real. */
private const val UNIVERSE_ID = "body_energy_wellbeing"
private const val THEME_ID = "body_energy_wellbeing.body_acceptance"
private const val FREE_COLLECTION_ID = "body_energy_wellbeing.body_acceptance.respect_my_body_today"

/**
 * Slice 2 (spec "personalization-signals — emission", design D3): every call site invokes the
 * injected [PersonalizationSignalRecorder] directly, functionally, and NEVER gated by analytics
 * consent.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AffirmityAppStatePersonalizationSignalTest {

    @Test
    fun `toggleFavorite on an owned affirmation records AFFIRMATION_SAVED with a null themeId`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val affirmations = PsRecordingAffirmationRepository(
            initial = listOf(
                AffirmationEntity(
                    id = "owned-1",
                    title = "Title",
                    subtitle = "Subtitle",
                    backgroundType = "color",
                    backgroundValue = "#000000",
                    groupId = PERSONALIZADAS_GROUP_ID,
                ),
            ),
        )
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder, affirmations = affirmations)
        runCurrent()

        state.toggleFavorite("owned-1")
        runCurrent()
        advanceUntilIdle()

        val signal = recorder.recorded.single()
        assertEquals(SignalType.AFFIRMATION_SAVED, signal.type)
        assertEquals(PERSONALIZADAS_GROUP_ID, signal.groupId)
        assertEquals(null, signal.themeId)
    }

    @Test
    fun `toggleFavorite on a catalog affirmation resolves themeId via its collectionId`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val catalog = PsFakeCatalogAffirmationRepository(
            listOf(
                CatalogAffirmationEntity(
                    id = "cat_1",
                    text = "Text",
                    subtitle = "Subtitle",
                    groupId = UNIVERSE_ID,
                    themeId = THEME_ID,
                    collectionId = FREE_COLLECTION_ID,
                    sortOrder = 1,
                ),
            ),
        )
        val state = buildState(
            backgroundScope,
            personalizationSignalRecorder = recorder,
            catalog = catalog,
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
        )
        runCurrent()

        state.toggleFavorite("cat_1")
        runCurrent()
        advanceUntilIdle()

        val signal = recorder.recorded.single()
        assertEquals(SignalType.AFFIRMATION_SAVED, signal.type)
        assertEquals(UNIVERSE_ID, signal.groupId)
        assertEquals(THEME_ID, signal.themeId)
    }

    @Test
    fun `unfavoriting an already-favorited affirmation records nothing`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val favorites = PsRecordingFavoritesRepository(initialIds = listOf("owned-1"))
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder, favorites = favorites)
        runCurrent()

        state.toggleFavorite("owned-1")
        runCurrent()
        advanceUntilIdle()

        assertTrue(recorder.recorded.isEmpty())
    }

    @Test
    fun `addAffirmationWithColor records AFFIRMATION_PERSONALIZED`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder)
        runCurrent()

        state.addAffirmationWithColor("Title", "Subtitle", "#000000")
        runCurrent()
        advanceUntilIdle()

        val signal = recorder.recorded.single()
        assertEquals(SignalType.AFFIRMATION_PERSONALIZED, signal.type)
        assertEquals(PERSONALIZADAS_GROUP_ID, signal.groupId)
        assertEquals(null, signal.themeId)
    }

    @Test
    fun `recordAffirmationShared records AFFIRMATION_SHARED for the shared affirmation`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder)
        runCurrent()

        state.recordAffirmationShared("owned-1")
        runCurrent()

        val signal = recorder.recorded.single()
        assertEquals(SignalType.AFFIRMATION_SHARED, signal.type)
    }

    @Test
    fun `recordSurfaceOpened records SURFACE_OPENED keyed by groupId, never themeId`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder)
        runCurrent()

        state.recordSurfaceOpened(UNIVERSE_ID)
        runCurrent()

        val signal = recorder.recorded.single()
        assertEquals(SignalType.SURFACE_OPENED, signal.type)
        assertEquals(UNIVERSE_ID, signal.groupId)
        assertEquals(null, signal.themeId)
    }

    @Test
    fun `applyThemeSelection records THEME_ADDED only for newly committed theme ids`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val themePreferences = PsFixedThemeSelectionPreferences(emptySet())
        val state = buildState(
            backgroundScope,
            personalizationSignalRecorder = recorder,
            themePreferences = themePreferences,
            knownThemeIds = setOf(THEME_ID),
        )
        runCurrent()

        state.toggleTheme(THEME_ID, toggleable = true)
        val committed = state.applyThemeSelection()
        runCurrent()
        advanceUntilIdle()

        assertTrue("commit must succeed", committed)
        val signal = recorder.recorded.single()
        assertEquals(SignalType.THEME_ADDED, signal.type)
        assertEquals(THEME_ID, signal.themeId)
        assertEquals(UNIVERSE_ID, signal.groupId)
    }

    @Test
    fun `committing a theme selection already containing the theme records nothing again`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val themePreferences = PsFixedThemeSelectionPreferences(setOf(THEME_ID))
        val state = buildState(
            backgroundScope,
            personalizationSignalRecorder = recorder,
            themePreferences = themePreferences,
            knownThemeIds = setOf(THEME_ID),
        )
        runCurrent()

        // No toggle -- draft already equals the committed set from `themePreferences` above.
        val committed = state.applyThemeSelection()
        runCurrent()
        advanceUntilIdle()

        assertTrue(committed)
        assertTrue(recorder.recorded.isEmpty())
    }

    @Test
    fun `analytics consent denied still records the personalization signal, with no AnalyticsEvent`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val analyticsDelegate = FakeAnalyticsLogger()
        val analytics = ConsentGatedAnalyticsLogger(
            delegate = analyticsDelegate,
            consentState = { AnalyticsConsentState.DENIED },
        )
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder, analytics = analytics)
        runCurrent()

        state.addAffirmationWithColor("Title", "Subtitle", "#000000")
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, recorder.recorded.size)
        assertTrue(
            "consent-gated analytics must suppress the event while the functional signal still records",
            analyticsDelegate.recorded.filterIsInstance<AnalyticsEvent.CustomAffirmationCreated>().isEmpty(),
        )
    }

    @Test
    fun `analytics consent granted emits both the AnalyticsEvent and the personalization signal`() = runTest {
        val recorder = FakePersonalizationSignalRecorder()
        val analyticsDelegate = FakeAnalyticsLogger()
        val analytics = ConsentGatedAnalyticsLogger(
            delegate = analyticsDelegate,
            consentState = { AnalyticsConsentState.GRANTED },
        )
        val state = buildState(backgroundScope, personalizationSignalRecorder = recorder, analytics = analytics)
        runCurrent()

        state.addAffirmationWithColor("Title", "Subtitle", "#000000")
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, recorder.recorded.size)
        assertEquals(
            1,
            analyticsDelegate.recorded.filterIsInstance<AnalyticsEvent.CustomAffirmationCreated>().size,
        )
    }
}

/** Hand-written recorder (project convention: hand-written fakes over Mockito, REQ-6.1). */
private class FakePersonalizationSignalRecorder : PersonalizationSignalRecorder {
    private val _recorded = mutableListOf<PersonalizationSignal>()
    val recorded: List<PersonalizationSignal> get() = _recorded

    override fun record(signal: PersonalizationSignal) {
        _recorded.add(signal)
    }
}

private class PsRecordingFavoritesRepository(
    initialIds: List<String> = emptyList(),
) : FavoriteAffirmationRepository {
    private val ids = MutableStateFlow(initialIds)
    override fun observeFavoriteIds(): Flow<List<String>> = ids
    override suspend fun isFavorite(id: String): Boolean = id in ids.value
    override suspend fun add(id: String, favoritedAtMillis: Long) {
        ids.value = listOf(id) + ids.value.filterNot { it == id }
    }
    override suspend fun remove(id: String) {
        ids.value = ids.value.filterNot { it == id }
    }
    override suspend fun clear() {
        ids.value = emptyList()
    }
}

private class PsRecordingAffirmationRepository(
    initial: List<AffirmationEntity> = emptyList(),
) : AffirmationRepository {
    private val entities = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<AffirmationEntity>> = entities
    override suspend fun insert(entity: AffirmationEntity) {
        entities.value = entities.value + entity
    }
    override suspend fun deleteById(id: String) {
        entities.value = entities.value.filterNot { it.id == id }
    }
    override suspend fun deleteAll() {
        entities.value = emptyList()
    }
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private class PsFakeCatalogAffirmationRepository(
    private val rows: List<CatalogAffirmationEntity>,
) : CatalogAffirmationRepository {
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> =
        flowOf(rows.filter { it.groupId in groupIds })
    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> =
        rows.filter { it.id in ids }
}

private class PsFixedThemeSelectionPreferences(private val ids: Set<String>) : ThemeSelectionPreferences {
    override fun observeSelectedThemeIds(): Flow<Set<String>?> = flowOf(ids)
    override suspend fun saveSelectedThemeIds(ids: Set<String>) = Unit
}

private object PsSignedOutAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(
        provider: AuthProviderId,
        activityContext: android.content.Context,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun signOut() = Unit
}

private object PsNoOpMigrationSource : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = true
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}

private object PsEmptyCompletionsRepository : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private object PsEmptyMoodsRepository : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private object PsEmptyHealerRepository : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private object PsEmptyMeditationRepository : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(600)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private object PsEmptyNotificationsRepository : NotificationSettingsRepository {
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

private object PsProEntitlementRepository : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flowOf(Entitlement(AccessTier.PRO))
}

private fun buildState(
    scope: CoroutineScope,
    personalizationSignalRecorder: PersonalizationSignalRecorder,
    favorites: FavoriteAffirmationRepository = PsRecordingFavoritesRepository(),
    affirmations: PsRecordingAffirmationRepository = PsRecordingAffirmationRepository(),
    catalog: CatalogAffirmationRepository = PsFakeCatalogAffirmationRepository(emptyList()),
    knownGroupIds: Set<String> = setOf(PERSONALIZADAS_GROUP_ID),
    knownThemeIds: Set<String> = emptySet(),
    themePreferences: ThemeSelectionPreferences = PsFixedThemeSelectionPreferences(emptySet()),
    analytics: com.pirxhio.affirmity.analytics.AnalyticsLogger = com.pirxhio.affirmity.analytics.NoOpAnalyticsLogger,
): AffirmityAppState {
    val trackerPreferences = mock(TrackerPreferences::class.java)
    whenever(trackerPreferences.observeAffirmationsViewedToday())
        .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
    whenever(trackerPreferences.observeHiddenAffirmationIds()).thenReturn(flowOf(emptySet()))
    whenever(trackerPreferences.observeFeedSources()).thenReturn(flowOf(FeedSources()))
    val notificationDebugLog = mock(NotificationDebugLog::class.java)
    whenever(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
    val onboardingPreferences = mock(OnboardingPreferences::class.java)
    whenever(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))
    val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
    whenever(onboardingGuidePreferences.observeHasSeenGuide()).thenReturn(flowOf(true))
    val local = DataSession.Local(
        affirmations = affirmations,
        completions = PsEmptyCompletionsRepository,
        moods = PsEmptyMoodsRepository,
        healerUses = PsEmptyHealerRepository,
        meditation = PsEmptyMeditationRepository,
        notifications = PsEmptyNotificationsRepository,
        entitlements = PsProEntitlementRepository,
        adUnlocks = FakeAdUnlockRepository(),
    )

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { error("Remote session is not used by personalization signal tests") },
        migrator = FirestoreMigrator(PsNoOpMigrationSource),
        trackerPreferences = trackerPreferences,
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = PsSignedOutAuthRepository,
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        themePreferences = themePreferences,
        knownGroupIds = knownGroupIds,
        knownThemeIds = knownThemeIds,
        defaultThematicThemeIds = emptySet(),
        favorites = favorites,
        catalog = catalog,
        analytics = analytics,
        useRemoteSession = false,
        personalizationSignalRecorder = personalizationSignalRecorder,
    )
}
