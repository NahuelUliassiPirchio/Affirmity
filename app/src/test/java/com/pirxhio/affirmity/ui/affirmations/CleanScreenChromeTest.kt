package com.pirxhio.affirmity.ui.affirmations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanScreenChromeTest {
    @Test
    fun `normal mode shows all chrome`() {
        val chrome = CleanScreenChrome.resolve(isCleanScreen = false, isFeedDestination = true)

        assertEquals(CleanScreenChrome.AllVisible, chrome)
        assertTrue(chrome.showNavigation && chrome.showFeedSheet && chrome.showStatusOverlay)
        assertTrue(chrome.showSnackbar && chrome.showSuggestionCard && chrome.showSystemBars)
        assertFalse(chrome.isImmersive)
    }

    @Test
    fun `clean mode on the feed hides every chrome element`() {
        val chrome = CleanScreenChrome.resolve(isCleanScreen = true, isFeedDestination = true)

        assertTrue(chrome.isImmersive)
        assertFalse(chrome.showNavigation)
        assertFalse(chrome.showFeedSheet)
        assertFalse(chrome.showStatusOverlay)
        assertFalse(chrome.showSnackbar)
        assertFalse(chrome.showSuggestionCard)
        assertFalse(chrome.showSystemBars)
    }

    @Test
    fun `clean mode is ignored away from the feed so chrome can never stay hidden`() {
        val chrome = CleanScreenChrome.resolve(isCleanScreen = true, isFeedDestination = false)

        assertEquals(CleanScreenChrome.AllVisible, chrome)
    }
}
