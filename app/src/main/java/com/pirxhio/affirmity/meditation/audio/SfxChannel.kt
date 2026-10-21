package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.meditation.AudioChannel

/** Where a `PlayAudio` command goes. */
enum class PlayAudioRoute { Sfx, Sound, Drop }

/** What the executor must do when a volume ramp finishes (see [VoicePlayerLifecycle.onRampFinished]). */
data class RampFinishPlan(
    /** The ramp reached exactly 0: pause the player and drop its channel assignment. */
    val silenced: Boolean,
    /** A voice went silent: end its SFX duck and restore any bed it was ducking. */
    val endVoice: Boolean,
    /** Release the voice player (must run while the id is still on the VOICE channel). */
    val releasePlayer: Boolean,
)

/** When a per-play voice player should be released instead of kept for reuse. */
object VoicePlayerLifecycle {
    fun shouldRelease(releaseVoiceAfterPlay: Boolean, channel: AudioChannel?): Boolean =
        releaseVoiceAfterPlay && channel == AudioChannel.VOICE

    /**
     * A ramp reaching silence on a VOICE channel is a voice ending without a completion callback:
     * SFX must un-duck and the player must not leak, same as natural completion or a hard stop.
     */
    fun onRampFinished(toVolume: Float, channel: AudioChannel?, releaseVoiceAfterPlay: Boolean): RampFinishPlan {
        if (toVolume != 0f) return RampFinishPlan(silenced = false, endVoice = false, releasePlayer = false)
        return RampFinishPlan(
            silenced = true,
            endVoice = channel == AudioChannel.VOICE,
            releasePlayer = shouldRelease(releaseVoiceAfterPlay, channel),
        )
    }
}

/**
 * The SFX side of the audio executor, free of Android types: routing, ducking against voice,
 * pause/resume and release ordering over an [SfxPlayer].
 */
class SfxChannel(
    private val player: SfxPlayer,
) {
    private class LiveStream(val streamId: Int, val clipId: String)

    private val liveStreams = ArrayDeque<LiveStream>()
    private val voicesPlaying = mutableSetOf<String>()

    private val ducked: Boolean get() = voicesPlaying.isNotEmpty()

    /** Plays [clipId] at the current duck gain. Returns false (and plays nothing) if not loaded. */
    fun play(clipId: String): Boolean {
        if (!player.isLoaded(clipId)) return false
        val stream = player.play(clipId, SfxDuckPolicy.gain(ducked)) ?: return true
        liveStreams.addLast(LiveStream(stream, clipId))
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

    /** Ends every live stream of [clipId], leaving other clips playing. */
    fun stop(clipId: String) {
        liveStreams.filter { it.clipId == clipId }.forEach { player.stop(it.streamId) }
        liveStreams.removeAll { it.clipId == clipId }
    }

    fun stopAll() {
        liveStreams.forEach { player.stop(it.streamId) }
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
        liveStreams.forEach { player.setVolume(it.streamId, gain) }
    }

    companion object {
        const val SFX_ID_PREFIX = "whm_sfx_"
        private const val MAX_TRACKED_STREAMS = 8

        fun isSfxId(audioId: String): Boolean = audioId.startsWith(SFX_ID_PREFIX)

        /** SFX bypass the cue-sound preference (they have their own breath-sound toggle in the
         * definition); every other `PlayAudio` stays gated by it. */
        fun route(audioId: String, cueSoundEnabled: Boolean): PlayAudioRoute = when {
            audioId.startsWith(SFX_ID_PREFIX) -> PlayAudioRoute.Sfx
            cueSoundEnabled -> PlayAudioRoute.Sound
            else -> PlayAudioRoute.Drop
        }
    }
}
