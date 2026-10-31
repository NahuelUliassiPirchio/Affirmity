package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.ui.meditation.catalog.meditationCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationCoverTest {
    @Test fun `every practice with its own form is a real catalog id`() {
        val catalogIds = meditationCatalog().map { it.id }.toSet()
        val orphans = meditationCoverMotifsById.keys - catalogIds
        assertTrue("Cover forms mapped to ids missing from the catalog: $orphans", orphans.isEmpty())
    }

    @Test fun `duration changes cover rhythm at design boundaries`() {
        assertEquals(listOf(2, 2, 3, 3, 4), listOf(1, 3, 4, 10, 11).map(::meditationCoverRhythm))
    }
    @Test fun `practice selects form instead of category icon`() {
        val expected = mapOf(
            "reset_rapido" to MeditationCoverMotif.RINGS,
            "box_breathing" to MeditationCoverMotif.SQUARES,
            "calma" to MeditationCoverMotif.HORIZON,
            "zazen" to MeditationCoverMotif.ROOT,
            "enfoque" to MeditationCoverMotif.CONVERGE,
            "trataka" to MeditationCoverMotif.FLAME,
            "vipassana" to MeditationCoverMotif.CONSTELLATION,
            "body_scan" to MeditationCoverMotif.BANDS,
            "walking_meditation" to MeditationCoverMotif.WAVE,
            "dormir" to MeditationCoverMotif.CRESCENT,
            "autocompasion_5" to MeditationCoverMotif.OVERLAP,
            "metta" to MeditationCoverMotif.RADIATE,
            "yoganidra" to MeditationCoverMotif.BANDS,
        )
        expected.forEach { (id, motif) -> assertEquals(id, motif, meditationCoverMotif(id)) }
        assertEquals(MeditationCoverMotif.RINGS, meditationCoverMotif("future_session"))
    }

    @Test fun `category colors follow reference rather than resource number order`() {
        assertEquals(0xFF5BBCC3, meditationCoverColor(R.string.meditation_catalog_category_respiracion))
        assertEquals(0xFF7FA8E0, meditationCoverColor(R.string.meditation_catalog_category_calma))
        assertEquals(0xFFE2C26A, meditationCoverColor(R.string.meditation_catalog_category_enfoque))
        assertEquals(0xFFA493E6, meditationCoverColor(R.string.meditation_catalog_category_sueno))
        assertEquals(0xFFE8906F, meditationCoverColor(R.string.meditation_catalog_category_cuerpo))
        assertEquals(0xFFE58AA6, meditationCoverColor(R.string.meditation_catalog_category_autocompasion))
    }
}
