package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.remote.OwnedSnapshot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedSnapshotTest {

    private val row = AffirmationEntity(id = "o1", title = "mine", subtitle = "", backgroundType = "color", backgroundValue = "")

    @Test
    fun `an empty cache-only emission is skipped in favour of the server one`() = runTest {
        val rows = flowOf(OwnedSnapshot(emptyList(), fromCache = true), OwnedSnapshot(listOf(row), fromCache = false))
            .firstTrustworthyOwned()

        assertEquals(listOf(row), rows)
    }

    @Test
    fun `an empty server emission is a genuinely empty collection`() = runTest {
        val rows = flowOf(OwnedSnapshot(emptyList(), fromCache = false)).firstTrustworthyOwned()

        assertEquals(emptyList<AffirmationEntity>(), rows)
    }

    @Test
    fun `a non-empty cache emission is usable offline`() = runTest {
        val rows = flowOf(OwnedSnapshot(listOf(row), fromCache = true)).firstTrustworthyOwned()

        assertEquals(listOf(row), rows)
    }

    @Test
    fun `offline with only an empty cache never resolves so the read times out as unavailable`() = runTest {
        val read = readOwned(4_000) {
            flow {
                emit(OwnedSnapshot(emptyList(), fromCache = true))
                awaitCancellation()
            }.firstTrustworthyOwned().map { it.id }
        }

        assertEquals(OwnedRead.Unavailable, read)
    }
}
