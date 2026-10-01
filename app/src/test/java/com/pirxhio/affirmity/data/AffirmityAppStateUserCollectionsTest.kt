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
import com.pirxhio.affirmity.data.repository.AffirmationRepository
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
import com.pirxhio.affirmity.data.repository.DailyCompletionRepository
import com.pirxhio.affirmity.data.repository.DailyMoodRepository
import com.pirxhio.affirmity.data.repository.DataSession
import com.pirxhio.affirmity.data.repository.Entitlement
import com.pirxhio.affirmity.data.repository.EntitlementRepository
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import com.pirxhio.affirmity.notifications.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

private const val UC_UNIVERSE_ID = "body_energy_wellbeing"
private const val UC_THEME_ID = "body_energy_wellbeing.body_acceptance"
private const val UC_OTHER_THEME_ID = "body_energy_wellbeing.body_confidence"
private const val UC_FREE_COLLECTION_ID = "body_energy_wellbeing.body_acceptance.respect_my_body_today"
private const val UC_PRO_COLLECTION_ID = "body_energy_wellbeing.body_confidence.take_physical_space"

/** Free catalog row in the selected theme. */
private const val FREE_ROW = "cat_free.001"
private const val FREE_ROW_2 = "cat_free.002"

/** Pro catalog row in a theme the feed does NOT select, so it only reaches the feed via a collection. */
private const val PRO_ROW = "cat_pro.001"

