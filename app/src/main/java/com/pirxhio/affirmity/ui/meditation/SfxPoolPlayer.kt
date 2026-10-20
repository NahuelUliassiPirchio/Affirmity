package com.pirxhio.affirmity.ui.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.pirxhio.affirmity.meditation.audio.SfxPlayer

/**
 * [SoundPool]-backed [SfxPlayer]. Every clip in [resourcesByClipId] (`@RawRes` ids) is loaded at
 * construction; a clip that has not finished loading is reported as not loaded and skipped by
 * the caller. Clips are tiny (SoundPool caps a decoded sample at about 1 MB).
 */
class SfxPoolPlayer(
    context: Context,
    resourcesByClipId: Map<String, Int>,
) : SfxPlayer {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundIdByClip = mutableMapOf<String, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) synchronized(loadedSoundIds) { loadedSoundIds += soundId }
        }
        resourcesByClipId.forEach { (clipId, resId) ->
            soundIdByClip[clipId] = pool.load(context, resId, 1)
        }
    }

    override fun isLoaded(clipId: String): Boolean {
        val soundId = soundIdByClip[clipId] ?: return false
        return synchronized(loadedSoundIds) { soundId in loadedSoundIds }
    }

    override fun play(clipId: String, volume: Float): Int? {
        val soundId = soundIdByClip[clipId] ?: return null
        val stream = pool.play(soundId, volume, volume, 1, 0, 1f)
        return stream.takeIf { it != 0 }
    }

    override fun setVolume(streamId: Int, volume: Float) = pool.setVolume(streamId, volume, volume)

    override fun autoPause() = pool.autoPause()

    override fun autoResume() = pool.autoResume()

    override fun stop(streamId: Int) = pool.stop(streamId)

    override fun release() = pool.release()

    private companion object {
        const val MAX_STREAMS = 4
    }
}
