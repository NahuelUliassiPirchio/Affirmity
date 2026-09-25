package com.pirxhio.affirmity.ui.meditation.customization

import com.pirxhio.affirmity.MeditationLaunchStep
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
import com.pirxhio.affirmity.decideMeditationLaunchStep
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import com.pirxhio.affirmity.ui.meditation.catalog.findMeditationCatalogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BreathingAffirmationsAffirmationSourceTest {

    private class FakeCatalogAffirmationRepository(
        private val byGroup: Map<Set<String>, List<CatalogAffirmationEntity>>,
    ) : CatalogAffirmationRepository {
        var lastRequestedGroupIds: Set<String>? = null

        override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> {
            lastRequestedGroupIds = groupIds
            return flowOf(byGroup.entries.firstOrNull { it.key == groupIds }?.value ?: emptyList())
        }

        override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> = emptyList()
    }

    private fun affirmation(id: String, groupId: String, sortOrder: Int = 0) =
        CatalogAffirmationEntity(id = id, text = "text-$id", subtitle = "subtitle-$id", groupId = groupId, themeId = "theme", collectionId = "collection", sortOrder = sortOrder)

    @Test
    fun `customization defaults shuffle off when no values were saved`() {
        val entry = requireNotNull(findMeditationCatalogEntry("breathing_affirmations"))

        val step = decideMeditationLaunchStep(
            entry = entry,
            confirmedCustomization = null,
            savedValues = emptyMap(),
        ) as MeditationLaunchStep.ShowCustomization

        assertEquals("false", step.seedValues["shuffleAffirmations"])
    }

    @Test
    fun `saved shuffle choices survive customization confirmation into the session`() {
        val entry = requireNotNull(findMeditationCatalogEntry("breathing_affirmations"))

        for (savedShuffle in listOf("true", "false")) {
            val savedValues = mapOf("shuffleAffirmations" to savedShuffle)
            val customization = decideMeditationLaunchStep(
                entry = entry,
                confirmedCustomization = null,
                savedValues = savedValues,
            ) as MeditationLaunchStep.ShowCustomization

            assertEquals(savedShuffle, customization.seedValues["shuffleAffirmations"])

            val session = decideMeditationLaunchStep(
                entry = entry,
                confirmedCustomization = customization.seedValues,
                savedValues = savedValues,
            ) as MeditationLaunchStep.StartSession

            assertEquals(savedShuffle, session.customization["shuffleAffirmations"])
        }
    }

    @Test
    fun `shuffle changes presentation order but preserves the randomly selected set`() = runBlocking {
        val pool = listOf(
            affirmation("a1", "self_worth", 2),
            affirmation("a2", "calm_peace", 3),
            affirmation("a3", "self_worth", 1),
            affirmation("a4", "calm_peace", 1),
            affirmation("a5", "confidence_courage", 2),
            affirmation("a6", "calm_peace", 2),
        )
        val repository = FakeCatalogAffirmationRepository(
            mapOf(defaultAffirmationGroups().map { it.id }.toSet() to pool),
        )
        val seed = 42
        val selected = pool.shuffled(Random(seed)).take(4)

        val shuffled = affirmationTextsForBreathingAffirmations(
            universe = "adaptive", count = 4, shuffleAffirmations = true,
            affirmationRepository = repository, random = Random(seed),
        )
        val ordered = affirmationTextsForBreathingAffirmations(
            universe = "adaptive", count = 4, shuffleAffirmations = false,
            affirmationRepository = repository, random = Random(seed),
        )

        assertEquals(selected.map { it.text }, shuffled)
        assertEquals(selected.sortedWith(compareBy({ it.groupId }, { it.sortOrder })).map { it.text }, ordered)
        assertEquals(shuffled.toSet(), ordered.toSet())
    }

    @Test
    fun `a smaller explicit universe keeps every affirmation and sorts only when shuffle is off`() = runBlocking {
        val pool = listOf(
            affirmation("a3", "calma", 3),
            affirmation("a1", "calma", 1),
            affirmation("a2", "calma", 2),
        )
        val repository = FakeCatalogAffirmationRepository(mapOf(setOf("calma") to pool))
        val seed = 7

        val shuffled = affirmationTextsForBreathingAffirmations(
            universe = "calma", count = 10, shuffleAffirmations = true,
            affirmationRepository = repository, random = Random(seed),
        )
        val ordered = affirmationTextsForBreathingAffirmations(
            universe = "calma", count = 10, shuffleAffirmations = false,
            affirmationRepository = repository, random = Random(seed),
        )

        assertEquals(pool.shuffled(Random(seed)).map { it.text }, shuffled)
        assertEquals(listOf("text-a1", "text-a2", "text-a3"), ordered)
        assertEquals(shuffled.toSet(), ordered.toSet())
    }

    @Test
    fun `an empty universe returns no texts in either presentation mode`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(emptyMap())

        for (shuffle in listOf(false, true)) {
            val texts = affirmationTextsForBreathingAffirmations(
                universe = "calma", count = 5, shuffleAffirmations = shuffle,
                affirmationRepository = repository,
            )

            assertEquals(emptyList<String>(), texts)
            assertEquals(setOf("calma"), repository.lastRequestedGroupIds)
        }
    }

    @Test
    fun `an explicit universe requests exactly that group id and returns up to count texts`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(
            mapOf(setOf("calma") to listOf(affirmation("a1", "calma"), affirmation("a2", "calma"), affirmation("a3", "calma"))),
        )

        val texts = affirmationTextsForBreathingAffirmations(universe = "calma", count = 2, shuffleAffirmations = true, affirmationRepository = repository)

        assertEquals(setOf("calma"), repository.lastRequestedGroupIds)
        assertEquals(2, texts.size)
        assertTrue(texts.all { it.startsWith("text-a") })
    }

    @Test
    fun `adaptive requests every default free universe, not a single hardcoded group`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(emptyMap())

        affirmationTextsForBreathingAffirmations(universe = "adaptive", count = 5, shuffleAffirmations = true, affirmationRepository = repository)

        val requested = requireNotNull(repository.lastRequestedGroupIds)
        assertTrue("expected more than one adaptive fallback group, got $requested", requested.size > 1)
    }

    @Test
    fun `fewer available affirmations than count returns everything available, no crash`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(
            mapOf(setOf("calma") to listOf(affirmation("a1", "calma"))),
        )

        val texts = affirmationTextsForBreathingAffirmations(universe = "calma", count = 10, shuffleAffirmations = true, affirmationRepository = repository)

        assertEquals(listOf("text-a1"), texts)
    }

    @Test
    fun `count of zero or less returns empty without ever querying the repository`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(emptyMap())

        for (shuffle in listOf(false, true)) {
            for (count in listOf(0, -1)) {
                val texts = affirmationTextsForBreathingAffirmations(
                    universe = "adaptive", count = count, shuffleAffirmations = shuffle,
                    affirmationRepository = repository,
                )

                assertEquals(emptyList<String>(), texts)
            }
        }
        assertEquals(null, repository.lastRequestedGroupIds)
    }
}
