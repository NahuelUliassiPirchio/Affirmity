package com.pirxhio.affirmity.ui.meditation

import android.media.MediaPlayer

/** The slice of [MediaPlayer] that [releaseAfterPlayback] needs, so the policy is JVM-testable. */
interface ReleasablePlayer {
    val isPlaying: Boolean

    /** Mirrors [MediaPlayer.setOnCompletionListener]: [listener] runs when playback finishes. */
    fun setOnCompletion(listener: () -> Unit)
    fun release()
}

fun MediaPlayer.asReleasablePlayer(): ReleasablePlayer = object : ReleasablePlayer {
    override val isPlaying: Boolean get() = this@asReleasablePlayer.isPlaying
    override fun setOnCompletion(listener: () -> Unit) = setOnCompletionListener { listener() }
    override fun release() = this@asReleasablePlayer.release()
}

/** [ReleasablePlayer.isPlaying] throws [IllegalStateException] on a released or errored player;
 * such a player is not playing anything, so treat it as idle. */
private fun ReleasablePlayer.isPlayingSafely(): Boolean = try {
    isPlaying
} catch (_: IllegalStateException) {
    false
}

/**
 * Releases [player] without cutting a sound that is still ringing: immediately if idle (or if its
 * state can no longer be read), otherwise once playback completes. A completion cue (the free
 * timer's gong) must survive its screen leaving composition, e.g. when a streak-healer grant
 * replaces the whole UI right after the session completes.
 */
fun releaseAfterPlayback(player: ReleasablePlayer) {
    if (!player.isPlayingSafely()) {
        player.release()
        return
    }
    // Guards against releasing twice: the completion callback can fire again, and the re-check
    // below can also release.
    var released = false
    val releaseOnce = {
        if (!released) {
            released = true
            player.release()
        }
    }
    player.setOnCompletion { releaseOnce() }
    // Playback may have finished between the first check and the registration above, in which
    // case the callback will never fire.
    if (!player.isPlayingSafely()) releaseOnce()
}
