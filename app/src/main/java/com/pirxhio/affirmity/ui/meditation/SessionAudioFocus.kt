package com.pirxhio.affirmity.ui.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Holds audio focus for a guided session and reports every loss through [onFocusLost] (see
 * [SessionInterruptionPolicy.shouldPauseFor]). Focus is never re-taken on its own: the caller asks
 * again via [request] when the user resumes. The media players don't take focus themselves, so
 * this is the only place the session talks to the system's focus arbitration.
 */
class SessionAudioFocus(
    context: Context,
    private val onFocusLost: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (SessionInterruptionPolicy.shouldPauseFor(change)) onFocusLost()
    }

    private val request: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
        } else {
            null
        }

    /** Asking again while already holding focus is harmless. Returns whether focus was granted
     * (see [SessionInterruptionPolicy.isFocusGranted]); the caller decides what a refusal means. */
    fun request(): Boolean {
        val result = if (request != null) {
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return SessionInterruptionPolicy.isFocusGranted(result)
    }

    fun abandon() {
        if (request != null) {
            audioManager.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
    }
}
