package com.pirxhio.affirmity.ui.affirmations

/** A feed this small is not worth a "round": an interstitial every few swipes would be spam. */
const val MIN_ROUND_SIZE = 10

/** Virtual pages per feed item: the pager is `feedSize * LOOP_MULTIPLIER` pages long. */
const val LOOP_MULTIPLIER = 10_000

/**
 * Pure bookkeeping for "the user has seen every affirmation in their current feed".
 *
 * Tracks the distinct ids the user settled on during the current round. Set semantics make
 * backward swipes and wrap-around revisits harmless. Progress is scoped to the exact feed id
 * list: any change (hide, sources, randomize/seed, shrink or grow) discards it, so a stale set
 * can never complete a round for a feed the user did not actually walk through.
 */
class RoundTracker(private val minRoundSize: Int = MIN_ROUND_SIZE) {

    private var feedIds: List<String> = emptyList()
    private val seen = mutableSetOf<String>()

    /**
     * Records that the pager settled on [settledIndex] (already reduced modulo the feed size)
     * of [currentFeedIds]. Returns true exactly once per round: on the settle that completes it.
     * The id the round ended on seeds the next round.
     */
    fun onSettled(currentFeedIds: List<String>, settledIndex: Int): Boolean {
        if (currentFeedIds != feedIds) {
            feedIds = currentFeedIds
            seen.clear()
        }
        val id = currentFeedIds.getOrNull(settledIndex) ?: return false
        val isNewInRound = seen.add(id)
        if (!isNewInRound) return false

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
