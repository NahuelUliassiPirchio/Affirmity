package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationSoundRegistryTest {

    @Test
    fun `every cue has a sound`() {
        MeditationCue.entries.forEach { cue ->
            assertTrue("missing sound for $cue", MeditationSoundRegistry.soundFor(cue) != null)
        }
    }

    @Test
    fun `cues map to the agreed assets and gains`() {
        assertEquals(CueSound(R.raw.aff_bell_soft, 1.00f), MeditationSoundRegistry.soundFor(MeditationCue.SessionStart))
        assertEquals(CueSound(R.raw.aff_bell_soft, 1.00f), MeditationSoundRegistry.soundFor(MeditationCue.RoundStart))
        assertEquals(CueSound(R.raw.aff_bell_low, 0.95f), MeditationSoundRegistry.soundFor(MeditationCue.RoundEnd))
        assertEquals(CueSound(R.raw.aff_gong_deep, 0.85f), MeditationSoundRegistry.soundFor(MeditationCue.SessionEnd))
        assertEquals(CueSound(R.raw.aff_gong_deep, 0.85f), MeditationSoundRegistry.soundFor(MeditationCue.ImportantTransition))
        assertEquals(CueSound(R.raw.aff_zazen_bell, 0.95f), MeditationSoundRegistry.soundFor(MeditationCue.ZazenBell))
        assertEquals(CueSound(R.raw.aff_chime_transition, 0.75f), MeditationSoundRegistry.soundFor(MeditationCue.SectionTransition))
    }
}