/**
 * Collections write API, tier rules and ordering (slice B). Convention as in the other
 * AffirmityAppState tests: every action is followed by `runCurrent()` then `advanceUntilIdle()`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AffirmityAppStateUserCollectionsTest {
    private fun uc(
        id: String,
        name: String = id,
        enabled: Boolean = true,
        created: Long = 1L,
        lastUsed: Long = created,
        items: List<String> = emptyList(),
    ) = UserCollection(id, name, created, enabled, lastUsed, items)

    // --- create -------------------------------------------------------------------------------

    @Test
    fun `create trims the name, stamps id and clock, and enables the new collection with its first item`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo, ids = sequenceOf("uuid-1").iterator(), now = { 1_000L })
        runCurrent()

        val result = state.createCollection("  Calm  ", withAffirmationId = "owned-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(CollectionNameResult.Ok("Calm"), result)
        assertEquals(listOf("create:start:uuid-1", "create:uuid-1:Calm:1000:owned-1"), repo.events)
        assertEquals(listOf(UserCollectionUi("uuid-1", "Calm", enabled = true, resolvedItemCount = 0)), state.userCollections)
    }

    @Test
    fun `create persists the chosen highlight`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo, ids = sequenceOf("u").iterator())
        runCurrent()

        state.createCollection("Calm", highlightId = "rose")
        runCurrent()
        advanceUntilIdle()

        assertEquals("rose", repo.current.single().highlightId)
        assertEquals("rose", state.userCollections.single().highlightId)
    }

    @Test
    fun `create from the add-to-collection flow stores the highlight and the seed affirmation together`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo, ids = sequenceOf("u").iterator())
        runCurrent()

        state.createCollection("Calm", withAffirmationId = "owned-1", highlightId = "gold")
        runCurrent()
        advanceUntilIdle()

        val created = repo.current.single()
        assertEquals("gold", created.highlightId)
        assertEquals(listOf("owned-1"), created.affirmationIds)
    }

    @Test
    fun `affirmation ids in user collections is the union of every group's members`() = runTest {
        val repo = RecordingUserCollectionRepository(
            listOf(uc("c1", items = listOf("a", "b")), uc("c2", items = listOf("b", "c")), uc("c3")),
        )
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        assertEquals(setOf("a", "b", "c"), state.affirmationIdsInUserCollections)
    }

    @Test
    fun `two rapid toggles of the same membership end as add then remove from persisted state`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1")))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        state.toggleAffirmationInCollection("c1", "a")
        state.toggleAffirmationInCollection("c1", "a")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("addItem:c1:a", "removeItem:c1:a"), repo.events)
        assertEquals(emptyList<String>(), repo.current.single().affirmationIds)
    }

    @Test
    fun `toggling membership of a deleted collection writes nothing`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        state.toggleAffirmationInCollection("gone", "a")
        runCurrent()
        advanceUntilIdle()

        assertTrue(repo.events.isEmpty())
    }

    @Test
    fun `an affirmation stops counting as grouped after removal or after its group is deleted`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1", items = listOf("a")), uc("c2", items = listOf("b"))))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()
        assertEquals(setOf("a", "b"), state.affirmationIdsInUserCollections)

        state.removeFromCollection("c1", "a")
        runCurrent()
        advanceUntilIdle()
        assertEquals(setOf("b"), state.affirmationIdsInUserCollections)

        state.deleteCollection("c2")
        runCurrent()
        advanceUntilIdle()
        assertEquals(emptySet<String>(), state.affirmationIdsInUserCollections)
    }

    @Test
    fun `create normalises an unknown highlight to the default`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo, ids = sequenceOf("u").iterator())
        runCurrent()

        state.createCollection("Calm", highlightId = "mauve")
        runCurrent()
        advanceUntilIdle()

        assertEquals(DEFAULT_COLLECTION_HIGHLIGHT_ID, repo.current.single().highlightId)
    }

    @Test
    fun `create without an initial affirmation passes null`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo, ids = sequenceOf("u").iterator())
        runCurrent()

        state.createCollection("Calm")
        runCurrent()
        advanceUntilIdle()

        assertTrue(repo.events.last().endsWith(":-"))
    }

    @Test
    fun `invalid names are rejected and nothing is written`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1", name = "Calm")))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        assertEquals(CollectionNameResult.Blank, state.createCollection("   "))
        assertEquals(CollectionNameResult.TooLong, state.createCollection("x".repeat(COLLECTION_NAME_MAX + 1)))
        assertEquals(CollectionNameResult.Duplicate, state.createCollection("cALM"))
        runCurrent()
        advanceUntilIdle()

        assertTrue(repo.events.isEmpty())
    }

    @Test
    fun `two rapid creates of the same name persist only one - the Mutex re-check rejects the second`() = runTest {
        val repo = RecordingUserCollectionRepository()
        val state = buildUcState(backgroundScope, repo, ids = listOf("u1", "u2").iterator())
        runCurrent()

        val results = mutableListOf<CollectionNameResult>()
        launch { results += state.createCollection("Calm") }
        launch { results += state.createCollection("calm") }
        runCurrent()
        advanceUntilIdle()

        // The returned value is the authoritative verdict computed under the lock.
        assertEquals(listOf(CollectionNameResult.Ok("Calm"), CollectionNameResult.Duplicate), results)
        assertEquals(1, repo.current.size)
        assertEquals(1, repo.events.count { it.startsWith("create:u") && !it.startsWith("create:start") })
    }

    // --- tier limit ---------------------------------------------------------------------------

    @Test
    fun `Free is blocked at the third collection and allowed below it`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1"), uc("c2")))
        val state = buildUcState(backgroundScope, repo, tier = AccessTier.FREE)
        runCurrent()

        assertFalse(state.canCreateCollection)
        assertEquals(CollectionNameResult.LimitReached, state.createCollection("Third"))
        runCurrent()
        advanceUntilIdle()
        assertTrue(repo.events.isEmpty())

        val below = RecordingUserCollectionRepository(listOf(uc("c1")))
        val belowState = buildUcState(backgroundScope, below, tier = AccessTier.FREE, ids = listOf("u").iterator())
        runCurrent()
        assertTrue(belowState.canCreateCollection)
        assertTrue(belowState.createCollection("Second") is CollectionNameResult.Ok)
    }

    @Test
    fun `Pro is unlimited`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1"), uc("c2"), uc("c3")))
        val state = buildUcState(backgroundScope, repo, tier = AccessTier.PRO, ids = listOf("u").iterator())
        runCurrent()

        assertTrue(state.canCreateCollection)
        assertTrue(state.createCollection("Fourth") is CollectionNameResult.Ok)
    }

    @Test
    fun `downgraded user keeps full use of existing collections but cannot create`() = runTest {
        val repo = RecordingUserCollectionRepository(
            (1..5).map { uc("c$it", enabled = false, lastUsed = it.toLong()) },
        )
        val state = buildUcState(backgroundScope, repo, tier = AccessTier.FREE, now = { 9_000L })
        runCurrent()

        state.toggleCollection("c1")
        state.addToCollection("c2", "owned-1")
        assertEquals(CollectionNameResult.Ok("Renamed"), state.renameCollection("c3", "Renamed"))
        state.deleteCollection("c4")
        runCurrent()
        advanceUntilIdle()

        assertTrue(repo.current.first { it.id == "c1" }.enabled)
        assertEquals(listOf("owned-1"), repo.current.first { it.id == "c2" }.affirmationIds)
        assertEquals("Renamed", repo.current.first { it.id == "c3" }.name)
        assertEquals(4, repo.current.size)
        assertFalse(state.canCreateCollection)
        assertEquals(CollectionNameResult.LimitReached, state.createCollection("New"))
    }

    // --- toggle / order -----------------------------------------------------------------------

    @Test
    fun `toggling ON bumps lastUsedAt to the clock and reorders the chips`() = runTest {
        val repo = RecordingUserCollectionRepository(
            listOf(
                uc("x", enabled = false, created = 1L, lastUsed = 1L),
                uc("y", enabled = false, created = 2L, lastUsed = 2L),
            ),
        )
        val state = buildUcState(backgroundScope, repo, now = { 5_000L })
        runCurrent()
        assertEquals(listOf("y", "x"), state.userCollections.map { it.id })

        state.toggleCollection("x")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("enable:x:5000"), repo.events)
        assertEquals(listOf("x", "y"), state.userCollections.map { it.id })
        assertEquals(5_000L, repo.current.first { it.id == "x" }.lastUsedAtMillis)
    }

    @Test
    fun `toggling OFF never touches lastUsedAt or position`() = runTest {
        val repo = RecordingUserCollectionRepository(
            listOf(
                uc("x", enabled = true, created = 1L, lastUsed = 7_000L),
                uc("y", enabled = false, created = 2L, lastUsed = 2L),
            ),
        )
        val state = buildUcState(backgroundScope, repo, now = { 9_999L })
        runCurrent()

        state.toggleCollection("x")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("disable:x"), repo.events)
        assertEquals(7_000L, repo.current.first { it.id == "x" }.lastUsedAtMillis)
        assertEquals(listOf("x", "y"), state.userCollections.map { it.id })
        assertFalse(state.userCollections.first { it.id == "x" }.enabled)
    }

    @Test
    fun `setCollectionEnabled routes true to enable with the clock and false to disable`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("x", enabled = false)))
        val state = buildUcState(backgroundScope, repo, now = { 42L })
        runCurrent()

        state.setCollectionEnabled("x", true)
        state.setCollectionEnabled("x", false)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("enable:x:42", "disable:x"), repo.events)
    }

    @Test
    fun `turning off the last enabled collection is allowed and exposes the empty state inputs`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("only", enabled = true, items = listOf("owned-1"))))
        val state = buildUcState(
            backgroundScope,
            repo,
            affirmations = RecordingAffirmationRepositoryUC(listOf(ucOwned("owned-1"))),
            feedSources = FeedSources(includeFavorites = false, includeOwn = false),
        )
        runCurrent()
        assertTrue(state.anyCollectionEnabled)
        assertEquals(listOf("owned-1"), state.filteredAffirmations.map { it.id })

        state.toggleCollection("only")
        runCurrent()
        advanceUntilIdle()

        assertFalse(state.anyCollectionEnabled)
        assertTrue(state.filteredAffirmations.isEmpty())
        assertEquals(listOf("disable:only"), repo.events)
    }

    @Test
    fun `writes are serialised - a toggle waits for an in-flight create`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = RecordingUserCollectionRepository(listOf(uc("x", enabled = true)), createGate = gate)
        val state = buildUcState(backgroundScope, repo, ids = listOf("u1").iterator(), now = { 10L })
        runCurrent()

        launch { state.createCollection("New") }
        runCurrent()
        state.setCollectionEnabled("x", false)
        runCurrent()
        assertEquals(listOf("create:start:u1"), repo.events)

        gate.complete(Unit)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("create:start:u1", "create:u1:New:10:-", "disable:x"), repo.events)
    }

    @Test
    fun `add after the collection was deleted performs no insert and does not throw`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1")))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        state.deleteCollection("c1")
        state.addToCollection("c1", "owned-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("delete:c1"), repo.events)
    }

    @Test
    fun `rapid toggles alternate because each one reads the persisted state under the lock`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("x", enabled = false)))
        val state = buildUcState(backgroundScope, repo, now = { 5L })
        runCurrent()

        repeat(3) { state.toggleCollection("x") }
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("enable:x:5", "disable:x", "enable:x:5"), repo.events)
    }

    @Test
    fun `two renames to the same name persist only one - the Mutex re-check rejects the second`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1", name = "A"), uc("c2", name = "B")))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        val results = mutableListOf<CollectionNameResult>()
        launch { results += state.renameCollection("c1", "Same") }
        launch { results += state.renameCollection("c2", "same") }
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf(CollectionNameResult.Ok("Same"), CollectionNameResult.Duplicate), results)
        assertEquals(listOf("rename:c1:Same"), repo.events)
    }

    @Test
    fun `sheet validity accepts an enabled collection alone and rejects none enabled`() = runTest {
        val enabled = buildUcState(
            backgroundScope,
            RecordingUserCollectionRepository(listOf(uc("c", enabled = true))),
            themeIds = emptySet(),
            feedSources = FeedSources(includeFavorites = false, includeOwn = false),
        )
        val none = buildUcState(
            backgroundScope,
            RecordingUserCollectionRepository(listOf(uc("c", enabled = false))),
            themeIds = emptySet(),
            feedSources = FeedSources(includeFavorites = false, includeOwn = false),
        )
        runCurrent()
        advanceUntilIdle()

        assertTrue(enabled.draftThemeIds.value.isEmpty())
        assertFalse(enabled.draftFeedSources.value.includeFavorites || enabled.draftFeedSources.value.includeOwn)
        assertTrue(enabled.isDraftThemeSelectionValid)
        assertFalse(none.isDraftThemeSelectionValid)
    }

    // --- rename / membership / ui -------------------------------------------------------------

    @Test
    fun `rename validates against the other collections and writes the trimmed name`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1", name = "Calm"), uc("c2", name = "Focus")))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        assertEquals(CollectionNameResult.Duplicate, state.renameCollection("c1", "fOCUS"))
        assertEquals(CollectionNameResult.Blank, state.renameCollection("c1", " "))
        assertEquals(CollectionNameResult.Ok("CALM"), state.renameCollection("c1", " CALM "))
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("rename:c1:CALM"), repo.events)
    }

    @Test
    fun `add and remove membership delegate to the repository and userCollectionIdsFor reflects them`() = runTest {
        val repo = RecordingUserCollectionRepository(
            listOf(uc("c1", items = listOf("a")), uc("c2", items = listOf("a", "b"))),
        )
        val state = buildUcState(backgroundScope, repo)
        runCurrent()
        assertEquals(setOf("c1", "c2"), state.userCollectionIdsFor("a"))
        assertEquals(setOf("c2"), state.userCollectionIdsFor("b"))

        state.addToCollection("c1", "b")
        state.removeFromCollection("c2", "a")
        runCurrent()
        advanceUntilIdle()

        assertEquals(setOf("c1"), state.userCollectionIdsFor("a"))
        assertEquals(setOf("c1", "c2"), state.userCollectionIdsFor("b"))
        assertEquals(emptySet<String>(), state.userCollectionIdsFor("none"))
    }

    @Test
    fun `resolvedItemCount counts only affirmations that still exist`() = runTest {
        val repo = RecordingUserCollectionRepository(
            listOf(uc("c1", items = listOf("owned-1", "orphan", FREE_ROW))),
        )
        val state = buildUcState(
            backgroundScope,
            repo,
            affirmations = RecordingAffirmationRepositoryUC(listOf(ucOwned("owned-1"))),
            catalogRows = listOf(ucCatalog(FREE_ROW, UC_FREE_COLLECTION_ID)),
        )
        runCurrent()
        advanceUntilIdle()

        assertEquals(2, state.userCollections.single().resolvedItemCount)
    }

    // --- feed ---------------------------------------------------------------------------------

    @Test
    fun `an enabled collection adds its members on top of themes`() = runTest {
        val state = feedState(
            this,
            collections = listOf(uc("c", enabled = true, items = listOf(PRO_ROW))),
        )
        assertEquals(listOf(PRO_ROW, FREE_ROW), state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `a disabled collection contributes nothing`() = runTest {
        val state = feedState(
            this,
            collections = listOf(uc("c", enabled = false, items = listOf(PRO_ROW))),
        )
        assertEquals(listOf(FREE_ROW), state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `an affirmation in two enabled collections and in themes appears once`() = runTest {
        val state = feedState(
            this,
            collections = listOf(
                uc("c1", items = listOf(PRO_ROW, FREE_ROW)),
                uc("c2", items = listOf(PRO_ROW)),
            ),
        )
        assertEquals(listOf(PRO_ROW, FREE_ROW), state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `collection rows sit between favorites and themed catalog rows`() = runTest {
        val state = feedState(
            this,
            collections = listOf(uc("c", items = listOf(PRO_ROW))),
            owned = listOf(ucOwned("fav")),
            favoriteIds = listOf("fav"),
            feedSources = FeedSources(includeFavorites = true, includeOwn = false),
        )
        assertEquals(listOf("fav", PRO_ROW, FREE_ROW), state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `hidden outranks an enabled collection and the membership is kept`() = runTest {
        val state = feedState(
            this,
            collections = listOf(uc("c", items = listOf(PRO_ROW))),
            hiddenIds = setOf(PRO_ROW),
        )
        assertEquals(listOf(FREE_ROW), state.filteredAffirmations.map { it.id })
        assertEquals(setOf("c"), state.userCollectionIdsFor(PRO_ROW))
    }

    @Test
    fun `a Pro-locked catalog row is excluded for Free but stays in the collection`() = runTest {
        val state = feedState(
            this,
            collections = listOf(uc("c", items = listOf(PRO_ROW))),
            tier = AccessTier.FREE,
        )
        assertEquals(listOf(FREE_ROW), state.filteredAffirmations.map { it.id })
        assertEquals(setOf("c"), state.userCollectionIdsFor(PRO_ROW))
        assertEquals(1, state.userCollections.single().resolvedItemCount)
    }

    @Test
    fun `before themes resolve only owned collection members contribute`() = runTest {
        val state = feedState(
            this,
            collections = listOf(uc("c", items = listOf("owned-1", FREE_ROW))),
            owned = listOf(ucOwned("owned-1")),
            themeIds = null,
            feedSources = FeedSources(includeFavorites = false, includeOwn = false),
        )
        assertEquals(listOf("owned-1"), state.filteredAffirmations.map { it.id })
    }

    @Test
    fun `toggling a collection on then off never reshuffles the rows already in the feed`() = runTest {
        for (randomize in listOf(false, true)) {
            val repo = RecordingUserCollectionRepository(
                listOf(uc("c2", enabled = false, items = listOf(PRO_ROW))),
            )
            val state = feedState(
                this,
                repo = repo,
                feedSources = FeedSources(includeOwn = false, randomizeOrder = randomize, orderSeed = 12345L),
                extraThemedRows = listOf(FREE_ROW_2),
            )
            fun baseOrder() = state.filteredAffirmations.map { it.id }.filter { it != PRO_ROW }
            val before = baseOrder()
            assertEquals(setOf(FREE_ROW, FREE_ROW_2), before.toSet())

            state.toggleCollection("c2")
            runCurrent()
            advanceUntilIdle()
            assertTrue(PRO_ROW in state.filteredAffirmations.map { it.id })
            assertEquals(before, baseOrder())

            state.toggleCollection("c2")
            runCurrent()
            advanceUntilIdle()
            assertFalse(PRO_ROW in state.filteredAffirmations.map { it.id })
            assertEquals(before, baseOrder())
        }
    }

    // --- cleanup hooks ------------------------------------------------------------------------

    @Test
    fun `removeAffirmation removes the id from every collection`() = runTest {
        val repo = RecordingUserCollectionRepository(
            listOf(uc("c1", items = listOf("owned-1", "keep")), uc("c2", items = listOf("owned-1"))),
        )
        val state = buildUcState(
            backgroundScope,
            repo,
            affirmations = RecordingAffirmationRepositoryUC(listOf(ucOwned("owned-1"))),
        )
        runCurrent()

        state.removeAffirmation("owned-1")
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("removeAffirmations:[owned-1]"), repo.events)
        assertEquals(listOf("keep"), repo.current.first { it.id == "c1" }.affirmationIds)
        assertTrue(repo.current.first { it.id == "c2" }.affirmationIds.isEmpty())
    }

    @Test
    fun `removeAffirmation on a catalog id never touches collections`() = runTest {
        val repo = RecordingUserCollectionRepository(listOf(uc("c1", items = listOf(FREE_ROW))))
        val state = buildUcState(backgroundScope, repo)
        runCurrent()

        state.removeAffirmation(FREE_ROW)
        runCurrent()
        advanceUntilIdle()

        assertTrue(repo.events.isEmpty())
        assertEquals(listOf(FREE_ROW), repo.current.single().affirmationIds)
    }

    @Test
    fun `replace import clears memberships of the deleted owned ids only`() = runTest {
        val shared = mutableListOf<String>()
        val repo = RecordingUserCollectionRepository(
            listOf(uc("c1", items = listOf("o1", FREE_ROW, "o2"))),
            events = shared,
        )
        val state = buildUcState(
            backgroundScope,
            repo,
            affirmations = RecordingAffirmationRepositoryUC(listOf(ucOwned("o1"), ucOwned("o2")), shared),
            tier = AccessTier.PRO,
        )
        runCurrent()

        state.importAffirmationsFromJson(IMPORT_JSON, replaceExisting = true)
        runCurrent()
        advanceUntilIdle()

        // Ids are captured BEFORE deleteAll, and the clear runs after the delete.
        assertEquals(listOf("affirmations.clear", "removeAffirmations:[o1, o2]"), shared)
        assertEquals(listOf(FREE_ROW), repo.current.single().affirmationIds)
    }

    @Test
    fun `append import leaves collection memberships untouched`() = runTest {
        val shared = mutableListOf<String>()
        val repo = RecordingUserCollectionRepository(listOf(uc("c1", items = listOf("o1"))), events = shared)
        val state = buildUcState(
            backgroundScope,
            repo,
            affirmations = RecordingAffirmationRepositoryUC(listOf(ucOwned("o1")), shared),
            tier = AccessTier.PRO,
        )
        runCurrent()

        state.importAffirmationsFromJson(IMPORT_JSON, replaceExisting = false)
        runCurrent()
        advanceUntilIdle()

        assertTrue(shared.isEmpty())
    }

    // --- harness ------------------------------------------------------------------------------

    private suspend fun feedState(
        scope: kotlinx.coroutines.test.TestScope,
        collections: List<UserCollection> = emptyList(),
        repo: RecordingUserCollectionRepository = RecordingUserCollectionRepository(collections),
        owned: List<AffirmationEntity> = emptyList(),
        favoriteIds: List<String> = emptyList(),
        hiddenIds: Set<String> = emptySet(),
        tier: AccessTier = AccessTier.PRO,
        themeIds: Set<String>? = setOf(UC_THEME_ID),
        feedSources: FeedSources = FeedSources(includeOwn = false),
        extraThemedRows: List<String> = emptyList(),
    ): AffirmityAppState {
        val state = buildUcState(
            scope.backgroundScope,
            repo,
            affirmations = RecordingAffirmationRepositoryUC(owned),
            catalogRows = listOf(ucCatalog(FREE_ROW, UC_FREE_COLLECTION_ID), ucCatalog(PRO_ROW, UC_PRO_COLLECTION_ID)) +
                extraThemedRows.map { ucCatalog(it, UC_FREE_COLLECTION_ID) },
            tier = tier,
            themeIds = themeIds,
            hiddenIds = hiddenIds,
            feedSources = feedSources,
            favoriteIds = favoriteIds,
        )
        scope.runCurrent()
        scope.advanceUntilIdle()
        return state
    }

    private companion object {
        const val IMPORT_JSON =
            """[{"title":"Title","subtitle":"Subtitle","background":{"type":"color","value":"#000000"}}]"""
    }
}

private fun ucOwned(id: String) = AffirmationEntity(
    id = id,
    title = "Title $id",
    subtitle = "Subtitle $id",
    backgroundType = "color",
    backgroundValue = "#000000",
    groupId = PERSONALIZADAS_GROUP_ID,
)

private fun ucCatalog(id: String, collectionId: String) = CatalogAffirmationEntity(
    id = id,
    text = "Text for $id",
    subtitle = "Subtitle for $id",
    groupId = UC_UNIVERSE_ID,
    themeId = "$UC_UNIVERSE_ID.theme",
    collectionId = collectionId,
    sortOrder = 1,
)

private class RecordingAffirmationRepositoryUC(
    initial: List<AffirmationEntity> = emptyList(),
    private val events: MutableList<String> = mutableListOf(),
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
        events += "affirmations.clear"
        entities.value = emptyList()
    }
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) = Unit
}

private class FakeCatalogRepositoryUC(private val rows: List<CatalogAffirmationEntity>) : CatalogAffirmationRepository {
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> =
        flowOf(rows.filter { it.groupId in groupIds })
    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> = rows.filter { it.id in ids }
}

private class FakeEntitlementRepositoryUC(private val tier: AccessTier) : EntitlementRepository {
    override fun observe(): Flow<Entitlement> = flowOf(Entitlement(tier))
}

/** [ids] == null models "DataStore has not resolved yet": the flow never emits. */
private class FixedThemePreferencesUC(private val ids: Set<String>?) : ThemeSelectionPreferences {
    override fun observeSelectedThemeIds(): Flow<Set<String>?> =
        if (ids == null) flow { awaitCancellation() } else flowOf(ids)
    override suspend fun saveSelectedThemeIds(ids: Set<String>) = Unit
}

