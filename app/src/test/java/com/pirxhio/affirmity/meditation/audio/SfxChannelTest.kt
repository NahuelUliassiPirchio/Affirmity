package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.meditation.AudioChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SfxChannelTest {

    private class FakePlayer(private val loaded: Set<String>, val log: MutableList<String> = mutableListOf()) : SfxPlayer {
        private var nextStream = 1
        override fun isLoaded(clipId: String) = clipId in loaded
        override fun play(clipId: String, volume: Float): Int? {
            log += "play:$clipId@$volume"
            return nextStream++
        }
        override fun setVolume(streamId: Int, volume: Float) { log += "vol:$streamId@$volume" }
        override fun autoPause() { log += "autoPause" }
        override fun autoResume() { log += "autoResume" }
        override fun stop(streamId: Int) { log += "stop:$streamId" }
        override fun release() { log += "poolRelease" }
    }

    private val clip = "whm_sfx_inhale_normal"

    private fun channel(player: FakePlayer = FakePlayer(setOf(clip, "whm_sfx_bowl"))) = SfxChannel(player) to player

    @Test
    fun `a loaded clip plays at full gain`() {
        val (channel, player) = channel()

        assertTrue(channel.play(clip))
        assertEquals(listOf("play:$clip@1.0"), player.log)
    }

    @Test
    fun `a clip that has not loaded is skipped`() {
        val (channel, player) = channel()

        assertFalse(channel.play("whm_sfx_exhale_slow"))
        assertTrue(player.log.isEmpty())
    }

    @Test
    fun `a clip starting while a voice plays is ducked at play time`() {
        val (channel, player) = channel()
        channel.voiceStarted("whm_vo_intro_1")

        channel.play(clip)

        assertEquals(listOf("play:$clip@0.42"), player.log)
    }

    @Test
    fun `live streams are ducked when a voice starts and restored when it ends`() {
        val (channel, player) = channel()
        channel.play(clip)
        player.log.clear()

        channel.voiceStarted("whm_vo_resp_01")
        channel.voiceEnded("whm_vo_resp_01")

        assertEquals(listOf("vol:1@0.42", "vol:1@1.0"), player.log)
    }

    @Test
    fun `duck state only changes on the first start and the last end`() {
        val (channel, player) = channel()
        channel.play(clip)
        player.log.clear()

        channel.voiceStarted("a")
        channel.voiceStarted("b")
        channel.voiceEnded("a")
        assertEquals(listOf("vol:1@0.42"), player.log)
        channel.voiceEnded("b")

        assertEquals(listOf("vol:1@0.42", "vol:1@1.0"), player.log)
    }

    @Test
    fun `pause maps to autoPause and resume to autoResume`() {
        val (channel, player) = channel()

        channel.pause()
        channel.resume()

        assertEquals(listOf("autoPause", "autoResume"), player.log)
    }

    @Test
    fun `release ordering is cancelRamps then pool then media players`() {
        val events = mutableListOf<String>()
        val player = object : SfxPlayer {
            override fun isLoaded(clipId: String) = true
            override fun play(clipId: String, volume: Float): Int? = null
            override fun setVolume(streamId: Int, volume: Float) = Unit
            override fun autoPause() = Unit
            override fun autoResume() = Unit
            override fun stop(streamId: Int) = Unit
            override fun release() { events += "pool" }
        }

        SfxChannel(player).releaseInOrder(
            cancelRamps = { events += "ramps" },
            releaseMediaPlayers = { events += "mediaPlayers" },
        )

        assertEquals(listOf("ramps", "pool", "mediaPlayers"), events)
    }

    @Test
    fun `stopAll stops every live stream`() {
        val (channel, player) = channel()
        channel.play(clip)
        channel.play("whm_sfx_bowl")
        player.log.clear()

        channel.stopAll()

        assertEquals(listOf("stop:1", "stop:2"), player.log)
    }

    @Test
    fun `stop ends only the live streams of that clip`() {
        val (channel, player) = channel()
        channel.play(clip)
        channel.play("whm_sfx_bowl")
        channel.play(clip)
        player.log.clear()

        channel.stop(clip)

        assertEquals(listOf("stop:1", "stop:3"), player.log)
        player.log.clear()
        channel.stopAll()
        assertEquals(listOf("stop:2"), player.log)
    }

    // --- routing ------------------------------------------------------------------------------

    @Test
    fun `sfx ids route to the sfx channel while sounds are on`() {
        assertEquals(PlayAudioRoute.Sfx, SfxChannel.route("whm_sfx_inhale_fast", cueSoundEnabled = true))
    }

    @Test
    fun `sfx ids are dropped when the in-session mute is on`() {
        assertEquals(PlayAudioRoute.Drop, SfxChannel.route("whm_sfx_inhale_fast", cueSoundEnabled = false))
    }

    @Test
    fun `non-sfx PlayAudio stays gated by the cue-sound preference`() {
        assertEquals(PlayAudioRoute.Sound, SfxChannel.route("chant_om", cueSoundEnabled = true))
        assertEquals(PlayAudioRoute.Drop, SfxChannel.route("chant_om", cueSoundEnabled = false))
    }

    // --- voice player lifecycle ---------------------------------------------------------------

    @Test
    fun `a voice player is released after play only when the presentation asks for it`() {
        assertTrue(VoicePlayerLifecycle.shouldRelease(releaseVoiceAfterPlay = true, channel = AudioChannel.VOICE))
        assertFalse(VoicePlayerLifecycle.shouldRelease(releaseVoiceAfterPlay = false, channel = AudioChannel.VOICE))
    }

    @Test
    fun `non-voice players are never released after play`() {
        listOf(AudioChannel.SOUND, AudioChannel.AMBIENT, null).forEach {
            assertFalse("channel=$it", VoicePlayerLifecycle.shouldRelease(releaseVoiceAfterPlay = true, channel = it))
        }
    }

    // --- ramp finished (voice faded to silence) ------------------------------------------------

    @Test
    fun `a voice ramp reaching silence ends the voice duck and releases the player when configured`() {
        val plan = VoicePlayerLifecycle.onRampFinished(toVolume = 0f, channel = AudioChannel.VOICE, releaseVoiceAfterPlay = true)

        assertTrue(plan.silenced)
        assertTrue(plan.endVoice)
        assertTrue(plan.releasePlayer)
    }

    @Test
    fun `a voice ramp reaching silence keeps the player when the presentation does not release voices`() {
        val plan = VoicePlayerLifecycle.onRampFinished(toVolume = 0f, channel = AudioChannel.VOICE, releaseVoiceAfterPlay = false)

        assertTrue(plan.silenced)
        assertTrue(plan.endVoice)
        assertFalse(plan.releasePlayer)
    }

    @Test
    fun `a non-voice ramp reaching silence never ends a voice or releases the player`() {
        listOf(AudioChannel.SOUND, AudioChannel.AMBIENT, null).forEach {
            val plan = VoicePlayerLifecycle.onRampFinished(toVolume = 0f, channel = it, releaseVoiceAfterPlay = true)

            assertTrue("channel=$it", plan.silenced)
            assertFalse("channel=$it", plan.endVoice)
            assertFalse("channel=$it", plan.releasePlayer)
        }
    }

    @Test
    fun `a ramp that does not reach silence does nothing`() {
        val plan = VoicePlayerLifecycle.onRampFinished(toVolume = 0.25f, channel = AudioChannel.VOICE, releaseVoiceAfterPlay = true)

        assertFalse(plan.silenced)
        assertFalse(plan.endVoice)
        assertFalse(plan.releasePlayer)
    }
}
