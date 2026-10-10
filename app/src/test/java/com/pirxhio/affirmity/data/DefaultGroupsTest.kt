package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGroupsTest {

    @Test
    fun `registry order is Everyday Essentials then Morning Start`() {
        assertEquals(
            listOf("default.everyday_essentials", "default.morning_start"),
            DefaultGroups.all.map { it.id },
        )
    }

    @Test
    fun `isDefaultGroupId recognises registry ids only`() {
        assertTrue(DefaultGroups.isDefaultGroupId("default.everyday_essentials"))
        assertTrue(DefaultGroups.isDefaultGroupId("default.morning_start"))
        assertFalse(DefaultGroups.isDefaultGroupId("3f2c7c1e-0000-4000-8000-000000000000"))
        assertFalse(DefaultGroups.isDefaultGroupId("default.unknown"))
    }

    // --- mergeWithDefaultGroups (DG-4, DG-5) ---------------------------------------------------

    private fun user(id: String, lastUsed: Long, enabled: Boolean = true) =
        UserCollection(id, "Name $id", createdAtMillis = 1L, enabled = enabled, lastUsedAtMillis = lastUsed, affirmationIds = emptyList())

    @Test
    fun `merge pins defaults first in registry order regardless of lastUsed or enable`() {
        val states = mapOf(
            DefaultGroups.MORNING_START_ID to DefaultGroupState(enabled = true, lastUsedAtMillis = 9_999L),
        )
        val merged = mergeWithDefaultGroups(
            DefaultGroups.all,
            states,
            listOf(user("u1", lastUsed = 5_000L), user("u2", lastUsed = 100L)),
        )

        assertEquals(
            listOf(DefaultGroups.EVERYDAY_ESSENTIALS_ID, DefaultGroups.MORNING_START_ID, "u1", "u2"),
            merged.map { it.id },
        )
        assertEquals(listOf(true, true, false, false), merged.map { it.isDefault })
    }

    @Test
    fun `merge reads state per default and treats a missing row as disabled and never used`() {
        val merged = mergeWithDefaultGroups(
            DefaultGroups.all,
            mapOf(DefaultGroups.MORNING_START_ID to DefaultGroupState(true, 42L)),
            emptyList(),
        )

        val essentials = merged.first { it.id == DefaultGroups.EVERYDAY_ESSENTIALS_ID }
        val morning = merged.first { it.id == DefaultGroups.MORNING_START_ID }
        assertFalse(essentials.enabled)
        assertEquals(0L, essentials.lastUsedAtMillis)
        assertTrue(morning.enabled)
        assertEquals(42L, morning.lastUsedAtMillis)
        assertEquals(DefaultGroups.all[1].affirmationIds, morning.affirmationIds)
        assertEquals(DefaultGroups.all[1].highlightId, morning.highlightId)
    }

    @Test
    fun `merge ignores stale state ids that are no longer in the registry`() {
        val merged = mergeWithDefaultGroups(
            DefaultGroups.all,
            mapOf("default.retired" to DefaultGroupState(true, 1L)),
            emptyList(),
        )

        assertEquals(DefaultGroups.all.map { it.id }, merged.map { it.id })
    }

    @Test
    fun `default members absent from the catalog are dropped from the resolved count`() {
        val merged = mergeWithDefaultGroups(DefaultGroups.all, emptyMap(), emptyList())
        val firstId = DefaultGroups.all[0].affirmationIds.first()
        val secondId = DefaultGroups.all[0].affirmationIds[1]

        val seeded = merged.toUserCollectionUi(knownIds = setOf(firstId, secondId, "unrelated"))
        val unseeded = merged.toUserCollectionUi(knownIds = emptySet())

        assertEquals(2, seeded.first { it.id == DefaultGroups.EVERYDAY_ESSENTIALS_ID }.resolvedItemCount)
        assertEquals(0, seeded.first { it.id == DefaultGroups.MORNING_START_ID }.resolvedItemCount)
        assertEquals(listOf(0, 0), unseeded.map { it.resolvedItemCount })
        assertTrue(seeded.all { it.isDefault })
    }

    // --- userCreated + limit (UC-1) ------------------------------------------------------------

    @Test
    fun `userCreated drops defaults and keeps user groups in order`() {
        val merged = mergeWithDefaultGroups(DefaultGroups.all, emptyMap(), listOf(user("u1", 3L), user("u2", 2L)))

        assertEquals(listOf("u1", "u2"), merged.userCreated().map { it.id })
    }

    @Test
    fun `free user at two user groups is limited even with defaults listed`() {
        val merged = mergeWithDefaultGroups(DefaultGroups.all, emptyMap(), listOf(user("u1", 3L), user("u2", 2L)))

        assertEquals(CollectionNameResult.LimitReached, validateNewCollection("Fresh", merged, AccessTier.FREE))
        assertEquals(CollectionNameResult.Ok("Fresh"), validateNewCollection("Fresh", merged, AccessTier.PRO))
    }

    @Test
    fun `free user with no user groups can still create despite two defaults`() {
        val merged = mergeWithDefaultGroups(DefaultGroups.all, emptyMap(), emptyList())

        assertEquals(CollectionNameResult.Ok("Mine"), validateNewCollection("Mine", merged, AccessTier.FREE))
    }
}
