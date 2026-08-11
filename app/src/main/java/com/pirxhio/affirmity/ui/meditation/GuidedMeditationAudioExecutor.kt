package com.pirxhio.affirmity.ui.meditation

import android.content.Context
import android.media.MediaPlayer
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.StopAudio

/**
 * Plays [PlayAudio]/[StopAudio] commands via one [MediaPlayer] per distinct audio id (created
 * lazily, reused across plays — same approach as the existing free-timer [com.pirxhio.affirmity.
 * ui.meditation.MeditationScreen]'s gong). An audio id with no entry in [resourcesByAudioId] is
 * silently ignored rather than crashing: the domain can declare sounds this build doesn't ship
 * yet, e.g. while only [com.pirxhio.affirmity.meditation.breathing.BreathingAudio.RETENTION_START]
 * has a real asset today.
 */
class GuidedMeditationAudioExecutor(
    private val context: Context,
    /** Values are `@RawRes` ids — not annotated on the map itself since `@RawRes` only applies to
     * a single int/long, not a `Map` value type. */
    private val resourcesByAudioId: Map<String, Int>,
) : MeditationCommandExecutor {
    private val players = mutableMapOf<String, MediaPlayer>()

    override fun execute(command: MeditationCommand) {
        when (command) {
            is PlayAudio -> play(command.audioId)
            is StopAudio -> if (command.audioId != null) stop(command.audioId) else stopAll()
            else -> Unit
        }
    }

    private fun play(audioId: String) {
        val player = players.getOrPut(audioId) {
            val resId = resourcesByAudioId[audioId] ?: return
            MediaPlayer.create(context, resId)
        }
        player.seekTo(0)
        player.start()
    }

    private fun stop(audioId: String) {
        players[audioId]?.takeIf { it.isPlaying }?.pause()
    }

    private fun stopAll() {
        players.values.forEach { if (it.isPlaying) it.pause() }
    }

    /** Must be called when the owning screen leaves composition — mirrors the `DisposableEffect
     * { onDispose { gongPlayer.release() } }` pattern already used by the free-timer screen. */
    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }
}
