package com.pirxhio.affirmity.ui.affirmations

/** Descending list of candidate text sizes from [largest] to [smallest] (inclusive). */
fun shareTextSizeCandidates(largest: Float, smallest: Float, step: Float): List<Float> {
    val sizes = generateSequence(largest) { it - step }.takeWhile { it > smallest }.toMutableList()
    sizes += smallest
    return sizes
}

/** Largest candidate for which [fits] holds; the smallest candidate if none does. */
fun fitTextSize(descendingCandidates: List<Float>, fits: (Float) -> Boolean): Float =
    descendingCandidates.firstOrNull(fits) ?: descendingCandidates.last()
