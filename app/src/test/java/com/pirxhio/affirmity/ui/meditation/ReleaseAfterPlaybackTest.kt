package com.pirxhio.affirmity.ui.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAfterPlaybackTest {

    private class FakePlayer(override var isPlaying: Boolean) : ReleasablePlayer {
        var releaseCount = 0
        var completion: (() -> Unit)? = null
        override fun setOnCompletion(listener: () -> Unit) {
            completion = listener
        }
        override fun release() {
            releaseCount++
        }
    }

    @Test
    fun `an idle player is released immediately`() {
        val player = FakePlayer(isPlaying = false)
        releaseAfterPlayback(player)
        assertEquals(1, player.releaseCount)
    }

    @Test
    fun `a playing player is not released until playback completes`() {
        val player = FakePlayer(isPlaying = true)
        releaseAfterPlayback(player)
        assertEquals(0, player.releaseCount)
        player.isPlaying = false
        player.completion!!.invoke()
        assertEquals(1, player.releaseCount)
    }

    @Test
    fun `a completion callback fired twice releases only once`() {
        val player = FakePlayer(isPlaying = true)
        releaseAfterPlayback(player)
        player.completion!!.invoke()
        player.completion!!.invoke()
        assertEquals(1, player.releaseCount)
    }

    @Test
    fun `an idle player never gets a completion listener`() {
        val player = FakePlayer(isPlaying = false)
        releaseAfterPlayback(player)
        assertNull(player.completion)
    }

    @Test
    fun `a player that finishes between the check and the registration is still released once`() {
        // Playback ends right after the first isPlaying read; the completion callback never fires
        // because it was registered too late.
        val player = object : ReleasablePlayer {
            var releaseCount = 0
            private var registered = false
            override val isPlaying: Boolean get() = !registered
            override fun setOnCompletion(listener: () -> Unit) {
                registered = true
            }
            override fun release() {
                releaseCount++
            }
        }
        releaseAfterPlayback(player)
        assertEquals(1, player.releaseCount)
    }

    @Test
    fun `a late completion callback after the re-check release does not release twice`() {
        var listener: (() -> Unit)? = null
        var releaseCount = 0
        val player = object : ReleasablePlayer {
            private var registered = false
            override val isPlaying: Boolean get() = !registered
            override fun setOnCompletion(listener2: () -> Unit) {
                listener = listener2
                registered = true
            }
            override fun release() {
                releaseCount++
            }
        }
        releaseAfterPlayback(player)
        listener!!.invoke()
        assertEquals(1, releaseCount)
    }

    @Test
    fun `a player whose state cannot be read is released immediately`() {
        val player = object : ReleasablePlayer {
            var released = false
            override val isPlaying: Boolean get() = throw IllegalStateException("released")
            override fun setOnCompletion(listener: () -> Unit) = Unit
            override fun release() {
                released = true
            }
        }
        releaseAfterPlayback(player)
        assertTrue(player.released)
    }
}
