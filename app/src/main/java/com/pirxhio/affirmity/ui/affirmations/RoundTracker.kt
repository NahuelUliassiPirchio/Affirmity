package com.pirxhio.affirmity.ui.affirmations

/** A feed this small is not worth a "round": an interstitial every few swipes would be spam. */
const val MIN_ROUND_SIZE = 10

/** Virtual pages per feed item: the pager is `feedSize * LOOP_MULTIPLIER` pages long. */
const val LOOP_MULTIPLIER = 10_000

/**
 * Pure bookkeeping for "the user has seen every affirmation in their current feed".
 *
 * Tracks the distinct ids the user saw during the current round. Set semantics make backward
 * swipes and wrap-around revisits harmless. A card counts as seen when the pager settles on it, and
 * also when it flew past in a forward fling shorter than the feed (so a fast swipe from card 4 to
 * card 8 counts 5..7 too). Backward moves, jumps of a full feed or more, and the first settle after
 * a reset count only the landed card. Progress is scoped to the exact feed id list: any change
 * (hide, sources, randomize/seed, shrink or grow) discards it and the remembered previous page, so a
 * stale set can never complete a round for a feed the user did not actually walk through.
 */
class RoundTracker(private val minRoundSize: Int = MIN_ROUND_SIZE) {

    private var feedIds: List<String> = emptyList()
    private val seen = mutableSetOf<String>()
    private var previousPage: Int? = null

    /** Distinct cards counted so far in the current round; exposed for tests. */
    internal val seenCount: Int get() = seen.size

    /**
     * Records that the pager settled on virtual [settledPage] of [currentFeedIds]. Returns true
     * exactly once per round: on the settle that completes it. The id the round ended on seeds the
     * next round.
     */
    fun onSettled(currentFeedIds: List<String>, settledPage: Int): Boolean {
        if (currentFeedIds != feedIds) {
            feedIds = currentFeedIds
            seen.clear()
            previousPage = null
        }
        val feedSize = currentFeedIds.size
        val index = settledAffirmationIndex(settledPage, feedSize) ?: return false
        val id = currentFeedIds[index]

        val previous = previousPage
        previousPage = settledPage

        var addedAny = seen.add(id)
        if (previous != null) {
            val jump = settledPage - previous
            if (jump > 1 && jump < feedSize) {
                for (page in previous + 1 until settledPage) {
                    if (seen.add(currentFeedIds[page % feedSize])) addedAny = true
                }
            }
        }
        if (!addedAny) return false

        val distinct = currentFeedIds.toSet()
        if (distinct.size < minRoundSize || !seen.containsAll(distinct)) return false

        seen.clear()
        seen.add(id)
        return true
    }
}

/**
 * Maps a pager page (virtual, wrapped over a huge range) to the feed index it displays. Null for an
 * empty feed or a negative page, so callers never divide by zero or index out of range.
 */
fun settledAffirmationIndex(page: Int, feedSize: Int): Int? =
    if (feedSize <= 0 || page < 0) null else page % feedSize

/** Middle of the virtual range, aligned so it lands on feed index 0. */
fun centeredStartPage(feedSize: Int): Int {
    val virtualPageCount = feedSize * LOOP_MULTIPLIER
    return virtualPageCount / 2 - (virtualPageCount / 2) % feedSize
}

/**
 * Page to jump to when the pager has been left without room to scroll forward, else null.
 *
 * Compose clamps `currentPage` to the last page when `pageCount` shrinks (it never leaves it
 * above the count), so "stuck" shows up as `currentPage` within the final cycle, not past the end.
 * The target keeps the same feed index, so the card on screen does not change.
 */
fun recenteredPageOrNull(currentPage: Int, virtualPageCount: Int, feedSize: Int): Int? {
    if (feedSize <= 0 || currentPage < virtualPageCount - feedSize) return null
    return centeredStartPage(feedSize) + currentPage % feedSize
}
