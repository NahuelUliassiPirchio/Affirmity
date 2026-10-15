package com.pirxhio.affirmity.widget.carousel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CarouselViewForRenderTest {

    private val accountA = CarouselAccount.of("A")
    private val accountB = CarouselAccount.of("B")
    private val frame = CarouselFrame("private text", null, null, isFreeTier = true)
    private val themed = CarouselTheme.Default.copy(title = SourceTitle.Text("Favorites"))
    private val forA = CarouselView(frame, themed, nonce = 3, account = accountA)

    @Test
    fun `a matching account renders the frame`() {
        assertSame(forA, viewForRender(forA, accountA))
    }

    @Test
    fun `another account renders the placeholder in the default theme`() {
        val out = viewForRender(forA, accountB)

        assertNull(out?.frame)
        assertNull(out?.account)
        assertEquals(CarouselTheme.Default, out?.theme)
        assertEquals(3L, out?.nonce)
    }

    @Test
    fun `the placeholder passes through for any account`() {
        val placeholder = CarouselView(null, CarouselTheme.Default, nonce = 1)

        assertSame(placeholder, viewForRender(placeholder, accountB))
        assertSame(placeholder, viewForRender(placeholder, null))
    }

    @Test
    fun `an unresolvable current account renders the placeholder`() {
        val out = viewForRender(forA, null)

        assertNull(out?.frame)
        assertEquals(CarouselTheme.Default, out?.theme)
    }

    @Test
    fun `a frame without an account is never drawn`() {
        assertNull(viewForRender(forA.copy(account = null), accountA)?.frame)
    }

    @Test
    fun `a missing view stays missing`() {
        assertNull(viewForRender(null, accountA))
    }
}
