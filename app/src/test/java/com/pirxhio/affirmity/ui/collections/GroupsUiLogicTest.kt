package com.pirxhio.affirmity.ui.collections

import com.pirxhio.affirmity.data.COLLECTION_NAME_MAX
import com.pirxhio.affirmity.data.UserCollectionUi
import com.pirxhio.affirmity.ui.theme.GroupHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsUiLogicTest {
    private fun ui(enabled: Boolean, count: Int = 0, highlightId: String = "violet") =
        UserCollectionUi("c1", "Calm", enabled, count, highlightId)

    @Test
    fun `an enabled group reads In your feed`() {
        assertEquals(GroupFeedStatus.InFeed, ui(enabled = true).toGroupCardUi().status)
    }

    @Test
    fun `inFeed mirrors the status`() {
        assertTrue(ui(enabled = true).toGroupCardUi().inFeed)
        assertFalse(ui(enabled = false).toGroupCardUi().inFeed)
    }

    @Test
    fun `a disabled group reads Not in feed`() {
        assertEquals(GroupFeedStatus.NotInFeed, ui(enabled = false).toGroupCardUi().status)
    }

    @Test
    fun `card carries id, name, count and the resolved highlight`() {
        val card = ui(enabled = true, count = 7).toGroupCardUi()

        assertEquals("c1", card.id)
        assertEquals("Calm", card.name)
        assertEquals(7, card.itemCount)
        assertEquals(GroupHighlight.Violet, card.highlight)
    }

    @Test
    fun `an unknown persisted highlight id degrades to the default`() {
        assertEquals(GroupHighlight.Default, ui(enabled = true, highlightId = "gone").toGroupCardUi().highlight)
    }

    @Test
    fun `cards keep the collection order`() {
        val cards = listOf(ui(true).copy(id = "a"), ui(false).copy(id = "b")).toGroupCards()

        assertEquals(listOf("a", "b"), cards.map { it.id })
    }

    // --- new group form ---

    @Test
    fun `name counter counts code points against the max`() {
        assertEquals("0/$COLLECTION_NAME_MAX", nameCounter(""))
        // One emoji outside the BMP is two chars but one code point.
        assertEquals("2/$COLLECTION_NAME_MAX", nameCounter("a😀"))
    }

    @Test
    fun `name length counts code points`() {
        assertEquals(0, nameLength(""))
        assertEquals(2, nameLength("a\uD83D\uDE00"))
    }

    @Test
    fun `create is only enabled for a non blank name`() {
        assertFalse(canSubmitNewGroup("   "))
        assertFalse(canSubmitNewGroup(""))
        assertTrue(canSubmitNewGroup(" Calm "))
    }

    @Test
    fun `name input is clamped to the max length in code points`() {
        assertEquals("x".repeat(COLLECTION_NAME_MAX), clampGroupName("x".repeat(COLLECTION_NAME_MAX + 5)))
        assertEquals("short", clampGroupName("short"))
    }

    @Test
    fun `no collections yield no cards`() {
        assertEquals(emptyList<GroupCardUi>(), emptyList<UserCollectionUi>().toGroupCards())
    }

    @Test
    fun `a blank name stays blank through the clamp`() {
        assertEquals("", clampGroupName(""))
        assertEquals("   ", clampGroupName("   "))
    }

    @Test
    fun `a name exactly at the limit is kept and its counter is full`() {
        val atLimit = "x".repeat(COLLECTION_NAME_MAX)

        assertEquals(atLimit, clampGroupName(atLimit))
        assertEquals("$COLLECTION_NAME_MAX/$COLLECTION_NAME_MAX", nameCounter(atLimit))
    }

    @Test
    fun `a name cut by the clamp never splits a surrogate pair`() {
        val raw = "x".repeat(COLLECTION_NAME_MAX - 1) + "\uD83D\uDE00\uD83D\uDE00"

        assertEquals("x".repeat(COLLECTION_NAME_MAX - 1) + "\uD83D\uDE00", clampGroupName(raw))
    }

    @Test
    fun `only Write my own is available as a starting point`() {
        assertTrue(GroupStartOption.WriteOwn.available)
        assertFalse(GroupStartOption.FromFavorites.available)
        assertFalse(GroupStartOption.RemixTheme.available)
    }
}
