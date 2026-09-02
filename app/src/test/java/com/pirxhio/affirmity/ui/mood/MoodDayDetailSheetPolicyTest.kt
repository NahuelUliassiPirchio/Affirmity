package com.pirxhio.affirmity.ui.mood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodDayDetailSheetPolicyTest {

    @Test
    fun `notification body starts genuinely unselected and cannot save`() {
        val selection = initialMoodSelection(null)

        assertNull(selection)
        assertFalse(canSaveMoodSelection(selection))
    }

    @Test
    fun `existing mood remains selected and can save`() {
        val selection = initialMoodSelection(3)

        assertEquals(3, selection)
        assertTrue(canSaveMoodSelection(selection))
    }

    @Test
    fun `new notification event resets unsaved selection from its own payload`() {
        val firstAction = initialMoodSelectionForEvent(eventKey = 1, initialMoodValue = 2)
        val unsavedEdit = firstAction.copy(selectedMood = 5)

        val repeatedBody = initialMoodSelectionForEvent(eventKey = 2, initialMoodValue = null)
        val repeatedSameAction = initialMoodSelectionForEvent(eventKey = 3, initialMoodValue = 2)

        assertEquals(5, unsavedEdit.selectedMood)
        assertNull(repeatedBody.selectedMood)
        assertEquals(2, repeatedSameAction.selectedMood)
    }
}
