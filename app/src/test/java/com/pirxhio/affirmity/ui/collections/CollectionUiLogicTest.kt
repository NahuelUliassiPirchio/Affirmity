package com.pirxhio.affirmity.ui.collections

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi
import com.pirxhio.affirmity.ui.theme.GroupHighlight
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
        assertEquals(R.string.collection_name_error_read_only, CollectionNameResult.ReadOnly.errorStringRes())
        assertEquals(R.string.collection_name_error_catalog_not_ready, CollectionNameResult.CatalogNotReady.errorStringRes())
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

    // --- "Save to" sheet ---

    @Test
    fun `save to rows pin Favorites first then groups in chip order`() {
        val rows = saveToRows(listOf(ui("a"), ui("b")), memberIds = setOf("b"), isFavorite = true)

        assertEquals(
            listOf(SaveToRow.Favorites(checked = true), SaveToRow.Group("a", "a", "teal", false), SaveToRow.Group("b", "b", "teal", true)),
            rows,
        )
    }

    @Test
    fun `save to rows with no groups still offer Favorites`() {
        assertEquals(listOf(SaveToRow.Favorites(checked = false)), saveToRows(emptyList(), emptySet(), isFavorite = false))
    }

    @Test
    fun `tapping a row toggles its own destination`() {
        assertEquals(SaveToAction.ToggleFavorite, SaveToRow.Favorites(checked = false).toggle())
        // Direction is decided later from persisted state, so a stale `checked` cannot matter.
        assertEquals(SaveToAction.ToggleGroup("g"), SaveToRow.Group("g", "G", "teal", false).toggle())
        assertEquals(SaveToAction.ToggleGroup("g"), SaveToRow.Group("g", "G", "teal", true).toggle())
    }

    @Test
    fun `an affirmation is saved when it is a favourite or in any group`() {
        assertEquals(false, isSaved(isFavorite = false, isInAnyGroup = false))
        assertEquals(true, isSaved(isFavorite = true, isInAnyGroup = false))
        assertEquals(true, isSaved(isFavorite = false, isInAnyGroup = true))
    }

    private fun card(id: String) = GroupCardUi(id, id, GroupHighlight.Teal, GroupFeedStatus.InFeed, itemCount = 3)

    @Test
    fun `groups row lists the groups in order then the new group tile last`() {
        val a = card("a")
        val b = card("b")
        assertEquals(
            listOf(GroupsRowEntry.Group(a), GroupsRowEntry.Group(b), GroupsRowEntry.NewGroup),
            groupsRowEntries(listOf(a, b)),
        )
    }

    @Test
    fun `groups row with no groups only has the new group tile`() {
        assertEquals(listOf(GroupsRowEntry.NewGroup), groupsRowEntries(emptyList()))
    }

    // --- tile play affordance ---

    @Test
    fun `a group tile with items has an enabled play affordance`() {
        assertEquals(TilePlay.Enabled, GroupsRowEntry.Group(card("a")).tilePlay())
    }

    @Test
    fun `a group tile with nothing resolved keeps its play affordance disabled`() {
        val empty = card("a").copy(itemCount = 0)
        assertEquals(TilePlay.Disabled, GroupsRowEntry.Group(empty).tilePlay())
    }

    @Test
    fun `the new group tile has no play affordance`() {
        assertEquals(TilePlay.None, GroupsRowEntry.NewGroup.tilePlay())
    }

    @Test
    fun `a disabled default group tile is still playable`() {
        val ui = UserCollectionUi("default.morning_start", "m", enabled = false, resolvedItemCount = 12, highlightId = "amber", isDefault = true)
        assertEquals(TilePlay.Enabled, GroupsRowEntry.Group(ui.toGroupCardUi()).tilePlay())
    }

    // --- default groups (DG-6, DG-9, DG-12, UC-4) ---

    private fun defaultUi(id: String = "default.morning_start", enabled: Boolean = false, count: Int = 12) =
        UserCollectionUi(id, name = id, enabled = enabled, resolvedItemCount = count, highlightId = "amber", isDefault = true)

    @Test
    fun `save to rows never list default groups`() {
        val rows = saveToRows(listOf(defaultUi(), ui("a"), defaultUi("default.everyday_essentials"), ui("b")), setOf("a"), isFavorite = false)

        assertEquals(
            listOf(SaveToRow.Favorites(false), SaveToRow.Group("a", "a", "teal", true), SaveToRow.Group("b", "b", "teal", false)),
            rows,
        )
    }

    @Test
    fun `picker rows never list default groups`() {
        val rows = pickerRows(listOf(defaultUi(), ui("a", name = "Calm")), memberIds = setOf("default.morning_start"))

        assertEquals(listOf(PickerRow("a", "Calm", checked = false)), rows)
    }

    @Test
    fun `reducer ignores rename and delete for a default group`() {
        val actions = CollectionManageState.Actions(defaultUi())

        assertEquals(actions, actions.reduce(CollectionManageEvent.ChooseRename))
        assertEquals(actions, actions.reduce(CollectionManageEvent.ChooseDelete))
        // A user group still moves on, so the guard is specific to defaults.
        assertEquals(
            CollectionManageState.Renaming(calm),
            CollectionManageState.Actions(calm).reduce(CollectionManageEvent.ChooseRename),
        )
    }

    @Test
    fun `actions sheet offers duplicate and the feed switch for defaults and rename and delete for user groups`() {
        assertEquals(
            listOf(ManageAction.Duplicate, ManageAction.ToggleInFeed),
            manageActionsFor(defaultUi()),
        )
        assertEquals(listOf(ManageAction.Rename, ManageAction.Delete), manageActionsFor(calm))
    }

    @Test
    fun `detail sheet is read-only only for defaults`() {
        assertEquals(true, isReadOnlyGroup(defaultUi()))
        assertEquals(false, isReadOnlyGroup(calm))
    }

    @Test
    fun `duplicate tap upgrades when the free limit is reached and duplicates otherwise`() {
        assertEquals(DuplicateTap.Duplicate, duplicateTap(canCreate = true))
        assertEquals(DuplicateTap.Upgrade, duplicateTap(canCreate = false))
    }

    @Test
    fun `saver round trip keeps highlight and isDefault`() {
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        val state = CollectionManageState.Actions(defaultUi(enabled = true, count = 7))

        val saved = with(CollectionManageStateSaver) { scope.save(state) }
        val restored = CollectionManageStateSaver.restore(saved!!) as CollectionManageState.Actions

        assertEquals(state, restored)
        assertEquals(true, restored.collection.isDefault)
        assertEquals("amber", restored.collection.highlightId)
    }

    @Test
    fun `saver restores a legacy five-field payload with defaults`() {
        val restored = CollectionManageStateSaver.restore(listOf("actions", "a", "Calm", true, 0))

        assertEquals(CollectionManageState.Actions(UserCollectionUi("a", "Calm", true, 0)), restored)
    }

    @Test
    fun `localizeDefaultNames renames defaults only and a locale switch only changes the name`() {
        val list = listOf(defaultUi("default.everyday_essentials", enabled = true), defaultUi(), ui("u1", name = "Mine"))
        val es = mapOf("default.everyday_essentials" to "Esenciales del día", "default.morning_start" to "Buen comienzo")
        val en = mapOf("default.everyday_essentials" to "Everyday Essentials", "default.morning_start" to "Morning Start")

        val inEs = localizeDefaultNames(list, es::get)
        val inEn = localizeDefaultNames(list, en::get)

        assertEquals(listOf("Esenciales del día", "Buen comienzo", "Mine"), inEs.map { it.name })
        assertEquals(listOf("Everyday Essentials", "Morning Start", "Mine"), inEn.map { it.name })
        assertEquals(inEs.map { it.copy(name = "") }, inEn.map { it.copy(name = "") })
    }

    @Test
    fun `localizeDefaultNames keeps the current name when no string is known`() {
        val localized = localizeDefaultNames(listOf(defaultUi("default.retired")), { null })

        assertEquals("default.retired", localized.single().name)
    }

    @Test
    fun `restored actions state for a default takes the current locale name, not the saved snapshot`() {
        val stale = CollectionManageState.Actions(defaultUi().copy(name = "Inicio de mañana"))
        val en = mapOf("default.morning_start" to "Morning Start")

        val resolved = stale.withLocalizedDefaultName(en::get) as CollectionManageState.Actions

        assertEquals("Morning Start", resolved.collection.name)
    }

    @Test
    fun `withLocalizedDefaultName leaves user groups and closed state untouched`() {
        val user = CollectionManageState.Actions(ui("u1", name = "Mine"))

        assertEquals(user, user.withLocalizedDefaultName { "X" })
        assertEquals(CollectionManageState.Closed, CollectionManageState.Closed.withLocalizedDefaultName { "X" })
    }

    @Test
    fun `duplicate verdict Ok closes the sheet`() {
        assertEquals(DuplicateOutcome.Close, CollectionNameResult.Ok("id").duplicateOutcome())
    }

    @Test
    fun `duplicate verdict CatalogNotReady keeps the sheet open with its localized message`() {
        assertEquals(
            DuplicateOutcome.ShowError(R.string.collection_name_error_catalog_not_ready),
            CollectionNameResult.CatalogNotReady.duplicateOutcome(),
        )
    }

    @Test
    fun `duplicate verdict LimitReached keeps the sheet open with the limit message`() {
        assertEquals(
            DuplicateOutcome.ShowError(R.string.collection_limit_reached),
            CollectionNameResult.LimitReached.duplicateOutcome(),
        )
    }
}
