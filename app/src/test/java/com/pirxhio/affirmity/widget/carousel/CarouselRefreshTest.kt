package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarouselRefreshTest {

    private fun key(
        tier: AccessTier = AccessTier.FREE,
        favorites: List<String> = emptyList(),
    ) = CarouselRefreshKey(tier, durable = emptyMap(), timed = emptyMap(), favoriteIds = favorites)

    private suspend fun collect(steps: List<Pair<Long, CarouselRefreshKey>>): List<CarouselRefreshKey> {
        val seen = mutableListOf<CarouselRefreshKey>()
        flow {
            for ((wait, k) in steps) {
                delay(wait)
                emit(k)
            }
        }.carouselRefreshTriggers().collect { seen += it }
        return seen
    }

    @Test
    fun `an unchanged key never triggers a refresh`() = runTest {
        val seen = collect(listOf(0L to key(), 1_000L to key(), 1_000L to key()))

        // Only the first emission is new; the repeats are filtered out.
        assertEquals(listOf(key()), seen)
    }

    @Test
    fun `a burst of changes within the debounce window refreshes once with the last key`() = runTest {
        val seen = collect(
            listOf(
                0L to key(favorites = listOf("a")),
                100L to key(favorites = listOf("a", "b")),
                100L to key(favorites = listOf("a", "b", "c")),
            ),
        )

        assertEquals(listOf(key(favorites = listOf("a", "b", "c"))), seen)
    }

    @Test
    fun `changes more than 500 ms apart each refresh`() = runTest {
        val seen = collect(
            listOf(
                0L to key(favorites = listOf("a")),
                600L to key(tier = AccessTier.PRO, favorites = listOf("a")),
            ),
        )

        assertEquals(2, seen.size)
        assertEquals(AccessTier.PRO, seen.last().tier)
    }

    @Test
    fun `the refresh waits for the debounce window`() = runTest {
        val emittedAt = mutableListOf<Long>()
        val job = launch {
            flow {
                emit(key(favorites = listOf("a")))
                awaitCancellation() // keep the upstream open, as a live state flow would
            }.carouselRefreshTriggers().collect { emittedAt += currentTime }
        }

        advanceTimeBy(499)
        runCurrent()
        assertEquals(emptyList<Long>(), emittedAt)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(500L), emittedAt)
        job.cancel()
    }
}
