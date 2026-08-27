package com.pirxhio.affirmity.ui.favorites

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.FavoritesOverlay
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationBackground
import com.pirxhio.affirmity.ui.theme.AffirmityTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyFavoritesShowsTheEmptyState() {
        composeTestRule.setContent {
            AffirmityTheme {
                FavoritesScreen(favorites = emptyList(), onUnfavorite = {})
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.favorites_empty_state),
        ).assertIsDisplayed()
    }

    @Test
    fun unlikeEmitsTheIdAndRemovesTheRowImmediately() {
        val affirmation = Affirmation(
            id = "favorite-1",
            title = "I choose progress",
            subtitle = "One step at a time",
            background = AffirmationBackground.Color("#000000"),
        )
        var favorites by mutableStateOf(listOf(affirmation))
        var removedId: String? = null
        composeTestRule.setContent {
            AffirmityTheme {
                FavoritesScreen(
                    favorites = favorites,
                    onUnfavorite = { id ->
                        removedId = id
                        favorites = favorites.filterNot { it.id == id }
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.favorites_unlike_content_description),
        ).performClick()

        assertEquals("favorite-1", removedId)
        composeTestRule.onAllNodesWithText(affirmation.title).assertCountEquals(0)
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.favorites_empty_state),
        ).assertIsDisplayed()
    }

    @Test
    fun systemBackDismissesTheOverlayAndRestoresThePreviousContent() {
        var showingFavorites by mutableStateOf(true)
        var dismissCalls = 0
        composeTestRule.setContent {
            AffirmityTheme {
                if (showingFavorites) {
                    FavoritesOverlay(
                        favorites = emptyList(),
                        onUnfavorite = {},
                        onDismiss = {
                            dismissCalls += 1
                            showingFavorites = false
                        },
                    )
                } else {
                    Text("Previous screen")
                }
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.favorites_title),
        ).assertIsDisplayed()

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        assertEquals(1, dismissCalls)
        composeTestRule.onNodeWithText("Previous screen").assertIsDisplayed()
    }
}
