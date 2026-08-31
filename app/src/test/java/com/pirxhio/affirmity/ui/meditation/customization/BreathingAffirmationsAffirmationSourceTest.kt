package com.pirxhio.affirmity.ui.meditation.customization

import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun affirmation(id: String, groupId: String) =
        CatalogAffirmationEntity(id = id, text = "text-$id", groupId = groupId, themeId = "theme", collectionId = "collection", sortOrder = 0)

    @Test
    fun `an explicit universe requests exactly that group id and returns up to count texts`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(
            mapOf(setOf("calma") to listOf(affirmation("a1", "calma"), affirmation("a2", "calma"), affirmation("a3", "calma"))),
        )

        val texts = affirmationTextsForBreathingAffirmations(universe = "calma", count = 2, affirmationRepository = repository)

        assertEquals(setOf("calma"), repository.lastRequestedGroupIds)
        assertEquals(2, texts.size)
        assertTrue(texts.all { it.startsWith("text-a") })
    }

    @Test
    fun `adaptive requests every default free universe, not a single hardcoded group`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(emptyMap())

        affirmationTextsForBreathingAffirmations(universe = "adaptive", count = 5, affirmationRepository = repository)

        val requested = requireNotNull(repository.lastRequestedGroupIds)
        assertTrue("expected more than one adaptive fallback group, got $requested", requested.size > 1)
    }

    @Test
    fun `fewer available affirmations than count returns everything available, no crash`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(
            mapOf(setOf("calma") to listOf(affirmation("a1", "calma"))),
        )

        val texts = affirmationTextsForBreathingAffirmations(universe = "calma", count = 10, affirmationRepository = repository)

        assertEquals(listOf("text-a1"), texts)
    }

    @Test
    fun `count of zero or less returns empty without ever querying the repository`() = runBlocking {
        val repository = FakeCatalogAffirmationRepository(emptyMap())

        val texts = affirmationTextsForBreathingAffirmations(universe = "adaptive", count = 0, affirmationRepository = repository)

        assertEquals(emptyList<String>(), texts)
        assertEquals(null, repository.lastRequestedGroupIds)
    }
}
