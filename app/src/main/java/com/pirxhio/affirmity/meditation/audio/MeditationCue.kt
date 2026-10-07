package com.pirxhio.affirmity.meditation.audio

/**
 * Semantic sound cues: meditation definitions say WHAT is happening, [MeditationSoundRegistry]
 * decides which asset represents it. Never reference an audio file from a definition.
 *
 * Future cues (BreathIn, BreathOut, BreathHoldIn, BreathHoldOut, BreathTick, Tense, Release,
 * MantraTick, voice, ...) are added here, together with their registry entry.
 */
enum class MeditationCue {
    /** The meditation begins. Emitted once by the engine on initial phase entry. */
    SessionStart,

    /** The meditation completes naturally. Emitted once by the engine; never on cancel. */
    SessionEnd,

    /** A round (e.g. a breathing round) begins. */
    RoundStart,

    /** A round completes. */
    RoundEnd,

    /** Moving on to the next step, body region or section. */
    SectionTransition,

    /** A major boundary, e.g. the start of a breath retention. */
    ImportantTransition,

    /** Interval bell during silent zazen. */
    ZazenBell,
}
