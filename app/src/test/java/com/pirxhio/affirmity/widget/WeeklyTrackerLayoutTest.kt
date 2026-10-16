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
        // Regular at 92dp: pad 14 -> 92 - 28 - 19.6 - 6 = 38 of height, but the column and cap allow
        // more, so the height decides.
        val withLetters = computeTrackerLayout(1000f, 92f, streakVisible = false)
        assertEquals(38, withLetters.cellDiameter)
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

    // --- clipping guards (font scale aware) -----------------------------------------------------

    private val fontScales = listOf(1.0f, 1.3f, 2.0f)
    private fun linear(scale: Float): (Float) -> Float = { it * scale }

    private inline fun forEachLayout(crossinline check: (scale: Float, w: Float, h: Float, TrackerLayout) -> Unit) {
        for (scale in fontScales) for (w in listOf(260f, 320f, 400f)) {
            var h = 56f
            while (h <= 300f) {
                for (streak in listOf(true, false)) {
                    check(scale, w, h, computeTrackerLayout(w, h, streak, linear(scale)))
                }
                h += 1f
            }
        }
    }

    @Test
    fun tallHeader_reservationHoldsStreakAndCaptionAtEveryFontScale() {
        for (scale in fontScales) {
            val layout = computeTrackerLayout(380f, 400f, streakVisible = true, spToDp = linear(scale))
            assertEquals(WidgetTier.Tall, layout.tier)
            assertTrue(
                "scale=$scale needs ${tallHeaderContentHeightDp(linear(scale))} of ${layout.tallHeaderHeight}",
                layout.tallHeaderHeight >= tallHeaderContentHeightDp(linear(scale)),
            )
        }
    }

    @Test
    fun tallHeader_growsWithFontScale() {
        val small = computeTrackerLayout(380f, 400f, true, linear(1f)).tallHeaderHeight
        val large = computeTrackerLayout(380f, 400f, true, linear(2f)).tallHeaderHeight
        assertTrue("small=$small large=$large", large > small)
    }

    @Test
    fun tallGrid_reservesTheHeaderHeightTheUiDraws() {
        // At the 150dp floor: 150 - 28 - header - 12 - letters - 6 left for the cells.
        val layout = computeTrackerLayout(1000f, 150f, streakVisible = true)
        val expected = (150f - 28f - layout.tallHeaderHeight - 12f - (4f + 1.2f * 13f) - 6f).toInt()
        assertEquals(WidgetTier.Tall, layout.tier)
        assertEquals(expected.coerceIn(10, 48), layout.cellDiameter)
    }

    @Test
    fun tallThatCannotFitItsHeaderFallsBackToRegular() {
        val layout = computeTrackerLayout(380f, 150f, streakVisible = true, spToDp = linear(2f))
        assertEquals(WidgetTier.Regular, layout.tier)
    }

    @Test
    fun sideColumn_alwaysFitsTheContentHeight() {
        forEachLayout { scale, w, h, layout ->
            if (layout.tier == WidgetTier.Tall) return@forEachLayout
            val content = h - 2 * layout.padding
            assertTrue(
                "scale=$scale w=$w h=$h column=${layout.sideColumnHeight} content=$content",
                layout.sideColumnHeight <= content,
            )
            if (layout.legendVisible) {
                val legend = 2 * 1.2f * layout.legendSizeSp * scale + 3f
                assertTrue("scale=$scale w=$w h=$h legend=$legend content=$content", legend <= content)
            }
        }
    }

    @Test
    fun legend_isHiddenInStripWhenTwoLinesCannotFitAtTheFloor() {
        for (scale in listOf(2f, 3f)) {
            assertFalse("scale=$scale", computeTrackerLayout(320f, 56f, false, linear(scale)).legendVisible)
        }
    }

    @Test
    fun legend_isVisibleWhenItFits() {
        assertTrue(computeTrackerLayout(320f, 56f, false, linear(1f)).legendVisible)
        for (h in listOf(92f, 120f, 210f)) {
            assertTrue("h=$h", computeTrackerLayout(320f, h, true, linear(1f)).legendVisible)
        }
        assertTrue(computeTrackerLayout(380f, 400f, true, linear(2f)).legendVisible)
    }

    @Test
    fun hiddenLegend_returnsItsWidthToTheGrid() {
        // 260 x 56 at 2x: with a 2x-scaled legend (~120dp + 12 gap) the columns would be ~15dp wide
        // and the cells pinned at the floor; without it the full 240dp row is the grid.
        val hidden = computeTrackerLayout(260f, 56f, false, linear(2f))
        assertFalse(hidden.legendVisible)
        assertEquals(27, hidden.cellDiameter) // limited by height: 56 - 20 - 6 = 30 -> 80% of 34 = 27
    }

    @Test
    fun regularStreak_movesToLeadTheRowWhenTheSideColumnCannotFit() {
        val roomy = computeTrackerLayout(320f, 92f, streakVisible = true, spToDp = linear(1f))
        val large = computeTrackerLayout(320f, 92f, streakVisible = true, spToDp = linear(2f))
        assertEquals(StreakPlacement.BesideGrid, roomy.streakPlacement)
        assertEquals(StreakPlacement.LeadingRow, large.streakPlacement)
    }

    @Test
    fun sideColumn_atRegularFloorIncludesStreakAndLegend() {
        val withStreak = computeTrackerLayout(320f, 92f, streakVisible = true)
        val without = computeTrackerLayout(320f, 92f, streakVisible = false)
        assertTrue(withStreak.sideColumnHeight > without.sideColumnHeight)
    }

    @Test
    fun leadingStreak_fitsThreeDigitsAtEveryFontScale() {
        for (scale in fontScales) {
            val layout = computeTrackerLayout(320f, 84f, streakVisible = true, spToDp = linear(scale))
            assertEquals(StreakPlacement.LeadingRow, layout.streakPlacement)
            assertTrue(
                "scale=$scale needs ${stripStreakContentWidthDp(3, linear(scale))} of ${layout.leadingStreakWidth}",
                layout.leadingStreakWidth >= stripStreakContentWidthDp(3, linear(scale)),
            )
        }
    }

    @Test
    fun leadingStreak_reservesItsWidthFromTheGrid() {
        val without = computeTrackerLayout(260f, 91f, streakVisible = false).cellDiameter
        val with = computeTrackerLayout(260f, 91f, streakVisible = true).cellDiameter
        assertTrue("with=$with without=$without", with < without)
    }

    @Test
    fun grid_neverGoesBelowTheFloorAtAnyFontScale() {
        forEachLayout { scale, w, h, layout ->
            assertTrue("scale=$scale w=$w h=$h dia=${layout.cellDiameter}", layout.cellDiameter >= 10)
        }
    }

    @Test
    fun lettersAreDroppedInsteadOfShrinkingCellsBelowTheFloor() {
        // Regular, 92dp, 3x fonts: 64 content - letters (~51) - ring leaves < 10 for cells.
        val layout = computeTrackerLayout(400f, 92f, streakVisible = false, spToDp = linear(3f))
        assertFalse(layout.showLetters)
        assertTrue(layout.cellDiameter >= 10)
    }

    @Test
    fun legendFontStepsDownRatherThanClipping() {
        val normal = computeTrackerLayout(320f, 92f, streakVisible = false, spToDp = linear(1f))
        val large = computeTrackerLayout(320f, 92f, streakVisible = false, spToDp = linear(2f))
        assertTrue(large.legendSizeSp < normal.legendSizeSp)
        assertTrue(large.legendSizeSp >= 9)
    }

    @Test
    fun legendWidthReservationGrowsWithFontScale() {
        // Wide enough that the width, not the height, limits the cells.
        val normal = computeTrackerLayout(300f, 140f, streakVisible = false, spToDp = linear(1f)).cellDiameter
        val large = computeTrackerLayout(300f, 140f, streakVisible = false, spToDp = linear(1.3f)).cellDiameter
        assertTrue("normal=$normal large=$large", large < normal)
    }

    @Test
    fun stripLetters_reserveRoomForTheirLineAtTheThresholdHeights() {
        for (h in listOf(74f, 75f)) {
            val layout = computeTrackerLayout(1000f, h, streakVisible = false)
            val lettersLine = 1.2f * layout.letterSizeSp + 4f
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
