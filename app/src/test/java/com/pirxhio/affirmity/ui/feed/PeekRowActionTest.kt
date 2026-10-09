package com.pirxhio.affirmity.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class PeekRowActionTest {
    @Test
    fun `collapsed sheet expands on tap`() {
        assertEquals(PeekRowAction.Expand, peekRowAction(isExpanded = false, isExpanding = false))
    }

    @Test
    fun `tap while the expand animation is in flight does nothing`() {
        assertEquals(PeekRowAction.None, peekRowAction(isExpanded = false, isExpanding = true))
    }

    @Test
    fun `settled expanded sheet updates the feed on tap`() {
        assertEquals(PeekRowAction.UpdateFeed, peekRowAction(isExpanded = true, isExpanding = false))
    }

    @Test
    fun `settled expanded wins over a stale expanding flag`() {
        assertEquals(PeekRowAction.UpdateFeed, peekRowAction(isExpanded = true, isExpanding = true))
    }
}
