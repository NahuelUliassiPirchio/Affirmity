package com.pirxhio.affirmity.widget.carousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.ui.groups.GroupBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A long option title must not squeeze the access badge into a vertical strip. Emulator only. */
class CarouselOptionRowLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longTitle_keepsTheBadgeLabelOnOneLine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val badgeLabel = context.getString(R.string.affirmation_group_badge_partial)
        val option = SourceOption(
            CarouselSource.Favorites,
            SourceTitle.Text("Motivation, discipline, and responsibility, and a bit more"),
            GroupBadge.PARTIALLY_LOCKED,
        )

        composeRule.setContent {
            Box(Modifier.width(320.dp)) { OptionRow(option, onPick = {}) }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        val action = composeRule.onNodeWithText(badgeLabel, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action ?: error("Badge text exposes no layout")
        assertTrue(action(layouts))
        assertEquals(1, layouts.single().lineCount)
    }
}