private class FavoritesFakeUC(initialIds: List<String>) : com.pirxhio.affirmity.data.repository.FavoriteAffirmationRepository {
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

private fun buildUcState(
    scope: CoroutineScope,
    collections: RecordingUserCollectionRepository,
    affirmations: RecordingAffirmationRepositoryUC = RecordingAffirmationRepositoryUC(),
    catalogRows: List<CatalogAffirmationEntity> = emptyList(),
    tier: AccessTier = AccessTier.PRO,
    themeIds: Set<String>? = setOf(UC_THEME_ID),
    hiddenIds: Set<String> = emptySet(),
    feedSources: FeedSources = FeedSources(),
    favoriteIds: List<String> = emptyList(),
    ids: Iterator<String> = emptyList<String>().iterator(),
    now: () -> Long = { 0L },
): AffirmityAppState {
    val trackerPreferences = mock(TrackerPreferences::class.java)
    whenever(trackerPreferences.observeAffirmationsViewedToday())
        .thenReturn(flowOf(DailyViewCount(epochDay = -1L, count = 0)))
    whenever(trackerPreferences.observeHiddenAffirmationIds()).thenReturn(flowOf(hiddenIds))
    whenever(trackerPreferences.observeFeedSources()).thenReturn(flowOf(feedSources))
    val notificationDebugLog = mock(NotificationDebugLog::class.java)
    whenever(notificationDebugLog.entries).thenReturn(flowOf(emptyList()))
    val onboardingPreferences = mock(OnboardingPreferences::class.java)
    whenever(onboardingPreferences.observeHasCompletedOnboarding()).thenReturn(flowOf(true))
    val onboardingGuidePreferences = mock(OnboardingGuidePreferences::class.java)
    whenever(onboardingGuidePreferences.observeHasSeenGuide()).thenReturn(flowOf(true))
    val local = DataSession.Local(
        affirmations = affirmations,
        completions = EmptyCompletionsRepositoryUC,
        moods = EmptyMoodsRepositoryUC,
        healerUses = EmptyHealerRepositoryUC,
        meditation = EmptyMeditationRepositoryUC,
        notifications = EmptyNotificationsRepositoryUC,
        entitlements = FakeEntitlementRepositoryUC(tier),
        adUnlocks = FakeAdUnlockRepository(),
    )

    return AffirmityAppState(
        scope = scope,
        local = local,
        remoteSessionFactory = { error("Remote session is not used by collections tests") },
        migrator = FirestoreMigrator(NoOpMigrationSourceUC),
        trackerPreferences = trackerPreferences,
        onboardingPreferences = onboardingPreferences,
        onboardingGuidePreferences = onboardingGuidePreferences,
        imageStore = mock(AffirmationImageStore::class.java),
        notificationDebugLog = notificationDebugLog,
        notifier = mock(Notifier::class.java),
        widgetUpdater = WidgetUpdater { },
        authRepository = SignedOutAuthRepositoryUC,
        fcmTokenRepository = mock(FcmTokenRepository::class.java),
        onboardingRepository = mock(FirestoreOnboardingRepository::class.java),
        knownGroupIds = setOf(PERSONALIZADAS_GROUP_ID, UC_UNIVERSE_ID),
        themePreferences = FixedThemePreferencesUC(themeIds),
        knownThemeIds = setOf(UC_THEME_ID, UC_OTHER_THEME_ID),
        defaultThematicThemeIds = emptySet(),
        favorites = FavoritesFakeUC(favoriteIds),
        catalog = FakeCatalogRepositoryUC(catalogRows),
        collectionRepository = collections,
        collectionClock = now,
        collectionIdFactory = { ids.next() },
        useRemoteSession = false,
    )
}

private object EmptyCompletionsRepositoryUC : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> = emptyList()
    override suspend fun markMeditation(epochDay: Long) = Unit
    override suspend fun markAffirmation(epochDay: Long) = Unit
    override suspend fun earliestEpochDay(): Long? = null
}

private object EmptyMoodsRepositoryUC : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> = emptyList()
    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = Unit
}

private object EmptyHealerRepositoryUC : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = flowOf(emptyList())
    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> = emptyList()
    override suspend fun recordUse(healedEpochDay: Long) = Unit
}

private object EmptyMeditationRepositoryUC : MeditationPreferencesRepository {
    override fun observeMeditationDurationSeconds(): Flow<Int?> = flowOf(600)
    override suspend fun saveMeditationDurationSeconds(seconds: Int) = Unit
}

private object EmptyNotificationsRepositoryUC : NotificationSettingsRepository {
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

private object SignedOutAuthRepositoryUC : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    override suspend fun signIn(
        provider: AuthProviderId,
        activityContext: android.content.Context,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun signOut() = Unit
}

private object NoOpMigrationSourceUC : FirestoreMigrationSource {
    override suspend fun markerExists(uid: String): Boolean = true
    override suspend fun commitChunk(writes: List<DocWrite>) = Unit
}
