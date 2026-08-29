package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
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
import com.pirxhio.affirmity.data.local.GroupSelectionPreferences
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.OnboardingGuidePreferences
import com.pirxhio.affirmity.data.local.OnboardingPreferences
import com.pirxhio.affirmity.data.catalog.CatalogSeeder
import com.pirxhio.affirmity.data.local.CatalogPreferences
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.data.remote.DocWrite
import com.pirxhio.affirmity.data.remote.FcmTokenRepository
import com.pirxhio.affirmity.data.remote.FirestoreMigrationSource
import com.pirxhio.affirmity.data.remote.FirestoreMigrator
import com.pirxhio.affirmity.data.remote.FirestoreOnboardingRepository
import com.pirxhio.affirmity.data.repository.AffirmationRepository
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

/** Real ids from the committed taxonomy (`ui/groups/CatalogTaxonomy.kt`), so this suite exercises
 * the actual generated data rather than an invented fixture. */
private const val UNIVERSE_ID = "body_energy_wellbeing"
private const val FREE_COLLECTION_ID = "body_energy_wellbeing.body_acceptance.respect_my_body_today"
private const val PRO_COLLECTION_ID = "body_energy_wellbeing.body_acceptance.kind_body_relationship"

/**
 * Write-routing (design D14), feed access filtering (D7), and cross-space favorites (D10) for the
 * catalog read-model. See `AffirmityAppStateFavoritesTest` for the owned-only favorites suite this
 * extends, and `AffirmityAppStateTest`'s convention note about `runCurrent()`/`advanceUntilIdle()`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AffirmityAppStateCatalogTest {

    @Test
    fun `setTokenOverride on a catalog id hits catalogOverrides, never affirmations`() = runTest {
        val affirmations = RecordingAffirmationRepository2()
        val catalogOverrides = RecordingCatalogOverrideRepository()
        val catalog = FakeCatalogAffirmationRepository(
            listOf(
                catalogEntity(
                    id = "cat_$FREE_COLLECTION_ID.001",
                    collectionId = FREE_COLLECTION_ID,
                    withToken = true,
                ),
            ),
        )
        val state = buildState(
            backgroundScope,
            affirmations = affirmations,
            catalog = catalog,
            catalogOverrides = catalogOverrides,
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        val tokenKey = AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 0, "name")
        state.setTokenOverride("cat_$FREE_COLLECTION_ID.001", tokenKey, "Alex")
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            listOf("cat_$FREE_COLLECTION_ID.001" to mapOf(tokenKey to "Alex")),
            catalogOverrides.written,
        )
        assertTrue(affirmations.setOverridesCalls.isEmpty())
    }

    @Test
    fun `setTokenOverride on a UUID id is unchanged -- hits affirmations, never catalogOverrides`() = runTest {
        val affirmations = RecordingAffirmationRepository2(
            initial = listOf(affirmationEntity("owned-1", withToken = true)),
        )
        val catalogOverrides = RecordingCatalogOverrideRepository()
        val state = buildState(backgroundScope, affirmations = affirmations, catalogOverrides = catalogOverrides)
        runCurrent()

        val tokenKey = AffirmationTemplateParser.tokenKey(TemplateField.TITLE, 0, "name")
        state.setTokenOverride("owned-1", tokenKey, "Alex")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("owned-1" to mapOf(tokenKey to "Alex")), affirmations.setOverridesCalls)
        assertTrue(catalogOverrides.written.isEmpty())
    }

    @Test
    fun `removeAffirmation on a catalog id performs zero repository calls`() = runTest {
        val affirmations = RecordingAffirmationRepository2()
        val favorites = RecordingFavoritesRepository2()
        val state = buildState(backgroundScope, affirmations = affirmations, favorites = favorites)
        runCurrent()

        state.removeAffirmation("cat_$FREE_COLLECTION_ID.001")
        runCurrent()
        advanceUntilIdle()

        assertTrue(affirmations.deleteCalls.isEmpty())
        assertTrue(favorites.removed.isEmpty())
    }

    @Test
    fun `removeAffirmation on a UUID id is unchanged`() = runTest {
        val affirmations = RecordingAffirmationRepository2(initial = listOf(affirmationEntity("owned-1")))
        val favorites = RecordingFavoritesRepository2()
        val state = buildState(backgroundScope, affirmations = affirmations, favorites = favorites)
        runCurrent()

        state.removeAffirmation("owned-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("owned-1"), affirmations.deleteCalls)
        assertEquals(listOf("owned-1"), favorites.removed)
    }

    @Test
    fun `filteredAffirmations excludes a catalog row whose collection is locked for a Free user`() = runTest {
        val catalog = FakeCatalogAffirmationRepository(
            listOf(
                catalogEntity(id = "cat_free.001", collectionId = FREE_COLLECTION_ID),
                catalogEntity(id = "cat_pro.001", collectionId = PRO_COLLECTION_ID),
            ),
        )
        val state = buildState(
            backgroundScope,
            catalog = catalog,
            entitlements = FakeCatalogEntitlementRepository(AccessTier.FREE),
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        val ids = state.filteredAffirmations.map { it.id }
        assertTrue("cat_free.001" in ids)
        assertTrue("cat_pro.001" !in ids)
    }

    @Test
    fun `filteredAffirmations includes the same locked row for a Pro user`() = runTest {
        val catalog = FakeCatalogAffirmationRepository(
            listOf(catalogEntity(id = "cat_pro.001", collectionId = PRO_COLLECTION_ID)),
        )
        val state = buildState(
            backgroundScope,
            catalog = catalog,
            entitlements = FakeCatalogEntitlementRepository(AccessTier.PRO),
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        assertTrue("cat_pro.001" in state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `deselecting a group removes its catalog rows from the feed`() = runTest {
        val catalog = FakeCatalogAffirmationRepository(
            listOf(catalogEntity(id = "cat_free.001", collectionId = FREE_COLLECTION_ID)),
        )
        val state = buildState(
            backgroundScope,
            catalog = catalog,
            entitlements = FakeCatalogEntitlementRepository(AccessTier.FREE),
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        assertTrue("cat_free.001" !in state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `favoriteAffirmations resolves a personal and a catalog favorite together in recency order`() = runTest {
        val favorites = RecordingFavoritesRepository2(initialIds = listOf("cat_free.001", "owned-1"))
        val affirmations = RecordingAffirmationRepository2(initial = listOf(affirmationEntity("owned-1")))
        val catalog = FakeCatalogAffirmationRepository(
            listOf(catalogEntity(id = "cat_free.001", collectionId = FREE_COLLECTION_ID)),
        )
        val state = buildState(
            backgroundScope,
            affirmations = affirmations,
            favorites = favorites,
            catalog = catalog,
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("cat_free.001", "owned-1"), state.favoriteAffirmations.map { it.id })
    }

    @Test
    fun `a catalog favorite remains resolvable after its group is deselected`() = runTest {
        val favorites = RecordingFavoritesRepository2()
        val affirmations = RecordingAffirmationRepository2(initial = listOf(affirmationEntity("owned-1")))
        val catalog = FakeCatalogAffirmationRepository(
            listOf(catalogEntity(id = "cat_free.001", collectionId = FREE_COLLECTION_ID)),
        )
        val state = buildState(
            backgroundScope,
            affirmations = affirmations,
            favorites = favorites,
            catalog = catalog,
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        state.toggleFavorite("cat_free.001")
        runCurrent()
        advanceUntilIdle()
        assertTrue("cat_free.001" in state.favoriteAffirmations.map { it.id })

        val stateAfterDeselection = buildState(
            backgroundScope,
            affirmations = affirmations,
            favorites = favorites,
            catalog = catalog,
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        assertTrue("cat_free.001" !in stateAfterDeselection.filteredAffirmations.map { it.id })
        assertTrue("cat_free.001" in stateAfterDeselection.favoriteAffirmations.map { it.id })
    }

    @Test
    fun `an id in neither space drops out of favoriteAffirmations`() = runTest {
        val favorites = RecordingFavoritesRepository2(initialIds = listOf("orphan-cat-id"))
        val state = buildState(backgroundScope, favorites = favorites)
        runCurrent()
        advanceUntilIdle()

        assertTrue(state.favoriteAffirmations.isEmpty())
    }

    @Test
    fun `a favorited catalog row whose collection is locked still appears in favorites`() = runTest {
        val favorites = RecordingFavoritesRepository2(initialIds = listOf("cat_pro.001"))
        val catalog = FakeCatalogAffirmationRepository(
            listOf(catalogEntity(id = "cat_pro.001", collectionId = PRO_COLLECTION_ID)),
        )
        val state = buildState(
            backgroundScope,
            favorites = favorites,
            catalog = catalog,
            entitlements = FakeCatalogEntitlementRepository(AccessTier.FREE),
            knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID),
            groupPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID, UNIVERSE_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        assertTrue("cat_pro.001" !in state.filteredAffirmations.map { it.id })
        assertTrue("cat_pro.001" in state.favoriteAffirmations.map { it.id })
    }

    // --- Startup wiring (task 5.10 -- gap found in PR3 review) ------------------------------

    @Test
    fun `CatalogSeeder seedIfNeeded is invoked exactly once on AffirmityAppState construction`() = runTest {
        val dao = RecordingSeederDao()
        val prefs = RecordingSeederPrefs()
        val seeder = CatalogSeeder(
            assetReader = { """{"version":"1.0.0","affirmations":[]}""" },
            dao = dao,
            prefs = prefs,
            knownCollectionIds = { emptySet() },
        )
        buildState(backgroundScope, catalogSeeder = seeder)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals("1.0.0", prefs.saved.single())
    }

    @Test
    fun `CatalogSeeder seedIfNeeded is idempotent -- a second construction with the same marker no-ops`() = runTest {
        val dao = RecordingSeederDao()
        val prefs = RecordingSeederPrefs(initial = "1.0.0")
        val seeder = CatalogSeeder(
            assetReader = { """{"version":"1.0.0","affirmations":[]}""" },
            dao = dao,
            prefs = prefs,
            knownCollectionIds = { emptySet() },
        )
        buildState(backgroundScope, catalogSeeder = seeder)
        runCurrent()
        advanceUntilIdle()

        assertTrue("already-seeded marker means no dao call at all", dao.calls.isEmpty())
    }
}

private class RecordingSeederDao : com.pirxhio.affirmity.data.local.CatalogAffirmationDao {
    val calls = mutableListOf<String>()
    override fun observeAll(): Flow<List<CatalogAffirmationEntity>> = flowOf(emptyList())
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> = flowOf(emptyList())
    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
        calls += "replaceAll"
    }
    override suspend fun insertAll(rows: List<CatalogAffirmationEntity>) = throw NotImplementedError()
    override suspend fun deleteAll() = throw NotImplementedError()
}

private class RecordingSeederPrefs(initial: String? = null) : CatalogPreferences {
    val saved = mutableListOf<String>()
    private val state = MutableStateFlow(initial)
    override fun observeSeededCatalogVersion(): Flow<String?> = state
    override suspend fun saveSeededCatalogVersion(version: String) {
        saved += version
        state.value = version
    }
}

private fun catalogEntity(id: String, collectionId: String, withToken: Boolean = false) = CatalogAffirmationEntity(
    id = id,
    text = if (withToken) "Text for $id, [name]" else "Text for $id",
    groupId = UNIVERSE_ID,
    themeId = "$UNIVERSE_ID.theme",
    collectionId = collectionId,
    sortOrder = 1,
)

private fun affirmationEntity(
    id: String,
    groupId: String = PERSONALIZADAS_GROUP_ID,
    withToken: Boolean = false,
) = AffirmationEntity(
    id = id,
    title = if (withToken) "Title $id, [name]" else "Title $id",
    subtitle = "Subtitle $id",
    backgroundType = "color",
    backgroundValue = "#000000",
    groupId = groupId,
)

private class FakeCatalogAffirmationRepository(
    private val rows: List<CatalogAffirmationEntity>,
) : CatalogAffirmationRepository {
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> =
        flowOf(rows.filter { it.groupId in groupIds })

    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> =
        rows.filter { it.id in ids }
}

private class RecordingCatalogOverrideRepository : CatalogOverrideRepository {
    val written = mutableListOf<Pair<String, Map<String, String>>>()
    override fun observeAll(): Flow<Map<String, Map<String, String>>> = flowOf(emptyMap())
    override suspend fun setOverrides(catalogAffirmationId: String, overrides: Map<String, String>) {
        written += catalogAffirmationId to overrides
    }
}

private class FakeCatalogEntitlementRepository(private val tier: AccessTier) : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flowOf(Entitlement(tier))
}

private class FixedGroupSelectionPreferences(private val ids: Set<String>) : GroupSelectionPreferences {
    override fun observeSelectedGroupIds(): Flow<Set<String>?> = flowOf(ids)
    override suspend fun saveSelectedGroupIds(ids: Set<String>) = Unit
}

private class RecordingAffirmationRepository2(
    initial: List<AffirmationEntity> = emptyList(),
) : AffirmationRepository {
    private val entities = MutableStateFlow(initial)
    val deleteCalls = mutableListOf<String>()
    val setOverridesCalls = mutableListOf<Pair<String, Map<String, String>>>()
    override fun observeAll(): Flow<List<AffirmationEntity>> = entities
    override suspend fun insert(entity: AffirmationEntity) {
        entities.value = entities.value + entity
    }
    override suspend fun deleteById(id: String) {
        deleteCalls += id
        entities.value = entities.value.filterNot { it.id == id }
    }
    override suspend fun deleteAll() {
        entities.value = emptyList()
    }
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) {
        setOverridesCalls += id to overrides
    }
}

private class RecordingFavoritesRepository2(
    initialIds: List<String> = emptyList(),
) : FavoriteAffirmationRepository {
    private val ids = MutableStateFlow(initialIds)
    val removed = mutableListOf<String>()
    override fun observeFavoriteIds(): Flow<List<String>> = ids
    override suspend fun isFavorite(id: String): Boolean = id in ids.value
    override suspend fun add(id: String, favoritedAtMillis: Long) {
        ids.value = listOf(id) + ids.value.filterNot { it == id }
    }
    override suspend fun remove(id: String) {
        removed += id
        ids.value = ids.value.filterNot { it == id }
    }
    override suspend fun clear() {
        ids.value = emptyList()
    }
}

private fun buildState(
    scope: CoroutineScope,
    affirmations: RecordingAffirmationRepository2 = RecordingAffirmationRepository2(),
    favorites: FavoriteAffirmationRepository = RecordingFavoritesRepository2(),
    catalog: CatalogAffirmationRepository = FakeCatalogAffirmationRepository(emptyList()),
    catalogOverrides: CatalogOverrideRepository = RecordingCatalogOverrideRepository(),
    entitlements: EntitlementRepository = FakeCatalogEntitlementRepository(AccessTier.PRO),
    knownGroupIds: Set<String> = setOf(PERSONALIZADAS_GROUP_ID),
    groupPreferences: GroupSelectionPreferences = FixedGroupSelectionPreferences(setOf(PERSONALIZADAS_GROUP_ID)),
    catalogSeeder: CatalogSeeder? = null,
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
        completions = EmptyCompletionsRepository2,
        moods = EmptyMoodsRepository2,
        healerUses = EmptyHealerRepository2,
        meditation = EmptyMeditationRepository2,
        notifications = EmptyNotificationsRepository2,
        entitlements = entitlements,
        adUnlocks = FakeAdUnlockRepository(),
        catalogOverrides = catalogOverrides,
    )

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { error("Remote session is not used by catalog tests") },
        migrator = FirestoreMigrator(NoOpMigrationSource2),
        trackerPreferences = trackerPreferences,
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = SignedOutAuthRepository2,
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        groupPreferences = groupPreferences,
        knownGroupIds = knownGroupIds,
        defaultThematicGroupIds = emptySet(),
        favorites = favorites,
        catalog = catalog,
        catalogSeeder = catalogSeeder,
        useRemoteSession = false,
    )
}

private object EmptyCompletionsRepository2 : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
}

private object EmptyMoodsRepository2 : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private object EmptyHealerRepository2 : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private object EmptyMeditationRepository2 : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(600)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private object EmptyNotificationsRepository2 : NotificationSettingsRepository {
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

private object SignedOutAuthRepository2 : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(
        provider: AuthProviderId,
        activityContext: android.content.Context,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun signOut() = Unit
}

private object NoOpMigrationSource2 : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = true
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}
