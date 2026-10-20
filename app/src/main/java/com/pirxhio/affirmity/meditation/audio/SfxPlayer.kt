package com.pirxhio.affirmity.meditation.audio

/**
 * Low-latency, overlap-capable backend for short SFX clips (breath sounds). Implemented over
 * SoundPool on Android; faked in JVM tests. A clip id is the raw resource name.
 */
interface SfxPlayer {
    /** True once the clip is decoded and playable. Unloaded clips must be skipped, not queued. */
    fun isLoaded(clipId: String): Boolean

    /** Starts the clip at [volume] (0f..1f) and returns a stream handle, or null if it did not start. */
    fun play(clipId: String, volume: Float): Int?

    fun setVolume(streamId: Int, volume: Float)

    /** Pauses every live stream. */
    fun autoPause()

    /** Resumes every stream paused by [autoPause]. */
    fun autoResume()

    fun stop(streamId: Int)

    fun release()
}
