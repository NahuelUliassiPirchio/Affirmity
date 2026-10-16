package com.pirxhio.affirmity.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyTrackerLayoutTest {

    // --- tier boundaries -------------------------------------------------------------------

    @Test
    fun tier_isStripBelow92() {
        listOf(56f, 73f, 74f, 91f, 91.9f).forEach {
            assertEquals("height $it", WidgetTier.Strip, tierForHeight(it))
        }
    }

    @Test
    fun tier_isRegularFrom92To149() {
        listOf(92f, 120f, 149f, 149.9f).forEach {
            assertEquals("height $it", WidgetTier.Regular, tierForHeight(it))
        }
    }

    @Test
    fun tier_isTallFrom150() {
        listOf(150f, 210f, 250f).forEach {
            assertEquals("height $it", WidgetTier.Tall, tierForHeight(it))
        }
    }

    // --- letters ---------------------------------------------------------------------------

    @Test
    fun letters_dropBelow74InStripOnly() {
        assertFalse(computeTrackerLayout(320f, 56f, streakVisible = true).showLetters)
        assertFalse(computeTrackerLayout(320f, 73f, streakVisible = true).showLetters)
        assertTrue(computeTrackerLayout(320f, 74f, streakVisible = true).showLetters)
        assertTrue(computeTrackerLayout(320f, 91f, streakVisible = true).showLetters)
        assertTrue(computeTrackerLayout(320f, 92f, streakVisible = true).showLetters)
        assertTrue(computeTrackerLayout(380f, 210f, streakVisible = true).showLetters)
    }

    // --- streak placement ------------------------------------------------------------------

    @Test
    fun streak_leadsTheRowInStrip() {
        val layout = computeTrackerLayout(320f, 84f, streakVisible = true)
        assertEquals(StreakPlacement.LeadingRow, layout.streakPlacement)
    }

    @Test
    fun streak_sitsOverLegendInRegular() {
        val layout = computeTrackerLayout(320f, 120f, streakVisible = true)
        assertEquals(StreakPlacement.BesideGrid, layout.streakPlacement)
    }

    @Test
    fun streak_becomesHeaderInTall() {
        val layout = computeTrackerLayout(380f, 210f, streakVisible = true)
        assertEquals(StreakPlacement.Header, layout.streakPlacement)
    }

    @Test
    fun streak_isHiddenWhenNotVisibleInEveryTier() {
        listOf(56f, 84f, 120f, 210f).forEach {
            assertEquals("height $it", StreakPlacement.None, computeTrackerLayout(320f, it, streakVisible = false).streakPlacement)
        }
    }

    // --- cell diameter ---------------------------------------------------------------------

    @Test
    fun cell_isCappedPerTier() {
        // Very wide and roomy: width and height never limit, so the tier cap decides.
        assertEquals(34, computeTrackerLayout(1000f, 91f, streakVisible = false).cellDiameter)
        assertEquals(40, computeTrackerLayout(1000f, 149f, streakVisible = false).cellDiameter)
        assertEquals(48, computeTrackerLayout(1000f, 400f, streakVisible = false).cellDiameter)
    }

    @Test
    fun cell_isLimitedByColumnWidth() {
        // Regular, narrow (<280): grid = 260 - 28 - 70 - 12 = 150 -> col ~21.43 -> col - 6 ring allowance = 15.4 -> 15.
        assertEquals(15, computeTrackerLayout(260f, 140f, streakVisible = false).cellDiameter)
    }

    @Test
    fun cell_isLimitedByHeightLeftAfterLetters() {
        // Regular at 92dp: pad 14 -> 92 - 28 - 21 - 6 = 37 of height, but the column and cap allow
        // more, so the height decides.
        val withLetters = computeTrackerLayout(1000f, 92f, streakVisible = false)
        assertEquals(37, withLetters.cellDiameter)
        // Strip without letters (73dp, pad 10): 73 - 20 - 0 - 6 = 47 -> capped at 34 instead.
        assertEquals(34, computeTrackerLayout(1000f, 73f, streakVisible = false).cellDiameter)
        // Strip without letters at the minimum 56dp: 56 - 20 - 6 = 30.
        assertEquals(30, computeTrackerLayout(1000f, 56f, streakVisible = false).cellDiameter)
    }

    @Test
    fun cell_neverDropsBelowTenDp() {
        assertEquals(10, computeTrackerLayout(100f, 56f, streakVisible = true).cellDiameter)
    }

    @Test
    fun streakInStrip_reducesGridWidth() {
        val without = computeTrackerLayout(260f, 56f, streakVisible = false).cellDiameter
        val with = computeTrackerLayout(260f, 56f, streakVisible = true).cellDiameter
        assertTrue("with=$with without=$without", with < without)
    }

    @Test
    fun padding_shrinksBelow76() {
        assertEquals(10f, computeTrackerLayout(320f, 75f, streakVisible = false).padding)
        assertEquals(14f, computeTrackerLayout(320f, 76f, streakVisible = false).padding)
    }

    @Test
    fun letterSize_followsCellDiameterWithinBounds() {
        // Strip letters are capped at 11sp to fit their reserved line; regular/tall go up to 13sp.
        assertEquals(11, computeTrackerLayout(1000f, 91f, streakVisible = false).letterSizeSp)
        assertEquals(13, computeTrackerLayout(1000f, 149f, streakVisible = false).letterSizeSp)
        assertEquals(13, computeTrackerLayout(1000f, 400f, streakVisible = false).letterSizeSp)
        assertEquals(10, computeTrackerLayout(100f, 56f, streakVisible = false).letterSizeSp)
    }

    // --- clipping guards ---------------------------------------------------------------------

    @Test
    fun tallHeader_fitsStreakNumberAndCaption() {
        assertTrue(
            "needs ${tallHeaderContentHeightDp()} of $TALL_HEADER_HEIGHT_DP",
            tallHeaderContentHeightDp() <= TALL_HEADER_HEIGHT_DP,
        )
    }

    @Test
    fun tallGrid_reservesTheSameHeaderHeightTheUiDraws() {
        // At the 150dp floor: 150 - 28 - header - 12 - 21 - 6 left for the cells.
        val expected = (150f - 28f - TALL_HEADER_HEIGHT_DP - 12f - 21f - 6f).toInt()
        assertEquals(expected.coerceAtMost(48), computeTrackerLayout(1000f, 150f, streakVisible = true).cellDiameter)
    }

    @Test
    fun sideColumn_fitsTheContentHeightAtEveryHeightWithAStreak() {
        for (narrow in listOf(260f, 320f, 400f)) {
            var h = 56f
            while (h <= 300f) {
                val layout = computeTrackerLayout(narrow, h, streakVisible = true)
                if (layout.tier != WidgetTier.Tall) {
                    val content = h - 2 * layout.padding
                    assertTrue(
                        "w=$narrow h=$h column=${layout.sideColumnHeight} content=$content",
                        layout.sideColumnHeight <= content,
                    )
                }
                h += 1f
            }
        }
    }

    @Test
    fun sideColumn_atRegularFloorIncludesStreakAndLegend() {
        val withStreak = computeTrackerLayout(320f, 92f, streakVisible = true)
        val without = computeTrackerLayout(320f, 92f, streakVisible = false)
        assertTrue(withStreak.sideColumnHeight > without.sideColumnHeight)
    }

    @Test
    fun stripStreak_fitsThreeDigitsInReservedWidth() {
        assertTrue(
            "needs ${stripStreakContentWidthDp(3)} of $STRIP_STREAK_WIDTH_DP",
            stripStreakContentWidthDp(3) <= STRIP_STREAK_WIDTH_DP,
        )
    }

    @Test
    fun stripStreak_reservesItsWidthFromTheGrid() {
        // 1000 wide strip is height/cap bound; use a narrow one so the width decides.
        val without = computeTrackerLayout(260f, 91f, streakVisible = false).cellDiameter
        val with = computeTrackerLayout(260f, 91f, streakVisible = true).cellDiameter
        // Reserved: STRIP_STREAK_WIDTH_DP + 12 -> (260-20... ) must shrink the column by that / 7.
        assertTrue("with=$with without=$without", with < without)
    }

    @Test
    fun stripLetters_reserveRoomForTheirLineAtTheThresholdHeights() {
        for (h in listOf(74f, 75f)) {
            val layout = computeTrackerLayout(1000f, h, streakVisible = false)
            val lettersLine = 1.2f * layout.letterSizeSp + 4f
            // 20 padding + ring allowance + letters line + cell must fit the height.
            assertTrue(
                "h=$h cell=${layout.cellDiameter} letters=$lettersLine",
                layout.cellDiameter + 6f + lettersLine + 2 * layout.padding <= h + 0.5f,
            )
        }
    }

    // --- accessibility ---------------------------------------------------------------------

    private val strings = TrackerA11yStrings(
        bothDone = "affirmed and meditated",
        onlyAffirm = "affirmed",
        onlyMeditate = "meditated",
        nothing = "nothing logged",
        healed = "no activity, saved by a streak healer",
        today = "today",
        streakUnit = "day streak",
        todayDone = "today done",
        todayPending = "today still open",
        startToday = "Start today",
    )

    @Test
    fun cellDescription_bothDone() {
        assertEquals(
            "Wednesday: affirmed and meditated",
            cellDescription("Wednesday", false, true, true, false, strings),
        )
    }

    @Test
    fun cellDescription_singleHabitAndNothing() {
        assertEquals("Monday: affirmed", cellDescription("Monday", false, true, false, false, strings))
        assertEquals("Monday: meditated", cellDescription("Monday", false, false, true, false, strings))
        assertEquals("Monday: nothing logged", cellDescription("Monday", false, false, false, false, strings))
    }

    @Test
    fun cellDescription_todayIsPrefixed() {
        assertEquals(
            "today, Friday: affirmed",
            cellDescription("Friday", true, true, false, false, strings),
        )
    }

    @Test
    fun cellDescription_healedWinsOverCompletionFlags() {
        assertEquals(
            "Tuesday: no activity, saved by a streak healer",
            cellDescription("Tuesday", false, true, true, true, strings),
        )
    }

    @Test
    fun widgetDescription_readsStreakAndTodayStatus() {
        assertEquals(
            "12 day streak, today done",
            widgetDescription(true, 12, true, strings),
        )
        assertEquals(
            "11 day streak, today still open",
            widgetDescription(true, 11, false, strings),
        )
    }

    @Test
    fun widgetDescription_withoutStreakReadsOnlyStatus() {
        assertEquals("today still open", widgetDescription(true, 0, false, strings))
    }

    @Test
    fun widgetDescription_emptyStateReadsPrompt() {
        assertEquals("Start today", widgetDescription(false, 0, false, strings))
    }
}
