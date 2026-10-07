package com.pirxhio.affirmity.meditation.audio

import androidx.annotation.RawRes
import com.pirxhio.affirmity.R

/** A raw resource plus its per-asset gain (0f..1f), applied on top of the master cue volume. */
data class CueSound(@RawRes val resId: Int, val gain: Float)

/**
 * The ONLY place that maps a [MeditationCue] to an asset. Swapping a sound or rebalancing a gain
 * is a one-line change here; meditation definitions never see a file name.
 */
object MeditationSoundRegistry {
    /** Single master level for every cue; a future settings slider plugs in here. */
    const val MASTER_CUE_VOLUME = 1.0f

    private val sounds: Map<MeditationCue, CueSound> = mapOf(
        MeditationCue.SessionStart to CueSound(R.raw.aff_bell_soft, 1.00f),
        MeditationCue.RoundStart to CueSound(R.raw.aff_bell_soft, 1.00f),
        MeditationCue.RoundEnd to CueSound(R.raw.aff_bell_low, 0.95f),
        MeditationCue.SessionEnd to CueSound(R.raw.aff_gong_deep, 0.85f),
        MeditationCue.ImportantTransition to CueSound(R.raw.aff_gong_deep, 0.85f),
        MeditationCue.ZazenBell to CueSound(R.raw.aff_zazen_bell, 0.95f),
        MeditationCue.SectionTransition to CueSound(R.raw.aff_chime_transition, 0.75f),
    )

    fun soundFor(cue: MeditationCue): CueSound? = sounds[cue]
}
