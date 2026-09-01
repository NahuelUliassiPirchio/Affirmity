package com.pirxhio.affirmity.ui.meditation.catalog

import androidx.annotation.StringRes

/**
 * Per-entry rendering data for the generic
 * [com.pirxhio.affirmity.ui.meditation.GuidedMeditationScreen]. The screen is a pure renderer over
 * `MeditationRuntimeState` + this; it holds no knowledge of any specific meditation.
 *
 * Deliberately does NOT carry phase durations or the set of phase ids: both are derived from the
 * entry's own [MeditationCatalogEntry.definition] tree (`fixedPhaseDurationsById`), so they cannot
 * drift from it.
 *
 * MUST import no Compose type (only `androidx.annotation`), so it stays constructible and
 * assertable in plain JVM tests.
 */
data class MeditationPresentation(
    /** `ShowText` text id -> `@StringRes`. Replaces the breathing-specific `phaseLabel` `when`. */
    val textResources: Map<String, Int> = emptyMap(),
    /** `PlayVoice`/`PlayAudio`/`StartAmbient` audio id -> `@RawRes`. Empty for every entry in this
     * spec — the executor silently ignores unmapped ids, so an entry declaring symbolic audio it
     * does not yet ship runs correctly and silently. */
    val audioResources: Map<String, Int> = emptyMap(),
    /** Repeat-node progress counters, rendered in order. Generalizes today's hardcoded
     * `iterationCounts["rounds"]` / `iterationCounts["breathing"]` pair. */
    val counters: List<MeditationCounter> = emptyList(),
    /** Optional "tap to release" affordance. Generalizes today's `currentPhaseId == "retention"`
     * gate on the release `TextButton`. `null` when no phase in this entry is user-released. */
    val manualRelease: ManualRelease? = null,
)

/**
 * One "N of M" progress line, keyed by a [com.pirxhio.affirmity.meditation.Repeat] node's id
 * (which is exactly what `MeditationRuntimeState.iterationCounts` is keyed by).
 */
data class MeditationCounter(
    val repeatId: String,
    /** Denominator. Declared rather than derived because `FixedCountRepetition.times` is private
     * and widening it is forbidden by DC-1a. */
    val total: Int,
    /** Two-arg label, e.g. `R.string.guided_meditation_round_label` = "Ronda %1$d de %2$d". */
    @StringRes val labelRes: Int,
    val emphasis: CounterEmphasis = CounterEmphasis.SECONDARY,
    /** Contrast/UX-audit item 10 fix: when set, the customization value at this key (if present
     * and parseable) overrides [total] for display -- [total] alone is authored once at catalog
     * definition time and can't reflect a per-session customized count (e.g. Dhikr's 11/33/99
     * `repetitions` field), unlike [MeditationCatalogEntry.definition], which already IS a
     * function of the confirmed customization. `null` (the default) means "always use [total]",
     * unchanged behavior for every other entry's counters. */
    val totalFromCustomizationKey: String? = null,
)

/** The two type styles the screen already uses for its two counters — `bodyMedium` for the outer,
 * `bodySmall` for the inner. Named rather than passed as a `TextStyle` so [MeditationPresentation]
 * stays free of Compose types and JVM-testable. */
enum class CounterEmphasis { PRIMARY, SECONDARY }

/** Shows a text button while [phaseId] is the active phase; tapping sends
 * `MeditationEvent.UserAction()`, exactly as the retention release button does today. */
data class ManualRelease(
    val phaseId: String,
    @StringRes val hintRes: Int,
)
