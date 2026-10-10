package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserCollectionsTest {
    private fun collection(
        id: String,
        name: String = id,
        enabled: Boolean = true,
        items: List<String> = emptyList(),
    ) = UserCollection(
        id = id,
        name = name,
        createdAtMillis = 0L,
        enabled = enabled,
        lastUsedAtMillis = 0L,
        affirmationIds = items,
    )

    // --- validateNewCollection ---------------------------------------------------------------

    @Test
    fun `new collection checks the tier limit before the name`() {
        val full = listOf(collection("c1", name = "A"), collection("c2", name = "B"))
        assertEquals(CollectionNameResult.LimitReached, validateNewCollection("  ", full, AccessTier.FREE))
        assertEquals(CollectionNameResult.Blank, validateNewCollection("  ", full, AccessTier.PRO))
    }

    @Test
    fun `new collection below the limit validates the name`() {
        val one = listOf(collection("c1", name = "Calm"))
        assertEquals(CollectionNameResult.Ok("Focus"), validateNewCollection(" Focus ", one, AccessTier.FREE))
        assertEquals(CollectionNameResult.Duplicate, validateNewCollection("calm", one, AccessTier.FREE))
    }

    // --- toUserCollectionUi ------------------------------------------------------------------

    @Test
    fun `ui projection keeps order and counts only known affirmations`() {
        val ui = listOf(
            collection("c1", name = "One", enabled = true, items = listOf("a", "orphan", "b")),
            collection("c2", name = "Two", enabled = false, items = emptyList()),
        ).toUserCollectionUi(knownIds = setOf("a", "b"))
        assertEquals(
            listOf(
                UserCollectionUi("c1", "One", enabled = true, resolvedItemCount = 2),
                UserCollectionUi("c2", "Two", enabled = false, resolvedItemCount = 0),
            ),
            ui,
        )
    }

    @Test
    fun `ui projection carries the highlight id`() {
        val ui = listOf(collection("c1").copy(highlightId = "rose")).toUserCollectionUi(knownIds = emptySet())
        assertEquals("rose", ui.single().highlightId)
    }

    @Test
    fun `a collection without a stored highlight uses the default`() {
        assertEquals(DEFAULT_COLLECTION_HIGHLIGHT_ID, collection("c1").highlightId)
    }

    // --- resolveCollectionMembers -------------------------------------------------------------

    @Test
    fun `members resolve in collection order and orphans drop out`() {
        val known = mapOf("a" to "Alpha", "b" to "Beta")

        val result = resolveCollectionMembers(listOf("b", "orphan", "a"), known::get)

        assertEquals(listOf("Beta", "Alpha"), result)
    }

    // --- validateCollectionName -------------------------------------------------------------

    @Test
    fun `name is trimmed before it is accepted`() {
        assertEquals(CollectionNameResult.Ok("Calm"), validateCollectionName("  Calm  ", emptyList()))
    }

    @Test
    fun `blank and whitespace-only names are rejected`() {
        assertEquals(CollectionNameResult.Blank, validateCollectionName("", emptyList()))
        assertEquals(CollectionNameResult.Blank, validateCollectionName("   \t ", emptyList()))
    }

    @Test
    fun `40 characters pass and 41 are rejected after trimming`() {
        assertEquals(
            CollectionNameResult.Ok("a".repeat(COLLECTION_NAME_MAX)),
            validateCollectionName(" " + "a".repeat(COLLECTION_NAME_MAX) + " ", emptyList()),
        )
        assertEquals(CollectionNameResult.TooLong, validateCollectionName("a".repeat(COLLECTION_NAME_MAX + 1), emptyList()))
    }

    @Test
    fun `length is counted in code points so 40 emoji pass and 41 fail`() {
        val emoji = "😀"
        assertEquals(
            CollectionNameResult.Ok(emoji.repeat(COLLECTION_NAME_MAX)),
            validateCollectionName(emoji.repeat(COLLECTION_NAME_MAX), emptyList()),
        )
        assertEquals(CollectionNameResult.TooLong, validateCollectionName(emoji.repeat(COLLECTION_NAME_MAX + 1), emptyList()))
    }

    @Test
    fun `duplicate is detected case-insensitively`() {
        val existing = listOf(collection("c1", name = "Calm"))
        assertEquals(CollectionNameResult.Duplicate, validateCollectionName("cALM", existing))
        assertEquals(CollectionNameResult.Ok("Focus"), validateCollectionName("Focus", existing))
    }

    @Test
    fun `duplicate detection folds non-ASCII letters`() {
        val existing = listOf(collection("c1", name = "Ánimo"))
        assertEquals(CollectionNameResult.Duplicate, validateCollectionName("ánimo", existing))
    }

    @Test
    fun `renaming a collection to its own name is not a duplicate`() {
        val existing = listOf(collection("c1", name = "Calm"), collection("c2", name = "Focus"))
        assertEquals(CollectionNameResult.Ok("CALM"), validateCollectionName("CALM", existing, excludingId = "c1"))
        assertEquals(CollectionNameResult.Duplicate, validateCollectionName("focus", existing, excludingId = "c1"))
    }

    // --- canCreateUserCollection ------------------------------------------------------------

    @Test
    fun `Free may create below the limit and not at or above it`() {
        assertTrue(canCreateUserCollection(AccessTier.FREE, 0))
        assertTrue(canCreateUserCollection(AccessTier.FREE, FREE_COLLECTION_LIMIT - 1))
        assertFalse(canCreateUserCollection(AccessTier.FREE, FREE_COLLECTION_LIMIT))
        assertFalse(canCreateUserCollection(AccessTier.FREE, 5))
    }

    @Test
    fun `Pro is unlimited`() {
        assertTrue(canCreateUserCollection(AccessTier.PRO, 0))
        assertTrue(canCreateUserCollection(AccessTier.PRO, 500))
    }

    // --- resolveEnabledCollectionRows -------------------------------------------------------

    private val rows = listOf("a", "b", "c", "d", "e").associateWith { it }

    @Test
    fun `members of an enabled collection resolve and a disabled one contributes nothing`() {
        val result = resolveEnabledCollectionRows(
            collections = listOf(
                collection("on", enabled = true, items = listOf("a", "b")),
                collection("off", enabled = false, items = listOf("c")),
            ),
            byId = rows::get,
            eligible = { true },
        )
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `an affirmation in several enabled collections resolves once`() {
        val result = resolveEnabledCollectionRows(
            collections = listOf(
                collection("c1", items = listOf("a", "b")),
                collection("c2", items = listOf("b", "a", "c")),
            ),
            byId = rows::get,
            eligible = { true },
        )
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `ineligible rows (hidden or locked) are dropped and orphan ids are ignored`() {
        val result = resolveEnabledCollectionRows(
            collections = listOf(collection("c1", items = listOf("a", "orphan", "b", "c"))),
            byId = rows::get,
            eligible = { it != "b" },
        )
        assertEquals(listOf("a", "c"), result)
    }

    @Test
    fun `owned-only eligibility keeps owned ids and drops catalog ids`() {
        val owned = setOf("a")
        val result = resolveEnabledCollectionRows(
            collections = listOf(collection("c1", items = listOf("a", "d"))),
            byId = rows::get,
            eligible = { it in owned },
        )
        assertEquals(listOf("a"), result)
    }

    // --- copyCollectionName (DG-11, S13, S14) --------------------------------------------------

    private val en = CopySuffixes(first = "(copy)", numbered = "(copy %1\$d)")
    private val es = CopySuffixes(first = "(copia)", numbered = "(copia %1\$d)")

    @Test
    fun `copy name appends the first suffix when free`() {
        assertEquals("Everyday Essentials (copy)", copyCollectionName("Everyday Essentials", emptyList(), en))
        assertEquals("Esenciales del día (copia)", copyCollectionName("Esenciales del día", emptyList(), es))
    }

    @Test
    fun `copy name increments the numbered suffix on clashes`() {
        val one = listOf(collection("a", name = "Everyday Essentials (copy)"))
        val two = one + collection("b", name = "Everyday Essentials (copy 2)")

        assertEquals("Everyday Essentials (copy 2)", copyCollectionName("Everyday Essentials", one, en))
        assertEquals("Everyday Essentials (copy 3)", copyCollectionName("Everyday Essentials", two, en))
        val esTaken = listOf(collection("a", name = "Esenciales del día (copia)"))
        assertEquals("Esenciales del día (copia 2)", copyCollectionName("Esenciales del día", esTaken, es))
    }

    @Test
    fun `copy name clash check is case-insensitive`() {
        val existing = listOf(collection("a", name = "everyday essentials (COPY)"))

        assertEquals("Everyday Essentials (copy 2)", copyCollectionName("Everyday Essentials", existing, en))
    }

    @Test
    fun `copy name truncates the base by code points so the whole name fits`() {
        val base = "\uD83D\uDE00".repeat(COLLECTION_NAME_MAX) // 40 emoji, 80 UTF-16 units

        val name = copyCollectionName(base, emptyList(), en)

        assertEquals(COLLECTION_NAME_MAX, name.codePointCount(0, name.length))
        assertTrue(name.endsWith(" (copy)"))
        assertEquals("\uD83D\uDE00".repeat(COLLECTION_NAME_MAX - " (copy)".length), name.removeSuffix(" (copy)"))
        assertEquals(CollectionNameResult.Ok(name), validateCollectionName(name, emptyList()))
    }

    @Test
    fun `copy name stays unique and within the limit when the longer numbered suffix overflows`() {
        val base = "A".repeat(COLLECTION_NAME_MAX)
        val first = copyCollectionName(base, emptyList(), en)
        val second = copyCollectionName(base, listOf(collection("a", name = first)), en)

        assertTrue(first.length <= COLLECTION_NAME_MAX)
        assertTrue(second.length <= COLLECTION_NAME_MAX)
        assertTrue(second != first)
        assertTrue(second.endsWith("(copy 2)"))
        assertEquals(CollectionNameResult.Ok(second), validateCollectionName(second, listOf(collection("a", name = first))))
    }
}
