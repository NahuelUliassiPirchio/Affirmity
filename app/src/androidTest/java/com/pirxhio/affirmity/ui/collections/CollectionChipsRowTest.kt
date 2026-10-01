package com.pirxhio.affirmity.ui.collections

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.data.UserCollectionUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionChipsRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val collections = listOf(
        UserCollectionUi("a", "Calm", enabled = true, resolvedItemCount = 2),
        UserCollectionUi("b", "Focus", enabled = false, resolvedItemCount = 0),
    )

    @Test
    fun tapTogglesAndLongPressRequestsManagement() {
        val toggled = mutableListOf<String>()
        val managed = mutableListOf<String>()
        composeRule.setContent {
            CollectionChipsRow(
                collections = collections,
                onToggle = { toggled += it },
                onLongPress = { managed += it.id },
            )
        }

        composeRule.onNodeWithText("Calm (2)").performClick()
        composeRule.onNodeWithText("Focus (0)").performTouchInput { longClick() }

        assertEquals(listOf("a"), toggled)
        assertEquals(listOf("b"), managed)
    }

    @Test
    fun enabledChipIsSelectedAndDisabledChipIsNot() {
        composeRule.setContent {
            CollectionChipsRow(collections = collections, onToggle = {}, onLongPress = {})
        }

        composeRule.onNodeWithText("Calm (2)").assertIsSelected()
        composeRule.onNodeWithText("Focus (0)").assertIsNotSelected()
    }
}
