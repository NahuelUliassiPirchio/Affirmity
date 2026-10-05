package com.pirxhio.affirmity.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentLaunchGuardTest {

    @Test
    fun `first start is allowed`() {
        assertTrue(ConsentLaunchGuard().tryStart())
    }

    @Test
    fun `a second start while the first is running is refused`() {
        val guard = ConsentLaunchGuard()
        guard.tryStart()
        assertFalse(guard.tryStart())
    }

    @Test
    fun `after a successful finish no further start is allowed`() {
        val guard = ConsentLaunchGuard()
        guard.tryStart()
        guard.finish(succeeded = true)
        assertFalse(guard.tryStart())
    }

    @Test
    fun `after a failed finish a later start is allowed again`() {
        val guard = ConsentLaunchGuard()
        guard.tryStart()
        guard.finish(succeeded = false)
        assertTrue(guard.tryStart())
    }
}
