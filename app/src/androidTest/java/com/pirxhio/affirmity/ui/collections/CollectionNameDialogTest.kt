package com.pirxhio.affirmity.ui.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.data.CollectionNameResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionNameDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun staysOpenOnDuplicateAndClosesOnlyOnOk() {
        val results = ArrayDeque(listOf(CollectionNameResult.Duplicate, CollectionNameResult.Ok("Calm")))
        val submitted = mutableListOf<String>()
        var done = 0
        composeRule.setContent {
            CollectionNameDialog(
                title = "New collection",
                confirmLabel = "Create",
                initialName = "",
                onSubmit = { name ->
                    submitted += name
                    results.removeFirst()
                },
                onDone = { done++ },
                onDismiss = {},
            )
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Calm")

        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()
        assertEquals(0, done)
        composeRule.onNodeWithText("New collection").assertIsDisplayed()

        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()
        assertEquals(1, done)
        assertEquals(listOf("Calm", "Calm"), submitted)
    }
}
