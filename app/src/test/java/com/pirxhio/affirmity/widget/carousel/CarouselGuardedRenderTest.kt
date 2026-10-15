package com.pirxhio.affirmity.widget.carousel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselGuardedRenderTest {

    private val account = CarouselAccount.of("A")
    private val stored = CarouselState(
        source = CarouselSource.Favorites, order = listOf("x"), lastText = "Free text", lastSubtitle = "s",
        lastFree = true, lastAccount = account,
    )

    @Test
    fun `a successful render is returned untouched`() = runTest {
        val ok = RenderOutcome(stored, CarouselFrame("Shown", null, null, true))

        assertSame(ok, renderGuarded(stored, { account }) { ok })
    }

    @Test
    fun `a failing render replays the last free frame of the same account`() = runTest {
        val out = renderGuarded(stored, { account }) { throw IllegalStateException("boom") }

        assertEquals(CarouselFrame("Free text", "s", null, true), out.frame)
    }

    @Test
    fun `a failing render never replays another account's frame`() = runTest {
        val out = renderGuarded(stored, { CarouselAccount.of("B") }) { throw IllegalStateException("boom") }

        assertNull(out.frame)
        assertNull(out.state.lastText)
        assertEquals(stored.order, out.state.order)
    }

    @Test
    fun `a failure while resolving the account shows the placeholder and drops the stored frame`() = runTest {
        val out = renderGuarded(stored, { throw IllegalStateException("auth") }) { error("unreachable") }

        assertNull(out.frame)
        assertNull(out.state.lastText)
        assertNull(out.state.lastAccount)
    }

    @Test
    fun `a failure with nothing stored shows the placeholder`() = runTest {
        val out = renderGuarded(CarouselState(), { account }) { throw java.io.IOException("x") }

        assertNull(out.frame)
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        var thrown: Throwable? = null
        try {
            renderGuarded(stored, { account }) { throw CancellationException("stop") }
        } catch (e: CancellationException) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `failures are reported to the callback`() = runTest {
        var reported: Exception? = null

        renderGuarded(stored, { account }, onError = { reported = it }) { throw IllegalStateException("boom") }

        assertEquals("boom", reported?.message)
    }
}
