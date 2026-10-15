package com.pirxhio.affirmity.widget.carousel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Answers [first] once, then [then] for every later call (a sign-out or switch mid-load). */
    private fun switchingAccount(first: String, then: String): () -> String {
        var calls = 0
        return { if (calls++ == 0) first else then }
    }

    @Test
    fun `a render whose account changed while loading is discarded`() = runTest {
        val loaded = RenderOutcome(stored.copy(order = listOf("y")), CarouselFrame("A's private text", null, null, false))

        val out = renderGuarded(stored, switchingAccount(account, CarouselAccount.of("B"))) { loaded }

        assertNull(out.frame)
        assertTrue(out.stale)
    }

    @Test
    fun `a failed render whose account changed while loading never replays the old account's frame`() = runTest {
        val out = renderGuarded(stored, switchingAccount(account, CarouselAccount.SIGNED_OUT)) {
            throw IllegalStateException("boom")
        }

        assertNull(out.frame)
        assertTrue(out.stale)
    }

    @Test
    fun `an account that cannot be resolved after loading discards the render`() = runTest {
        var calls = 0
        val out = renderGuarded(stored, { if (calls++ == 0) account else throw IllegalStateException("auth") }) {
            RenderOutcome(stored, CarouselFrame("Shown", null, null, true))
        }

        assertNull(out.frame)
        assertTrue(out.stale)
    }

    @Test
    fun `a render for an unchanged account is not stale`() = runTest {
        val out = renderGuarded(stored, { account }) { RenderOutcome(stored, CarouselFrame("Shown", null, null, true)) }

        assertFalse(out.stale)
    }
}
