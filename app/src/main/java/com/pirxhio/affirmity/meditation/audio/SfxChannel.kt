package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.meditation.AudioChannel

/** Where a `PlayAudio` command goes. */
enum class PlayAudioRoute { Sfx, Sound, Drop }

/** When a per-play voice player should be released instead of kept for reuse. */
object VoicePlayerLifecycle {
    fun shouldRelease(releaseVoiceAfterPlay: Boolean, channel: AudioChannel?): Boolean =
        releaseVoiceAfterPlay && channel == AudioChannel.VOICE
}

/**
 * The SFX side of the audio executor, free of Android types: routing, ducking against voice,
 * pause/resume and release ordering over an [SfxPlayer].
 */
class SfxChannel(
    private val player: SfxPlayer,
) {
    private val liveStreams = ArrayDeque<Int>()
    private val voicesPlaying = mutableSetOf<String>()

    private val ducked: Boolean get() = voicesPlaying.isNotEmpty()

    /** Plays [clipId] at the current duck gain. Returns false (and plays nothing) if not loaded. */
    fun play(clipId: String): Boolean {
        if (!player.isLoaded(clipId)) return false
        val stream = player.play(clipId, SfxDuckPolicy.gain(ducked)) ?: return true
        liveStreams.addLast(stream)
        while (liveStreams.size > MAX_TRACKED_STREAMS) liveStreams.removeFirst()
        return true
    }

    fun voiceStarted(voiceId: String) {
        val wasDucked = ducked
        voicesPlaying += voiceId
        if (!wasDucked) applyGainToLiveStreams()
    }

    fun voiceEnded(voiceId: String) {
        if (!voicesPlaying.remove(voiceId)) return
        if (!ducked) applyGainToLiveStreams()
    }

    fun pause() = player.autoPause()

    fun resume() = player.autoResume()

    fun stopAll() {
        liveStreams.forEach { player.stop(it) }
        liveStreams.clear()
    }

    /**
     * Mandatory teardown order: in-flight volume ramps first (a ramp tick on a released player
     * throws), then the SoundPool, then the per-id MediaPlayers.
     */
    fun releaseInOrder(cancelRamps: () -> Unit, releaseMediaPlayers: () -> Unit) {
        cancelRamps()
        player.release()
        liveStreams.clear()
        voicesPlaying.clear()
        releaseMediaPlayers()
    }

    private fun applyGainToLiveStreams() {
        val gain = SfxDuckPolicy.gain(ducked)
        liveStreams.forEach { player.setVolume(it, gain) }
    }

    companion object {
        const val SFX_ID_PREFIX = "whm_sfx_"
        private const val MAX_TRACKED_STREAMS = 8

        /** SFX bypass the cue-sound preference (they have their own breath-sound toggle in the
         * definition); every other `PlayAudio` stays gated by it. */
        fun route(audioId: String, cueSoundEnabled: Boolean): PlayAudioRoute = when {
            audioId.startsWith(SFX_ID_PREFIX) -> PlayAudioRoute.Sfx
            cueSoundEnabled -> PlayAudioRoute.Sound
            else -> PlayAudioRoute.Drop
        }
    }
}
