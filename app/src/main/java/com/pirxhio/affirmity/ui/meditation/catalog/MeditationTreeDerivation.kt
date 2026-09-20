package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat

/**
 * Pure tree walks over a [MeditationDefinition]'s [MeditationNode] structure (REQ-4.8, design
 * §5.3). Used to derive everything about a catalog entry's presentation that is structural rather
 * than editorial, so it can never drift from the entry's own definition tree.
 */

/** Every [Phase] leaf reachable from [node], in tree order. [Repeat] contributes its child's
 * phases exactly once — this is a structural walk of the tree, not a simulation of how many times
 * an iteration actually runs. */
fun collectPhases(node: MeditationNode): List<Phase> = when (node) {
    is Phase -> listOf(node)
    is MeditationSequence -> node.children.flatMap(::collectPhases)
    is Repeat -> collectPhases(node.child)
}

/** Every [MeditationCommand] declared by any phase reachable from [node] — [Phase.onEnter] then
 * [Phase.onExit], in phase order. */
fun collectCommands(node: MeditationNode): List<MeditationCommand> =
    collectPhases(node).flatMap { it.onEnter + it.onExit }

/**
 * `phaseId -> Fixed duration millis` for every [PhaseDuration.Fixed] phase in [definition]'s tree.
 * [PhaseDuration.Manual] phases are absent from the map — a byte-for-byte-equivalent replacement
 * for the deleted `phaseTotalMillis(phaseId, config)` `when`, which returned `null` for the
 * (then-only) manual phase, "retention".
 */
fun fixedPhaseDurationsById(definition: MeditationDefinition): Map<String, Long> =
    collectPhases(definition.root)
        .mapNotNull { phase -> (phase.duration as? PhaseDuration.Fixed)?.let { phase.id to it.millis } }
        .toMap()

private const val MILLIS_PER_MINUTE = 60_000L
private const val HALF_MINUTE_MILLIS = MILLIS_PER_MINUTE / 2

/**
 * Session length shown to the user, in whole minutes — the single source of truth for both the
 * catalog list and the guided-meditation detail screen. Derived from [expectedDurationMillis] (so
 * the "no fixed time -> declared approx" rule lives in exactly one place), rounded to the nearest
 * minute (30 s rounds up), minimum 1, clamped to [Int.MAX_VALUE].
 */
fun displayDurationMinutes(definition: MeditationDefinition, approxDurationMinutes: Int): Int {
    val expectedMillis = expectedDurationMillis(definition, approxDurationMinutes)
    val minutes = (expectedMillis + HALF_MINUTE_MILLIS) / MILLIS_PER_MINUTE
    return minutes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Expected wall-clock length of the session actually played, in millis: [fixedTotalMillis] of
 * [definition] (customization and [Repeat] counts included), falling back to the catalog-declared
 * [approxDurationMinutes] only when the definition has no Fixed time (e.g. manual-only phases).
 * A mixed Manual + Fixed definition counts only its Fixed phases (theoretical today: no catalog
 * entry uses Manual phases). Exact millis rather than [displayDurationMinutes] so a completion
 * threshold is not biased by minute rounding (up to 30 s on short sessions).
 */
fun expectedDurationMillis(definition: MeditationDefinition, approxDurationMinutes: Int): Long {
    val fixedMillis = fixedTotalMillis(definition)
    return if (fixedMillis > 0L) fixedMillis else approxDurationMinutes.coerceAtLeast(1) * MILLIS_PER_MINUTE
}

/**
 * Total Fixed-phase time of [definition] with [Repeat] iteration counts applied: a
 * [FixedCountRepetition] multiplies its child's total by its count (nested repeats multiply
 * through); any other strategy is counted once, as its length is not knowable statically.
 * Unlike [fixedPhaseDurationsById] this never collapses same-id phases. [PhaseDuration.Manual]
 * phases contribute nothing.
 */
internal fun fixedTotalMillis(definition: MeditationDefinition): Long = nodeFixedTotalMillis(definition.root)

private fun nodeFixedTotalMillis(node: MeditationNode): Long = when (node) {
    is Phase -> (node.duration as? PhaseDuration.Fixed)?.millis ?: 0L
    is MeditationSequence -> node.children.sumOf(::nodeFixedTotalMillis)
    is Repeat -> nodeFixedTotalMillis(node.child) * ((node.strategy as? FixedCountRepetition)?.times ?: 1)
}
