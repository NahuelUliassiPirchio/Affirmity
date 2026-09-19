package com.pirxhio.affirmity.ui.affirmations

/**
 * Which app chrome is visible for the feed's "clean screen" (immersive) mode.
 *
 * Clean mode only applies on the feed destination: if the user ends up elsewhere while the flag is
 * still set (e.g. a notification deep link), everything resolves back to visible.
 */
data class CleanScreenChrome(
    val showNavigation: Boolean,
    val showFeedSheet: Boolean,
    val showStatusOverlay: Boolean,
    val showSnackbar: Boolean,
    val showSuggestionCard: Boolean,
    val showSystemBars: Boolean,
) {
    val isImmersive: Boolean get() = !showNavigation

    companion object {
        val AllVisible = CleanScreenChrome(
            showNavigation = true,
            showFeedSheet = true,
            showStatusOverlay = true,
            showSnackbar = true,
            showSuggestionCard = true,
            showSystemBars = true,
        )

        private val Immersive = CleanScreenChrome(
            showNavigation = false,
            showFeedSheet = false,
            showStatusOverlay = false,
            showSnackbar = false,
            showSuggestionCard = false,
            showSystemBars = false,
        )

        fun resolve(isCleanScreen: Boolean, isFeedDestination: Boolean): CleanScreenChrome =
            if (isCleanScreen && isFeedDestination) Immersive else AllVisible
    }
}
