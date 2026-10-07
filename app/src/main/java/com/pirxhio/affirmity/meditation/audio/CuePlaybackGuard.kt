package com.pirxhio.affirmity.meditation.audio

/**
 * Blocks the same [MeditationCue] from sounding twice within one phase entry (e.g. a definition
 * that accidentally lists a cue twice, or an exit cue colliding with an entry cue). Keyed by the
 * phase-entry key the engine stamps on every cue (its timer generation), so a cue legitimately
 * repeats on the next entry of the same phase (every round, every Repeat iteration).
 *
 * Pure and Android-free. One instance per session executor; [reset] on session end.
 */
class CuePlaybackGuard {
    private val played = mutableSetOf<Pair<MeditationCue, Int>>()

    /** A null [entryKey] means "unkeyed" and always plays. */
    fun shouldPlay(cue: MeditationCue, entryKey: Int?): Boolean {
        if (entryKey == null) return true
        return played.add(cue to entryKey)
    }

    fun reset() = played.clear()
}
