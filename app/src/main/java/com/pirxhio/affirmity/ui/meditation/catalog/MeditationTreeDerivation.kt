package com.pirxhio.affirmity.ui.meditation.catalog

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
