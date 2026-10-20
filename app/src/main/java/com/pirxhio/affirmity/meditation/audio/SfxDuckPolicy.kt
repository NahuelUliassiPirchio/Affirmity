package com.pirxhio.affirmity.meditation.audio

/** How loud short SFX clips play relative to a spoken guide clip. */
object SfxDuckPolicy {
    /** About -7.5 dB: breath sounds stay audible but sit under the voice. */
    const val DUCKED_GAIN = 0.42f

    fun gain(voicePlaying: Boolean): Float = if (voicePlaying) DUCKED_GAIN else 1f
}
