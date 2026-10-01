package com.pirxhio.affirmity.ui.collections

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionUiLogicTest {
    private val calm = UserCollectionUi("a", "Calm", true, 0)

    private fun ui(id: String, name: String = id, enabled: Boolean = true, count: Int = 0) =
        UserCollectionUi(id, name, enabled, count)

    // --- name error mapping ---

    @Test
    fun `ok maps to no error`() {
        assertNull(CollectionNameResult.Ok("Calm").errorStringRes())
    }

    @Test
    fun `each failure maps to its own string resource`() {
        assertEquals(R.string.collection_name_error_blank, CollectionNameResult.Blank.errorStringRes())
        assertEquals(R.string.collection_name_error_too_long, CollectionNameResult.TooLong.errorStringRes())
        assertEquals(R.string.collection_name_error_duplicate, CollectionNameResult.Duplicate.errorStringRes())
        assertEquals(R.string.collection_limit_reached, CollectionNameResult.LimitReached.errorStringRes())
    }

    // --- picker rows ---

    @Test
    fun `picker rows keep collection order and mark members`() {
        val rows = pickerRows(listOf(ui("a"), ui("b"), ui("c")), memberIds = setOf("c", "a"))

        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
        assertEquals(listOf(true, false, true), rows.map { it.checked })
    }

    @Test
    fun `picker rows are empty without collections`() {
        assertEquals(emptyList<PickerRow>(), pickerRows(emptyList(), memberIds = setOf("a")))
    }

    @Test
    fun `member ids of unknown collections are ignored`() {
        val rows = pickerRows(listOf(ui("a", name = "Calm")), memberIds = setOf("ghost"))

        assertEquals(listOf(PickerRow("a", "Calm", checked = false)), rows)
    }

    @Test
    fun `toggling a row requests add when unchecked and remove when checked`() {
        assertEquals(PickerToggle.Add, PickerRow("a", "A", checked = false).toggle())
        assertEquals(PickerToggle.Remove, PickerRow("a", "A", checked = true).toggle())
    }

    // --- chip label ---

    @Test
    fun `chip label shows name and resolved count`() {
        assertEquals("Calm (3)", collectionChipLabel(ui("a", name = "Calm", count = 3)))
        assertEquals("Calm (0)", collectionChipLabel(ui("a", name = "Calm", count = 0)))
    }

    // --- empty state ---

    @Test
    fun `non empty feed has no empty state`() {
        assertEquals(
            FeedEmptyState.None,
            feedEmptyState(feedIsEmpty = false, hasCollections = true),
        )
    }

    @Test
    fun `empty feed with all collections off points to collections`() {
        assertEquals(
            FeedEmptyState.Collections,
            feedEmptyState(feedIsEmpty = true, hasCollections = true),
        )
    }

    @Test
    fun `empty feed without any collection keeps the generic state`() {
        assertEquals(
            FeedEmptyState.Generic,
            feedEmptyState(feedIsEmpty = true, hasCollections = false),
        )
    }

    // --- chip management flow ---

    @Test
    fun `long press opens the actions for that chip`() {
        val next = CollectionManageState.Closed.reduce(CollectionManageEvent.LongPress(calm))

        assertEquals(CollectionManageState.Actions(calm), next)
    }

    @Test
    fun `choosing rename and delete moves to their stages`() {
        val actions = CollectionManageState.Actions(calm)

        assertEquals(CollectionManageState.Renaming(calm), actions.reduce(CollectionManageEvent.ChooseRename))
        assertEquals(CollectionManageState.ConfirmingDelete(calm), actions.reduce(CollectionManageEvent.ChooseDelete))
    }

    @Test
    fun `the delete confirmation stage is reachable only from the actions stage`() {
        val others = listOf(
            CollectionManageState.Closed,
            CollectionManageState.Renaming(calm),
            CollectionManageState.ConfirmingDelete(calm),
        )

        assertEquals(
            CollectionManageState.ConfirmingDelete(calm),
            CollectionManageState.Actions(calm).reduce(CollectionManageEvent.ChooseDelete),
        )
        others.forEach { state ->
            assertEquals(state, state.reduce(CollectionManageEvent.ChooseDelete))
        }
        assertEquals(
            CollectionManageState.Closed,
            CollectionManageState.Closed.reduce(CollectionManageEvent.LongPress(calm)).reduce(CollectionManageEvent.Dismiss),
        )
    }

    @Test
    fun `every manage state survives the saver round trip`() {
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        listOf(
            CollectionManageState.Closed,
            CollectionManageState.Actions(calm),
            CollectionManageState.Renaming(calm),
            CollectionManageState.ConfirmingDelete(calm),
        ).forEach { state ->
            val saved = with(CollectionManageStateSaver) { scope.save(state) }
            assertEquals(state, CollectionManageStateSaver.restore(saved!!))
        }
    }

    @Test
    fun `confirming or cancelling closes the flow`() {
        val confirming = CollectionManageState.ConfirmingDelete(calm)

        assertEquals(CollectionManageState.Closed, confirming.reduce(CollectionManageEvent.ConfirmDelete))
        assertEquals(CollectionManageState.Closed, confirming.reduce(CollectionManageEvent.Dismiss))
        assertEquals(CollectionManageState.Closed, CollectionManageState.Renaming(calm).reduce(CollectionManageEvent.Dismiss))
    }

    @Test
    fun `choosing delete without a chip selected does nothing`() {
        assertEquals(
            CollectionManageState.Closed,
            CollectionManageState.Closed.reduce(CollectionManageEvent.ChooseDelete),
        )
        assertEquals(
            CollectionManageState.Closed,
            CollectionManageState.Closed.reduce(CollectionManageEvent.ConfirmDelete),
        )
    }
}
