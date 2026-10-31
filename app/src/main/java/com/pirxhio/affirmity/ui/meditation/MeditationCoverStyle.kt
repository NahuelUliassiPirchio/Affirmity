package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.R

/** Design 9: short, medium and long sessions have progressively denser artwork. */
internal fun meditationCoverRhythm(minutes: Int): Int = when {
    minutes <= 3 -> 2
    minutes <= 10 -> 3
    else -> 4
}

internal enum class MeditationCoverMotif {
    RINGS, SQUARES, HORIZON, ROOT, CONVERGE, FLAME,
    CONSTELLATION, BANDS, WAVE, CRESCENT, OVERLAP, RADIATE,
}

/** Practice-specific overrides, keyed by catalog id. Data rather than a `when` so a test can check
 *  every key still exists in the catalog -- a renamed id would otherwise silently fall back. */
internal val meditationCoverMotifsById: Map<String, MeditationCoverMotif> = buildMap {
    fun form(motif: MeditationCoverMotif, vararg ids: String) = ids.forEach { put(it, motif) }
    form(MeditationCoverMotif.SQUARES, "box_breathing")
    form(MeditationCoverMotif.HORIZON, "calma", "extended_exhale", "visualization", "muraqabah")
    form(MeditationCoverMotif.ROOT, "zazen", "centering_prayer")
    form(MeditationCoverMotif.CONVERGE, "enfoque", "mantra_meditation", "om_meditation", "dhikr",
        "jesus_prayer", "lectio_divina")
    form(MeditationCoverMotif.FLAME, "trataka")
    form(MeditationCoverMotif.CONSTELLATION, "vipassana", "noting", "open_awareness")
    form(MeditationCoverMotif.BANDS, "body_scan", "yoganidra", "progressive_muscle_relaxation")
    form(MeditationCoverMotif.WAVE, "walking_meditation", "kapalabhati", "breath_of_fire", "wim_hof")
    form(MeditationCoverMotif.CRESCENT, "dormir")
    form(MeditationCoverMotif.OVERLAP, "autocompasion_5", "autocompasion_10", "self_compassion_break",
        "hitbodedut")
    form(MeditationCoverMotif.RADIATE, "metta", "gratitude_meditation")
}

/** Unknown practices retain the neutral breathing form. */
internal fun meditationCoverMotif(id: String): MeditationCoverMotif =
    meditationCoverMotifsById[id] ?: MeditationCoverMotif.RINGS

/** Explicit semantic colors stay stable when generated Android resource IDs change. */
internal fun meditationCoverColor(categoryRes: Int): Long = when (categoryRes) {
    R.string.meditation_catalog_category_calma,
    R.string.meditation_catalog_category_mindfulness,
    R.string.meditation_catalog_category_silencio,
    R.string.meditation_catalog_category_contemplacion -> 0xFF7FA8E0
    R.string.meditation_catalog_category_enfoque,
    R.string.meditation_catalog_category_energia,
    R.string.meditation_catalog_category_mantra -> 0xFFE2C26A
    R.string.meditation_catalog_category_sueno -> 0xFFA493E6
    R.string.meditation_catalog_category_cuerpo,
    R.string.meditation_catalog_category_movimiento -> 0xFFE8906F
    R.string.meditation_catalog_category_autocompasion,
    R.string.meditation_catalog_category_compasion,
    R.string.meditation_catalog_category_gratitud -> 0xFFE58AA6
    else -> 0xFF5BBCC3
}
